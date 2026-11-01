const delay = (ms = 250) => new Promise((resolve) => setTimeout(resolve, ms));

const WORD_PAIRS_BANK = [
  { id: 'p1', wordEn: 'Accommodate', meaningVi: 'Cung cấp chỗ ở, đáp ứng' },
  { id: 'p2', wordEn: 'Collaborate', meaningVi: 'Cộng tác, hợp tác' },
  { id: 'p3', wordEn: 'Mandatory', meaningVi: 'Bắt buộc, theo quy định' },
  { id: 'p4', wordEn: 'Implement', meaningVi: 'Thi hành, áp dụng' },
  { id: 'p5', wordEn: 'Eligible', meaningVi: 'Đủ tư cách, đủ điều kiện' },
  { id: 'p6', wordEn: 'Negotiate', meaningVi: 'Đàm phán, thương lượng' },
  { id: 'p7', wordEn: 'Comply with', meaningVi: 'Tuân thủ theo' },
  { id: 'p8', wordEn: 'Substantial', meaningVi: 'Đáng kể, quan trọng' },
  { id: 'p9', wordEn: 'Terminate', meaningVi: 'Chấm dứt, kết thúc' },
  { id: 'p10', wordEn: 'Anticipate', meaningVi: 'Dự đoán, lường trước' },
];

export class FakeGameRepository {
  async getGameCatalog() {
    await delay(200);
    return [
      {
        id: 'word-match',
        title: 'Nối Từ Siêu Tốc (Word Match)',
        description: 'Ghép cặp từ tiếng Anh và nghĩa tiếng Việt tương ứng trước khi hết giờ!',
        icon: 'Zap',
        difficulty: 'Mọi cấp độ',
        playTime: '45 giây',
        playedCount: 4120,
        highScore: 850,
        active: true,
      },
      {
        id: 'speed-listening',
        title: 'Bắt Từ Nghe Nhanh (Speed Listening)',
        description: 'Nghe phát âm từ vựng và chọn mặt chữ chính xác trong 3 giây.',
        icon: 'Headphones',
        difficulty: 'Part 1 & 2',
        playTime: '60 giây',
        playedCount: 2350,
        highScore: 620,
        active: false,
      },
      {
        id: 'grammar-duel',
        title: 'Đấu Trường Ngữ Pháp (Grammar Duel)',
        description: 'Vượt qua các câu hỏi trắc nghiệm ngữ pháp Part 5 với điểm số nhân bội.',
        icon: 'Trophy',
        difficulty: 'Part 5',
        playTime: '90 giây',
        playedCount: 1890,
        highScore: 740,
        active: false,
      },
    ];
  }

  async getWordMatchSession(pairCount = 6) {
    await delay(300);
    // Shuffle and pick `pairCount` pairs
    const shuffled = [...WORD_PAIRS_BANK].sort(() => 0.5 - Math.random());
    const selectedPairs = shuffled.slice(0, pairCount);

    // Create cards: one for EN, one for VI
    const cards = [];
    selectedPairs.forEach((pair) => {
      cards.push({
        cardId: `${pair.id}-en`,
        pairId: pair.id,
        text: pair.wordEn,
        lang: 'en',
      });
      cards.push({
        cardId: `${pair.id}-vi`,
        pairId: pair.id,
        text: pair.meaningVi,
        lang: 'vi',
      });
    });

    // Shuffle the cards on the board
    const boardCards = cards.sort(() => 0.5 - Math.random());

    return {
      sessionId: `game_sess_${Date.now()}`,
      timeLimitSeconds: 45,
      pairs: selectedPairs,
      cards: boardCards,
    };
  }

  async submitGameResult(sessionId, score, clearedPairs) {
    await delay(200);
    const prevHigh = Number(localStorage.getItem('game_word_match_high') || 0);
    const isNewHigh = score > prevHigh;
    if (isNewHigh) {
      localStorage.setItem('game_word_match_high', String(score));
    }

    return {
      sessionId,
      score,
      clearedPairs,
      isNewHigh,
      highScore: isNewHigh ? score : prevHigh,
    };
  }
}
