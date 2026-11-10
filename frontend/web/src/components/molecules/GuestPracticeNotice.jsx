import { useMock } from '@/lib/runtime-config';

export function GuestPracticeNotice() {
  if (useMock) return null;
  return <p className="rounded-xl border border-blue-100 bg-blue-50 p-4 mb-5 text-sm text-blue-900" role="note">Thi thử miễn phí, không cần đăng nhập. Đây là bộ câu hỏi Reading mẫu; bài làm và kết quả chỉ lưu trên trình duyệt này, không đồng bộ tài khoản. Kết quả hiển thị số câu đúng, không quy đổi điểm TOEIC.</p>;
}
