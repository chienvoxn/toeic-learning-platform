import { request } from "./apiClient.js";

export const contentApi = {
  async getContents(query = {}) {
    const params = new URLSearchParams();
    if (query.category && query.category !== "Tất cả") {
      params.append("category", query.category);
    }
    if (query.search) {
      params.append("search", query.search);
    }
    const queryString = params.toString();
    const endpoint = `/contents${queryString ? `?${queryString}` : ""}`;
    return await request(endpoint);
  },

  async getContentDetail(id) {
    return await request(`/contents/${id}`);
  },
};
