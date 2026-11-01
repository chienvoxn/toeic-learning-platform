import { Badge } from '@/components/ui/badge';

export function StatusBadge({ status }) {
  switch (status) {
    case 'IN_PROGRESS':
      return <Badge variant="warning">Đang làm bài</Badge>;
    case 'SUBMITTED':
      return <Badge variant="secondary">Đã nộp bài</Badge>;
    case 'GRADED':
      return <Badge variant="success">Đã có kết quả</Badge>;
    case 'EXPIRED':
      return <Badge variant="destructive">Hết giờ</Badge>;
    default:
      return <Badge variant="outline">{status || 'Chưa bắt đầu'}</Badge>;
  }
}
