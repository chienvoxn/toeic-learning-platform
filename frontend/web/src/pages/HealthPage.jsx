import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { healthApi } from '@/services/apiClient';
import { useMock } from '@/lib/runtime-config';

export default function HealthPage() {
  const [state, setState] = useState({ loading: true });
  useEffect(() => {
    const controller = new AbortController();
    Promise.allSettled([healthApi.check({ signal: controller.signal }), healthApi.database({ signal: controller.signal })]).then(([app, database]) => {
      if (!controller.signal.aborted) setState({ loading: false,
        app: app.status === 'fulfilled' ? app.value.status : 'Không kết nối được',
        database: database.status === 'fulfilled' ? database.value.status : 'Không kết nối được',
        error: app.status === 'rejected' ? app.reason.message : database.status === 'rejected' ? database.reason.message : '',
      });
    });
    return () => controller.abort();
  }, []);
  return <main className="max-w-xl mx-auto p-8 space-y-5"><Link to="/" className="pill-button">← Trang chủ</Link><h1 className="text-2xl font-bold">Kiểm tra kết nối hệ thống</h1><section className="surface p-6 space-y-4">
    <p>Nguồn dữ liệu: <strong>{useMock ? 'Dữ liệu mẫu (mock)' : 'API backend thật'}</strong></p>
    <p>Backend: <strong>{state.loading ? 'Đang kiểm tra...' : state.app}</strong></p>
    <p>Database: <strong>{state.loading ? 'Đang kiểm tra...' : state.database}</strong></p>
    {state.error && <p className="form-error" role="alert">{state.error}</p>}
  </section></main>;
}