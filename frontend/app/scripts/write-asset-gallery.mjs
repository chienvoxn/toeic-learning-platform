import { readFile, writeFile } from "node:fs/promises";
const manifest = JSON.parse(
  await readFile(
    new URL("../assets/asset-manifest.json", import.meta.url),
    "utf8",
  ),
);
const escape = (s) =>
  String(s)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll('"', "&quot;");
const encoded = (p) => p.split("/").map(encodeURIComponent).join("/");
const cards = (
  await Promise.all(
    manifest.assets.map(async (a) => {
      // Data URL permits offline downloads when opened directly as a local file.
      const png = await readFile(
        new URL("../" + a.relativePath, import.meta.url),
      );
      return `<article><div class="preview"><img src="../${encoded(a.relativePath)}" alt="${escape(a.id)}"></div><h2>${escape(a.id)}</h2><code>${escape(a.relativePath)}</code><p>${a.width} × ${a.height} px</p><a download="${escape(a.relativePath.split("/").at(-1))}" href="data:image/png;base64,${png.toString("base64")}">Tải PNG</a> · <a href="../${encoded(a.relativePath)}">Mở ảnh</a> · <a href="../${encoded(a.sourcePath)}">SVG nguồn</a></article>`;
    }),
  )
).join("\n");
await writeFile(
  new URL("../assets/image-gallery.html", import.meta.url),
  `<!doctype html><html lang="vi"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Ảnh app từ Figma</title><style>body{font:15px system-ui;background:#f6f8fc;color:#172033;margin:32px auto;max-width:1200px;padding:0 20px}h1{font-size:26px}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(245px,1fr));gap:16px}article{background:white;border:1px solid #e2e8f0;border-radius:16px;padding:16px}h2{font-size:15px;overflow-wrap:anywhere}.preview{height:170px;display:grid;place-items:center;background:repeating-conic-gradient(#eee 0% 25%,white 0% 50%) 0/20px 20px;border-radius:10px}img{max-height:150px;max-width:100%;object-fit:contain}code{display:block;overflow-wrap:anywhere;font-size:12px}a{color:#0084ff}</style><h1>Ảnh app từ bản export Figma</h1><p>${manifest.assets.length} ảnh đã được đặt vào dự án. Có thể tải lại bằng các link dưới đây. Đường dẫn tính từ <code style="display:inline">frontend/app/</code>. Runtime dùng file local, không phụ thuộc URL Figma.</p><main>${cards}</main></html>\n`,
);
const rows = manifest.assets
  .map(
    (a) =>
      `| \`${a.id}\` | [Tải PNG](../../${encoded(a.relativePath)}) | \`${a.relativePath}\` | [Nguồn SVG](../../${encoded(a.sourcePath)}) | [Nơi dùng](../../${a.callsite}) |`,
  )
  .join("\n");
await writeFile(
  new URL("../docs/visual/ASSET_DOWNLOADS.md", import.meta.url),
  `# Ảnh app từ bản export Figma\n\nNguồn người dùng cung cấp: \`D:\\Downloads\\Toeic (1)\`, nhập ngày 2026-11-02. [Gallery ảnh có nút tải](../../assets/image-gallery.html) · [Manifest](../../assets/asset-manifest.json) · [Danh sách 25 SVG](../figma/export-inventory.json).\n\nĐã đặt ${manifest.assets.length} PNG vào đúng thư mục và sinh static require cho Metro. Bạn không cần tải thêm các ảnh này để chạy app. Các link dưới đây mở file local hoặc tải lại từ gallery; không phải URL ảnh được host công khai.\n\nPNG nhúng được giữ nguyên byte. Mascot, Google G, parents, bìa và icon vector được tách đúng lớp đồ họa và render PNG 3× có alpha, kèm SVG dẫn xuất cùng tên. Không dùng screenshot nguyên màn làm giao diện. Màu trống của thumbnail học liệu được giữ theo export vì nguồn không có ảnh ở các ô đó.\n\nMọi đường dẫn tính từ \`frontend/app/\`. Link SVG là bản export nguyên gốc, tên chứa # đã được mã hoá trong link.\n\n| Asset | Link ảnh local | Đường dẫn tương đối | Nguồn | Callsite |\n|---|---|---|---|---|\n${rows}\n\nSau khi lấy lại ảnh đúng nguồn, chạy từ \`frontend/app/\`:\n\n\`\`\`powershell\nnpm.cmd run assets:sync\nnpm.cmd run assets:check\n\`\`\`\n\nKiểm tra ảnh xác nhận PNG, kích thước và SHA-256 khớp manifest; thay ảnh khác nguồn sẽ bị báo lỗi. Nếu export lại thiết kế, nhập lại SVG và sinh lại asset theo [README](../../README.md). Font dùng file local kèm giấy phép OFL trong \`assets/fonts/\`.\n`,
);
console.log(
  `Wrote gallery and relative download links for ${manifest.assets.length} assets.`,
);
