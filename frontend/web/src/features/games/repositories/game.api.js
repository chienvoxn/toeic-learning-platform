import { apiRequest } from '@/services/apiClient';

export class RemoteGameRepository {
  async getGameCatalog() {
    return await apiRequest('/games');
  }

  async getWordMatchSession(pairCount = 6) {
    return await apiRequest(`/games/word-match/start?pairCount=${pairCount}`, {
      method: 'POST',
    });
  }

  async submitGameResult(sessionId, score, clearedPairs) {
    return await apiRequest(`/games/word-match/submit`, {
      method: 'POST',
      body: { sessionId, score, clearedPairs },
    });
  }
}
