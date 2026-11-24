import React, { useRef, useEffect } from "react";
import {
  View,
  StyleSheet,
  ScrollView,
  TextInput,
  KeyboardAvoidingView,
  Platform,
  ActivityIndicator,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import Text from "../../../components/atoms/AppText";
import AppButton from "../../../components/atoms/AppButton";
import useAIChat from "../hooks/useAIChat";
import { colors } from "../../../theme/tokens";

export default function AIChatScreen({ goBack }) {
  const {
    messages,
    inputText,
    setInputText,
    sending,
    handleSend,
    quickPrompts,
  } = useAIChat();

  const scrollRef = useRef(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollToEnd({ animated: true });
    }
  }, [messages, sending]);

  return (
    <KeyboardAvoidingView
      style={s.screen}
      behavior={Platform.OS === "ios" ? "padding" : undefined}
    >
      {/* Header */}
      <View style={s.header}>
        <AppButton onPress={goBack} style={s.backBtn}>
          <Ionicons name="arrow-back" size={20} color="#1E293B" />
        </AppButton>
        <View style={s.headerTitleArea}>
          <Text style={s.headerTitle}>Parroto AI Tutor</Text>
          <View style={s.statusRow}>
            <View style={s.onlineDot} />
            <Text style={s.onlineText}>Trực tuyến · Sẵn sàng trợ giúp</Text>
          </View>
        </View>
        <View style={{ width: 36 }} />
      </View>

      {/* Messages */}
      <ScrollView
        ref={scrollRef}
        contentContainerStyle={s.messagesContent}
        keyboardShouldPersistTaps="handled"
      >
        {messages.map((m) => {
          const isUser = m.role === "user";
          return (
            <View
              key={m.id}
              style={[s.msgWrapper, isUser ? s.msgUserWrap : s.msgBotWrap]}
            >
              {!isUser && (
                <View style={s.botAvatar}>
                  <Ionicons name="sparkles" size={14} color="#8A2BE2" />
                </View>
              )}
              <View
                style={[s.msgBubble, isUser ? s.msgUserBubble : s.msgBotBubble]}
              >
                <Text
                  style={[s.msgText, isUser ? s.msgUserText : s.msgBotText]}
                >
                  {m.content}
                </Text>
              </View>
            </View>
          );
        })}

        {sending && (
          <View style={[s.msgWrapper, s.msgBotWrap]}>
            <View style={s.botAvatar}>
              <Ionicons name="sparkles" size={14} color="#8A2BE2" />
            </View>
            <View style={[s.msgBubble, s.msgBotBubble, s.typingBubble]}>
              <ActivityIndicator size="small" color={colors.primary} />
              <Text style={s.typingText}>Parroto đang suy nghĩ…</Text>
            </View>
          </View>
        )}
      </ScrollView>

      {/* Quick Prompts */}
      <View style={s.promptsContainer}>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={s.promptsScroll}
        >
          {quickPrompts.map((p, idx) => (
            <AppButton
              key={idx}
              style={s.promptChip}
              onPress={() => handleSend(p)}
            >
              <Text style={s.promptChipText}>{p}</Text>
            </AppButton>
          ))}
        </ScrollView>
      </View>

      {/* Input Bar */}
      <View style={s.inputBar}>
        <TextInput
          style={s.input}
          placeholder="Hỏi Parroto AI về TOEIC..."
          value={inputText}
          onChangeText={setInputText}
          multiline
          maxLength={1000}
        />
        <AppButton
          style={[
            s.sendBtn,
            (!inputText.trim() || sending) && s.sendBtnDisabled,
          ]}
          disabled={!inputText.trim() || sending}
          onPress={() => handleSend()}
        >
          <Ionicons name="arrow-up" size={20} color="white" />
        </AppButton>
      </View>
    </KeyboardAvoidingView>
  );
}

const s = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#F8FAFC" },
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 12,
    backgroundColor: "white",
    borderBottomWidth: 1,
    borderBottomColor: "#E2E8F0",
  },
  backBtn: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: "#F1F5F9",
    alignItems: "center",
    justifyContent: "center",
  },
  headerTitleArea: { alignItems: "center" },
  headerTitle: { fontSize: 15, fontWeight: "800", color: "#0F172A" },
  statusRow: { flexDirection: "row", alignItems: "center", gap: 5, marginTop: 2 },
  onlineDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: "#22C55E" },
  onlineText: { fontSize: 10, color: "#64748B", fontWeight: "600" },
  messagesContent: { padding: 16, paddingBottom: 16, gap: 12 },
  msgWrapper: { flexDirection: "row", alignItems: "flex-end", gap: 8 },
  msgUserWrap: { justifyContent: "flex-end" },
  msgBotWrap: { justifyContent: "flex-start" },
  botAvatar: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: "#F3E8FF",
    alignItems: "center",
    justifyContent: "center",
  },
  msgBubble: {
    maxWidth: "80%",
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 18,
  },
  msgUserBubble: {
    backgroundColor: colors.primary,
    borderBottomRightRadius: 4,
  },
  msgBotBubble: {
    backgroundColor: "white",
    borderBottomLeftRadius: 4,
    borderWidth: 1,
    borderColor: "#E2E8F0",
  },
  msgText: { fontSize: 13, lineHeight: 20 },
  msgUserText: { color: "white" },
  msgBotText: { color: "#1E293B" },
  typingBubble: { flexDirection: "row", alignItems: "center", gap: 8 },
  typingText: { fontSize: 12, color: "#64748B" },
  promptsContainer: { backgroundColor: "white", paddingVertical: 8 },
  promptsScroll: { paddingHorizontal: 16, gap: 8 },
  promptChip: {
    backgroundColor: "#F1F5F9",
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: "#E2E8F0",
  },
  promptChipText: { fontSize: 11, color: "#475569", fontWeight: "600" },
  inputBar: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 16,
    paddingVertical: 10,
    backgroundColor: "white",
    borderTopWidth: 1,
    borderTopColor: "#E2E8F0",
    gap: 10,
  },
  input: {
    flex: 1,
    backgroundColor: "#F1F5F9",
    borderRadius: 20,
    paddingHorizontal: 16,
    paddingVertical: 10,
    fontSize: 13,
    maxHeight: 100,
    color: "#0F172A",
  },
  sendBtn: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: colors.primary,
    alignItems: "center",
    justifyContent: "center",
  },
  sendBtnDisabled: { opacity: 0.4 },
});
