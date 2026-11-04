import React from "react";
import {
  View,
  StyleSheet,
  ScrollView,
  TextInput,
  KeyboardAvoidingView,
  Platform,
  Alert,
} from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import { Ionicons } from "@expo/vector-icons";
import Text from "../../../components/atoms/AppText";
import AssetImage from "../../../components/atoms/AssetImage";
import AppButton from "../../../components/atoms/AppButton";
import useDemoLogin from "../hooks/useDemoLogin";
import { colors } from "../../../theme/tokens";

export default function LoginScreen({ navigate }) {
  const {
    emailMode,
    setEmailMode,
    email,
    setEmail,
    password,
    setPassword,
    handleLogin,
    handleGoogleLogin,
  } = useDemoLogin({ navigate });
  return (
    <KeyboardAvoidingView
      style={s.screen}
      behavior={Platform.OS === "ios" ? "padding" : undefined}
    >
      <ScrollView
        contentContainerStyle={s.content}
        keyboardShouldPersistTaps="handled"
      >
        <LinearGradient
          colors={["#81BBF9", "#A9CDFF", "#DEBDF9"]}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 1 }}
          style={s.hero}
        >
          <View style={s.glow} />
          <AppButton
            onPress={() => navigate("home")}
            style={s.close}
            accessibilityLabel="Đóng đăng nhập"
          >
            <Ionicons name="close" size={18} color="#11141A" />
          </AppButton>
          <AssetImage
            assetId="auth.mascot"
            style={s.mascot}
            resizeMode="contain"
            accessibilityLabel="Linh vật Parroto đeo tai nghe"
          />
        </LinearGradient>
        <View style={s.body}>
          <Text style={s.title}>Chào mừng trở lại</Text>
          <Text style={s.subtitle}>
            Đăng nhập để đồng bộ tiến độ giữa các{"\n"}thiết bị.
          </Text>
          {!emailMode ? (
            <AppButton onPress={handleGoogleLogin} style={s.google}>
              <AssetImage
                assetId="auth.google-logo"
                style={s.googleIcon}
                resizeMode="contain"
              />
              <Text style={s.googleText}>Tiếp tục với Google</Text>
            </AppButton>
          ) : (
            <View style={s.form}>
              <TextInput
                value={email}
                onChangeText={setEmail}
                autoCapitalize="none"
                keyboardType="email-address"
                placeholder="Email"
                accessibilityLabel="Email"
                style={s.input}
              />
              <TextInput
                value={password}
                onChangeText={setPassword}
                secureTextEntry
                placeholder="Mật khẩu"
                accessibilityLabel="Mật khẩu"
                style={s.input}
              />
              <AppButton style={s.google} onPress={handleLogin}>
                <Text style={s.googleText}>Đăng nhập</Text>
              </AppButton>
            </View>
          )}
          <Text style={s.terms}>
            Tiếp tục đồng nghĩa với bạn đã đồng ý{" "}
            <Text
              style={s.link}
              onPress={() =>
                Alert.alert(
                  "Điều khoản",
                  "Điều khoản sử dụng sẽ được cung cấp khi phát hành ứng dụng.",
                )
              }
            >
              Điều khoản
            </Text>{" "}
            và{" "}
            <Text
              style={s.link}
              onPress={() =>
                Alert.alert(
                  "Chính sách bảo mật",
                  "Chính sách bảo mật sẽ được cung cấp khi phát hành ứng dụng.",
                )
              }
            >
              Chính sách bảo mật.
            </Text>
          </Text>
          {/* Giữ lối vào email sẵn có cho bàn phím/accessibility, ngoài vùng thiết kế Google. */}
          <AppButton
            style={s.emailAccess}
            onPress={() => setEmailMode(!emailMode)}
            accessibilityLabel={
              emailMode ? "Quay lại Google" : "Đăng nhập bằng Email"
            }
          >
            <Text style={s.emailText}>
              {emailMode ? "Quay lại Google" : "Đăng nhập bằng Email"}
            </Text>
          </AppButton>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
const s = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "white" },
  content: { flexGrow: 1 },
  hero: {
    height: 246,
    borderBottomLeftRadius: 36,
    borderBottomRightRadius: 36,
    alignItems: "center",
    justifyContent: "flex-end",
    paddingBottom: 20,
    overflow: "hidden",
  },
  close: {
    position: "absolute",
    left: 18,
    top: 4,
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: "white",
    alignItems: "center",
    justifyContent: "center",
  },
  glow: {
    position: "absolute",
    right: 26,
    top: 14,
    width: 76,
    height: 76,
    borderRadius: 38,
    backgroundColor: "#C99386",
    opacity: 0.4,
    filter: "blur(7px)",
  },
  mascot: { width: 115, height: 135 },
  body: { paddingHorizontal: 25, paddingTop: 34, alignItems: "center" },
  title: { fontSize: 23, fontWeight: "800", color: colors.foreground },
  subtitle: {
    fontSize: 13,
    color: "#64748B",
    lineHeight: 21,
    textAlign: "center",
    marginTop: 10,
  },
  google: {
    height: 50,
    borderRadius: 25,
    borderWidth: 1,
    borderColor: "#E2E8F0",
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 12,
    width: "100%",
    marginTop: 34,
    backgroundColor: "white",
    boxShadow: "0 2px 5px #0f172a08",
  },
  googleIcon: { width: 24, height: 24 },
  googleText: { fontSize: 14, fontWeight: "700", color: "#0F172A" },
  terms: {
    fontSize: 12,
    color: "#64748B",
    textAlign: "center",
    lineHeight: 21,
    marginTop: 24,
    maxWidth: 290,
  },
  link: { color: colors.primary, fontWeight: "700" },
  emailAccess: { marginTop: 320, padding: 12 },
  emailText: { fontSize: 11, color: "#64748B" },
  form: { width: "100%", marginTop: 18 },
  input: {
    padding: 14,
    borderWidth: 1,
    borderColor: "#E2E8F0",
    borderRadius: 12,
    marginTop: 8,
  },
});
