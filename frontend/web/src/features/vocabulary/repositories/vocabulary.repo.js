import { FakeVocabularyRepository } from './vocabulary.fake';
import { RemoteVocabularyRepository } from './vocabulary.api';

import { useMock as isMock } from '@/lib/runtime-config';

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
