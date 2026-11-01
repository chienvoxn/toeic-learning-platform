import { readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const manifestPath = resolve(root, 'docs/visual/asset-downloads.json');
const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));
const registry = JSON.parse(await readFile(resolve(root, 'src/assets/figma-assets.json'), 'utf8'));
const failures = [];
for (const asset of manifest.assets) {
  try {
    const bytes = await readFile(resolve(root, asset.relativePath));
    const sha256 = createHash('sha256').update(bytes).digest('hex');
    if (asset.sha256 && asset.sha256 !== sha256) failures.push(`Changed asset: ${asset.relativePath}`);
    if (!bytes.length) failures.push(`Empty asset: ${asset.relativePath}`);
    if (asset.sourceFile.endsWith('.png')) {
      if (!bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10]))) throw new Error('Invalid PNG signature');
      asset.width = bytes.readUInt32BE(16); asset.height = bytes.readUInt32BE(20);
    } else {
      const svg = bytes.toString('utf8');
      if (!svg.includes('<svg')) throw new Error('Invalid SVG');
      asset.viewBox = svg.match(/viewBox="([^"]+)"/)?.[1] || null;
    }
    asset.sha256 = sha256; asset.bytes = bytes.length;
  } catch (error) { failures.push(`${asset.relativePath}: ${error.message}`); }
}
for (const [screen, values] of Object.entries(registry)) {
  for (const [name, path] of Object.entries(values)) {
    if (!manifest.assets.some(a => a.webPath === path)) failures.push(`Unregistered mapping: ${screen}.${name}`);
  }
}
const mascot = await readFile(resolve(root, 'public/assets/figma/login/mascot.svg'));
const mascotHash = createHash('sha256').update(mascot).digest('hex');
const previousMascot = manifest.localExports?.find(a => a.id === 'login/mascot');
if (!mascot.toString('utf8').includes('<svg') || (previousMascot?.sha256 && previousMascot.sha256 !== mascotHash)) failures.push('Invalid or changed login mascot SVG');
manifest.localExports = [{
  id: 'login/mascot', relativePath: 'public/assets/figma/login/mascot.svg',
  figmaUrl: 'https://www.figma.com/design/kTVpctPi75i8QZgUzSarHc/Toeic?node-id=144-2103',
  exportFormat: 'SVG_STRING', viewBox: '0 0 320 320', bytes: mascot.length,
  sha256: mascotHash,
}];
if (process.argv.includes('--update-manifest') && !failures.length) await writeFile(manifestPath, JSON.stringify(manifest,null,2)+'\n');
console.log(JSON.stringify({ downloadedAssets: manifest.assets.length, localExports: manifest.localExports.length, registryScreens: Object.keys(registry).length, failures },null,2));
if (failures.length) process.exitCode = 1;
