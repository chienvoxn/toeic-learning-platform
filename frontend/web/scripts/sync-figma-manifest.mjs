import { readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const path = resolve(root, 'docs/visual/manifest.json');
const visual = JSON.parse(await readFile(path, 'utf8'));
const runtime = JSON.parse(await readFile(resolve(root,'docs/visual/asset-downloads.json'), 'utf8'));
visual.accessStatus = 'FIGMA_CONTEXT_AND_USER_EXPORT_AVAILABLE';
visual.figmaApiStatus = 'CONTEXT_READ_THEN_PLAN_LIMIT_REACHED';
visual.metadataStatus = 'PARTIALLY_VERIFIED';
visual.runtimeAssetManifestPath = 'asset-downloads.json';
visual.runtimeAssetGuidePath = 'ASSET_DOWNLOADS.md';
visual.implementationReportPath = 'reports/2026-11-01-ui-review.md';
visual.contextRead = {
  date: '2026-11-01',
  nodes: ['73:2618','144:2053','145:2142','76:3625','77:4444','82:3137','82:3684','83:4528','80:2377','81:2764','170:2052','170:2391'],
  note: 'Figma context read for these frames. USER_EXPORT provenance/hash and per-reference verification state remain unchanged. Overview/result use supplied exported screenshots after context timeouts/plan limits.',
};
visual.assets = [...runtime.assets, ...runtime.localExports].map(asset => ({
  id: `web.${asset.id.replaceAll('/','.')}`, path: '../../'+asset.relativePath,
  type: asset.relativePath.endsWith('.png') ? 'PNG' : 'SVG',
  source: 'FIGMA_MCP', figmaFileKey: runtime.fileKey, figmaUrl: asset.figmaUrl,
  ...(asset.width ? { widthPx: asset.width, heightPx: asset.height } : { viewBox: asset.viewBox }),
  sha256: asset.sha256, bytes: asset.bytes, variant: 'default', theme: 'light',
}));
await writeFile(path, JSON.stringify(visual,null,2)+'\n');
console.log(`Linked ${visual.assets.length} runtime assets; kept ${visual.references.length} original references.`);
