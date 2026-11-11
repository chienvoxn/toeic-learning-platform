import { useEffect, useState } from 'react';
import { healthApi } from '@/services/apiClient';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { authMode, demoModules } from '@/features/auth/config';

export default function HealthPage() {
  const [state, setState] = useState({ loading: true, status: '', error: '' });

  useEffect(() => {
    const controller = new AbortController();
    healthApi.check()
      .then(data => {
        if (!controller.signal.aborted) {
          setState({ loading: false, status: data?.status || 'ONLINE', error: '' });
        }
      })
      .catch(error => {
        if (!controller.signal.aborted) {
          setState({ loading: false, status: 'OFFLINE / DISCONNECTED', error: error.message });
        }
      });
    return () => controller.abort();
  }, []);

  return (
    <LearnerTemplate>
      <div className="max-w-xl mx-auto space-y-6">
        <Card>
          <CardHeader>
            <CardTitle className="text-xl">Kiểm tra kết nối hệ thống (System Health)</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex items-center justify-between p-3 bg-slate-50 rounded-xl border border-slate-200">
              <span className="text-sm font-semibold text-slate-700">Frontend Web React:</span>
              <Badge variant="success">Hoạt động bình thường</Badge>
            </div>
            <div className="flex items-center justify-between p-3 bg-slate-50 rounded-xl border border-slate-200">
              <span className="text-sm font-semibold text-slate-700">Auth / Các module học:</span>
              <Badge variant="default">{authMode} / {demoModules ? 'demo' : 'remote'}</Badge>
            </div>
            <div className="flex items-center justify-between p-3 bg-slate-50 rounded-xl border border-slate-200">
              <span className="text-sm font-semibold text-slate-700">Backend Spring Boot API:</span>
              <Badge variant={state.status === 'ONLINE' ? 'success' : 'destructive'}>
                {state.loading ? 'Đang kiểm tra...' : state.status}
              </Badge>
            </div>
            {state.error && (
              <p className="text-xs text-red-500 bg-red-50 p-3 rounded-lg border border-red-200" role="alert">
                Không kết nối được backend: {state.error}. Remote auth không tự chuyển sang tài khoản demo.
              </p>
            )}
          </CardContent>
        </Card>
      </div>
    </LearnerTemplate>
  );
}
