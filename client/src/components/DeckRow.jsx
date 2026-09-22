import { useRef, useState } from 'react';
import { API_BASE } from '../lib/api';
import { cardColors } from '../lib/cardFormatting';
import { CardTable } from './CardTable';

const SIGNATURE_PREVIEW_WIDTH = 200;
const PREVIEW_MARGIN = 12;

export function DeckRow({ deck, folders, onDelete, onUpdate, onMoveToFolder }) {
  const [expanded, setExpanded] = useState(false);
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(deck.name);
  const [showSignature, setShowSignature] = useState(false);
  const titleRef = useRef(null);

  const cardCount = (deck.cards || [])
    .filter((card) => card.includedInDeck !== false)
    .reduce((total, card) => total + card.quantity, 0)
    + (deck.signatureCards || [])
      .filter((c) => c.includedInDeck !== false)
      .reduce((total, c) => total + c.quantity, 0);
  const colors = [...new Set((deck.cards || [])
    .filter((card) => card.includedInDeck !== false).flatMap(cardColors))];
  const signatures = deck.signatureCards || [];

  async function save(event) {
    event.preventDefault();
    if (!name.trim()) return;
    await onUpdate(deck, name.trim(), deck.description);
    setEditing(false);
  }

  function positionSignature() {
    if (!titleRef.current) return null;
    const rect = titleRef.current.getBoundingClientRect();
    const maxLeft = Math.max(window.innerWidth - SIGNATURE_PREVIEW_WIDTH - PREVIEW_MARGIN,
      PREVIEW_MARGIN);
    const left = Math.min(Math.max(rect.left, PREVIEW_MARGIN), maxLeft);
    const top = rect.bottom + PREVIEW_MARGIN;
    return { top, left };
  }

  const signatureStyle = showSignature ? positionSignature() : null;

  return (
    <article className="deck-card">
      <div className="deck-summary">
        <button className="expand-button" onClick={() => setExpanded(!expanded)}
          aria-label={expanded ? 'Collapse card list' : 'Expand card list'}>
          {expanded ? '\u2212' : '+'}
        </button>
        <div className="deck-title-block"
          ref={titleRef}
          onMouseEnter={() => signatures.length && setShowSignature(true)}
          onMouseLeave={() => setShowSignature(false)}>
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
              <span>
                {deck.folderName} · {cardCount} cards
                {deck.source && <em className="deck-source"> · {deck.source}</em>}
                {signatures.length > 0 && <em className="deck-signature-hint"> · {signatures.length} signature</em>}
              </span>
            </>
          )}
          {showSignature && signatureStyle && signatures.length > 0 && (
            <div className="signature-preview" role="tooltip"
              style={{ top: `${signatureStyle.top}px`, left: `${signatureStyle.left}px` }}>
              <strong>Signature cards</strong>
              <div className="signature-preview-grid">
                {signatures.map((card) => (
                  <div className="signature-preview-item" key={card.scryfallId || card.name}>
                    {card.scryfallId ? (
                      <img src={`${API_BASE}/cards/${encodeURIComponent(card.scryfallId)}/image`}
                        alt={card.name} loading="lazy" />
                    ) : (
                      <span className="placeholder">{card.name}</span>
                    )}
                    <small>{card.name}</small>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
        <div className="color-pips" aria-label={`Colors: ${colors.join(', ')}`}>
          {colors.map((color) => <span key={color} className={`pip ${color.toLowerCase()}`}
            title={color}>{color.slice(0, 1)}</span>)}
        </div>
        <div className="row-actions">
          <a className="edit-link" href={`/ui/decks/${encodeURIComponent(deck.id)}`}>Edit</a>
          <button onClick={() => setEditing(true)}>Rename</button>
          {onMoveToFolder && (
            <select className="move-folder-select" value={deck.folderRef || ''}
              aria-label="Move deck to folder"
              onChange={(event) => onMoveToFolder(deck, event.target.value || null)}>
              <option value="">Library root</option>
              {(folders || []).map((folder) => (
                <option key={folder.id} value={folder.id}>{folder.name}</option>
              ))}
            </select>
          )}
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
