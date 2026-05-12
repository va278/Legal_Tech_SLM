const ASK_SYSTEM = `You are an expert legal analyst. The user has loaded a legal document and will ask specific questions about it.

Rules:
- Answer based strictly on the document content provided.
- Be concise, direct, and cite the specific section or clause your answer comes from.
- If the answer cannot be determined from the document, say so explicitly — never guess.
- Format your answer in plain prose (no markdown). Lead with the direct answer, then explain.
- If the question involves legal advice, note that the user should consult qualified counsel for their specific situation.`;

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') return res.status(200).end();
  if (req.method !== 'POST')    return res.status(405).json({ error: 'Method not allowed' });

  const apiKey = process.env.ANTHROPIC_API_KEY;
  if (!apiKey) return res.status(500).json({ error: 'ANTHROPIC_API_KEY not configured.' });

  const { question, documentText, history = [] } = req.body || {};
  if (!question?.trim())      return res.status(400).json({ error: 'question is required.' });
  if (!documentText?.trim())  return res.status(400).json({ error: 'documentText is required.' });

  // Build message history — document is injected as first user turn (cacheable)
  const messages = [
    {
      role: 'user',
      content: [
        {
          type: 'text',
          text: `<legal_document>\n${documentText.slice(0, 150000)}\n</legal_document>\n\nI have questions about this document.`,
          cache_control: { type: 'ephemeral' }, // cache the document across follow-up questions
        }
      ]
    },
    { role: 'assistant', content: 'Understood. I have read the document. Please ask your questions.' },
    // Prior conversation turns
    ...history.flatMap(({ q, a }) => [
      { role: 'user',      content: q },
      { role: 'assistant', content: a },
    ]),
    { role: 'user', content: question.trim() },
  ];

  let apiResp;
  try {
    apiResp = await fetch('https://api.anthropic.com/v1/messages', {
      method: 'POST',
      headers: {
        'Content-Type':      'application/json',
        'x-api-key':         apiKey,
        'anthropic-version': '2023-06-01',
        'anthropic-beta':    'prompt-caching-2024-07-31',
      },
      body: JSON.stringify({
        model:      'claude-sonnet-4-6',
        max_tokens: 1024,
        system:     ASK_SYSTEM,
        messages,
      })
    });
  } catch (err) {
    return res.status(502).json({ error: `Network error: ${err.message}` });
  }

  if (!apiResp.ok) {
    const e = await apiResp.json().catch(() => ({}));
    return res.status(apiResp.status).json({ error: e?.error?.message || `Claude error (${apiResp.status})` });
  }

  const data   = await apiResp.json();
  const answer = data?.content?.[0]?.text?.trim() || '';

  return res.json({
    answer,
    _meta: { inputTokens: data.usage?.input_tokens, outputTokens: data.usage?.output_tokens }
  });
};
