import assets from '@/assets/figma-assets.json';

export function assetUrl(path) { return `${import.meta.env.BASE_URL}${path}`; }
export function figmaAsset(screen, name) {
  const path = assets[screen]?.[name];
  if (!path) throw new Error(`Unknown Figma asset: ${screen}.${name}`);
  return assetUrl(path);
}
