import React from "react";
import { View, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import Text from "../../../components/atoms/AppText";
import AppButton from "../../../components/atoms/AppButton";
import useWordMatch from "../hooks/useWordMatch";

export default function WordMatchScreen({ navigate }) {
  const {
    gameState,
    setGameState,
    cards,
    selectedCards,
    clearedPairIds,
    mismatchedIds,
    score,
    timeLeft,
    initGame,
    handleCardClick,
    isTimeRunningOut,
    paused,
    setPaused,
    sound,
    setSound,
  } = useWordMatch();
  const ready = gameState === "ready",
    ended = gameState === "time-up" || gameState === "completed";
  return (
    <View style={s.screen}>
      <View style={s.toolbar}>
        <AppButton
          onPress={() => navigate("games")}
          style={s.back}
          accessibilityLabel="Về danh mục game"
        >
          <Ionicons name="chevron-back" size={22} color="#9CB8B7" />
        </AppButton>
        <Ionicons
          name="time-outline"
          size={17}
          color={isTimeRunningOut ? "#FF4265" : "#21CCB7"}
        />
        <View style={s.track}>
          <View
            style={[
              s.fill,
              {
                width: `${(timeLeft / 45) * 100}%`,
                backgroundColor: isTimeRunningOut ? "#FF4265" : "#21CCB7",
              },
            ]}
          />
        </View>
        <Text style={[s.time, isTimeRunningOut && { color: "#FF4265" }]}>
          {timeLeft}s
        </Text>
        <View style={s.score}>
          <Ionicons name="trophy" size={12} color="#D7E8E6" />
          <Text style={s.scoreText}>{score}</Text>
        </View>
        <AppButton
          style={s.round}
          onPress={() => setSound(!sound)}
          accessibilityLabel={sound ? "Tắt âm thanh" : "Bật âm thanh"}
        >
          <Ionicons
            name={sound ? "volume-high" : "volume-mute"}
            size={17}
            color="#A5C6C3"
          />
        </AppButton>
        <AppButton
          style={s.round}
          onPress={() => setPaused(!paused)}
          accessibilityLabel={paused ? "Tiếp tục chơi" : "Tạm dừng"}
        >
          <Ionicons
            name={paused ? "play" : "pause"}
            size={17}
            color="#A5C6C3"
          />
        </AppButton>
      </View>
      <View style={s.grid}>
        {cards.map((card) => {
          const cleared = clearedPairIds.has(card.pairId);
          const selected = selectedCards.some((c) => c.cardId === card.cardId);
          return (
            <AppButton
              key={card.cardId}
              disabled={cleared || gameState !== "playing" || paused}
              onPress={() => handleCardClick(card)}
              style={[
                s.card,
                selected && s.selected,
                mismatchedIds.includes(card.cardId) && s.mismatch,
                cleared && { opacity: 0 },
              ]}
              accessibilityLabel={cleared ? "Đã ghép" : card.text}
            >
              <Text style={s.cardText}>{card.text}</Text>
            </AppButton>
          );
        })}
      </View>
      {(ready || ended || paused) && (
        <View style={s.overlay}>
          <View style={s.dialog}>
            <Ionicons
              name={
                ready
                  ? "extension-puzzle"
                  : paused
                    ? "pause-circle"
                    : gameState === "completed"
                      ? "checkmark-circle"
                      : "time"
              }
              color="#14B8A6"
              size={ready ? 46 : 56}
            />
            <Text style={s.title}>
              {ready
                ? "Sẵn sàng nối từ?"
                : paused
                  ? "Đã tạm dừng"
                  : gameState === "completed"
                    ? "Hoàn thành!"
                    : "Hết giờ!"}
            </Text>
            {ready ? (
              <Text style={s.description}>
                Chạm một từ rồi chạm nghĩa của nó – xoá sạch bảng trước khi hết
                giờ
              </Text>
            ) : ended ? (
              <View style={s.results}>
                <View>
                  <Text style={s.result}>{score}</Text>
                  <Text style={s.resultLabel}>ĐIỂM</Text>
                </View>
                <View>
                  <Text style={s.result}>{clearedPairIds.size}</Text>
                  <Text style={s.resultLabel}>CẶP</Text>
                </View>
              </View>
            ) : null}
            <AppButton
              style={s.primary}
              accessibilityLabel={
                ready ? "Bắt đầu" : paused ? "Tiếp tục" : "Chơi lại"
              }
              onPress={
                ready
                  ? () => setGameState("playing")
                  : paused
                    ? () => setPaused(false)
                    : initGame
              }
            >
              <Ionicons
                name={ready ? "play" : paused ? "play" : "refresh"}
                size={18}
                color="white"
              />
              <Text style={s.primaryText}>
                {ready ? "Bắt đầu" : paused ? "Tiếp tục" : "Chơi lại"}
              </Text>
            </AppButton>
            {ended && (
              <AppButton onPress={() => navigate("games")} style={s.exit}>
                <Text style={s.exitText}>Thoát</Text>
              </AppButton>
            )}
          </View>
        </View>
      )}
    </View>
  );
}
const s = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#061F1E", padding: 14 },
  toolbar: {
    flexDirection: "row",
    alignItems: "center",
    gap: 9,
    height: 42,
    marginBottom: 12,
  },
  back: { width: 20, alignItems: "center", justifyContent: "center" },
  track: { height: 7, flex: 1, backgroundColor: "#173C38", borderRadius: 4 },
  fill: { height: 7, borderRadius: 4 },
  time: { fontSize: 12, fontWeight: "700", color: "#95B8B5", minWidth: 26 },
  score: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    borderRadius: 14,
    paddingHorizontal: 12,
    paddingVertical: 5,
    backgroundColor: "#113431",
  },
  scoreText: { color: "#D7E8E6", fontSize: 12, fontWeight: "700" },
  round: {
    height: 34,
    width: 34,
    borderRadius: 17,
    backgroundColor: "#113431",
    alignItems: "center",
    justifyContent: "center",
  },
  grid: { flex: 1, flexDirection: "row", flexWrap: "wrap", gap: 12 },
  card: {
    width: "48%",
    flexGrow: 1,
    height: "15.3%",
    borderRadius: 20,
    borderWidth: 1.5,
    borderColor: "#153C38",
    backgroundColor: "#103A36",
    alignItems: "center",
    justifyContent: "center",
    padding: 12,
  },
  cardText: {
    color: "#CAE3E0",
    fontSize: 13,
    fontWeight: "700",
    textAlign: "center",
    lineHeight: 20,
  },
  selected: {
    borderColor: "#14DEC6",
    backgroundColor: "#0C4540",
    boxShadow: "0 0 0 3px #14dec610",
  },
  mismatch: { borderColor: "#FB7185", backgroundColor: "#532B34" },
  overlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: "#020E13F0",
    justifyContent: "center",
    alignItems: "center",
    padding: 20,
  },
  dialog: { width: "100%", alignItems: "center" },
  title: {
    fontSize: 23,
    fontWeight: "800",
    color: "#F8FAFC",
    marginTop: 18,
    textAlign: "center",
  },
  description: {
    fontSize: 14,
    lineHeight: 23,
    color: "#96A7AA",
    textAlign: "center",
    maxWidth: 330,
    marginTop: 14,
    marginBottom: 32,
  },
  primary: {
    height: 54,
    borderRadius: 28,
    backgroundColor: "#13BCA8",
    alignItems: "center",
    justifyContent: "center",
    flexDirection: "row",
    gap: 10,
    width: "100%",
    boxShadow: "0 8px 20px #14b8a633",
  },
  primaryText: { fontSize: 15, fontWeight: "800", color: "white" },
  results: { flexDirection: "row", gap: 54, marginVertical: 26 },
  result: {
    fontSize: 28,
    fontWeight: "800",
    color: "#14B8A6",
    textAlign: "center",
  },
  resultLabel: {
    fontSize: 10,
    fontWeight: "700",
    color: "#839BA0",
    textAlign: "center",
    marginTop: 4,
  },
  exit: { padding: 20 },
  exitText: { fontSize: 14, fontWeight: "700", color: "#00A5EF" },
});
