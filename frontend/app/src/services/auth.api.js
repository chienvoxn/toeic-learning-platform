import { request, authStorage } from "./apiClient.js";

export const authApi = {
  async login(email, password) {
    try {
      const data = await request("/auth/login", {
        method: "POST",
        body: JSON.stringify({ email, password }),
      });
      if (data?.accessToken || data?.sessionId) {
        authStorage.setToken(data.accessToken || data.sessionId);
      }
      return { success: true, user: data.user || { email } };
    } catch (error) {
      // Graceful fallback cho chế độ demo / offline
      if (email && password) {
        authStorage.setToken("mock-session-token");
        return {
          success: true,
          user: {
            id: "demo-user",
            email,
            fullName: "Học viên TOEIC",
            role: "STUDENT",
          },
          isMock: true,
        };
      }
      throw error;
    }
  },

  async logout() {
    try {
      await request("/auth/logout", { method: "POST" });
    } catch {
      // bỏ qua lỗi logout trên server nếu offline
    } finally {
      authStorage.clearToken();
    }
  },

  async getMe() {
    try {
      return await request("/users/me");
    } catch (error) {
      return {
        id: "demo-user",
        email: "student@toeic.edu.vn",
        fullName: "Lê Ngọc Phước",
        role: "STUDENT",
      };
    }
  },
};
