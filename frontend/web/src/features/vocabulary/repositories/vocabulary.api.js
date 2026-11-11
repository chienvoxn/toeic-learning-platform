
import { apiRequest } from '@/services/apiClient';

const ratings = {
  AGAIN: 1,
  HARD: 2,
  GOOD: 3,
  EASY: 4,
};

const mapDeck = (deck) => ({
  id: deck.deckId,
  title: deck.name,
  description: deck.description,
  category: 'Cá nhân',
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
  note: card.note || '',
});

const mapReviewStatus = (state) => {
  if (!state) return 'new';

  const status = state.status ?? state.reviewStatus;

  if (typeof status === 'string') {
    return status.toLowerCase();
  }

  return 'learning';
};

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

    return Promise.all(
      cards.map(async (card) => {
        const mappedCard = mapCard(card);

        try {
          const reviewState = await this.getReviewState(
            card.vocabularyId
          );

          return {
            ...mappedCard,
            status: mapReviewStatus(reviewState),
            reviewState,
          };
        } catch (error) {
          // 404: Vocabulary has not been reviewed yet.
          // 401/403 and other errors must propagate.
          if (error?.status === 404) {
            return {
              ...mappedCard,
              status: 'new',
              reviewState: null,
            };
          }

          throw error;
        }
      })
    );
  }

  async rateCard(cardId, rating) {
    const normalized =
      typeof rating === 'string'
        ? ratings[rating.toUpperCase()]
        : rating;

    if (
      !Number.isInteger(normalized) ||
      normalized < 1 ||
      normalized > 4
    ) {
      throw new Error('Đánh giá ôn tập không hợp lệ.');
    }

    return apiRequest(`/vocabularies/${cardId}/reviews`, {
      method: 'POST',
      body: { rating: normalized },
    });
  }

  async createDeck(name, description) {
    return apiRequest('/vocabulary-decks', {
      method: 'POST',
      body: { name, description },
    });
  }

  async createVocabulary(payload) {
    return apiRequest('/vocabularies', {
      method: 'POST',
      body: payload,
    });
  }

  async addToDeck(deckId, vocabularyId) {
    return apiRequest(
      `/vocabulary-decks/${deckId}/vocabularies`,
      {
        method: 'POST',
        body: { vocabularyId },
      }
    );
  }

  async getReviewState(cardId) {
    return apiRequest(
      `/vocabularies/${cardId}/review-state`
    );
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
    return apiRequest(
      `/vocabulary-decks/${deckId}/learning-session`
    );
  }

  async saveLearningSession(deckId, data) {
    return apiRequest(
      `/vocabulary-decks/${deckId}/learning-session`,
      {
        method: 'PUT',
        body: data,
      }
    );
  }

  async clearLearningSession(deckId) {
    return apiRequest(
      `/vocabulary-decks/${deckId}/learning-session`,
      {
        method: 'DELETE',
      }
    );
  }

  async getDueReviews() {
    return apiRequest('/vocabularies/reviews/due');
  }

  async getReviewStatistics() {
    return apiRequest('/vocabularies/reviews/statistics');
  }
}
