import { useEffect, useState } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { API_BASE } from '../lib/api';

const FILTERS = [
  { value: 'owned', label: 'Already in deck' },
  { value: 'partial', label: 'Partially in deck' },
  { value: 'legal', label: 'Legal for deck' },
];

export function RecommendationsDialog({ deck, securedFetch, onClose, onAddCard }) {
  const [filter, setFilter] = useState('legal');
  const [state, setState] = useState({ busy: true, error: null, items: [] });
  const [adding, setAdding] = useState(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const url = `${API_BASE}/decks/${encodeURIComponent(deck.id)}/recommendations?filter=${filter}`;
        const response = await securedFetch(url);
        if (!response.ok) throw new Error('Could not load recommendations');
        const items = await response.json();
        if (!cancelled) setState({ busy: false, error: null, items });
      } catch (error) {
        if (!cancelled) setState({ busy: false, error: error.message, items: [] });
      }
    })();
    return () => { cancelled = true; };
  }, [deck.id, filter, securedFetch]);

  async function addCard(card) {
    if (!onAddCard || !card || !card.name) return;
    setAdding(card.scryfallId || card.name);
    try {
      await onAddCard(card.name);
    } finally {
      setAdding(null);
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="modal recommendations" onClick={(event) => event.stopPropagation()}>
        <div className="modal-heading">
          <div>
            <p className="eyebrow">Commander Spellbook</p>
            <h2>Suggested combo pieces</h2>
          </div>
          <button className="close-button" onClick={onClose} aria-label="Close">&times;</button>
        </div>

        <fieldset className="recommendation-filters">
          <legend>Show combos</legend>
          {FILTERS.map((option) => (
            <label key={option.value} className={filter === option.value ? 'checked' : ''}>
              <input type="radio" name="combo-filter" value={option.value}
                checked={filter === option.value}
                onChange={() => setFilter(option.value)} />
              {option.label}
            </label>
          ))}
        </fieldset>

        {state.busy && <div className="empty-state">Assembling recommendations&hellip;</div>}
        {state.error && <div className="notice error"><span>{state.error}</span></div>}
        {!state.busy && !state.error && state.items.length === 0 && (
          <div className="empty-state">No combo recommendations were found for this deck.</div>
        )}

        <div className="recommendation-list">
          {state.items.map((rec) => (
            <div className="recommendation" key={rec.comboId}>
              <div className="recommendation-summary">
                <strong>{rec.ownedPieces}/{rec.requiredPieces} pieces owned</strong>
                <span>{rec.description}</span>
              </div>
              {/* The combo explanation is emitted once per combo, not once per
                  suggested card, so it is not duplicated across the card list. */}
              {rec.explanation && <p className="recommendation-explanation">{rec.explanation}</p>}
              {rec.newCards.map((card) => (
                <div className="recommendation-card" key={`${rec.comboId}-${card.scryfallId || card.name}`}>
                  <button type="button" className="recommendation-image"
                    title={`Add ${card.name} to the deck`}
                    disabled={adding === (card.scryfallId || card.name)}
                    onClick={() => addCard(card)}>
                    {card.scryfallId ? (
                      <img src={`${API_BASE}/cards/${encodeURIComponent(card.scryfallId)}/image`}
                        alt={card.name} loading="lazy" />
                    ) : (
                      <span className="placeholder">{card.name}</span>
                    )}
                    <span className="add-overlay" aria-hidden="true">
                      <FontAwesomeIcon icon={faPlus} />
                    </span>
                  </button>
                  <div className="recommendation-detail">
                    <strong>{card.name}</strong>
                    {card.note && <small>{card.note}</small>}
                  </div>
                </div>
              ))}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
