import { Link } from 'react-router-dom';
import { X } from 'lucide-react';
import { Brand } from '@/components/atoms/Brand';
import { assetUrl } from '@/lib/figma-assets';

export default function AuthTemplate({ children, title, subtitle }) {
  return <main className="auth-page"><section className="auth-dialog" aria-labelledby="auth-heading">
    <div className="auth-content"><div className="auth-heading"><Brand compact /><h1 id="auth-heading">{title}</h1></div><p className="auth-subtitle">{subtitle}</p>{children}</div>
    <div className="auth-illustration"><img src={assetUrl('assets/figma/login/mascot.svg')} alt="Parroto đeo tai nghe và cầm điện thoại" /></div>
    <Link to="/" className="auth-close icon-button" aria-label="Đóng đăng nhập"><X size={20} /></Link>
  </section></main>;
}
