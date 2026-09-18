import { useEffect, useState } from 'react';

export function AddCardBox({ securedFetch, onAdd }) {
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (query.trim().length < 2) {
      setSuggestions([]);
      return undefined;
    }
    let cancelled = false;
    const handle = setTimeout(async () => {
      try {
        const response = await securedFetch(`/decks/card-search?q=${encodeURIComponent(query.trim())}`);
        if (!response.ok) return;
        const results = await response.json();
        if (!cancelled) { setSuggestions(results); setOpen(true); }
      } catch { /* ignore search errors */ }
    }, 200);
    return () => { cancelled = true; clearTimeout(handle); };
  }, [query, securedFetch]);

  async function submit(name) {
    const cardName = (name || query).trim();
    if (!cardName) return;
    await onAdd(cardName);
    setQuery('');
    setSuggestions([]);
    setOpen(false);
  }

  return (
    <div className="add-card-box">
      <div className="input-row">
        <div className="autocomplete">
          <input value={query} placeholder="Add a card by name…"
            aria-label="Card name"
            onChange={(event) => setQuery(event.target.value)}
            onFocus={() => suggestions.length && setOpen(true)}
            onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); submit(); } }} />
          {open && suggestions.length > 0 && (
            <ul className="suggestions" role="listbox">
              {suggestions.map((card) => (
                <li key={card.scryfallId || card.name}>
                  <button type="button" onClick={() => submit(card.name)}>
                    <strong>{card.name}</strong>
                    <small>{card.typeLine}</small>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
        <button className="primary-button" onClick={() => submit()}>Add</button>
      </div>
    </div>
  );
}
