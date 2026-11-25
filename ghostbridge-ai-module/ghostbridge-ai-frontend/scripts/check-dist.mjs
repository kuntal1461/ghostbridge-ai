// Post-build guard: the production bundle must contain no development mock and no secret-looking content.
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const DIST = fileURLToPath(new URL('../dist', import.meta.url));

const FORBIDDEN = [
  ['development mock marker', /GhostBridge-Mock|MOCK DATA \(synthetic\)|mock-model \(synthetic\)|!mock-\d{3}/],
  ['Ollama credential or host reference', /OLLAMA_API_KEY|VITE_OLLAMA|ollama\.com/i],
  ['hardcoded bearer token', /Bearer [A-Za-z0-9._~+/-]{20,}/],
  ['API-key-looking literal', /\bsk-[A-Za-z0-9]{20,}/],
  ['private key material', /-----BEGIN [A-Z ]*PRIVATE KEY-----/],
];

function* files(dir) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) yield* files(path);
    else yield path;
  }
}

let problems = 0;
let scanned = 0;
for (const path of files(DIST)) {
  if (!/\.(js|css|html|map|json|txt)$/.test(path)) continue;
  scanned += 1;
  const content = readFileSync(path, 'utf8');
  for (const [label, pattern] of FORBIDDEN) {
    if (pattern.test(content)) {
      console.error(`check-dist: ${label} found in ${path.replace(DIST, 'dist')}`);
      problems += 1;
    }
  }
}
if (scanned === 0) {
  console.error('check-dist: no build output found');
  process.exit(1);
}
if (problems > 0) process.exit(1);
console.log(`check-dist: ${scanned} files scanned, no mock code or secret patterns found`);
