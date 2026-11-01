import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Sparkles, Check, ChevronRight, ArrowRight } from 'lucide-react';

export default function OnboardingPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState(1); // 1: Benefits, 2: Proficiency, 3: Daily Goal

  // Selected states matching Figma frames
  const [proficiency, setProficiency] = useState('basic'); // 'basic' | 'many-topics' | 'most-topics'
  const [dailyGoal, setDailyGoal] = useState(15); // 5, 10, 15, 20

  const handleFinish = () => {
    localStorage.setItem('onboarding_completed', 'true');
    localStorage.setItem('user_proficiency', proficiency);
    localStorage.setItem('user_daily_goal', String(dailyGoal));
    navigate('/');
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-sky-50 via-white to-gray-50 flex flex-col justify-between p-4 max-w-md mx-auto">
      {/* Top progress dots */}
      <div className="pt-4 flex items-center justify-between">
        <span className="text-xs font-bold text-gray-400">Bước {step} / 3</span>
        <div className="flex gap-1.5">
          {[1, 2, 3].map((s) => (
            <div
              key={s}
              className={`h-1.5 rounded-full transition-all duration-300 ${
                s === step ? 'w-8 bg-blue-600' : s < step ? 'w-4 bg-blue-300' : 'w-4 bg-gray-200'
              }`}
            />
          ))}
        </div>
      </div>

      {/* Main Content Area */}
      <div className="my-auto py-6">
        {/* STEP 1: BENEFITS & WELCOME (div#Frame-Screen-6-Achievements.png) */}
        {step === 1 && (
          <div className="space-y-6 text-center animate-fade-in">
            <div className="w-20 h-20 mx-auto rounded-3xl bg-gradient-to-tr from-blue-600 to-indigo-500 text-white flex items-center justify-center shadow-lg shadow-blue-500/20">
              <Sparkles size={40} />
            </div>

            <div>
              <h1 className="text-2xl font-extrabold text-gray-900 tracking-tight">
                Chinh Phục TOEIC Theo Cách Cá Nhân Hóa
              </h1>
              <p className="text-xs text-gray-500 mt-2 leading-relaxed max-w-xs mx-auto">
                Học qua phương pháp Lặp lại ngắt quãng (SRS), luyện đề bấm giờ và nghe chép chính tả hàng ngày.
              </p>
            </div>

            <div className="space-y-3 text-left pt-2">
              <div className="flex items-center gap-3 p-3.5 rounded-2xl bg-white border border-gray-100 shadow-2xs">
                <div className="w-8 h-8 rounded-full bg-blue-50 text-blue-600 flex items-center justify-center font-bold text-xs">
                  🎯
                </div>
                <div>
                  <strong className="text-xs font-bold text-gray-900 block">Lộ trình theo mục tiêu điểm</strong>
                  <span className="text-[11px] text-gray-500">Tự động đề xuất bài học từ 500 đến 900+</span>
                </div>
              </div>

              <div className="flex items-center gap-3 p-3.5 rounded-2xl bg-white border border-gray-100 shadow-2xs">
                <div className="w-8 h-8 rounded-full bg-green-50 text-green-600 flex items-center justify-center font-bold text-xs">
                  🧠
                </div>
                <div>
                  <strong className="text-xs font-bold text-gray-900 block">Flashcard Spaced Repetition</strong>
                  <span className="text-[11px] text-gray-500">Ghi nhớ từ vựng vĩnh viễn không sợ quên</span>
                </div>
              </div>

              <div className="flex items-center gap-3 p-3.5 rounded-2xl bg-white border border-gray-100 shadow-2xs">
                <div className="w-8 h-8 rounded-full bg-amber-50 text-amber-600 flex items-center justify-center font-bold text-xs">
                  🔥
                </div>
                <div>
                  <strong className="text-xs font-bold text-gray-900 block">Giữ vững chuỗi Streak học tập</strong>
                  <span className="text-[11px] text-gray-500">Nhắc nhở học đều đặn mỗi ngày</span>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* STEP 2: PROFICIENCY LEVEL (Option3, Option4, Option5) */}
        {step === 2 && (
          <div className="space-y-5 animate-fade-in">
            <div className="text-center">
              <h2 className="text-xl font-extrabold text-gray-900">Trình Độ Tiếng Anh Hiện Tại Của Bạn?</h2>
              <p className="text-xs text-gray-500 mt-1">Hệ thống sẽ lựa chọn nội dung phù hợp nhất cho bạn.</p>
            </div>

            <div className="space-y-3 pt-2">
              <button
                type="button"
                onClick={() => setProficiency('basic')}
                className={`w-full p-4 rounded-2xl border-2 text-left flex items-center justify-between transition-all ${
                  proficiency === 'basic'
                    ? 'border-blue-600 bg-blue-50/70 shadow-sm ring-2 ring-blue-300'
                    : 'border-gray-200 bg-white hover:border-gray-300'
                }`}
              >
                <div>
                  <div className="flex items-center gap-2">
                    <span className="text-base">🌱</span>
                    <strong className="text-sm font-bold text-gray-900">Giao tiếp cơ bản (TOEIC ~400-500)</strong>
                  </div>
                  <p className="text-xs text-gray-500 mt-1 pl-6">
                    Biết các từ vựng đơn giản, nghe hiểu câu ngắn và cần xây dựng lại gốc ngữ pháp.
                  </p>
                </div>
                {proficiency === 'basic' && <Check size={18} className="text-blue-600 shrink-0" />}
              </button>

              <button
                type="button"
                onClick={() => setProficiency('many-topics')}
                className={`w-full p-4 rounded-2xl border-2 text-left flex items-center justify-between transition-all ${
                  proficiency === 'many-topics'
                    ? 'border-blue-600 bg-blue-50/70 shadow-sm ring-2 ring-blue-300'
                    : 'border-gray-200 bg-white hover:border-gray-300'
                }`}
              >
                <div>
                  <div className="flex items-center gap-2">
                    <span className="text-base">🚀</span>
                    <strong className="text-sm font-bold text-gray-900">Nói nhiều chủ đề (TOEIC ~600-750)</strong>
                  </div>
                  <p className="text-xs text-gray-500 mt-1 pl-6">
                    Nghe hiểu bài nói công sở, đọc email nhanh nhưng thỉnh thoảng mắc bẫy từ vựng nâng cao.
                  </p>
                </div>
                {proficiency === 'many-topics' && <Check size={18} className="text-blue-600 shrink-0" />}
              </button>

              <button
                type="button"
                onClick={() => setProficiency('most-topics')}
                className={`w-full p-4 rounded-2xl border-2 text-left flex items-center justify-between transition-all ${
                  proficiency === 'most-topics'
                    ? 'border-blue-600 bg-blue-50/70 shadow-sm ring-2 ring-blue-300'
                    : 'border-gray-200 bg-white hover:border-gray-300'
                }`}
              >
                <div>
                  <div className="flex items-center gap-2">
                    <span className="text-base">⚡</span>
                    <strong className="text-sm font-bold text-gray-900">Nói sâu hầu hết chủ đề (TOEIC 800+)</strong>
                  </div>
                  <p className="text-xs text-gray-500 mt-1 pl-6">
                    Phản xạ nhanh, muốn luyện đề bấm giờ thực tế và tối ưu tốc độ làm Part 7.
                  </p>
                </div>
                {proficiency === 'most-topics' && <Check size={18} className="text-blue-600 shrink-0" />}
              </button>
            </div>
          </div>
        )}

        {/* STEP 3: DAILY GOAL (div#Frame-Screen-Daily-Goal.png, w-full, w-full-1, 2) */}
        {step === 3 && (
          <div className="space-y-5 animate-fade-in">
            <div className="text-center">
              <h2 className="text-xl font-extrabold text-gray-900">Mục Tiêu Học Mỗi Ngày Của Bạn?</h2>
              <p className="text-xs text-gray-500 mt-1">Duy trì việc học hàng ngày giúp nhớ lâu hơn gấp 4 lần.</p>
            </div>

            <div className="space-y-3 pt-2">
              {[
                { time: 5, label: 'Dễ dàng', desc: '5 phút / ngày · 10 từ vựng SRS' },
                { time: 10, label: 'Vừa sức', desc: '10 phút / ngày · 1 bài nghe ngắn + 15 từ' },
                { time: 15, label: 'Tích cực', desc: '15 phút / ngày · Luyện 1 Part đề thi + 20 từ' },
                { time: 20, label: 'Tăng tốc', desc: '20 phút / ngày · Đề thi trọn gói + Chép chính tả' },
              ].map((item) => (
                <button
                  key={item.time}
                  type="button"
                  onClick={() => setDailyGoal(item.time)}
                  className={`w-full p-4 rounded-2xl border-2 text-left flex items-center justify-between transition-all ${
                    dailyGoal === item.time
                      ? 'border-blue-600 bg-blue-50/70 shadow-sm ring-2 ring-blue-300'
                      : 'border-gray-200 bg-white hover:border-gray-300'
                  }`}
                >
                  <div className="flex items-center gap-3">
                    <div
                      className={`w-10 h-10 rounded-xl flex items-center justify-center font-extrabold text-sm ${
                        dailyGoal === item.time ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-700'
                      }`}
                    >
                      {item.time}p
                    </div>
                    <div>
                      <strong className="text-sm font-bold text-gray-900 block">{item.label}</strong>
                      <span className="text-xs text-gray-500">{item.desc}</span>
                    </div>
                  </div>
                  {dailyGoal === item.time && <Check size={18} className="text-blue-600 shrink-0" />}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Bottom Action Button */}
      <div className="pb-4 pt-2">
        {step < 3 ? (
          <button
            type="button"
            onClick={() => setStep((prev) => prev + 1)}
            className="w-full py-3.5 bg-blue-600 hover:bg-blue-700 text-white font-extrabold text-sm rounded-2xl flex items-center justify-center gap-2 shadow-sm transition-all"
          >
            TIẾP TỤC <ChevronRight size={16} />
          </button>
        ) : (
          <button
            type="button"
            onClick={handleFinish}
            className="w-full py-3.5 bg-green-600 hover:bg-green-700 text-white font-extrabold text-sm rounded-2xl flex items-center justify-center gap-2 shadow-sm transition-all"
          >
            HOÀN THÀNH & BẮT ĐẦU HỌC <ArrowRight size={16} />
          </button>
        )}
      </div>
    </div>
  );
}
