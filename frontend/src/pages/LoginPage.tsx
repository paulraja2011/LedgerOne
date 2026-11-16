import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import { ErrorText } from '../components/ui';

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('admin@ledgerone.dev');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email, password);
      navigate('/', { replace: true });
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login-wrap">
      <form className="card login" onSubmit={submit}>
        <div className="brand big"><span className="brand-mark">L1</span> LedgerOne</div>
        <p className="muted">Sign in to your books</p>
        <label>Email<input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></label>
        <label>Password<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required autoFocus /></label>
        <ErrorText error={error} />
        <button className="btn primary wide" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
        <p className="hint">Demo: admin@ledgerone.dev / Admin@123</p>
      </form>
    </div>
  );
}
