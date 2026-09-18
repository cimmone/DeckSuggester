import { useCallback, useEffect, useState } from 'react';
import { AddCardBox } from '../components/AddCardBox';
import { AppFooter } from '../components/AppFooter';
import { CardTable } from '../components/CardTable';
import { RecommendationsDialog } from '../components/RecommendationsDialog';
import { navigate } from '../lib/router';

export function DeckEditor({ session, deckId, onSignOut, onUnauthenticated }) {
  const [deck, setDeck] = useState(null);
  const [notice, setNotice] = useState(null);
  const [busy, setBusy] = useState(true);
  const [showRecommendations, setShowRecommendations] = useState(false);

  const securedFetch = useCallback(async (url, options = {}) => {
    const headers = new Headers(options.headers || {});
    if (options.method && options.method !== 'GET') {
      headers.set(session.csrfHeaderName, session.csrfToken);
    }
    const response = await fetch(url, { ...options, headers });
    if (response.status === 401) onUnauthenticated();
    return response;
  }, [onUnauthenticated, session.csrfHeaderName, session.csrfToken]);

  const loadDeck = useCallback(async () => {
    const response = await securedFetch(`/decks/${encodeURIComponent(deckId)}`);
    if (response.status === 404) throw new Error('That deck could not be found.');
    if (!response.ok) throw new Error('Could not load the deck.');
    setDeck(await response.json());
  }, [deckId, securedFetch]);

  useEffect(() => {
    loadDeck().catch((error) => setNotice({ type: 'error', text: error.message }))
      .finally(() => setBusy(false));
  }, [loadDeck]);

  async function mutate(method, name, successText) {
    try {
      const response = await securedFetch(`/decks/${encodeURIComponent(deckId)}/cards`, {
        method, headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name }),
      });
      const body = await response.json();
      if (!response.ok) throw new Error(body.message || 'The change could not be saved.');
      setDeck(body);
      setNotice({ type: 'success', text: successText });
    } catch (error) {
      setNotice({ type: 'error', text: error.message });
    }
  }

  const addCard = (name) => mutate('POST', name, `Added ${name}.`);
  const removeCard = (card) => mutate('DELETE', card.scryfallId || card.name,
    `Removed one ${card.name}.`);

  const cardCount = (deck?.cards || [])
    .filter((card) => card.includedInDeck !== false)
    .reduce((total, card) => total + card.quantity, 0);

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="/ui/" onClick={(event) => { event.preventDefault(); navigate('/ui/'); }}>
          <span className="brand-mark">DS</span>
          <span>DeckSuggester <small>Deck editor</small></span>
        </a>
        <div className="account-status">
          <button onClick={() => navigate('/ui/')}>← Back to library</button>
          <span>{session.username} · {session.libraryId}</span>
          <button onClick={onSignOut}>Sign out</button>
        </div>
      </header>

      <main>
        {notice && <div className={`notice ${notice.type}`}>
          <span>{notice.text}</span><button onClick={() => setNotice(null)}>×</button>
        </div>}

        {busy && !deck ? <div className="empty-state">Loading deck…</div> : deck && (
          <>
            <section className="editor-heading">
              <div>
                <p className="eyebrow">{deck.folderName}</p>
                <h1>{deck.name}</h1>
                <span>{cardCount} cards · {(deck.colorIdentity || []).join(', ') || 'Colorless'}</span>
              </div>
              <div className="editor-actions">
                <button className="primary-button" onClick={() => setShowRecommendations(true)}>
                  Suggest combos
                </button>
              </div>
            </section>

            <section className="editor-add">
              <p className="eyebrow">Add cards</p>
              <AddCardBox securedFetch={securedFetch} onAdd={addCard} />
            </section>

            <section className="library-section">
              <div className="library-heading">
                <div><p className="eyebrow">Deck list</p><h2>Cards</h2></div>
              </div>
              <div className="card-list editor-card-list">
                <CardTable cards={deck.cards || []} editable
                  onAdd={(card) => addCard(card.name)} onRemove={removeCard} />
              </div>
            </section>
          </>
        )}
      </main>

      {showRecommendations && deck && (
        <RecommendationsDialog deck={deck} securedFetch={securedFetch}
          onClose={() => setShowRecommendations(false)} />
      )}
      <AppFooter />
    </div>
  );
}
