import { apiRequest } from '@/services/apiClient';

export class RemoteVocabularyRepository {
  async getDecks() {
    return await apiRequest('/vocabulary/decks');
  }

  async getDeckCards(deckId) {
    return await apiRequest(`/vocabulary/decks/${deckId}/cards`);
  }

  async getDeckDetail(deckId) {
    return await apiRequest(`/vocabulary/decks/${deckId}`);
  }

  async rateCard(cardId, rating) {
    return await apiRequest(`/vocabulary/cards/${cardId}/review`, {
      method: 'POST',
      body: { rating },
    });
  }
}
