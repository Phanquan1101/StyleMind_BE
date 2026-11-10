const fs = require('node:fs');
const path = require('node:path');
const cp = require('node:child_process');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const { importSql, literal, directory } = require('./build-clothing-200-import.cjs');
const root = path.resolve(__dirname, '..');
const sourceContainer = 'stylemind-task-postgres-1';
const stateFile = path.join(directory, 'database-import-state.json');
const catalog = { category:'category_id', brand:'brand_id', product:'product_id', product_variant:'variant_id',
  variant_image:'image_id', image_asset:'image_asset_id', variant_inventory:'variant_id' };
const quote = name => '"' + name.replace(/"/g, '""') + '"';
const hash = data => crypto.createHash('sha256').update(data).digest('hex');
function docker(args, options = {}) {
  try { return cp.execFileSync('docker', args, { windowsHide:true, stdio:['pipe','pipe','pipe'], maxBuffer:512*1024*1024, ...options }); }
  catch(e) {
    const state = String(e.stderr || '').match(/ERROR:\s+([A-Z0-9]{5})/);
    throw Error(state ? 'DATABASE_SQLSTATE_' + state[1] : 'DOCKER_DATABASE_COMMAND_FAILED');
  }
}
function sql(container, statement, asApp = false) {
  const command = asApp
    ? 'PGPASSWORD="$STYLEMIND_COMMERCE_DB_PASSWORD" exec psql -X -w -v ON_ERROR_STOP=1 -v VERBOSITY=sqlstate -U "$STYLEMIND_COMMERCE_DB_USERNAME" -d stylemind_commerce -At'
    : 'exec psql -X -w -v ON_ERROR_STOP=1 -v VERBOSITY=sqlstate -U "$POSTGRES_USER" -d stylemind_commerce -At';
  return docker(['exec','-i',container,'sh','-ec',command], { input:statement, encoding:'utf8' }).trim();
}
const jsonSql = (container, statement) => JSON.parse(sql(container, statement));
function snapshotSql(tables, ids, excludeProductStatus = false) {
  const selects = tables.map(table => {
    const row = excludeProductStatus && table === 'product' ? "to_jsonb(t)-'product_status'" : 'to_jsonb(t)';
    const where = ids ? `WHERE ${quote(catalog[table])} IN (${ids[table].map(id => literal(id)+'::uuid').join(',') || 'NULL'})` : '';
    return `SELECT ${literal(table)} AS name,jsonb_build_object('count',count(*),'digest',md5(coalesce(string_agg(h,'' ORDER BY h),''))) AS value FROM (SELECT md5((${row})::text) h FROM public.${quote(table)} t ${where}) s`;
  });
  return `SELECT jsonb_object_agg(name,value) FROM (${selects.join(' UNION ALL ')}) hashes`;
}
function guardEquals(query, expected, message) {
  return `DO $$ BEGIN IF (${query}) IS DISTINCT FROM ${literal(JSON.stringify(expected))}::jsonb THEN RAISE EXCEPTION ${literal(message)}; END IF; END $$;`;
}
function write(file, value) { fs.writeFileSync(file, JSON.stringify(value,null,2)+'\n'); }
function sourceCheck() {
  const inspect = JSON.parse(docker(['inspect',sourceContainer],{encoding:'utf8'}))[0];
  assert.equal(inspect.Config.Labels['com.docker.compose.project'],'stylemind-task');
  assert.equal(inspect.Config.Labels['com.docker.compose.service'],'postgres');
  assert.ok(inspect.Mounts.some(m=>m.Name==='stylemind-task_postgres-data'));
  assert.equal(inspect.State.Running,true);
  const service = JSON.parse(docker(['inspect','stylemind-task-commerce-service-1'],{encoding:'utf8'}))[0];
  if (service.State.Running) throw Error('STOP_COMMERCE_WRITER_BEFORE_IMPORT');
  return { container:sourceContainer, database:'stylemind_commerce', volume:'stylemind-task_postgres-data' };
}
function dumpBackup(destination) {
  fs.mkdirSync(destination,{recursive:true});
  const bytes = docker(['exec',sourceContainer,'sh','-ec','exec pg_dump -Fc --no-password -U "$POSTGRES_USER" -d stylemind_commerce']);
  const file = path.join(destination,'stylemind_commerce-before-catalog.dump');
  fs.writeFileSync(file,bytes,{flag:'wx'});
  const listing = docker(['exec','-i',sourceContainer,'pg_restore','--list'],{input:bytes,encoding:'utf8'});
  assert.ok(listing.includes('public product ') && listing.includes('public order_item '));
  fs.writeFileSync(path.join(destination,'restore-list.txt'),listing);
  return { file:path.relative(root,file), bytes:bytes.length, sha256:hash(bytes), dump:bytes };
}
function capture(container) {
  const tables = jsonSql(container,"SELECT json_agg(tablename ORDER BY tablename) FROM pg_tables WHERE schemaname='public';");
  for (const table of Object.keys(catalog)) assert.ok(tables.includes(table));
  const fingerprints = jsonSql(container,snapshotSql(tables));
  const ids = jsonSql(container,`SELECT json_build_object(${Object.entries(catalog).map(([t,id])=>`${literal(t)},(SELECT coalesce(json_agg(${quote(id)}),'[]') FROM public.${quote(t)})`).join(',')});`);
  const statuses = jsonSql(container,"SELECT json_agg(json_build_object('id',product_id,'status',product_status) ORDER BY product_id) FROM public.product;");
  const oldCatalog = jsonSql(container,snapshotSql(Object.keys(catalog),ids,true));
  return { tables,fingerprints,ids,statuses,oldCatalog };
}
function buildProtectedSql(payload,before,rollback = false) {
  const unchangedTables = before.tables.filter(t=>!Object.hasOwn(catalog,t));
  const unchanged = Object.fromEntries(unchangedTables.map(t=>[t,before.fingerprints[t]]));
  const productIds = payload.products.map(p=>literal(p.id)+'::uuid').join(',');
  const assertions = guardEquals(snapshotSql(unchangedTables),unchanged,'Noncatalog rows changed') + '\n'
    + guardEquals(snapshotSql(Object.keys(catalog),before.ids,true),before.oldCatalog,'Existing catalog rows changed') + `
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM public.product WHERE product_id NOT IN (${productIds}) AND product_status<>'INACTIVE') THEN RAISE EXCEPTION 'Old products still visible'; END IF;
 IF (SELECT count(*) FROM public.image_asset WHERE image_asset_id IN (SELECT DISTINCT (i->>'assetId')::uuid FROM _livo_products,jsonb_array_elements(p->'images') i))<>1337 THEN RAISE EXCEPTION 'Image asset total'; END IF;
 IF (SELECT count(*) FROM public.variant_image WHERE variant_id IN (SELECT (v->>'id')::uuid FROM _livo_products,jsonb_array_elements(p->'variants') v))<>18865 THEN RAISE EXCEPTION 'Gallery link total'; END IF;
 IF (SELECT count(*) FROM public.variant_inventory WHERE variant_id IN (SELECT (v->>'id')::uuid FROM _livo_products,jsonb_array_elements(p->'variants') v) AND on_hand_quantity=0 AND reserved_quantity=0)<>2705 THEN RAISE EXCEPTION 'New inventory defaults'; END IF;
END $$;`;
  return importSql(payload,{rollback, locks:`LOCK TABLE ${before.tables.map(t=>'public.'+quote(t)).join(',')} IN SHARE ROW EXCLUSIVE MODE;`,
    guard:guardEquals(snapshotSql(before.tables),before.fingerprints,'Database changed since verified backup'), assertions });
}
function recoverySql(payload,before) {
  return `SET standard_conforming_strings=on;\nBEGIN;\nSET LOCAL lock_timeout='10s';\nSELECT pg_advisory_xact_lock(72620020261110);\nLOCK TABLE public.product IN SHARE ROW EXCLUSIVE MODE;\nUPDATE public.product SET product_status='INACTIVE' WHERE product_id IN (${payload.products.map(p=>literal(p.id)+'::uuid').join(',')});\nUPDATE public.product p SET product_status=s.status FROM jsonb_to_recordset(${literal(JSON.stringify(before.statuses))}::jsonb) AS s(id uuid,status text) WHERE p.product_id=s.id;\nCOMMIT;\n`;
}
async function rehearse(payload, payloadHash) {
  const identity = sourceCheck();
  const before = capture(sourceContainer);
  assert.equal(before.fingerprints.product.count,381);
  const stamp = new Date().toISOString().replace(/[:.]/g,'-');
  const destination = path.join(root,'commerce-service/.seed-data/db-backups',stamp);
  const backup = dumpBackup(destination);
  write(path.join(destination,'before-snapshot.json'),before);
  fs.writeFileSync(path.join(destination,'recover-old-catalog.sql'),recoverySql(payload,before));
  const restoreContainer = `stylemind-catalog-restore-${Date.now()}`;
  let created = false;
  try {
    docker(['run','--rm','-d','--name',restoreContainer,'--label','stylemind.validation=catalog-restore','--network','none',
      '-e','POSTGRES_USER=catalog_restore','-e','POSTGRES_DB=stylemind_commerce','-e','POSTGRES_HOST_AUTH_METHOD=trust','postgres:18']);
    created = true;
    let ready = false;
    for(let i=0;i<60;i++) {
      try { docker(['exec',restoreContainer,'pg_isready','-U','catalog_restore','-d','stylemind_commerce']); ready = true; break; }
      catch { await new Promise(r=>setTimeout(r,500)); }
    }
    if (!ready) throw Error('RESTORE_DATABASE_NOT_READY');
    docker(['exec','-i',restoreContainer,'pg_restore','--exit-on-error','--no-owner','--no-acl','-U','catalog_restore','-d','stylemind_commerce'],{input:backup.dump});
    const restored = capture(restoreContainer);
    assert.deepEqual(restored.fingerprints,before.fingerprints);
    console.log('BACKUP_RESTORE_ALL_TABLE_FINGERPRINTS_PASS');
    sql(restoreContainer,buildProtectedSql(payload,before,true));
    assert.deepEqual(capture(restoreContainer).fingerprints,before.fingerprints);
    console.log('IMPORT_TRANSACTION_ROLLBACK_PASS');
    sql(restoreContainer,buildProtectedSql(payload,before));
    console.log('IMPORT_REHEARSAL_COMMIT_PASS');
    sql(restoreContainer,recoverySql(payload,before));
    const activeOld = Number(sql(restoreContainer,"SELECT count(*) FROM product WHERE product_status='ACTIVE';"));
    assert.equal(activeOld,before.statuses.filter(s=>s.status==='ACTIVE').length);
    assert.deepEqual(jsonSql(restoreContainer,snapshotSql(Object.keys(catalog),before.ids,true)),before.oldCatalog);
    console.log('OLD_CATALOG_RECOVERY_PASS');
    const manifest = { status:'BACKUP_RESTORE_AND_IMPORT_REHEARSAL_PASSED',source:identity,backup_file:backup.file,
      backup_sha256:backup.sha256,backup_bytes:backup.bytes,actual_restore_tested:true,all_table_fingerprints_equal:true,
      transaction_rollback_tested:true,old_catalog_recovery_tested:true,payload_sha256:payloadHash,
      backup_directory:path.relative(root,destination),created_at:new Date().toISOString(),live_database_modified:false };
    write(path.join(destination,'manifest.json'),manifest); write(stateFile,manifest);
    console.log(JSON.stringify(manifest));
  } finally {
    if(created) {
      const owned = JSON.parse(docker(['inspect',restoreContainer],{encoding:'utf8'}))[0];
      assert.equal(owned.Config.Labels['stylemind.validation'],'catalog-restore');
      docker(['stop',restoreContainer]);
    }
  }
}
function apply(payload,payloadHash) {
  sourceCheck();
  const state = JSON.parse(fs.readFileSync(stateFile,'utf8'));
  assert.equal(state.status,'BACKUP_RESTORE_AND_IMPORT_REHEARSAL_PASSED'); assert.equal(state.payload_sha256,payloadHash);
  const destination = path.resolve(root,state.backup_directory);
  const allowed = path.join(root,'commerce-service/.seed-data/db-backups')+path.sep;
  if(!destination.startsWith(allowed)) throw Error('INVALID_BACKUP_PATH');
  const dump = fs.readFileSync(path.join(destination,'stylemind_commerce-before-catalog.dump'));
  assert.equal(hash(dump),state.backup_sha256);
  const before = JSON.parse(fs.readFileSync(path.join(destination,'before-snapshot.json'),'utf8'));
  assert.deepEqual(capture(sourceContainer).fingerprints,before.fingerprints);
  const statement = buildProtectedSql(payload,before);
  fs.writeFileSync(path.join(destination,'applied-import.sql'),statement);
  sql(sourceContainer,statement,true);
  state.status='IMPORTED'; state.live_database_modified=true; state.imported_at=new Date().toISOString();
  state.result=jsonSql(sourceContainer,"SELECT json_build_object('active_products',(SELECT count(*) FROM product WHERE product_status='ACTIVE'),'inactive_products',(SELECT count(*) FROM product WHERE product_status='INACTIVE'),'livostyle_variants',(SELECT count(*) FROM product_variant v JOIN product p USING(product_id) JOIN brand b USING(brand_id) WHERE b.slug='livostyle-catalog'),'orders',(SELECT count(*) FROM customer_order),'cart_items',(SELECT count(*) FROM cart_item));");
  write(stateFile,state); write(path.join(destination,'manifest.json'),state);
  console.log(JSON.stringify(state));
}
function recover(payload,payloadHash) {
  sourceCheck();
  const state=JSON.parse(fs.readFileSync(stateFile,'utf8'));
  assert.equal(state.status,'IMPORTED'); assert.equal(state.payload_sha256,payloadHash);
  const destination=path.resolve(root,state.backup_directory);
  if(!destination.startsWith(path.join(root,'commerce-service/.seed-data/db-backups')+path.sep)) throw Error('INVALID_BACKUP_PATH');
  assert.equal(hash(fs.readFileSync(path.join(destination,'stylemind_commerce-before-catalog.dump'))),state.backup_sha256);
  const before=JSON.parse(fs.readFileSync(path.join(destination,'before-snapshot.json'),'utf8'));
  sql(sourceContainer,recoverySql(payload,before),true);
  const expected=before.statuses.filter(s=>s.status==='ACTIVE').length;
  assert.equal(Number(sql(sourceContainer,"SELECT count(*) FROM product WHERE product_status='ACTIVE';")),expected);
  state.status='RECOVERED_OLD_CATALOG'; state.recovered_at=new Date().toISOString();
  write(stateFile,state); write(path.join(destination,'manifest.json'),state);
  console.log(JSON.stringify({status:state.status,original_active_products:expected,new_products_retired:200,rows_deleted:0}));
}
async function main() {
  const bytes = fs.readFileSync(path.join(directory,'clothing-200-db-payload.json'));
  const payload = JSON.parse(bytes.toString('utf8'));
  if(process.argv[2]==='--rehearse') await rehearse(payload,hash(bytes));
  else if(process.argv[2]==='--apply') apply(payload,hash(bytes));
  else if(process.argv[2]==='--recover-old-catalog') recover(payload,hash(bytes));
  else throw Error('USE_REHEARSE_APPLY_OR_RECOVER');
}
module.exports={snapshotSql,guardEquals,buildProtectedSql,recoverySql};
if(require.main===module)main().catch(e=>{console.error(/^[A-Z][A-Z0-9_]+$/.test(e.message)?e.message:'IMPORT_GUARD_OR_VALIDATION_FAILED');process.exitCode=1;});
