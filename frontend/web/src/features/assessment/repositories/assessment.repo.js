import { FakeAssessmentRepository } from './assessment.fake';
import { GuestAssessmentRepository } from './assessment.guest';
import { RemoteAssessmentRepository } from './assessment.api';

import { useMock as isMock } from '@/lib/runtime-config';

class AssessmentRepositoryFactory {
  static instance = null;

  static getInstance() {
    if (!this.instance) {
      const guest = new GuestAssessmentRepository();
      const remote = new RemoteAssessmentRepository();
      const forAttempt = id => id.startsWith('guest_') ? guest : remote;
      this.instance = isMock ? new FakeAssessmentRepository() : {
        getCatalog: async (page = 0) => [...await remote.getCatalog(page), ...(page === 0 ? await guest.getCatalog() : [])],
        getTestDetail: id => id === 'guest-reading-sample' ? guest.getTestDetail(id) : remote.getTestDetail(id),
        startAttempt: (id, parts) => id === 'guest-reading-sample' ? guest.startAttempt(id, parts) : remote.startAttempt(id),
        getAttempt: id => forAttempt(id).getAttempt(id),
        saveAnswer: payload => forAttempt(payload.attemptId).saveAnswer(payload),
        submitAttempt: id => forAttempt(id).submitAttempt(id),
        getResult: id => forAttempt(id).getResult(id),
      };
    }
    return this.instance;
  }
}

export const assessmentRepo = AssessmentRepositoryFactory.getInstance();
