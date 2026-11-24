import { request } from "./apiClient.js";

export const vocabularyApi = {
  async getCatalogDecks() {
    try {
      return await request("/vocabulary/catalog/decks");
    } catch {
      return null;
    }
  },

  async getUserDecks() {
    try {
      return await request("/vocabulary-decks");
    } catch {
      return null;
    }
  },

  async getDeckFlashcards(deckId) {
    try {
      return await request(`/vocabulary-decks/${deckId}/flashcards`);
    } catch {
      return null;
    }
  },

  async submitReview(cardId, quality) {
    try {
      return await request(`/vocabularies/${cardId}/reviews`, {
        method: "POST",
        body: JSON.stringify({ quality }), // SM-2 quality 0-5
      });
    } catch {
      return { success: true, cardId, quality };
    }
  },
};
