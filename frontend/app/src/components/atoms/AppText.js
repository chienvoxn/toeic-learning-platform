import React from "react";
import { StyleSheet, Text } from "react-native";

export default function AppText({ style, ...props }) {
  const { fontWeight = "400", fontFamily } = StyleSheet.flatten(style) || {};
  const weight = Math.min(
    800,
    Math.max(400, Number(fontWeight) || (fontWeight === "bold" ? 700 : 400)),
  );
  return (
    <Text
      {...props}
      style={[
        { fontFamily: `Jakarta${Math.round(weight / 100) * 100}` },
        style,
        !fontFamily && {
          fontFamily: `Jakarta${Math.round(weight / 100) * 100}`,
          fontWeight: "normal",
        },
      ]}
    />
  );
}
