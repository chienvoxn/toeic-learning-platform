import { FakeVocabularyRepository } from './vocabulary.fake';
import { RemoteVocabularyRepository } from './vocabulary.api';

const isMock = import.meta.env.VITE_USE_MOCK !== 'false';

class VocabularyRepositoryFactory {
  static instance = null;

  static getInstance() {
    if (!this.instance) {
      this.instance = isMock ? new FakeVocabularyRepository() : new RemoteVocabularyRepository();
    }
    return this.instance;
  }
}

export const vocabularyRepo = VocabularyRepositoryFactory.getInstance();
