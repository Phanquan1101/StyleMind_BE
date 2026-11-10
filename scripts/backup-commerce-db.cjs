// Local PostgreSQL backup helper. Default mode only checks connectivity.
// No credentials or database contents are printed. Backups are Git-ignored.
const fs = require('node:fs');
const path = require('node:path');
const cp = require('node:child_process');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '..');
function readEnvFile(name) {
  const file = path.join(root, name);
  const values = {};
  if (!fs.existsSync(file)) return values;
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const match = line.match(/^\s*([A-Z][A-Z0-9_]*)\s*=\s*(.*?)\s*$/);
    if (match) values[match[1]] = match[2].replace(/^(['"])(.*)\1$/, '$2');
  }
  return values;
}
function settings() {
  const env = { ...readEnvFile('.env'), ...readEnvFile('.env.catalog-db'), ...process.env };
  return {
    host: env.STYLEMIND_CATALOG_DB_HOST || '', port: env.STYLEMIND_CATALOG_DB_PORT || '5432',
    database: 'stylemind_commerce', username: env.STYLEMIND_CATALOG_DB_USERNAME || env.STYLEMIND_COMMERCE_DB_USERNAME,
    password: env.STYLEMIND_CATALOG_DB_PASSWORD || env.STYLEMIND_COMMERCE_DB_PASSWORD,
    bin: env.STYLEMIND_CATALOG_PG_BIN || 'C:\\Program Files\\PostgreSQL\\18\\bin'
  };
}
function postgres(config, executable, args) {
  if (!['127.0.0.1', 'localhost', '::1'].includes(config.host) || !/^\d{1,5}$/.test(config.port)
      || !config.username || !config.password) throw Error('LOCAL_DATABASE_CONFIG_REQUIRED');
  try {
    return cp.execFileSync(path.join(config.bin, executable + '.exe'), args, {
      env: { ...process.env, PGHOST: config.host, PGPORT: config.port, PGDATABASE: config.database,
        PGUSER: config.username, PGPASSWORD: config.password, PGCONNECT_TIMEOUT: '5', PGCLIENTENCODING: 'UTF8' },
      encoding: 'utf8', windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'], maxBuffer: 32 * 1024 * 1024
    });
  } catch (e) {
    const stderr = String(e.stderr || '');
    if (/password authentication failed/i.test(stderr)) throw Error('DATABASE_AUTHENTICATION_FAILED');
    if (/does not exist/i.test(stderr)) throw Error('DATABASE_OR_ROLE_NOT_FOUND');
    throw Error('POSTGRES_COMMAND_FAILED_NO_DATABASE_CONTENT_LOGGED');
  }
}
function query(config, sql) {
  return postgres(config, 'psql', ['-X', '-w', '-v', 'ON_ERROR_STOP=1', '-At', '-c', sql]).trim();
}
function backup(config) {
  const identity = JSON.parse(query(config, "SELECT json_build_object('database',current_database(),'server_address',inet_server_addr(),'server_port',inet_server_port(),'version',current_setting('server_version'));"));
  if (identity.database !== 'stylemind_commerce') throw Error('WRONG_DATABASE');
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
  const destination = path.join(root, 'commerce-service/.seed-data/db-backups', timestamp);
  fs.mkdirSync(destination, { recursive: true });
  const dump = path.join(destination, 'stylemind_commerce-before-catalog.dump');
  // pg_dump takes its own consistent snapshot. No catalog mutation is performed.
  postgres(config, 'pg_dump', ['-w', '--format=custom', '--file', dump, '--dbname', config.database]);
  const listing = postgres(config, 'pg_restore', ['--list', dump]);
  if (!listing.includes('public product ') || !listing.includes('public product_variant ')) throw Error('BACKUP_CATALOG_TABLES_MISSING');
  fs.writeFileSync(path.join(destination, 'restore-list.txt'), listing);
  const bytes = fs.readFileSync(dump);
  if (bytes.length === 0) throw Error('EMPTY_BACKUP');
  const manifest = {
    status: 'DUMP_CREATED_RESTORE_NOT_YET_REHEARSED', source: identity, created_at: new Date().toISOString(),
    filename: path.basename(dump), bytes: bytes.length, sha256: crypto.createHash('sha256').update(bytes).digest('hex'),
    archive_listing_validated: true, actual_restore_tested: false, database_data_modified: false,
    note: 'Full Commerce database backup. Contains private business/customer data; keep ignored and local.'
  };
  fs.writeFileSync(path.join(destination, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  return { ...manifest, backup_directory: path.relative(root, destination) };
}
function main() {
  const args = process.argv.slice(2);
  if (args.some(a => a !== '--backup' && a !== '--check')) throw Error('INVALID_ARGUMENT');
  const config = settings();
  if (args.includes('--backup')) console.log(JSON.stringify(backup(config)));
  else console.log(JSON.stringify(JSON.parse(query(config, "SELECT json_build_object('status','CONNECTED','database',current_database(),'server_port',inet_server_port(),'product_table_present',to_regclass('public.product') IS NOT NULL);"))));
}
module.exports = { readEnvFile, settings, postgres, query, backup };
if (require.main === module) {
  try { main(); } catch (e) { console.error(/^[A-Z][A-Z0-9_]+$/.test(e.message) ? e.message : 'BACKUP_FAILED'); process.exitCode = 1; }
}
