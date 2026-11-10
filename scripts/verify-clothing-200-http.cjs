const fs=require('node:fs');
const path=require('node:path');
const assert=require('node:assert/strict');
const directory=path.resolve(__dirname,'../artifacts/catalog-review/livostyle');
const base='http://127.0.0.1:8080/commerce/api/v1';
async function request(route,status) {
  const response=await fetch(base+route,{signal:AbortSignal.timeout(30000)});
  assert.equal(response.status,status,`Unexpected HTTP status: ${route}`);
  return status===200?response.json():null;
}
async function main() {
  const localizedFile=path.join(directory,'clothing-200-db-payload-vi.json');
  const localized=fs.existsSync(path.join(directory,'vietnamese-database-report.json'))&&fs.existsSync(localizedFile);
  const payload=JSON.parse(fs.readFileSync(localized?localizedFile:path.join(directory,'clothing-200-db-payload.json'),'utf8'));
  const state=JSON.parse(fs.readFileSync(path.join(directory,'database-import-state.json'),'utf8'));
  assert.equal(state.status,'IMPORTED');
  const root=path.resolve(__dirname,'..');
  const backupDir=path.resolve(root,state.backup_directory);
  assert.ok(backupDir.startsWith(path.join(root,'commerce-service/.seed-data/db-backups')+path.sep));
  const before=JSON.parse(fs.readFileSync(path.join(backupDir,'before-snapshot.json'),'utf8'));
  const first=await request('/products?page=0&size=100',200);
  const second=await request('/products?page=1&size=100',200);
  assert.equal(first.total_elements,200); assert.equal(first.total_pages,2);
  const items=[...first.items,...second.items];
  assert.equal(items.length,200); assert.equal(new Set(items.map(p=>p.product_id)).size,200);
  assert.deepEqual(items.map(p=>p.product_id).sort(),payload.products.map(p=>p.id).sort());
  const byId=new Map(payload.products.map(p=>[p.id,p]));
  let variants=0, galleryLinks=0;
  for(const item of items) {
    const expected=byId.get(item.product_id);
    assert.equal(item.name,expected.name); assert.equal(item.description,expected.description); assert.equal(item.brand_name,'Livostyle Catalog');
    assert.equal(item.product_status,'ACTIVE'); assert.equal(item.variants.length,expected.variants.length);
    const bySku=new Map(expected.variants.map(v=>[v.sku,v]));
    for(const v of item.variants) {
      const original=bySku.get(v.sku); assert.ok(original);
      assert.equal(v.variant_id,original.id); assert.equal(v.unit_price_vnd,original.price);
      assert.equal(v.size,original.size); assert.equal(v.color,original.color); assert.equal(v.material,original.material);
      assert.equal(v.unit_price_vnd%1000,0); assert.equal(v.available_quantity,0);
      assert.equal(v.images.length,expected.images.length);
      assert.equal(v.images.filter(i=>i.is_primary).length,1);
      assert.deepEqual([...v.images].sort((a,b)=>a.display_order-b.display_order).map(i=>i.image_url),expected.images.map(i=>i.url));
      assert.deepEqual([...v.images].sort((a,b)=>a.display_order-b.display_order).map(i=>i.alt_text),expected.images.map(i=>i.alt));
      galleryLinks+=v.images.length; variants++;
    }
  }
  const sample=await request('/products/'+payload.products[0].id,200);
  assert.equal(sample.product_id,payload.products[0].id);
  await request('/products/'+before.statuses[0].id,404);
  await request('/products/not-a-uuid',400);
  await request('/products?size=101',400);
  await request('/admin/products/'+payload.products[0].id,401);
  assert.equal(variants,2705); assert.equal(galleryLinks,18865);
  const report={status:'PASS',checked_at:new Date().toISOString(),base_url:base,products:items.length,variants,
    gallery_links:galleryLinks,brand:'Livostyle Catalog',old_product_hidden_404:true,anonymous_admin_denied_401:true,
    invalid_uuid_and_page_size_rejected_400:true,cloudinary_urls_and_rounded_vnd_match:true,new_available_quantity:0,
    localized_vietnamese:localized,runtime:'Node HTTP requests through running Docker Gateway; not Postman/Newman execution'};
  fs.writeFileSync(path.join(directory,'database-http-verification.json'),JSON.stringify(report,null,2)+'\n');
  console.log(JSON.stringify(report));
}
main().catch(()=>{console.error('CATALOG_HTTP_VERIFICATION_FAILED');process.exitCode=1;});
