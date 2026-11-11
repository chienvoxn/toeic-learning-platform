import React from "react";
import { View, StyleSheet, ScrollView, Alert } from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import Text from "../../../components/atoms/AppText";
import AppButton from "../../../components/atoms/AppButton";
import AssetImage from "../../../components/atoms/AssetImage";
import useGamesCatalog from "../hooks/useGamesCatalog";

export default function GamesScreen({ navigate }) {
  const { games } = useGamesCatalog();
  return (
    <ScrollView style={s.screen} contentContainerStyle={s.grid}>
      {games.map((game) => (
        <View key={game.id} style={s.card}>
          <LinearGradient
            colors={game.gradient}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 1 }}
            style={s.art}
          >
            <AssetImage
              assetId={game.imageId}
              style={s.image}
              resizeMode="contain"
            />
            {game.isNew && <Text style={s.badge}>MỚI</Text>}
          </LinearGradient>
          <View style={s.body}>
            <Text style={s.title} numberOfLines={1}>
              {game.title}
            </Text>
            <Text style={s.description} numberOfLines={2}>
              {game.desc}
            </Text>
            <AppButton
              onPress={() =>
                game.active
                  ? navigate("wordMatch")
                  : Alert.alert(
                      game.title,
                      "Trò chơi này sẽ sớm ra mắt. Bạn có thể chơi Nối Từ ngay bây giờ.",
                    )
              }
              style={[s.play, { backgroundColor: game.button }]}
            >
              <Text style={s.playText}>Chơi ngay →</Text>
            </AppButton>
          </View>
        </View>
      ))}
    </ScrollView>
  );
}
const s = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#F6F8FC" },
  grid: { flexDirection: "row", flexWrap: "wrap", padding: 12, gap: 12 },
  card: {
    width: "48%",
    flexGrow: 1,
    maxWidth: "49%",
    borderRadius: 20,
    overflow: "hidden",
    backgroundColor: "white",
    borderWidth: 1,
    borderColor: "#E2E8F0",
  },
  art: { height: 112, justifyContent: "center", alignItems: "center" },
  image: { width: 32, height: 32 },
  badge: {
    position: "absolute",
    right: 8,
    top: 8,
    color: "white",
    backgroundColor: "#00CEC1",
    fontSize: 8,
    fontWeight: "800",
    borderRadius: 10,
    paddingVertical: 3,
    paddingHorizontal: 7,
  },
  body: { padding: 12 },
  title: { fontSize: 13, fontWeight: "800", color: "#1E293B" },
  description: {
    fontSize: 11,
    lineHeight: 16,
    color: "#94A3B8",
    height: 34,
    marginTop: 6,
  },
  play: {
    height: 32,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: 17,
    marginTop: 8,
  },
  playText: { fontSize: 11, fontWeight: "700", color: "white" },
});
