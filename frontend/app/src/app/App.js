import React from "react";
import AppNavigator from "../navigation/AppNavigator";
import { useFonts } from "expo-font";
import { ActivityIndicator, View } from "react-native";

export default function App() {
  const [loaded, error] = useFonts({
    Jakarta400: require("../../assets/fonts/plus-jakarta-sans-400.ttf"),
    Jakarta500: require("../../assets/fonts/plus-jakarta-sans-500.ttf"),
    Jakarta600: require("../../assets/fonts/plus-jakarta-sans-600.ttf"),
    Jakarta700: require("../../assets/fonts/plus-jakarta-sans-700.ttf"),
    Jakarta800: require("../../assets/fonts/plus-jakarta-sans-800.ttf"),
  });
  if (!loaded && !error)
    return (
      <View style={{ flex: 1, justifyContent: "center" }}>
        <ActivityIndicator />
      </View>
    );
  return <AppNavigator />;
}
