# TOEIC Learning Mobile

React Native + Expo, JavaScript. Giao diện hiện tại được đối chiếu với **25 SVG người dùng export từ Figma** trong `D:\Downloads\Toeic (1)` ngày 2026-11-02.

[Gallery 61 ảnh có link tải](assets/image-gallery.html) · [Bảng đường dẫn tương đối](docs/visual/ASSET_DOWNLOADS.md) · [Báo cáo đối chiếu](docs/visual/reports/FIGMA_EXPORT_REVIEW.md) · [Kiến trúc](docs/ARCHITECTURE.md).

Từ `frontend/app/`:

```powershell
npm.cmd ci
npm.cmd start
```

`npm.cmd run android` mở Android khi có SDK/emulator. `npm.cmd run web` mở bản xem trên trình duyệt. iOS native cần macOS/Xcode. Auth, catalog và tiến trình học hiện dùng mock, không thay đổi API backend.

Ảnh đã nằm sẵn trong `assets/images/`, font trong `assets/fonts/`. Không cần tải thêm ảnh để chạy. Gallery có nút tải PNG và link SVG gốc. Link local không phụ thuộc URL Figma tạm thời.

```powershell
npm.cmd run check
npm.cmd test
npm.cmd run assets:check
npm.cmd run export:web -- --max-workers 1
npm.cmd run export:android -- --max-workers 1
```

Kiểm tra trình duyệt (cần Chrome; có thể đặt `CHROME_PATH` nếu ở vị trí khác):

```powershell
npm.cmd run web -- --port 8083 --max-workers 1
# Terminal khác, tại frontend/app:
npm.cmd run test:ui
```

Để kiểm tra bản export: phục vụ `dist/web` bằng HTTP và đặt `APP_URL` trước khi chạy `test:ui`. Ảnh chụp và thông tin geometry nằm trong `docs/visual/reports/current/`. Metro export không phải APK hay kiểm thử trên thiết bị.

Khi người thiết kế export SVG mới, nhập lại từ thư mục nguồn (script chỉ đọc thư mục nguồn, ghi kết quả vào app):

```powershell
python scripts/import-figma-export.py 'D:\Downloads\Toeic (1)'
npm.cmd run assets:prepare
npm.cmd run assets:sync
npm.cmd run assets:gallery
npm.cmd run assets:check
```

`assets:prepare` dùng các lớp/geometry cụ thể của bộ export này. Khi thiết kế đổi cấu trúc phải cập nhật mapping trong script, không tự crop screenshot để thay ảnh. Kiểm tra SHA-256 và kích thước PNG sẽ phát hiện ảnh thay sai nguồn.

SVG cả màn chỉ lưu làm tài liệu đối chiếu trong `docs/figma/exports/`; không render screenshot thành app. Các PNG reference cũ trong `docs/visual/references/` được giữ như tài liệu lịch sử.
