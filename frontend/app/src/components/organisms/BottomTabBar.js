import React from "react";
import { StyleSheet, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import Text from "../atoms/AppText";
import AssetImage from "../atoms/AssetImage";
import AppButton from "../atoms/AppButton";
import { colors } from "../../theme/tokens";

export default function BottomTabBar({ tabs, currentScreen, onSelect }) {
  return (
    <View style={styles.bar} accessibilityLabel="Điều hướng chính">
      {tabs.map((tab) => {
        const selected = currentScreen === tab.id;
        const color = selected ? colors.primary : "#98A2B3";
        return (
          <AppButton
            key={tab.id}
            style={styles.item}
            onPress={() => onSelect(tab.id)}
            accessibilityLabel={tab.label}
            accessibilityState={{ selected }}
          >
            {tab.id === "vocab" ? (
              <Ionicons name="language-outline" size={20} color={color} />
            ) : (
              <AssetImage
                assetId={`nav.${tab.id}`}
                style={styles.icon}
                imageStyle={{ tintColor: color }}
                resizeMode="contain"
              />
            )}
            <Text
              style={[
                styles.label,
                { color, fontWeight: selected ? "700" : "500" },
              ]}
            >
              {tab.label}
            </Text>
          </AppButton>
        );
      })}
    </View>
  );
}
const styles = StyleSheet.create({
  bar: {
    flexDirection: "row",
    height: 62,
    backgroundColor: colors.surface,
    borderTopWidth: 1,
    borderTopColor: "#EEF1F6",
  },
  item: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    gap: 4,
    minHeight: 44,
  },
  icon: { width: 20, height: 20 },
  label: { fontSize: 9 },
});
