import { Link } from 'react-router-dom';
import { assetUrl } from '@/lib/figma-assets';

export function Brand({ compact = false, className = '' }) {
  return <Link to="/" className={`brand ${className}`} aria-label="ENGLINGO — Trang chủ">
    <img className="brand-mark" src={assetUrl('favicon.svg')} width="34" height="34" alt="" />
    {!compact && <span className="brand-wordmark">ENGLINGO</span>}
  </Link>;
}
