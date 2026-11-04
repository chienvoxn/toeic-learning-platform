import React from "react";
import { View, StyleSheet, ScrollView, Alert, Share } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import Text from "../../../components/atoms/AppText";
import AppButton from "../../../components/atoms/AppButton";
import AssetImage from "../../../components/atoms/AssetImage";
import { colors } from "../../../theme/tokens";

export default function SettingsScreen({ navigate }) {
  const info = (title) =>
    Alert.alert(title, "Nội dung này sẽ có trong phiên bản tiếp theo.");
  const groups = [
    [
      ["note", "Ghi chú của tôi", () => navigate("vocab")],
      ["review", "Ôn tập", () => navigate("flashcardReview")],
      ["progress", "Tiến độ bài học", () => navigate("home")],
      ["trophy", "Bảng xếp hạng", () => info("Bảng xếp hạng")],
    ],
    [
      ["star", "Đánh giá Parroto", () => info("Đánh giá Parroto")],
      [
        "link",
        "Chia sẻ với bạn bè",
        () =>
          Share.share({ message: "Cùng học tiếng Anh mỗi ngày với Parroto!" }),
      ],
    ],
    [
      ["terms", "Điều khoản sử dụng", () => info("Điều khoản sử dụng")],
      ["privacy", "Chính sách bảo mật", () => info("Chính sách bảo mật")],
      ["mail", "Gửi phản hồi", () => info("Gửi phản hồi")],
    ],
  ];
  const renderGroup = (rows, key) => (
    <View style={s.group} key={key}>
      {rows.map(([icon, title, onPress], i) => (
        <AppButton
          key={icon}
          style={[s.row, i > 0 && s.border]}
          onPress={onPress}
        >
          <AssetImage
            assetId={`profile.${icon}`}
            style={s.icon}
            resizeMode="contain"
          />
          <Text style={s.rowTitle}>{title}</Text>
          <Ionicons name="chevron-forward" size={17} color="#BDCADB" />
        </AppButton>
      ))}
    </View>
  );
  return (
    <ScrollView style={s.screen} contentContainerStyle={s.content}>
      <Text style={s.title}>Cài đặt</Text>
      <AppButton style={s.profile} onPress={() => navigate("login")}>
        <View style={s.avatarBorder}>
          <AssetImage assetId="profile.avatar" style={s.avatar} />
        </View>
        <View style={{ flex: 1 }}>
          <Text style={s.name}>Lê Ngọc Phước</Text>
          <View style={s.loginLine}>
            <AssetImage
              assetId="auth.google-logo"
              style={s.google}
              resizeMode="contain"
            />
            <Text style={s.loginText}>Đăng nhập với Google</Text>
          </View>
        </View>
        <Ionicons name="chevron-forward" color="#BDCADB" size={20} />
      </AppButton>
      {renderGroup(groups[0], "learning")}
      <Text style={s.label}>BỘ MÁY NHẬN DIỆN GIỌNG NÓI</Text>
      <AppButton style={s.engine} onPress={() => info("Parroto AI")}>
        <AssetImage
          assetId="profile.brain"
          style={s.icon}
          resizeMode="contain"
        />
        <View style={{ flex: 1 }}>
          <Text style={s.engineTitle}>Parroto AI</Text>
          <Text style={s.engineDescription}>
            Độ chính xác cao hơn, tốt với giọng vùng miền. Dễ đóng gói sẵn trong
            app.
          </Text>
        </View>
        <Ionicons name="checkmark-circle" color="#168CFF" size={20} />
      </AppButton>
      <Text style={s.engineNote}>Chọn bộ máy chấm điểm bài luyện nói.</Text>
      {renderGroup(groups[1], "community")}
      {renderGroup(groups[2], "policies")}
      <AppButton style={s.onboarding} onPress={() => navigate("onboarding")}>
        <Text style={s.onboardingText}>Xem lại hướng dẫn học</Text>
      </AppButton>
    </ScrollView>
  );
}
const s = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  content: { padding: 16, paddingTop: 16, paddingBottom: 16 },
  title: {
    fontSize: 16,
    fontWeight: "700",
    textAlign: "center",
    marginBottom: 16,
  },
  profile: {
    backgroundColor: "white",
    borderRadius: 24,
    padding: 16,
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    marginBottom: 16,
  },
  avatarBorder: {
    width: 56,
    height: 56,
    padding: 2,
    borderWidth: 2,
    borderColor: "#00A5EF",
    borderRadius: 28,
  },
  avatar: {
    width: "100%",
    height: "100%",
    borderRadius: 25,
    overflow: "hidden",
  },
  name: { fontSize: 16, fontWeight: "800", color: "#21252C" },
  loginLine: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    marginTop: 5,
  },
  google: { width: 14, height: 14 },
  loginText: { fontSize: 11, color: "#7E8594" },
  group: {
    backgroundColor: "white",
    borderRadius: 20,
    overflow: "hidden",
    marginBottom: 16,
  },
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
    height: 53,
    paddingHorizontal: 16,
  },
  border: { borderTopWidth: 1, borderTopColor: "#F6F8FC" },
  icon: { width: 22, height: 22 },
  rowTitle: { flex: 1, fontSize: 13, color: "#21252C" },
  label: { fontSize: 10, color: "#7E8594", marginBottom: 10, marginLeft: 4 },
  engine: {
    backgroundColor: "white",
    borderRadius: 20,
    padding: 16,
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 12,
  },
  engineTitle: { fontSize: 12, fontWeight: "700", color: "#21252C" },
  engineDescription: {
    fontSize: 11,
    lineHeight: 18,
    color: "#7E8594",
    marginTop: 4,
  },
  engineNote: {
    fontSize: 10,
    color: "#7E8594",
    marginLeft: 4,
    marginTop: 8,
    marginBottom: 14,
  },
  onboarding: { alignItems: "center", padding: 12 },
  onboardingText: { fontSize: 11, color: "#7E8594" },
});
