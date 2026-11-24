import { Link } from 'react-router-dom';
import { useAdminReport } from './useAdminReport';
import { moderationApi } from './moderation.api';
export default function ModerationNavigation() {
  const caps = useAdminReport(moderationApi.capabilities, null, true);
  if (!caps.data || !Object.values(caps.data).some(scope => Object.values(scope).some(Boolean))) return null;
  return <><Link className="sidebar-link" to="/teacher/content">Soạn nội dung</Link><Link className="sidebar-link" to="/moderation">Duyệt và xuất bản</Link></>;
}
