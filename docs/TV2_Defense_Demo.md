# TV2 — dữ liệu và diễn tập bảo vệ 25/11/2026

> **Cập nhật media-grant 24/11/2026:** Listening P1–4 và Section đã publish qua
> workflow có grant và kiểm Web thật. Phần checkpoint điều tra cũ dưới đây được
> giữ làm lịch sử, gồm blocker đã giải quyết. Trạng thái hiện hành xem mục cập
> nhật cuối file và [runbook media](Listening_Media_Reuse_Grant_20261125.md).

Checkpoint chuẩn bị 24/11/2026. Nhánh `feat/web-auth-integration`, HEAD
`cf2b6d372fe6891b06f7a6c9791d0bd2bcce93e6`. Không stage/commit/push/merge PR
trong nhiệm vụ này. FE wiring chưa commit của lượt trước được giữ; BE/JAR vẫn
là nguồn đã bàn giao cf2b6d3 (BE giống merge9042dda). Không Sprint4/quên mật khẩu.

## Môi trường và tài khoản

- Web http://127.0.0.1:5173; API http://127.0.0.1:8080/api/v1.
- Health http://127.0.0.1:8080/api/health và `/api/health/database`: UP.
- PostgreSQL17.11 **127.0.0.1:55469/toeic_tv2_merge_http_20261124**, schema `public`,
  role vận hành lab `commit_review_test`, profile `local-demo` **có datasource override**.
  Flyway mode `validate`, JPA `validate`; 44 history rows/checksum giữ nguyên tớiV43.
- SeaweedFS S3 thật, private, loopback9000; dữ liệu cũ cùng cluster, không tạo cluster mới.
- Credentials local ignored: **`backend/target/sprint13-demo-20261124/demo-credentials.json`**.
  Dùng `roles.qa` cho Learner TV2, `roles.teacher` cho Teacher, `roles.admin` cho Admin;
  không chép password/token/OTP vào tài liệu/log. QA email `sprint13.qa.20261125@example.invalid`;
  Teacher `sprint13.teacher.20261125@example.invalid`; Admin `sprint13.admin.20261125@example.invalid`.
- ID QA `e621e5f0-8fe7-45e6-8466-b796f1cead0a`, Teacher `f25791ab-b8d9-4528-964e-2e99a1e4af75`, Admin `b463248b-e321-4aac-9b99-3fdcb64945df`.
  Account Learner khác dùng `roles.learner` để kiểm ownership404.
- Không truy cập/sửa `toeic_db` cá nhân hoặc DB nguồn55459. DB này là **clone lab**,
  không gọi là clone DB thành viên lâu dài. Giữ hai DB review/JUnit cũ, không tạo thêm.

## Dữ liệu trước/sau và tổ hợp thực sự dùng được

| Bảng | Trước | Sau |
|---|---:|---:|
| `questions` | 22 | 38 |
| `question_versions` | 24 | 40 |
| `question_choices` | 95 | 155 |
| `question_groups` | 8 | 11 |
| `question_group_versions` | 9 | 12 |
| `media_resources` | 1 | 12 |
| `media_reuse_grants` | 0 | 0 |
| `tests` | 5 | 6 |
| `test_versions` | 5 | 6 |
| `test_questions` | 0 | 2 |
| `attempts` | 55 | 59 |
| `attempt_question_snapshots` | 326 | 346 |
| `attempt_answers` | 257 | 277 |
| `attempt_part_results` | 86 | 92 |

Học liệu current published/API member đều **2**, không thêm học liệu lượt này.
Đã thêm **16 câu**:14 Listening và2 câu P7;3 nhóm(P3/P4/P7);10 audio tiếng Anh
tổng hợp local bằng Microsoft David Desktop từ lời tự soạn và1 minh họa office
tự vẽ. Tất cả có nhãn **DEMO**, không tài liệu/đề TOEIC chính thức. Asset tone1 giây
cũ được giữ riêng, không nhận tone là nội dung Listening. Không tạo điểm/progress giả.

| Part | Đủ điều kiện bank trước→sau | Tổng câu hợp lệ cho riêng Part | Thực tế |
|---|---:|---|---|
| P1 | 0 → 0 | Không có | Blocked: APPROVED, chưa published/media grant |
| P2 | 0 → 0 | Không có | Blocked: APPROVED, chưa published/media grant |
| P3 | 0 → 0 | Không có | Blocked: APPROVED, chưa published/media grant |
| P4 | 0 → 0 | Không có | Blocked: APPROVED, chưa published/media grant |
| P5 | 4 → 4 | 1, 2, 3, 4 | Browser thật đạt với 4 câu |
| P6 | 4 → 4 | 4 | Browser thật đạt với 4 câu |
| P7 | 5 → 7 | 2, 3, 4, 5, 7 | Browser thật đạt với 4 câu |

P6 giữ nguyên nhóm4. P7 từ hai nhóm3+2 thành ba nhóm3+2+2, nên chọn4 là hai
nhóm2, không cắt nhóm. Các số khác không có trong bảng có thể bị409 hợp lệ.
Reading Section **8 câu/20 phút**, đủP5–7, đã chạy Web thật; đây là cấu hình
luyện tập sản phẩm, không blueprint Full Mock hay cấu trúc TOEIC chính thức.

**P1 chọn4**: trước lượt này không có câuP1, không phải DB trống; sau bổ sung có4
versionP1 **APPROVED nhưng parent vẫn DRAFT/pointer NULL**, nên vẫn không dùng được.
P2 có5 versionAPPROVED (4 mới+tone cũ), P3/P4 mỗi3APPROVED; cả4 Part có **0 eligible**.
Không gán APPROVED thành published. Published Reading hiện4+4+7=15 câu.

Script chuẩn bị dùng POST/PATCH Teacher API → submit → Admin khác contributor
approve; nhómP7 publish-batch G1 nguyên tử. Transcript được đối chiếu lời TTS đầy
đủ; khi bổ sung transcript trước publication dùng revoke có lý do → PATCH DRAFT →
submit/approve lại đúng workflow, không sửa version đã published.

## Bằng chứng, IDs và trạng thái AC

| Phần | Kết quả thật |
|---|---|
| Teacher DRAFT | Tạo trên Web, sửa/reload, revision0→1→2, stale409; versionDRAFT tham chiếu đúng nguyên nhómP7 hai câu |
| Publication P7/G1 | API thật publish-batch group+members nguyên tử; không có UI batch, không nhận UI batch Done |
| PracticeP5/P6/P7 | Chọn→thay→bỏ→reload không còn đáp án→lưu lại→reload→submit→result/lời giải/history/replay đạt |
| Reading Section | 8 câu/20 phút, đủP5/6/7; cùng lưu/reload/result/replay đạt |
| Ownership/an toàn | Learner khác GET attempt/result/media đều404; result trước submit409; payload trước submit không key/lời giải/transcript |
| Snapshot/DB | Question/group/order pins, answer đã chọn, correct key/explanation snapshot, score và Part rows khớp API; không chấm lại bank |
| Restart | Dừng đúng demo stack, native pg_ctl dừng sạch; khởi động lại cùng DB/profile validate;16 bảng nghiệp vụ count/hash nguyên,12 S3 objects khớp hash; Web login/history/result/pin đọc lại đạt |
| Listening | **BLOCKED**, negative testP1–4/Listening Section409 đúng; chưa Learner playback/submit/result Listening |
| Full Mock/worker | Chưa có blueprint được duyệt/published test đủ điều kiện; không seed FULL-LR/MINI-LAB, không nhận demo worker mới đạt |

Không nhận toàn bộ Sprint3 Done. Quy đổi TOEIC/NFR p95 chưa có bằng chứng.

| Luồng đã nộp bằng QA | Attempt ID | Số câu | Điểm thô thật |
|---|---|---:|---:|
| p5 | `88e0be13-405b-44da-9dc5-75e5f57cce77` | 4 | 1 |
| p6 | `317f5900-4cef-4616-8592-3f579a820d22` | 4 | 1 |
| p7 | `27e9d3b6-b940-4d36-ae91-adb51cef0822` | 4 | 1 |
| reading | `a27a6cc5-800f-47f2-bffe-d7758c691f8f` | 8 | 1 |

Teacher test `0de95033-ba97-442e-bc22-ee42abcb169e`, version `79d724c8-31ee-4005-adef-dded1a1613e8`, revision2,
typeREADING/DRAFT, blueprintNULL. Có **2 ref** trong `test_questions` và documentJSON,
không phải đề published/immutable Full Mock. GroupP7 `dd521283-2629-449b-9f51-57c503a9d59f`, version
`ce794a33-817a-4659-a5a7-8c0f870991ca`; hai question versions `b2e8835b-466b-4d5d-acd0-30c714663e37`,
`cb8f182b-1f9c-4d66-ab87-fcb762ddcf84` theo thứ tự1,2.
P1 mẫu question `64bfd9af-5e84-4383-801f-3c60ac2b3664`, version `ae2d65ac-eb69-4bd5-9860-019b84a186f7`.
Toàn bộ16question/3group/11asset IDs trong local `data-checkpoint.json` bên dưới;
không tạo lại khi tiếp tục. ImageID `ebd80a1b-5318-49bb-8647-9588b6120739`.

## Truy vết Listening — điều kiện hỏng và gói cần chốt

Đã kiểm từng predicate, không chỉ dựa vào409:

- Publisher `b463248b-e321-4aac-9b99-3fdcb64945df` ACTIVE+ADMIN, đủ question:publish/question:review/content:read;
  không creator/editor/submitter; approvedRevision=revision, evidence complete.
- Tất cả12media thuộc Teacher `f25791ab-b8d9-4528-964e-2e99a1e4af75`, publisher không owner và **0 active
  reuse-grant**. Đây là điều kiện M1 **không đạt**; manage:any không thay owner/grant.
- TypeAUDIO/IMAGE, visibilityAUTHENTICATED và quyền đọc publisher đều đạt.
  Schema `media_resources` **không có cột status**, không suy trạng thái giả.
-12object đọc S3 trực tiếp, bytes/hash khớp file gốc trước/sau restart; anonymous403.
  Khi resume, S3 từng dừng; đã mở lại đúng data. Đây là sự cố môi trường riêng:
  publication M1 chỉ kiểm metadata/owner/grant, không gọi S3, nên không quy409M1
  thành lỗi object/storage. Một publish thử lại trả409 và giữ revision/pointer/decisions.
- Source không có endpoint/service cấp-thu hồi grant; repository chỉ đọc/khóa.
  M1 đang tuân contract đã chốt trong ADMIN03, không có căn cứ đổi publisher subject.
- Luồng stream cũ `loadObjectOrDemoFallback` còn có khả năng sinh WAV/SVG khi S3
  lỗi. Không dùng nó làm bằng chứng playback thật, không nhận LearnerListening Pass.
  Cần xử lý fail-closed riêng trước khi chứng nhận đường fallback/error của media.

**Đề xuất cần xác nhận (chưa triển khai):** giữ nguyênM1; bổ sung workflow owner
cấp/thu hồi reuse cho **actor ACTIVE cụ thể**, không tự grant, không ownership transfer,
không đổi quyền đọc Learner; khóa media→grant và audit nguyên tử. Đề xuất dùng quyền
quản lý media đang có kèm kiểm **đúng owner**, không cho manage:any tự cấp quyền
trên asset người khác. Cần chốt chủ thể được nhận grant (mọi actor ACTIVE hay chỉ
actor có quyền publication/authoring phù hợp) và giao diện/API cấp quyền. API dự kiến
`POST/DELETE /teacher/media/{id}/reuse-grants/{userId}` chỉ là phương án kỹ thuật.
Nếu muốn Admin quản trị grant của asset không sở hữu thì phải chốt quyền/contract
riêng. Không hỏi lại hay thay đổi M1/contributor đã chốt; không SQL grant để demo.

Sau khi workflow được duyệt/triển khai: owner cấp quyền hợp lệ, Admin publish lại
exact revisions; G1 nguyên nhómP3/P4; kiểm LearnerUI audio/image thật, URL hết hạn,
reload/submit/transcript/ownership và lỗi storage trả lỗi thật. Chưa làm các bước này.

## Checklist Web và kịch bản TV2 5–7 phút

1. **0–1 phút Teacher:** loginTeacher, `/teacher/exams`, mở testDEMO → biên soạn.
   Sửa mô tả DRAFT, lưu/reload xem revision; groupP7 hai ref cùngversion/thứ tự.
   Không bấm publish Full Mock hoặc gắn blueprint chưa duyệt.
2. **1–3 phút LearnerQA:** `/practice`, chọn **riêngP7,4câu** (bỏP5 mặc định nếu đang chọn),
   tạo bài; chọn/thay/bỏ, chờ “Đã lưu”, reload; chọn lại rồi nộp; xem raw score/lời giải.
   Hoặc chọnP5=4/P6=4. Không dùng Dashboard preset30 khi bankP5 chỉ4.
3. **3–4 phút:** history mở lại kết quả; cho thấy đúng/sai/bỏ trống/Part từ DB.
   Chuyển accountLearner khác để GET bàiQA bị404, không lộ dữ liệu.
4. **4–5 phút:** Reading Section8/20, chọn một đáp án và nộp sớm; Part5–7 và lịch sử.
   Không mở AI trong Section (guard server đúng contract).
5. **5–7 phút Admin/DB:** show groupP7 published/pointer/order, snapshot/answers/result
   bằng SELECT dưới đây; mở moderation evidence cho ListeningAPPROVED/pointerNULL,
   giải thích grant blocker. G1 batch dùng API chuẩn bị, chưa có nút UI batch.

Thao tác đều dùng backend/DB thật, không HTTP fixtures hay mock. Điểm là kết quả
chọn đáp án thật, không cần cố làm đúng để trình diễn. Key chỉ mở trong admin hoặc
result **sau submit**. Không chiếu credential file/Mailpit OTP/session bảng khi bảo vệ.

## SELECT show DB đã chạy kiểm đúng schema

Kết nối đúng demo55469, dùng phiên read-only. Các truy vấn bên dưới đã chạy trong
`BEGIN READ ONLY` rồi rollback. Dừng nếu connection guard không đúng DB/schema/port.
Không SELECT*users/session/OTP. Querykey là **ADMIN ONLY**; queryresult ràng GRADED,
actor/attempt cụ thể. Eligibilitybank theo code hiện tại, **không chứng nhận playback**:
storage/media completeness phải kiểm riêng như phần blocker trên.

### Kết nối (phải đúng trước khi show)

```sql
SELECT current_database() AS database_name,current_schema() AS schema_name,inet_server_addr() AS host,inet_server_port() AS port;
```

### Học liệu current published đọc được của member

```sql
SELECT c.content_id,v.content_version_id,v.title,c.visibility,v.status
FROM learning_contents c JOIN learning_content_versions v
  ON v.content_version_id=c.current_published_version_id AND v.content_id=c.content_id
WHERE c.status='PUBLISHED' AND v.status='PUBLISHED' AND v.archived_at IS NULL
  AND v.published_at<=CURRENT_TIMESTAMP AND c.visibility IN ('PUBLIC','AUTHENTICATED')
ORDER BY c.content_id;
```

### Số câu thực sự đủ điều kiện bank (current source, nguyên nhóm)

```sql
WITH pool AS (
select q.question_id,v.question_version_id,v.group_version_id,q.group_id,q.part_id,
                       p.part_number,q.topic_id,v.difficulty,q.created_at,v.group_question_order,
                       (q.status='PUBLISHED' and v.status='PUBLISHED'
                        and v.published_at is not null and v.published_at<=CURRENT_TIMESTAMP and v.archived_at is null
                        and t.code='SINGLE_CHOICE' and v.question_text ~ '[^[:space:]]'
                        and (NULL::integer is null or q.topic_id=NULL::integer)
                        and (NULL::varchar is null or v.difficulty=NULL::varchar)
                        and p.part_number between 1 and 7
                        and ((p.part_number in (1,2,5) and q.group_id is null and v.group_version_id is null)
                         or (p.part_number in (3,4,6,7) and g.group_id is not null
                          and gv.group_id=g.group_id and g.status='PUBLISHED' and gv.status='PUBLISHED'
                          and g.current_published_version_id=gv.group_version_id
                          and gv.published_at is not null and gv.published_at<=CURRENT_TIMESTAMP and gv.archived_at is null
                          and not exists(
                            select 1 from questions sibling
                            left join question_versions sv on sv.question_version_id=sibling.current_published_version_id
                            left join question_group_versions sg on sg.group_version_id=sv.group_version_id
                            where sibling.group_id=g.group_id
                              and ((sibling.status in ('PUBLISHED','ARCHIVED')
                                    and (sv.question_version_id is null or sv.group_version_id is null or sg.group_id<>g.group_id))
                                or (sv.question_version_id is null and exists(
                                    select 1 from question_versions history where history.question_id=sibling.question_id
                                      and history.group_version_id=gv.group_version_id and history.published_at is not null))))))
                        and (select count(*) from question_choices c where c.question_version_id=v.question_version_id
                             and c.is_correct)=1
                        and (select count(*) from question_choices c where c.question_version_id=v.question_version_id)
                            =case when p.part_number=2 then 3 else 4 end
                        and not exists(select 1 from question_choices c where c.question_version_id=v.question_version_id
                             and (c.content !~ '[^[:space:]]' or c.display_order<1
                               or c.display_order>case when p.part_number=2 then 3 else 4 end
                               or c.choice_label<>chr(64+c.display_order)))) as eligible
                from questions q join question_versions v
                  on v.question_id=q.question_id and v.question_version_id=q.current_published_version_id
                join toeic_parts p on p.part_id=q.part_id join question_types t on t.question_type_id=q.question_type_id
                left join question_groups g on g.group_id=q.group_id
                left join question_group_versions gv on gv.group_version_id=v.group_version_id
                where p.part_number in (1,2,3,4,5,6,7) or v.group_version_id in (
                  select v2.group_version_id from questions q2
                  join question_versions v2 on v2.question_version_id=q2.current_published_version_id
                  join toeic_parts p2 on p2.part_id=q2.part_id where p2.part_number in (1,2,3,4,5,6,7))
                order by p.part_number,q.created_at,q.question_id
), units AS (
  SELECT CASE WHEN group_version_id IS NULL THEN 'q:'||question_version_id::text
              ELSE 'g:'||group_version_id::text END AS unit_key,
    min(part_number) AS part_number,count(*)::int AS size,
    bool_and(eligible) AND count(DISTINCT part_number)=1
      AND (bool_and(group_version_id IS NULL) OR
        (count(*)=count(group_question_order) AND min(group_question_order)>0
         AND count(DISTINCT group_question_order)=count(*))) AS ready
  FROM pool GROUP BY 1
)
SELECT p.part_number,COALESCE(sum(u.size) FILTER(WHERE u.ready),0) AS eligible_questions,
  COALESCE(array_agg(u.size ORDER BY u.size) FILTER(WHERE u.ready),'{}'::int[]) AS whole_unit_sizes
FROM toeic_parts p LEFT JOIN units u USING(part_number)
WHERE p.part_number BETWEEN 1 AND 7 GROUP BY p.part_number ORDER BY p.part_number;
```

### Question → exact version → choices (CHỈ màn hình quản trị)

```sql
SELECT p.part_number,q.question_id,q.current_published_version_id,v.question_version_id,
  v.version_number,v.status,v.group_version_id,v.group_question_order,
  c.choice_id,c.choice_label,c.content,c.is_correct,v.explanation
FROM questions q JOIN toeic_parts p USING(part_id)
JOIN question_versions v USING(question_id) JOIN question_choices c USING(question_version_id)
WHERE q.created_by='f25791ab-b8d9-4528-964e-2e99a1e4af75'::uuid
  AND q.question_id IN ('bcfe077e-b49d-4dd2-86fc-2cd1b31bfe77'::uuid,'64bfd9af-5e84-4383-801f-3c60ac2b3664'::uuid)
ORDER BY p.part_number,v.version_number,c.display_order;
```

### Group/version và thứ tự thành viên (nhóm P7 published)

```sql
SELECT g.group_id,g.current_published_version_id,gv.group_version_id,gv.status,
  p.part_number,q.question_id,v.question_version_id,v.group_question_order,v.status AS question_status
FROM question_groups g JOIN question_group_versions gv USING(group_id)
JOIN question_versions v ON v.group_version_id=gv.group_version_id
JOIN questions q ON q.question_id=v.question_id JOIN toeic_parts p USING(part_id)
WHERE g.created_by='f25791ab-b8d9-4528-964e-2e99a1e4af75'::uuid AND g.group_id='dd521283-2629-449b-9f51-57c503a9d59f'::uuid
ORDER BY gv.version_number,v.group_question_order;
```

### Media metadata và đúng quyền publisher (không URL/secret/transcript)

```sql
SELECT m.resource_id,m.resource_type,m.mime_type,m.file_size,m.duration_seconds,m.visibility,
  m.owner_id,m.owner_id='b463248b-e321-4aac-9b99-3fdcb64945df'::uuid AS publisher_is_owner,
  EXISTS(SELECT 1 FROM media_reuse_grants g WHERE g.media_id=m.resource_id
    AND g.user_id='b463248b-e321-4aac-9b99-3fdcb64945df'::uuid AND g.revoked_at IS NULL) AS publisher_has_active_grant
FROM media_resources m WHERE m.owner_id='f25791ab-b8d9-4528-964e-2e99a1e4af75'::uuid ORDER BY m.resource_id;
```

### Test/version và các ref exact version trong DRAFT (không gọi là published)

```sql
SELECT t.test_id,t.title,t.current_published_version_id,v.test_version_id,v.status,
  v.version_number,v.blueprint_id,d.revision,tq.question_order,tq.question_version_id,
  tq.question_group_version_id
FROM tests t JOIN test_versions v USING(test_id)
LEFT JOIN test_version_drafts d ON d.test_version_id=v.test_version_id
LEFT JOIN test_questions tq ON tq.test_version_id=v.test_version_id
WHERE t.created_by='f25791ab-b8d9-4528-964e-2e99a1e4af75'::uuid AND t.test_id='0de95033-ba97-442e-bc22-ee42abcb169e'::uuid
ORDER BY v.version_number,tq.question_order;
```

### Attempt → snapshot → đáp án/kết quả đã lưu (chỉ sau GRADED)

```sql
SELECT a.attempt_id,a.attempt_source_type,a.status,a.started_at,a.expires_at,a.submitted_at,
  s.question_order,s.question_version_id,s.question_group_version_id,
  ans.selected_choice_id,ans.correct_choice_id,ans.is_correct,ans.explanation_snapshot
FROM attempts a JOIN attempt_question_snapshots s USING(attempt_id)
LEFT JOIN attempt_answers ans ON ans.attempt_id=a.attempt_id AND ans.question_version_id=s.question_version_id
WHERE a.user_id='e621e5f0-8fe7-45e6-8466-b796f1cead0a'::uuid AND a.attempt_id='27e9d3b6-b940-4d36-ae91-adb51cef0822'::uuid AND a.status='GRADED'
ORDER BY s.question_order;
```

### Thống kê đúng/sai/bỏ trống từ result snapshot, không tính lại bank

```sql
SELECT a.attempt_id,a.correct_count AS saved_raw_score,a.total_questions,
  count(*) FILTER(WHERE ans.is_correct) AS correct,
  count(*) FILTER(WHERE ans.selected_choice_id IS NOT NULL AND NOT ans.is_correct) AS incorrect,
  count(*) FILTER(WHERE ans.selected_choice_id IS NULL) AS unanswered,
  round(100.0*a.correct_count/NULLIF(a.total_questions,0),2) AS accuracy
FROM attempts a JOIN attempt_answers ans USING(attempt_id)
WHERE a.user_id='e621e5f0-8fe7-45e6-8466-b796f1cead0a'::uuid AND a.attempt_id IN ('27e9d3b6-b940-4d36-ae91-adb51cef0822'::uuid,'a27a6cc5-800f-47f2-bffe-d7758c691f8f'::uuid)
  AND a.status='GRADED'
GROUP BY a.attempt_id,a.correct_count,a.total_questions ORDER BY a.attempt_id;
```

### Thống kê Part đã lưu + phân biệt sai với bỏ trống

```sql
SELECT r.attempt_id,p.part_number,r.total_questions,r.correct_count,r.accuracy,
  count(*) FILTER(WHERE ans.selected_choice_id IS NOT NULL AND NOT ans.is_correct) AS incorrect,
  count(*) FILTER(WHERE ans.selected_choice_id IS NULL) AS unanswered
FROM attempts a JOIN attempt_part_results r USING(attempt_id) JOIN toeic_parts p USING(part_id)
JOIN attempt_answers ans ON ans.attempt_id=a.attempt_id AND ans.part_number_snapshot=p.part_number
WHERE a.user_id='e621e5f0-8fe7-45e6-8466-b796f1cead0a'::uuid AND a.attempt_id='a27a6cc5-800f-47f2-bffe-d7758c691f8f'::uuid AND a.status='GRADED'
GROUP BY r.attempt_id,p.part_number,r.total_questions,r.correct_count,r.accuracy ORDER BY p.part_number;
```

## Chạy lại ngày mai, backup và checkpoint

Tại repo, PowerShell (giữ terminal không chạy seed song song browser):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File backend/target/sprint13-demo-20261124/start-demo-services.ps1
Invoke-RestMethod http://127.0.0.1:8080/api/health
Invoke-RestMethod http://127.0.0.1:8080/api/health/database
# Chỉ khi muốn dừng đúng demo; hiện các dịch vụ đang được giữ chạy:
powershell -NoProfile -ExecutionPolicy Bypass -File backend/target/sprint13-demo-20261124/stop-demo-services.ps1
# Thêm -StopDatabase dùng pg_ctl cho đúng cluster riêng, không xóa dữ liệu.
```

Không chạy chỉ profile local-demo vì datasource mặc định profile còn55459;
script cung cấp override55469/validate. Đã kiểm một lần stop/start FE/BE/PG/S3/Mailpit;
PG postmaster đổi, dữ liệu giữ nguyên; Java4052/dịch vụ khác không bị dừng.
Log/process metadata cũ trước restart được chép vào `restart-before/` để không mất
bằng chứng khi scriptstart ghi lại operating logs. Không Maven clean target trước demo.
KhiS3 dừng, start script rồi kiểm object thật; không chấp nhận WAV/SVG fallback.
AI/Internet không cần cho Practice/Reading/DRAFT/history local; thiếuAI báo giới hạn,
không tạo AI giả. Nếu BE/mạng lỗi, không nhận lưu thành công; kiểmhealth/retry cùngattempt.

Backup trước bổ sung: `backups.local/sprint13-demo-20261125/defense-before.custom`,
SHA256 `0c1bf8b24eb62f0fe47418b7f8e8d9bdd488f42194132d7ee605a44be5b37bc4`.
Backup sau dữ liệu và browser, trước restart: `backups.local/sprint13-demo-20261125/defense-rehearsed.custom`,
SHA256 `0ad85187f0b4f87b269299ef5732e9e538111a93e9c637ed995db6612501186c`, 355201bytes, TOC đọc đạt.
Media logical backup12object + manifest/hash ở `backups.local/sprint13-demo-20261125/defense-media/`;
credentials và dataS3 gốc giữ nguyên. Có roles-no-passwords.sql từ backup môi trường
cùng thư mục. **Không restore thử backup mới trong lượt này**; không gán bằng chứng
restore cũ. Không cần restore DB đangUP; nếu cần, restore DB đích mới riêng sau
kiểmendpoint, `pg_restore --exit-on-error --dbname=NEW_DEMO_DB <custom>`; không migrate
chỉ để kiểm restore, không restore đè nguồn/toeic_db.

Local ignored `backend/target/tv2-defense-20261125/` giữ:
- `start.json`/backup-before; inventory-before/prepared/final: số liệu, schema, history.
- `data-checkpoint.json`, `prepare_data.py`, `generate-media.ps1`, media/speech-text:
  IDs/hash/nguồn tự soạn, chạy lại theo checkpoint không seed lại toàn bộ.
- `idempotency.json`: count+hash bank/media/moderation12bảng nguyên sau rerun;
  sessions login mới không thuộc so sánh nghiệp vụ. Không chạy lại nếu đã có bằng chứng.
  Sau đó chỉ thêm guard kiểm datasource/API và system identifier trước mọi ghi;
  `preflight-guard.json` kiểm riêng guard bằng import, không chạy seed lại, bank/media hash nguyên.
- `browser-first.log`:6/6 ca (1 ca là negative Listening, **không ListeningPass**).
  `browser-draft-pin.log`:Teacher delta thêm nguyên nhómP7 và typeREADING đạt.
  `browser-after-restart-final.log`:login/Webhistory/pin/result/replay sau restart đạt.
  Log sau restart đầu có selector exacttext sai; đã sửa harness, không sửa product.
- `db-proof.json`:pin/order/selected/key/explanation/result/Part đối chiếuDB;
  `show-sql-validation.json`:mọi SELECT trong doc đã thực thi read-only.
- `listening-blocker.json`:predicate/quyền/object/rollback409, không signed URL/secret.
- `restart.json`:stop/start một lần,16bảng hash nguyên và12object khớp.

Không chạy lại full backend/FE chỉ để tăng bằng chứng. RuntimeFE/BE không sửa trong
lượt này; bằng chứng CIcf2b6d3 và107FE/lint/build trước được giữ đúng nguồn, không
gán cho data hay test mới. Chỉ thêm testbrowser scoped và tài liệu/tool local.
Source/background/settings/credential hashes kiểm từ start; index tiếp tục rỗng.
Chưa nhận toàn bộSprint3Done; chờ media-grant, LearnerListening, blueprint/chuyển
Section FullMock, UI G1 batch và các AC chưa kiểm.

## Manifest và lệnh kiểm đã thực thi

Hai path mới ngoài index của nhiệm vụ này:

| File | Phạm vi |
|---|---|
| `docs/TV2_Defense_Demo.md` | Runbook, số liệu/ID, SELECT read-only đã kiểm, kịch bản, blocker và checkpoint |
| `frontend/web/tests/tv2-defense.live.spec.js` | Chrome thật với API/DB: Teacher DRAFT, P5/P6/P7/Reading, negative Listening, đọc lại sau restart |

Tool local ignored mới trong `backend/target/tv2-defense-20261125/`:
`inventory.py`, `prepare_data.py`, `generate-media.ps1`, `checkpoint.py`,
`listening_blocker.py`, `restart_demo.py`, `render_doc.py`. Không sửa backend,
frontend runtime hoặc migration/coordinator trong lượt này. Không stage các file
nền/Problems/AGENTS.md/Postman R2/tatus/settings, credentials hoặc output/backup.
Toàn bộ source demo chưa commit của lượt trước vẫn giữ riêng trong manifest Sprint13.

Lệnh đã chạy (mỗi lượt có đúng phạm vi trong log; không phải một full suite chung):

```powershell
# Trong frontend/web, env trỏ credential local đã có; không dán giá trị password:
npx playwright test tests/tv2-defense.live.spec.js
# Lượt đầu lúc file có 6 ca: 6 đạt, gồm một ca negative Listening.
npx playwright test tests/tv2-defense.live.spec.js --grep 'Teacher creates'
# Delta thêm nguyên nhóm P7 vào DRAFT: 1 đạt.
npx playwright test tests/tv2-defense.live.spec.js --grep 'After demo restart'
# Sau sửa selector harness: 1 đạt; log lỗi cũ vẫn giữ.
.\node_modules\.bin\oxlint.cmd tests/tv2-defense.live.spec.js
# Lint đúng bản local của dự án: exit0.
```

Không cần chạy lại các lệnh này chỉ để tăng bằng chứng trước bảo vệ. Nếu tiếp tục
sửa dữ liệu/code thì chọn kiểm đúng phần đổi, đọc checkpoint trước để không nhân
attempt hoặc tạo thêm bank/media. Lệnh restart có checkpoint chống chạy trùng;
không chạy lại `restart_demo.py` khi `restart.json` đã complete.


## Cập nhật: Listening đã kiểm trên storage thật

Đã triển khai owner API/UI media-grant theo contract xác nhận; không SQL grant,
không đổi storage/bucket, không tạo lại audio/media/câu/nhóm.14versionP1–4 đã
publish qua workflow, P3/P4 G1 nguyên nhóm. Activegrants11, sourcebankeligible:
**P1=4/P2=4/P3=3/P4=3/P5=4/P6=4/P7=7**.
ListeningSection hợp lệ8–14; đã kiểm8câu/20phút. P3/P4 chỉ3câu, không chọn4 để
cắt nhóm. FullMockblueprint/chuyểnSectionA–B vẫn OPEN, chưa seed.

OwnerUI `/teacher/media`; cùng credentials local ở đầu tài liệu, không in secret.
JARdemo hiện là workingtree media-grant, không phải committed cf2b6d3. Startscript
đọc backend-selection.json để chạy đúngJAR mới; datasource vẫn55469 validate-only.

| Luồng Listening hiện hành | Attempt ID | Số câu | Raw score thật |
|---|---|---:|---:|
| p1 | `fb9f32ae-531e-433e-a59e-f69fde0d0813` | 4 | 0 |
| p2 | `23ea3f37-ef6a-4744-9b67-0780fffe9a58` | 4 | 1 |
| p3 | `c4421d13-64c2-426d-81d5-933251be605f` | 3 | 1 |
| p4 | `d9431de8-73e8-4c1e-870d-1dc262bbe08c` | 3 | 1 |
| listening | `35e310f1-7cba-4390-9f23-49a2bae81dc3` | 8 | 1 |

Đã kiểm phátảnh/audio thật, chọn/thay/bỏ/reload/nộp/result/lời giải/transcript,
history/replay/ownership; key/transcript không lộ trướcsubmit. Hồi quy ngắnP5/P6/P7
đạt;59attempt cũ và pins/answers/Part rows giữ nguyên hash.
SignedURLhết hạn403, refresh/reload đọc đúng bytes; storage xuống trả503/UIaudioerror,
không syntheticWAV/SVG; restart cùng volume phát được lại, result vẫn đọc được.
P4 mởresulttimeout một lần, APIkiểm200/không lỗiBE; Webcheckriêng cùngattempt sau đó
đạt. Chưa xác định nguyên nhân lần timeout, không nhận đã sửa một sourcebug.

Currentcounts:38questions/40versions/11groups/12media/11grants/67attempts,
380snapshot/311answer/103Partresult; không có publishedFullMock/blueprint seed.
SELECT ở phần lịch sử vẫn chạy đúngschema và tự phản ánh số liệu mới; dùng ID
Listening ở bảng trên thay IDP7 khi muốn show snapshot/resultListening sauGRADED.
Manifest feature/data tách riêng trong hai tài liệu media. Không stage/commit/push,
không nhận toàn bộSprint3Done. Bằng chứng/localcheckpoint mới ở
`backend/target/listening-grants-20261125/`, xem runbookmedia để tiếp tục.
