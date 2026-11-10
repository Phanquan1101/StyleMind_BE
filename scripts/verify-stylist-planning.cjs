#!/usr/bin/env node
'use strict';

// Read-only documentation guard. It never starts an app, test container or model call.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const cp = require('node:child_process');
const root = path.resolve(__dirname, '..');
const relativeManifest = 'docs/planning/stylist/EP-07_EXECUTION_MANIFEST.json';
const args = process.argv.slice(2);
const failures = [];
const warnings = [];
const fail = message => failures.push(message);
const hash = data => crypto.createHash('sha256').update(data).digest('hex');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8').replace(/^\uFEFF/, '');
const git = (...a) => cp.execFileSync('git', a, { cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();

if (args.length && !(args.length === 2 && args[0] === '--phase')) {
  console.error('Usage: node scripts/verify-stylist-planning.cjs [--phase P01..P10]');
  process.exit(1);
}

let m;
try { m = JSON.parse(read(relativeManifest)); }
catch (e) { console.error(`Invalid execution manifest: ${e.message}`); process.exit(1); }
if (m.schema_version !== 1 || m.epic !== 'EP-07') fail('Unknown manifest schema or Epic');
if (!['DOCUMENTATION_AND_HARNESS_ONLY', 'SCOPED_IMPLEMENTATION'].includes(m.authorization_scope)) fail('Unknown authorization scope');
if (!Array.isArray(m.phases) || m.phases.length !== 10) fail('Expected ten planning phases');
if (!Array.isArray(m.tasks) || m.tasks.length !== 10) fail('Expected ten source tasks');
const phaseIds = new Set();
for (const p of m.phases || []) {
  if (!/^P(?:0[1-9]|10)$/.test(p.id) || phaseIds.has(p.id)) fail(`Invalid/duplicate phase ${p.id}`);
  phaseIds.add(p.id);
  for (const row of p.task_rows || []) if (!(m.tasks || []).some(t => t.row === row)) fail(`${p.id}: unknown task row ${row}`);
  for (const key of p.required_approvals || []) if (!m.approvals[key]) fail(`${p.id}: unknown approval ${key}`);
  for (const id of p.depends_on || []) if (!(m.phases || []).some(other => other.id === id)) fail(`${p.id}: unknown dependency ${id}`);
  if (p.status === 'VERIFIED' && !p.completion_evidence) fail(`${p.id}: VERIFIED without completion evidence`);
}
const taskRows = new Set();
for (const t of m.tasks || []) {
  if (!Number.isInteger(t.row) || taskRows.has(t.row) || !['US-07.01', 'US-07.02'].includes(t.us) || !t.task) fail('Invalid or duplicate source task');
  taskRows.add(t.row);
  if (!read('docs/planning/stylist/EP-07_IMPLEMENTATION_PLAN.md').includes(t.task)) fail(`Task R${t.row} differs from plan`);
}
for (const [key, approval] of Object.entries(m.approvals || {})) {
  if (!['NOT_APPROVED', 'APPROVED'].includes(approval.status)) fail(`Unknown approval status ${key}`);
  if (approval.status === 'APPROVED' && (!approval.owner_evidence || !approval.approved_scope || !approval.recorded_at)) fail(`${key}: approval lacks owner evidence, scope or time`);
}

let branch = '';
try {
  branch = git('branch', '--show-current');
  if (!branch || ['dev', 'main', 'master'].includes(branch)) fail('Use a dedicated AI branch, not detached/dev/main/master');
  else if (branch !== m.git.work_branch) warnings.push(`Branch ${branch} differs from reviewed ${m.git.work_branch}; verify owner branch instruction`);
  cp.execFileSync('git', ['merge-base', '--is-ancestor', m.git.reviewed_remote_dev, 'HEAD'], { cwd: root, stdio: 'pipe' });
  const drift = git('diff', '--name-only', m.git.reviewed_head, 'HEAD').split(/\r?\n/).filter(Boolean)
    .filter(p => /^(stylist-service|identity-service|commerce-service|wardrobe-community-service|api-gateway|database)\//.test(p) && !/AGENTS\.md$/.test(p));
  if (drift.length) fail(`Relevant committed source changed since review: ${drift.slice(0, 8).join(', ')}. Re-review before refreshing fingerprints`);
} catch (e) { fail('Git review baseline is unavailable or not integrated into HEAD'); }

let sources = 0;
for (const s of m.source_fingerprints || []) {
  const full = path.resolve(root, s.path);
  if (!full.startsWith(root + path.sep) || !fs.existsSync(full)) { fail(`Missing/outside source: ${s.path}`); continue; }
  if (hash(fs.readFileSync(full)) !== s.sha256) fail(`Source drift: ${s.path}. Inspect change; do not blindly accept a new hash`);
  sources++;
}
if (!sources) fail('No source fingerprints recorded');

for (const asset of m.rendered_assets || []) {
  if (!fs.existsSync(path.join(root, asset.path))) { fail(`Missing rendered asset: ${asset.path}`); continue; }
  const blocks = [...read(asset.source).matchAll(/```mermaid\s*\r?\n([\s\S]*?)```/g)];
  const source = blocks[asset.block_index - 1];
  if (!source || hash(Buffer.from(source[1].replace(/\r\n/g, '\n').trim(), 'utf8')) !== asset.source_block_sha256) fail(`Diagram source changed: re-render ${asset.path} and record reviewed hashes`);
  if (hash(fs.readFileSync(path.join(root, asset.path))) !== asset.svg_sha256) fail(`Rendered SVG differs from reviewed output: ${asset.path}`);
}

// Workbook bytes are checked without opening Excel or rewriting the source workbook.
if (!fs.existsSync(m.workbook.path)) warnings.push('Workbook unavailable: cannot freshly verify current backlog');
else if (hash(fs.readFileSync(m.workbook.path)) !== m.workbook.sha256) fail('Workbook changed: re-read the requested rows and reconcile before updating its hash');

let links = 0;
let jsonExamples = 0;
let mermaidBlocks = 0;
for (const relative of m.required_documents || []) {
  const full = path.join(root, relative);
  if (!fs.existsSync(full)) { fail(`Missing required document: ${relative}`); continue; }
  const text = read(relative);
  if (text.split(/\r?\n/).some(line => /[\t ]+$/.test(line))) fail(`Trailing whitespace: ${relative}`);
  const fences = [...text.matchAll(/^(`{3,}|~{3,})([^\r\n]*)$/gm)];
  if (fences.length % 2) fail(`Unbalanced Markdown fences: ${relative}`);
  for (const match of text.matchAll(/(?:```|~~~)json\s*\r?\n([\s\S]*?)(?:```|~~~)/g)) {
    try { JSON.parse(match[1]); jsonExamples++; } catch (e) { fail(`Invalid JSON example in ${relative}: ${e.message}`); }
  }
  mermaidBlocks += [...text.matchAll(/^```mermaid\s*$/gm)].length;
  for (const match of text.matchAll(/\[[^\]\n]+\]\(([^)\n]+)\)/g)) {
    const target = match[1].replace(/^<|>$/g, '');
    if (/^(https?:|mailto:|#)/i.test(target)) continue;
    const raw = decodeURIComponent(target.split('#')[0]);
    const linked = path.resolve(path.dirname(full), raw);
    if (!linked.startsWith(root + path.sep) && linked !== root) fail(`Outside-repository documentation link in ${relative}`);
    else if (!fs.existsSync(linked)) fail(`Broken local link in ${relative}: ${target}`);
    links++;
  }
}
const flowText = read('docs/planning/stylist/EP-07_FLOWS.md');
for (let n = 1; n <= 10; n++) if (!flowText.includes(`## F${String(n).padStart(2, '0')} `)) fail(`Missing flow F${n}`);
if (mermaidBlocks < 10) fail('Expected detailed flow/architecture diagrams');

const requested = args[1];
const phase = requested && (m.phases || []).find(p => p.id === requested);
if (requested && !phase) fail(`Unknown phase ${requested}`);
const pending = phase ? phase.required_approvals.filter(key => m.approvals[key].status !== 'APPROVED') : [];
if (phase) {
  const auth = m.phase_authorizations && m.phase_authorizations[requested];
  if (!auth || !auth.owner_evidence || !auth.approved_scope || !auth.recorded_at) pending.push(`owner_phase_authorization:${requested}`);
  if (requested !== 'P01' && m.authorization_scope === 'DOCUMENTATION_AND_HARNESS_ONLY') pending.push('implementation_authorization');
  for (const id of phase.depends_on || []) {
    const prerequisite = m.phases.find(p => p.id === id);
    if (prerequisite.status !== 'VERIFIED' || !prerequisite.completion_evidence) pending.push(`prerequisite:${id}`);
  }
}
if (phase && phase.requires_current_workbook && !fs.existsSync(m.workbook.path)) pending.push('current_workbook');

console.log(`EP-07 planning guard: branch=${branch}; sources=${sources}; documents=${m.required_documents.length}; links=${links}; JSON examples=${jsonExamples}; Mermaid blocks=${mermaidBlocks}`);
console.log('This guard checks planning structure/source drift. It does not parse/render Mermaid, verify remote URLs, prove human approval, or test business APIs.');
for (const message of warnings) console.warn(`WARNING: ${message}`);
for (const message of failures) console.error(`FAIL: ${message}`);
if (failures.length) process.exit(1);
if (pending.length) { console.log(`${requested} NOT AUTHORIZED by recorded decisions: ${pending.join(', ')}`); process.exit(2); }
console.log(requested ? `${requested} recorded prerequisites satisfied; verify the actual owner instruction and allowed slice before editing.` : 'PASS: planning checks. Business approvals remain separate.');
