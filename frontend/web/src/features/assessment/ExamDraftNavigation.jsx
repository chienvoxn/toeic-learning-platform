import { Link } from 'react-router-dom';
import { useAdminReport } from '../admin/useAdminReport';
import { examDraftApi } from './exam-drafts.api';

export default function ExamDraftNavigation() {
  const result = useAdminReport(examDraftApi.capabilities, null, true);
  return result.data?.manage ? <Link className="sidebar-link" to="/teacher/tests">Biên soạn bộ đề</Link> : null;
}
