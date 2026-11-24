import { useState } from "react";
import { Alert } from "react-native";
import { authApi } from "../../../services/auth.api";

export default function useDemoLogin({ navigate }) {
  const [emailMode, setEmailMode] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);

  const handleLogin = async () => {
    if (!email || !password) {
      Alert.alert("Thông báo", "Vui lòng nhập đầy đủ email và mật khẩu.");
      return;
    }
    setLoading(true);
    try {
      await authApi.login(email, password);
      navigate("home");
    } catch (error) {
      Alert.alert("Đăng nhập thất bại", error.message || "Kiểm tra lại tài khoản.");
    } finally {
      setLoading(false);
    }
  };

  const handleGoogleLogin = async () => {
    // Giả lập hoặc liên kết Google OAuth
    await authApi.login("google.student@toeic.edu.vn", "oauth-google-pass");
    navigate("home");
  };

  return {
    emailMode,
    setEmailMode,
    email,
    setEmail,
    password,
    setPassword,
    loading,
    handleLogin,
    handleGoogleLogin,
  };
}
