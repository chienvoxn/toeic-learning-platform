import React from "react";
import { View, StyleSheet, ScrollView, Alert } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LinearGradient } from "expo-linear-gradient";
import Text from "../../../components/atoms/AppText";
import AppButton from "../../../components/atoms/AppButton";
import AssetImage from "../../../components/atoms/AssetImage";
import { colors } from "../../../theme/tokens";

export default function HomeScreen({ navigate }) {
  return (
    <ScrollView style={s.screen} contentContainerStyle={s.content}>
      <View style={s.header}>
        <View style={{ flex: 1 }}>
          <Text style={s.date}>THỨ TƯ, 26 THÁNG 8</Text>
          <Text style={s.title}>Chào mừng đến{"\n"}Parroto</Text>
        </View>
        <AppButton
          style={s.upgrade}
          onPress={() =>
            Alert.alert(
              "Parroto Pro",
              "Tính năng nâng cấp chưa có trong bản demo.",
            )
          }
        >
          <Text style={s.upgradeText}>Nâng{"\n"}cấp</Text>
        </AppButton>
      </View>
      <View style={s.guestRow}>
        <Text style={s.subtitle}>
          Đăng nhập để theo dõi chuỗi ngày học và{"\n"}tiến độ.
        </Text>
        <AppButton
          onPress={() => navigate("login")}
          style={s.avatar}
          accessibilityLabel="Đăng nhập tài khoản"
        >
          <Ionicons name="person-outline" size={22} color="#94A3B8" />
        </AppButton>
      </View>
      <Text style={s.startLabel}>BẮT ĐẦU NÀO</Text>
      <AppButton onPress={() => navigate("login")} style={s.login}>
        <View style={s.loginTile} />
        <Text style={s.loginText}>Đăng nhập để bắt đầu học</Text>
        <Ionicons name="chevron-forward" size={19} color="white" />
      </AppButton>
      <View style={s.heading}>
        <Text style={s.section}>
          <Text style={{ color: "#A43DFF" }}>★ </Text>GỢI Ý CHO BẠN
        </Text>
        <AppButton onPress={() => navigate("catalog")} style={s.seeAll}>
          <Text style={s.seeText}>Xem tất cả ›</Text>
        </AppButton>
      </View>
      <View style={s.recommendations}>
        {[
          ["analytics.song", "Passenger | Let Her Go", "302"],
          ["analytics.phrase", "Day By Day – one minute phrase lesson", "34k"],
        ].map(([image, title, views], i) => (
          <AppButton
            key={image}
            onPress={() => navigate("catalog")}
            style={s.lesson}
          >
            <View>
              <AssetImage assetId={image} style={s.thumbnail} />
              {!i && <Text style={s.youBadge}>CHO BẠN</Text>}
            </View>
            <Text style={s.lessonTitle} numberOfLines={2}>
              {title}
            </Text>
            <Text style={s.views}>◉ {views}</Text>
          </AppButton>
        ))}
      </View>
      <View style={s.heading}>
        <Text style={s.section}>🏆 TOP HỌC VIÊN TUẦN NÀY</Text>
        <AppButton
          onPress={() =>
            Alert.alert(
              "Top học viên tuần này",
              "1. idk · 65.033 XP\n2. I ♥ P · 48.022 XP\n3. N… · 43.443 XP",
            )
          }
          style={s.seeAll}
        >
          <Text style={s.seeText}>Xem tất cả ›</Text>
        </AppButton>
      </View>
      <View style={s.podium}>
        {[
          {
            rank: 2,
            name: "I ♥ P",
            initial: "P",
            xp: "48022",
            height: 64,
            color: "#D7DCE5",
          },
          {
            rank: 1,
            name: "idk",
            initial: "1st",
            xp: "65033",
            height: 96,
            color: "#FFAE00",
          },
          {
            rank: 3,
            name: "N…",
            initial: "N",
            xp: "43443",
            height: 48,
            color: "#CE8330",
          },
        ].map((p) => (
          <View key={p.rank} style={s.place}>
            {p.rank === 1 && <Text style={s.crown}>👑</Text>}
            <View
              style={[
                s.medal,
                {
                  borderColor: p.rank === 2 ? "#CBD5E1" : "#FF9500",
                  backgroundColor:
                    p.rank === 1
                      ? "#FFF2CB"
                      : p.rank === 2
                        ? "#E2E8F0"
                        : "#FFFAEB",
                },
              ]}
            >
              <Text
                style={{
                  fontSize: 15,
                  color: p.rank === 2 ? "#64748B" : "#C66A0C",
                  fontWeight: "700",
                }}
              >
                {p.initial}
              </Text>
            </View>
            <Text style={s.name}>{p.name}</Text>
            <Text style={s.xp}>⚡ {p.xp}</Text>
            <LinearGradient
              colors={[p.color, p.color]}
              style={[s.step, { height: p.height }]}
            >
              <Text style={s.rank}>#{p.rank}</Text>
            </LinearGradient>
          </View>
        ))}
      </View>
    </ScrollView>
  );
}
const s = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "white" },
  content: { padding: 20, paddingTop: 16, paddingBottom: 20 },
  header: { flexDirection: "row", alignItems: "center" },
  date: {
    fontSize: 10,
    color: "#94A3B8",
    fontWeight: "600",
    letterSpacing: 0.5,
  },
  title: {
    fontSize: 23,
    lineHeight: 30,
    fontWeight: "800",
    color: "#1E293B",
    marginTop: 6,
  },
  upgrade: {
    backgroundColor: "#FF6519",
    borderRadius: 22,
    width: 82,
    paddingVertical: 9,
    alignItems: "center",
    alignSelf: "flex-start",
    marginTop: 13,
  },
  upgradeText: {
    color: "white",
    fontWeight: "700",
    fontSize: 12,
    textAlign: "center",
  },
  guestRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginTop: 12,
  },
  subtitle: { color: "#7D8BA6", fontSize: 12, lineHeight: 20 },
  avatar: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: "#F1F5F9",
    alignItems: "center",
    justifyContent: "center",
  },
  startLabel: {
    fontSize: 10,
    fontWeight: "700",
    color: "#00A5EF",
    marginTop: 24,
    marginBottom: 10,
  },
  login: {
    height: 56,
    borderRadius: 16,
    backgroundColor: "#009DFF",
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 16,
    gap: 12,
    boxShadow: "0 3px 5px #009dff25",
  },
  loginTile: {
    width: 28,
    height: 28,
    borderRadius: 9,
    backgroundColor: "#FFFFFF30",
  },
  loginText: {
    flex: 1,
    color: "white",
    fontSize: 14,
    fontWeight: "700",
    textAlign: "center",
  },
  heading: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginTop: 24,
    marginBottom: 12,
  },
  section: { fontSize: 12, fontWeight: "800", color: "#25334D" },
  seeAll: {
    backgroundColor: "#ECF8FF",
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 14,
  },
  seeText: { fontSize: 10, color: "#00A5EF", fontWeight: "700" },
  recommendations: { flexDirection: "row", gap: 12 },
  lesson: {
    flex: 1,
    backgroundColor: "#F8FAFC",
    padding: 10,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: "#F1F5F9",
  },
  thumbnail: {
    width: "100%",
    aspectRatio: 368 / 220,
    borderRadius: 10,
    overflow: "hidden",
  },
  youBadge: {
    position: "absolute",
    top: 6,
    left: 6,
    backgroundColor: "#8A2BE2",
    color: "white",
    fontSize: 8,
    fontWeight: "700",
    paddingHorizontal: 6,
    paddingVertical: 3,
    borderRadius: 7,
  },
  lessonTitle: {
    fontSize: 10,
    fontWeight: "800",
    lineHeight: 15,
    marginTop: 7,
    color: "#24334A",
  },
  views: { fontSize: 9, color: "#94A3B8", marginTop: 4 },
  podium: {
    borderWidth: 1,
    borderColor: "#E9EEF5",
    borderRadius: 24,
    paddingHorizontal: 16,
    paddingTop: 22,
    paddingBottom: 16,
    flexDirection: "row",
    alignItems: "flex-end",
    gap: 12,
    minHeight: 265,
  },
  place: { flex: 1, alignItems: "center" },
  crown: { fontSize: 22, marginBottom: 6 },
  medal: {
    height: 50,
    width: 50,
    borderRadius: 25,
    borderWidth: 1.5,
    justifyContent: "center",
    alignItems: "center",
  },
  name: { fontSize: 10, fontWeight: "800", marginTop: 6, color: "#1E293B" },
  xp: {
    fontSize: 8,
    fontWeight: "700",
    color: "#009DFF",
    marginTop: 4,
    marginBottom: 8,
  },
  step: {
    width: "100%",
    borderTopLeftRadius: 14,
    borderTopRightRadius: 14,
    alignItems: "center",
    justifyContent: "center",
  },
  rank: { fontSize: 16, color: "white", fontWeight: "800" },
});
