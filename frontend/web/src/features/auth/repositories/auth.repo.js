import { FakeAuthRepository } from './auth.fake';
import { RemoteAuthRepository } from './auth.api';

const isMock = import.meta.env.VITE_USE_MOCK !== 'false';

class AuthRepositoryFactory {
  static instance = null;

  static getInstance() {
    if (!this.instance) {
      this.instance = isMock ? new FakeAuthRepository() : new RemoteAuthRepository();
    }
    return this.instance;
  }
}

export const authRepo = AuthRepositoryFactory.getInstance();
