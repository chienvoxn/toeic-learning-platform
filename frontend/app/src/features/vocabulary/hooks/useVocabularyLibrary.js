import { vocabularyRepository } from '../repositories/vocabulary.mock';

export default function useVocabularyLibrary() {
  const decks = vocabularyRepository.getDecks();

  return { decks };
}
