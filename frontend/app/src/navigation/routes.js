export const MAIN_TABS = Object.freeze([
  { id: "home", label: "Hôm nay", icon: "bulb", iconOutline: "bulb-outline" },
  {
    id: "catalog",
    label: "Học tập",
    icon: "book",
    iconOutline: "book-outline",
  },
  {
    id: "vocab",
    label: "Từ vựng",
    icon: "albums",
    iconOutline: "albums-outline",
  },
  {
    id: "games",
    label: "Game",
    icon: "game-controller",
    iconOutline: "game-controller-outline",
  },
  {
    id: "settings",
    label: "Cài đặt",
    icon: "settings",
    iconOutline: "settings-outline",
  },
]);

export const SCREEN_IDS = Object.freeze([
  ...MAIN_TABS.map((tab) => tab.id),
  "deckDetail",
  "flashcardReview",
  "wordMatch",
  "onboarding",
  "login",
]);

export function navigationReducer(stack, action) {
  if (action.type === "back")
    return stack.length > 1 ? stack.slice(0, -1) : stack;
  if (!SCREEN_IDS.includes(action.screen)) return stack;
  if (action.type === "tab") return [action.screen];
  if (action.type === "navigate") {
    // Tránh giữ màn login/review cũ sau khi trở lại tab gốc.
    if (MAIN_TABS.some((tab) => tab.id === action.screen))
      return [action.screen];
    return stack.at(-1) === action.screen ? stack : [...stack, action.screen];
  }
  return stack;
}
