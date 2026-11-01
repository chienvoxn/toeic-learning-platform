import { FakeGameRepository } from './game.fake';
import { RemoteGameRepository } from './game.api';

const isMock = import.meta.env.VITE_USE_MOCK !== 'false';

class GameRepositoryFactory {
  static instance = null;

  static getInstance() {
    if (!this.instance) {
      this.instance = isMock ? new FakeGameRepository() : new RemoteGameRepository();
    }
    return this.instance;
  }
}

export const gameRepo = GameRepositoryFactory.getInstance();
