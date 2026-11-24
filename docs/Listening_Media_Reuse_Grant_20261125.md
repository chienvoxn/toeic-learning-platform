# Listening và media reuse grant — 25/11/2026

Nguồn hiện tại: **working tree trên cf2b6d3**, chưa stage/commit/push. JAR mới
`backend/target/listening-grants-20261125/build/backend-0.0.1-SNAPSHOT.jar`,
SHA256 `fd771fc63564e0b52ba6313299ff9a786e5829b0f34d7f9e9213c8216d93077c`. Không gán CI của cf2b6d3 cho delta này.
Không Sprint4; blueprint Full Mock và chuyển Section vẫn OPEN, chưa seed.

## Storage thực tế — giữ nguyên provider và audio

Runtime là **SeaweedFS4.55** (`weed.exe version`:4.55/a98c9e2/windows amd64),
không suy AWS/MinIO từ tên class. Endpoint **http://127.0.0.1:9000**,
private bucket **toeic-media**. Binaries/config/data vẫn ở
`backend/target/demo-runtime-20261123/storage/`. Không đổi bucket/credential,
không mở public, không upload lại hoặc tạo audio mới.

- Audio gốc10WAV và ảnhPNG: **`backend/target/tv2-defense-20261125/media/`**;
  lời gốc trong `speech-text.json`, giọng local Microsoft David Desktop.
- Object đã upload giữ nguyên12ID/metadata/hash, trong đó11asset thực hành mới
  và1tone cũ. Tone cũ vẫn APPROVED, không publish/không chọn làm Listening.
- Object key là keyS3 logic ở bảng dưới, **không phải mỗi WAV là một file dưới volume**.
  Seaweed lưu object vào `data/toeic-media_2.dat`, `.idx`, `.vif`; filer metadata
  `data/filerldb2`, master `data/m9333`, cùng file quản lý volume.
- Bảo toàn **toàn bộ `storage/data`**, kể cả fileẩn `.mini_*`/khóa mã hóa và
  volume/filer metadata; không in nội dung khóa, không init lại storage.
- Credentials local ignored `storage/credentials.json`; backend nhận qua env,
  không nằm trong code/tài liệu. Không có signed URL trong tài liệu/manifest.

| File gốc (dưới thư mục media trên) | Media ID giữ nguyên | Object key trong bucket toeic-media |
|---|---|---|
| `original-office.png` | `ebd80a1b-5318-49bb-8647-9588b6120739` | `image/ebd80a1b-5318-49bb-8647-9588b6120739.png` |
| `p1-1.wav` | `c8cd44fd-f99d-4119-9455-0a635656680f` | `audio/c8cd44fd-f99d-4119-9455-0a635656680f.wav` |
| `p1-2.wav` | `b1b28789-81a8-430f-ba16-d680a30336eb` | `audio/b1b28789-81a8-430f-ba16-d680a30336eb.wav` |
| `p1-3.wav` | `03816668-95a7-4544-bc9c-60408d99adaf` | `audio/03816668-95a7-4544-bc9c-60408d99adaf.wav` |
| `p1-4.wav` | `66659ff3-f30a-454b-a413-e90eb8d2b9f0` | `audio/66659ff3-f30a-454b-a413-e90eb8d2b9f0.wav` |
| `p2-1.wav` | `2c248235-88c7-42f0-8b8c-8ddf97d764fb` | `audio/2c248235-88c7-42f0-8b8c-8ddf97d764fb.wav` |
| `p2-2.wav` | `7a9cee8a-8ea7-4443-bd89-a3db6bf4d5ac` | `audio/7a9cee8a-8ea7-4443-bd89-a3db6bf4d5ac.wav` |
| `p2-3.wav` | `fb97f97d-b89c-4d97-bd9a-1f81ee001734` | `audio/fb97f97d-b89c-4d97-bd9a-1f81ee001734.wav` |
| `p2-4.wav` | `d862b0f5-27c8-4f85-b5cc-fb1691de0c3e` | `audio/d862b0f5-27c8-4f85-b5cc-fb1691de0c3e.wav` |
| `p3.wav` | `52d9855f-65c5-416c-866b-dce0c9b484e5` | `audio/52d9855f-65c5-416c-866b-dce0c9b484e5.wav` |
| `p4.wav` | `a7f99328-97c1-44fa-8a96-996ebc3cf6dc` | `audio/a7f99328-97c1-44fa-8a96-996ebc3cf6dc.wav` |

Đã đọc hash trực tiếp S3 và phát bằng Chrome trên Web Learner. Bản sao logical
12object đã xác minh nằm ở `backups.local/sprint13-demo-20261125/defense-media/`;
manifest private có key/hash, không signedURL. Không sao chép toàn cluster.

## Contract đã chốt và trách nhiệm service

Owner có sessionACTIVE + một permission `question:manage:self/any` hoặc
`content:manage:self/any` được cấp/thu hồi reuse cho **user tồn tại, khác chính mình**.
Actor lấy từ session; không nhận actor/owner trong body. Recipient không tự cấp
grant; ADMIN/manage:any không vượt owner. Grant không cấp role, publish permission,
đổi owner, visibility hoặc session; active theo `revoked_at`, không expiresAt.

`MediaReuseGrantService` kiểm permission/ownership, tuần tự mutation và ghi audit
nguyên tử. `MediaReuseGrantRepository` chỉ đọc media của owner và ghi đúng bảng
V20 hiện có. Không migration mới. Khóa sharedRBAC → userFKs theoUUID (KEY SHARE)
→ mediaFORUPDATE → grant; publicationM1/G1 giữ share-lock và recheck grant trong
transaction. Revoke commit trước publish làm publish fail; publish trước revoke
giữ bản đã publish. Revoke không tự sửa/xóa publication, snapshot hoặc kết quả.

`AttemptListeningMediaService` giữ ownership/source permission và chỉ trả transcript
sauGRADED. `S3CompatibleMediaStorageClient` chỉ tải object thật: đã bỏ nhánh tự sinh
WAV/SVG khi storage lỗi. Stream thất bại503 `MEDIA_STORAGE_UNAVAILABLE`.
`fallbackUrl` trongDTO là proxy authenticated đọc **cùng object thật**, không mock.

## API/UI thực tế

Prefix `/api/v1`, Bearer session hiện hữu. Owner UI **`/teacher/media`** có link ở
Teacher sidebar: chọn media sở hữu, nhập UUIDngười nhận, cấp/thu hồi/cấp lại và đọc
lại trạng thái. Không user search hay UI batch G1 mới.

| API | Kết quả |
|---|---|
| GET `/teacher/media?page=0&size=20` | Page metadata owner-only, không storageURL/key/transcript |
| GET `/teacher/media/{id}/reuse-grants?page=0&size=20` | Page userId/active/grantedAt/revokedAt |
| PUT `/teacher/media/{id}/reuse-grants/{userId}` | 200 Grant, body/query rỗng; active=true |
| DELETE cùng path | 200 Grant; thu hồi hoặc no-op |

Lặp trạng thái: `changed:false`, không timestamp/audit mới. Regrant mở kỳ cấp mới
trên cùng PK, audit giữ transitions. Self400; Guest401; thiếupermission403;
foreign/missingmedia404; recipientunknown404; query/body/UUIDsai400. Listpage0–10000,
size1–100. Lỗi DB/audit503 `MEDIA_OPERATION_UNAVAILABLE`, rollback toàn grant.
Recipient nhận grant vẫn chịu quyền đọc/publish/ACTIVE của endpoint sử dụng.

## Dữ liệu và cấu hình đã chạy được

OwnerTeacher đã cấp **11 active grants** cho Adminpublisher, rồi publish14 version
APPROVED đúngrevision/contributor. P3/P4 dùng G1 batch với3members/order1–3.
Giữ38questions/40versions/11groups/12media; không tạo/trộn lại bank/media.
Reading vẫn4/4/7eligible, không repin các bài cũ.

| Part/Section | Eligible hiện tại | Cấu hình thực sự hợp lệ | Web đã kiểm |
|---|---:|---|---|
| P1 |4|1–4, độc lập|4câu,4ảnh/4audio thật |
| P2 |4|1–4, độc lập|4câu,4audio thật; tone cũ không được chọn |
| P3 |3|3 (một nhóm)|3câu, một audio dùng chung đúnggroupversion |
| P4 |3|3 (một nhóm)|3câu, một audio dùng chung đúnggroupversion |
| ListeningSection |14nguồn|8–14, phải đủP1–4/nguyên nhóm|8câu/20phút, đủ4Part |
| P5 |4|1–4|hồi quy4câu |
| P6 |4|4|hồi quy4câu/nguyên nhóm |
| P7 |7|2,3,4,5,7|hồi quy4câu, không cắt nhóm |

Đây là cấu hình luyện tập/demo, **không blueprint TOEIC chính thức/Full Mock**.
Lưu/thay/bỏ→reload→submit→result/lời giải/transcript/history/replay đều qua Web/API/DB
thật. Key/transcript trướcsubmit không lộ; Learnerkhác nhận404. ỞReading không có
media nên không gán Reading thành bằng chứng audio. Không tạo điểm/progress giả.

| Luồng | Attempt ID | Số câu | Điểm thô thật |
|---|---|---:|---:|
| p1 | `fb9f32ae-531e-433e-a59e-f69fde0d0813` | 4 | 0 |
| p2 | `23ea3f37-ef6a-4744-9b67-0780fffe9a58` | 4 | 1 |
| p3 | `c4421d13-64c2-426d-81d5-933251be605f` | 3 | 1 |
| p4 | `d9431de8-73e8-4c1e-870d-1dc262bbe08c` | 3 | 1 |
| listening | `35e310f1-7cba-4390-9f23-49a2bae81dc3` | 8 | 1 |

P1raw0 là kết quả chọnA thật, không seed điểm. Bộ59attempt cũ và toàn bộ snapshot,
answer/result/Part rows giữ nguyên count+hash khi loại8attempt mới khỏi phép so sánh.
44Flyway rows/checksum nguyên. Revoke saupublish giữ pointer/pin/result và media
read của Learner theo policy hiện hành; owner đã regrant lại cuối kiểm.

## Bằng chứng đúng nguồn

`backend/target/listening-grants-20261125/` (ignored):

- `backend-proof.json`: **124 test BE riêng biệt/8class** qua hai lượt scoped;
 117test ngoài grant pass ở lượt đầu, grant7pass sau sửa hai testcase tự nhắm actor
 thay vì người khác để kiểmforeign404. Cópackage/JARđạt; không fullrepository.
- `maven-first.log` và `maven-grant-final.log`; fixture session trong failure log đã
 redacted, giữ assertion/status/totals. Các test S3 HTTPfixture không là E2E.
- `fe-unit-final.log`:107/107; `fe-lint-final.log`, `fe-build-final.log`:exit0, warning có sẵn.
 Không relabel bằng chứng broad/CI cũ cho delta mới.
- `browser-owner-final.log`:UIowner grant/revoke/regrant/reload và quyền trên source
 cuối; response được ràng đúng media/page/revision, không hiển thị grant cũ khi đổi asset.
- `browser-listening-first.log`:P1/P2/P3/ListeningSection + Reading đạt;
 P4timeout một lần khi mở result. APIresult/media sau đó200, không lỗiBE; cùng
 attempt P4được kiểm Webriêng đạt trong `browser-p4-recheck.log`. **Chưa xác định
 nguyên nhân timeout lượt đầu**, không gọi nó là sourcebug đã sửa.
- `browser-expiry-revoke.log`:2/2, revoke không đổi dữ liệu cũ; signedURLthật
 hết hạn403, URLmới đúnghash, dừng đúngS3 thì stream503/UIbáolỗi, result vẫn đọc;
 mở lại cùngstorage và playback thật phục hồi. Không mockHTTP/syntheticmedia.
- `data-proof.json`:đối chiếu selected/key/explanation/order/group pins và scoreDB;
 `publication.json`:exactversion/G1/11grant; `start.json`:baseline/hash/history.
- Tất cả9ca browser đã có kết quả đạt qua các lượt trên; không phải một lượt9/9.

## Vận hành và bảo toàn

Web5173, BE8080; DB **55469/toeic_tv2_merge_http_20261124**, schema `public`,
profilelocal-demo cóoverride, Flyway/JPAvalidate. S3giữ9000. Credentials theo role
ở `backend/target/sprint13-demo-20261124/demo-credentials.json`, không inpassword.
Dùng `roles.teacher` quản lý media, `roles.admin` publish, `roles.qa` làm bài,
`roles.learner` kiểm truy cập sai ownership.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File backend/target/sprint13-demo-20261124/start-demo-services.ps1
Invoke-RestMethod http://127.0.0.1:8080/api/health/database
# Chỉ khi cần khởi động lại BE có chủ ý:
# thêm -RestartBackend vào start script; script kiểm JAR/hash trước khi dừng BE.
# Sau demo, chỉ khi muốn dừng stack riêng:
powershell -NoProfile -ExecutionPolicy Bypass -File backend/target/sprint13-demo-20261124/stop-demo-services.ps1
```

Start đọc local `backend-selection.json` trỏ JARworkingtree ở trên, không chạy lại
JARcf2b6d3 thiếu grantAPI. Không compile nguồn cũ rồi nhận là đã cóAPI/UI mới.
Script giữ dịch vụ hiện có và cùng volume; runtime/operating log cũ được lưu trước
cycle vào `storage-before-cycle`. Không dừng mọi postgres/node/java, không đổibranch.
Không xóa/clean backendtarget/storage: đang có binaries, originalaudio, private
credential, keyvolume, JAR và proof. Lõi demo local không cầnAI/Internet; lỗiAI báo
giới hạn, lỗistorage báo lỗi thật, không bật mock để demo.

Backup trước cấpgrant/data: `backups.local/sprint13-demo-20261125/before-media-grants.custom`
(SHA trong `backup.json`). Backup cuối và status trong final-checkpoint của lượt này.
Backup bàn giao: `backups.local/sprint13-demo-20261125/listening-grants-handoff.custom`,
SHA256 `e38f07d674f01cc53a6ec2b863f7ffe3f19ae386bf652235de4540c66f13e3f2`,
366787 bytes; `pg_restore --list` đọc đạt, chưa restore thử backup mới.
Giữ backupmedia12object cũ do ID/bytes không đổi; không tạo bản sao nguyên cluster.
TOC/hash backupDB được kiểm, chưa diễn tập restore mới trong lượt này. Không truy cập
`toeic_db` cá nhân, không sửa SQL/history/coordinator, không tạo DB/cluster mới.

Xem manifest **media feature** và **demo data/tool** riêng bên cạnh. Toàn bộ công
việc mới còn ngoài index; chưa commit/push/PR. FullMockblueprint/SectionA–B và UIbatch
G1 vẫn ngoài phạm vi; không nhận toàn bộSprint3Done.
