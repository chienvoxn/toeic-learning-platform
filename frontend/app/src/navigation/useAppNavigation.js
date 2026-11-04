import { useCallback, useEffect, useReducer, useState } from "react";
import { BackHandler, Platform } from "react-native";
import { navigationReducer } from "./routes";

export default function useAppNavigation() {
  const [stack, dispatch] = useReducer(navigationReducer, ["home"]);
  const [params, setParams] = useState({});
  const navigate = useCallback((screen, values = {}) => {
    setParams((previous) => ({ ...previous, [screen]: values }));
    dispatch({ type: "navigate", screen });
  }, []);
  const selectTab = useCallback(
    (screen) => dispatch({ type: "tab", screen }),
    [],
  );
  const goBack = useCallback(() => dispatch({ type: "back" }), []);

  useEffect(() => {
    if (Platform.OS !== "android") return undefined;
    const subscription = BackHandler.addEventListener(
      "hardwareBackPress",
      () => {
        if (stack.length <= 1) return false;
        goBack();
        return true;
      },
    );
    return () => subscription.remove();
  }, [stack.length, goBack]);

  return {
    currentScreen: stack.at(-1),
    screenParams: params[stack.at(-1)] || {},
    navigate,
    selectTab,
    goBack,
  };
}
