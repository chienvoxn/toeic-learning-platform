# TV2 — tích hợp develop Listening và contract tiếp theo

## Mốc và phạm vi

Base feature `d3d6f0b0de535d505b17be091ffe41ce86faedd3`; bảo toàn cả012c257/e851f11.
Fetch origin21/11: develop `98d81c0d2d2d3033fd018ecb9d5bb05780b61b39`, chưa có commit
thêm sau mốc này. Hai commit sau a1a85b6: 1ae9c13 (tannhat1607, feat: listening) và98d81c0
(merge PR65). Không viết lại author/committer hoặc nhập nhánh thành viên chưa vào develop.

Develop thêm upload/presign Teacher media, attempt media/content, client playback và transcript
sau GRADED. Không đổi SQL migration hoặc permission catalog. Các endpoint mới dùng quyền
content/question management hiện có hoặc đúng permission nguồn Practice/Section cùng ownership.
Không thay năm route lưu đáp án/submit/result; không thêm TEST_VERSION runtime hoặc publication đề.
Toàn bộ SQL, fingerprint và policy historical V34/V35/V40 của feature giữ nguyên.

Hai conflict được kết hợp:

- IdentitySecurityConfig: giữ cả test:manage:self/any cho DRAFT và route Teacher media của develop.
- PracticeSetupPage: giữ Part1–7, Section type LISTENING/READING tường minh và giới hạn đã chốt;
  dùng layout/playback integration của develop. Không thêm state sectionType trùng, không ép
  mọi SECTION thành Reading. Khóa tua/nghe một lần chỉ là tùy chọn giao diện, không contract
  TOEIC chính thức hoặc enforcement server. Expiry vẫn không tự submit khi không có request.

Không sửa6 tracked/35 untracked nền hoặc settings. Snapshot/hash gốc và credential proof ở
backend/target/tv2-sync-start-20261121.json và tv2-git-auth-proof.json; không chứa token.
Tài khoản Git được kiểm qua credential thực dùng với GitHub /user, không suy từ user.name/email.

## Kiểm chứng và giới hạn bằng chứng

Full local cũ1201 có5 failure RBAC trước sửa
registry; working-tree88 và staged88 của d3d6f0b là bằng chứng riêng. Không gọi chúng là full pass
của cây mới. Không lặp full local khi CI sẽ chạy clean verify PostgreSQL17/Java21 đúng SHA push.

Browser media dùng PCM fixture tổng hợp để kiểm decode/playback và transcript trước/sau submit.
Backend Listening tests dùng HTTP mock MinIO; chưa là kiểm bucket S3/MinIO vận hành thực tế.
Develop có fallback WAV/SVG demo khi storage không tải được; âm mẫu không phải audio bài TOEIC
thực. Không nhận UI chọn Listening hoặc fallback demo là ngân hàng audio học thuật hoàn thiện.
Không chạy local-demo.sql hoặc Docker provisioning vào DB đang dùng; chỉ DB test riêng.

| Bằng chứng cây tích hợp trước push | Kết quả |
|---|---|
| Nguồn backend xuất từ index, compile/package | 499 blob; POM chỉ đổi đường dẫn source/resources/output; không dùng code nền chưa stage |
| ListeningAttempt + S3 client + Section + Practice submission + EXAM-04 + AuthFlow | 117/117, 0 fail/error/skip, DB mới toeic_tv2_sync_20261121 tại lab55463 |
| Frontend Node/lint/build | 94/94 test; lint/build exit0, có cảnh báo có sẵn và từ code develop |
| Browser production build | 9/9: DRAFT3, Practice/Section5, media playback/transcript1. Ca mới cần bấm Mở đáp án sau submit; đã sửa bước test, không đổi logic sản phẩm |
| HTTP thật/JAR index/PostgreSQL riêng + HTTP mock S3 | 18 request đạt; upload/presign quyền Teacher, owner attempt, pin/private media404, decode RIFF stream, transcript sau GRADED, visibility đổi thì stream bị chặn |
| SQL/JAR và bảo toàn | 43 resource SQL khớp source; migration/history policy không đổi so với d3d6f0b; 42 file nền/settings và526 file candidate nguyên hash |

HTTP chạy trên DB mới toeic_tv2_sync_http_20261121, không clone/restore vào DB nguồn.
Mock S3 chỉ kiểm plumbing HTTP, không xác nhận chữ ký/quyền bucket của một triển khai MinIO thật.
Git credential đã xác thực qua /user là pthanh137 (id199277454), repo push permission=true.
Credential chỉ trong bộ nhớ, không nằm trong Git URL/config hoặc file. Khi push sẽ kiểm lại và
buộc Git dùng đúng credential đã xác minh; không suy tài khoản push từ author/committer.

Report/log tạm ở backend/target/tv2-sync-{index-export,audit,http-proof}.json,
tv2-sync-focused-package.log và tv2-sync-browser.log; không đưa vào commit.
CI sẽ được kiểm trên SHA cuối sau push; các test local trên không được gọi là CI pass.

## Những quyết định đã chốt — không hỏi lại

- EXAM-04 DRAFT own/any, expected revision, exact current published Question/GroupVersion,
  nguyên nhóm/thứ tự, audit nguyên tử và không sửa bản đã pin giữ nguyên contract.
- ADMIN-03 cho content/question: DRAFT→IN_REVIEW→APPROVED→PUBLISHED, trả sửa có lý do,
  approval đúng revision, bất biến sau publish; ACTIVE+ADMIN+review/publish permission tách biệt.
  R1 theo contributor của vòng publication, clone đúng current pointer, legacy thiếu bằng chứng
  bị chặn; không bỏ guard khi chỉ có một Admin. Đây không tự động là API TestVersion đã triển khai.
- M1 owner/grant/type/visibility và permission publication độc lập tiếp tục áp dụng. Teacher upload
  từ develop không đồng nghĩa có đầy đủ media provisioning/reuse-grant workflow.
- G1 đã có định hướng batch group+question APPROVED, ID/order/revision/pointers tường minh,
  audit/pointer atomic, không tự bỏ/clone/repin thành viên. Implementation G1 vẫn chưa có;
  publication nhóm/câu thuộc nhóm tiếp tục bị chặn. Không hỏi lại thiết kế G1 đã xác nhận.
- Practice/Section đã có snapshot, expiry/late Section submit có request và raw statistics;
  không quy đổi điểm TOEIC, không worker EXAM-03 trong phạm vi này.

## Gói quyết định còn thiếu cho EXAM-02

Đây là đề xuất để chốt cho bước sau, chưa là contract và chưa triển khai.

| Quyết định thực sự còn thiếu | Phương án đề xuất | Tác động EXAM-02 |
|---|---|---|
| Đưa TestVersion vào workflow ADMIN-03 và map permission/actor như thế nào? | Tái sử dụng nguyên nguyên tắc đã chốt cho content/question; thêm test:review và test:publish riêng, mặc định ADMIN; Teacher own/any submit. Cần persistence contributor/revision có bằng chứng thật, fail-closed legacy, không dùng test:manage:any thay publish. | Chỉ đề đã APPROVED và publish hợp lệ mới được tạo attempt; không dùng workflow riêng của PR61 để bỏ R1. |
| Blueprint nào là chuẩn xuất bản Full Mock: code/version, số câu/nhóm mỗi Part, thời lượng và thứ tự Section? | Blueprint version bất biến/configured; publish validate đúng blueprint đã chọn, không hard-code 200/120 phút từ tài liệu tham khảo. Cần chốt một cấu hình cụ thể trước phát hành Full Mock. DRAFT không cần đủ cấu trúc vẫn được lưu. | Pin blueprint/config/duration cùng TestVersion vào attempt; không giả Full Mock bằng snapshot Section hoặc dữ liệu thiếu. |
| Quy tắc fork TestVersion, gồm trường hợp bank đã đổi version? | Chỉ fork đúng current published của parent, một working DRAFT, khóa parent+expected revision; giữ sourceVersionId và cấp version number tăng. Nếu reference bank không còn current/valid, trả409 và yêu cầu chọn thay thế tường minh; không tự repin/clone/publish bank. | Giữ nguyên đề/attempt/kết quả cũ; có đường sửa đề mới mà không đổi version đã dùng. Cần xác nhận cách xử lý bank đổi trước triển khai endpoint fork. |
| Unpublish/retire có cho attempt đang làm tiếp tục không? | Ưu tiên retire để chặn start mới; attempt đã pin tiếp tục lưu/nộp/đọc kết quả theo expiry/source, đồng thời vẫn kiểm auth và media permission hiện hành. Không quay published về DRAFT, không xóa/đổi snapshot/đáp án. | Xác định rõ điều kiện start và resume/submit, tránh cắt ngang bài hoặc làm mất kết quả; đây là điểm chưa chốt cho TestVersion. |

Không mở lại quyết định G1 hoặc số câu/thời lượng Practice/Section đã chốt. Test publication chỉ
tham chiếu bank published; không xuất bản câu/nhóm kèm theo để đủ bộ đề. G1 là dependency khi
cần xuất bản nhóm mới; dữ liệu/media còn thiếu phải được báo rõ, không bypass để demo.
EXAM-02 có thể trả raw score như Practice/Section; conversion governance để EXAM-05 riêng,
không cần tự chốt công thức TOEIC trong gói publication này.
