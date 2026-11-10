import { apiRequest } from '@/services/apiClient';

const ratings = { AGAIN: 1, HARD: 2, GOOD: 3, EASY: 4 };
export function toCard(dto, state = null) {
  return {
    id: dto.vocabularyId, word: dto.word, definitionVi: dto.meaning,
    partOfSpeech: dto.partOfSpeech || '', phonetic: dto.pronunciation || '',
    exampleEn: dto.example || '', exampleVi: '', note: dto.note || '',
    status: !state?.lastReviewedAt ? 'new' : state.repetitionCount >= 5 ? 'mastered' : 'learning',
    due: Boolean(state?.nextReviewAt && Date.parse(state.nextReviewAt) <= Date.now()),
  };
}
export function toDeck(dto, cards = []) {
  return { id: dto.deckId, title: dto.name, description: dto.description || '',
    category: 'Cá nhân', totalCards: cards.length,
    dueCards: cards.filter(card => card.due).length,
    masteredCards: cards.filter(card => card.status === 'mastered').length };
}
export class RemoteVocabularyRepository {
  async getDecks() {
    const decks = await apiRequest('/vocabulary-decks');
    return Promise.all(decks.map(async dto => toDeck(dto, await this.getDeckCards(dto.deckId))));
  }
  async getDeckCards(deckId) {
    const cards = await apiRequest(`/vocabulary-decks/${deckId}/flashcards`);
    return Promise.all(cards.map(async card => {
      let state;
      try { state = await apiRequest(`/vocabularies/${card.vocabularyId}/review-state`); }
      catch (error) { if (error.status !== 404) throw error; }
      return toCard(card, state);
    }));
  }
  async getDeckDetail(deckId) {
    const [dto, words] = await Promise.all([apiRequest(`/vocabulary-decks/${deckId}`), this.getDeckCards(deckId)]);
    const deck = toDeck(dto, words);
    return { deck, groups: words.length ? [{ id: deck.id, title: deck.title, description: deck.description, words }] : [],
      totalWords: words.length, masteredCount: deck.masteredCards,
      learningCount: words.filter(word => word.status === 'learning').length };
  }
  rateCard(cardId, rating) {
    if (!ratings[rating]) throw new Error('Đánh giá ôn tập không hợp lệ.');
    return apiRequest(`/vocabularies/${cardId}/reviews`, { method: 'POST', body: { rating: ratings[rating] } });
  }
  createDeck(name, description) {
    return apiRequest('/vocabulary-decks', { method: 'POST', body: { name, description } });
  }
  createVocabulary(payload) {
    return apiRequest('/vocabularies', { method: 'POST', body: payload });
  }
  addToDeck(deckId, vocabularyId) {
    return apiRequest(`/vocabulary-decks/${deckId}/vocabularies`, { method: 'POST', body: { vocabularyId } });
  }
}