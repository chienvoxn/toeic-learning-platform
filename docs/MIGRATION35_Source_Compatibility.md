# V35 compatibility — source tích hợp 20/11/2026

**Nối tiếp 22/11/2026:** [Hai lineage V36–V38 và release43](MIGRATION36_38_Source_Compatibility.md). Giữ chính sách và SQL/checksum V34/V35; không gán số test hoặc CI cũ cho cây tích hợp mới.

**Phần nối tiếp TV2 EXAM-04 (working tree 20–21/11):** [V40 và bằng chứng mới](EXAM04_Draft_Authoring_20261120.md).
Coordinator nhận thêm release40 và fingerprint bảng DRAFT mới; policy historical V34/V35,
SQL cũ và các giới hạn hỗ trợ ở báo cáo này giữ nguyên. Số39/40 history rows và test phía
dưới là bằng chứng của commit tích hợp trước, không phải kết quả của cây EXAM-04 mới.

Phạm vi lượt tích hợp được người dùng xác nhận: giữ nguyên chính sách V34 của
0e6b37c, nhận diện thêm hai lịch sử V35. Develop đã rà: a1a85b6. Không triển khai
thêm tính năng ADMIN, G1, media hay EXAM-04 trong commit tích hợp.

## Chính sách và resource

- DB trống/prefix hợp lệ tới V33 vẫn chọn RAG34; V34 đã có giữ đúng Settings/RAG.
- History hợp lệ chưa có V35 chọn ADMIN-03. V35 đã có phải khớp script,
  description, checksum và full schema; Settings34+RAG35 chưa được hỗ trợ và bị
  chặn trước mutation. Không có tùy chọn location do người vận hành tự chọn.
- V35 ADMIN-03 checksum-949250551 và V35 RAG checksum359517897 được giữ nguyên
  Git blob tại db/history/v35/admin03 và db/history/v35/rag. Common không có34/35.
- V36/V37 và hai SQL lịch sử V34 không đổi. V38 bổ sung moderation cho RAG35;
  V39 bổ sung soft-delete hội thoại cho ADMIN35. Feature có sẵn chỉ kiểm invariant,
  không seed lại grant đã thu hồi, không suy/backfill contributor legacy.
- MigrationConfiguration được dùng chung bởi Boot/test/CLI/CI; chọn một34 và
  một35 dưới advisory lock. Kiểm config, prefix/checksum/catalog/full metadata
  trước mutation và validate sau migration. Không repair/clean/baseline/outOfOrder,
  ignore migrations hay tắt validation. Giữ whitelist6 CHECK restore đã chứng minh.

V35 ADMIN-03 chỉ phụ thuộc schema trước34. V35 RAG thêm deleted_at/partial index
vào ai_conversations từV7, không có DDL xóa dữ liệu dù tên file có delete_cascade.
V36/V37 không phụ thuộc identity35. Code RAG develop có query deleted_at, nên phải
hoàn thiện schema39 trước business beans/JPA. Các prefix36…38 hợp lệ vẫn có đường
nâng cấp sau khi một migration riêng đã commit và tiến trình bị gián đoạn.

## Bằng chứng theo nguồn

Candidate đã được diễn tập độc lập trên PostgreSQL17.11:8/8 đường trống, backup
demo V33, Settings34, RAG34, Settings34+ADMIN35, RAG34+ADMIN35, RAG34+RAG35 và DB
đã migrate tới37 bằng đúng binary0e6. Tất cả migrate/validate39,40 history rows,
Boot/JPA/health200 hai lần và CLI đạt; mọi row/history cũ giữ nguyên. Schema
normalized cùng70b821f949d4804f8bbbcfeedbe9255c3321c4ed67264e6429593afd9d2e75b9.

45 ca unique candidate có bằng chứng pass qua hai lượt:36 đạt/9 lỗi hạ tầng
Windows trong lượt đầu,29/29 của3 nhóm bị ảnh hưởng đạt khi retry với heap/DB memory
giới hạn. Không đổi guard/assertion/checksum. SQL/core/test compatibility áp dụng
source chính khớp candidate; khác biệt tích hợp là code RAG/auth/learning mới và
config RAG giữ cả PYTHON_RAG_BASE_URL ưu tiên, AI_BASE_URL dự phòng.

Không gọi45 ca hoặc ma trận candidate là kết quả source/staged mới. Kết quả source,
staged và merge commit ghi theo tree/SHA trong audit backend/target/integration-*.
CI1140/working-tree1150 của0e6 và CI1034 của develop là bằng chứng cũ.
Không push hoặc tạo PR trong lượt này.

## Vận hành và giới hạn

CLI vẫn dùng backend/scripts/migrate-database.ps1 với datasource tường minh,
Action Migrate/Validate; không hỗ trợ standalone Flyway chọn locations tùy ý.
DB sai checksum/history/schema/config phải dừng, không fallback để validate xanh.

BackupV33 là demo local78 bảng1411 row; V34/V35 là fixtures SQL gốc, không phải
clone DB thành viên lâu dài. Chưa chứng minh mọi DB thành viên tương thích. Trước
DB thật: inventory, backup/restore đích riêng, rehearsal, so data/history/grants,
restart. V39 có ALTER/CREATE INDEX lấy khóa; cần đo trên kích thước thực tế.

Binary0e6 chặn history vượt37; sau nâng cấp39 không rollback binary đơn thuần.
Cần kế hoạch restore vào DB riêng và binary phù hợp, không xóa/sửa history/repair.
Không tác động toeic_db. SQL đã áp dụng tiếp tục bất biến; release sau39 cần
version set/fingerprint và rehearsal mới. [Cơ chế V34](MIGRATION34_Source_Compatibility.md).
