import { useEffect, useState } from 'react';
import { useAuth } from '../auth/hooks/useAuth';

export function useAdminReport(loader, query, enabled, revision = 0) {
  const { restore } = useAuth();
  const [state, setState] = useState({ loading: false, data: null, error: '' });
  useEffect(() => {
    const controller = new AbortController();
    Promise.resolve().then(async () => {
      if (controller.signal.aborted) return;
      setState({ loading: enabled, data: null, error: '' });
      if (!enabled) return;
      try {
        const data = await loader(query, controller.signal);
        if (!controller.signal.aborted) setState({ loading: false, data, error: '' });
      } catch (error) {
        if (!controller.signal.aborted) {
          setState({ loading: false, data: null, error: error.message });
          if (error.status === 403) await restore({ background: true });
        }
      }
    });
    return () => controller.abort();
  }, [loader, query, enabled, revision, restore]);
  return state;
}
