import { useEffect, useRef, useState } from 'react';
import { authRepo } from '@/features/auth/repositories/auth.repo';
import { loadGoogleSdk } from '@/features/auth/api/google-sdk';
import { useAuth } from '@/features/auth/hooks/useAuth';

export default function GoogleSignIn({ onSuccess, onBusyChange, disabled = false }) {
  const { loginWithGoogle } = useAuth();
  const container = useRef(null);
  const active = useRef(false);
  const working = useRef(false);
  const callbacks = useRef({ onSuccess, onBusyChange, loginWithGoogle, disabled });
  const [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [pending, setPending] = useState(null);
  const [password, setPassword] = useState('');
  useEffect(() => { callbacks.current = { onSuccess, onBusyChange, loginWithGoogle, disabled }; }, [onSuccess, onBusyChange, loginWithGoogle, disabled]);
  async function authenticate(payload) {
    if (working.current || callbacks.current.disabled || !active.current) return;
    working.current = true; setBusy(true); setError('');
    callbacks.current.onBusyChange?.(true);
    try {
      await callbacks.current.loginWithGoogle(payload);
      if (active.current) { setPending(null); setPassword(''); callbacks.current.onSuccess(); }
    } catch (err) {
      if (!active.current) return;
      if (err.code === 'GOOGLE_LINK_REQUIRED') setPending(payload);
      else if (err.status === 401) { setPending(null); setPassword(''); setRevision(value => value + 1); }
      setError(err.message || 'Không đăng nhập được bằng Google.');
    } finally { working.current = false; if (active.current) { setBusy(false); callbacks.current.onBusyChange?.(false); } }
  }
  useEffect(() => {
    let cancelled = false;
    active.current = true;
    let timer;
    setError('');
    authRepo.googleChallenge().then(async challenge => {
      if (cancelled) return;
      const google = await loadGoogleSdk();
      if (cancelled) return;
      google.initialize({ client_id: challenge.clientId, nonce: challenge.nonce, auto_select: false,
        callback: response => { if (!cancelled && response.credential) authenticate({ credential: response.credential, challengeId: challenge.challengeId }); },
      });
      container.current.replaceChildren();
      google.renderButton(container.current, { type: 'standard', theme: 'outline', size: 'large', text: 'continue_with', locale: 'vi', width: 280 });
      timer = setTimeout(() => { if (!cancelled && !working.current) { setPending(null); setPassword(''); setRevision(value => value + 1); } }, challenge.expiresIn * 1000);
    }).catch(err => { if (!cancelled) setError(err.message); });
    return () => { cancelled = true; active.current = false; clearTimeout(timer); };
    // Revision rotates the nonce/challenge; callbacks are read from refs.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [revision]);
  return <section className="my-5 space-y-3" aria-label="Đăng nhập Google">
    <div ref={container} inert={busy || disabled} className={busy || disabled ? 'opacity-60' : ''} aria-busy={busy} />
    {busy && <p className="text-sm" role="status">Đang xác minh với Google...</p>}
    {error && <p className="form-error" role="alert">{error}</p>}
    {pending && <form className="space-y-3" onSubmit={event => { event.preventDefault(); authenticate({ ...pending, currentPassword: password }); }}>
      <label className="block text-sm" htmlFor="google-link-password">Mật khẩu của tài khoản đã có<input id="google-link-password" className="border rounded-lg p-2 w-full mt-2" type="password" required maxLength={72} autoComplete="current-password" value={password} onChange={event => setPassword(event.target.value)} /></label>
      <button className="pill-button navy" type="submit" disabled={busy || disabled}>Xác nhận liên kết Google và đăng nhập</button>
    </form>}
    {error && !pending && <button type="button" className="text-button" disabled={busy} onClick={() => setRevision(value => value + 1)}>Thử lại Google</button>}
  </section>;
}
