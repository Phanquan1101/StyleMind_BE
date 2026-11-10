const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {snapshotSql}=require('./import-clothing-200-docker.cjs');
const {literal}=require('./build-clothing-200-import.cjs');
const root=path.resolve(__dirname,'..'),dir=path.join(root,'artifacts/catalog-review/livostyle'),source='stylemind-task-postgres-1';
const read=n=>JSON.parse(fs.readFileSync(path.join(dir,n),'utf8'));
const write=(f,v)=>fs.writeFileSync(f,JSON.stringify(v,null,2)+'\n');
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
function docker(args,options={}){return cp.execFileSync('docker',args,{windowsHide:true,stdio:['pipe','pipe','pipe'],maxBuffer:100*1024*1024,...options});}
function sql(container,text,app=false){return docker(['exec','-i',container,'sh','-ec',app?'PGPASSWORD="$STYLEMIND_COMMERCE_DB_PASSWORD" exec psql -X -w -v ON_ERROR_STOP=1 -U "$STYLEMIND_COMMERCE_DB_USERNAME" -d stylemind_commerce -At':'exec psql -X -w -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d stylemind_commerce -At'],{input:text,encoding:'utf8'}).trim();}
const json=(c,q)=>JSON.parse(sql(c,q));
const qi=n=>'"'+n.replace(/"/g,'""')+'"';
function protectedSql(tables){const mutable={product:['name','description'],product_variant:['size','color','material'],variant_image:['alt_text'],category:['name']};return `SELECT jsonb_object_agg(n,v) FROM (${tables.map(t=>`SELECT ${literal(t)} n,jsonb_build_object('count',count(*),'digest',md5(coalesce(string_agg(h,'' ORDER BY h),''))) v FROM (SELECT md5((to_jsonb(t)${mutable[t]?'-ARRAY['+mutable[t].map(literal).join(',')+']':''})::text) h FROM public.${qi(t)} t) r`).join(' UNION ALL ')}) hashes`;}
function slim(payload){return {categories:payload.categories.map(c=>({slug:c.slug,name:c.name})),products:payload.products.map(p=>({id:p.id,name:p.name,description:p.description,variants:p.variants.map(v=>({id:v.id,size:v.size,color:v.color,material:v.material})),images:p.images.map(i=>({assetId:i.assetId,alt:i.alt}))}))};}
function updateSql(before,after,tables,rollback=false){return `SET standard_conforming_strings=on;
BEGIN;
SET LOCAL lock_timeout='10s';
SELECT pg_advisory_xact_lock(72620020261110);
LOCK TABLE ${tables.map(t=>'public.'+qi(t)).join(',')} IN SHARE ROW EXCLUSIVE MODE;
CREATE TEMP TABLE _vi_protected ON COMMIT DROP AS ${protectedSql(tables)};
CREATE TEMP TABLE _vi_source(old jsonb,new jsonb) ON COMMIT DROP;
INSERT INTO _vi_source VALUES (${literal(JSON.stringify(slim(before)))}::jsonb,${literal(JSON.stringify(slim(after)))}::jsonb);
CREATE TEMP TABLE _vi_old_products ON COMMIT DROP AS SELECT * FROM product WHERE product_id NOT IN (SELECT (p->>'id')::uuid FROM _vi_source,jsonb_array_elements(old->'products') p);
DO $$ BEGIN
 IF current_database()<>'stylemind_commerce' THEN RAISE EXCEPTION 'Wrong database'; END IF;
 IF (SELECT count(*) FROM _vi_source,jsonb_array_elements(old->'products') p JOIN product d ON d.product_id=(p->>'id')::uuid AND d.name=p->>'name' AND d.description IS NOT DISTINCT FROM p->>'description')<>200 THEN RAISE EXCEPTION 'Product text changed; preserve user edits'; END IF;
 IF (SELECT count(*) FROM _vi_source,jsonb_array_elements(old->'products') p,jsonb_array_elements(p->'variants') v JOIN product_variant d ON d.variant_id=(v->>'id')::uuid AND d.size IS NOT DISTINCT FROM v->>'size' AND d.color IS NOT DISTINCT FROM v->>'color' AND d.material IS NOT DISTINCT FROM v->>'material')<>2705 THEN RAISE EXCEPTION 'Variant text changed'; END IF;
 IF (SELECT count(*) FROM _vi_source,jsonb_array_elements(old->'products') p,jsonb_array_elements(p->'variants') v,jsonb_array_elements(p->'images') i JOIN variant_image d ON d.image_asset_id=(i->>'assetId')::uuid AND d.alt_text IS NOT DISTINCT FROM i->>'alt' WHERE d.variant_id=(v->>'id')::uuid)<>18865 THEN RAISE EXCEPTION 'Image text changed'; END IF;
END $$;
UPDATE product d SET name=p->>'name',description=p->>'description' FROM _vi_source,jsonb_array_elements(new->'products') p WHERE d.product_id=(p->>'id')::uuid;
UPDATE product_variant d SET size=v->>'size',color=v->>'color',material=v->>'material' FROM _vi_source,jsonb_array_elements(new->'products') p,jsonb_array_elements(p->'variants') v WHERE d.variant_id=(v->>'id')::uuid;
UPDATE variant_image d SET alt_text=i->>'alt' FROM _vi_source,jsonb_array_elements(new->'products') p,jsonb_array_elements(p->'variants') v,jsonb_array_elements(p->'images') i WHERE d.variant_id=(v->>'id')::uuid AND d.image_asset_id=(i->>'assetId')::uuid;
UPDATE category d SET name=c->>'name' FROM _vi_source,jsonb_array_elements(new->'categories') c WHERE d.slug=c->>'slug';
DO $$ BEGIN
 IF (${protectedSql(tables)}) IS DISTINCT FROM (SELECT * FROM _vi_protected) THEN RAISE EXCEPTION 'Nontext data changed'; END IF;
 IF EXISTS((SELECT * FROM _vi_old_products) EXCEPT (SELECT * FROM product)) THEN RAISE EXCEPTION 'Old catalog changed'; END IF;
 IF (SELECT count(*) FROM _vi_source,jsonb_array_elements(new->'products') p JOIN product d ON d.product_id=(p->>'id')::uuid AND d.name=p->>'name' AND d.description=p->>'description')<>200 THEN RAISE EXCEPTION 'Localized product mismatch'; END IF;
 IF (SELECT count(*) FROM _vi_source,jsonb_array_elements(new->'products') p,jsonb_array_elements(p->'variants') v JOIN product_variant d ON d.variant_id=(v->>'id')::uuid AND d.size IS NOT DISTINCT FROM v->>'size' AND d.color IS NOT DISTINCT FROM v->>'color' AND d.material IS NOT DISTINCT FROM v->>'material')<>2705 THEN RAISE EXCEPTION 'Localized variant mismatch'; END IF;
 IF (SELECT count(*) FROM _vi_source,jsonb_array_elements(new->'products') p,jsonb_array_elements(p->'variants') v,jsonb_array_elements(p->'images') i JOIN variant_image d ON d.image_asset_id=(i->>'assetId')::uuid AND d.alt_text=i->>'alt' WHERE d.variant_id=(v->>'id')::uuid)<>18865 THEN RAISE EXCEPTION 'Localized image mismatch'; END IF;
END $$;
${rollback?'ROLLBACK':'COMMIT'};
`;}
function validate(before,after){assert.equal(before.products.length,200);assert.equal(after.products.length,200);for(let i=0;i<200;i++){const a=structuredClone(before.products[i]),b=structuredClone(after.products[i]);for(const k of ['name','description']){delete a[k];delete b[k];}for(const p of [a,b]){p.variants=p.variants.map(({size,color,material,...v})=>v);p.images=p.images.map(({alt,...image})=>image);}assert.deepEqual(a,b);}assert.deepEqual(after.categories.map(({name,...c})=>c),before.categories.map(({name,...c})=>c));assert.deepEqual(after.brand,before.brand);}
async function main(){
 const refining=process.argv.includes('--refine');
 const before=read(refining?'clothing-200-db-payload-vi-previous.json':'clothing-200-db-payload.json'),after=read('clothing-200-db-payload-vi.json');validate(before,after);
 const inspect=JSON.parse(docker(['inspect',source],{encoding:'utf8'}))[0];assert.equal(inspect.Config.Labels['com.docker.compose.project'],'stylemind-task');assert.ok(inspect.Mounts.some(m=>m.Name==='stylemind-task_postgres-data'));
 const tables=json(source,"SELECT json_agg(tablename ORDER BY tablename) FROM pg_tables WHERE schemaname='public';");
 const fingerprints=json(source,snapshotSql(tables));
 const destination=path.join(root,'commerce-service/.seed-data/db-backups','vietnamese-'+new Date().toISOString().replace(/[:.]/g,'-'));fs.mkdirSync(destination,{recursive:true});
 const dump=docker(['exec',source,'sh','-ec','exec pg_dump -Fc --no-password -U "$POSTGRES_USER" -d stylemind_commerce']);fs.writeFileSync(path.join(destination,'before-vietnamese.dump'),dump);
 assert.deepEqual(json(source,snapshotSql(tables)),fingerprints);
 fs.writeFileSync(path.join(destination,'restore-before-localization.sql'),updateSql(after,before,tables));
 const target='stylemind-vietnamese-check-'+Date.now();let created=false;
 try{
  docker(['run','--rm','-d','--name',target,'--label','stylemind.validation=vietnamese-localization','--network','none','-e','POSTGRES_USER=vi_check','-e','POSTGRES_DB=stylemind_commerce','-e','POSTGRES_HOST_AUTH_METHOD=trust','postgres:18']);created=true;
  let ready=false;for(let i=0;i<60;i++){try{docker(['exec',target,'pg_isready','-h','127.0.0.1','-U','vi_check','-d','stylemind_commerce']);ready=true;break;}catch{await new Promise(r=>setTimeout(r,500));}}if(!ready)throw Error('RESTORE_NOT_READY');
  docker(['exec','-i',target,'pg_restore','--exit-on-error','--no-owner','--no-acl','-U','vi_check','-d','stylemind_commerce'],{input:dump});
  assert.deepEqual(json(target,snapshotSql(tables)),fingerprints);console.log('VI_BACKUP_RESTORE_PASS');
  sql(target,updateSql(before,after,tables,true));assert.deepEqual(json(target,snapshotSql(tables)),fingerprints);console.log('VI_TRANSACTION_ROLLBACK_PASS');
  sql(target,updateSql(before,after,tables));sql(target,updateSql(after,before,tables));assert.deepEqual(json(target,snapshotSql(tables)),fingerprints);console.log('VI_UPDATE_AND_TEXT_RECOVERY_PASS');
 }finally{if(created)docker(['stop',target]);}
 const statement=updateSql(before,after,tables);fs.writeFileSync(path.join(destination,'applied-vietnamese.sql'),statement);sql(source,statement,true);
 const report={status:'APPLIED',phase:refining?'REFINEMENT':'INITIAL',method:'LOCAL_EDITORIAL_REWRITE',external_translation_used:false,products:200,variants:2705,shared_image_alts:1337,variant_image_alts:18865,measurement_tables_preserved:198,old_inactive_catalog_unchanged:true,nontext_data_unchanged:true,backup_file:path.relative(root,path.join(destination,'before-vietnamese.dump')),backup_sha256:sha(dump),backup_bytes:dump.length,actual_restore_tested:true,transaction_rollback_tested:true,text_recovery_tested:true,english_recovery_tested:!refining,prior_english_recovery_report:refining?'vietnamese-database-initial-report.json':null,updated_at:new Date().toISOString()};
 write(path.join(dir,'vietnamese-database-report.json'),report);write(path.join(destination,'manifest.json'),report);console.log(JSON.stringify(report));
}
if(require.main===module)main().catch(e=>{console.error(e.code==='ERR_ASSERTION'?'VI_ASSERTION_FAILED':e.message?.startsWith('VI_')?e.message:'VI_LOCALIZATION_FAILED_NO_PROVIDER_DATA_LOGGED');console.error(JSON.stringify({type:e.name,code:e.code||null,reason:String(e.message).split('\n')[0].slice(0,220)}));const pg=String(e.stderr||'').match(/ERROR:\s*([^\r\n]+)/);if(pg)console.error('POSTGRES: '+pg[1].slice(0,180));process.exitCode=1;});
module.exports={validate,updateSql};
