import { Navigate, Outlet } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { useAuth } from './hooks/useAuth';
import { examDraftApi } from '../assessment/exam-drafts.api';

export default function RequireTeacher() {
  const { user } = useAuth();

  const roles = Array.isArray(user?.roles) ? user.roles : [];

  const allowed = roles.some(
    role => ['TEACHER', 'ADMIN'].includes(
      typeof role === 'string' ? role : role?.name
    )
  );

  const capabilities = user?.adminCapabilities || {};
  const authorOrReviewer = capabilities.contentManagement || capabilities.questionManagement;
  const [examAccess, setExamAccess] = useState(null);
  useEffect(() => {
    let active = true;
    if (!allowed && !authorOrReviewer) examDraftApi.capabilities().then(value => {
      if (active) setExamAccess({ user, allowed: value.manage || value.review || value.publish });
    }).catch(() => { if (active) setExamAccess({ user, allowed: false }); });
    return () => { active = false; };
  }, [user, allowed, authorOrReviewer]);
  if (!allowed && !authorOrReviewer && examAccess?.user !== user) return <p role="status">Đang kiểm tra quyền biên soạn…</p>;
  if (!allowed && !authorOrReviewer && !examAccess?.allowed) {
    return <Navigate to="/dashboard" replace />;
  }

  return <Outlet />;
}
