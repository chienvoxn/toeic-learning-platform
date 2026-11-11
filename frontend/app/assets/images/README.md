# Ảnh runtime của app

61 PNG đã được nhập từ bộ SVG export do người dùng cung cấp, có trong [manifest](../asset-manifest.json). [Gallery có nút tải](../image-gallery.html) và [bảng đường dẫn tương đối](../../docs/visual/ASSET_DOWNLOADS.md).

PNG nhúng được giải mã nguyên byte. Các hình vector được tách theo đúng lớp và render PNG 3× có alpha; SVG dẫn xuất cùng tên được giữ để đối chiếu. Toàn bộ SVG màn gốc nằm trong `docs/figma/exports/`, không dùng làm nền runtime.

Runtime chỉ dùng static Metro require trong `src/assets/images.generated.js`. Chạy `npm.cmd run assets:sync` để sinh registry; `npm.cmd run assets:check` kiểm tra file, kích thước, hash và registry. Không dùng URL Figma hoặc dịch vụ ảnh bên ngoài trong runtime.
