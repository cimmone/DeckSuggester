import { useEffect, useState } from 'react';

export function RecommendationsDialog({ deck, securedFetch, onClose }) {
  const [state, setState] = useState({ loading: true, error: null, items: [] });

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const response = await securedFetch(`/decks/${encodeURIComponent(deck.id)}/recommendations`);
        if (!response.ok) throw new Error('Could not load recommendations');
        const items = await response.json();
        if (!cancelled) setState({ loading: false, error: null, items });
      } catch (error) {
        if (!cancelled) setState({ loading: false, error: error.message, items: [] });
      }
    })();
    return () => { cancelled = true; };
  }, [deck.id, securedFetch]);

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="modal recommendations" onClick={(event) => event.stopPropagation()}>
        <div className="modal-heading">
          <div>
            <p className="eyebrow">Commander Spellbook</p>
            <h2>Suggested combo pieces</h2>
          </div>
          <button className="close-button" onClick={onClose} aria-label="Close">×</button>
        </div>
        {state.loading && <div className="empty-state">Assembling recommendations…</div>}
        {state.error && <div className="notice error"><span>{state.error}</span></div>}
        {!state.loading && !state.error && state.items.length === 0 && (
          <div className="empty-state">No combo recommendations were found for this deck.</div>
        )}
        <div className="recommendation-list">
          {state.items.map((rec) => (
            <div className="recommendation" key={rec.comboId}>
              <div className="recommendation-summary">
                <strong>{rec.ownedPieces}/{rec.requiredPieces} pieces owned</strong>
                <span>{rec.description}</span>
              </div>
              {rec.newCards.map((card) => (
                <div className="recommendation-card" key={`${rec.comboId}-${card.scryfallId || card.name}`}>
                  <div className="recommendation-image">
                    {card.scryfallId ? (
                      <img src={`/cards/${encodeURIComponent(card.scryfallId)}/image`}
                        alt={card.name} loading="lazy" />
                    ) : (
                      <div className="recommendation-image placeholder">{card.name}</div>
                    )}
                    <span className="add-overlay" aria-hidden="true">+</span>
                  </div>
                  <div className="recommendation-detail">
                    <strong>{card.name}</strong>
                    {card.note && <small>{card.note}</small>}
                    <p>{rec.explanation}</p>
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
