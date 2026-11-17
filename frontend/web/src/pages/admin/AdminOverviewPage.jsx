import { useState } from 'react';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { useAuth } from '@/features/auth/hooks/useAuth';
import { monitoringApi } from '@/features/admin/monitoring.api';
import { useAdminReport } from '@/features/admin/useAdminReport';

export default function AdminOverviewPage() {
  const { user } = useAuth();
  const healthAllowed = user?.adminCapabilities?.healthRead === true;
  const usageAllowed = user?.adminCapabilities?.usageRead === true;
  const [draft, setDraft] = useState({ from: '', to: '' });
  const [query, setQuery] = useState({});
  const [revision, setRevision] = useState(0);
  const health = useAdminReport(monitoringApi.health, query, healthAllowed, revision);
  const usage = useAdminReport(monitoringApi.usage, query, usageAllowed, revision);
  return <LearnerTemplate><div className="max-w-6xl mx-auto space-y-5">
    <div className="page-heading"><h1>Tổng quan hệ thống</h1><p>Snapshot từ server và dữ liệu DB thực, không phải số người dùng online hoặc số liệu realtime.</p></div>
    {!healthAllowed && !usageAllowed ? <p role="alert" className="form-error">Bạn không có quyền xem tổng quan hệ thống.</p> : <>
      <button className="pill-button" disabled={health.loading || usage.loading} onClick={() => setRevision(value => value + 1)}>Làm mới tổng quan</button>
      {healthAllowed && <section className="surface p-5 space-y-3" aria-label="Health thành phần"><h2>Health thành phần</h2>
        {health.loading && <LoadingSpinner text="Đang kiểm tra health..." />}{health.error && <p role="alert" className="form-error">{health.error}</p>}
        {health.data && <><ul>{Object.entries(health.data.components).map(([name, component]) => <li key={name} className="py-2"><strong>{name}</strong>: <span>{component.status}</span> · {component.checkedAt}</li>)}</ul><p>Thời điểm UTC: {health.data.checkedAt}</p></>}
        <p className="text-sm">AI NOT_CONFIGURED nghĩa là backend chưa có endpoint kiểm tra AI. UP chỉ xác nhận HTTP health của service, không xác nhận model/provider hay chất lượng trả lời.</p>
      </section>}
      {usageAllowed && <><form className="surface p-5 flex flex-wrap gap-4 items-end" onSubmit={event => { event.preventDefault(); setQuery({ ...draft }); }}>
        <label htmlFor="usage-from">Từ (UTC, gồm mốc)<input id="usage-from" className="border rounded-lg p-2 block" placeholder="2026-11-13T00:00:00Z" value={draft.from} onChange={event => setDraft(previous => ({ ...previous, from: event.target.value }))} /></label>
        <label htmlFor="usage-to">Đến (UTC, không gồm mốc)<input id="usage-to" className="border rounded-lg p-2 block" placeholder="2026-11-14T00:00:00Z" value={draft.to} onChange={event => setDraft(previous => ({ ...previous, to: event.target.value }))} /></label>
        <button className="pill-button navy" disabled={usage.loading}>Xem thống kê</button>
      </form>{usage.loading && <LoadingSpinner text="Đang tải thống kê..." />}{usage.error && <p role="alert" className="form-error">{usage.error}</p>}
      {usage.data && <>
        <section className="surface p-5 space-y-3" aria-label="Thống kê tài khoản"><h2>Tài khoản hiện tại</h2><p>Tổng user: <strong>{usage.data.users.total}</strong> · Không có role: {usage.data.users.withoutRoleCount}</p><h3>Theo trạng thái</h3><ul>{usage.data.users.byStatus.map(group => <li key={group.name}>{group.name}: {group.count}</li>)}</ul><h3>Theo role</h3><ul>{usage.data.users.byRole.map(group => <li key={group.roleId}>{group.name} (#{group.roleId}): {group.count}</li>)}</ul><p className="text-sm">User nhiều role được tính ở nhiều nhóm; không cộng các nhóm role thành tổng user. Các số tài khoản là snapshot hiện tại, không lọc theo ngày đăng ký.</p></section>
        <section className="surface p-5 space-y-3" aria-label="Thống kê lượt làm bài"><h2>Lượt làm bài trong khoảng</h2><p>{usage.data.window.from} → {usage.data.window.to} (UTC, không gồm mốc cuối)</p><table className="w-full text-left"><thead><tr><th>Loại snapshot</th><th>Bắt đầu</th><th>Đã nộp</th></tr></thead><tbody><tr><td>Practice</td><td>{usage.data.practice.startedCount}</td><td>{usage.data.practice.submittedCount}</td></tr><tr><td>Section</td><td>{usage.data.section.startedCount}</td><td>{usage.data.section.submittedCount}</td></tr></tbody></table><p className="text-sm">Bắt đầu theo startedAt; đã nộp theo submittedAt và trạng thái SUBMITTED/GRADED. Hai số tính độc lập, không phải cùng cohort; không quy đổi điểm TOEIC.</p></section>
        <section className="surface p-5 space-y-3" aria-label="Hoạt động quản trị đã ghi"><h2>Hoạt động quản trị đã ghi</h2><p>{usage.data.administrativeActivity.recordedCount} bản ghi trong khoảng</p>{usage.data.administrativeActivity.byAction.length ? <ul>{usage.data.administrativeActivity.byAction.map(group => <li key={group.name}>{group.name}: {group.count}</li>)}</ul> : <p>Chưa có dữ liệu hoạt động quản trị trong khoảng này.</p>}<p className="text-sm">Chỉ các hành động ADMIN-01/02 đã ghi audit, không phải toàn bộ hoạt động học tập/đăng nhập.</p><p>Snapshot UTC: {usage.data.generatedAt}</p></section>
      </>}
      </>}
    </>}
  </div></LearnerTemplate>;
}
