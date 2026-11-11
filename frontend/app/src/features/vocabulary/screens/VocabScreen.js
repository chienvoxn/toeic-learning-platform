import React from "react";
import { View, StyleSheet, ScrollView } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import Text from "../../../components/atoms/AppText";
import AssetImage from "../../../components/atoms/AssetImage";
import AppButton from "../../../components/atoms/AppButton";
import useVocabularyLibrary from "../hooks/useVocabularyLibrary";
import { colors } from "../../../theme/tokens";

export default function VocabScreen({ navigate }) {
  const { decks } = useVocabularyLibrary();
  return (
    <ScrollView style={s.screen} contentContainerStyle={s.content}>
      <View style={s.header}>
        <View>
          <Text style={s.title}>Từ vựng</Text>
          <Text style={s.subtitle}>Bộ thẻ có sẵn và từ bạn tự lưu</Text>
        </View>
        <AppButton
          onPress={() => navigate("flashcardReview")}
          accessibilityLabel="Từ đã lưu"
        >
          <Ionicons name="bookmark-outline" size={22} color="#11141A" />
        </AppButton>
      </View>
      {["Từ Vựng Tiếng Anh Thông Dụng", "Từ Vựng Oxford"].map((section) => (
        <View key={section}>
          <View style={s.sectionHeading}>
            <Text style={s.sectionTitle}>{section}</Text>
            <View style={s.count}>
              <Text style={s.countText}>
                {section.includes("Oxford") ? 6 : 2}
              </Text>
            </View>
          </View>
          <View style={s.grid}>
            {decks
              .filter((d) => d.category === section)
              .map((deck) => (
                <View key={deck.id} style={s.card}>
                  <AppButton
                    onPress={() => navigate("deckDetail", { deckId: deck.id })}
                    accessibilityLabel={`Xem ${deck.title}`}
                  >
                    <AssetImage assetId={deck.imageId} style={s.cover} />
                  </AppButton>
                  <View style={s.body}>
                    <Text style={s.deckTitle} numberOfLines={2}>
                      {deck.title}
                    </Text>
                    <Text style={s.total}>✧ {deck.total} thẻ</Text>
                    <AppButton
                      style={s.start}
                      onPress={() =>
                        navigate("deckDetail", { deckId: deck.id })
                      }
                    >
                      <Text style={s.startText}>Bắt đầu học</Text>
                    </AppButton>
                  </View>
                </View>
              ))}
          </View>
        </View>
      ))}
    </ScrollView>
  );
}
const s = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  content: { paddingHorizontal: 16, paddingTop: 0, paddingBottom: 20 },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 16,
    paddingHorizontal: 4,
  },
  title: { fontSize: 30, lineHeight: 38, fontWeight: "800", color: "#11141A" },
  subtitle: { fontSize: 14, lineHeight: 20, color: "#7E8594" },
  sectionHeading: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    marginBottom: 14,
    marginTop: 6,
  },
  sectionTitle: { fontSize: 18, lineHeight: 24, fontWeight: "700" },
  count: {
    backgroundColor: "#1A8CFF",
    borderRadius: 12,
    width: 23,
    height: 22,
    alignItems: "center",
    justifyContent: "center",
  },
  countText: { color: "white", fontSize: 10, fontWeight: "700" },
  grid: { flexDirection: "row", flexWrap: "wrap", gap: 12, marginBottom: 24 },
  card: {
    width: "48%",
    flexGrow: 1,
    maxWidth: "49%",
    backgroundColor: "white",
    borderRadius: 18,
    overflow: "hidden",
    borderWidth: 1,
    borderColor: "#EEF1F6",
  },
  cover: { width: "100%", aspectRatio: 173 / 150 },
  body: { padding: 10, minHeight: 136 },
  deckTitle: {
    fontSize: 14,
    lineHeight: 20,
    fontWeight: "700",
    height: 40,
    color: "#11141A",
  },
  total: { fontSize: 12, color: "#98A2B3", marginTop: 16, marginBottom: 9 },
  start: {
    backgroundColor: "#1A8CFF",
    borderRadius: 18,
    height: 36,
    alignItems: "center",
    justifyContent: "center",
  },
  startText: { fontSize: 12, fontWeight: "700", color: "white" },
});
