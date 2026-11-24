# Đối chiếu nội dung Listening demo P1–4

Ngày kiểm: **24/11/2026**, chuẩn bị bảo vệ 25/11. Đây là **dữ liệu demo tự soạn, không phải đề TOEIC chính thức**.

Nguồn: working tree trên `cf2b6d372fe6891b06f7a6c9791d0bd2bcce93e6`, có delta media-grant chưa commit. Không gán CI của commit cho delta. Nhánh `feat/web-auth-integration`; lượt này không stage/commit/push.

## Kết luận và giới hạn nghe

Đã lấy lại đúng **14 question version, 2 group version, 10 WAV và 1 PNG** từ media API của **5 attempt GRADED** dưới đây. Đã đối chiếu pin câu hỏi/lựa chọn/thứ tự/nhóm/media và kết quả lưu với checkpoint trước; chỉ đọc đúng version ID đã pin, không chọn latest hoặc current published pointer.

Đã chạy nhận dạng **trực tiếp trên bytes WAV vừa tải từ object của attempt** bằng Windows `System.Speech`, recognizer `MS-1033-80-DESK`, `en-US`, `DictationGrammar` tự do. Không đưa transcript, đáp án, tên file hoặc speech-text/script tạo giọng đọc vào grammar/prompt. Đã xem trực tiếp PNG tương ứng. Không gọi dịch vụ ASR bên ngoài.

**11/14 câu có bằng chứng nội dung hỗ trợ đáp án từ ASR**; bảng ghi “Khớp (ASR)” hoặc “Khớp (ASR + ảnh)”. **P1-1, P1-3, P2-3 chưa xác minh**. Chưa có lỗi nội dung được xác nhận chắc chắn để sửa phiên bản. Chưa có người nghe xác nhận bất kỳ clip nào trong lượt này: nhãn “Khớp” giới hạn ở đối chiếu ASR/ảnh, **không phải Pass nghe thực tế bởi người hoặc Pass toàn bộ chất lượng Listening**.

ASR nhầm nhãn A/B/C/D, một số từ và tên riêng; confidence dưới đây là điểm engine, không phải xác suất đáp án đúng. Phần transcript là dữ liệu lưu, không được dùng để lấp chỗ ASR không rõ. Nhãn người nói trong P3 được nhận dạng không ổn định. Timing trong JSON nhận dạng là vị trí tương đối của engine, không dùng làm timestamp tuyệt đối.

Không sửa bank, object, publication, attempt hoặc kết quả; không tạo attempt mới vì chưa có lỗi đã xác minh và chưa có bản sửa được publish. Chỉ đăng nhập tài khoản demo để đọc API, có thể phát sinh session/audit đăng nhập. Không chạy migration/build/full test; không truy cập `toeic_db`.

## Môi trường và chuỗi bằng chứng

- Web `http://127.0.0.1:5173`; API `http://127.0.0.1:8080/api/v1`.
- DB riêng `127.0.0.1:55469/toeic_tv2_merge_http_20261124`, schema `public`, system ID `7711587379292605296`; profile `local-demo` với datasource override, validate-only.
- Storage SeaweedFS, endpoint loopback `http://127.0.0.1:9000`, private bucket `toeic-media`. Object key AUDIO là `audio/<media-ID>.wav`, IMAGE là `image/<media-ID>.png`. Không suy provider từ tên class S3.
- Đọc `GET /attempts/{id}`, `/result`, `/media` với owner QA demo. URL ký chỉ dùng trong bộ nhớ để tải bytes; không lưu/in signed URL, token hoặc credential. Nội dung transcript ở đây lấy sau submit.
- Đối chiếu DB: `attempt_question_snapshots.question_version_id/question_group_version_id` → exact `question_versions`, `question_choices`, `question_resources` hoặc `question_group_version_resources`; key/explanation so với **result đã lưu**. Toàn bộ nội dung choices/text/ID từ API pin khớp exact version đọc DB.
- Bằng chứng local ignored: `backend/target/listening-content-verification-20261125/pinned-content.json`, `audio/`, `image/`, `asr/<media-ID>.json`, scripts `collect_pinned.py`, `recognize.ps1`, `build_report.py`. Hash bytes tải xuống khớp hash input ASR; không dựa tên file để đoán lời thoại.
- Audio gốc giữ tại `backend/target/tv2-defense-20261125/media/`; không tạo lại/ghi đè. Bản sao bytes audit có tổng dung lượng khoảng 6 MB, không copy cluster. Credentials chỉ tham chiếu `backend/target/sprint13-demo-20261124/demo-credentials.json`, role `qa` để mở attempt; không ghi password vào tài liệu.

| Loại attempt | ID | Trạng thái / số câu |
|---|---|---|
| p1 | `fb9f32ae-531e-433e-a59e-f69fde0d0813` | GRADED / 4 |
| p2 | `23ea3f37-ef6a-4744-9b67-0780fffe9a58` | GRADED / 4 |
| p3 | `c4421d13-64c2-426d-81d5-933251be605f` | GRADED / 3 |
| p4 | `d9431de8-73e8-4c1e-870d-1dc262bbe08c` | GRADED / 3 |
| listening | `35e310f1-7cba-4390-9f23-49a2bae81dc3` | GRADED / 8 |

Có thể mở Web `/practice/attempts/<id>/result` bằng owner QA, rồi xem lại media của đúng attempt. Không dùng màn hình chọn bài mới để thay cho attempt đã pin trong báo cáo.

## Bảng từng câu: nội dung nhận dạng → đáp án → kết quả lưu

Ký hiệu Pn-x là marker trong `questionText`, không phải thứ tự ngẫu nhiên trong attempt. “Đáp án lưu” dưới đây là key sau chấm, không phải lựa chọn Learner. Mapping UUID chính xác ở mục định danh.

| Câu | Thông tin nhận dạng từ WAV thực tế | Đáp án tương ứng | Key lưu / lời giải lưu (bỏ tiền tố DEMO DATA) | Kết luận |
|---|---|---|---|---|
| P1-1 | ASR: “a laptop is on the desk” (0.663); các đoạn sau nhận ra driving a car / room is empty / train is leaving (đoạn cuối 0.392). | A — laptop trên bàn, nếu ảnh xác nhận là laptop. | A — A laptop is on the desk. | Chưa xác minh |
| P1-2 | ASR: “be a plant is beside the desk” (0.776); ba mô tả khác là under the cheer [chair?] / woman swimming / books in a bag. | B — cây bên cạnh bàn. | B — A plant is beside the desk. | Khớp (ASR + ảnh) |
| P1-3 | ASR: “CIA red book is on the desk” (0.776); đoạn A thành “8:00 shows 12”, B “the desk is outside”, D “Celanese opening the window”. | C theo nội dung ASR, nhưng chưa xác định vật đỏ là sách. | C — A red book is on the desk. | Chưa xác minh |
| P1-4 | ASR: “deed o'clock shows nine” (0.825); các mô tả còn lại laptop closed / plant has no leaves / clock on floor. | D — đồng hồ chỉ 9 giờ. | D — The clock shows nine. | Khớp (ASR + ảnh) |
| P2-1 | ASR: “what time does the meeting began” (0.628), “a at 9:00” (0.440), “be in the office”, “sea by e-mail”. | A — At nine o clock. | A — The meeting begins at nine. | Khớp (ASR) |
| P2-2 | ASR: “where is the report” (0.768); “a on Tuesday”, “be on your desk” (0.854), “see about 10 people”. | B — On your desk. | B — The report is on your desk. | Khớp (ASR) |
| P2-3 | ASR: “who will call the customer” (0.869); “the next week”, “be at the station”, nhưng đáp án thứ ba thành “Seattle will” (0.576). | Chưa xác minh người/tên ở C; transcript ghi Anna will. | C — Anna will call the customer. | Chưa xác minh |
| P2-4 | ASR: “would you like some tea” (0.859); “a yes please” (0.638), “be at 330” (0.446), “see the blue folder”. | A — Yes, please. | A — Yes, please. | Khớp (ASR) |
| P3-1 | ASR: “our team meeting starts at nine on Monday” (đoạn 0.706). | A — Monday at nine. | A — The original audio states: Monday at nine. | Khớp (ASR) |
| P3-2 | ASR: “woman shall I bring the monthly report” (0.775), sau đó “yes please bring the report to room 200” (0.752). | B — The monthly report. | B — The original audio states: The monthly report. | Khớp (ASR) |
| P3-3 | ASR: “yes please bring the report to room 200” (0.752). | C — Room 200. | C — The original audio states: Room 200. | Khớp (ASR) |
| P4-1 | ASR: “the office library will close at six this evening” (0.682). | A — At six. | A — The original audio states: At six. | Khớp (ASR) |
| P4-2 | ASR: “please return borrowed books to the front desk before 530” (0.814). | B — The front desk. | B — The original audio states: The front desk. | Khớp (ASR) |
| P4-3 | ASR: “the library will open again at eight tomorrow morning” (0.605). | C — At eight. | C — The original audio states: At eight. | Khớp (ASR) |

## Đặc điểm từng Part và tính duy nhất của đáp án

P1 có 4 đoạn mô tả sau lời giới thiệu ở mỗi WAV; P2 có 1 câu hỏi và 3 đoạn phản hồi sau lời giới thiệu. Đếm từ các segment nhận dạng thực tế, không từ script tạo audio. Cần người nghe xác nhận nhãn và phát âm nguyên văn. P3/P4 mỗi nhóm 3 câu giữ thứ tự 1–2–3 và **cùng group version/cùng một audio ID/hash** trong cả attempt Part và Listening Section. Transcript chia sẻ đúng group version đã pin; không trộn audio P3 với P4.

Ảnh P1 thực tế là minh họa 640×360 có chữ ORIGINAL DEMO, bàn, màn hình xanh, chậu cây ở bên phải, hình chữ nhật đỏ trên bàn và đồng hồ kim dài 12/kim ngắn 9. Chưa đủ chi tiết nhận diện laptop và sách. Vì thế không nhận P1-1/P1-3 đạt yêu cầu “một mô tả đúng rõ ràng”. Đây là hạn chế hình minh họa đã quan sát; chưa khẳng định vật trong ảnh là vật khác với đáp án.

- **P1-1**: Ảnh có màn hình xanh và khung xám trên bàn, không thấy rõ bàn phím/đế laptop. Không đủ căn cứ phân biệt laptop với màn hình desktop. B/C/D không được ảnh hỗ trợ. Lời giải lưu khẳng định laptop, mạnh hơn bằng chứng ảnh.
- **P1-2**: Ảnh có chậu cây bên phải bàn; không có ghế bên trên cây, người bơi hoặc túi sách. B là mô tả duy nhất được ảnh hỗ trợ; chữ B/“be”, chair/“cheer” vẫn cần nghe xác nhận.
- **P1-3**: Ảnh chỉ thể hiện hình chữ nhật đỏ trên bàn, không thấy trang sách/gáy/bìa đủ rõ. Đồng hồ trông như 9 giờ, không phải 12. Không xác nhận C đúng rõ ràng chỉ từ lời giải. Các lỗi “CIA”/“Celanese” là sai khác ASR, chưa chứng minh audio sai.
- **P1-4**: Kim dài hướng 12, kim ngắn hướng 9. Màn hình dựng mở, cây có lá, đồng hồ ở phía trên bàn. D duy nhất phù hợp hình minh họa; cần người nghe xác nhận nhãn D.
- **P2-1**: Hỏi giờ; A trả thời gian, B trả địa điểm, C trả cách thức. Key/lời giải phù hợp nội dung nhận dạng; begin/began và cách đọc nhãn phải nghe lại.
- **P2-2**: Hỏi vị trí; chỉ B trả vị trí bản báo cáo, A là ngày và C là số người. Lời giải lưu phù hợp.
- **P2-3**: ASR không xác nhận được Anna. Không thay “Seattle” bằng “Anna” chỉ vì transcript/key đang lưu. C có thể là ý định đúng cho câu hỏi Who, nhưng phải nghe WAV để xác nhận tên, nhãn C và mức rõ ràng; A/B lần lượt thời gian/địa điểm.
- **P2-4**: Lời mời uống trà; chỉ A chấp nhận phù hợp. B trả giờ, C nêu vật. Lời giải lưu phù hợp; nghe lại nhãn và three thirty nếu cần xác nhận nguyên văn.
- **P3-1**: Chứng cứ trực tiếp về ngày/giờ; ba phương án Tuesday at six / Friday at two / Sunday at ten đều trái đoạn nhận dạng. Lời giải phù hợp.
- **P3-2**: Câu đề nghị mang báo cáo được xác nhận Yes. Không có ticket/gift/suitcase trong thông tin được nhận dạng. Lời giải phù hợp; nhận dạng nhãn người nói Man/Woman không hoàn toàn chính xác.
- **P3-3**: Địa điểm được chỉ ra trong mạch hội thoại cuộc họp; Library/front desk/station không được hỗ trợ. Lời giải phù hợp; quan hệ phòng 200 với cuộc họp là suy luận ngữ cảnh rất gần, không phải câu “meeting is in room 200” nguyên văn.
- **P4-1**: Hỏi đóng cửa: six this evening, không nhầm eight là mở cửa sáng hôm sau. Nine/noon không có căn cứ. Lời giải phù hợp.
- **P4-2**: Trả sách tại front desk; kitchen/room 200/station không xuất hiện như nơi trả. Lời giải phù hợp.
- **P4-3**: Eight là giờ mở ngày mai; five thirty là hạn trả sách, six là giờ đóng tối nay, ten không có căn cứ. Lời giải phù hợp.

## Question/version/group/media và vị trí đã kiểm

Tất cả question version dưới đây có `version_number=1`, status đọc được `PUBLISHED`. Chỉ các UUID này nằm trong phạm vi; không suy sang phiên bản khác hoặc attempt khác.

| Câu | Question ID | Question version ID | Group version / thứ tự trong nhóm | Audio media ID | Vị trí trong attempt |
|---|---|---|---|---|---|
| P1-1 | `64bfd9af-5e84-4383-801f-3c60ac2b3664` | `ae2d65ac-eb69-4bd5-9860-019b84a186f7` | Không nhóm | `c8cd44fd-f99d-4119-9455-0a635656680f` | p1 #2, listening #1 |
| P1-2 | `71ebbc9a-376f-4bca-9e37-0bd44c3aa817` | `f492b101-4c7a-411b-8da5-1bd67c1e3f09` | Không nhóm | `b1b28789-81a8-430f-ba16-d680a30336eb` | p1 #1 |
| P1-3 | `bafc2123-38ec-4e85-843c-6eabaaa7714c` | `75a1488b-f16b-4ebe-92f3-815452c04524` | Không nhóm | `03816668-95a7-4544-bc9c-60408d99adaf` | p1 #3 |
| P1-4 | `a8441e82-49fc-44d2-bc12-8ccd276a2638` | `507c9d91-1b1c-4c47-a1c2-14fa836ab3f3` | Không nhóm | `66659ff3-f30a-454b-a413-e90eb8d2b9f0` | p1 #4 |
| P2-1 | `09e866fc-0c9a-49cb-b273-e4bf15f055ac` | `e9b5951b-1b38-4a07-93d8-b1cd4e6eaf40` | Không nhóm | `2c248235-88c7-42f0-8b8c-8ddf97d764fb` | p2 #1 |
| P2-2 | `753a42b6-30f5-4e41-92ac-3a05ee3dea0b` | `df25157c-4a47-49f5-93c9-9310d0890371` | Không nhóm | `7a9cee8a-8ea7-4443-bd89-a3db6bf4d5ac` | p2 #2 |
| P2-3 | `df745191-b108-4d93-b913-b2a0e3d51848` | `1c4afa96-8ba4-46f1-a10f-a2f1749460cf` | Không nhóm | `fb97f97d-b89c-4d97-bd9a-1f81ee001734` | p2 #4, listening #2 |
| P2-4 | `d8ea98f8-0d9f-40a7-8738-5396a8dfd825` | `d48aa18c-5570-4fbf-acb1-0c0a3b586006` | Không nhóm | `d862b0f5-27c8-4f85-b5cc-fb1691de0c3e` | p2 #3 |
| P3-1 | `58eeb15d-99c4-4412-9dcc-b6a475965028` | `3529d0a2-8194-4bc0-816a-f9f4a43105be` | `4b325d42-450d-4764-8780-de1794677f80` / 1 | `52d9855f-65c5-416c-866b-dce0c9b484e5` | p3 #1, listening #3 |
| P3-2 | `62f80b6b-87ed-4dfd-97af-6ff159e78d0e` | `63b5290f-06e6-4d95-ac07-28ef3318995e` | `4b325d42-450d-4764-8780-de1794677f80` / 2 | `52d9855f-65c5-416c-866b-dce0c9b484e5` | p3 #2, listening #4 |
| P3-3 | `6e25fc99-9921-43bd-a54f-a2f8cfd140fa` | `b527bd2f-5471-4aa5-8c71-18b8a4a69947` | `4b325d42-450d-4764-8780-de1794677f80` / 3 | `52d9855f-65c5-416c-866b-dce0c9b484e5` | p3 #3, listening #5 |
| P4-1 | `7c325d06-e085-413e-8368-b5961a217840` | `4a37a1dd-c522-483a-aa60-04b8b366607e` | `5c9bd957-0a62-442e-9e63-c3415083070d` / 1 | `a7f99328-97c1-44fa-8a96-996ebc3cf6dc` | p4 #1, listening #6 |
| P4-2 | `c7421022-0c10-4c2f-9fb6-b24984cbd924` | `a83342db-e136-4f19-b39c-d628e65ffe4c` | `5c9bd957-0a62-442e-9e63-c3415083070d` / 2 | `a7f99328-97c1-44fa-8a96-996ebc3cf6dc` | p4 #2, listening #7 |
| P4-3 | `a9fcee14-5cca-47b5-9503-2c39938e0943` | `cfc3f960-1152-47c9-957d-e3d8a72b89cd` | `5c9bd957-0a62-442e-9e63-c3415083070d` / 3 | `a7f99328-97c1-44fa-8a96-996ebc3cf6dc` | p4 #3, listening #8 |

Bốn câu P1 cùng pin IMAGE `ebd80a1b-5318-49bb-8647-9588b6120739`. P3 pin group `4b325d42-450d-4764-8780-de1794677f80` v1; P4 pin group `5c9bd957-0a62-442e-9e63-c3415083070d` v1.

## Câu hỏi, lựa chọn và key đúng trong pin

P1/P2 choices lưu chỉ là Statement A–D / Response A–C; các mô tả/phản hồi đầy đủ nằm trong audio và transcript link bên dưới. Không trình bày transcript như thể là text choices đã lưu.

### P1-1

[DEMO-TV2-P1-1] Original demonstration. Listen and choose the best statement.

Lựa chọn pin: A. Statement A; B. Statement B; C. Statement C; D. Statement D.

Key lưu: **A**, choice ID `90c510d5-90fa-41c7-8c91-02ad081a8a65`. Lời giải lưu: DEMO DATA, original material. A laptop is on the desk.

### P1-2

[DEMO-TV2-P1-2] Original demonstration. Listen and choose the best statement.

Lựa chọn pin: A. Statement A; B. Statement B; C. Statement C; D. Statement D.

Key lưu: **B**, choice ID `b57574e2-be98-4e6f-b5ac-18d5ec34eeac`. Lời giải lưu: DEMO DATA, original material. A plant is beside the desk.

### P1-3

[DEMO-TV2-P1-3] Original demonstration. Listen and choose the best statement.

Lựa chọn pin: A. Statement A; B. Statement B; C. Statement C; D. Statement D.

Key lưu: **C**, choice ID `737a4846-f130-4719-9c90-c87b00941805`. Lời giải lưu: DEMO DATA, original material. A red book is on the desk.

### P1-4

[DEMO-TV2-P1-4] Original demonstration. Listen and choose the best statement.

Lựa chọn pin: A. Statement A; B. Statement B; C. Statement C; D. Statement D.

Key lưu: **D**, choice ID `3fe95529-6ea1-4c72-addb-d117f0e80fd1`. Lời giải lưu: DEMO DATA, original material. The clock shows nine.

### P2-1

[DEMO-TV2-P2-1] Original demonstration. Choose the best response.

Lựa chọn pin: A. Response A; B. Response B; C. Response C.

Key lưu: **A**, choice ID `7a5d2223-b1b5-464c-b0ab-fe9a04966920`. Lời giải lưu: DEMO DATA, original material. The meeting begins at nine.

### P2-2

[DEMO-TV2-P2-2] Original demonstration. Choose the best response.

Lựa chọn pin: A. Response A; B. Response B; C. Response C.

Key lưu: **B**, choice ID `a3df3581-7c25-4deb-8cc2-1f27ce481c64`. Lời giải lưu: DEMO DATA, original material. The report is on your desk.

### P2-3

[DEMO-TV2-P2-3] Original demonstration. Choose the best response.

Lựa chọn pin: A. Response A; B. Response B; C. Response C.

Key lưu: **C**, choice ID `2111b11c-e4ca-4466-867e-e1d504d49899`. Lời giải lưu: DEMO DATA, original material. Anna will call the customer.

### P2-4

[DEMO-TV2-P2-4] Original demonstration. Choose the best response.

Lựa chọn pin: A. Response A; B. Response B; C. Response C.

Key lưu: **A**, choice ID `7d835f2e-c130-4b62-b160-88b33ab21ae3`. Lời giải lưu: DEMO DATA, original material. Yes, please.

### P3-1

[DEMO-TV2-P3-1] When is the meeting?

Lựa chọn pin: A. Monday at nine; B. Tuesday at six; C. Friday at two; D. Sunday at ten.

Key lưu: **A**, choice ID `e78d1960-05c6-4040-85b4-0baea809a14a`. Lời giải lưu: DEMO DATA, original material. The original audio states: Monday at nine.

### P3-2

[DEMO-TV2-P3-2] What should the woman bring?

Lựa chọn pin: A. A ticket; B. The monthly report; C. A gift; D. A suitcase.

Key lưu: **B**, choice ID `1b7e3cd9-eb25-4182-9ac4-1796a1ebec80`. Lời giải lưu: DEMO DATA, original material. The original audio states: The monthly report.

### P3-3

[DEMO-TV2-P3-3] Where is the meeting?

Lựa chọn pin: A. The library; B. The front desk; C. Room 200; D. The station.

Key lưu: **C**, choice ID `b818291c-b8e3-4f9f-868d-83fabd76e551`. Lời giải lưu: DEMO DATA, original material. The original audio states: Room 200.

### P4-1

[DEMO-TV2-P4-1] When does the library close?

Lựa chọn pin: A. At six; B. At eight; C. At nine; D. At noon.

Key lưu: **A**, choice ID `ca2922fa-8513-418d-b077-eada427224bf`. Lời giải lưu: DEMO DATA, original material. The original audio states: At six.

### P4-2

[DEMO-TV2-P4-2] Where should books be returned?

Lựa chọn pin: A. The kitchen; B. The front desk; C. Room 200; D. The station.

Key lưu: **B**, choice ID `92be852a-db47-4314-a4d4-21103ae6ad0e`. Lời giải lưu: DEMO DATA, original material. The original audio states: The front desk.

### P4-3

[DEMO-TV2-P4-3] When does it open tomorrow?

Lựa chọn pin: A. At five thirty; B. At six; C. At eight; D. At ten.

Key lưu: **C**, choice ID `7936af12-21be-49e4-be51-8548a13ff753`. Lời giải lưu: DEMO DATA, original material. The original audio states: At eight.

## Transcript lưu và nhận dạng độc lập từng object

Transcript không phải bằng chứng đã nghe. Các đoạn ASR dưới đây giữ nguyên lỗi nhận dạng, không sửa theo transcript. Không cung cấp timestamp tuyệt đối do engine trả vị trí segment không ổn định. Người nghe có thể phát bản audit local theo đúng media ID hoặc mở attempt result; không chia sẻ signed URL hết hạn.

### Media 03816668-95a7-4544-bc9c-60408d99adaf

Loại AUDIO; object key `audio/03816668-95a7-4544-bc9c-60408d99adaf.wav`; MIME `audio/wav`; 621958 bytes.

SHA256: `ea6b4584fbc30cb43f0429bc9b9e105b8101ff67b941177515dff462d8d3fc9b`.

**Transcript lưu trên link đúng version:** This is original demonstration material. A. The clock shows twelve. B. The desk is outside. C. A red book is on the desk. D. Someone is opening the window.

**ASR tự do từ WAV thực tế:**

1. This is original demonstration material — confidence 0.670.
2. 8:00 shows 12 — confidence 0.824.
3. be the desk is outside — confidence 0.819.
4. CIA red book is on the desk — confidence 0.776.
5. D Celanese opening the window — confidence 0.831.

### Media 2c248235-88c7-42f0-8b8c-8ddf97d764fb

Loại AUDIO; object key `audio/2c248235-88c7-42f0-8b8c-8ddf97d764fb.wav`; MIME `audio/wav`; 546124 bytes.

SHA256: `01496d3e0b214be5cb8f74954f0da1c7a30b2cb1574fb66d542d2efdfd22c386`.

**Transcript lưu trên link đúng version:** This is original demonstration material. What time does the meeting begin? A. At nine o clock. B. In the office. C. By email.

**ASR tự do từ WAV thực tế:**

1. This is original demonstration material — confidence 0.707.
2. what time does the meeting began — confidence 0.628.
3. a at 9:00 — confidence 0.440.
4. be in the office — confidence 0.805.
5. sea by e-mail — confidence 0.854.

### Media 52d9855f-65c5-416c-866b-dce0c9b484e5

Loại AUDIO; object key `audio/52d9855f-65c5-416c-866b-dce0c9b484e5.wav`; MIME `audio/wav`; 910120 bytes.

SHA256: `60e7d0cf05d672678f34288cc052f1d2357e3d0f1c50dc8278f6d6d11b0c896d`.

**Transcript lưu trên link đúng version:** This is an original demonstration conversation. Man: Our team meeting starts at nine on Monday. Woman: Shall I bring the monthly report? Man: Yes, please bring the report to room two hundred. Woman: I will arrive ten minutes early.

**ASR tự do từ WAV thực tế:**

1. This is an original demonstration conversation — confidence 0.655.
2. and then our team meeting starts at nine on Monday — confidence 0.706.
3. woman shall I bring the monthly report — confidence 0.775.
4. men yes please bring the report to room 200 — confidence 0.752.
5. willow and I will arrive 10 minutes early — confidence 0.750.

### Media 66659ff3-f30a-454b-a413-e90eb8d2b9f0

Loại AUDIO; object key `audio/66659ff3-f30a-454b-a413-e90eb8d2b9f0.wav`; MIME `audio/wav`; 605642 bytes.

SHA256: `bb20daf04555cf141230deb4c4251860de541c3e52da6f9c8a80ba0fe3a68b22`.

**Transcript lưu trên link đúng version:** This is original demonstration material. A. The laptop is closed. B. The plant has no leaves. C. The clock is on the floor. D. The clock shows nine.

**ASR tự do từ WAV thực tế:**

1. This is original demonstration material — confidence 0.707.
2. aid the laptop is closed — confidence 0.709.
3. be the plant has no leaves — confidence 0.863.
4. see the clock is on the floor — confidence 0.785.
5. deed o'clock shows nine — confidence 0.825.

### Media 7a9cee8a-8ea7-4443-bd89-a3db6bf4d5ac

Loại AUDIO; object key `audio/7a9cee8a-8ea7-4443-bd89-a3db6bf4d5ac.wav`; MIME `audio/wav`; 522314 bytes.

SHA256: `d630692e07417a0ce3f67b552a45cf5fffb2d82a530dc64208845f4b525b9e86`.

**Transcript lưu trên link đúng version:** This is original demonstration material. Where is the report? A. On Tuesday. B. On your desk. C. About ten people.

**ASR tự do từ WAV thực tế:**

1. This is original demonstration material — confidence 0.707.
2. where is the report — confidence 0.768.
3. a on Tuesday — confidence 0.730.
4. be on your desk — confidence 0.854.
5. see about 10 people — confidence 0.856.

### Media a7f99328-97c1-44fa-8a96-996ebc3cf6dc

Loại AUDIO; object key `audio/a7f99328-97c1-44fa-8a96-996ebc3cf6dc.wav`; MIME `audio/wav`; 805376 bytes.

SHA256: `2e1419e7dd530307d9fe9686ebd5bb5232ebdaf8bf05df8bd9cea3f41d262125`.

**Transcript lưu trên link đúng version:** This is an original demonstration announcement. Attention colleagues. The office library will close at six this evening. Please return borrowed books to the front desk before five thirty. The library will open again at eight tomorrow morning.

**ASR tự do từ WAV thực tế:**

1. This is an original demonstration announcement — confidence 0.568.
2. attention colleagues — confidence 0.844.
3. the office library will close at six this evening — confidence 0.682.
4. please return borrowed books to the front desk before 530 — confidence 0.814.
5. the library will open again at eight tomorrow morning — confidence 0.605.

### Media b1b28789-81a8-430f-ba16-d680a30336eb

Loại AUDIO; object key `audio/b1b28789-81a8-430f-ba16-d680a30336eb.wav`; MIME `audio/wav`; 616224 bytes.

SHA256: `4c331fc73e05439de850b9cff843531c3190e496af92d021ef9196c43175880b`.

**Transcript lưu trên link đúng version:** This is original demonstration material. A. The plant is under the chair. B. A plant is beside the desk. C. A woman is swimming. D. The books are in a bag.

**ASR tự do từ WAV thực tế:**

1. This is original demonstration material — confidence 0.707.
2. aid the plant is under the cheer — confidence 0.708.
3. be a plant is beside the desk — confidence 0.776.
4. see a woman is swimming — confidence 0.766.
5. the the books are in a bag — confidence 0.811.

### Media c8cd44fd-f99d-4119-9455-0a635656680f

Loại AUDIO; object key `audio/c8cd44fd-f99d-4119-9455-0a635656680f.wav`; MIME `audio/wav`; 587568 bytes.

SHA256: `71d45b0a5dc0cd1d2e8c4b5dbe1838020aed109dc66d43ba40a4d1f1f8f0f514`.

**Transcript lưu trên link đúng version:** This is original demonstration material. A. A laptop is on the desk. B. A man is driving a car. C. The room is empty. D. A train is leaving.

**ASR tự do từ WAV thực tế:**

1. This is original demonstration material — confidence 0.707.
2. a laptop is on the desk — confidence 0.663.
3. be a man is driving a car — confidence 0.625.
4. see the room is empty — confidence 0.662.
5. the a train is leaving — confidence 0.392.

### Media d862b0f5-27c8-4f85-b5cc-fb1691de0c3e

Loại AUDIO; object key `audio/d862b0f5-27c8-4f85-b5cc-fb1691de0c3e.wav`; MIME `audio/wav`; 544146 bytes.

SHA256: `404c190537123bbfd8b2254a4217c738604b5345527dfa1efaf9b4645e4e53f8`.

**Transcript lưu trên link đúng version:** This is original demonstration material. Would you like some tea? A. Yes, please. B. At three thirty. C. The blue folder.

**ASR tự do từ WAV thực tế:**

1. This is original demonstration material — confidence 0.707.
2. would you like some tea — confidence 0.859.
3. a yes please — confidence 0.638.
4. be at 330 — confidence 0.446.
5. see the blue folder — confidence 0.831.

### Media ebd80a1b-5318-49bb-8647-9588b6120739

Loại IMAGE; object key `image/ebd80a1b-5318-49bb-8647-9588b6120739.png`; MIME `image/png`; 7871 bytes.

SHA256: `a2d87687777fc6f33507bed930050029281253db878dbf94f02c7b24e14fdaa8`.

Đã xem PNG trực tiếp; nhận xét về hình nằm ở mục P1. Không chỉnh sửa ảnh.

### Media fb97f97d-b89c-4d97-bd9a-1f81ee001734

Loại AUDIO; object key `audio/fb97f97d-b89c-4d97-bd9a-1f81ee001734.wav`; MIME `audio/wav`; 515038 bytes.

SHA256: `c05e947f3013463f59eb3fe4f51450ad9253d6747dbfada5cd08369a769b70d1`.

**Transcript lưu trên link đúng version:** This is original demonstration material. Who will call the customer? A. Next week. B. At the station. C. Anna will.

**ASR tự do từ WAV thực tế:**

1. This is original demonstration material — confidence 0.707.
2. who will call the customer — confidence 0.869.
3. the next week — confidence 0.790.
4. be at the station — confidence 0.695.
5. Seattle will — confidence 0.576.

## Checklist nghe xác nhận và xử lý tiếp

Người nghe dùng owner QA demo tại Web, mở các attempt GRADED đã liệt kê. Mỗi dấu dưới đây đang **chưa được người nghe xác nhận**; ASR không thay cho phần duyệt này. Ghi ngày/người kiểm, media ID/hash và câu nghe khác transcript nếu có.

| Câu/clip | Checklist nghe/nhìn cần xác nhận | Trạng thái người nghe |
|---|---|---|
| P1-1 | Nghe đủ A laptop on desk / B man driving / C room empty / D train leaving. Xem ảnh có nhận ra laptop rõ ràng không; nếu chỉ giống monitor, chưa duyệt ảnh. | Chưa xác nhận |
| P1-2 | Nghe đủ A plant under chair / B plant beside desk / C woman swimming / D books in bag; ảnh chỉ hỗ trợ B. | Chưa xác nhận |
| P1-3 | Nghe A clock twelve / B desk outside / C red book on desk / D someone opening window. Xem vật đỏ có thật sự nhận ra sách không; chưa duyệt nếu chỉ là hình chữ nhật. | Chưa xác nhận |
| P1-4 | Nghe đủ 4 mô tả; D clock shows nine. Đối chiếu kim đồng hồ 9:00; các phương án A/B/C sai rõ. | Chưa xác nhận |
| P2-1 | Nghe câu hỏi giờ bắt đầu và đủ A at nine o clock / B in office / C by email; nhãn không bị nuốt. | Chưa xác nhận |
| P2-2 | Nghe Where is the report và đủ ba phản hồi; B on your desk rõ ràng. | Chưa xác nhận |
| P2-3 | Ưu tiên: phát clip fb97f97d… thực tế, chép độc lập phản hồi thứ ba. Có nghe được C. Anna will không, hay Seattle/khác? Không nhìn transcript trước khi chép. | Chưa xác nhận |
| P2-4 | Nghe lời mời trà và đủ Yes please / at three thirty / blue folder, đúng nhãn A/B/C. | Chưa xác nhận |
| P3-1 | Nghe cùng group audio: team meeting Monday at nine; không nhầm thời gian arrive ten minutes early. | Chưa xác nhận |
| P3-2 | Cùng audio: monthly report được hỏi mang theo và xác nhận Yes; xác định lời Woman/Man dễ hiểu. | Chưa xác nhận |
| P3-3 | Cùng audio: room two hundred là nơi đem report tới cuộc họp; các lựa chọn khác không có căn cứ. | Chưa xác nhận |
| P4-1 | Cùng group audio: library CLOSE at six this evening. | Chưa xác nhận |
| P4-2 | Cùng audio: return borrowed books to FRONT DESK before five thirty. | Chưa xác nhận |
| P4-3 | Cùng audio: OPEN at eight tomorrow morning, phân biệt giờ đóng và hạn trả sách. | Chưa xác nhận |

Nếu người nghe xác nhận nội dung sai hoặc ảnh không đủ rõ: giữ nguyên object/version đã publish và toàn bộ pin/result cũ; tạo media mới nếu cần, tạo working DRAFT/version mới qua Teacher workflow, dùng owner grant hợp lệ cho publisher, submit → reviewer khác approve → publish (P3/P4 dùng G1 nguyên nhóm). Không sửa SQL/history/pointer/object cũ. Chỉ sau publish hợp lệ mới tạo attempt mới để đối chiếu lại, ghi riêng UUID cũ/mới và evidence. Chưa thực hiện các bước sửa này trong lượt audit vì chưa có kết luận nội dung sai chắc chắn.

## Lệnh kiểm và phạm vi thay đổi

Đã chạy:

```powershell
python backend/target/listening-content-verification-20261125/collect_pinned.py
powershell -NoProfile -STA -ExecutionPolicy Bypass -File backend/target/listening-content-verification-20261125/recognize.ps1
git diff --check
```

ASR đã nhận đủ 10/10 WAV, mỗi WAV 5 segment; file checkpoint ngăn chạy trùng. Lần đầu engine báo hết input ở cuối file, script bổ sung ghi rõ EOF và giữ segment đã nhận; không đổi WAV hoặc nội dung kỳ vọng để vượt lỗi. `Limit 1` thử trước được giữ checkpoint và không chạy lại clip đó. Không có test Maven/FE mới vì lượt này chỉ đọc nội dung và thêm báo cáo.

`git diff --check` toàn working tree báo trailing whitespace có sẵn tại `AGENTS.md:1`; giữ nguyên file nền này. Kiểm whitespace riêng báo cáo mới bằng `git diff --no-index --check -- /dev/null docs/Listening_Content_Verification.md`; kết quả ghi ở checkpoint cuối. Không nhận toàn working tree sạch whitespace. Index rỗng, không có unmerged entry; 71 file có sẵn trong snapshot bảo toàn vẫn nguyên SHA256. Chỉ thêm báo cáo này: status cuối 17 tracked modified và 51 untracked file (đếm `-uall`, có cả các thay đổi nền đã tồn tại).

Manifest riêng lượt này: **chỉ thêm `docs/Listening_Content_Verification.md`** trong cây source. Script/bytes/ASR/proof local nằm dưới `backend/target/listening-content-verification-20261125/` ignored. Không thay đổi runbook/manifest và code của lượt media-grant đang có; không đưa nền/credentials vào index. HEAD và index giữ nguyên; kiểm hash bảo toàn và health ở `final-checkpoint.json` local. Dịch vụ demo giữ chạy.
