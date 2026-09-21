import { useCallback, useEffect, useState } from 'react';
import { AuthChallenge } from './pages/AuthChallenge';
import { DeckEditor } from './pages/DeckEditor';
import { LibraryApp } from './pages/LibraryApp';
import { getJson } from './lib/api';
import { deckIdFromPath, useLocationPath } from './lib/router';
import './styles.css';

function App() {
  const [session, setSession] = useState(null);

  const refreshSession = useCallback(async () => {
    const nextSession = await getJson('/auth/session', 'Could not check your session');
    setSession(nextSession);
    return nextSession;
  }, []);

  useEffect(() => {
    refreshSession().catch(() => setSession({ authenticated: false }));
  }, [refreshSession]);

  async function signOut() {
    // Fire-and-forget: the client always drops to signed-out state locally,
    // regardless of whether the server call succeeds, so this bypasses the
    // JSON request helpers rather than surfacing an error nothing reads.
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
