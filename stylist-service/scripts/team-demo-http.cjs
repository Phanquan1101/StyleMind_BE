'use strict';
// Loopback-only JWKS for offline verification. Not a replacement for Identity login.
const fs = require('node:fs');
const http = require('node:http');
const crypto = require('node:crypto');
const [stateDir, port] = process.argv.slice(2);
const path = require('node:path');
const keyFile = path.join(stateDir, 'offline-jwt-private.pem');
if (!fs.existsSync(keyFile)) {
  const pair = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
  fs.writeFileSync(keyFile, pair.privateKey.export({ type: 'pkcs8', format: 'pem' }), { mode: 0o600 });
}
const key = crypto.createPrivateKey(fs.readFileSync(keyFile));
const jwk = crypto.createPublicKey(key).export({ format: 'jwk' });
Object.assign(jwk, { kid: 'team-demo-offline-only', alg: 'RS256', use: 'sig' });
http.createServer((req, res) => {
  const body = req.url === '/.well-known/jwks.json' ? { keys: [jwk] } : req.url === '/healthz' ? { status: 'UP' } : null;
  res.writeHead(body ? 200 : 404, { 'content-type': 'application/json' });
  res.end(JSON.stringify(body || { error: 'NOT_FOUND' }));
}).listen(Number(port), '127.0.0.1', () => console.log('Offline JWKS ready'));
