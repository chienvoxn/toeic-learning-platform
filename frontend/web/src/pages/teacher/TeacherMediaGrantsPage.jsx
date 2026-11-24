import { useEffect, useState } from 'react';
import { mediaGrantsApi as api } from '@/features/assessment/media-grants.api';

export default function TeacherMediaGrantsPage() {
  const [page, setPage] = useState(0), [ownedResponse, setOwnedResponse] = useState(null);
  const [media, setMedia] = useState(''), [grantPage, setGrantPage] = useState(0);
  const [grantResponse, setGrantResponse] = useState(null), [revision, setRevision] = useState(0);
  const [recipient, setRecipient] = useState(''), [busy, setBusy] = useState(false);
  const [error, setError] = useState(''), [notice, setNotice] = useState('');
  const owned = ownedResponse?.page === page ? ownedResponse : null;
  const grants = grantResponse?.media === media && grantResponse?.page === grantPage && grantResponse?.revision === revision ? grantResponse.data : null;
  useEffect(() => {
    const abort = new AbortController();
    api.owned(page, abort.signal).then(value => { if (!abort.signal.aborted) { setOwnedResponse(value);setError(''); } }).catch(e => { if (!abort.signal.aborted) setError(e.message); });
    return () => abort.abort();
  }, [page]);
  useEffect(() => {
    const abort = new AbortController();
    if (media) api.grants(media, grantPage, abort.signal).then(value => { if (!abort.signal.aborted) setGrantResponse({ media, page: grantPage, revision, data: value }); }).catch(e => { if (!abort.signal.aborted) setError(e.message); });
    return () => abort.abort();
  }, [media, grantPage, revision]);
  async function change(user, active) {
    setBusy(true);setError('');setNotice('');
    try { const value = await api.change(media, user, active);setNotice(value.changed ? (value.active ? 'Đã cấp quyền tái sử dụng.' : 'Đã thu hồi quyền tái sử dụng.') : 'Trạng thái đã đúng; không ghi thay đổi lặp.');setRevision(n => n + 1); }
    catch (e) { setError(e.message); }
    finally { setBusy(false); }
  }
  return <div className="max-w-5xl space-y-5">
    <div className="page-heading"><h1>Quyền tái sử dụng media</h1><p>Chỉ quản lý tài nguyên bạn sở hữu. Quyền này không cấp quyền xuất bản, đổi chủ sở hữu hoặc thay quyền xem của học viên.</p></div>
    {error && <p role="alert" className="form-error">{error}</p>}{notice && <p role="status">{notice}</p>}
    {!owned && !error && <p>Đang tải media…</p>}
    {owned && <section className="surface p-5 space-y-3" aria-label="Media của tôi">
      <label className="block" htmlFor="grant-media">Tài nguyên của tôi</label>
      <select id="grant-media" className="border rounded p-2 w-full" disabled={busy} value={media} onChange={e => { setMedia(e.target.value);setGrantPage(0);setError('');setNotice(''); }}>
        <option value="">Chọn media</option>{owned.content.map(m => <option key={m.mediaId} value={m.mediaId}>{m.resourceType} · {m.mediaId} · {m.fileSize ?? 0} bytes · {m.visibility}</option>)}
      </select>
      {!owned.content.length && <p>Bạn chưa có media sở hữu.</p>}
      <div className="flex gap-3"><button disabled={busy || !page} onClick={() => { setMedia('');setPage(n => n - 1); }}>Trang trước</button><button disabled={busy || (page + 1) * owned.size >= owned.totalElements} onClick={() => { setMedia('');setPage(n => n + 1); }}>Trang sau</button></div>
    </section>}
    {media && <>
      <form className="surface p-5 space-y-3" aria-label="Cấp quyền tái sử dụng" onSubmit={e => { e.preventDefault();void change(recipient.trim(), true); }}>
        <label className="block" htmlFor="grant-user">User ID người nhận</label><input id="grant-user" className="border rounded p-2 w-full" required pattern="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}" maxLength={36} disabled={busy} value={recipient} onChange={e => setRecipient(e.target.value)} />
        <p>Người nhận phải tồn tại và khác bạn. Server kiểm session, permission và ownership; không gửi mật khẩu hoặc token.</p>
        <button className="pill-button navy" disabled={busy || !grants}>Cấp quyền</button>
      </form>
      <section className="surface p-5 space-y-3" aria-label="Các quyền tái sử dụng">
        {!grants ? <p>Đang tải quyền…</p> : <><table className="w-full"><thead><tr><th>User ID</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{grants.content.map(g => <tr key={g.userId}><td className="break-all">{g.userId}</td><td>{g.active ? 'Đang có quyền' : 'Đã thu hồi'}</td><td><button className="pill-button" disabled={busy} onClick={() => change(g.userId, !g.active)}>{g.active ? 'Thu hồi' : 'Cấp lại'}</button></td></tr>)}</tbody></table>{!grants.content.length && <p>Chưa cấp quyền cho ai.</p>}
          <div className="flex gap-3"><button disabled={busy || !grantPage} onClick={() => setGrantPage(n => n - 1)}>Quyền trước</button><button disabled={busy || (grantPage + 1) * grants.size >= grants.totalElements} onClick={() => setGrantPage(n => n + 1)}>Quyền sau</button><button disabled={busy} onClick={() => { setError('');setRevision(n => n + 1); }}>Đọc lại quyền</button></div>
        </>}
      </section>
    </>}
  </div>;
}
