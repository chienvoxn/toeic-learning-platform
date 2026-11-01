import { FakeAssessmentRepository } from './assessment.fake';
import { RemoteAssessmentRepository } from './assessment.api';

const isMock = import.meta.env.VITE_USE_MOCK !== 'false';

class AssessmentRepositoryFactory {
  static instance = null;

  static getInstance() {
    if (!this.instance) {
      this.instance = isMock ? new FakeAssessmentRepository() : new RemoteAssessmentRepository();
    }
    return this.instance;
  }
}

export const assessmentRepo = AssessmentRepositoryFactory.getInstance();
