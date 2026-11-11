import { FakeContentRepository } from './content.fake';
import { RemoteContentRepository } from './content.api';

import { useMock as isMock } from '@/lib/runtime-config';

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
