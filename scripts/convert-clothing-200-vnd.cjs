// Owner requested USD -> VND, rounded to a whole 1,000 VND. No database writes.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const directory = path.resolve(__dirname, '../artifacts/catalog-review/livostyle');
const RATE = 25730n;
function toVnd(usd) {
  if (typeof usd !== 'number' || !Number.isFinite(usd) || usd < 0) throw Error('INVALID_USD_PRICE');
  const match = String(usd).match(/^(\d+)(?:\.(\d{1,2}))?$/);
  if (!match) throw Error('USD_PRICE_MUST_HAVE_AT_MOST_TWO_DECIMALS');
  const cents = BigInt(match[1]) * 100n + BigInt((match[2] || '').padEnd(2, '0'));
  // Exact integer arithmetic: half-up to nearest 1,000 VND, no floating point.
  const rounded = ((cents * RATE + 50000n) / 100000n) * 1000n;
  if (rounded > BigInt(Number.MAX_SAFE_INTEGER)) throw Error('VND_PRICE_OUT_OF_RANGE');
  return Number(rounded);
}
function main() {
  const input = fs.readFileSync(path.join(directory, 'clothing-200-cloudinary.json'));
  const source = JSON.parse(input.toString('utf8'));
  if (source.length !== 200) throw Error('EXPECTED_200_PRODUCTS');
  const output = source.map(p => {
    if (p.price.currency !== 'USD' || !p.variants.length) throw Error('UNEXPECTED_SOURCE_CURRENCY_OR_VARIANTS');
    const variants = p.variants.map(v => ({ ...v, unit_price_vnd: toVnd(v.price_usd),
      compare_at_price_vnd: v.compare_at_price_usd == null ? null : toVnd(v.compare_at_price_usd) }));
    const prices = variants.map(v => v.unit_price_vnd);
    if (prices.some(v => v <= 0)) throw Error('NONPOSITIVE_ROUNDED_PRICE');
    return { ...p, source_price: p.price, price: { min_vnd: Math.min(...prices), max_vnd: Math.max(...prices), currency: 'VND' }, variants };
  });
  const allPrices = output.flatMap(p => p.variants.map(v => v.unit_price_vnd));
  const summary = {
    status: 'VND_REVIEW_DATA_NOT_DATABASE_IMPORT', products: output.length, variants: allPrices.length,
    currency: 'VND', vnd_per_usd: Number(RATE), rounding_increment_vnd: 1000, rounding: 'HALF_UP',
    reference: { bank: 'Vietcombank', rate_type: 'USD selling reference', source_date: '2026-11-09',
      observed_on: '2026-11-10', url: 'https://www.vietcombank.com.vn/To-chuc/Trang-chu-DCTC/KHTC---Ti-gia---DCTC',
      note: 'Fixed reference for this dataset conversion; not a live or automatically updating rate.' },
    input: 'clothing-200-cloudinary.json', input_sha256: crypto.createHash('sha256').update(input).digest('hex'),
    min_unit_price_vnd: Math.min(...allPrices), max_unit_price_vnd: Math.max(...allPrices),
    source_prices_preserved: true, cloudinary_urls_preserved: true, database_touched: false,
    examples: [19.99, 39.99, 50].map(usd => ({ usd, vnd: toVnd(usd) }))
  };
  fs.writeFileSync(path.join(directory, 'clothing-200-cloudinary-vnd.json'), JSON.stringify(output, null, 2) + '\n');
  fs.writeFileSync(path.join(directory, 'clothing-200-vnd-summary.json'), JSON.stringify(summary, null, 2) + '\n');
  console.log(JSON.stringify(summary));
}
module.exports = { toVnd };
if (require.main === module) main();
