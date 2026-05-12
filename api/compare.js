const COMPARE_PROMPT = `You are a senior contract attorney specializing in redline review.
You will be given two versions of a legal document. Compare them carefully and identify
every meaningful difference. Return ONLY a valid JSON object — no markdown fences, no commentary.

JSON schema:
{
  "summary": "<2-3 sentence overview of the key changes and their significance>",
  "risk_change": "INCREASED|DECREASED|UNCHANGED",
  "risk_change_explanation": "<why the overall risk profile changed>",
  "favors": "<which party the revised document (B) favors vs original (A)>",
  "added_clauses": [
    { "clause_type": "<type>", "description": "<what was added>", "impact": "<legal/business impact>", "severity": "HIGH|MEDIUM|LOW" }
  ],
  "removed_clauses": [
    { "clause_type": "<type>", "description": "<what was removed>", "impact": "<legal/business impact>", "severity": "HIGH|MEDIUM|LOW" }
  ],
  "modified_clauses": [
    {
      "clause_type":  "<type>",
      "original":     "<what document A said>",
      "revised":      "<what document B says>",
      "impact":       "<legal/business impact of the change>",
      "favors":       "<which party benefits from the revision>",
      "severity":     "HIGH|MEDIUM|LOW"
    }
  ],
  "key_concerns":                ["<concern 1>", "<concern 2>"],
  "negotiation_recommendations": ["<recommendation 1>", "<recommendation 2>"]
}`;

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') return res.status(200).end();
  if (req.method !== 'POST')    return res.status(405).json({ error: 'Method not allowed' });

  const apiKey = process.env.ANTHROPIC_API_KEY;
  if (!apiKey) return res.status(500).json({ error: 'ANTHROPIC_API_KEY not configured.' });

  const { docA, docB, nameA = 'Document A', nameB = 'Document B' } = req.body || {};
  if (!docA?.trim() || !docB?.trim())
    return res.status(400).json({ error: 'Both docA and docB are required.' });

  const userMessage =
    `<document_a name="${nameA}">\n${docA.slice(0, 100000)}\n</document_a>\n\n` +
    `<document_b name="${nameB}">\n${docB.slice(0, 100000)}\n</document_b>\n\n` +
    `Compare these two documents and return the JSON comparison object.`;

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
        max_tokens: 4096,
        system: [{ type: 'text', text: COMPARE_PROMPT, cache_control: { type: 'ephemeral' } }],
        messages: [{ role: 'user', content: userMessage }]
      })
    });
  } catch (err) {
    return res.status(502).json({ error: `Network error: ${err.message}` });
  }

  if (!apiResp.ok) {
    const e = await apiResp.json().catch(() => ({}));
    return res.status(apiResp.status).json({ error: e?.error?.message || `Claude error (${apiResp.status})` });
  }

  const data = await apiResp.json();
  let json   = (data?.content?.[0]?.text || '').trim();
  if (json.startsWith('```')) json = json.replace(/^```(?:json)?\n?/, '').replace(/\n?```$/, '').trim();
  const brace = json.indexOf('{');
  if (brace > 0) json = json.slice(brace);

  try {
    return res.json({ ...JSON.parse(json), _meta: { inputTokens: data.usage?.input_tokens, outputTokens: data.usage?.output_tokens } });
  } catch {
    return res.status(500).json({ error: 'Could not parse comparison response.', raw: json.slice(0, 400) });
  }
};
