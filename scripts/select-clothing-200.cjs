// Select review data only; never connect to a database or change application seed.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const directory = path.resolve(__dirname, '../artifacts/catalog-review/livostyle');
const sourcePath = path.join(directory, 'clothing-products.json');
const sourceBytes = fs.readFileSync(sourcePath);
const source = JSON.parse(sourceBytes.toString('utf8'));
const quotas = { tops: 65, pants: 40, dresses: 40, skirts: 15, outerwear: 20, sets: 15, one_pieces: 5 };
function group(p) {
  const c = p.category?.full_path || '';
  if (c.includes(' > Clothing Tops')) return 'tops';
  if (/ > (Pants|Shorts|Activewear Pants)(?: >|$)/.test(c)) return 'pants';
  if (c.endsWith(' > Dresses')) return 'dresses';
  if (/ > (Skirts|Skorts)$/.test(c)) return 'skirts';
  if (c.includes(' > Outerwear >')) return 'outerwear';
  if (c.endsWith(' > Outfit Sets')) return 'sets';
  if (c.endsWith(' > One-Pieces')) return 'one_pieces';
  return null;
}
const key = p => p.title.normalize('NFKC').trim().replace(/\s+/g, ' ').toLowerCase();
function score(p) {
  const available = p.variants.filter(v => v.in_stock === true);
  const images = p.images.filter(i => /^https:\/\//.test(i.url) && i.width >= 600 && i.height >= 600);
  return Math.min(images.length, 6) * 10
    + Math.min(new Set(available.map(v => v.options?.Size).filter(Boolean)).size, 5) * 4
    + Math.min(new Set(available.map(v => v.options?.Color).filter(Boolean)).size, 4) * 2
    + (p.description?.trim() ? 5 : 0);
}
const eligible = source.filter(p => group(p)
  && !/\b(men(?:'s|s)?|boys?(?:'s)?|kids?|children|toddlers?|unisex)\b/i.test(p.title)
  && p.description?.trim()
  && p.images?.some(i => /^https:\/\//.test(i.url) && i.width >= 600 && i.height >= 600)
  && p.variants?.some(v => v.in_stock === true && v.options?.Size && v.options?.Color)
  && p.variants.every(v => v.sku && Number.isFinite(v.price_usd) && v.price_usd > 0));
const selected = [], names = new Set(), ids = new Set();
for (const [name, quota] of Object.entries(quotas)) {
  // Rotate source subcategories to avoid filling all tops with a single style.
  const buckets = new Map();
  for (const p of eligible.filter(p => group(p) === name)) {
    const category = p.category.full_path;
    if (!buckets.has(category)) buckets.set(category, []);
    buckets.get(category).push(p);
  }
  const queues = [...buckets.entries()].sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0).map(([, products]) =>
    products.sort((a, b) => score(b) - score(a) || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0)));
  let count = 0;
  while (count < quota) {
    let progress = false;
    for (const queue of queues) {
      while (queue.length && (names.has(key(queue[0])) || ids.has(queue[0].id))) queue.shift();
      if (!queue.length) continue;
      const p = queue.shift();
      selected.push(p); names.add(key(p)); ids.add(p.id); count++; progress = true;
      if (count === quota) break;
    }
    if (!progress) throw new Error(`Insufficient eligible ${name}: ${count}/${quota}; no output written.`);
  }
}
if (selected.length !== 200 || names.size !== 200 || ids.size !== 200) throw new Error('Selection invariant failed');
const variants = selected.flatMap(p => p.variants);
if (new Set(variants.map(v => v.sku)).size !== variants.length) throw new Error('Duplicate selected SKU');
const summary = {
  status: 'REVIEW_SOURCE_DATA_NOT_AN_APPLICATION_SEED',
  source_file: 'clothing-products.json',
  source_sha256: crypto.createHash('sha256').update(sourceBytes).digest('hex'),
  products: selected.length, variants: variants.length,
  source_in_stock_variants: variants.filter(v => v.in_stock === true).length,
  groups: quotas,
  selection_rules: ['Unique normalized titles and source IDs.', 'At least one HTTPS image >=600x600; no visual validation.',
    'Nonblank description; all variant prices positive.', 'At least one source in-stock variant with Size and Color.',
    'Prefer multiple image entries and size/color choices, rotating source subcategories.',
    'Preserve all original fields and variants, including source out-of-stock variants.'],
  limitations: ['Source category/gender and image matching not visually verified.', 'Remote URLs not checked.',
    'in_stock is a source snapshot, not current availability or a quantity.', 'USD prices unchanged; no VND pricing rule chosen.',
    'No seed, schema, business behavior, or database changed.'],
  selected_ids: selected.map(p => p.id)
};
fs.writeFileSync(path.join(directory, 'clothing-200.json'), JSON.stringify(selected, null, 2) + '\n');
fs.writeFileSync(path.join(directory, 'clothing-200-summary.json'), JSON.stringify(summary, null, 2) + '\n');
console.log(JSON.stringify({ products: summary.products, variants: summary.variants, source_in_stock_variants: summary.source_in_stock_variants, groups: quotas }));
