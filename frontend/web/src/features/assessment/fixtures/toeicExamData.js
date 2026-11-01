export const MOCK_TEST_CATALOG = [
  {
    id: 'test_ets_2024_01',
    title: 'ETS TOEIC 2024 — Test 01 (Full 200 câu)',
    difficulty: 'STANDARD',
    totalQuestions: 200,
    durationMinutes: 120,
    parts: [1, 2, 3, 4, 5, 6, 7],
    participantsCount: 3420,
    averageScore: 685,
    tag: 'Đề mới nhất',
  },
  {
    id: 'test_ets_2024_part5_mini',
    title: 'Luyện chuyên sâu Part 5 — Ngữ pháp & Từ vựng (Test 01)',
    difficulty: 'INTERMEDIATE',
    totalQuestions: 30,
    durationMinutes: 20,
    parts: [5],
    participantsCount: 8940,
    averageScore: 23,
    tag: 'Phổ biến',
  },
  {
    id: 'test_hacker_2024_02',
    title: 'Hacker TOEIC Practice — Full Simulation Test 02',
    difficulty: 'HARD',
    totalQuestions: 200,
    durationMinutes: 120,
    parts: [1, 2, 3, 4, 5, 6, 7],
    participantsCount: 1530,
    averageScore: 640,
    tag: 'Độ khó 850+',
  },
  {
    id: 'test_listening_part1_2',
    title: 'Luyện nghe phản xạ nhanh Part 1 & Part 2',
    difficulty: 'EASY',
    totalQuestions: 31,
    durationMinutes: 25,
    parts: [1, 2],
    participantsCount: 5210,
    averageScore: 26,
    tag: 'Dễ ăn điểm',
  },
];

export const MOCK_PART5_QUESTIONS = [
  {
    id: 'q_101',
    order_index: 1,
    part_number: 5,
    content: 'Ms. Henderson requested that all expense reports be submitted _______ Friday at 5:00 P.M.',
    options: [
      { key: 'A', text: 'by' },
      { key: 'B', text: 'on' },
      { key: 'C', text: 'in' },
      { key: 'D', text: 'at' }
    ],
    correct_option: 'A',
    explanation: 'Giới từ "by" dùng để chỉ thời hạn muộn nhất mà một hành động phải hoàn thành (trước hoặc đúng lúc đó). "by Friday at 5:00 P.M." = trước hoặc vào lúc 5 giờ chiều thứ Sáu.'
  },
  {
    id: 'q_102',
    order_index: 2,
    part_number: 5,
    content: 'The marketing team developed an _______ campaign that increased website traffic by 40 percent.',
    options: [
      { key: 'A', text: 'innovate' },
      { key: 'B', text: 'innovator' },
      { key: 'C', text: 'innovative' },
      { key: 'D', text: 'innovatively' }
    ],
    correct_option: 'C',
    explanation: 'Trước danh từ "campaign" và sau mạo từ "an" cần một tính từ bổ nghĩa. "innovative" (tính từ) nghĩa là có tính đổi mới, sáng tạo.'
  },
  {
    id: 'q_103',
    order_index: 3,
    part_number: 5,
    content: 'Neither the department manager nor the team members _______ available for the conference call.',
    options: [
      { key: 'A', text: 'is' },
      { key: 'B', text: 'are' },
      { key: 'C', text: 'was' },
      { key: 'D', text: 'has been' }
    ],
    correct_option: 'B',
    explanation: 'Cấu trúc "Neither A nor B": động từ chia theo chủ ngữ gần nhất (B). Ở đây "team members" là số nhiều hiện tại nên chọn "are".'
  },
  {
    id: 'q_104',
    order_index: 4,
    part_number: 5,
    content: 'Mr. Tanaka will oversee the facility upgrade _______ Ms. Gomez is on annual leave.',
    options: [
      { key: 'A', text: 'while' },
      { key: 'B', text: 'during' },
      { key: 'C', text: 'despite' },
      { key: 'D', text: 'because of' }
    ],
    correct_option: 'A',
    explanation: 'Phía sau là một mệnh đề hoàn chỉnh (S + V) nên cần liên từ phụ thuộc. "while" (+ S + V) nghĩa là "trong khi". "during" và "despite" chỉ đi với cụm danh từ.'
  },
  {
    id: 'q_105',
    order_index: 5,
    part_number: 5,
    content: 'Participants in the seminar praised Dr. Vance for his _______ explanation of corporate finance.',
    options: [
      { key: 'A', text: 'clear' },
      { key: 'B', text: 'cleared' },
      { key: 'C', text: 'clearing' },
      { key: 'D', text: 'clearly' }
    ],
    correct_option: 'A',
    explanation: 'Sau tính từ sở hữu "his" và trước danh từ "explanation" cần một tính từ miêu tả: "clear" (rõ ràng, mạch lạc).'
  },
  {
    id: 'q_106',
    order_index: 6,
    part_number: 5,
    content: 'The new aerodynamic design makes the vehicle significantly more _______ than earlier models.',
    options: [
      { key: 'A', text: 'economical' },
      { key: 'B', text: 'economy' },
      { key: 'C', text: 'economic' },
      { key: 'D', text: 'economize' }
    ],
    correct_option: 'A',
    explanation: 'Cấu trúc so sánh hơn "more + adj". "economical" nghĩa là tiết kiệm nhiên liệu/chi phí; còn "economic" thuộc về nền kinh tế.'
  },
  {
    id: 'q_107',
    order_index: 7,
    part_number: 5,
    content: 'Please make sure that all confidential documents are _______ stored before leaving the office.',
    options: [
      { key: 'A', text: 'secure' },
      { key: 'B', text: 'security' },
      { key: 'C', text: 'securely' },
      { key: 'D', text: 'securing' }
    ],
    correct_option: 'C',
    explanation: 'Trạng từ "securely" bổ nghĩa cho động từ phân từ hai "stored" (được cất giữ một cách an toàn).'
  },
  {
    id: 'q_108',
    order_index: 8,
    part_number: 5,
    content: 'Customer feedback indicates that our mobile application is both intuitive and _______ reliable.',
    options: [
      { key: 'A', text: 'highly' },
      { key: 'B', text: 'high' },
      { key: 'C', text: 'higher' },
      { key: 'D', text: 'highest' }
    ],
    correct_option: 'A',
    explanation: 'Cần một phó từ chỉ mức độ bổ nghĩa cho tính từ "reliable". "highly reliable" = cực kỳ đáng tin cậy.'
  }
];

export const MOCK_PART7_PASSAGE = {
  id: 'passage_01',
  title: 'Questions 147-148 refer to the following internal memorandum.',
  content: `MEMORANDUM
To: All Sales Staff
From: Karen Patel, Regional Director
Date: October 14
Subject: Software Upgrade Schedule

Please be advised that our customer relationship management (CRM) software will undergo a major upgrade this coming weekend, starting Saturday at 10:00 P.M. and concluding by Sunday at 6:00 A.M. During this maintenance window, access to client records will be completely unavailable.

All staff members are strongly urged to finalize and save any pending client interactions before Friday evening. If you anticipate urgent client inquiries over the weekend, please export the relevant offline contact sheets by 5:00 P.M. on Friday. Thank you for your cooperation as we improve our operational efficiency.`,
  questions: [
    {
      id: 'q_147',
      order_index: 9,
      part_number: 7,
      content: 'What is the main purpose of the memorandum?',
      options: [
        { key: 'A', text: 'To announce the hiring of a new director' },
        { key: 'B', text: 'To notify employees of planned system maintenance' },
        { key: 'C', text: 'To request volunteers for weekend overtime' },
        { key: 'D', text: 'To introduce a new client interaction policy' }
      ],
      correct_option: 'B',
      explanation: 'Bản ghi nhớ thông báo về việc bảo trì và nâng cấp phần mềm CRM vào cuối tuần ("Software Upgrade Schedule").'
    },
    {
      id: 'q_148',
      order_index: 10,
      part_number: 7,
      content: 'What are sales employees advised to do by Friday evening?',
      options: [
        { key: 'A', text: 'Complete and save pending client interactions' },
        { key: 'B', text: 'Attend a software training seminar' },
        { key: 'C', text: 'Submit their weekly timesheets' },
        { key: 'D', text: 'Contact regional directors directly' }
      ],
      correct_option: 'A',
      explanation: 'Trong thông báo: "All staff members are strongly urged to finalize and save any pending client interactions before Friday evening."'
    }
  ]
};
