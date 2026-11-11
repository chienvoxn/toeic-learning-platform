import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import {
  Bell,
  Volume2,
  LogOut,
  ChevronRight,
  Shield,
  Sparkles,
  Target,
  Clock,
} from 'lucide-react';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { useAuth } from '@/features/auth/hooks/useAuth';

export default function SettingsPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  // Local settings state
  const [dailyGoal, setDailyGoal] = useState(() => Number(localStorage.getItem('user_daily_goal') || 15));
  const [notificationsEnabled, setNotificationsEnabled] = useState(true);
  const [accent, setAccent] = useState('us'); // 'us' | 'uk'
  const [speechSpeed, setSpeechSpeed] = useState('1.0');
  const [savedMessage, setSavedMessage] = useState('');

  const handleGoalChange = (newGoal) => {
    setDailyGoal(newGoal);
    localStorage.setItem('user_daily_goal', String(newGoal));
    setSavedMessage('Đã cập nhật mục tiêu học tập!');
    setTimeout(() => setSavedMessage(''), 2500);
  };

  const handleLogout = async () => {
    const result = await logout();
    navigate('/login', { state: { logoutFailed: result?.revocationFailed } });
  };

  return (
    <LearnerTemplate variant="catalog">
      <div className="max-w-md mx-auto px-4 py-4 pb-24 md:pb-12 space-y-5">
        <h1 className="text-xl font-extrabold text-gray-900">Cài Đặt & Tài Khoản</h1>

        {savedMessage && (
          <div className="p-3 bg-green-50 border border-green-200 text-green-700 text-xs font-semibold rounded-xl text-center animate-fade-in">
            {savedMessage}
          </div>
        )}

        {/* User Profile Card (Frame 4 in Figma) */}
        <div className="bg-white rounded-2xl border border-gray-100 p-4 shadow-xs flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-12 h-12 rounded-full bg-gradient-to-tr from-blue-600 to-indigo-500 text-white flex items-center justify-center font-extrabold text-base shadow-xs">
              {user?.fullName?.charAt(0) || 'H'}
            </div>
            <div>
              <strong className="text-sm font-extrabold text-gray-900 block">{user?.fullName || 'Học viên'}</strong>
              <span className="text-xs text-gray-400">{user?.email}</span>
              <div className="mt-1 flex items-center gap-1.5 text-[11px] font-bold text-blue-600">
                <Target size={12} /> Mục tiêu: {user?.targetScore ?? 'Chưa đặt'} TOEIC
              </div>
            </div>
          </div>

          <Link
            to="/profile"
            className="p-2 hover:bg-gray-50 text-gray-400 hover:text-gray-700 rounded-xl transition-colors"
            title="Xem hồ sơ"
          >
            <ChevronRight size={18} />
          </Link>
        </div>

        <p className="integration-notice">Các tùy chọn học tập, âm thanh và nhắc nhở bên dưới là demo/local, chưa được lưu qua Identity.</p>
        {/* Learning Targets Section */}
        <section className="bg-white rounded-2xl border border-gray-100 p-4 shadow-xs space-y-3">
          <h2 className="text-xs font-bold text-gray-400 uppercase tracking-wider flex items-center gap-1.5">
            <Clock size={13} /> Mục tiêu học tập
          </h2>

          <div>
            <div className="flex justify-between items-center mb-2">
              <span className="text-xs font-medium text-gray-700">Thời gian học hàng ngày:</span>
              <span className="text-xs font-extrabold text-blue-600">{dailyGoal} phút / ngày</span>
            </div>
            <div className="grid grid-cols-4 gap-2">
              {[5, 10, 15, 20].map((mins) => (
                <button
                  key={mins}
                  type="button"
                  onClick={() => handleGoalChange(mins)}
                  className={`py-2 rounded-xl text-xs font-bold transition-all border ${
                    dailyGoal === mins
                      ? 'border-blue-600 bg-blue-50 text-blue-700 shadow-2xs'
                      : 'border-gray-200 bg-white text-gray-600 hover:bg-gray-50'
                  }`}
                >
                  {mins}p
                </button>
              ))}
            </div>
          </div>
        </section>

        {/* Audio & Pronunciation Settings */}
        <section className="bg-white rounded-2xl border border-gray-100 p-4 shadow-xs space-y-3">
          <h2 className="text-xs font-bold text-gray-400 uppercase tracking-wider flex items-center gap-1.5">
            <Volume2 size={13} /> Cài đặt giọng đọc & Audio
          </h2>

          <div className="flex items-center justify-between py-1">
            <span className="text-xs font-medium text-gray-700">Chất giọng phát âm:</span>
            <div className="flex gap-1.5">
              <button
                type="button"
                onClick={() => setAccent('us')}
                className={`px-3 py-1 rounded-lg text-xs font-bold ${
                  accent === 'us' ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600'
                }`}
              >
                Mỹ (US)
              </button>
              <button
                type="button"
                onClick={() => setAccent('uk')}
                className={`px-3 py-1 rounded-lg text-xs font-bold ${
                  accent === 'uk' ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600'
                }`}
              >
                Anh (UK)
              </button>
            </div>
          </div>

          <div className="flex items-center justify-between py-1 border-t border-gray-100">
            <span className="text-xs font-medium text-gray-700">Tốc độ nói:</span>
            <select
              value={speechSpeed}
              onChange={(e) => setSpeechSpeed(e.target.value)}
              className="text-xs font-bold border border-gray-200 rounded-lg px-2.5 py-1 bg-white text-gray-800 focus:outline-none"
            >
              <option value="0.8">0.8x (Chậm)</option>
              <option value="1.0">1.0x (Chuẩn)</option>
              <option value="1.2">1.2x (Nhanh)</option>
            </select>
          </div>
        </section>

        {/* Notification Settings */}
        <section className="bg-white rounded-2xl border border-gray-100 p-4 shadow-xs space-y-3">
          <h2 className="text-xs font-bold text-gray-400 uppercase tracking-wider flex items-center gap-1.5">
            <Bell size={13} /> Nhắc nhở học tập
          </h2>

          <div className="flex items-center justify-between">
            <div>
              <strong className="text-xs font-bold text-gray-800 block">Thông báo giữ Streak</strong>
              <span className="text-[11px] text-gray-400">Nhắc nhở ôn từ vựng vào 20:00 mỗi tối</span>
            </div>
            <button
              type="button"
              role="switch"
              aria-checked={notificationsEnabled}
              onClick={() => setNotificationsEnabled(!notificationsEnabled)}
              className={`w-11 h-6 rounded-full transition-colors relative ${
                notificationsEnabled ? 'bg-blue-600' : 'bg-gray-200'
              }`}
            >
              <span
                className={`w-4 h-4 rounded-full bg-white absolute top-1 transition-transform ${
                  notificationsEnabled ? 'right-1' : 'left-1'
                }`}
              />
            </button>
          </div>
        </section>

        {/* Account & App Info */}
        <section className="bg-white rounded-2xl border border-gray-100 divide-y divide-gray-100 shadow-xs overflow-hidden">
          <Link
            to="/onboarding"
            className="w-full p-3.5 flex items-center justify-between text-xs text-gray-700 hover:bg-gray-50 transition-colors"
          >
            <div className="flex items-center gap-2.5">
              <Sparkles size={16} className="text-indigo-500" />
              <span>Xem lại phần giới thiệu (Onboarding)</span>
            </div>
            <ChevronRight size={15} className="text-gray-400" />
          </Link>

          <Link
            to="/health"
            className="w-full p-3.5 flex items-center justify-between text-xs text-gray-700 hover:bg-gray-50 transition-colors"
          >
            <div className="flex items-center gap-2.5">
              <Shield size={16} className="text-emerald-500" />
              <span>Trạng thái kết nối hệ thống (Health Check)</span>
            </div>
            <ChevronRight size={15} className="text-gray-400" />
          </Link>

          <button
            type="button"
            onClick={handleLogout}
            className="w-full p-3.5 flex items-center justify-between text-xs text-red-600 font-bold hover:bg-red-50 transition-colors text-left"
          >
            <div className="flex items-center gap-2.5">
              <LogOut size={16} />
              <span>Đăng xuất khỏi tài khoản</span>
            </div>
          </button>
        </section>

        <p className="text-[11px] text-center text-gray-400 pt-2">
          TOEIC Learning Platform • Phiên bản v1.2
        </p>
      </div>
    </LearnerTemplate>
  );
}
