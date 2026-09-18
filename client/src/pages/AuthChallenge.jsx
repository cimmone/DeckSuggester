import { useState } from 'react';
import { jsonRequest } from '../lib/api';

export function AuthChallenge({ onAuthenticated }) {
  const resetToken = new URLSearchParams(window.location.search).get('resetToken');
  const [mode, setMode] = useState(resetToken ? 'confirm-reset' : 'login');
  const [form, setForm] = useState({
    username: '', password: '', email: '', account: '', token: resetToken || '',
  });
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState(null);

  function update(field) {
    return (event) => setForm((current) => ({ ...current, [field]: event.target.value }));
  }

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setNotice(null);
    try {
      if (mode === 'login') {
        await jsonRequest('/auth/login', { username: form.username, password: form.password });
        await onAuthenticated();
      } else if (mode === 'register') {
        await jsonRequest('/auth/register', {
          username: form.username, password: form.password, email: form.email,
        });
        setForm((current) => ({ ...current, password: '' }));
        setMode('login');
        setNotice({ type: 'success', text: 'Account created. Sign in to open your library.' });
      } else if (mode === 'request-reset') {
        const result = await jsonRequest('/auth/password-reset/request', { account: form.account });
        setNotice({ type: 'success', text: result.message });
      } else {
        const result = await jsonRequest('/auth/password-reset/confirm', {
          token: form.token, password: form.password,
        });
        window.history.replaceState({}, '', '/ui/');
        setForm((current) => ({ ...current, password: '', token: '' }));
        setMode('login');
        setNotice({ type: 'success', text: result.message });
      }
    } catch (error) {
      setNotice({ type: 'error', text: error.message });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-shell">
      <section className="auth-intro">
        <a className="brand" href="/ui/">
          <span className="brand-mark">DS</span>
          <span>DeckSuggester <small>Private library</small></span>
        </a>
        <div>
          <p className="eyebrow">Your collection, protected</p>
          <h1>Know every<br /><em>draw.</em></h1>
          <p>Import and explore your Archidekt decks in a library isolated to your account.</p>
        </div>
      </section>
      <main className="auth-card">
        {mode === 'login' && <><p className="eyebrow">Authentication challenge</p>
          <h2>Sign in</h2><p>Enter your username and password to continue.</p></>}
        {mode === 'register' && <><p className="eyebrow">New account</p>
          <h2>Create your library</h2>
          <p>A private library is created for you automatically — there's nothing else to set up.</p></>}
        {mode === 'request-reset' && <><p className="eyebrow">Password recovery</p>
          <h2>Request a reset</h2><p>The reset URL will be written to the application logs.</p></>}
        {mode === 'confirm-reset' && <><p className="eyebrow">Password recovery</p>
          <h2>Choose a new password</h2><p>Reset links are single-use and expire automatically.</p></>}

        {notice && <div className={`notice ${notice.type}`}>{notice.text}</div>}
        <form className="auth-form" onSubmit={submit}>
          {(mode === 'login' || mode === 'register') && <label>
            Username
            <input required minLength={mode === 'register' ? 3 : undefined} maxLength="64"
              autoComplete="username" value={form.username} onChange={update('username')} />
          </label>}
          {mode === 'register' && <label>
            Email
            <input required type="email" maxLength="254" autoComplete="email"
              value={form.email} onChange={update('email')} />
          </label>}
          {mode === 'request-reset' && <label>
            Username or email
            <input required maxLength="254" autoComplete="username"
              value={form.account} onChange={update('account')} />
          </label>}
          {mode === 'confirm-reset' && <input type="hidden" value={form.token} />}
          {mode !== 'request-reset' && <label>
            {mode === 'confirm-reset' ? 'New password' : 'Password'}
            <input required type="password" minLength={mode === 'login' ? undefined : 12}
              maxLength="256" autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              value={form.password} onChange={update('password')} />
            {mode !== 'login' && <small>Use at least 12 characters.</small>}
          </label>}
          <button className="primary-button auth-submit" disabled={busy}>
            {busy ? 'Working…' : mode === 'login' ? 'Sign in' : mode === 'register'
              ? 'Create account' : mode === 'request-reset' ? 'Write reset URL' : 'Reset password'}
          </button>
        </form>

        <div className="auth-links">
          {mode !== 'login' && <button onClick={() => setMode('login')}>Back to sign in</button>}
          {mode === 'login' && <button onClick={() => setMode('register')}>Create an account</button>}
          {mode === 'login' && <button onClick={() => setMode('request-reset')}>Forgot password?</button>}
        </div>
      </main>
    </div>
  );
}
