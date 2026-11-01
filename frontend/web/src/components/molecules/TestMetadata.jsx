import { Clock, File, Users } from 'lucide-react';

export function TestMetadata({ test }) {
  return <div className="test-metadata"><span><File size={15} />{test.totalQuestions} câu hỏi</span>{test.participantsCount != null && <span><Users size={15} />{test.participantsCount.toLocaleString('vi-VN')} lượt làm</span>}<span><Clock size={15} />{test.durationMinutes} phút</span></div>;
}
