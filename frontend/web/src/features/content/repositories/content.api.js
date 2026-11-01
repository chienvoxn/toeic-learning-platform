import { apiRequest } from '@/services/apiClient';
import { toClientContentList, toClientContent } from '../api/content.mapper';

export class RemoteContentRepository {
  async getContents(filters = {}) {
    const params = new URLSearchParams();
    if (filters.category && filters.category !== 'Tất cả') params.append('category', filters.category);
    if (filters.difficulty && filters.difficulty !== 'Tất cả') params.append('difficulty', filters.difficulty);
    if (filters.search) params.append('q', filters.search);

    const data = await apiRequest(`/contents?${params.toString()}`);
    return toClientContentList(data.content || data);
  }

  async getContentById(id) {
    const data = await apiRequest(`/contents/${id}`);
    return toClientContent(data);
  }

  async toggleBookmark(id) {
    const data = await apiRequest(`/contents/${id}/bookmarks`, {
      method: 'POST',
    });
    return toClientContent(data);
  }

  async getCategories() {
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
