import { request } from "./apiClient.js";

export const aiApi = {
  async createConversation() {
    try {
      return await request("/ai/conversations", { method: "POST" });
    } catch {
      return { id: "mock-conv-" + Date.now(), title: "Hội thoại luyện TOEIC" };
    }
  },

  async getMessages(conversationId) {
    try {
      return await request(`/ai/conversations/${conversationId}/messages`);
    } catch {
      return [
        {
          id: "m-welcome",
          role: "assistant",
          content:
            "Xin chào! Mình là Parroto AI Tutor. Mình có thể giúp bạn giải đáp ngữ pháp TOEIC, dịch nghĩa từ vựng hoặc chữa bài tập. Hãy hỏi mình bất kỳ điều gì nhé!",
          createdAt: new Date().toISOString(),
        },
      ];
    }
  },

  async askMessage(conversationId, question) {
    const requestId = "req-" + Date.now();
    try {
      return await request(`/ai/conversations/${conversationId}/messages`, {
        method: "POST",
        body: JSON.stringify({ requestId, question }),
      });
    } catch {
      // Mock trả lời thông minh khi backend AI service offline
      let answerText = "Parroto AI: Cảm ơn câu hỏi của bạn!";
      const q = question.toLowerCase();
      if (q.includes("part 5") || q.includes("ngữ pháp")) {
        answerText =
          "Trong TOEIC Part 5, hãy chú ý cấu trúc câu: Tìm chủ ngữ (Subject) và động từ chính (Main Verb) trước. Nếu câu đã có đủ S-V, chỗ trống thường là trạng từ (Adverb) bổ nghĩa hoặc mệnh đề quan hệ rút gọn.";
      } else if (q.includes("part 7") || q.includes("đọc hiểu")) {
        answerText =
          "Mẹo làm Part 7: Đọc lướt (skimming) câu hỏi trước để xác định từ khóa (Keyword), sau đó dò (scanning) vị trí tương ứng trong bài đọc để tiết kiệm thời gian.";
      } else if (q.includes("few") && q.includes("a few")) {
        answerText =
          "Sự khác nhau giữa 'few' và 'a few':\n- 'a few': mang nghĩa tích cực, 'một vài, một ít' (đủ dùng).\n- 'few': mang nghĩa tiêu cực, 'rất ít, hầu như không có'. Cả hai đều đi với danh từ đếm được số nhiều.";
      } else {
        answerText = `Parroto AI: Về vấn đề "${question}": Trong bài thi TOEIC, việc nắm chắc từ vựng theo chủ đề kinh doanh (văn phòng, hợp đồng, du lịch, giao hàng) và các thì căn bản sẽ giúp bạn tăng từ 100-200 điểm rất nhanh.`;
      }

      return {
        id: "msg-" + Date.now(),
        role: "assistant",
        content: answerText,
        createdAt: new Date().toISOString(),
      };
    }
  },
};
