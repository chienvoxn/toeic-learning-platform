import React from "react";
import {
  Platform,
  SafeAreaView,
  StatusBar as NativeStatusBar,
  StyleSheet,
  View,
} from "react-native";
import { StatusBar } from "expo-status-bar";
import { colors, layout } from "../../theme/tokens";

export default function LearnerTemplate({
  children,
  navigation,
  dark = false,
}) {
  return (
    <SafeAreaView
      style={[styles.safeArea, dark && { backgroundColor: "#061F1E" }]}
    >
      <StatusBar style={dark ? "light" : "dark"} />
      <View style={styles.container}>
        <View style={styles.content}>{children}</View>
        {navigation}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: colors.surface,
    paddingTop: Platform.OS === "android" ? NativeStatusBar.currentHeight : 0,
  },
  container: {
    flex: 1,
    backgroundColor: colors.background,
    width: "100%",
    maxWidth: layout.maxContentWidth,
    alignSelf: "center",
  },
  content: { flex: 1 },
});
