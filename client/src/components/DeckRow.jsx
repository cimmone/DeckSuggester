import { useState } from 'react';
import { cardColors } from '../lib/cardFormatting';
import { CardTable } from './CardTable';

export function DeckRow({ deck, onDelete, onUpdate }) {
  const [expanded, setExpanded] = useState(false);
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(deck.name);
  const cardCount = (deck.cards || [])
    .filter((card) => card.includedInDeck !== false)
    .reduce((total, card) => total + card.quantity, 0);
  const colors = [...new Set((deck.cards || [])
    .filter((card) => card.includedInDeck !== false).flatMap(cardColors))];

  async function save(event) {
    event.preventDefault();
    if (!name.trim()) return;
    await onUpdate(deck.id, name.trim(), deck.description);
    setEditing(false);
  }

  return (
    <article className="deck-card">
      <div className="deck-summary">
        <button className="expand-button" onClick={() => setExpanded(!expanded)}
          aria-label={expanded ? 'Collapse card list' : 'Expand card list'}>
          {expanded ? '−' : '+'}
        </button>
        <div className="deck-title-block">
          {editing ? (
            <form className="edit-form" onSubmit={save}>
              <input value={name} onChange={(event) => setName(event.target.value)}
                aria-label="Deck name" autoFocus />
              <button type="submit" className="small-button primary">Save</button>
              <button type="button" className="small-button" onClick={() => {
                setName(deck.name); setEditing(false);
              }}>Cancel</button>
            </form>
          ) : (
            <>
              <a href={deck.sourceUrl} target="_blank" rel="noreferrer">{deck.name}</a>
              <span>{deck.folderName} · {cardCount} cards</span>
            </>
          )}
        </div>
        <div className="color-pips" aria-label={`Colors: ${colors.join(', ')}`}>
          {colors.map((color) => <span key={color} className={`pip ${color.toLowerCase()}`}
            title={color}>{color.slice(0, 1)}</span>)}
        </div>
        <div className="row-actions">
          <a className="edit-link" href={`/ui/decks/${encodeURIComponent(deck.id)}`}>Edit</a>
          <button onClick={() => setEditing(true)}>Rename</button>
          <button className="danger-text" onClick={() => onDelete(deck)}>Delete</button>
        </div>
      </div>
      {expanded && (
        <div className="card-list">
          <CardTable cards={deck.cards || []} />
        </div>
      )}
    </article>
  );
}
