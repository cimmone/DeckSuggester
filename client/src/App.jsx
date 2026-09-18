import { useCallback, useEffect, useState } from 'react';
import { AuthChallenge } from './pages/AuthChallenge';
import { DeckEditor } from './pages/DeckEditor';
import { LibraryApp } from './pages/LibraryApp';
import { deckIdFromPath, useLocationPath } from './lib/router';
import './styles.css';

function App() {
  const [session, setSession] = useState(null);

  const refreshSession = useCallback(async () => {
    const response = await fetch('/auth/session');
    if (!response.ok) throw new Error('Could not check your session');
    const nextSession = await response.json();
    setSession(nextSession);
    return nextSession;
  }, []);

  useEffect(() => {
    refreshSession().catch(() => setSession({ authenticated: false }));
  }, [refreshSession]);

  async function signOut() {
    await fetch('/auth/logout', {
      method: 'POST', headers: { [session.csrfHeaderName]: session.csrfToken },
    });
    setSession({ authenticated: false });
  }

  const path = useLocationPath();
  const deckId = deckIdFromPath(path);

  if (session === null) return <div className="session-loading">Opening DeckSuggester…</div>;
  if (!session.authenticated) return <AuthChallenge onAuthenticated={refreshSession} />;
  if (deckId) {
    return <DeckEditor session={session} deckId={deckId} onSignOut={signOut}
      onUnauthenticated={() => setSession({ authenticated: false })} />;
  }
  return <LibraryApp session={session} onSignOut={signOut}
    onUnauthenticated={() => setSession({ authenticated: false })} />;
}

export default App;
