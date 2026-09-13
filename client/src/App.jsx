import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Highcharts from 'highcharts';
import './styles.css';

const METRICS = {
  cardColors: { label: 'Card colors', empty: 'No color data' },
  deckColors: { label: 'Decks by color', empty: 'No deck color data' },
  manaValues: { label: 'Mana values', empty: 'No mana value data' },
  cardTypes: { label: 'Card types', empty: 'No card type data' },
};

const COLOR_PALETTE = {
  White: '#f1e4bd', Blue: '#58a6d8', Black: '#665f70', Red: '#df684d',
  Green: '#5c9b78', Colorless: '#a9b2b7',
};

const KNOWN_TYPES = new Set([
  'Artifact', 'Battle', 'Conspiracy', 'Creature', 'Dungeon', 'Enchantment',
  'Instant', 'Kindred', 'Land', 'Phenomenon', 'Plane', 'Planeswalker',
  'Scheme', 'Sorcery', 'Vanguard',
]);

function formatManaValue(value) {
  return Number(value).toString();
}

function cardColors(card) {
  return card.colors?.length ? card.colors : ['Colorless'];
}

function cardTypes(card) {
  const words = (card.typeLine || '').split(/[—-]/, 1)[0].trim().split(/\s+/);
  const found = words.filter((word) => KNOWN_TYPES.has(word));
  return found.length ? found : [card.typeLine ? 'Other' : 'Unknown'];
}

function deckMatchesFilter(deck, filter) {
  const cards = (deck.cards || []).filter((card) => card.includedInDeck !== false);
  switch (filter.metric) {
    case 'manaValues':
      return cards.some((card) => formatManaValue(card.manaValue) === filter.value);
    case 'cardColors':
    case 'deckColors':
      return cards.some((card) => cardColors(card).includes(filter.value));
    case 'cardTypes':
      return cards.some((card) => cardTypes(card).includes(filter.value));
    default:
      return true;
  }
}

function PieChart({ metric, values, onSelect }) {
  const chartElement = useRef(null);

  useEffect(() => {
    if (!chartElement.current || !Object.keys(values || {}).length) return undefined;
    const chart = Highcharts.chart(chartElement.current, {
      chart: {
        type: 'pie', backgroundColor: 'transparent', height: 330,
        style: { fontFamily: 'Inter, ui-sans-serif, system-ui, sans-serif' },
      },
      title: { text: undefined },
      credits: { enabled: false },
      accessibility: { enabled: false },
      tooltip: {
        pointFormat: '<b>{point.y}</b> ({point.percentage:.1f}%)',
        backgroundColor: '#15231f', borderWidth: 0, borderRadius: 10,
        style: { color: '#f8f5ec' },
      },
      legend: {
        align: 'center', verticalAlign: 'bottom',
        itemStyle: { color: '#52605a', fontSize: '12px', fontWeight: '500' },
        itemHoverStyle: { color: '#16251f' },
      },
      plotOptions: {
        pie: {
          allowPointSelect: true, cursor: 'pointer', borderColor: '#fbfaf5',
          borderWidth: 3, innerSize: '56%', showInLegend: true,
          dataLabels: { enabled: false },
          point: { events: { click() { onSelect(metric, this.name); } } },
        },
      },
      series: [{
        name: METRICS[metric].label,
        colorByPoint: true,
        data: Object.entries(values).map(([name, value]) => ({
          name, y: value, color: COLOR_PALETTE[name],
        })),
      }],
    });
    return () => chart.destroy();
  }, [metric, values, onSelect]);

  if (!Object.keys(values || {}).length) {
    return <div className="chart-empty">{METRICS[metric].empty}</div>;
  }
  return <div ref={chartElement} aria-label={`${METRICS[metric].label} pie chart`} />;
}

function DeckRow({ deck, onDelete, onUpdate }) {
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
          <button onClick={() => setEditing(true)}>Rename</button>
          <button className="danger-text" onClick={() => onDelete(deck)}>Delete</button>
        </div>
      </div>
      {expanded && (
        <div className="card-list">
          {(deck.cards || []).map((card, index) => (
            <div className="card-line" key={`${card.scryfallId || card.name}-${index}`}>
              <span className="quantity">{card.quantity}×</span>
              <strong>{card.name}</strong>
              <span>{card.typeLine || 'Unknown type'}</span>
              <span className="mana-value">MV {formatManaValue(card.manaValue)}</span>
              {!card.matchedScryfallCard && <span className="unmatched">Not in Scryfall</span>}
            </div>
          ))}
        </div>
      )}
    </article>
  );
}

function App() {
  const [decks, setDecks] = useState([]);
  const [stats, setStats] = useState(null);
  const [folderUrl, setFolderUrl] = useState('');
  const [folderFilter, setFolderFilter] = useState('all');
  const [metric, setMetric] = useState('cardColors');
  const [filters, setFilters] = useState([]);
  const [busy, setBusy] = useState(true);
  const [notice, setNotice] = useState(null);

  const loadData = useCallback(async () => {
    const [decksResponse, statsResponse] = await Promise.all([fetch('/decks'), fetch('/data')]);
    if (!decksResponse.ok || !statsResponse.ok) throw new Error('Could not load Mongo data');
    const [nextDecks, nextStats] = await Promise.all([
      decksResponse.json(), statsResponse.json(),
    ]);
    setDecks(nextDecks);
    setStats(nextStats);
  }, []);

  useEffect(() => {
    loadData().catch((error) => setNotice({ type: 'error', text: error.message }))
      .finally(() => setBusy(false));
  }, [loadData]);

  const toggleFilter = useCallback((selectedMetric, value) => {
    setFilters((current) => {
      const exists = current.some((item) => item.metric === selectedMetric && item.value === value);
      return exists
        ? current.filter((item) => item.metric !== selectedMetric || item.value !== value)
        : [...current, { metric: selectedMetric, value }];
    });
  }, []);

  const rootFolders = useMemo(() => {
    const names = new Map();
    decks.forEach((deck) => names.set(String(deck.rootFolderId),
      deck.rootFolderName || `Folder ${deck.rootFolderId}`));
    return [...names.entries()];
  }, [decks]);

  const filteredDecks = useMemo(() => decks.filter((deck) => (
    (folderFilter === 'all' || String(deck.rootFolderId) === folderFilter)
    && filters.every((filter) => deckMatchesFilter(deck, filter))
  )), [decks, filters, folderFilter]);

  const groupedDecks = useMemo(() => {
    const groups = new Map();
    filteredDecks.forEach((deck) => {
      const key = `${deck.folderId}:${deck.folderName}`;
      if (!groups.has(key)) groups.set(key, { key, name: deck.folderName, decks: [] });
      groups.get(key).decks.push(deck);
    });
    return [...groups.values()];
  }, [filteredDecks]);

  async function importFolder(event) {
    event.preventDefault();
    setBusy(true);
    setNotice({ type: 'info', text: 'Importing every nested folder and deck…' });
    try {
      const response = await fetch('/decks/', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url: folderUrl }),
      });
      const body = await response.json();
      if (!response.ok) throw new Error(body.message || 'Import failed');
      await loadData();
      setFolderUrl('');
      setNotice({ type: 'success', text: `Imported ${body.decksImported} decks from ${body.foldersVisited} folders.` });
    } catch (error) {
      setNotice({ type: 'error', text: error.message });
    } finally {
      setBusy(false);
    }
  }

  async function deleteDeck(deck) {
    if (!window.confirm(`Delete “${deck.name}” from Mongo?`)) return;
    const response = await fetch(`/decks/${deck.id}`, { method: 'DELETE' });
    if (response.ok) {
      await loadData();
      setNotice({ type: 'success', text: `Deleted ${deck.name}.` });
    }
  }

  async function updateDeck(id, name, description) {
    const response = await fetch(`/decks/${id}`, {
      method: 'PUT', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, description }),
    });
    if (!response.ok) throw new Error('Could not update the deck');
    await loadData();
    setNotice({ type: 'success', text: 'Deck updated.' });
  }

  async function deleteAll() {
    if (!window.confirm('Delete the entire decks collection? This cannot be undone.')) return;
    const response = await fetch('/decks', { method: 'DELETE' });
    if (response.ok) {
      setFilters([]);
      await loadData();
      setNotice({ type: 'success', text: 'All imported decks were deleted.' });
    }
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="/ui/">
          <span className="brand-mark">DS</span>
          <span>DeckSuggester <small>Library console</small></span>
        </a>
        <span className="database-status"><i /> Mongo · {stats?.totalDecks || 0} decks</span>
      </header>

      <main>
        <section className="hero">
          <div>
            <p className="eyebrow">Archidekt importer</p>
            <h1>Your collection,<br /><em>mapped.</em></h1>
            <p>Import an Archidekt folder and all of its nested decks. Cards are matched to
              the local Scryfall catalog automatically.</p>
          </div>
          <form className="import-panel" onSubmit={importFolder}>
            <label htmlFor="folder-url">Archidekt folder URL</label>
            <div className="input-row">
              <input id="folder-url" type="url" value={folderUrl} required
                placeholder="https://archidekt.com/folders/95630"
                onChange={(event) => setFolderUrl(event.target.value)} />
              <button className="primary-button" disabled={busy}>Import folder</button>
            </div>
            <span>Nested folders are discovered recursively. Existing decks are refreshed.</span>
          </form>
        </section>

        {notice && <div className={`notice ${notice.type}`}>
          <span>{notice.text}</span><button onClick={() => setNotice(null)}>×</button>
        </div>}

        <section className="stat-strip">
          <div><strong>{stats?.totalDecks || 0}</strong><span>Decks</span></div>
          <div><strong>{stats?.totalCards || 0}</strong><span>Cards</span></div>
          <div><strong>{Object.keys(stats?.cardColors || {}).length}</strong><span>Colors</span></div>
          <div><strong>{Object.keys(stats?.cardTypes || {}).length}</strong><span>Card types</span></div>
        </section>

        <section className="dashboard-grid">
          <div className="chart-panel">
            <div className="section-heading">
              <div><p className="eyebrow">Collection shape</p><h2>Explore the data</h2></div>
              <span>Click a slice to filter</span>
            </div>
            <div className="metric-tabs">
              {Object.entries(METRICS).map(([key, value]) => (
                <button key={key} className={metric === key ? 'active' : ''}
                  onClick={() => setMetric(key)}>{value.label}</button>
              ))}
            </div>
            <PieChart metric={metric} values={stats?.[metric] || {}} onSelect={toggleFilter} />
          </div>

          <aside className="filter-panel">
            <p className="eyebrow">Active filters</p>
            <h2>Refine results</h2>
            <p>Filters combine as an intersection. A deck must match every selected slice.</p>
            <div className="filter-chips">
              {filters.length === 0 && <span className="empty-filter">No filters selected</span>}
              {filters.map((filter) => (
                <button key={`${filter.metric}-${filter.value}`}
                  onClick={() => toggleFilter(filter.metric, filter.value)}>
                  <small>{METRICS[filter.metric].label}</small>{filter.value}<b>×</b>
                </button>
              ))}
            </div>
            {filters.length > 0 && <button className="clear-button"
              onClick={() => setFilters([])}>Clear all filters</button>}
            <div className="result-count"><strong>{filteredDecks.length}</strong>
              <span>matching decks</span></div>
          </aside>
        </section>

        <section className="library-section">
          <div className="library-heading">
            <div><p className="eyebrow">Mongo library</p><h2>Imported decks</h2></div>
            <div className="library-actions">
              <select value={folderFilter} onChange={(event) => setFolderFilter(event.target.value)}
                aria-label="Filter by imported root folder">
                <option value="all">All imported folders</option>
                {rootFolders.map(([id, label]) => <option key={id} value={id}>{label}</option>)}
              </select>
              <button className="danger-button" disabled={!decks.length} onClick={deleteAll}>
                Delete collection
              </button>
            </div>
          </div>

          {busy && !decks.length ? <div className="empty-state">Loading your library…</div>
            : groupedDecks.length === 0 ? (
              <div className="empty-state">
                <strong>{decks.length ? 'No decks match these filters.' : 'No decks imported yet.'}</strong>
                <span>{decks.length ? 'Remove a filter to widen the results.'
                  : 'Paste an Archidekt folder URL above to get started.'}</span>
              </div>
            ) : groupedDecks.map((group) => (
              <div className="folder-group" key={group.key}>
                <div className="folder-label"><span>⌄</span><strong>{group.name}</strong>
                  <small>{group.decks.length} decks</small></div>
                {group.decks.map((deck) => <DeckRow key={deck.id} deck={deck}
                  onDelete={deleteDeck} onUpdate={updateDeck} />)}
              </div>
            ))}
        </section>
      </main>
      <footer>DeckSuggester · Scryfall-backed collection intelligence</footer>
    </div>
  );
}

export default App;
