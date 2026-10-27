import { useEffect, useState } from 'react';
import { healthApi } from '../services/apiClient.js';
export default function HealthPage() {
  const [state, setState] = useState({ loading: true, status: '', error: '' });
  useEffect(() => {
    const controller = new AbortController();
    healthApi.check().then(data => { if (!controller.signal.aborted) setState({ loading: false, status: data?.status || 'UNKNOWN', error: '' }); })
      .catch(error => { if (!controller.signal.aborted) setState({ loading: false, status: 'ERROR', error: error.message }); });
    return () => controller.abort();
  }, []);
  return <section><h1>Kiểm tra kết nối</h1><p>Frontend: Đang chạy</p><p>Backend: {state.loading ? 'Đang kiểm tra...' : state.status}</p>{state.error && <p role="alert">{state.error}</p>}</section>;
}
