# React + Vite

Google sign-in is implemented with Google Identity Services and backend ID token
verification. Set `GOOGLE_CLIENT_ID` on the backend; see the Vietnamese
[Google OAuth2 setup guide](../../docs/Google_OAuth2.md). Existing email accounts
require password confirmation before linking. No Client Secret is needed.

## Local preview and entry pages

Run `npm ci` and `npm run dev` in `frontend/web`, then open `http://localhost:5173`.
The development server proxies `/api` to the backend at `http://localhost:8080`.

- `/`: ENGLINGO landing page for visitors; learning dashboard for authenticated users.
- `/dashboard`: existing learning dashboard.
- `/login` and `/register`: existing authentication pages, linked from the landing page.

To preview the visitor landing page while signed in, use a private browser window.
The real backend is enabled by default (`VITE_USE_MOCK=false`). API requests use
`/api/v1`; legacy `VITE_API_BASE_URL=/api` is normalized to `/api/v1`.
Set `VITE_USE_MOCK=true` only to preview demo data, and restart Vite after changing
environment variables. There is no automatic fallback to mock on API failure.
Registration creates an ACTIVE account without OTP and returns to `/login`.
OTP is used only at `/forgot-password` for recovery. See
[password recovery policy](../../docs/Password_Recovery.md).
Private pages redirect unauthenticated visitors to `/login` and return them to
their requested page after login. `/health` checks backend and database publicly.
The sample exam routes `/tests`, `/tests/:testId/overview`, `/attempts/:attemptId`
and `/attempts/:attemptId/result` are public. In the default mode they provide a
local Reading sample with browser storage, raw grading and no account sync.
This is explicitly labelled local practice, not a backend exam integration.

Authenticated `/practice` uses the real PRAC-01/02 and EXAM-01 APIs: configure
Practice or Reading Section, save/replace/clear answers, reload the server snapshot,
submit and read persisted grading/explanations. It does not fall back to the public
sample routes. See the [isolated local demo guide](../../docs/LOCAL_DEMO_FE_BE_Integration.md)
for database/seed, generated demo credentials, service controls and verified limits.
After starting and seeding that demo, `npm run test:demo:e2e` runs Chromium against
the real frontend/backend, including the one-minute Section expiry case.

Run `npm test`, `npm run lint` and `npm run build` for frontend checks.
See [integration setup and supported flows](../../docs/Frontend_Backend_Integration.md).
`npm test` exercises adapters with HTTP response fixtures; the opt-in E2E commands
use the running services described in their guides.
Landing styles are scoped to `.landing-page`. The Vietnamese landing includes
a photo collage, dictation, shadowing, practice review and learning progress
sections. The footer introduces ENGLINGO and links to learning and account pages.
The feature illustrations are presentation graphics, not live session data.
Photos are stored locally in `public/assets/landing`; source URLs are listed
in that directory's `SOURCES.md`.

This template provides a minimal setup to get React working in Vite with HMR and some Oxlint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the Oxlint configuration

If you are developing a production application, we recommend using TypeScript with type-aware lint rules enabled. Check out the [TS template](https://github.com/vitejs/vite/tree/main/packages/create-vite/template-react-ts) for information on how to integrate TypeScript and Oxlint's TypeScript related rules in your project.

## Bảo toàn và tích hợp web-auth local

Xem [WEB_Auth_Integration](../../docs/WEB_Auth_Integration.md). `VITE_AUTH_MODE=remote` là mặc định, độc lập với `VITE_USE_MOCK`. Phiên remote dùng token và hạn server, restore bằng GET me, không fallback tài khoản mẫu. `npm run test:auth:e2e` cần frontend/backend thật và DB kiểm thử riêng.
