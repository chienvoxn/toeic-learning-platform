import { apiRequest } from '@/services/apiClient';
import { toClientContent } from '../api/content.mapper';

// Duyệt hết các trang khi màn hình hiện tại không có điều khiển phân trang.
async function allPages(path) {
  const join = path.includes('?') ? '&' : '?';
  const first = await apiRequest(`${path}${join}page=0&size=100`);
  const items = [...first.content];
  for (let page = 1; page < first.totalPages; page++) {
    const next = await apiRequest(`${path}${join}page=${page}&size=100`);
    items.push(...next.content);
  }
  return items;
}
export class RemoteContentRepository {
  async getContents(filters = {}) {
    const params = new URLSearchParams();
    if (filters.difficulty && filters.difficulty !== 'Tất cả') params.set('difficulty', filters.difficulty);
    if (filters.search) params.set('q', filters.search);
    const [contents, bookmarks] = await Promise.all([
      allPages(`/contents?${params}`),
      apiRequest('/users/me/bookmarks?page=0&size=100').then(async first => {
        const entries = [...first.content];
        for (let page = 1; page < first.totalPages; page++) {
          entries.push(...(await apiRequest(`/users/me/bookmarks?page=${page}&size=100`)).content);
        }
        return entries;
      }).catch(error => { if (error.status === 403) return []; throw error; }),
    ]);
    const saved = new Set(bookmarks.map(entry => entry.content.contentId));
    return contents.map(dto => ({ ...toClientContent(dto), isBookmarked: saved.has(dto.contentId) }))
      .filter(item => !filters.category || filters.category === 'Tất cả' || item.category === filters.category);
  }
  async getContentById(id) { return toClientContent(await apiRequest(`/contents/${id}`)); }
  async toggleBookmark(id, current = false) {
    try { await apiRequest(`/contents/${id}/bookmarks`, { method: current ? 'DELETE' : 'POST' }); }
    catch (error) {
      if (!(current && error.status === 404) && !(!current && error.status === 409)) throw error;
    }
    return { id, isBookmarked: !current };
  }
  recordHistory(id) { return apiRequest(`/contents/${id}/history`, { method: 'POST' }); }
  async getCategories() {
    const entries = await allPages('/contents');
    return ['Tất cả', ...new Set(entries.map(dto => dto.category?.name).filter(Boolean))];
  }
}
