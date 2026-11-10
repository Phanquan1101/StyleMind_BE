// Local-only Gemini HTTP fixture for manual Stylist demos. It never contacts Google.
const http = require('node:http');

const host = '127.0.0.1';
const port = Number(process.argv[2] || 63280);
if (!Number.isInteger(port) || port < 1 || port > 65535) {
  throw new Error('Fixture port must be an integer from 1 to 65535');
}

const stats = { countTokens: 0, documentEmbeddings: 0, queryEmbeddings: 0, generations: 0, rejected: 0 };
if (process.env.STYLEMIND_GEMINI_FIXTURE_INITIAL_STATS) {
  const initialStats = JSON.parse(process.env.STYLEMIND_GEMINI_FIXTURE_INITIAL_STATS);
  for (const key of Object.keys(stats)) {
    const value = initialStats[key];
    if (Number.isSafeInteger(value) && value >= 0) stats[key] = value;
  }
}

function send(res, status, value) {
  const body = Buffer.from(JSON.stringify(value), 'utf8');
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': body.length,
  });
  res.end(body);
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://${host}`);
  if (req.method === 'GET' && url.pathname === '/healthz') return send(res, 200, { status: 'UP' });
  if (req.method === 'GET' && url.pathname === '/__stats') return send(res, 200, stats);
  if (req.method !== 'POST') return send(res, 405, { error: 'method' });

  let raw = '';
  req.setEncoding('utf8');
  req.on('data', (chunk) => {
    raw += chunk;
    if (raw.length > 8_000_000) {
      stats.rejected += 1;
      req.destroy();
    }
  });
  req.on('end', () => {
    let body;
    try {
      body = JSON.parse(raw);
    } catch {
      stats.rejected += 1;
      return send(res, 400, { error: 'json' });
    }

    if (url.pathname.endsWith(':countTokens')) {
      stats.countTokens += 1;
      return send(res, 200, { totalTokens: 100 });
    }
    if (url.pathname.endsWith(':batchEmbedContents')) {
      const requests = Array.isArray(body.requests) ? body.requests : [];
      if (!requests.length || requests.some((item) => item.outputDimensionality !== 1536)) {
        stats.rejected += 1;
        return send(res, 400, { error: 'embedding-contract' });
      }
      for (const item of requests) {
        if (item.taskType === 'RETRIEVAL_QUERY') stats.queryEmbeddings += 1;
        else stats.documentEmbeddings += 1;
      }
      const values = new Array(1536).fill(0);
      values[0] = 1;
      return send(res, 200, { embeddings: requests.map(() => ({ values })) });
    }
    if (url.pathname.endsWith(':generateContent')) {
      stats.generations += 1;
      try {
        const text = (body.contents || []).flatMap((item) => item.parts || []).map((part) => part.text || '').join('');
        const prompt = JSON.parse(text);
        const choice = (prompt.complete_outfit_choices || [])[0];
        if (!choice || !Array.isArray(choice.items) || choice.items.length !== 2) throw new Error('whitelist');
        const output = {
          recommendations: [{
            outfit_alias: choice.outfit_alias,
            explanation: 'Gợi ý được chọn từ các sản phẩm đang hoạt động trong catalog demo.',
            styling_tips: 'Phản hồi fixture offline dùng để xác minh tích hợp.',
            item_reasons: choice.items.map((item) => ({
              item_alias: item.item_alias,
              reason: 'Sản phẩm nằm trong whitelist để kiểm tra.',
            })),
          }],
        };
        return send(res, 200, {
          candidates: [{ content: { role: 'model', parts: [{ text: JSON.stringify(output) }] }, finishReason: 'STOP' }],
        });
      } catch {
        stats.rejected += 1;
        return send(res, 400, { error: 'generation-contract' });
      }
    }

    stats.rejected += 1;
    return send(res, 404, { error: 'endpoint' });
  });
});

server.listen(port, host, () => {
  process.stdout.write(`Offline Gemini HTTP fixture listening on http://${host}:${port}\n`);
});
