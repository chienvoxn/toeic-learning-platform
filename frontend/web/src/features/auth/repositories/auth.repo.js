import { FakeAuthRepository } from './auth.fake';
import { RemoteAuthRepository } from './auth.api';
import { isRemoteAuth } from '../config';

const isMock = !isRemoteAuth;

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
