# Frontend web

React/Vite web. Tích hợp Identity thật và hướng dẫn chạy/kiểm chứng: [Web Auth Integration](../../docs/WEB_Auth_Integration.md).

```powershell
Copy-Item .env.example .env.local
npm ci
npm run dev -- --host 127.0.0.1 --strictPort
```

- Web: http://127.0.0.1:5173
- Vite proxy /api → http://localhost:8080; API base /api/v1.
- VITE_AUTH_MODE=remote (mặc định): Identity thật; không fallback user demo khi lỗi.
- VITE_AUTH_MODE=mock: demo auth riêng, không gửi OTP thật/đổi mật khẩu.
- VITE_USE_MOCK=true: assessment/content/vocabulary/games còn demo; không chi phối auth.
- SMTP local: Mailpit 127.0.0.1:1025, mailbox http://127.0.0.1:8025.

Backend cần PostgreSQL và DB_PASSWORD hoặc cấu hình datasource kiểm thử riêng như tài liệu. Không reset database cá nhân.

```powershell
npm run lint
npm run build
npm test
npm run test:auth:e2e
```

npm test là contract test với transport stub. test:auth:e2e dùng Chrome, frontend/backend/Mailpit thật và DB toeic_web_auth_test riêng; xem biến WEB_AUTH_* và fixture trong tài liệu trước khi chạy. Không lưu trace/screenshot/video chứa thông tin đăng nhập. Kết quả và giới hạn nằm trong tài liệu tích hợp.
