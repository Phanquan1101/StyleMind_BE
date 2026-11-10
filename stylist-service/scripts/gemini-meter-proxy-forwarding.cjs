'use strict';

const OMIT_HOP_BY_HOP_HEADERS = new Set([
  'host', 'connection', 'proxy-connection', 'content-length', 'transfer-encoding'
]);

function buildUpstreamForward(request, url, host, body, signal) {
  const headers = new Headers();
  for (const [name, value] of Object.entries(request.headers)) {
    if (OMIT_HOP_BY_HOP_HEADERS.has(name.toLowerCase())) continue;
    if (Array.isArray(value)) headers.set(name, value.join(', '));
    else if (value != null) headers.set(name, value);
  }
  return {
    url: `https://${host}${url.pathname}${url.search}`,
    options: {
      method: request.method,
      headers,
      // readBody() returns the original bytes as a Buffer. Do not parse/stringify JSON here.
      body: request.method === 'GET' || request.method === 'HEAD' ? undefined : body,
      redirect: 'error',
      signal
    }
  };
}

module.exports = { buildUpstreamForward };
