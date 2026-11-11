import { writeFile } from "node:fs/promises";
const pairs = [
  ["Hôm nay", "div.bg-white.svg", "home", 38, 57, 390, 820],
  ["Đăng nhập", "Frame 3.svg", "login", 36, 64, 375, 734],
  ["Học tập", "main#MainScreen.svg", "catalog", 30, 64, 390, 818],
  ["Từ vựng", "Frame 5.svg", "vocabulary-figma-height", 40, 68, 390, 1074],
  ["Chi tiết bộ từ", "Frame 6.svg", "deck-detail", 40, 68, 390, 1113],
  ["Flashcard", "Frame 2.svg", "flashcard-revealed", 40, 68, 390, 766],
  ["Cài đặt", "Frame 4.svg", "settings", 36, 64, 390, 895],
  [
    "Trình độ",
    "div#Frame-Screen-Option3-Selected.svg",
    "onboarding-proficiency",
    40,
    64,
    390,
    766,
  ],
  ["Mục tiêu", "div.w-full.svg", "onboarding-daily-goal", 38, 57, 390, 766],
  [
    "Thành quả",
    "div#Frame-Screen-6-Achievements.svg",
    "onboarding-benefits",
    38,
    57,
    390,
    766,
  ],
  [
    "Danh mục game",
    "Danh sách thẻ bài học/game_margin.svg",
    "games",
    0,
    0,
    452,
    894,
  ],
  ["Nối từ", "div.mobile-screen-1.svg", "word-match-playing", 0, 0, 390, 844],
  ["Hết giờ", "div.mobile-screen-5.svg", "word-match-time-up", 0, 0, 390, 844],
];
const encoded = (p) => p.split("/").map(encodeURIComponent).join("/");
const rows = pairs
  .map(
    ([title, file, current, x, y, width, height]) =>
      `<section><h2>${title}</h2><div class="pair"><figure><figcaption>SVG export · vùng nội dung</figcaption><div class="crop" style="width:${width}px;height:${height}px"><img class="original" style="left:-${x}px;top:-${y}px" src="../../figma/exports/${encoded(file)}"></div></figure><figure><figcaption>App chạy trên browser</figcaption><img class="current" width="${width}" src="current/${current}.png"></figure></div></section>`,
  )
  .join("\n");
await writeFile(
  "docs/visual/reports/comparison.html",
  `<!doctype html><html lang="vi"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Đối chiếu app với export Figma</title><style>body{font:15px system-ui;color:#1e293b;background:#eef2f6;margin:24px}h1{font-size:25px}section{border-bottom:1px solid #cbd5e1;padding-bottom:24px;margin-bottom:24px}.pair{display:flex;gap:24px;align-items:flex-start;flex-wrap:wrap}figure{margin:0}figcaption{padding:10px 0;font-weight:600}.crop{position:relative;overflow:hidden;background:white;border-radius:12px}.original{position:absolute;max-width:none}.current{display:block;border-radius:12px}p{max-width:850px;line-height:1.6}</style><h1>Đối chiếu SVG Figma và giao diện app</h1><p>SVG nguồn giữ nguyên byte. Cột trái chỉ ẩn viền máy/status bar/gesture bar để xem phần nội dung; không sửa file gốc. Cột phải là ảnh app chạy thật trên trình duyệt. Viewport export có chiều cao khác nhau nên mức cuộn và số hàng nhìn thấy có thể khác. Dữ liệu timer/điểm/game thay đổi khi chơi. Đây là bản đối chiếu trực quan, không phải kết quả pixel-diff tự động.</p>${rows}</html>\n`,
);
console.log("Wrote visual comparison for 13 screen/state pairs.");
