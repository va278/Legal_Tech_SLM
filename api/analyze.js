const SYSTEM_PROMPT = `You are an expert legal analyst and attorney with decades of experience in contract law,
litigation, regulatory compliance, employment law, intellectual property, and legal risk
assessment. Your task is to meticulously analyze legal documents and return structured,
actionable insights in strict JSON format.

When analyzing a legal document you MUST:
1. Identify the document/case type (Contract, Litigation, Regulatory, Employment, IP, Real Estate, Criminal, or Other).
2. Identify all named parties.
3. Detect the governing jurisdiction if stated.
4. Extract every significant legal clause, categorizing each as one of:
   Indemnification, Limitation of Liability, Termination, Payment Terms,
   IP Assignment, Confidentiality / NDA, Dispute Resolution, Force Majeure,
   Warranty / Representation, Non-Compete / Non-Solicitation, Governing Law,
   Data Protection, Change of Control, or Other.
5. Assess legal risks, rating each as HIGH / MEDIUM / LOW and assigning it to one of:
   Financial, Legal, Operational, Reputational, or Compliance.
6. Produce an overall risk score from 1 (minimal risk) to 10 (extreme risk).
7. Summarize the case in 2-3 sentences.
8. List the top key points a lawyer or client should know immediately.
9. List concrete recommended actions to mitigate the identified risks.

CRITICAL: Respond ONLY with a single, valid JSON object matching EXACTLY this schema.
Do NOT wrap it in markdown code fences. Do NOT add commentary before or after the JSON.

{
  "case_summary":        "<2-3 sentence summary>",
  "case_type":           "<type>",
  "parties_involved":    ["<party1>", "<party2>"],
  "jurisdiction":        "<jurisdiction or Not specified>",
  "key_date":            "<key date or date range or Not specified>",
  "key_clauses": [
    {
      "clause_type":   "<type>",
      "clause_text":   "<verbatim or paraphrased text>",
      "significance":  "HIGH|MEDIUM|LOW",
      "explanation":   "<why this matters legally>",
      "section":       "<section/page reference or Not specified>"
    }
  ],
  "risks": [
    {
      "risk_id":        "R001",
      "category":       "Financial|Legal|Operational|Reputational|Compliance",
      "severity":       "HIGH|MEDIUM|LOW",
      "title":          "<short risk title>",
      "description":    "<detailed description>",
      "affected_clause":"<which clause creates this risk>",
      "recommendation": "<specific mitigation action>"
    }
  ],
  "overall_risk_score":   7,
  "key_points":          ["<point1>", "<point2>"],
  "recommended_actions": ["<action1>", "<action2>"]
}`;

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') return res.status(200).end();
  if (req.method !== 'POST')    return res.status(405).json({ error: 'Method not allowed' });

  const apiKey = process.env.ANTHROPIC_API_KEY;
  if (!apiKey) {
    return res.status(500).json({
      error: 'Server not configured. Set ANTHROPIC_API_KEY in your Vercel project environment variables.'
    });
  }

  const { text } = req.body || {};
  if (!text || !text.trim()) {
    return res.status(400).json({ error: 'Document text is required.' });
  }

  const truncated = text.slice(0, 200000);

  let apiResponse;
  try {
    apiResponse = await fetch('https://api.anthropic.com/v1/messages', {
      method: 'POST',
      headers: {
        'Content-Type':    'application/json',
        'x-api-key':       apiKey,
        'anthropic-version': '2023-06-01',
        'anthropic-beta':  'prompt-caching-2024-07-31',
      },
      body: JSON.stringify({
        model: 'claude-sonnet-4-6',
        max_tokens: 4096,
        system: [{ type: 'text', text: SYSTEM_PROMPT, cache_control: { type: 'ephemeral' } }],
        messages: [{
          role: 'user',
          content: `<legal_document>\n${truncated}\n</legal_document>\n\nAnalyze this legal document and respond with the JSON object described in your instructions.`
        }]
      })
    });
  } catch (err) {
    return res.status(502).json({ error: `Network error reaching Claude API: ${err.message}` });
  }

  if (!apiResponse.ok) {
    const errData = await apiResponse.json().catch(() => ({}));
    return res.status(apiResponse.status).json({
      error: errData?.error?.message || `Claude API error (${apiResponse.status})`
    });
  }

  const data = await apiResponse.json();
  const rawText = data?.content?.[0]?.text || '';

  // Strip markdown fences in case Claude ignores the instruction
  let json = rawText.trim();
  if (json.startsWith('```')) {
    json = json.replace(/^```(?:json)?\n?/, '').replace(/\n?```$/, '').trim();
  }
  // Find the first { if Claude added preamble text
  const brace = json.indexOf('{');
  if (brace > 0) json = json.slice(brace);

  try {
    const analysis = JSON.parse(json);
    return res.json({
      ...analysis,
      _meta: {
        model:        data.model,
        inputTokens:  data.usage?.input_tokens,
        outputTokens: data.usage?.output_tokens,
      }
    });
  } catch {
    return res.status(500).json({ error: 'Could not parse Claude response as JSON.', raw: rawText.slice(0, 500) });
  }
};
