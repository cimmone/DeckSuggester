import { useCallback, useEffect, useState } from 'react';

const ACTION_LABELS = {
  ADD_CARD: 'Added',
  REMOVE_CARD: 'Removed',
  IMPORT: 'Imported',
  RENAME: 'Renamed',
  DELETE: 'Deleted',
};

function describe(entry) {
  const label = ACTION_LABELS[entry.action] || entry.action;
  if (entry.action === 'ADD_CARD' || entry.action === 'REMOVE_CARD') {
    return `${label} ${entry.cardName}`;
  }
  if (entry.action === 'IMPORT') return `${label} ${entry.deckName}`;
  return `${label} ${entry.deckName || ''}`.trim();
}

// Only card add/remove actions can be reversed by a single opposite mutation.
function isUndoable(entry) {
  return entry.action === 'ADD_CARD' || entry.action === 'REMOVE_CARD';
}

export function DeckHistoryPanel({ securedFetch, onClose, onUndo, refreshKey }) {
  const [state, setState] = useState({ loading: true, error: null, entries: [] });
  const [undoing, setUndoing] = useState(null);

  const load = useCallback(async () => {
    setState((current) => ({ ...current, loading: true }));
    try {
      const response = await securedFetch('/decks/history');
      if (!response.ok) throw new Error('Could not load the deck history.');
      const entries = await response.json();
      setState({ loading: false, error: null, entries });
    } catch (error) {
      setState({ loading: false, error: error.message, entries: [] });
    }
  }, [securedFetch]);

  useEffect(() => { load(); }, [load, refreshKey]);

  async function undo(entry, index) {
    setUndoing(index);
    try {
      await onUndo(entry);
      await load();
    } finally {
      setUndoing(null);
    }
  }

  return (
    <aside className="history-panel" aria-label="Deck edit history">
      <div className="history-heading">
        <div>
          <p className="eyebrow">Rolling history</p>
          <h2>Recent deck edits</h2>
        </div>
        <button className="close-button" onClick={onClose} aria-label="Close history">×</button>
      </div>
      {state.loading && <div className="empty-state">Loading history…</div>}
      {state.error && <div className="notice error"><span>{state.error}</span></div>}
      {!state.loading && !state.error && state.entries.length === 0 && (
        <div className="empty-state">No deck edits recorded yet.</div>
      )}
      <ol className="history-list">
        {state.entries.map((entry, index) => (
          <li key={`${entry.occurredAt}-${index}`} className="history-entry">
            <div className="history-entry-body">
              <strong>{describe(entry)}</strong>
              <small>
                {entry.deckName}
                {entry.occurredAt ? ` · ${new Date(entry.occurredAt).toLocaleString()}` : ''}
              </small>
            </div>
            {isUndoable(entry) && (
              <button type="button" className="small-button" disabled={undoing === index}
                onClick={() => undo(entry, index)}>
                {undoing === index ? 'Undoing…' : 'Undo'}
              </button>
            )}
          </li>
        ))}
      </ol>
    </aside>
  );
}
