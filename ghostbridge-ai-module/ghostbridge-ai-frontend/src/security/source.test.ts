// @vitest-environment node
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const ROOT = fileURLToPath(new URL('../..', import.meta.url));

function* walk(dir: string): Generator<string> {
  for (const name of readdirSync(dir)) {
    if (name === 'node_modules' || name === 'dist' || name === '.git') continue;
    const path = join(dir, name);
    if (statSync(path).isDirectory()) yield* walk(path);
    else yield path;
  }
}

const allFiles = [...walk(ROOT)];
const sourceFiles = allFiles.filter((f) => /\/src\/.*\.(ts|tsx|css)$/.test(f) && !f.endsWith('.test.ts') && !f.endsWith('.test.tsx') && !f.includes('/src/test/') && !f.endsWith('/security/source.test.ts'));
const read = (file: string) => readFileSync(file, 'utf8');
const where = (file: string) => relative(ROOT, file);

function offenders(pattern: RegExp, files = sourceFiles) {
  return files.filter((file) => pattern.test(read(file))).map(where);
}

describe('secret absence in frontend source', () => {
  it('references no Ollama credential, VITE_ secret or provider host', () => {
    expect(offenders(/OLLAMA_API_KEY|VITE_OLLAMA|ollama\.com|api\.ollama/i)).toEqual([]);
  });

  it('contains no hardcoded tokens or keys', () => {
    expect(offenders(/Bearer [A-Za-z0-9._~+/-]{20,}/)).toEqual([]);
    expect(offenders(/\bsk-[A-Za-z0-9]{20,}/)).toEqual([]);
    expect(offenders(/-----BEGIN [A-Z ]*PRIVATE KEY-----/)).toEqual([]);
  });

  it('only declares non-secret VITE_ variables', () => {
    const declared = new Set<string>();
    for (const file of [...sourceFiles, join(ROOT, '.env.example')]) {
      for (const match of read(file).matchAll(/VITE_[A-Z0-9_]+/g)) declared.add(match[0]);
    }
    expect([...declared].sort()).toEqual(['VITE_AI_MOCK', 'VITE_AI_STRUCTURED_SCHEMAS', 'VITE_API_BASE_URL', 'VITE_API_TIMEOUT_MS']);
  });

  it('ships no real .env file and the example holds only placeholders', () => {
    const names = allFiles.map((f) => f.split('/').pop() ?? '');
    expect(names.filter((n) => /^\.env(\..+)?$/.test(n) && n !== '.env.example')).toEqual([]);
    expect(read(join(ROOT, '.env.example'))).not.toMatch(/(key|token|secret|password)\s*=\s*\S+/i);
  });
});

describe('unsafe rendering and storage', () => {
  it('never renders HTML from strings', () => {
    expect(offenders(/dangerouslySetInnerHTML|\.innerHTML\s*=|insertAdjacentHTML|document\.write|\beval\(|new Function\(/)).toEqual([]);
  });

  it('never persists anything in web storage or cookies', () => {
    expect(offenders(/localStorage|sessionStorage|indexedDB|document\.cookie/)).toEqual([]);
  });

  it('performs HTTP only in api/client.ts and the transport', () => {
    const allowed = new Set(['src/api/transport.ts']);
    expect(offenders(/\bfetch\(|XMLHttpRequest|axios|navigator\.sendBeacon|new WebSocket|new EventSource/).filter((f) => !allowed.has(f))).toEqual([]);
  });

  it('has no data-built or external links without safe rel', () => {
    const tsx = sourceFiles.filter((f) => f.endsWith('.tsx'));
    // No dynamic href: model output or server text can never become a link target.
    expect(tsx.filter((f) => /<a\b[^>]*\bhref=\{/.test(read(f))).map(where)).toEqual([]);
    expect(tsx.filter((f) => /\btarget="_blank"/.test(read(f)) && !/noopener noreferrer/.test(read(f))).map(where)).toEqual([]);
    expect(tsx.filter((f) => /href="https?:/.test(read(f)) && !/noopener noreferrer/.test(read(f))).map(where)).toEqual([]);
  });
});
