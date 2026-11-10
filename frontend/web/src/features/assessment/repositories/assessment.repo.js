import { FakeAssessmentRepository } from './assessment.fake';
import { GuestAssessmentRepository } from './assessment.guest';

import { useMock as isMock } from '@/lib/runtime-config';

class AssessmentRepositoryFactory {
  static instance = null;

  static getInstance() {
    if (!this.instance) {
      this.instance = isMock ? new FakeAssessmentRepository() : new GuestAssessmentRepository();
    }
    return this.instance;
  }
}

export const assessmentRepo = AssessmentRepositoryFactory.getInstance();
