import React from "react";
import { View, ScrollView, StyleSheet } from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import { Ionicons } from "@expo/vector-icons";
import Text from "../../../components/atoms/AppText";
import AppButton from "../../../components/atoms/AppButton";
import AssetImage from "../../../components/atoms/AssetImage";
import useOnboarding from "../hooks/useOnboarding";

const levels = [
  ["new", "🌱", "Tôi mới học tiếng Anh", "Mới bắt đầu", "#84CC16"],
  [
    "words",
    "📚",
    "Tôi biết một vài từ thông dụng",
    "Nền tảng cơ bản",
    "#EC4899",
  ],
  [
    "basic",
    "chatbubble-ellipses-outline",
    "Tôi có thể giao tiếp cơ bản",
    "Giao tiếp hằng ngày",
    "#FFAA00",
  ],
  [
    "many-topics",
    "🎯",
    "Tôi có thể nói về nhiều chủ đề",
    "Tự tin trò chuyện",
    "#AE5CFF",
  ],
  [
    "most-topics",
    "🏆",
    "Tôi có thể nói sâu vào hầu hết các chủ đề",
    "Gần như người bản xứ",
    "#FFB900",
  ],
];
const goals = [
  [5, "☕", "Dễ", "NHẸ NHÀNG", "#64748B"],
  [10, "🧁", "Vừa", "ĐỀU ĐẶN", "#009BFF"],
  [15, "🔥", "Khó", "NGHIÊM TÚC", "#FF7900"],
  [20, "🚀", "Siêu khó", "CƯỜNG ĐỘ CAO", "#E929F2"],
];
export default function OnboardingScreen({ navigate }) {
  const {
    step,
    setStep,
    proficiency,
    setProficiency,
    dailyGoal,
    setDailyGoal,
    handleFinish,
  } = useOnboarding({ navigate });
  return (
    <LinearGradient colors={["#F1F9FF", "#FFFFFF", "#FDF7FF"]} style={s.screen}>
      <View style={s.progress}>
        {[0, 1, 2, 3].map((i) => (
          <View
            key={i}
            style={[
              s.segment,
              { backgroundColor: i < step + 1 ? "#0088FF" : "#E2E8F0" },
            ]}
          />
        ))}
      </View>
      <ScrollView style={{ flex: 1 }} contentContainerStyle={s.content}>
        {step < 3 ? (
          <>
            <View style={s.logoGlow}>
              <AssetImage
                assetId="onboarding.mascot"
                style={s.mascot}
                resizeMode="contain"
              />
            </View>
            <Text style={s.title}>
              {step === 1
                ? "Trình độ tiếng Anh của bạn ở mức nào?"
                : "Mục tiêu học hằng ngày của bạn là gì?"}
            </Text>
            <Text style={s.subtitle}>
              {step === 1
                ? "Chúng tôi sẽ cá nhân hoá lộ trình học cho bạn"
                : "Thói quen nhỏ mỗi ngày sẽ tạo kết quả lớn"}
            </Text>
            <View style={s.options}>
              {step === 1
                ? levels.map(([id, icon, label, description, color], index) => {
                    const selected = proficiency === id;
                    return (
                      <AppButton
                        key={id}
                        style={[
                          s.option,
                          selected && {
                            borderColor: color,
                            backgroundColor: color + "0A",
                          },
                        ]}
                        onPress={() => setProficiency(id)}
                        accessibilityState={{ selected }}
                      >
                        {index < 5 ? (
                          <AssetImage
                            assetId={`onboarding.level-${index + 1}${index === 2 && !selected ? "-default" : index === 3 && selected ? "-selected" : ""}`}
                            style={s.levelIcon}
                            resizeMode="contain"
                          />
                        ) : (
                          <View
                            style={[
                              s.levelIcon,
                              {
                                backgroundColor: color + "13",
                                borderColor: color + "22",
                              },
                            ]}
                          >
                            <Text style={{ fontSize: 20 }}>{icon}</Text>
                          </View>
                        )}
                        <View style={{ flex: 1 }}>
                          <Text style={s.optionTitle}>{label}</Text>
                          <Text style={s.optionDescription}>{description}</Text>
                          {selected && (
                            <View style={s.levelTrack}>
                              <View
                                style={[
                                  s.levelFill,
                                  {
                                    backgroundColor: color,
                                    width:
                                      id === "basic"
                                        ? "65%"
                                        : id === "many-topics"
                                          ? "85%"
                                          : "100%",
                                  },
                                ]}
                              />
                            </View>
                          )}
                        </View>
                        {selected ? (
                          <Ionicons
                            name="checkmark-circle"
                            color={color}
                            size={22}
                          />
                        ) : (
                          <View style={s.radio} />
                        )}
                      </AppButton>
                    );
                  })
                : goals.map(([time, icon, label, description, color]) => {
                    const selected = dailyGoal === time;
                    return (
                      <AppButton
                        key={time}
                        onPress={() => setDailyGoal(time)}
                        accessibilityState={{ selected }}
                        style={[
                          s.option,
                          s.goalOption,
                          selected && {
                            borderColor: time === 5 ? color : "#3478FF",
                            backgroundColor: "#F2F7FF",
                          },
                        ]}
                      >
                        <AssetImage
                          assetId={`onboarding.goal-${time}`}
                          style={{ height: 56, width: 56 }}
                          resizeMode="contain"
                        />
                        <View style={{ flex: 1 }}>
                          <Text style={s.goalTitle}>{time} phút / ngày</Text>
                          <Text style={s.goalLabel}>{label}</Text>
                          <Text
                            style={[
                              s.goalDescription,
                              { color: selected ? color : "#94A3B8" },
                            ]}
                          >
                            {description}
                          </Text>
                        </View>
                        {selected ? (
                          <Ionicons
                            name="checkmark-circle"
                            color={time === 5 ? "#94A3B8" : color}
                            size={22}
                          />
                        ) : (
                          <View style={s.radio} />
                        )}
                      </AppButton>
                    );
                  })}
            </View>
          </>
        ) : (
          <>
            <View style={s.trophy}>
              <Ionicons name="trophy" size={30} color="white" />
            </View>
            <Text style={s.title}>
              Và đây là những thành quả bạn sẽ đạt được!
            </Text>
            <Text style={s.subtitle}>
              Hành trình chinh phục tiếng Anh của bạn bắt đầu từ đây
            </Text>
            <View style={s.timeline}>
              {[
                ["star", "Giao tiếp tự tin", "Luyện nghe nói không áp lực"],
                [
                  "book",
                  "Từ vựng đa dạng",
                  "Các từ và cụm từ phổ biến, thiết thực trong cuộc sống",
                ],
                [
                  "calendar",
                  "Tạo thói quen học tập",
                  "Lời nhắc thông minh, thử thách thú vị",
                ],
              ].map(([image, title, description], i) => (
                <View style={s.timelineRow} key={image}>
                  <View style={s.numberColumn}>
                    <View style={s.number}>
                      <Text style={s.numberText}>{i + 1}</Text>
                    </View>
                    {i < 2 && <View style={s.line} />}
                  </View>
                  <View style={s.achievement}>
                    <View style={s.achievementHeader}>
                      <View style={s.achievementIcon}>
                        <AssetImage
                          assetId={`onboarding.${image}`}
                          style={{ width: 20, height: 20 }}
                          resizeMode="contain"
                        />
                      </View>
                      <Text style={s.achievementTitle}>{title}</Text>
                    </View>
                    <Text style={s.achievementDescription}>{description}</Text>
                  </View>
                </View>
              ))}
            </View>
          </>
        )}
      </ScrollView>
      <View style={s.footer}>
        <AppButton
          onPress={step < 3 ? () => setStep(step + 1) : handleFinish}
          accessibilityLabel={step < 3 ? "Tiếp tục" : "Bắt đầu thôi"}
        >
          <LinearGradient colors={["#199BFF", "#0084FF"]} style={s.continue}>
            <Text style={s.continueText}>
              {step < 3 ? "Tiếp tục" : "Bắt đầu thôi"}
            </Text>
            {step < 3 && (
              <Ionicons name="arrow-forward" size={18} color="white" />
            )}
          </LinearGradient>
        </AppButton>
      </View>
    </LinearGradient>
  );
}
const s = StyleSheet.create({
  screen: { flex: 1, paddingTop: 12 },
  progress: { flexDirection: "row", gap: 6, marginHorizontal: 20 },
  segment: { flex: 1, height: 4, borderRadius: 3 },
  content: { paddingHorizontal: 20, paddingBottom: 24, alignItems: "center" },
  logoGlow: {
    marginTop: 24,
    marginBottom: 12,
    width: 116,
    height: 112,
    alignItems: "center",
    justifyContent: "center",
  },
  mascot: { width: 92, height: 102 },
  title: {
    fontSize: 22,
    lineHeight: 29,
    fontWeight: "800",
    textAlign: "center",
    color: "#0F172A",
    maxWidth: 325,
  },
  subtitle: {
    fontSize: 12,
    lineHeight: 20,
    color: "#7D8BA6",
    textAlign: "center",
    marginTop: 10,
    maxWidth: 320,
  },
  options: { width: "100%", gap: 12, marginTop: 34 },
  option: {
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
    padding: 16,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: "#EEF2F6",
    backgroundColor: "white",
    minHeight: 76,
    boxShadow: "0 3px 8px #0f172a04",
  },
  levelIcon: {
    height: 46,
    width: 46,
    borderRadius: 23,
    borderWidth: 1,
    alignItems: "center",
    justifyContent: "center",
  },
  optionTitle: {
    fontSize: 12,
    fontWeight: "800",
    lineHeight: 18,
    color: "#1E293B",
  },
  optionDescription: { fontSize: 10, color: "#A1AEC3", marginTop: 3 },
  radio: {
    width: 20,
    height: 20,
    borderRadius: 10,
    borderWidth: 1.5,
    borderColor: "#CBD5E1",
  },
  levelTrack: {
    height: 4,
    backgroundColor: "#E2E8F0",
    borderRadius: 3,
    marginTop: 9,
  },
  levelFill: { height: 4, borderRadius: 3 },
  goalOption: { minHeight: 86, paddingVertical: 12 },
  goalIcon: {
    height: 48,
    width: 48,
    borderRadius: 24,
    borderWidth: 2,
    alignItems: "center",
    justifyContent: "center",
  },
  minutes: { fontSize: 9, fontWeight: "700", marginTop: 1 },
  goalTitle: { fontSize: 14, fontWeight: "700", color: "#1E293B" },
  goalLabel: { fontSize: 11, color: "#64748B", marginTop: 3 },
  goalDescription: { fontSize: 8, fontWeight: "700", marginTop: 3 },
  footer: { padding: 20, paddingTop: 10, paddingBottom: 18 },
  continue: {
    height: 54,
    borderRadius: 27,
    alignItems: "center",
    justifyContent: "center",
    flexDirection: "row",
    gap: 8,
    boxShadow: "0 7px 14px #0084ff33",
  },
  continueText: { fontSize: 14, fontWeight: "700", color: "white" },
  trophy: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: "#0088FF",
    borderWidth: 14,
    borderColor: "#E0F2FE",
    alignItems: "center",
    justifyContent: "center",
    marginTop: 40,
    marginBottom: 28,
  },
  timeline: { width: "100%", marginTop: 44, gap: 14 },
  timelineRow: { flexDirection: "row", gap: 14 },
  numberColumn: { width: 32, alignItems: "center" },
  number: {
    height: 32,
    width: 32,
    borderRadius: 16,
    backgroundColor: "#0EA5E9",
    alignItems: "center",
    justifyContent: "center",
    marginTop: 15,
  },
  numberText: { fontSize: 14, fontWeight: "700", color: "white" },
  line: {
    width: 2,
    flex: 1,
    backgroundColor: "#BFDBFE",
    marginTop: 5,
    marginBottom: -14,
  },
  achievement: {
    flex: 1,
    backgroundColor: "white",
    borderRadius: 22,
    borderWidth: 1,
    borderColor: "#EEF2F6",
    padding: 16,
    boxShadow: "0 3px 8px #0f172a04",
  },
  achievementHeader: { flexDirection: "row", alignItems: "center", gap: 12 },
  achievementIcon: {
    height: 40,
    width: 40,
    borderRadius: 12,
    backgroundColor: "#E0F2FE",
    alignItems: "center",
    justifyContent: "center",
  },
  achievementTitle: {
    fontSize: 12,
    fontWeight: "800",
    flex: 1,
    color: "#1E293B",
  },
  achievementDescription: {
    fontSize: 11,
    lineHeight: 18,
    color: "#7D8BA6",
    marginTop: 10,
  },
});
