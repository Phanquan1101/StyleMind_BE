const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '..');
const directory = path.join(root, 'artifacts/catalog-review/livostyle');
const groups = {
  tops: ['Áo', 'ao'], pants: ['Quần', 'quan'], dresses: ['Đầm', 'dam'], skirts: ['Váy', 'vay'],
  outerwear: ['Áo khoác', 'ao-khoac'], sets: ['Set đồ', 'set-do'], one_pieces: ['Jumpsuit / đồ liền', 'jumpsuit']
};
function stableUuid(value) {
  const bytes = crypto.createHash('sha1').update('stylemind:livostyle-catalog:2026-11:' + value).digest().subarray(0, 16);
  bytes[6] = (bytes[6] & 15) | 80; bytes[8] = (bytes[8] & 63) | 128;
  const hex = bytes.toString('hex'); return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
}
function group(p) {
  const c = p.category.full_path;
  if (c.includes(' > Clothing Tops')) return 'tops';
  if (/ > (Pants|Shorts|Activewear Pants)(?: >|$)/.test(c)) return 'pants';
  if (c.endsWith(' > Dresses')) return 'dresses';
  if (/ > (Skirts|Skorts)$/.test(c)) return 'skirts';
  if (c.includes(' > Outerwear >')) return 'outerwear';
  if (c.endsWith(' > Outfit Sets')) return 'sets';
  if (c.endsWith(' > One-Pieces')) return 'one_pieces';
  throw Error('UNMAPPED_CATEGORY');
}
function bounded(value, max, required = false) {
  if (value == null && !required) return null;
  if (typeof value !== 'string' || (required && !value.trim()) || [...value].length > max || /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/.test(value)) throw Error('SOURCE_FIELD_INVALID');
  return value.trim() || null;
}
function buildPayload(source) {
  if (source.length !== 200) throw Error('EXPECTED_200_PRODUCTS');
  const ids = new Set(), skus = new Set();
  const products = source.map(p => {
    const id = stableUuid('product:' + p.id);
    if (ids.has(id) || p.price.currency !== 'VND') throw Error('DUPLICATE_ID_OR_WRONG_CURRENCY'); ids.add(id);
    const images = p.images.map((i, index) => {
      const url = new URL(i.url);
      if (url.origin !== 'https://res.cloudinary.com' || !url.pathname.includes('/image/upload/')) throw Error('NON_CLOUDINARY_IMAGE');
      return { assetId: stableUuid('asset:' + i.url), url: i.url, alt: bounded(i.alt, 160), order: index, primary: i.url === p.featured_image_url };
    });
    if (images.filter(i => i.primary).length !== 1) throw Error('EXPECTED_ONE_PRIMARY');
    const variants = p.variants.map(v => {
      const sku = 'LIVO-' + v.sku;
      if (!/^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$/.test(sku) || skus.has(sku)) throw Error('INVALID_OR_DUPLICATE_SKU'); skus.add(sku);
      if (!Number.isSafeInteger(v.unit_price_vnd) || v.unit_price_vnd <= 0 || v.unit_price_vnd % 1000) throw Error('INVALID_VND_PRICE');
      return { id: stableUuid('variant:' + p.id + ':' + v.sku), sku, size: bounded(v.options?.Size, 64),
        color: bounded(v.options?.Color, 64), material: bounded(p.metafields?.material || p.metafields?.fabric, 64), price: v.unit_price_vnd };
    });
    return { id, sourceId: p.id, categorySlug: groups[group(p)][1], name: bounded(p.title, 160, true),
      description: bounded(p.description, 2000), demographic: 'WOMEN', variants, images };
  });
  return { version: 1, brand: { id: stableUuid('brand:livostyle-catalog'), name: 'Livostyle Catalog', slug: 'livostyle-catalog' },
    categories: Object.values(groups).map(([name, slug]) => ({ id: stableUuid('category:' + slug), name, slug })), products };
}
function literal(value) { return "'" + String(value).replace(/'/g, "''") + "'"; }
function importSql(payload, options = {}) {
  const guard = options.guard || '';
  const assertions = options.assertions || '';
  return `SET standard_conforming_strings=on;
BEGIN;
SET LOCAL lock_timeout='10s';
SELECT pg_advisory_xact_lock(72620020261110);
${options.locks || ''}
${guard}
CREATE TEMP TABLE _livo_import(payload jsonb) ON COMMIT DROP;
INSERT INTO _livo_import VALUES (${literal(JSON.stringify(payload))}::jsonb);
CREATE TEMP VIEW _livo_products AS SELECT p FROM _livo_import, jsonb_array_elements(payload->'products') p;
DO $$ BEGIN
 IF (SELECT count(*) FROM _livo_products)<>200 THEN RAISE EXCEPTION 'Expected 200 products'; END IF;
 IF EXISTS (SELECT 1 FROM public.product WHERE product_id IN (SELECT (p->>'id')::uuid FROM _livo_products))
 THEN RAISE EXCEPTION 'Dataset already exists; do not repeat import'; END IF;
 IF EXISTS (SELECT 1 FROM public.product_variant WHERE sku IN (SELECT v->>'sku' FROM _livo_products,jsonb_array_elements(p->'variants') v))
 THEN RAISE EXCEPTION 'SKU conflict'; END IF;
END $$;
INSERT INTO public.category(category_id,name,slug,is_active)
SELECT (c->>'id')::uuid,c->>'name',c->>'slug',true FROM _livo_import,jsonb_array_elements(payload->'categories') c
ON CONFLICT(slug) DO NOTHING;
INSERT INTO public.brand(brand_id,name,slug,is_active)
SELECT (payload->'brand'->>'id')::uuid,payload->'brand'->>'name',payload->'brand'->>'slug',true FROM _livo_import
ON CONFLICT(slug) DO NOTHING;
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM public.brand WHERE slug='livostyle-catalog' AND name='Livostyle Catalog' AND is_active)
 THEN RAISE EXCEPTION 'Unexpected source brand'; END IF;
 IF EXISTS(SELECT 1 FROM _livo_products lp LEFT JOIN public.category c ON c.slug=lp.p->>'categorySlug' WHERE c.category_id IS NULL OR NOT c.is_active)
 THEN RAISE EXCEPTION 'Missing/inactive category'; END IF;
END $$;
UPDATE public.product SET product_status='INACTIVE' WHERE product_status<>'INACTIVE';
INSERT INTO public.product(product_id,category_id,brand_id,name,description,demographic_segment,product_status)
SELECT (p->>'id')::uuid,c.category_id,b.brand_id,p->>'name',p->>'description',p->>'demographic','ACTIVE'
FROM _livo_products JOIN public.category c ON c.slug=p->>'categorySlug'
CROSS JOIN public.brand b WHERE b.slug='livostyle-catalog';
INSERT INTO public.product_variant(variant_id,product_id,sku,size,color,material,unit_price_vnd,variant_status)
SELECT (v->>'id')::uuid,(p->>'id')::uuid,v->>'sku',v->>'size',v->>'color',v->>'material',(v->>'price')::bigint,'ACTIVE'
FROM _livo_products,jsonb_array_elements(p->'variants') v;
INSERT INTO public.variant_inventory(variant_id,on_hand_quantity,reserved_quantity,updated_at)
SELECT (v->>'id')::uuid,0,0,CURRENT_TIMESTAMP FROM _livo_products,jsonb_array_elements(p->'variants') v;
INSERT INTO public.image_asset(image_asset_id,image_url,created_at)
SELECT DISTINCT (i->>'assetId')::uuid,i->>'url',CURRENT_TIMESTAMP FROM _livo_products,jsonb_array_elements(p->'images') i;
-- The source provides a product gallery, not per-color image identifiers.
-- Share the full source gallery across variants, without inferring color matches.
INSERT INTO public.variant_image(image_id,variant_id,image_asset_id,alt_text,display_order,is_primary)
SELECT md5((v->>'id')||':'||(i->>'assetId'))::uuid,(v->>'id')::uuid,(i->>'assetId')::uuid,i->>'alt',(i->>'order')::int,(i->>'primary')::boolean
FROM _livo_products,jsonb_array_elements(p->'variants') v,jsonb_array_elements(p->'images') i;
DO $$ BEGIN
 IF (SELECT count(*) FROM public.product WHERE product_status='ACTIVE')<>200 THEN RAISE EXCEPTION 'Active product total mismatch'; END IF;
 IF (SELECT count(*) FROM public.product_variant v JOIN public.product p USING(product_id) JOIN public.brand b USING(brand_id) WHERE b.slug='livostyle-catalog')<>2705 THEN RAISE EXCEPTION 'Variant total mismatch'; END IF;
 IF EXISTS(SELECT 1 FROM public.product_variant v JOIN public.product p USING(product_id) JOIN public.brand b USING(brand_id)
 WHERE b.slug='livostyle-catalog' AND (unit_price_vnd<=0 OR unit_price_vnd%1000<>0)) THEN RAISE EXCEPTION 'Invalid VND prices'; END IF;
END $$;
${assertions}
DROP VIEW _livo_products;
${options.rollback ? 'ROLLBACK' : 'COMMIT'};
`;
}
function main() {
  const sourceFile = path.join(directory, 'clothing-200-cloudinary-vnd.json');
  const bytes = fs.readFileSync(sourceFile);
  const payload = buildPayload(JSON.parse(bytes.toString('utf8')));
  fs.writeFileSync(path.join(directory, 'clothing-200-db-payload.json'), JSON.stringify(payload, null, 2) + '\n');
  const report = { products: payload.products.length, variants: payload.products.reduce((n,p)=>n+p.variants.length,0),
    image_assets: new Set(payload.products.flatMap(p=>p.images.map(i=>i.assetId))).size,
    variant_images: payload.products.reduce((n,p)=>n+p.variants.length*p.images.length,0), brand: payload.brand.name,
    inventory_on_hand_per_new_variant: 0, image_association: 'SHARED_PRODUCT_GALLERY_NOT_COLOR_SPECIFIC',
    source_sha256: crypto.createHash('sha256').update(bytes).digest('hex'), database_touched: false };
  fs.writeFileSync(path.join(directory, 'clothing-200-db-plan.json'), JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify(report));
}
module.exports = { stableUuid, buildPayload, importSql, literal, directory };
if (require.main === module) main();
