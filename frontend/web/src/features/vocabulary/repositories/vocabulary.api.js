
import { apiRequest } from '@/services/apiClient';

const mapDeck = (deck) => ({
  id: deck.deckId,
  title: deck.name,
  description: deck.description,
  category: 'TỪ VỰNG',
  totalCards: null,
});

const mapCard = (card) => ({
  id: card.vocabularyId,
  word: card.word,
  definitionVi: card.meaning,
  partOfSpeech: card.partOfSpeech,
  phonetic: card.pronunciation,
  exampleEn: card.example,
  exampleVi: '',
  image: null,
  collocations: card.note,
});

export class RemoteVocabularyRepository {
  async getCatalogDecks() {
    const decks = await apiRequest('/vocabulary/catalog/decks');
    return decks.map((deck) => ({
      id: deck.deckId,
      title: deck.name,
      description: deck.description,
      category: deck.category || 'TOEIC',
    }));
  }

  async startCatalogDeck(deckId) {
    return apiRequest(
      `/vocabulary/catalog/decks/${deckId}/start`,
      { method: 'POST' }
    );
  }

  async getDecks() {
    const decks = await apiRequest('/vocabulary-decks');
    return decks.map(mapDeck);
  }

  async getDeckDetail(deckId) {
    const deck = await apiRequest(
      `/vocabulary-decks/${deckId}`
    );
    return mapDeck(deck);
  }

  async getDeckCards(deckId) {
    const cards = await apiRequest(
      `/vocabulary-decks/${deckId}/flashcards`
    );
    return cards.map(mapCard);
  }

  async rateCard(cardId, rating) {
    return apiRequest(`/vocabularies/${cardId}/reviews`, {
      method: 'POST',
      body: { rating },
    });
  }

  async getReviewState(cardId) {
    return apiRequest(`/vocabularies/${cardId}/review-state`);
  }

  async getRecommendations() {
    return apiRequest('/vocabulary/recommendations');
  }

  async getLearningOverview() {
    return apiRequest('/learning-profile/overview');
  }

  async getLearningStatistics() {
    return apiRequest('/learning-profile/statistics');
  }

  async getLearningProgress(days = 7) {
    return apiRequest(
      `/learning-profile/progress?days=${days}`
    );
  }

  async getLearningSession(deckId) {
    return apiRequest(`/vocabulary-decks/${deckId}/learning-session`);
  }

  async saveLearningSession(deckId, data) {
    return apiRequest(`/vocabulary-decks/${deckId}/learning-session`, {
      method: 'PUT', body: data,
    });
  }

  async clearLearningSession(deckId) {
    return apiRequest(`/vocabulary-decks/${deckId}/learning-session`, { method: 'DELETE' });
  }

  async getDueReviews() {
    return apiRequest('/vocabularies/reviews/due');
  }

  async getReviewStatistics() {
    return apiRequest('/vocabularies/reviews/statistics');
  }
}
