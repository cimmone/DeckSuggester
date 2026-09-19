import { useCallback, useEffect, useRef, useState } from 'react';
import { AddCardBox } from '../components/AddCardBox';
import { AppFooter } from '../components/AppFooter';
import { CardTable } from '../components/CardTable';
import { DeckHistoryPanel } from '../components/DeckHistoryPanel';
import { RecommendationsDialog } from '../components/RecommendationsDialog';
import { Toasts } from '../components/Toasts';
import { navigate } from '../lib/router';

export function DeckEditor({ session, deckId, onSignOut, onUnauthenticated }) {
  const [deck, setDeck] = useState(null);
  const [notice, setNotice] = useState(null);
  const [busy, setBusy] = useState(true);
  const [showRecommendations, setShowRecommendations] = useState(false);
  const [showHistory, setShowHistory] = useState(false);
  const [historyKey, setHistoryKey] = useState(0);
  const [toasts, setToasts] = useState([]);
  const toastId = useRef(0);

  const dismissToast = useCallback((id) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const pushToast = useCallback((text, type = 'error') => {
    const id = ++toastId.current;
    setToasts((current) => [...current, { id, text, type }]);
    setTimeout(() => dismissToast(id), 6000);
  }, [dismissToast]);

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

  // Runs a card mutation against an arbitrary deck. Returns the updated deck on
  // success, or throws with the server's message on failure. When the mutation
  // targets the deck currently open, its state is refreshed in place.
  const mutateDeck = useCallback(async (targetDeckId, method, name) => {
    const response = await securedFetch(`/decks/${encodeURIComponent(targetDeckId)}/cards`, {
      method, headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name }),
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || 'The change could not be saved.');
    if (targetDeckId === deckId) setDeck(body);
    return body;
  }, [deckId, securedFetch]);

  async function mutate(method, name, successText) {
    try {
      await mutateDeck(deckId, method, name);
      setNotice({ type: 'success', text: successText });
      setHistoryKey((key) => key + 1);
    } catch (error) {
      setNotice({ type: 'error', text: error.message });
    }
  }

  const addCard = (name) => mutate('POST', name, `Added ${name}.`);
  const removeCard = (card) => mutate('DELETE', card.scryfallId || card.name,
    `Removed one ${card.name}.`);

  // Reverses a recorded edit. Undoing an add removes one copy; undoing a remove
  // adds one back. If the card can no longer be removed (it is no longer in the
  // deck), the failure is surfaced as a corner toast.
  const undoEntry = useCallback(async (entry) => {
    try {
      if (entry.action === 'ADD_CARD') {
        await mutateDeck(entry.deckId, 'DELETE', entry.scryfallId || entry.cardName);
        pushToast(`Undid adding ${entry.cardName}.`, 'success');
      } else if (entry.action === 'REMOVE_CARD') {
        await mutateDeck(entry.deckId, 'POST', entry.cardName);
        pushToast(`Restored ${entry.cardName}.`, 'success');
      }
      setHistoryKey((key) => key + 1);
    } catch (error) {
      pushToast(`Couldn't undo "${entry.cardName}": ${error.message}`, 'error');
    }
  }, [mutateDeck, pushToast]);

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
                <button onClick={() => setShowHistory(true)}>Edit history</button>
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
      {showHistory && (
        <DeckHistoryPanel securedFetch={securedFetch} refreshKey={historyKey}
          onUndo={undoEntry} onClose={() => setShowHistory(false)} />
      )}
      <Toasts toasts={toasts} onDismiss={dismissToast} />
      <AppFooter />
    </div>
  );
}
