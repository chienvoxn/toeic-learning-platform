import { FakeContentRepository } from './content.fake';
import { RemoteContentRepository } from './content.api';

const isMock = import.meta.env.VITE_USE_MOCK !== 'false';

class ContentRepositoryFactory {
  static instance = null;

  static getInstance() {
    if (!this.instance) {
      this.instance = isMock ? new FakeContentRepository() : new RemoteContentRepository();
    }
    return this.instance;
  }
}

export const contentRepo = ContentRepositoryFactory.getInstance();
