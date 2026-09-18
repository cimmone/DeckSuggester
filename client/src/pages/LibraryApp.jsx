import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppFooter } from '../components/AppFooter';
import { DeckRow } from '../components/DeckRow';
import { PieChart } from '../components/PieChart';
import { METRICS, deckMatchesFilters } from '../lib/filters';

export function LibraryApp({ session, onSignOut, onUnauthenticated }) {
  const [decks, setDecks] = useState([]);
  const [stats, setStats] = useState(null);
  const [folderUrl, setFolderUrl] = useState('');
  const [folderFilter, setFolderFilter] = useState('all');
  const [metric, setMetric] = useState('cardColors');
  const [filters, setFilters] = useState([]);
  const [only, setOnly] = useState(false);
  const [busy, setBusy] = useState(true);
  const [notice, setNotice] = useState(null);

  const securedFetch = useCallback(async (url, options = {}) => {
    const headers = new Headers(options.headers || {});
    if (options.method && options.method !== 'GET') {
      headers.set(session.csrfHeaderName, session.csrfToken);
    }
    const response = await fetch(url, { ...options, headers });
    if (response.status === 401) onUnauthenticated();
    return response;
  }, [onUnauthenticated, session.csrfHeaderName, session.csrfToken]);

  const loadData = useCallback(async () => {
    const response = await securedFetch('/library');
    if (!response.ok) throw new Error('Could not load Mongo data');
    const library = await response.json();
    setDecks(library.decks);
    setStats(library.statistics);
  }, [securedFetch]);

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
    && deckMatchesFilters(deck, filters, only)
  )), [decks, filters, folderFilter, only]);

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
      const response = await securedFetch('/decks/', {
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
    const response = await securedFetch(`/decks/${encodeURIComponent(deck.id)}`,
      { method: 'DELETE' });
    if (response.ok) {
      await loadData();
      setNotice({ type: 'success', text: `Deleted ${deck.name}.` });
    }
  }

  async function updateDeck(id, name, description) {
    const response = await securedFetch(`/decks/${encodeURIComponent(id)}`, {
      method: 'PUT', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, description }),
    });
    if (!response.ok) throw new Error('Could not update the deck');
    await loadData();
    setNotice({ type: 'success', text: 'Deck updated.' });
  }

  async function deleteAll() {
    if (!window.confirm('Delete every deck in your library? This cannot be undone.')) return;
    const response = await securedFetch('/decks', { method: 'DELETE' });
    if (response.ok) {
      setFilters([]);
      await loadData();
      setNotice({ type: 'success', text: 'All of your imported decks were deleted.' });
    }
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="/ui/">
          <span className="brand-mark">DS</span>
          <span>DeckSuggester <small>Library console</small></span>
        </a>
        <div className="account-status">
          <span className="database-status"><i /> Mongo · {stats?.totalDecks || 0} decks</span>
          <span>{session.username} · {session.libraryId}</span>
          <button onClick={onSignOut}>Sign out</button>
        </div>
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
            <label className="only-toggle">
              <input type="checkbox" checked={only}
                onChange={(event) => setOnly(event.target.checked)} />
              <span><b>Only</b> — exclude decks with values beyond the selected filters</span>
            </label>
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
                Delete my decks
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
      <AppFooter />
    </div>
  );
}
