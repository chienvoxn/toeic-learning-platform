import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { contentRepo } from '@/features/content/repositories/content.repo';

export default function ContentDetailPage() {
  const { contentId } = useParams();
  const [content, setContent] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    contentRepo.getContentById(contentId).then(result => { if (active) setContent(result); })
      .catch(err => { if (active) setError(err.message); });
    return () => { active = false; };
  }, [contentId]);
  return <LearnerTemplate><article className="max-w-3xl mx-auto space-y-5">
    <Link className="pill-button" to="/catalog">← Kho học liệu</Link>
    {error && <p className="form-error" role="alert">{error}</p>}
    {!content && !error && <LoadingSpinner text="Đang tải bài học..." />}
    {content && <><h1 className="text-3xl font-bold">{content.title}</h1><p className="text-slate-500">{content.description}</p><div className="surface p-6 whitespace-pre-wrap leading-8 break-words">{content.body || 'Bài học chưa có nội dung văn bản.'}</div>
      {content.media.length > 0 && <p className="text-sm text-slate-500">Bài học có {content.media.length} tài liệu đa phương tiện. Phát media chưa được hỗ trợ.</p>}
    </>}
  </article></LearnerTemplate>;
}
