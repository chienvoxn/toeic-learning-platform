import { vocabularyRepository } from "../repositories/vocabulary.mock";

// Hook chỉ đọc fixture để trình bày; nhóm trong export chưa phải schema deck API.
export default function useDeckDetail(deckId) {
  const decks = vocabularyRepository.getDecks();
  return {
    deck: decks.find((d) => d.id === deckId) || decks[0],
    groups: vocabularyRepository.getGroups(),
    words: vocabularyRepository.getWords(),
  };
}
