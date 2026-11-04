import { useEffect, useMemo, useState } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { API_BASE } from '../lib/api';
import { cardId, groupRecommendations } from '../lib/recommendationGroups';

const FILTERS = [
  { value: 'owned', label: 'Already in deck' },
  { value: 'partial', label: 'Partially in deck' },
  { value: 'legal', label: 'Legal for deck' },
];

export function RecommendationsDialog({ deck, securedFetch, onClose, onAddCard }) {
  const [filter, setFilter] = useState('legal');
  const [state, setState] = useState({ busy: true, error: null, items: [] });
  const [adding, setAdding] = useState(null);
  const [expandedGroups, setExpandedGroups] = useState(() => new Set());
  const groups = useMemo(() => groupRecommendations(state.items, filter), [state.items, filter]);

  function toggleGroup(key) {
    setExpandedGroups((current) => {
      const next = new Set(current);
      if (!next.delete(key)) next.add(key);
      return next;
    });
  }

  useEffect(() => {
    let cancelled = false;
    setState({ busy: true, error: null, items: [] });
    setExpandedGroups(new Set());
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
    setAdding(cardId(card));
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

        {state.busy && (
          <div className="empty-state loading-state" role="status" aria-live="polite">
            <span className="spinner" aria-hidden="true" />
            <strong>Assembling combos&hellip;</strong>
            <small>Checking Commander Spellbook against your deck. The first load can take a moment; repeat loads are faster.</small>
          </div>
        )}
        {state.error && <div className="notice error"><span>{state.error}</span></div>}
        {!state.busy && !state.error && state.items.length === 0 && (
          <div className="empty-state">No combo recommendations were found for this deck.</div>
        )}

        <div className="recommendation-list">
          {groups.map((group) => {
            const [lead, ...others] = group.members;
            const expanded = expandedGroups.has(group.key);
            return (
              <div className="recommendation-group" key={group.key}>
                <div className="recommendation">
                  <RecommendationHeader rec={lead} />
                  {lead.newCards.map((card) => (
                    <CardRow key={`${lead.comboId}-${cardId(card)}`} card={card}
                      adding={adding} onAdd={addCard} />
                  ))}
                  {others.length > 0 && (
                    <button type="button" className="duplicate-toggle" aria-expanded={expanded}
                      onClick={() => toggleGroup(group.key)}>
                      {expanded ? 'Hide' : 'Show'} {others.length} duplicate combo{others.length === 1 ? '' : 's'} detected
                    </button>
                  )}
                </div>
                {expanded && others.map((rec) => (
                  <div className="recommendation duplicate" key={rec.comboId}>
                    <RecommendationHeader rec={rec} />
                    {rec.newCards.map((card) => (
                      <CardRow key={`${rec.comboId}-${cardId(card)}`} card={card}
                        adding={adding} onAdd={addCard} />
                    ))}
                  </div>
                ))}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

function RecommendationHeader({ rec }) {
  return (
    <>
      <div className="recommendation-summary">
        <strong>{rec.ownedPieces}/{rec.requiredPieces} pieces owned</strong>
        <span>{rec.description}</span>
      </div>
      {/* The combo explanation is emitted once per combo, not once per
          suggested card, so it is not duplicated across the card list. */}
      {rec.explanation && <p className="recommendation-explanation">{rec.explanation}</p>}
    </>
  );
}

function CardImage({ card }) {
  return card.scryfallId ? (
    <img src={`${API_BASE}/cards/${encodeURIComponent(card.scryfallId)}/image`}
      alt={card.name} loading="lazy" />
  ) : (
    <span className="placeholder">{card.name}</span>
  );
}

function CardRow({ card, adding, onAdd }) {
  return (
    <div className="recommendation-card">
      <button type="button" className="recommendation-image"
        title={`Add ${card.name} to the deck`}
        disabled={adding === cardId(card)}
        onClick={() => onAdd(card)}>
        <CardImage card={card} />
        <span className="add-overlay" aria-hidden="true">
          <FontAwesomeIcon icon={faPlus} />
        </span>
      </button>
      <div className="recommendation-detail">
        <strong>{card.name}</strong>
        {card.note && <small>{card.note}</small>}
      </div>
    </div>
  );
}
