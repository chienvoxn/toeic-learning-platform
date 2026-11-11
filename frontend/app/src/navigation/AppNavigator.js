import React from "react";
import { HomeScreen } from "../features/analytics";
import { CatalogScreen } from "../features/content";
import {
  VocabScreen,
  DeckDetailScreen,
  FlashcardScreen,
} from "../features/vocabulary";
import { GamesScreen, WordMatchScreen } from "../features/games";
import { SettingsScreen } from "../features/profile";
import { OnboardingScreen } from "../features/onboarding";
import { LoginScreen } from "../features/auth";
import LearnerTemplate from "../components/templates/LearnerTemplate";
import BottomTabBar from "../components/organisms/BottomTabBar";
import useAppNavigation from "./useAppNavigation";
import { MAIN_TABS } from "./routes";

const screens = {
  home: HomeScreen,
  catalog: CatalogScreen,
  vocab: VocabScreen,
  deckDetail: DeckDetailScreen,
  flashcardReview: FlashcardScreen,
  games: GamesScreen,
  wordMatch: WordMatchScreen,
  settings: SettingsScreen,
  onboarding: OnboardingScreen,
  login: LoginScreen,
};

export default function AppNavigator() {
  const { currentScreen, screenParams, navigate, selectTab, goBack } =
    useAppNavigation();
  const Screen = screens[currentScreen] || HomeScreen;
  const isMainTab = MAIN_TABS.some((tab) => tab.id === currentScreen);
  return (
    <LearnerTemplate
      dark={currentScreen === "wordMatch"}
      navigation={
        isMainTab ? (
          <BottomTabBar
            tabs={MAIN_TABS}
            currentScreen={currentScreen}
            onSelect={selectTab}
          />
        ) : null
      }
    >
      <Screen
        key={currentScreen}
        {...screenParams}
        navigate={navigate}
        goBack={goBack}
      />
    </LearnerTemplate>
  );
}
