# Listening — review và bàn giao GitHub 25/11/2026

Checkpoint trước push. Làm trên `feat/web-auth-integration`, xuất phát từ
`cf2b6d372fe6891b06f7a6c9791d0bd2bcce93e6`. Không sửa SQL/history/coordinator,
không reset/stash/clean, không tác động DB cá nhân `toeic_db`.

## Phạm vi và thứ tự commit

1. `e8c2176420945df5ff81ee1d91ab39148db403f4` —
   `fix(web): connect teacher demo authoring navigation`: 9 path theo
   [manifest demo](SPRINT13_Web_Demo_20261125_manifest.md).
   Editor dùng chung nằm trong TeacherLayout/RequireTeacher, nối link học liệu/nhóm,
   mục chưa có đánh dấu disabled. Bỏ assertion demo cũ buộc P2 trả insufficient409:
   dữ liệu Listening hiện đã publish; không tạo attempt phụ để kiểm một giới hạn cũ.
   Các hunk route/nav media grant chưa vào commit này.
2. `5fad2ed10fb52112a00e7f93596b4f58dfa35201` —
   `feat(listening): add owner media reuse grants and real storage reads`: 15 path theo
   [manifest feature](Listening_Media_Reuse_Grant_20261125_manifest.md).
   Owner API/UI cấp/thu hồi/cấp lại, permission/ownership từ session, idempotency,
   audit và khóa transaction; publication vẫn recheck grant. Revoke không xóa
   published version/pin/result. Đọc object thật, storage lỗi trả503; không sinh media giả.
3. Phạm vi tài liệu/diễn tập Listening: 6 path —
   `frontend/web/tests/listening-grants.live.spec.js`,
   `frontend/web/tests/tv2-defense.live.spec.js`,
   `docs/TV2_Defense_Demo.md`, `docs/Listening_Demo_Data_20261125_manifest.md`,
   `docs/Listening_Content_Verification.md` và tài liệu này.
   Đây là browser harness cần credential/checkpoint local, không kèm dữ liệu DB/media.

Hai file dùng chung `routes.jsx` và `TeacherLayout.jsx` được stage bằng patch riêng:
commit UI demo lấy snapshot trước media-grant, commit feature chỉ lấy hunk media còn lại.
Tổng cộng **28 path duy nhất**, không cộng lặp hai file chung.
Author/committer từng commit mới: `pthanh137 <sleapf137@gmail.com>`; tác giả các
commit TV1/TV2 cũ giữ nguyên. Không stage AGENTS.md, Problems, PostmanR2, tatus,
settings, data-driven test nền hoặc tài liệu V34/V35 chưa bàn giao.

## Kiểm chứng đúng nguồn

- Index A `85cbad9cfc702a289e8601996ffd5b449a079715`: xuất blob index thành nguồn
  FE độc lập, 107/107 unit, lint và build đạt. BE tree giống HEAD trước đó, tái dùng
  bằng chứng committed cũ đúng phạm vi; không chạy lại BE cho commit chỉ đổi UI.
- Index B `59d1d7929e2be85f7ca8d7f59cc309dec4fd82c0`: nguồn BE/FE xuất từ index,
  không lấy code/test/fixtures chưa stage. Maven Wrapper `package` với 8 class:
  MediaReuseGrantIntegrationTest, S3CompatibleMediaStorageClientTest,
  ListeningAttemptIntegrationTest, GroupPublicationIntegrationTest,
  ModerationIntegrationTest, PracticeSubmissionIntegrationTest,
  SectionAttemptIntegrationTest và AuthFlowIntegrationTest:
  **124 thực thi, 0 failure/error/skipped**; FE **107/107**, lint/build đạt.
- DB test duy nhất tái sử dụng `127.0.0.1:55469/toeic_tv2_merge_test_20261124`;
  không dựng lại cluster hoặc hàng loạt fixture DB.
- JAR B có50SQL đúng layout, byte bằng blob Git. 29SQL trong JAR demo cũ dùngCRLF,
  export index dùngLF; nội dung bằng nhau sau chuẩn hóa newline, Git không đổiSQL
  và44history đã áp dụng validate đạt. Không gọi hai JAR bằng nhau từng byte.
- Lint mặc định bỏ qua export dưới `target`; đã kiểm lại source tường minh:
  `npm run lint -- --no-ignore src tests vite.config.js playwright.config.js`.
- Cached diff check/scan actual local-secret values, token/private key/signed URL,
  conflict marker/artifact đạt cho từng index. `git diff --check` toàn working tree
  vẫn có whitespace nền tại `AGENTS.md:1`; giữ nguyên, không nhận toàn tree sạch.
- Evidence log/proof ở `backend/target/listening-delivery-20261124/` ignored.
  Các bản xuất index chỉ phục vụ compile, không phải nhánh/worktree và không kèm
  credential/storage/DB. Không đưa target/JAR/log/dump lên Git.

Các runbook cũ giữ nguyên nhãn working-tree của từng lượt. 124 test cũ vẫn là
bằng chứng working tree; 124 test mới ở trên là lượt riêng trên index B. Không đổi
nhãn các broad run cũ thành full pass của nguồn mới. Không chạy lại full suite local.
CI cuối và smoke sau đồng bộ phải đọc checkpoint cuối/đường dẫn CI bàn giao thực tế;
ở checkpoint tài liệu này chưa nhận CI mới xanh.

## Remote đã review

Fetch tất cả remote cấu hình (`origin`): feature remote vẫn `cf2b6d3`; develop mới
`e2c9701e9d67b44d70b8fb8c3b58bf32dcbd7579`. Commit mới ngoài feature:

- `8b3035e`: mobile tích hợp content/AI/services/SRS.
- `f86c74b`: merge PR74.
- `e2c9701`: merge PR73 (chứa cf2b6d3).

Diff HEAD cũ → develop chỉ19path `frontend/app`, không đổi BE/Web/SQL/permission/
publication/attempt/coordinator. Không có collision migration mới. Merge chỉ develop
đã fetch, không nhập remote nhánh thành viên khác. Remote mobile còn fallback demo
trong auth/AI/vocabulary; test mobile không được nhận là Web/API/DB thật hoặc bằng
chứng AI/media thật. Không sửa phạm vi mobile trong lượt Listening.

PR [#73](https://github.com/chienvoxn/toeic-learning-platform/pull/73) đã được nhóm
merge lúc 23:49 ngày24/11 (+07). Không còn mở để cập nhật, không tự mở PR thay thế
hoặc merge PR mới. Tài khoản Git Credential Manager được kiểm `/user` là `pthanh137`
trước push; connector đọc GitHub là tài khoản khác, không dùng nó để nhận danh tính push.

## Nội dung Listening và giới hạn vẫn giữ

Xem [bảng từng câu](Listening_Content_Verification.md): **ASR hỗ trợ 11/14**, không
phải nghe xác nhận bởi người. **P1-1, P1-3, P2-3 chưa xác minh**: ảnh chưa rõ laptop/
sách, ASR chưa xác nhận “Anna will”. Không sửa báo cáo thành toàn bộPass.
Không sửa object/version published hoặc regrade attempt/result cũ. Test phát audio
bằng Chrome không thay cho duyệt tính chính xác nội dung.

FullMock FULL-LR/v1, số liệu blueprint và chuyển SectionA/B vẫn OPEN, chưa seed.
MINI-LAB là demo ngắn, không là đềTOEIC chính thức. G1 cóAPI nguyên tử, chưa cóUI
batch hoàn chỉnh. Không quy đổi điểmTOEIC, không claimNFRp95 hoặc toànSprint3Done.

## Code Git khác dữ liệu local — phục hồi trên máy khác

Git chỉ chứa API/UI/test harness/tài liệu, **không chứa DB demo, audio hoặc credential**.
Để chạy lại đúng dữ liệu/UUID trong runbook phải giữ riêng, truyền qua kênh riêng:

- Backup mới của lượt này: `backups.local/sprint13-demo-20261125/listening-delivery-20261125.custom`,
  PostgreSQL17.11 custom, 369400bytes;
  SHA256 `a2e62229848ddab8cb8eec09fd8b6fa16ed3d15a3a0a6cf6d907a57fd22a8d90`.
  Snapshot logic mới giữ cả attempt đã phát sinh sau checkpoint grant; không dừng
  hoặc sửa DB nguồn. TOC đọc đạt; **chưa restore thử**, không nhận kiểm restore đã đạt.
- `backups.local/sprint13-demo-20261125/listening-grants-handoff.custom`, PostgreSQL17.11,
  366787bytes; SHA256 `e38f07d674f01cc53a6ec2b863f7ffe3f19ae386bf652235de4540c66f13e3f2`.
  Đây là backup checkpoint sau grant và trước các thao tác người dùng phát sinh sau đó;
  không nhận nó chứa mọi attempt mới hiện tại. TOC/hash đã kiểm; chưa restore thử
  backup này trong lượt hiện tại. Giữ cả `roles-no-passwords.sql` cùngfolder.
- `backups.local/sprint13-demo-20261125/defense-media/`: 12object và manifest key/hash;
  audio gốc10WAV/PNG ở `backend/target/tv2-defense-20261125/media/`.
- Nếu tiếp tục nguyên Seaweed runtime, giữ toàn bộ
  `backend/target/demo-runtime-20261123/storage/data`, gồm metadata/volume và fileẩn
  `.mini_*`/keys; không chỉ copy WAV ra ngoài rồi bỏ metadata. Không in keycontents.
- Credentials theo role: `backend/target/sprint13-demo-20261124/demo-credentials.json`;
  S3 private config: `backend/target/demo-runtime-20261123/storage/credentials.json`.
  Không đưa vào Git/chat, không public bucket. Cấu hình mới phải khớp DB/owner/grants.
- Local vận hành: `backend/target/sprint13-demo-20261124/start-demo-services.ps1`,
  `stop-demo-services.ps1`, `backend-selection.json`; giữ runtime JAR và proof tương ứng.
  Không chạy Maven clean ở backend/target đang chứa các thành phần này.

Restore vào **DB demo mới riêng**, review roles trước, dùng `pg_restore --exit-on-error`
với custom backup; không restore đè nguồn/toeic_db, không chạy migrate để kiểm restore.
Kiểm schema/history/count/hash và media object trước khi dùng. Giữ validate-only.
Nếu cần toàn bộ trạng thái mới nhất, phải backup bổ sung sau khi dừng thao tác demo;
backup nêu trên chỉ bảo đảm checkpoint đã ghi, không tự coi là bản live hiện tại.

Demo hiện hành: Web5173, BE8080, DB `55469/toeic_tv2_merge_http_20261124`, schema public,
profile local-demo với datasource override; S3 private Seaweed9000. Lượt review này
không thay datasource, storage hoặc credential và giữ các dịch vụ cần thiết chạy.
