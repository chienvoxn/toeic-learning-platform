import { useEffect, useState } from 'react';
import { apiRequest } from '@/services/apiClient';

export function ExamMedia({ resourceId, resourceType, transcript }) {
  const [loaded, setLoaded] = useState({ key: '', url: '', error: '' });
  const [retry, setRetry] = useState(0);
  const requestKey = `${resourceId}:${retry}`;
  const { url, error } = loaded.key === requestKey ? loaded : { url: '', error: '' };
  useEffect(() => {
    let active = true, objectUrl;
    apiRequest(`/media/${resourceId}`, { responseType: 'blob' }).then(blob => {
      if (active) { objectUrl = URL.createObjectURL(blob); setLoaded({ key: requestKey, url: objectUrl, error: '' }); }
    }).catch(e => { if (active) setLoaded({ key: requestKey, url: '', error: e.message }); });
    return () => { active = false; if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [resourceId, requestKey]);
  return <div className="exam-media">{error ? <p role="alert">{error} <button onClick={() => setRetry(n => n + 1)}>Tải lại media</button></p> : !url ? <p>Đang tải media…</p> : resourceType === 'AUDIO' ? <audio controls preload="metadata" src={url} aria-label="Audio câu hỏi" /> : <img src={url} alt="Hình minh họa câu hỏi TOEIC" style={{ maxWidth: '100%', maxHeight: 400 }} />}{transcript && <details><summary>Transcript</summary><p style={{ whiteSpace: 'pre-wrap' }}>{transcript}</p></details>}</div>;
}
