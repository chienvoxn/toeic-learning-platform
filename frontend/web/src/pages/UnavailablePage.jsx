import { Link } from 'react-router-dom';
import LearnerTemplate from '@/components/templates/LearnerTemplate';

export default function UnavailablePage({ title }) {
  return <LearnerTemplate><section className="surface p-8 max-w-xl mx-auto">
    <h1 className="text-2xl font-bold mb-4">{title}</h1>
    <p className="mb-6">Chức năng này chưa sẵn sàng. Bạn có thể tiếp tục học với kho học liệu và bộ từ vựng.</p>
    <div className="flex gap-4"><Link className="pill-button navy" to="/catalog">Kho học liệu</Link><Link className="pill-button" to="/vocabulary">Từ vựng</Link></div>
  </section></LearnerTemplate>;
}
