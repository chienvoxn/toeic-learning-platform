"""Build an opt-in, idempotent pgAdmin SQL dataset; never access credentials.

Run from repository root: python backend/scripts/sample-data/generate.py
Audio is generated offline through Windows System.Speech and embedded in SQL.
"""
from __future__ import annotations

import base64
import hashlib
import json
import subprocess
import uuid
import wave
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
BACKEND = HERE.parents[1]
ROOT = BACKEND.parent
CACHE = BACKEND / 'target' / 'sample-data-media'
OUTPUT = BACKEND / 'sample-data'


def sid(key: str) -> str:
    return str(uuid.UUID(hashlib.md5(('toeic-sample-v1:' + key).encode()).hexdigest()))


audio: dict[str, str] = {}
questions: list[dict] = []
groups: list[dict] = []


def speech(text: str) -> str:
    key = hashlib.sha256(text.encode()).hexdigest()[:24]
    audio[key] = text
    return sid('media:' + key)


def question(part, text, choices, answer, explanation, group=None, audio_id=None, image_id=None, transcript=None):
    choices = list(choices)
    # Rotate correct answers; the original input always identifies its own correct text.
    shift = len(questions) % len(choices)
    correct = choices[answer]
    choices = choices[shift:] + choices[:shift]
    q = dict(partNumber=part, groupKey=group, questionText=text, choices=choices,
             answer=chr(65 + choices.index(correct)), explanation=explanation,
             audioId=audio_id, imageId=image_id, transcript=transcript)
    questions.append(q)


def group(key, part, passage=None, transcript=None):
    groups.append(dict(key=key, partNumber=part, instruction='Read the passage and choose the best answer.' if part >= 6 else 'Listen and choose the best answer.',
                       passageText=passage, audioId=speech(transcript) if transcript else None,
                       imageId=None, transcript=transcript))


def exams():
    descriptions = [
        ('A woman is working at a desk.', 'She is swimming in a pool.', 'The room is outdoors.', 'Several buses are parked outside.'),
        ('A computer is on the desk.', 'A suitcase is on the bed.', 'A car is being washed.', 'A train is leaving the station.'),
        ('A window is behind the desk.', 'There is a bicycle on the table.', 'A man is cooking dinner.', 'The shelves are empty.'),
        ('Documents are lying on the desk.', 'Boxes are falling from a truck.', 'People are eating in a restaurant.', 'A boat is crossing the river.'),
        ('A filing cabinet is beside the work area.', 'A ladder is leaning against a tree.', 'A customer is buying fruit.', 'Workers are painting a bridge.'),
        ('The person is facing a computer screen.', 'The woman is carrying an umbrella.', 'A dog is sleeping under a bench.', 'The person is standing in a queue.'),
    ]
    for i, options in enumerate(descriptions):
        # Part 1 audio enumerates the displayed order, so rotation must be reflected in audio.
        shift = len(questions) % 4
        rotated = list(options[shift:] + options[:shift])
        transcript = 'Look at the office illustration. ' + ' '.join(f'{chr(65+n)}. {t}' for n, t in enumerate(rotated))
        question(1, 'Choose the statement that describes the illustration.', options, 0,
                 'Đối chiếu chủ thể và vị trí đồ vật trong hình văn phòng; đây là hình minh họa tự tạo.',
                 audio_id=speech(transcript), image_id=sid('media:office'), transcript=transcript)

    responses = [
        ('When does the workshop begin?', 'At nine thirty tomorrow morning.', 'At the main office.', 'Yes, I bought one.'),
        ('Where can I collect my visitor badge?', 'At the reception desk.', 'For two hours.', 'I sent it yesterday.'),
        ('Who approved the travel request?', 'The department manager did.', 'On Friday afternoon.', 'It costs fifty dollars.'),
        ('How long will the repairs take?', 'About three business days.', 'In the parking lot.', 'No, that is my colleague.'),
        ('Would you like me to reserve a meeting room?', 'Yes, a room for six would be helpful.', 'It is made of wood.', 'The train was late.'),
        ('Why was the shipment delayed?', 'The delivery truck broke down.', 'On the second shelf.', 'I usually walk to work.'),
        ('Which printer should I use?', 'The one beside the window.', 'At half past eight.', 'Because it was raining.'),
        ('Could you forward the agenda to everyone?', 'Certainly, I will send it now.', 'That is a comfortable chair.', 'We stayed for a week.'),
        ('Have the new uniforms arrived yet?', 'They were delivered this morning.', 'At the end of the corridor.', 'It is quite an interesting book.'),
        ('How often is the equipment inspected?', 'Every three months.', 'By the front entrance.', 'The tickets sold out.'),
        ('Is there a vegetarian option on the menu?', 'Yes, the vegetable pasta is available.', 'I work on the fifth floor.', 'The next train leaves at six.'),
        ('Where should I submit my expense report?', 'Upload it through the staff portal.', 'About ten kilometers.', 'She speaks two languages.'),
        ('Who is giving the presentation this afternoon?', 'Our marketing director.', 'The blue folder.', 'A little after lunch.'),
        ('Can we move our appointment to Thursday?', 'Thursday afternoon works for me.', 'The package is very heavy.', 'She has already read it.'),
        ('What time does the store close on Saturdays?', 'At seven in the evening.', 'Across from the library.', 'It belongs to my team.'),
        ('Do you need help carrying those boxes?', 'Thanks, please take the smaller one.', 'The recipe is on the table.', 'Every other Wednesday.'),
        ('Why did you choose the express delivery service?', 'We need the documents by tomorrow.', 'At the ticket counter.', 'It is the green button.'),
        ('How much is the monthly membership?', 'Thirty dollars per person.', 'Near the city center.', 'The meeting was productive.'),
        ('Will the conference be held online?', 'Yes, a link will be sent to participants.', 'Two more chairs, please.', 'I paid with a credit card.'),
        ('Whose laptop is on the reception desk?', 'It belongs to the new intern.', 'I am leaving in ten minutes.', 'The soup tastes delicious.'),
        ('Should we order more stationery?', 'We still have plenty in the cupboard.', 'At the airport terminal.', 'I studied there last year.'),
        ('Has the manager signed the contract?', 'Not yet; she is reviewing the final page.', 'There are six platforms.', 'The window is open.'),
        ('What is the fastest way to contact support?', 'Use the live chat on our website.', 'The office is very bright.', 'It was an excellent concert.'),
        ('May I borrow your calculator for a moment?', 'Of course, it is in the top drawer.', 'The hotel serves breakfast.', 'Only three people attended.'),
        ('Are we supposed to bring a printed copy?', 'A digital copy will be sufficient.', 'They painted it last summer.', 'I prefer the aisle seat.'),
    ]
    for prompt, *choices in responses:
        transcript = prompt
        question(2, 'Listen to the question and choose the best response.', choices, 0,
                 'Chọn câu trả lời phù hợp với từ hỏi hoặc mục đích giao tiếp: ' + prompt,
                 audio_id=speech(transcript), transcript=transcript)

    names = ['Oak', 'Maple', 'Cedar', 'Pine', 'Birch', 'Elm', 'Willow', 'Ash', 'Cherry', 'Spruce', 'Walnut', 'Beech', 'Palm']
    for i, name in enumerate(names):
        day = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday'][i % 5]
        time = f'{9 + i % 3}:30'
        transcript = (f'Woman: We need to prepare the {name} meeting room for the customer workshop on {day}. '
                      f'Man: I have reserved the room for {time} in the morning. Shall I print the handouts? '
                      'Woman: Yes, please print twelve copies. I will check the projector before the visitors arrive. '
                      'Man: All right. I will bring the handouts to reception when they are ready.')
        key = f'conversation-{i+1}'; group(key, 3, transcript=transcript)
        question(3, 'What are the speakers preparing for?', ['A customer workshop', 'A job interview', 'A music concert', 'A building inspection'], 0, 'Đầu hội thoại nhắc rõ customer workshop.', key)
        question(3, 'When will the workshop take place?', [f'{day} at {time} a.m.', 'Saturday at 6 p.m.', 'Sunday at noon', 'Next month at 8 p.m.'], 0, 'Kết hợp ngày trong lời người nữ và giờ trong lời người nam.', key)
        question(3, 'What will the man do next?', ['Print twelve handouts', 'Repair the projector', 'Cancel the room', 'Call a taxi'], 0, 'Người nam nhận nhiệm vụ in mười hai bản tài liệu.', key)

    destinations = ['Riverton', 'Lakeside', 'Hillcrest', 'Brookfield', 'Greenwood', 'Fairview', 'Woodland', 'Westport', 'Eastvale', 'Meadowpark']
    for i, dest in enumerate(destinations):
        platform = str(i + 2); departure = f'{10+i%7}:45'
        transcript = (f'Attention passengers traveling to {dest}. The train scheduled to depart at {departure} '
                      f'will now leave from platform {platform}. Please follow the signs beside the information desk. '
                      'Passengers requiring assistance should speak to a member of staff in a blue uniform. '
                      'Keep your ticket ready for inspection before boarding. Thank you for your cooperation.')
        key = f'announcement-{i+1}'; group(key, 4, transcript=transcript)
        question(4, 'Where is this announcement most likely being made?', ['At a railway station', 'At a hospital', 'At a restaurant', 'At a sports center'], 0, 'Các từ train, platform và boarding xác định bối cảnh nhà ga.', key)
        question(4, f'Which platform should passengers for {dest} use?', [f'Platform {platform}', 'Platform 21', 'Platform 24', 'Platform 30'], 0, 'Nghe con số đi sau platform.', key)
        question(4, 'What should passengers needing help do?', ['Speak to a staff member in blue', 'Buy another ticket online', 'Wait outside the station', 'Telephone the restaurant'], 0, 'Thông báo yêu cầu tìm nhân viên mặc đồng phục xanh.', key)

    grammar = [
        ('The meeting starts ___ nine o’clock.', ['at', 'on', 'in', 'for'], 'At dùng với giờ cụ thể.'),
        ('Please submit the report ___ Friday.', ['by', 'during', 'between', 'throughout'], 'By chỉ hạn chót.'),
        ('All employees must wear ___ identification badges.', ['their', 'them', 'they', 'themselves'], 'Trước danh từ cần tính từ sở hữu their.'),
        ('The new software is ___ than the previous version.', ['faster', 'fast', 'fastest', 'fastly'], 'Than đi với dạng so sánh hơn.'),
        ('Ms. Lee has worked here ___ 2020.', ['since', 'for', 'during', 'while'], 'Since đi với mốc thời gian.'),
        ('The package will ___ tomorrow morning.', ['arrive', 'arriving', 'arrived', 'arrival'], 'Sau will là động từ nguyên mẫu.'),
        ('We look forward to ___ you at the conference.', ['seeing', 'see', 'seen', 'saw'], 'Look forward to đi với V-ing.'),
        ('The manager spoke ___ about the new policy.', ['clearly', 'clear', 'clarity', 'clearing'], 'Trạng từ bổ nghĩa động từ spoke.'),
        ('___ the heavy rain, the delivery arrived on time.', ['Despite', 'Although', 'Because', 'Unless'], 'Despite đi trước cụm danh từ.'),
        ('A receipt is required ___ you request a refund.', ['when', 'during', 'despite', 'because of'], 'When nối mệnh đề thời gian.'),
        ('The documents ___ by the supervisor yesterday.', ['were approved', 'approve', 'approving', 'are approve'], 'Quá khứ bị động: were + phân từ.'),
        ('Please contact us if you need ___ information.', ['additional', 'addition', 'additionally', 'add'], 'Tính từ additional bổ nghĩa information.'),
        ('This room is large ___ for twenty participants.', ['enough', 'too', 'very', 'many'], 'Cấu trúc adjective + enough.'),
        ('The office is closed ___ public holidays.', ['on', 'at', 'to', 'from'], 'On dùng với ngày.'),
        ('Neither the printer nor the scanners ___ working.', ['are', 'is', 'be', 'was'], 'Động từ hòa hợp với chủ ngữ gần nhất scanners.'),
        ('We have ___ completed the safety inspection.', ['already', 'yet', 'ever', 'still not'], 'Already dùng trong câu khẳng định hoàn thành.'),
        ('The candidate has extensive ___ in accounting.', ['experience', 'experienced', 'experiences of', 'experiencing'], 'Experience ở đây là danh từ không đếm được.'),
        ('The presentation was postponed ___ the speaker was ill.', ['because', 'because of', 'despite', 'during'], 'Because đứng trước mệnh đề.'),
        ('Please keep the entrance ___ at all times.', ['clear', 'clearly', 'clarity', 'clears'], 'Keep + tân ngữ + tính từ.'),
        ('The company plans ___ a new branch.', ['to open', 'opening', 'opened', 'opens'], 'Plan đi với to-infinitive.'),
        ('Our sales increased ___ fifteen percent.', ['by', 'at', 'from', 'on'], 'By chỉ mức tăng.'),
        ('The assistant prepared the slides ___.', ['carefully', 'careful', 'care', 'caring'], 'Trạng từ bổ nghĩa prepared.'),
        ('The engineer ___ designed the system will lead the training.', ['who', 'which', 'where', 'whose'], 'Who làm chủ ngữ chỉ người.'),
        ('There are only a ___ seats available.', ['few', 'little', 'much', 'less'], 'A few đi với danh từ đếm được số nhiều.'),
        ('The invoice must be paid ___ thirty days.', ['within', 'between', 'among', 'onto'], 'Within chỉ khoảng thời hạn.'),
        ('If the weather improves, we ___ the event outdoors.', ['will hold', 'held', 'would held', 'holding'], 'Điều kiện loại 1: if hiện tại, will + V.'),
        ('The customer asked ___ the warranty covered repairs.', ['whether', 'unless', 'despite', 'during'], 'Whether mở mệnh đề nghi vấn gián tiếp.'),
        ('This is the ___ route to the airport.', ['shortest', 'shorter', 'short', 'shortly'], 'The đi với dạng so sánh nhất.'),
        ('The team is responsible ___ maintaining the equipment.', ['for', 'of', 'to', 'by'], 'Responsible for + V-ing.'),
        ('The instructions should be read before ___ the machine.', ['operating', 'operate', 'operated', 'operation of to'], 'Giới từ before đi với V-ing.'),
    ]
    for text, choices, explanation in grammar: question(5, text, choices, 0, explanation)

    for i, dept in enumerate(['Sales', 'Finance', 'Operations', 'Customer Service']):
        passage = (f'To: {dept} staff\nSubject: Training invitation\n'
                   'Our monthly workshop will be held ___[1]___ Monday, 18 May, in room 204. '
                   'Please ___[2]___ your attendance by Thursday. The workshop includes a short demonstration '
                   'of the new booking system. ___[3]___\n'
                   'Printed materials will be ___[4]___ at the entrance. Thank you for your cooperation.')
        key = f'text-completion-{i+1}'; group(key, 6, passage=passage)
        question(6, 'Choose the word for blank [1].', ['on', 'at', 'from', 'by'], 0, 'On dùng trước ngày/thứ cụ thể.', key)
        question(6, 'Choose the word for blank [2].', ['confirm', 'confirmation', 'confirmed', 'confirming'], 0, 'Sau please cần động từ nguyên mẫu.', key)
        question(6, 'Choose the sentence for blank [3].', ['Participants will have time to practice using it.', 'The restaurant closed ten years ago.', 'Your flight has already departed.', 'The building is for sale.'], 0, 'Câu bổ sung nói về việc thực hành hệ thống đang được giới thiệu.', key)
        question(6, 'Choose the word for blank [4].', ['available', 'availability', 'availably', 'avail'], 0, 'Be available: có sẵn.', key)

    for i in range(10):
        name = names[i]; deadline = 10 + i
        passage = (f'{name} Community Center\nWeekend photography workshop\n'
                   f'Registration closes on June {deadline}. The class meets on Saturday from 10 a.m. to noon. '
                   'The fee is $35 and includes a printed workbook. Participants should bring their own camera. '
                   f'To register, email classes@{name.lower()}.example. Beginners are welcome.')
        key = f'single-passage-{i+1}'; group(key, 7, passage=passage)
        question(7, 'What is the purpose of this notice?', ['To advertise a photography class', 'To announce a camera recall', 'To request job applications', 'To describe a bus route'], 0, 'Thông báo giới thiệu lớp nhiếp ảnh cuối tuần.', key)
        question(7, 'What is included in the fee?', ['A printed workbook', 'A new camera', 'A hotel room', 'A monthly travel pass'], 0, 'Chi phí bao gồm printed workbook.', key)
        if i < 9: question(7, 'What should participants bring?', ['Their own camera', 'A company uniform', 'A signed medical form', 'A packed dinner'], 0, 'Thông báo yêu cầu mang máy ảnh của mình.', key)

    for i in range(5):
        name = destinations[i]; date = 12+i
        passage = (f'Document 1 — Order confirmation\n{name} Office Supplies\nOrder date: July {date}\n'
                   'Order: 12 ergonomic chairs. Unit price: $80. Delivery charge: $20. Delivery: July 20, 9–11 a.m.\n\n'
                   'Document 2 — Email from customer\nThank you for the confirmation. Please deliver to the rear entrance '
                   'because the main lobby is being painted. Our receptionist, Ms. Chen, will sign for the order. '
                   'We need two additional chairs. Could you update the order and confirm the new total?')
        key=f'multiple-passage-{i+1}';group(key, 7, passage=passage)
        question(7, 'What did the customer originally order?', ['Twelve ergonomic chairs', 'Twelve desks', 'Two printers', 'Twenty filing cabinets'], 0, 'Order confirmation ghi 12 ergonomic chairs.', key)
        question(7, 'Why should the rear entrance be used?', ['The main lobby is being painted', 'The elevators are broken', 'The receptionist is on leave', 'The front gate opens at noon'], 0, 'Email nêu lý do main lobby is being painted.', key)
        question(7, 'Who will sign for the delivery?', ['Ms. Chen', 'The painter', 'The driver’s assistant', 'The accounting director'], 0, 'Ms. Chen là người ký nhận được chỉ định.', key)
        question(7, 'How many chairs does the customer now want in total?', ['Fourteen', 'Ten', 'Twelve', 'Twenty'], 0, '12 chiếc ban đầu cộng 2 chiếc thêm = 14.', key)
        question(7, 'What is the updated total including the unchanged delivery charge?', ['$1,140', '$960', '$1,120', '$1,200'], 0, '14 × $80 + $20 vận chuyển = $1,140.', key)
    assert len(questions)==200
    assert [sum(q['partNumber']==p for q in questions) for p in range(1,8)] == [6,25,39,30,30,16,54]


LESSONS = [
    ('GRAMMAR','EASY','Giới từ thời gian: at, on, in','Nắm cách dùng giới từ với giờ, ngày và khoảng thời gian.',
     'AT dùng với giờ: at 9 a.m., at noon.\nON dùng với ngày: on Monday, on 12 June.\nIN dùng với tháng, năm: in July, in 2026.\n\nVí dụ: The workshop starts at 9 a.m. on Monday.\nBài tập: The office opens ___ eight o’clock.\nĐáp án: at.\n\nMẹo: xác định loại mốc thời gian trước khi chọn giới từ.'),
    ('GRAMMAR','MEDIUM','Từ loại trong Part 5','Nhận biết danh từ, động từ, tính từ và trạng từ qua vị trí.',
     'Tính từ đứng trước danh từ: an efficient system.\nTrạng từ bổ nghĩa động từ: work efficiently.\nSau mạo từ và tính từ thường là danh từ.\n\nVí dụ: Please review the report carefully. Carefully bổ nghĩa review.\nBài tập: We need additional ___.\nA. inform B. information C. informative D. informatively\nĐáp án B: information là danh từ.'),
    ('VOCAB','EASY','Từ vựng công sở và cuộc họp','Học agenda, deadline, minutes và appointment trong ngữ cảnh.',
     'agenda: chương trình nghị sự — Please read the agenda before the meeting.\ndeadline: hạn chót — The deadline is Friday.\nminutes: biên bản họp — She takes the meeting minutes.\nappointment: cuộc hẹn — I have an appointment at two.\n\nÔn tập: viết một câu cho mỗi từ, sau đó dùng bộ thẻ Công sở trong thư viện từ vựng.'),
    ('VOCAB','MEDIUM','Từ vựng mua hàng và giao nhận','Phân biệt invoice, receipt, shipment và refund.',
     'invoice: hóa đơn yêu cầu thanh toán.\nreceipt: biên lai xác nhận đã trả tiền.\nshipment: lô hàng.\nrefund: khoản hoàn tiền.\n\nThe invoice is due within 30 days.\nKeep your receipt if you need a refund.\n\nCâu hỏi: tài liệu nào xác nhận giao dịch đã thanh toán? Đáp án: receipt.'),
    ('LISTENING','EASY','Part 1: nhận diện hành động và vị trí','Luyện quan sát chủ thể, động từ và giới từ vị trí.',
     'Trước khi nghe, nhận diện người, đồ vật và hành động có thể thấy.\nPhân biệt working at a desk với carrying a box.\nChú ý beside, behind, in front of và on.\n\nKhông chọn câu chỉ vì có một từ xuất hiện trong hình; toàn bộ mô tả phải khớp.\nMở đề Listening mẫu để luyện với audio giọng tổng hợp và hình văn phòng.'),
    ('LISTENING','MEDIUM','Part 2: trả lời câu hỏi gián tiếp','Chọn phản hồi phù hợp thay vì tìm từ lặp lại.',
     'When hỏi thời gian; where hỏi nơi chốn; who hỏi người; why hỏi lý do.\nCould you… thường là lời nhờ, không phải câu hỏi về khả năng.\n\nCould you forward the agenda? — Certainly, I will send it now.\nWhy was the shipment delayed? — The truck broke down.\n\nBẫy: từ giống âm hoặc lặp từ trong câu hỏi không bảo đảm đáp án đúng.'),
    ('READING','EASY','Đọc email công việc theo mục đích','Tìm người gửi, người nhận, mục đích và hành động tiếp theo.',
     'Đọc subject trước để xác định chủ đề.\nTìm các động từ yêu cầu: confirm, submit, contact, attach.\nGạch mốc ngày và hạn chót.\n\nSubject: Training invitation\nPlease confirm your attendance by Thursday.\nCâu hỏi: người nhận cần làm gì? Xác nhận tham dự trước thứ Năm.\n\nTránh suy luận vượt quá thông tin trong email.'),
    ('READING','HARD','Part 7: nối thông tin hai văn bản','Kết hợp đơn hàng và email để tính số lượng, chi phí.',
     'Văn bản 1: đặt 12 ghế, đơn giá $80, vận chuyển $20.\nVăn bản 2: khách yêu cầu thêm 2 ghế.\n\nSố lượng mới: 12 + 2 = 14.\nTổng mới: 14 × 80 + 20 = $1,140.\n\nĐọc câu hỏi để xác định cần đối chiếu dữ liệu nào; chú ý thông tin được sửa ở email mới.'),
    ('STRATEGIES','EASY','Lộ trình luyện thi 4 tuần','Kết hợp học từ, luyện Part và xem lại câu sai.',
     'Tuần 1: ôn từ công sở và ngữ pháp cơ bản, luyện Part 1/2/5.\nTuần 2: luyện nhóm hội thoại và bài nói Part 3/4.\nTuần 3: đọc email, thông báo, nhiều văn bản Part 6/7.\nTuần 4: làm Section Test rồi Full Mock, chữa lại lỗi.\n\nMỗi ngày: 15 phút từ vựng, 20 phút luyện Part, 10 phút chữa bài.\nĐiều chỉnh theo mục tiêu và thời gian thực tế.'),
    ('STRATEGIES','MEDIUM','Chữa bài và ghi sổ lỗi','Biến kết quả thành kế hoạch ôn tập cụ thể.',
     'Sau khi nộp bài, lọc câu sai và câu bỏ trống.\nGhi lại câu gốc, đáp án, lý do và ví dụ mới.\nVới Listening, nghe lại rồi xem transcript để tìm từ chưa nhận ra.\nVới Reading, tìm đúng câu chứa bằng chứng.\n\nKhông chỉ học thuộc chữ A/B/C/D; hiểu ngữ cảnh và cấu trúc.\nLịch sử thi và thống kê theo Part giúp xác định phần cần luyện thêm.'),
    ('DOCS','EASY','Hướng dẫn dùng bộ dữ liệu mẫu','Các màn hình và giới hạn của nội dung luyện tập tự soạn.',
     'Đây là nội dung tự biên soạn để trải nghiệm ứng dụng, không phải đề thi ETS.\nAudio được tổng hợp offline bằng giọng tiếng Anh Windows.\nHình Part 1 là tranh minh họa văn phòng.\n\n/catalog: học liệu và bookmark.\n/vocabulary: bộ thẻ và ôn SRS.\n/tests: đề luyện tập, Listening, Reading và Full Mock.\n/exam-history: lịch sử và chữa bài.\n/teacher/tests: soạn và gửi duyệt.\n/admin/tests: duyệt và xuất bản.\n\nKhông có bảng quy đổi đã thẩm định nên bộ mẫu chỉ hiển thị số câu đúng.'),
    ('DOCS','MEDIUM','Checklist trước khi xuất bản đề','Kiểm tra cấu trúc, nhóm câu hỏi, audio, ảnh và lời giải.',
     '1. Kiểm tra Part và số lượng câu.\n2. Cùng nhóm phải đứng liên tiếp, đúng thứ tự.\n3. Part 2 có 3 lựa chọn, các Part khác có 4.\n4. Mỗi câu đúng một đáp án và có lời giải phù hợp.\n5. Nghe audio, xem hình và đối chiếu transcript.\n6. Teacher gửi duyệt; Admin kiểm tra và xuất bản.\n7. Sửa đề đã xuất bản bằng phiên bản mới để giữ bài làm cũ.'),
]

WORDS = [
    ('Công sở & cuộc họp',[
        ('agenda','chương trình nghị sự','noun','əˈdʒendə','Please review the agenda before the meeting.'),
        ('deadline','hạn chót','noun','ˈdedlaɪn','The deadline for the report is Friday.'),
        ('appointment','cuộc hẹn','noun','əˈpɔɪntmənt','I have an appointment with the manager.'),
        ('colleague','đồng nghiệp','noun','ˈkɒliːɡ','My colleague will lead the workshop.'),
        ('confirm','xác nhận','verb','kənˈfɜːm','Please confirm your attendance by email.'),
        ('schedule','lịch trình; lên lịch','noun / verb','ˈskedʒuːl','We need to schedule a team meeting.'),
        ('attend','tham dự','verb','əˈtend','All staff should attend the training.'),
        ('minutes','biên bản họp','noun','ˈmɪnɪts','She sent the meeting minutes yesterday.'),
        ('department','phòng ban','noun','dɪˈpɑːtmənt','Contact the accounting department.'),
        ('proposal','đề xuất','noun','prəˈpəʊzəl','The director approved our proposal.'),
        ('supervisor','người giám sát','noun','ˈsuːpəvaɪzə','Ask your supervisor for permission.'),
        ('equipment','thiết bị','noun','ɪˈkwɪpmənt','The equipment is inspected every month.'),
    ]),
    ('Mua hàng & giao nhận',[
        ('invoice','hóa đơn yêu cầu thanh toán','noun','ˈɪnvɔɪs','The invoice is due in thirty days.'),
        ('receipt','biên lai','noun','rɪˈsiːt','Keep the receipt for your records.'),
        ('shipment','lô hàng','noun','ˈʃɪpmənt','Your shipment will arrive tomorrow.'),
        ('refund','khoản hoàn tiền','noun','ˈriːfʌnd','You can request a refund within a week.'),
        ('purchase','mua; giao dịch mua','verb / noun','ˈpɜːtʃəs','We purchased twelve office chairs.'),
        ('supplier','nhà cung cấp','noun','səˈplaɪə','Our supplier offers free delivery.'),
        ('warehouse','nhà kho','noun','ˈweəhaʊs','The goods are stored in the warehouse.'),
        ('delivery','việc giao hàng','noun','dɪˈlɪvəri','The delivery charge is twenty dollars.'),
        ('quantity','số lượng','noun','ˈkwɒntəti','Please check the quantity on the order.'),
        ('discount','khoản giảm giá','noun','ˈdɪskaʊnt','Members receive a ten percent discount.'),
        ('warranty','bảo hành','noun','ˈwɒrənti','The printer has a one-year warranty.'),
        ('available','có sẵn','adjective','əˈveɪləbl','The product is available online.'),
    ]),
    ('Du lịch & dịch vụ',[
        ('reservation','đặt chỗ','noun','ˌrezəˈveɪʃn','I would like to confirm my reservation.'),
        ('itinerary','lịch trình chuyến đi','noun','aɪˈtɪnərəri','The itinerary includes two factory visits.'),
        ('departure','sự khởi hành','noun','dɪˈpɑːtʃə','Departure is scheduled for ten forty-five.'),
        ('platform','sân ga','noun','ˈplætfɔːm','The train leaves from platform four.'),
        ('accommodation','chỗ ở','noun','əˌkɒməˈdeɪʃn','The company arranged accommodation for us.'),
        ('reception','quầy lễ tân','noun','rɪˈsepʃn','Collect your visitor badge at reception.'),
        ('complimentary','miễn phí; tặng kèm','adjective','ˌkɒmplɪˈmentəri','The hotel provides complimentary breakfast.'),
        ('assistance','sự hỗ trợ','noun','əˈsɪstəns','Please ask a staff member for assistance.'),
        ('passenger','hành khách','noun','ˈpæsɪndʒə','Passengers should keep their tickets ready.'),
        ('destination','điểm đến','noun','ˌdestɪˈneɪʃn','Our final destination is Lakeside.'),
        ('boarding','việc lên tàu hoặc máy bay','noun','ˈbɔːdɪŋ','Boarding begins twenty minutes before departure.'),
        ('postpone','hoãn lại','verb','pəˈspəʊn','We had to postpone the business trip.'),
    ]),
]


def main():
    exams();CACHE.mkdir(parents=True,exist_ok=True);OUTPUT.mkdir(parents=True,exist_ok=True)
    manifest=CACHE/'speech.json'
    manifest.write_text(json.dumps([dict(key=k,text=t) for k,t in audio.items()],ensure_ascii=False),encoding='utf-8')
    media_ready = (CACHE/'office.png').exists() and all((CACHE/(key+'.wav')).exists() for key in audio)
    if '--reuse-media' not in sys.argv and not media_ready:
        subprocess.run(['powershell','-Sta','-NoProfile','-ExecutionPolicy','Bypass','-File',str(HERE/'synthesize.ps1'),'-Manifest',str(manifest),'-OutputDirectory',str(CACHE)],check=True)
    media=[]
    for key in audio:
        path=CACHE/(key+'.wav')
        with wave.open(str(path),'rb') as stream: duration=round(stream.getnframes()/stream.getframerate())
        media.append(dict(id=sid('media:'+key),type='AUDIO',mime='audio/wav',duration=duration,data=base64.b64encode(path.read_bytes()).decode()))
    media.append(dict(id=sid('media:office'),type='IMAGE',mime='image/png',duration=None,data=base64.b64encode((CACHE/'office.png').read_bytes()).decode()))
    full=dict(title='[Mẫu] Full Mock 01 — Công sở và dịch vụ',description='Đề tự biên soạn trải nghiệm hệ thống; audio giọng tổng hợp, không phải đề ETS. Không quy đổi điểm.',testType='FULL',durationSeconds=7200,maxAttempts=None,conversionId=None,groups=groups,questions=questions)
    documents=[]
    for key,title,kind,seconds,parts in [
        ('full',full['title'],'FULL',7200,list(range(1,8))),
        ('listening','[Mẫu] Listening 01 — Hội thoại và thông báo','LISTENING',2700,[1,2,3,4]),
        ('reading','[Mẫu] Reading 01 — Ngữ pháp, email và đơn hàng','READING',4500,[5,6,7]),
        ('part5','[Mẫu] Part 5 nhanh — 12 câu ngữ pháp','PRACTICE',720,[5]),
    ]:
        qs=[dict(q,_sourceOrder=i+1) for i,q in enumerate(questions) if q['partNumber'] in parts]
        if key=='part5':qs=qs[:12]
        selected={q['groupKey'] for q in qs if q['groupKey']}
        documents.append(dict(key=key,state='PUBLISHED',review='APPROVED',doc=dict(full,title=title,testType=kind,durationSeconds=seconds,groups=[g for g in groups if g['key'] in selected],questions=qs)))
    for key,state,review,title in [
        ('draft','DRAFT','DRAFT','[Mẫu] Bản nháp Part 5 — tiếp tục biên soạn'),
        ('review','DRAFT','IN_REVIEW','[Mẫu] Đề đang chờ Admin duyệt'),
        ('approved','DRAFT','APPROVED','[Mẫu] Đề đã duyệt — sẵn sàng xuất bản'),
        ('retired','RETIRED','APPROVED','[Mẫu] Đề cũ — đã ngừng phát hành'),
    ]:
        d=json.loads(json.dumps(documents[-1]['doc']))
        d['title']=title
        documents.append(dict(key=key,state=state,review=review,doc=d))
    # _sourceOrder belongs only to the seed mapping; never export it into the runtime contract.
    imports=[]
    for exam in documents[:4]:
        clean=json.loads(json.dumps(exam['doc']))
        for q in clean['questions']:q.pop('_sourceOrder',None)
        imports.append(clean)
    for exam,doc in zip(documents[:4],imports):
        (OUTPUT/f"import-{exam['key']}.json").write_text(json.dumps(doc,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    payload=dict(media=media,full=full,exams=documents,lessons=[dict(category=c,difficulty=d,title=t,summary=s,body=b) for c,d,t,s,b in LESSONS],decks=[dict(name=n,words=[dict(word=w,meaning=m,partOfSpeech=p,pronunciation=ph,example=e) for w,m,p,ph,e in entries]) for n,entries in WORDS])
    template=(HERE/'template.sql').read_text(encoding='utf-8')
    sql=template.replace('/* SAMPLE_PAYLOAD */',"$sample_json$"+json.dumps(payload,ensure_ascii=False,separators=(',',':'))+"$sample_json$::jsonb")
    target=OUTPUT/'toeic_sample_data.sql';target.write_text(sql,encoding='utf-8')
    print(json.dumps(dict(sql=str(target.relative_to(ROOT)),sizeBytes=target.stat().st_size,questions=len(questions),groups=len(groups),audio=len(audio),images=1,publishedTests=4,managementStates=4,lessons=len(LESSONS),wordsPerUser=sum(len(e) for _,e in WORDS)),ensure_ascii=False))


if __name__=='__main__':main()
