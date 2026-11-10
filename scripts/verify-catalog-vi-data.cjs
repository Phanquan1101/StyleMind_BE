const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const dir=path.resolve(__dirname,'../artifacts/catalog-review/livostyle');
const read=n=>JSON.parse(fs.readFileSync(path.join(dir,n),'utf8'));
const src=read('clothing-200-cloudinary-vnd.json'),out=read('clothing-200-db-payload-vi.json');
const plain=s=>s.replace(/<[^>]*>/g,' ').replace(/&nbsp;|&#160;/g,' ').replace(/&amp;/g,'&').replace(/&quot;/g,'"').replace(/&#39;|&apos;/g,"'").replace(/&#(\d+);/g,(_,n)=>String.fromCodePoint(+n)).replace(/\s+/g,' ').trim();
let tables=0,cells=0;
for(let i=0;i<200;i++){
 const h=[src[i].metafields?.supplier_description,src[i].description_html].filter(Boolean).find(h=>/<table\b/i.test(h));
 if(!h)continue;
 const expected=[...h.matchAll(/<td\b[^>]*>([\s\S]*?)<\/td>/gi)].map(m=>plain(m[1])).map(x=>x.replace(/\bone size\b/gi,'Một cỡ').replace(/\bplus size\b/gi,'cỡ lớn').replace(/\bin\b/g,'inch'));
 const table=out.products[i].description.match(/Thông số kích thước[^\n]*:\n([\s\S]*?)(?:\n\n|$)/u);
 assert.ok(table,'Missing table '+i);
 const actual=table[1].split('\n').filter(l=>!/^Cỡ(?: chữ| Hoa Kỳ)? \|/.test(l)).flatMap(l=>l.split('|').map(x=>x.trim()));
 assert.deepEqual(actual,expected,'Measurement cells '+i);tables++;cells+=expected.length;
}
const leftovers=out.products.filter(p=>/\b(Why|Fabric|Wear|Sizing|Measurements|Length|Bust|Waist|Size|Machine wash|Front view)\b/i.test(p.description));
assert.equal(tables,198);assert.equal(leftovers.length,0);
require('./apply-catalog-vietnamese.cjs').validate(read('clothing-200-db-payload.json'),out);
const original=read('clothing-200-db-payload.json');
for(let n=0;n<200;n++){
 const colors=new Map();
 original.products[n].variants.forEach((v,i)=>{const label=out.products[n].variants[i].color;if(!colors.has(label))colors.set(label,new Set());colors.get(label).add(v.color);});
 for(const sources of colors.values())assert.equal(sources.size,1,'Distinct color choices collapsed');
}
for(const p of out.products){assert.ok([...p.name].length<=160);assert.ok([...p.description].length<=2000);for(const v of p.variants)for(const k of ['size','color','material'])assert.ok([...(v[k]||'')].length<=64);for(const i of p.images)assert.ok([...i.alt].length<=160);}
console.log(JSON.stringify({status:'PASS',products:out.products.length,measurement_tables:tables,original_measurement_cells_preserved:cells,english_prose_remaining:0,nontext_payload_preserved:true}));
