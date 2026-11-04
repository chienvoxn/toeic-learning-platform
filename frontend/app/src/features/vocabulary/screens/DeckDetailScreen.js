import React from "react";
import { View, StyleSheet, ScrollView } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import Text from "../../../components/atoms/AppText";
import AppButton from "../../../components/atoms/AppButton";
import AssetImage from "../../../components/atoms/AssetImage";
import useDeckDetail from "../hooks/useDeckDetail";
import { colors } from "../../../theme/tokens";

export default function DeckDetailScreen({ navigate, deckId }) {
  const { deck, groups } = useDeckDetail(deckId);
  return (
    <ScrollView style={s.screen} contentContainerStyle={s.content}>
      <View style={s.header}>
        <AppButton
          onPress={() => navigate("vocab")}
          accessibilityLabel="Quay lại danh sách từ vựng"
        >
          <Ionicons name="arrow-back" size={22} />
        </AppButton>
        <Text style={s.title} numberOfLines={1}>
          {deck.englishTitle}
        </Text>
      </View>
      <View style={s.stats}>
        {[
          [deck.total, "Tổng số thẻ", "vocabulary.stat-cards"],
          [deck.groups, "Tổng số nhóm", "vocabulary.stat-groups"],
        ].map(([count, label, image]) => (
          <View key={label} style={s.stat}>
            <AssetImage
              assetId={image}
              style={s.statIcon}
              resizeMode="contain"
            />
            <View>
              <Text style={s.statCount}>{count}</Text>
              <Text style={s.statLabel}>{label}</Text>
            </View>
          </View>
        ))}
      </View>
      <AppButton
        onPress={() => navigate("flashcardReview", { deckId: deck.id })}
        style={s.start}
        accessibilityLabel="Bắt đầu học"
      >
        <Ionicons name="play" size={13} color="white" />
        <Text style={s.startText}>Bắt đầu học</Text>
      </AppButton>
      <View style={s.heading}>
        <Text style={s.groupTitle}>Các nhóm</Text>
        <Text style={s.badge}>{deck.groups}</Text>
      </View>
      <View style={s.groups}>
        {groups.map((group) => (
          <AppButton
            key={group.id}
            onPress={() =>
              navigate("flashcardReview", {
                deckId: deck.id,
                groupId: group.id,
              })
            }
            style={s.group}
          >
            <AssetImage
              assetId={`vocabulary.${group.id}`}
              style={s.icon}
              resizeMode="contain"
            />
            <View style={{ flex: 1 }}>
              <Text style={s.name}>{group.title}</Text>
              <Text style={s.total}>{group.total} thẻ</Text>
            </View>
            <Ionicons name="chevron-forward" color="#B3BFCE" size={18} />
          </AppButton>
        ))}
      </View>
    </ScrollView>
  );
}
const s = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  content: { padding: 16, paddingTop: 12, paddingBottom: 26 },
  header: {
    flexDirection: "row",
    alignItems: "center",
    gap: 16,
    marginBottom: 16,
  },
  title: { fontSize: 14, fontWeight: "700", flex: 1 },
  stats: { flexDirection: "row", gap: 10 },
  stat: {
    flex: 1,
    backgroundColor: "white",
    borderRadius: 20,
    padding: 13,
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
  },
  statIcon: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: "center",
    justifyContent: "center",
  },
  statCount: { fontSize: 16, fontWeight: "800" },
  statLabel: { fontSize: 9, color: "#98A2B3", marginTop: 2 },
  start: {
    height: 44,
    backgroundColor: colors.primary,
    borderRadius: 24,
    alignItems: "center",
    justifyContent: "center",
    flexDirection: "row",
    gap: 8,
    marginVertical: 14,
    boxShadow: "0 4px 6px #0084ff22",
  },
  startText: { fontSize: 12, fontWeight: "700", color: "white" },
  heading: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
    marginTop: 4,
    marginBottom: 12,
  },
  groupTitle: { fontSize: 19, fontWeight: "800" },
  badge: {
    fontSize: 10,
    fontWeight: "700",
    color: "white",
    backgroundColor: colors.primary,
    borderRadius: 12,
    paddingHorizontal: 6,
    paddingVertical: 3,
  },
  groups: { gap: 12 },
  group: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    backgroundColor: "white",
    borderRadius: 20,
    padding: 12,
    minHeight: 72,
  },
  icon: {
    height: 54,
    width: 54,
    borderRadius: 16,
    alignItems: "center",
    justifyContent: "center",
  },
  name: { fontSize: 14, fontWeight: "700" },
  total: { fontSize: 10, color: "#98A2B3", marginTop: 4 },
});
