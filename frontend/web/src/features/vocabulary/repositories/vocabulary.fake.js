const delay = (ms = 300) => new Promise(res => setTimeout(res, ms));

export const MOCK_DECKS = [
  {
    id: 'deck_common_1000',
    title: '1000 từ tiếng Anh thông dụng',
    description: 'Từ vựng giao tiếp thông dụng — dữ liệu mẫu cho màn flashcard Figma.',
    totalCards: 1,
    dueCards: 1,
    masteredCards: 0,
    category: 'Thông dụng',
  },
  {
    id: 'deck_600_essential',
    title: '600 Từ vựng TOEIC căn bản & thiết yếu',
    description: 'Bộ từ vựng cốt lõi thường xuyên xuất hiện nhất trong các đề thi ETS TOEIC từ trước đến nay.',
    totalCards: 60,
    dueCards: 12,
    masteredCards: 38,
    category: 'Căn bản',
  },
  {
    id: 'deck_business_contracts',
    title: 'Từ vựng Hợp đồng & Đàm phán thương mại (Contracts)',
    description: 'Chuyên đề từ vựng Part 4 và Part 7 về hợp đồng, điều khoản bảo hành và ký kết kinh doanh.',
    totalCards: 45,
    dueCards: 8,
    masteredCards: 25,
    category: 'Chuyên đề',
  },
  {
    id: 'deck_office_personnel',
    title: 'Nhân sự & Môi trường công sở (Office & Personnel)',
    description: 'Từ vựng về tuyển dụng, phúc lợi nhân viên, thăng chức và chính sách nội bộ.',
    totalCards: 50,
    dueCards: 15,
    masteredCards: 20,
    category: 'Chuyên đề',
  },
];

export const MOCK_FLASHCARDS = [
  {
    id: 'card_01',
    word: 'implement',
    phonetic: '/ˈɪmplɪment/',
    partOfSpeech: 'verb',
    definitionVi: 'thi hành, thực hiện, áp dụng (kế hoạch, chính sách, hệ thống)',
    exampleEn: 'The company plans to implement the new remote work policy next month.',
    exampleVi: 'Công ty dự kiến sẽ áp dụng chính sách làm việc từ xa mới vào tháng tới.',
    collocations: 'implement a policy / implement changes / implement a system',
  },
  {
    id: 'card_02',
    word: 'accommodate',
    phonetic: '/əˈkɑːmədeɪt/',
    partOfSpeech: 'verb',
    definitionVi: 'đáp ứng (nhu cầu), chứa được (số lượng người), cung cấp chỗ ở',
    exampleEn: 'The conference room can comfortably accommodate up to 100 participants.',
    exampleVi: 'Phòng hội thảo có thể chứa thoải mái lên tới 100 người tham gia.',
    collocations: 'accommodate needs / accommodate requests / accommodate guests',
  },
  {
    id: 'card_03',
    word: 'reimburse',
    phonetic: '/ˌriːɪmˈbɜːrs/',
    partOfSpeech: 'verb',
    definitionVi: 'hoàn trả, bồi hoàn (tiền công tác phí, chi phí)',
    exampleEn: 'Employees will be reimbursed for travel expenses within two business days.',
    exampleVi: 'Nhân viên sẽ được hoàn trả các chi phí đi lại trong vòng hai ngày làm việc.',
    collocations: 'reimburse expenses / reimburse fully',
  },
  {
    id: 'card_04',
    word: 'mandatory',
    phonetic: '/ˈmændətɔːri/',
    partOfSpeech: 'adjective',
    definitionVi: 'mang tính bắt buộc theo quy định hoặc luật lệ',
    exampleEn: 'Attendance at the annual safety orientation is mandatory for all personnel.',
    exampleVi: 'Việc tham gia buổi hướng dẫn an toàn hàng năm là bắt buộc đối với tất cả nhân viên.',
    collocations: 'mandatory training / mandatory meeting / legally mandatory',
  },
  {
    id: 'card_05',
    word: 'eligible',
    phonetic: '/ˈelɪdʒəbl/',
    partOfSpeech: 'adjective',
    definitionVi: 'đủ tư cách, đủ điều kiện được hưởng quyền lợi',
    exampleEn: 'Only full-time employees are eligible for the corporate health insurance plan.',
    exampleVi: 'Chỉ nhân viên toàn thời gian mới đủ điều kiện tham gia chương trình bảo hiểm y tế doanh nghiệp.',
    collocations: 'eligible for a promotion / eligible to receive benefits',
  },
];

export class FakeVocabularyRepository {
  async getDecks() {
    await delay(300);
    return MOCK_DECKS;
  }

  async getDeckCards(deckId) {
    await delay(350);
    if (deckId === 'deck_common_1000') return [{
      id: 'card_parents',
      word: 'parents',
      phonetic: '/ˈpeərənts/',
      partOfSpeech: 'Danh từ',
      definitionVi: 'cha mẹ',
      exampleEn: 'My parents taught me to be kind.',
      exampleVi: 'Cha mẹ tôi đã dạy tôi phải tử tế.',
      image: 'assets/figma/flashcard-front/people.png',
    }];
    return MOCK_FLASHCARDS;
  }

  async getDeckDetail(deckId) {
    await delay(300);
    const deck = MOCK_DECKS.find(d => d.id === deckId) || MOCK_DECKS[0];
    const cards = await this.getDeckCards(deckId);

    // Chia thành các nhóm từ vựng như Frame 6 trong Figma
    const groups = [
      {
        id: 'group_1',
        title: 'Nhóm 1: Từ vựng nền tảng (Cốt lõi)',
        description: 'Các từ vựng bắt buộc phải nắm vững với tần suất xuất hiện cao',
        words: cards.slice(0, 3).map((c, i) => ({
          ...c,
          status: i === 0 ? 'mastered' : 'learning',
          progress: i === 0 ? 100 : 60,
        })),
      },
      {
        id: 'group_2',
        title: 'Nhóm 2: Từ vựng nâng cao & Collocations',
        description: 'Các cụm từ cố định giúp tăng tốc độ đọc hiểu',
        words: cards.slice(3).map((c) => ({
          ...c,
          status: 'new',
          progress: 0,
        })),
      },
    ];

    return {
      deck,
      groups,
      totalWords: cards.length,
      masteredCount: 1,
      learningCount: 2,
    };
  }

  async rateCard(cardId, rating) {
    await delay(200);
    // rating: 'AGAIN' | 'HARD' | 'GOOD' | 'EASY'
    return {
      success: true,
      cardId,
      rating,
      nextReviewAt: new Date(Date.now() + 86400 * 1000 * 3).toISOString(),
    };
  }
}
