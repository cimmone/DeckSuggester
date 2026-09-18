export function AppFooter() {
  return (
    <footer className="app-footer">
      <span>DeckSuggester · Scryfall-backed collection intelligence</span>
      <span className="credits">
        Data lovingly sourced from{' '}
        <a href="https://archidekt.com" target="_blank" rel="noreferrer">Archidekt</a>,{' '}
        <a href="https://scryfall.com" target="_blank" rel="noreferrer">Scryfall</a>, and{' '}
        <a href="https://commanderspellbook.com" target="_blank" rel="noreferrer">Commander Spellbook</a>
        {' '}— thank you for being great sources of data.
      </span>
    </footer>
  );
}
