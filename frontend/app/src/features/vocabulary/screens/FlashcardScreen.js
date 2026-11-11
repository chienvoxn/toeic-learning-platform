import React, { useState } from "react";
import { View, StyleSheet, ScrollView, Platform, Alert } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import Text from "../../../components/atoms/AppText";
import AppButton from "../../../components/atoms/AppButton";
import AssetImage from "../../../components/atoms/AssetImage";
import useFlashcardReview from "../hooks/useFlashcardReview";
import { colors } from "../../../theme/tokens";

export default function FlashcardScreen({ navigate }) {
  const {
    cards,
    currentIndex,
    isRevealed,
    setIsRevealed,
    completed,
    current,
    handleRate,
    handleRestart,
  } = useFlashcardReview();
  const [saved, setSaved] = useState(false);
  const speak = (uk = false) => {
    if (Platform.OS === "web" && globalThis.speechSynthesis) {
      const u = new SpeechSynthesisUtterance(current.word);
      u.lang = uk ? "en-GB" : "en-US";
      speechSynthesis.speak(u);
    } else
      Alert.alert(
        "Phát âm",
        `${current.word} ${uk ? current.phoneticUk || current.phonetic : current.phonetic}`,
      );
  };
  if (completed)
    return (
      <View style={s.finished}>
        <Ionicons name="checkmark-circle" size={64} color="#10B981" />
        <Text style={s.finishTitle}>Đã hoàn thành lượt ôn tập!</Text>
        <AppButton style={s.restart} onPress={handleRestart}>
          <Text style={{ color: "white" }}>Ôn lại</Text>
        </AppButton>
        <AppButton onPress={() => navigate("vocab")}>
          <Text>Về thư viện từ vựng</Text>
        </AppButton>
      </View>
    );
  return (
    <View style={s.screen}>
      <View style={s.toolbar}>
        <AppButton
          onPress={() => navigate("vocab")}
          accessibilityLabel="Đóng ôn tập"
          style={s.close}
        >
          <Ionicons name="close" size={22} color="#555A68" />
        </AppButton>
        <View style={{ flex: 1 }} />
        <AppButton
          style={s.flip}
          onPress={() => setIsRevealed(!isRevealed)}
          accessibilityLabel="Lật thẻ"
        >
          <Ionicons name="albums-outline" size={14} color={colors.primary} />
          <Text style={s.flipText}>Lật thẻ⌄</Text>
        </AppButton>
        <AppButton
          style={s.flag}
          onPress={() => setSaved(!saved)}
          accessibilityLabel="Đánh dấu thẻ"
        >
          <Ionicons
            name={saved ? "flag" : "flag-outline"}
            color="#F59E0B"
            size={18}
          />
        </AppButton>
        <AppButton
          style={s.settings}
          onPress={() =>
            Alert.alert(
              "Ôn tập",
              "Lật thẻ để xem nghĩa, sau đó chọn mức độ ghi nhớ.",
            )
          }
          accessibilityLabel="Cài đặt ôn tập"
        >
          <Ionicons name="settings-sharp" size={19} color="#64748B" />
        </AppButton>
      </View>
      <View style={s.progressRow}>
        <Text style={s.progressText}>
          Hoàn thành {Math.round((currentIndex / cards.length) * 100)}%
        </Text>
        <Text style={s.progressCount}>✓ {currentIndex}/49</Text>
      </View>
      <View style={s.progress}>
        <View
          style={[
            s.progressFill,
            { width: `${(currentIndex / cards.length) * 100}%` },
          ]}
        />
      </View>
      <View style={s.card}>
        <ScrollView contentContainerStyle={s.cardContent}>
          <View style={s.wordRow}>
            {current.imageId && (
              <AssetImage
                assetId={current.imageId}
                style={s.image}
                resizeMode="contain"
              />
            )}
            <View style={{ flex: 1 }}>
              <View style={s.wordLine}>
                <Text style={s.word}>{current.word}</Text>
                <Text style={s.type}>{current.type}</Text>
              </View>
              {isRevealed && <Text style={s.meaning}>{current.meaningVi}</Text>}
            </View>
          </View>
          <View style={s.pronunciations}>
            {[false, true].map((uk) => (
              <AppButton
                key={String(uk)}
                onPress={() => speak(uk)}
                style={s.pronunciation}
              >
                <Text style={s.phonetic}>
                  {uk ? "🇬🇧" : "🇺🇸"}{" "}
                  {uk
                    ? current.phoneticUk || current.phonetic
                    : current.phonetic}
                </Text>
                <Ionicons name="volume-high" size={14} color={colors.primary} />
              </AppButton>
            ))}
          </View>
          {isRevealed ? (
            <>
              <Text style={s.definition}>{current.definition}</Text>
              <View style={s.example}>
                <Text style={s.exampleLabel}>❞ VÍ DỤ</Text>
                <Text style={s.exampleEn}>{current.exampleEn}</Text>
                <Text style={s.exampleVi}>{current.exampleVi}</Text>
              </View>
            </>
          ) : (
            <AppButton style={s.reveal} onPress={() => setIsRevealed(true)}>
              <Text style={s.flipText}>Chạm để xem nghĩa</Text>
            </AppButton>
          )}
        </ScrollView>
      </View>
      <View style={s.ratings}>
        {[
          ["Lại", "<10 phút", "#FF3B30", "white"],
          ["Khó", "1–4 ngày", "#FDF1E2", "#DD6B20"],
          ["Tốt", "1–4 ngày", "#E8F8F0", "#10B981"],
          ["Dễ", "4–7 ngày", "#E2F2FE", "#0284C7"],
        ].map(([label, interval, bg, color]) => (
          <AppButton
            key={label}
            disabled={!isRevealed}
            onPress={handleRate}
            style={[
              s.rating,
              { backgroundColor: bg, opacity: isRevealed ? 1 : 0.45 },
            ]}
          >
            <Text style={[s.rateLabel, { color }]}>{label}</Text>
            <Text style={[s.interval, { color }]}>{interval}</Text>
          </AppButton>
        ))}
      </View>
      <AppButton
        style={s.mark}
        accessibilityLabel="Đánh dấu đã thuộc"
        onPress={() => {
          setIsRevealed(true);
          handleRate();
        }}
      >
        <Ionicons name="checkmark-circle" size={15} color="#10B981" />
        <Text style={s.markText}>Đánh dấu đã thuộc</Text>
      </AppButton>
    </View>
  );
}
const s = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#F6F8FC", padding: 16, paddingTop: 10 },
  toolbar: { flexDirection: "row", alignItems: "center", gap: 8 },
  close: { width: 38, height: 38, justifyContent: "center" },
  flip: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    borderRadius: 20,
    padding: 10,
    backgroundColor: "#EBF4FF",
  },
  flipText: { fontSize: 12, fontWeight: "700", color: colors.primary },
  flag: {
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: "#FFF6E8",
    justifyContent: "center",
    alignItems: "center",
  },
  settings: {
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: "#EEF1F6",
    justifyContent: "center",
    alignItems: "center",
  },
  progressRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginTop: 14,
    marginBottom: 12,
  },
  progressText: { fontSize: 11, color: "#7E8594" },
  progressCount: {
    fontSize: 10,
    fontWeight: "700",
    backgroundColor: "#E8F8F0",
    color: "#10B981",
    paddingVertical: 5,
    paddingHorizontal: 9,
    borderRadius: 14,
  },
  progress: {
    height: 5,
    backgroundColor: "#EAEFF6",
    borderRadius: 3,
    marginBottom: 14,
  },
  progressFill: { height: 5, backgroundColor: colors.primary, borderRadius: 3 },
  card: {
    flex: 1,
    backgroundColor: "white",
    borderRadius: 28,
    boxShadow: "0 2px 5px #0f172a08",
    overflow: "hidden",
  },
  cardContent: { padding: 20 },
  wordRow: { flexDirection: "row", alignItems: "center", gap: 12 },
  image: { width: 74, height: 74, borderRadius: 16, overflow: "hidden" },
  wordLine: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    flexWrap: "wrap",
  },
  word: { fontSize: 24, fontWeight: "800" },
  type: {
    fontSize: 9,
    fontWeight: "700",
    backgroundColor: colors.primary,
    color: "white",
    borderRadius: 9,
    paddingHorizontal: 6,
    paddingVertical: 3,
  },
  meaning: {
    fontSize: 22,
    color: colors.primary,
    fontWeight: "700",
    marginTop: 4,
  },
  pronunciations: {
    flexDirection: "row",
    gap: 10,
    marginVertical: 16,
    flexWrap: "wrap",
  },
  pronunciation: {
    flexDirection: "row",
    alignItems: "center",
    gap: 7,
    backgroundColor: "#F1F4FA",
    borderRadius: 18,
    paddingHorizontal: 10,
    height: 29,
  },
  phonetic: { fontSize: 11, color: "#2D3748" },
  definition: {
    fontSize: 14,
    lineHeight: 22,
    color: "#7D8BA6",
    marginBottom: 16,
  },
  example: {
    padding: 16,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: "#FEEBC8",
    backgroundColor: "#FFFDF8",
  },
  exampleLabel: {
    fontSize: 12,
    fontWeight: "700",
    color: "#DD6B20",
    marginBottom: 8,
  },
  exampleEn: { fontSize: 14, lineHeight: 22, color: "#1A202C" },
  exampleVi: { fontSize: 12, lineHeight: 20, color: "#718096", marginTop: 4 },
  reveal: {
    alignItems: "center",
    padding: 24,
    marginTop: 24,
    backgroundColor: "#EBF4FF",
    borderRadius: 18,
  },
  ratings: { flexDirection: "row", gap: 8, marginTop: 12 },
  rating: {
    flex: 1,
    height: 64,
    borderRadius: 18,
    alignItems: "center",
    justifyContent: "center",
  },
  rateLabel: { fontSize: 13, fontWeight: "700" },
  interval: { fontSize: 10, marginTop: 4 },
  mark: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    height: 42,
  },
  markText: { fontSize: 12, fontWeight: "700", color: "#10B981" },
  finished: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    gap: 20,
    padding: 20,
  },
  finishTitle: { fontSize: 20, fontWeight: "700" },
  restart: {
    backgroundColor: colors.primary,
    borderRadius: 22,
    paddingHorizontal: 30,
    paddingVertical: 14,
  },
});
