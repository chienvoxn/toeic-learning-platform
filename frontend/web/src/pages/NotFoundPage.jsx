import { Link } from 'react-router-dom';
import { Home } from 'lucide-react';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { Button } from '@/components/ui/button';

export default function NotFoundPage() {
  return (
    <LearnerTemplate>
      <div className="flex flex-col items-center justify-center py-20 text-center space-y-4">
        <div className="text-7xl font-black text-blue-600">404</div>
        <h1 className="text-2xl font-bold text-slate-900">Không tìm thấy trang yêu cầu</h1>
        <p className="text-sm text-slate-500 max-w-md">
          Đường dẫn bạn đang truy cập có thể đã bị di chuyển hoặc không tồn tại trên hệ thống TOEIC Learning Platform.
        </p>
        <div className="pt-2">
          <Link to="/">
            <Button className="gap-2">
              <Home className="h-4 w-4" />
              Về trang chủ
            </Button>
          </Link>
        </div>
      </div>
    </LearnerTemplate>
  );
}
