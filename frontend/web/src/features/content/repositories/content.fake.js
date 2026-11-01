import { toClientContentList, toClientContent } from '../api/content.mapper';

const delay = (ms = 300) => new Promise((resolve) => setTimeout(resolve, ms));

const MOCK_CONTENTS = [
  // Nhóm 1: TOEIC & Hội thoại (MainScreen.png)
  {
    id: 'cnt-01',
    title: 'TOEIC Part 1: Chiến thuật nghe tranh tả người và vật',
    description: 'Nắm vững các bẫy phổ biến trong Part 1 và từ vựng trọng tâm',
    category: 'TOEIC',
    part: 1,
    difficulty: 'A2',
    media_type: 'video',
    media_url: 'https://www.youtube.com/watch?v=mock1',
    thumbnail_url: 'https://images.unsplash.com/photo-1434030216411-0b793f4b4173?w=500&q=80',
    source_platform: 'YouTube',
    duration_minutes: 12,
    total_views: 1420,
    is_bookmarked: false,
  },
  {
    id: 'cnt-02',
    title: 'Hội thoại công sở: Đàm phán hợp đồng cung ứng',
    description: 'Luyện nghe câu hỏi & phản hồi Part 2 trong môi trường công sở thực tế',
    category: 'Hội thoại',
    part: 2,
    difficulty: 'B1',
    media_type: 'audio',
    media_url: 'https://assets.example.com/audio/contract-negotiation.mp3',
    thumbnail_url: 'https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=500&q=80',
    source_platform: 'Audio',
    duration_minutes: 8,
    total_views: 980,
    is_bookmarked: true,
  },
  {
    id: 'cnt-03',
    title: 'Đoạn phim ngắn: Tranh luận dự án trong buổi họp chiến lược',
    description: 'Học từ vựng và ngữ điệu tự nhiên qua trích đoạn phim văn phòng',
    category: 'Phim',
    part: 3,
    difficulty: 'B2',
    media_type: 'video',
    media_url: 'https://www.youtube.com/watch?v=mock3',
    thumbnail_url: 'https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?w=500&q=80',
    source_platform: 'YouTube',
    duration_minutes: 6,
    total_views: 2310,
    is_bookmarked: false,
  },

  // Nhóm 2: Luyện nghe & Giải trí (MainScreen-1.png)
  {
    id: 'cnt-04',
    title: 'TED-Ed: Tại sao chúng ta lại trì hoãn công việc?',
    description: 'Nâng cao vốn từ vựng học thuật và kỹ năng bắt ý chính (Main Idea)',
    category: 'Luyện nghe',
    part: 4,
    difficulty: 'B2',
    media_type: 'video',
    media_url: 'https://www.youtube.com/watch?v=mock4',
    thumbnail_url: 'https://images.unsplash.com/photo-1507679799987-c73779587ccf?w=500&q=80',
    source_platform: 'YouTube',
    duration_minutes: 10,
    total_views: 3100,
    is_bookmarked: true,
  },
  {
    id: 'cnt-05',
    title: 'Talkshow Giải trí: Phỏng vấn diễn viên đoạt giải Oscar',
    description: 'Nghe ngữ điệu thực tế của người bản xứ và từ lóng hàng ngày',
    category: 'Giải trí',
    part: 3,
    difficulty: 'B1',
    media_type: 'video',
    media_url: 'https://www.youtube.com/watch?v=mock5',
    thumbnail_url: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500&q=80',
    source_platform: 'YouTube',
    duration_minutes: 7,
    total_views: 1890,
    is_bookmarked: false,
  },

  // Nhóm 3: Bài hát, Tin tức & Khoa học (MainScreen-2.png)
  {
    id: 'cnt-06',
    title: 'BBC 6 Minute English: Tương lai của trí tuệ nhân tạo',
    description: 'Học từ vựng khoa học công nghệ kèm transcript giải thích chi tiết',
    category: 'Khoa học',
    part: 4,
    difficulty: 'B2',
    media_type: 'audio',
    media_url: 'https://assets.example.com/audio/bbc-ai.mp3',
    thumbnail_url: 'https://images.unsplash.com/photo-1485827404703-89b55fcc595e?w=500&q=80',
    source_platform: 'Podcast',
    duration_minutes: 6,
    total_views: 4200,
    is_bookmarked: true,
  },
  {
    id: 'cnt-07',
    title: 'Bản tin kinh tế Reuters: Dự báo lãi suất toàn cầu',
    description: 'Chủ đề tài chính kinh doanh thường xuyên gặp trong Part 4 & Part 7',
    category: 'Tin tức',
    part: 4,
    difficulty: 'C1',
    media_type: 'video',
    media_url: 'https://www.youtube.com/watch?v=mock7',
    thumbnail_url: 'https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?w=500&q=80',
    source_platform: 'News',
    duration_minutes: 5,
    total_views: 860,
    is_bookmarked: false,
  },
  {
    id: 'cnt-08',
    title: 'Học từ vựng qua bài hát: "Count on Me" - Bruno Mars',
    description: 'Nối từ, điền từ vào chỗ trống và học các cụm Idioms thú vị',
    category: 'Bài hát',
    part: 1,
    difficulty: 'A1',
    media_type: 'video',
    media_url: 'https://www.youtube.com/watch?v=mock8',
    thumbnail_url: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&q=80',
    source_platform: 'YouTube',
    duration_minutes: 4,
    total_views: 5200,
    is_bookmarked: false,
  },

  // Nhóm 4: Truyện, Podcast & Anime (MainScreen-3.png)
  {
    id: 'cnt-09',
    title: 'English Story: Chuyến công tác bất ngờ tại Singapore',
    description: 'Truyện ngắn có lồng ghép 20 từ vựng TOEIC trọng điểm về du lịch',
    category: 'Truyện',
    part: 3,
    difficulty: 'B1',
    media_type: 'audio',
    media_url: 'https://assets.example.com/audio/story-singapore.mp3',
    thumbnail_url: 'https://images.unsplash.com/photo-1525610553991-2bede1a236e2?w=500&q=80',
    source_platform: 'Audio',
    duration_minutes: 9,
    total_views: 1750,
    is_bookmarked: false,
  },
  {
    id: 'cnt-10',
    title: 'Podcast: Phương pháp luyện nghe chủ động (Active Listening)',
    description: 'Chia sẻ từ các cao thủ TOEIC 900+ về cách cải thiện điểm Listening',
    category: 'Podcast',
    part: 4,
    difficulty: 'B1',
    media_type: 'audio',
    media_url: 'https://assets.example.com/audio/podcast-listening-tips.mp3',
    thumbnail_url: 'https://images.unsplash.com/photo-1478737270239-2f02b77fc618?w=500&q=80',
    source_platform: 'Podcast',
    duration_minutes: 15,
    total_views: 2900,
    is_bookmarked: true,
  },
  {
    id: 'cnt-11',
    title: 'Anime English Dub: Cuộc phiêu lưu tìm lại giọng nói',
    description: 'Luyện nghe phát âm rõ ràng, chuẩn giọng Bắc Mỹ qua lồng tiếng hoạt hình',
    category: 'Anime',
    part: 3,
    difficulty: 'A2',
    media_type: 'video',
    media_url: 'https://www.youtube.com/watch?v=mock11',
    thumbnail_url: 'https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80',
    source_platform: 'YouTube',
    duration_minutes: 8,
    total_views: 3400,
    is_bookmarked: false,
  },
];

export class FakeContentRepository {
  contents = [...MOCK_CONTENTS];

  async getContents(filters = {}) {
    await delay(300);
    let result = [...this.contents];

    if (filters.category && filters.category !== 'Tất cả') {
      result = result.filter((item) => item.category === filters.category);
    }
    if (filters.difficulty && filters.difficulty !== 'Tất cả') {
      result = result.filter((item) => item.difficulty === filters.difficulty);
    }
    if (filters.search) {
      const q = filters.search.toLowerCase().trim();
      result = result.filter(
        (item) =>
          item.title.toLowerCase().includes(q) ||
          item.description.toLowerCase().includes(q) ||
          item.category.toLowerCase().includes(q)
      );
    }

    return toClientContentList(result);
  }

  async getContentById(id) {
    await delay(200);
    const found = this.contents.find((c) => c.id === id);
    if (!found) throw new Error('Không tìm thấy nội dung học tập.');
    return toClientContent(found);
  }

  async toggleBookmark(id) {
    await delay(150);
    const item = this.contents.find((c) => c.id === id);
    if (!item) throw new Error('Nội dung không tồn tại.');
    item.is_bookmarked = !item.is_bookmarked;
    return toClientContent(item);
  }

  async getCategories() {
    await delay(100);
    return [
      'Tất cả',
      'TOEIC',
      'Hội thoại',
      'Phim',
      'Luyện nghe',
      'Giải trí',
      'Khoa học',
      'Tin tức',
      'Bài hát',
      'Truyện',
      'Podcast',
      'Anime',
    ];
  }
}
