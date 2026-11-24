import { useState, useEffect, useCallback } from "react";
import { aiApi } from "../../../services/ai.api";

export const QUICK_PROMPTS = [
  "Phân biệt thì Hiện tại hoàn thành và Quá khứ đơn",
  "Mẹo tránh bẫy trong TOEIC Part 5",
  "Cụm từ thông dụng hay gặp trong TOEIC Part 7",
  "Giải thích cách dùng liên từ Although / Despite",
];

export default function useAIChat() {
  const [conversationId, setConversationId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [inputText, setInputText] = useState("");
  const [sending, setSending] = useState(false);

  useEffect(() => {
    let mounted = true;
    async function initChat() {
      try {
        const conv = await aiApi.createConversation();
        if (mounted && conv?.id) {
          setConversationId(conv.id);
          const initialMsgs = await aiApi.getMessages(conv.id);
          if (mounted) setMessages(initialMsgs || []);
        }
      } catch {
        if (mounted) {
          setMessages([
            {
              id: "msg-welcome",
              role: "assistant",
              content:
                "Xin chào! Mình là Parroto AI Tutor. Hãy hỏi mình bất kỳ câu hỏi nào về ngữ pháp, từ vựng hoặc mẹo thi TOEIC!",
              createdAt: new Date().toISOString(),
            },
          ]);
        }
      }
    }
    initChat();
    return () => {
      mounted = false;
    };
  }, []);

  const handleSend = useCallback(
    async (textToSend) => {
      const text = (textToSend || inputText).trim();
      if (!text || sending) return;

      const userMsg = {
        id: "usr-" + Date.now(),
        role: "user",
        content: text,
        createdAt: new Date().toISOString(),
      };

      setMessages((prev) => [...prev, userMsg]);
      setInputText("");
      setSending(true);

      try {
        const resp = await aiApi.askMessage(conversationId, text);
        if (resp) {
          setMessages((prev) => [
            ...prev,
            {
              id: resp.id || "bot-" + Date.now(),
              role: "assistant",
              content: resp.content || resp.answer || "Không có câu trả lời.",
              createdAt: new Date().toISOString(),
            },
          ]);
        }
      } catch {
        setMessages((prev) => [
          ...prev,
          {
            id: "err-" + Date.now(),
            role: "assistant",
            content:
              "Xin lỗi, hiện tại không thể kết nối tới máy chủ AI. Vui lòng thử lại sau.",
            createdAt: new Date().toISOString(),
          },
        ]);
      } finally {
        setSending(false);
      }
    },
    [inputText, sending, conversationId],
  );

  return {
    messages,
    inputText,
    setInputText,
    sending,
    handleSend,
    quickPrompts: QUICK_PROMPTS,
  };
}
