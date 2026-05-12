// Proxies to CourtListener's free public search API (no key required)
// Docs: https://www.courtlistener.com/api/rest/v4/

const COURT_LABELS = {
  scotus: 'Supreme Court', cadc: 'D.C. Circuit', cafc: 'Federal Circuit',
  ca1:'1st Cir.', ca2:'2nd Cir.', ca3:'3rd Cir.', ca4:'4th Cir.',
  ca5:'5th Cir.', ca6:'6th Cir.', ca7:'7th Cir.', ca8:'8th Cir.',
  ca9:'9th Cir.', ca10:'10th Cir.', ca11:'11th Cir.',
  cal:'CA Supreme', ny:'NY Court of Appeals', tex:'TX Supreme',
  fla:'FL Supreme', ill:'IL Supreme', pa:'PA Supreme', ohio:'OH Supreme',
};

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  if (req.method === 'OPTIONS') return res.status(200).end();
  if (req.method !== 'GET') return res.status(405).json({ error: 'Method not allowed' });

  const { q, court, filed_after, filed_before, page = 1 } = req.query;
  if (!q?.trim()) return res.status(400).json({ error: 'q (search query) is required' });

  const params = new URLSearchParams({ q: q.trim(), type: 'o', order_by: 'score desc', page });
  if (court && court !== 'all') params.set('court', court);
  if (filed_after)  params.set('filed_after',  filed_after);
  if (filed_before) params.set('filed_before', filed_before);

  try {
    const resp = await fetch(
      `https://www.courtlistener.com/api/rest/v4/search/?${params}`,
      { headers: { Accept: 'application/json', 'User-Agent': 'LegalAI/1.0 (legal research tool)' } }
    );

    if (!resp.ok) {
      const txt = await resp.text();
      return res.status(resp.status).json({ error: `CourtListener error (${resp.status}): ${txt.slice(0, 200)}` });
    }

    const raw = await resp.json();

    // Normalise results into a consistent shape the UI can rely on
    const results = (raw.results || []).map(r => ({
      id:       r.id,
      name:     r.case_name || r.caseName || 'Untitled',
      court:    COURT_LABELS[r.court_id] || r.court_id || r.court || '',
      date:     r.dateFiled || r.date_filed || '',
      citation: Array.isArray(r.citation) ? r.citation[0] : (r.citation || ''),
      snippet:  r.snippet  || '',
      status:   r.status   || '',
      url:      r.absolute_url
                  ? `https://www.courtlistener.com${r.absolute_url}`
                  : `https://www.courtlistener.com/?q=${encodeURIComponent(r.case_name || '')}`,
    }));

    return res.json({ count: raw.count || results.length, results });
  } catch (err) {
    return res.status(502).json({ error: `Search failed: ${err.message}` });
  }
};
