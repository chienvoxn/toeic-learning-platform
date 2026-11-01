import { figmaAsset } from '@/lib/figma-assets';

export function FigmaAsset({ screen = 'dashboard', name, alt = '', ...props }) {
  return <img src={figmaAsset(screen, name)} alt={alt} {...props} />;
}
