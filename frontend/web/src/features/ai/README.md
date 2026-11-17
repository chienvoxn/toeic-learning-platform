# Trợ lý TOEIC

`ChatBubble.jsx` cung cấp bong bóng chat trong LearnerTemplate khi đã đăng nhập
remote. `chat.api.js` chỉ gọi Spring Boot; trình duyệt không giữ khóa nội bộ AI.
Gửi tin dùng UUID request và timeout 55 giây, lịch sử/citation lấy từ server.
`chat.css` giữ typography và bảng màu ENGLINGO, có bố cục mobile trên bottom nav.

Cấu hình, ingestion và giới hạn runtime: [RAG_Chat_Integration.md](../../../../../docs/RAG_Chat_Integration.md).
