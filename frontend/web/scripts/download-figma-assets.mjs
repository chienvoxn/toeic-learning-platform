import { readFile, mkdir, writeFile, stat } from 'node:fs/promises';
import { dirname, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';

// Run from any directory: node frontend/web/scripts/download-figma-assets.mjs
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const { assets } = JSON.parse(await readFile(resolve(root, 'docs/visual/asset-downloads.json'), 'utf8'));
let next = 0;
const failures = [];
let downloaded = 0;
let skipped = 0;
async function worker() {
  while (next < assets.length) {
    const asset = assets[next++];
    const target = resolve(root, asset.relativePath);
    if (!target.startsWith(resolve(root, 'public/assets/figma') + sep)) throw new Error('Asset path outside public/assets/figma');
    if ((await stat(target).catch(() => null))?.size > 0) { skipped++; continue; }
    try {
      const response = await fetch(asset.downloadUrl, { signal: AbortSignal.timeout(45000), redirect: 'follow' });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const bytes = Buffer.from(await response.arrayBuffer());
      const svg = target.endsWith('.svg');
      if (svg ? !bytes.toString('utf8').includes('<svg') : !bytes.subarray(0, 8).equals(Buffer.from([137,80,78,71,13,10,26,10]))) {
        throw new Error('Response is not the expected image');
      }
      if (asset.sha256 && createHash('sha256').update(bytes).digest('hex') !== asset.sha256) throw new Error('Downloaded image does not match the recorded SHA-256');
      await mkdir(dirname(target), { recursive: true });
      await writeFile(target, bytes);
      downloaded++;
    } catch (error) { failures.push(`${asset.relativePath}: ${error.message}`); }
  }
}
await Promise.all(Array.from({ length: 6 }, worker));
console.log(JSON.stringify({ downloaded, skipped, failed: failures.length, failures }, null, 2));
if (failures.length) process.exitCode = 1;
