import React from "react";
import { View, StyleSheet, ScrollView, Alert } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import Text from "../../../components/atoms/AppText";
import AppButton from "../../../components/atoms/AppButton";
import { colors } from "../../../theme/tokens";

export default function ContentDetailScreen({
  content,
  contentId,
  navigate,
  goBack,
}) {
  const item = content || {
    id: contentId || "sample-article",
    title: "10 Cụm từ thông dụng nhất trong giao tiếp văn phòng TOEIC",
    diff: "B1",
    tagColor: "#F4ECFF",
    duration: "4 phút đọc",
    author: "Đội ngũ học thuật TOEIC",
    category: "TOEIC Tips",
    body: `Trong môi trường làm việc quốc tế và các bài thi TOEIC Listening/Reading (Part 3, 4, 7), các thành ngữ và cụm từ công sở xuất hiện với tần suất rất cao.

1. "Touch base"
Nghĩa: Liên lạc, trao đổi ngắn để cập nhật tiến độ công việc.
Ví dụ: "Let's touch base next Monday to discuss the quarterly revenue report."

2. "On the same page"
Nghĩa: Đồng thuận, cùng hiểu một vấn đề giống nhau.
Ví dụ: "Before we sign the vendor contract, I want to make sure we are all on the same page."

3. "Behind schedule" vs "Ahead of schedule"
Nghĩa: Chậm tiến độ / Vượt tiến độ so với kế hoạch ban đầu.
Ví dụ: "The factory renovation is two weeks ahead of schedule."

4. "Bring up"
Nghĩa: Đưa ra một vấn đề, chủ đề để thảo luận trong cuộc họp.
Ví dụ: "Mr. Henderson brought up the issue of overtime compensation during the staff meeting."`,
    keyVocabs: [
      { word: "touch base", ipa: "/tʌtʃ beɪs/", meaning: "liên lạc trao đổi" },
      { word: "quarterly", ipa: "/ˈkwɔːtəli/", meaning: "hàng quý" },
      { word: "renovation", ipa: "/ˌrenəˈveɪʃn/", meaning: "sự nâng cấp, cải tạo" },
      { word: "overtime", ipa: "/ˈəʊvətaɪm/", meaning: "làm thêm giờ" },
    ],
  };

  return (
    <View style={s.screen}>
      <View style={s.header}>
        <AppButton onPress={goBack} style={s.backBtn}>
          <Ionicons name="arrow-back" size={20} color="#1E293B" />
        </AppButton>
        <Text style={s.headerTitle} numberOfLines={1}>
          {item.category || "Bài học"}
        </Text>
        <AppButton
          onPress={() =>
            Alert.alert("Đã lưu bài học", "Bài học đã được thêm vào mục yêu thích.")
          }
          style={s.bookmarkBtn}
        >
          <Ionicons name="bookmark-outline" size={20} color="#64748B" />
        </AppButton>
      </View>

      <ScrollView contentContainerStyle={s.content}>
        {/* Banner Tag */}
        <View style={s.metaRow}>
          <Text style={s.levelBadge}>{item.diff || "B1"}</Text>
          <Text style={s.durationText}>⏱ {item.duration || "5 phút"}</Text>
        </View>

        <Text style={s.title}>{item.title}</Text>

        {/* Audio Player Bar */}
        <View style={s.audioBar}>
          <AppButton
            style={s.playCircle}
            onPress={() =>
              Alert.alert("Phát bài đọc", "Đang phát giọng đọc tiếng Anh bản xứ…")
            }
          >
            <Ionicons name="play" size={18} color="white" />
          </AppButton>
          <View style={{ flex: 1 }}>
            <Text style={s.audioTrack}>Giọng đọc bản ngữ Mỹ (US Audio)</Text>
            <Text style={s.audioSub}>Tốc độ 1.0x · Giúp luyện nghe shadowing</Text>
          </View>
        </View>

        {/* Article Body */}
        <View style={s.bodyCard}>
          <Text style={s.bodyText}>{item.body}</Text>
        </View>

        {/* Key Vocabularies */}
        {item.keyVocabs && item.keyVocabs.length > 0 && (
          <View style={s.vocabSection}>
            <Text style={s.vocabHeading}>TỪ VỰNG TRỌNG TÂM CẦN NHỚ</Text>
            <View style={s.vocabList}>
              {item.keyVocabs.map((v, i) => (
                <View key={i} style={s.vocabCard}>
                  <View style={{ flex: 1 }}>
                    <Text style={s.vocabWord}>{v.word}</Text>
                    <Text style={s.vocabIpa}>{v.ipa}</Text>
                  </View>
                  <Text style={s.vocabMeaning}>{v.meaning}</Text>
                </View>
              ))}
            </View>
          </View>
        )}
      </ScrollView>

      {/* Action Footer */}
      <View style={s.footer}>
        <AppButton
          style={s.practiceBtn}
          onPress={() => navigate("vocab")}
        >
          <Ionicons name="albums-outline" size={18} color="white" />
          <Text style={s.practiceBtnText}>Học từ vựng của bài này</Text>
        </AppButton>
      </View>
    </View>
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
  headerTitle: { fontSize: 14, fontWeight: "700", color: "#1E293B" },
  bookmarkBtn: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: "#F1F5F9",
    alignItems: "center",
    justifyContent: "center",
  },
  content: { padding: 16, paddingBottom: 24 },
  metaRow: { flexDirection: "row", alignItems: "center", gap: 10, marginBottom: 10 },
  levelBadge: {
    backgroundColor: "#DCFCE7",
    color: "#166534",
    fontSize: 10,
    fontWeight: "800",
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 8,
  },
  durationText: { fontSize: 11, color: "#64748B", fontWeight: "600" },
  title: {
    fontSize: 18,
    fontWeight: "800",
    color: "#0F172A",
    lineHeight: 26,
    marginBottom: 16,
  },
  audioBar: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    backgroundColor: "#EFF6FF",
    padding: 12,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: "#BFDBFE",
    marginBottom: 16,
  },
  playCircle: {
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: colors.primary,
    alignItems: "center",
    justifyContent: "center",
  },
  audioTrack: { fontSize: 12, fontWeight: "700", color: "#1E293B" },
  audioSub: { fontSize: 10, color: "#64748B", marginTop: 2 },
  bodyCard: {
    backgroundColor: "white",
    borderRadius: 16,
    padding: 18,
    borderWidth: 1,
    borderColor: "#E2E8F0",
    marginBottom: 20,
  },
  bodyText: { fontSize: 13, lineHeight: 22, color: "#334155" },
  vocabSection: { marginTop: 4 },
  vocabHeading: {
    fontSize: 11,
    fontWeight: "800",
    color: "#64748B",
    letterSpacing: 0.5,
    marginBottom: 10,
  },
  vocabList: { gap: 8 },
  vocabCard: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    backgroundColor: "white",
    padding: 12,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: "#E2E8F0",
  },
  vocabWord: { fontSize: 13, fontWeight: "800", color: "#0F172A" },
  vocabIpa: { fontSize: 11, color: "#94A3B8", marginTop: 2 },
  vocabMeaning: { fontSize: 12, fontWeight: "700", color: colors.primary },
  footer: {
    padding: 16,
    backgroundColor: "white",
    borderTopWidth: 1,
    borderTopColor: "#E2E8F0",
  },
  practiceBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    backgroundColor: colors.primary,
    paddingVertical: 14,
    borderRadius: 16,
  },
  practiceBtnText: { color: "white", fontWeight: "800", fontSize: 14 },
});
