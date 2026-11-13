import { useState } from 'react';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { useAuth } from '@/features/auth/hooks/useAuth';
import { settingsApi } from '@/features/admin/settings.api';
import { useAdminReport } from '@/features/admin/useAdminReport';

const titles = {
  'vocabulary.recommendations.defaultLimit': 'Số gợi ý từ vựng mặc định',
  'learning.progress.defaultDays': 'Số ngày tiến độ học mặc định',
};
function SettingEditor({ setting, saved, failed }) {
  const [value, setValue] = useState(String(setting.value));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  async function submit(event) {
    event.preventDefault(); setError(''); setNotice('');
    const number = Number(value);
    if (!/^-?\d+$/.test(value) || !Number.isSafeInteger(number) || number < setting.minValue || number > setting.maxValue) {
      setError(`Nhập số nguyên từ ${setting.minValue} đến ${setting.maxValue}.`); return;
    }
    setBusy(true);
    try {
      const result = await settingsApi.update(setting.key, number, setting.revision);
      setNotice(result.revision === setting.revision ? 'Giá trị không đổi.' : 'Đã lưu cấu hình.'); saved(result);
    } catch (err) { setError(err.message); await failed(err); }
    finally { setBusy(false); }
  }
  return <form className="surface p-5 space-y-3" aria-label={titles[setting.key] || setting.key} onSubmit={submit}>
    <h2>{titles[setting.key] || setting.key}</h2><p className="text-sm break-all">{setting.key}</p>
    <p>Hiện tại: <strong>{setting.value}</strong> · Mặc định sản phẩm: {setting.defaultValue} · Giới hạn: {setting.minValue}–{setting.maxValue} · Kiểu: {setting.type}</p>
    <label className="grid gap-2" htmlFor={setting.key}>Giá trị mới<input id={setting.key} className="border rounded-lg p-2" inputMode="numeric" value={value} disabled={busy} onChange={event => { setValue(event.target.value); setNotice(''); }} /></label>
    <p className="text-sm">Phiên bản: {setting.revision} · Cập nhật UTC: {setting.updatedAt}</p>
    <button className="pill-button navy" disabled={busy}>{busy ? 'Đang lưu...' : 'Lưu cấu hình'}</button>
    {error && <p role="alert" className="form-error">{error}</p>}{notice && <p role="status">{notice}</p>}
  </form>;
}
export default function AdminSettingsPage() {
  const { user, restore } = useAuth();
  const allowed = user?.adminCapabilities?.settingsManagement === true;
  const [revision, setRevision] = useState(0);
  const [overrides, setOverrides] = useState({});
  const report = useAdminReport(settingsApi.list, null, allowed, revision);
  async function failed(error) { if (error.status === 403) { setOverrides({}); await restore({ background: true }); } }
  return <LearnerTemplate><div className="max-w-5xl mx-auto space-y-5">
    <div className="page-heading"><h1>Cấu hình hệ thống</h1><p>Hai mặc định nghiệp vụ đã chốt. Không quản trị biến môi trường, secret hoặc cấu hình kết nối tại đây.</p></div>
    {!allowed ? <p role="alert" className="form-error">Bạn không có quyền quản trị cấu hình.</p> : <>
      <p>Giá trị mới áp dụng từ request tiếp theo khi không truyền tham số. Tham số hợp lệ đã truyền luôn được ưu tiên. Không cần restart và không thay đổi lịch sử/attempt.</p>
      <button className="pill-button" disabled={report.loading} onClick={() => { setOverrides({}); setRevision(value => value + 1); }}>Tải lại cấu hình</button>
      {report.loading && <LoadingSpinner text="Đang tải cấu hình..." />}{report.error && <p role="alert" className="form-error">{report.error}</p>}
      {report.data && report.data.map(item => {
        const current = overrides[item.key] || item;
        return <SettingEditor key={current.key + ':' + revision} setting={current} saved={result => setOverrides(previous => ({ ...previous, [result.key]: result }))} failed={failed} />;
      })}
      <section className="surface p-5 space-y-3"><h2>Quản trị dữ liệu</h2><p>Backup/restore là thao tác vận hành bằng script, không có endpoint hoặc nút chạy SQL/shell. Trang này không thực thi hay báo thành công backup.</p><p>Script: <code>backend/scripts/manage-local-data.ps1</code> với Backup / Restore / Verify. Chỉ dùng PostgreSQL demo/test riêng và DB đích mới.</p><p>Xem runbook <code>docs/ADMIN06_System_Configuration_Data_Management.md</code> để chạy và kiểm hash/count thực tế. Không restore đè nguồn hoặc toeic_db.</p></section>
    </>}
  </div></LearnerTemplate>;
}
