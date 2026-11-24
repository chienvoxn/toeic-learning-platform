# Manifest Listening demo — dữ liệu và tool local (tách khỏi media feature)

Không tạo lại audio, question, group, storage hoặc DB/cluster. Không SQL cấp grant,
đổi owner/publication pointer, repair, baseline hay tắt validate.

| Path/local artifact | Phạm vi |
|---|---|
| frontend/web/tests/listening-grants.live.spec.js | Chrome/API/PG/S3 thật; owner UI, 5luồngListening, Reading, revoke/expiry/storage error/recovery; private env gate |
| docs/TV2_Defense_Demo.md | Banner/cập nhật kết quả mới, giữ checkpoint cũ và SELECT đã kiểm |
| docs/Listening_Demo_Data_20261125_manifest.md | Manifest data/tool này |
| backend/target/listening-grants-20261125/publish_existing.py | Chỉ owner API PUTgrant cho11asset cũ + exactapprovedversions/G1, checkpoint chống lặp |
| backend/target/listening-grants-20261125/stop-storage-once.ps1 | Chỉ stop PID/executable/data marker của Seaweed demo; giữdata/keys, archive operating logs |
| backend/target/sprint13-demo-20261124/start-demo-services.ps1 | Local ignored: lựa JAR/hash mới trước stopBE, giữ PG/FE/S3 cùngdata; không migrate |
| backend/target/sprint13-demo-20261124/backend-selection.json | Local ignored: pointer/hash workingtree JAR, không secret |
| backend/target/listening-grants-20261125/ | Logs/checkpoints/POM chỉ chuyểnoutput và compile test phù hợp; JAR/proof, không source copy |
| backups.local/sprint13-demo-20261125/before-media-grants.custom | BackupDB trước mutation; không restore đè nguồn |
| backups.local/sprint13-demo-20261125/listening-grants-handoff.custom | BackupDB sau kiểm cuối và owner UI; hash/TOC đạt, chưa restore thử mới |

11grant owner→publisher được ghi bằng API, statuspublications14câuP1–4 và2nhómP3/P4
đổi qua workflow, audit nguyên tử. Tone cũ không publish; P7/Reading giữ nguyên.
8attempt mới là thao tác Web thật (5Listening/Section+3Reading), không seedđiểm.
Toàn bộ59attempt cũ/pins/answers/Part rows giữ nguyên hash. Private credentials,
signedURL và `.mini_*` key contents không vào source/log/tài liệu tracked.

Không chạy lại prepare_data.py của lượt điều tra cũ: script đó mô tả Listen bị409
trước khi có grant và đã có checkpoint. Chỉ đọc data-checkpoint.json để nhận diệnID.
Feature source ở manifest media riêng; không trộn tool/output/data dump vào commit.
