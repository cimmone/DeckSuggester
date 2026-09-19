import { useEffect, useRef, useState } from 'react';

export function AddCardBox({ securedFetch, onAdd }) {
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [open, setOpen] = useState(false);
  const [highlight, setHighlight] = useState(-1);
  const boxRef = useRef(null);

  useEffect(() => {
    if (query.trim().length < 2) {
      setSuggestions([]);
      setHighlight(-1);
      return undefined;
    }
    let cancelled = false;
    const handle = setTimeout(async () => {
      try {
        const response = await securedFetch(`/decks/card-search?q=${encodeURIComponent(query.trim())}`);
        if (!response.ok) return;
        const results = await response.json();
        if (!cancelled) {
          setSuggestions(results);
          setOpen(true);
          setHighlight(results.length ? 0 : -1);
        }
      } catch { /* ignore search errors */ }
    }, 200);
    return () => { cancelled = true; clearTimeout(handle); };
  }, [query, securedFetch]);

  useEffect(() => {
    function onClickAway(event) {
      if (boxRef.current && !boxRef.current.contains(event.target)) setOpen(false);
    }
    document.addEventListener('mousedown', onClickAway);
    return () => document.removeEventListener('mousedown', onClickAway);
  }, []);

  async function addSuggestion(card) {
    if (!card) return;
    await onAdd(card.name);
    setQuery('');
    setSuggestions([]);
    setOpen(false);
    setHighlight(-1);
  }

  // Only real, matched cards can be added: submit the highlighted suggestion,
  // or an exact (case-insensitive) name match if the user typed a full name.
  function submit() {
    if (!suggestions.length) return;
    const exact = suggestions.find(
      (card) => card.name.toLowerCase() === query.trim().toLowerCase());
    addSuggestion(exact || suggestions[Math.max(highlight, 0)]);
  }

  function onKeyDown(event) {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setOpen(true);
      setHighlight((current) => Math.min(current + 1, suggestions.length - 1));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setHighlight((current) => Math.max(current - 1, 0));
    } else if (event.key === 'Enter') {
      event.preventDefault();
      submit();
    } else if (event.key === 'Escape') {
      setOpen(false);
    }
  }

  return (
    <div className="add-card-box" ref={boxRef}>
      <div className="input-row">
        <div className="autocomplete">
          <input value={query} placeholder="Add a card by name…"
            aria-label="Card name" autoComplete="off"
            role="combobox" aria-expanded={open && suggestions.length > 0}
            aria-controls="card-suggestions"
            onChange={(event) => setQuery(event.target.value)}
            onFocus={() => suggestions.length && setOpen(true)}
            onKeyDown={onKeyDown} />
          {open && suggestions.length > 0 && (
            <ul className="suggestions" role="listbox" id="card-suggestions">
              {suggestions.map((card, index) => (
                <li key={card.scryfallId || card.name} role="option"
                  aria-selected={index === highlight}>
                  <button type="button"
                    className={index === highlight ? 'highlighted' : undefined}
                    onMouseEnter={() => setHighlight(index)}
                    onClick={() => addSuggestion(card)}>
                    <strong>{card.name}</strong>
                    <small>{card.typeLine}</small>
                  </button>
                </li>
              ))}
            </ul>
          )}
          {open && query.trim().length >= 2 && suggestions.length === 0 && (
            <ul className="suggestions" role="listbox" id="card-suggestions">
              <li className="suggestion-empty">No matching cards found</li>
            </ul>
          )}
        </div>
        <button className="primary-button" onClick={submit}
          disabled={!suggestions.length}>Add</button>
      </div>
    </div>
  );
}
