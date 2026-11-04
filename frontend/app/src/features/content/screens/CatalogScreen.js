import React, { useState } from "react";
import { View, StyleSheet, ScrollView, TextInput, Alert } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import Text from "../../../components/atoms/AppText";
import AppButton from "../../../components/atoms/AppButton";
import useContentCatalog from "../hooks/useContentCatalog";
import { colors } from "../../../theme/tokens";

export default function CatalogScreen() {
  const {
    selectedCat,
    setSelectedCat,
    search,
    setSearch,
    categories,
    filtered,
    sections,
  } = useContentCatalog();
  const [filters, setFilters] = useState(false);
  return (
    <View style={s.screen}>
      <View style={s.toolbar}>
        <View style={{ flex: 1 }} />
        <AppButton
          style={s.filter}
          onPress={() => setFilters(!filters)}
          accessibilityLabel="Lọc bài học"
        >
          <Ionicons name="options" size={16} color="#7E8594" />
        </AppButton>
        <AppButton
          style={s.upgrade}
          onPress={() =>
            Alert.alert(
              "Parroto Pro",
              "Tính năng nâng cấp chưa có trong bản demo.",
            )
          }
        >
          <Ionicons name="diamond" size={14} color="white" />
          <Text style={s.upgradeText}>Nâng cấp</Text>
        </AppButton>
      </View>
      {filters && (
        <View style={s.filters}>
          <TextInput
            placeholder="Tìm bài học…"
            accessibilityLabel="Tìm bài học"
            style={s.search}
            value={search}
            onChangeText={setSearch}
          />
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={{ gap: 6 }}
          >
            {categories.map((cat) => (
              <AppButton
                key={cat}
                style={[
                  s.chip,
                  selectedCat === cat && { backgroundColor: colors.primary },
                ]}
                onPress={() => setSelectedCat(cat)}
              >
                <Text
                  style={{
                    fontSize: 11,
                    color: selectedCat === cat ? "white" : "#64748B",
                  }}
                >
                  {cat}
                </Text>
              </AppButton>
            ))}
          </ScrollView>
        </View>
      )}
      <ScrollView contentContainerStyle={s.content}>
        {sections.map((section) => {
          const items = filtered.filter((i) => i.section === section.id);
          return (
            items.length > 0 && (
              <View style={s.section} key={section.id}>
                <View style={s.heading}>
                  <View>
                    <Text style={s.title}>{section.title}</Text>
                    <Text style={s.count}>{section.total} bài học</Text>
                  </View>
                  <AppButton
                    onPress={() => {
                      setSelectedCat(section.cat);
                      setFilters(true);
                    }}
                    style={s.see}
                  >
                    <Text style={s.seeText}>Xem tất cả ›</Text>
                  </AppButton>
                </View>
                <View style={s.grid}>
                  {items.map((item) => (
                    <View key={item.id} style={s.card}>
                      <AppButton
                        onPress={() =>
                          Alert.alert(
                            item.title,
                            "Bài học này chưa có nội dung phát. Hãy chọn bài học khác.",
                          )
                        }
                        accessibilityLabel={`Mở ${item.title}`}
                      >
                        <View
                          style={[
                            s.thumbnail,
                            { backgroundColor: item.tagColor },
                          ]}
                        >
                          <Text
                            style={[
                              s.level,
                              {
                                backgroundColor: item.diff.startsWith("A")
                                  ? "#D9FCE2"
                                  : "#DBEEFF",
                                color: item.diff.startsWith("A")
                                  ? "#16A064"
                                  : "#376DB3",
                              },
                            ]}
                          >
                            {item.diff}
                          </Text>
                          {item.pro && <Text style={s.pro}>♛ PRO</Text>}
                        </View>
                        <View style={s.body}>
                          <Text style={s.duration}>▪◂ {item.duration}</Text>
                          <Text style={s.cardTitle} numberOfLines={2}>
                            {item.title}
                          </Text>
                        </View>
                      </AppButton>
                      <View style={s.actions}>
                        {["pencil", "mic"].map((icon) => (
                          <AppButton
                            key={icon}
                            onPress={() =>
                              Alert.alert(
                                icon === "pencil"
                                  ? "Luyện chép chính tả"
                                  : "Luyện nói",
                                item.title,
                              )
                            }
                            style={s.action}
                            accessibilityLabel={`${icon === "pencil" ? "Luyện viết" : "Luyện nói"} ${item.title}`}
                          >
                            <Ionicons name={icon} size={12} color="#9CA3AF" />
                          </AppButton>
                        ))}
                      </View>
                    </View>
                  ))}
                </View>
              </View>
            )
          );
        })}
        {!filtered.length && (
          <Text style={s.empty}>Không tìm thấy bài học phù hợp.</Text>
        )}
      </ScrollView>
    </View>
  );
}
const s = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "white" },
  toolbar: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 20,
  },
  filter: {
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: "#F3F5F7",
    alignItems: "center",
    justifyContent: "center",
  },
  upgrade: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
    backgroundColor: "#FF7900",
    borderRadius: 18,
    paddingHorizontal: 13,
    paddingVertical: 8,
  },
  upgradeText: { fontSize: 11, fontWeight: "800", color: "white" },
  content: { paddingHorizontal: 16, paddingBottom: 24 },
  section: { marginBottom: 28 },
  heading: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 12,
  },
  title: { fontSize: 16, fontWeight: "800", color: "#1C2536" },
  count: { fontSize: 10, color: "#7E8594", marginTop: 4 },
  see: {
    borderRadius: 12,
    paddingVertical: 4,
    paddingHorizontal: 10,
    backgroundColor: "#E2F4FF",
    alignSelf: "flex-end",
  },
  seeText: { fontSize: 10, fontWeight: "700", color: "#0095D9" },
  grid: { flexDirection: "row", gap: 12 },
  card: {
    flex: 1,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: "#F0F1F3",
    overflow: "hidden",
    backgroundColor: "white",
    boxShadow: "0 3px 6px #00000008",
  },
  thumbnail: {
    height: 96,
    padding: 8,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
  },
  level: {
    fontSize: 9,
    fontWeight: "700",
    borderRadius: 10,
    paddingHorizontal: 7,
    paddingVertical: 3,
  },
  pro: {
    fontSize: 8,
    fontWeight: "800",
    borderRadius: 10,
    paddingHorizontal: 5,
    paddingVertical: 2,
    backgroundColor: "#FFAB00",
    color: "white",
  },
  body: { padding: 10, paddingBottom: 3 },
  duration: { fontSize: 9, color: "#9AA3B3" },
  cardTitle: {
    fontSize: 11,
    fontWeight: "700",
    lineHeight: 16,
    marginTop: 5,
    height: 32,
    color: "#344054",
  },
  actions: { flexDirection: "row", gap: 8, padding: 10, paddingTop: 6 },
  action: {
    height: 24,
    width: 24,
    borderRadius: 12,
    backgroundColor: "#F4F5F7",
    alignItems: "center",
    justifyContent: "center",
  },
  filters: { paddingHorizontal: 16, paddingBottom: 16, gap: 10 },
  search: {
    borderWidth: 1,
    borderColor: "#E2E8F0",
    borderRadius: 14,
    padding: 12,
  },
  chip: { padding: 9, borderRadius: 12, backgroundColor: "#F1F5F9" },
  empty: { paddingVertical: 40, textAlign: "center", color: "#64748B" },
});
