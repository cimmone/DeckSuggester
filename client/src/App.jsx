import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Highcharts from 'highcharts';
import './styles.css';

const METRICS = {
  cardColors: { label: 'Card colors', empty: 'No color data' },
  deckColors: { label: 'Commander identity', empty: 'No deck identity data' },
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

function metricValues(deck, metric) {
  const cards = (deck.cards || []).filter((card) => card.includedInDeck !== false);
  switch (metric) {
    case 'manaValues':
      return new Set(cards.map((card) => formatManaValue(card.manaValue)));
    case 'cardColors':
      return new Set(cards.flatMap(cardColors));
    case 'deckColors': {
      // The server already resolves this to the commander's identity when one
      // is designated, falling back to the deck's included cards otherwise.
      const identity = deck.colorIdentity || [];
      return new Set(identity.length ? identity : ['Colorless']);
    }
    case 'cardTypes':
      return new Set(cards.flatMap(cardTypes));
    default:
      return new Set();
  }
}

function anyValueMatches(actual, selected, metric) {
  if ((metric === 'cardColors' || metric === 'deckColors')
      && selected !== 'Colorless' && actual.size === 1 && actual.has('Colorless')) return true;
  return actual.has(selected);
}

function onlyValuesMatch(actual, selected, metric) {
  if (metric === 'cardColors' || metric === 'deckColors') {
    if (actual.size === 1 && actual.has('Colorless')) return selected.size > 0;
    const allowed = new Set(selected);
    if ([...selected].some((value) => value !== 'Colorless')) allowed.add('Colorless');
    const required = [...selected].filter((value) => value !== 'Colorless');
    return [...actual].every((value) => allowed.has(value))
      && required.every((value) => actual.has(value));
  }
  return actual.size === selected.size && [...actual].every((value) => selected.has(value));
}

function deckMatchesFilters(deck, filters, only) {
  if (!only) {
    return filters.every((filter) => anyValueMatches(
      metricValues(deck, filter.metric), filter.value, filter.metric));
  }
  const grouped = new Map();
  filters.forEach((filter) => {
    if (!grouped.has(filter.metric)) grouped.set(filter.metric, new Set());
    grouped.get(filter.metric).add(filter.value);
  });
  return [...grouped].every(([metric, selected]) => (
    onlyValuesMatch(metricValues(deck, metric), selected, metric)
  ));
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

const CARD_PREVIEW_WIDTH = 250;
const CARD_PREVIEW_ASPECT_RATIO = 88 / 63; // Standard Magic card height:width ratio
const CARD_PREVIEW_MARGIN = 12;

function CardName({ card }) {
  const anchorRef = useRef(null);
  const [previewStyle, setPreviewStyle] = useState(null);
  const hasImage = card.matchedScryfallCard && card.scryfallId;

  function showPreview() {
    if (!hasImage || !anchorRef.current) return;
    const rect = anchorRef.current.getBoundingClientRect();
    const previewHeight = CARD_PREVIEW_WIDTH * CARD_PREVIEW_ASPECT_RATIO;

    // Center the preview on the row, but clamp it inside the viewport so it is
    // never cut off — including when the row sits near the top of the page,
    // where anchoring purely below/above the row would push it off-screen.
    const maxTop = Math.max(window.innerHeight - previewHeight - CARD_PREVIEW_MARGIN,
      CARD_PREVIEW_MARGIN);
    const idealTop = rect.top + rect.height / 2 - previewHeight / 2;
    const top = Math.min(Math.max(idealTop, CARD_PREVIEW_MARGIN), maxTop);

    const spaceOnRight = window.innerWidth - rect.right;
    const idealLeft = spaceOnRight >= CARD_PREVIEW_WIDTH + CARD_PREVIEW_MARGIN
      ? rect.right + CARD_PREVIEW_MARGIN
      : rect.left - CARD_PREVIEW_WIDTH - CARD_PREVIEW_MARGIN;
    const maxLeft = Math.max(window.innerWidth - CARD_PREVIEW_WIDTH - CARD_PREVIEW_MARGIN,
      CARD_PREVIEW_MARGIN);
    const left = Math.min(Math.max(idealLeft, CARD_PREVIEW_MARGIN), maxLeft);

    setPreviewStyle({ top, left });
  }

  function hidePreview() {
    setPreviewStyle(null);
  }

  return (
    <span ref={anchorRef} className={`card-name${hasImage ? ' has-image' : ''}`}
      tabIndex={hasImage ? 0 : undefined}
      onMouseEnter={showPreview} onMouseLeave={hidePreview}
      onFocus={showPreview} onBlur={hidePreview}>
      <strong>{card.name}</strong>
      {previewStyle && (
        <span className="card-preview" role="tooltip"
          style={{ top: `${previewStyle.top}px`, left: `${previewStyle.left}px` }}>
          <img src={`/cards/${encodeURIComponent(card.scryfallId)}/image`}
            alt={card.name} loading="lazy" />
        </span>
      )}
    </span>
  );
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
              <CardName card={card} />
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

function LibraryApp({ session, onSignOut, onUnauthenticated }) {
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
      <footer>DeckSuggester · Scryfall-backed collection intelligence</footer>
    </div>
  );
}

async function jsonRequest(url, body) {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(payload.message || 'The request could not be completed');
  return payload;
}

function AuthChallenge({ onAuthenticated }) {
  const resetToken = new URLSearchParams(window.location.search).get('resetToken');
  const [mode, setMode] = useState(resetToken ? 'confirm-reset' : 'login');
  const [form, setForm] = useState({
    username: '', password: '', email: '', account: '', token: resetToken || '',
  });
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState(null);

  function update(field) {
    return (event) => setForm((current) => ({ ...current, [field]: event.target.value }));
  }

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setNotice(null);
    try {
      if (mode === 'login') {
        await jsonRequest('/auth/login', { username: form.username, password: form.password });
        await onAuthenticated();
      } else if (mode === 'register') {
        await jsonRequest('/auth/register', {
          username: form.username, password: form.password, email: form.email,
        });
        setForm((current) => ({ ...current, password: '' }));
        setMode('login');
        setNotice({ type: 'success', text: 'Account created. Sign in to open your library.' });
      } else if (mode === 'request-reset') {
        const result = await jsonRequest('/auth/password-reset/request', { account: form.account });
        setNotice({ type: 'success', text: result.message });
      } else {
        const result = await jsonRequest('/auth/password-reset/confirm', {
          token: form.token, password: form.password,
        });
        window.history.replaceState({}, '', '/ui/');
        setForm((current) => ({ ...current, password: '', token: '' }));
        setMode('login');
        setNotice({ type: 'success', text: result.message });
      }
    } catch (error) {
      setNotice({ type: 'error', text: error.message });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-shell">
      <section className="auth-intro">
        <a className="brand" href="/ui/">
          <span className="brand-mark">DS</span>
          <span>DeckSuggester <small>Private library</small></span>
        </a>
        <div>
          <p className="eyebrow">Your collection, protected</p>
          <h1>Know every<br /><em>draw.</em></h1>
          <p>Import and explore your Archidekt decks in a library isolated to your account.</p>
        </div>
      </section>
      <main className="auth-card">
        {mode === 'login' && <><p className="eyebrow">Authentication challenge</p>
          <h2>Sign in</h2><p>Enter your username and password to continue.</p></>}
        {mode === 'register' && <><p className="eyebrow">New account</p>
          <h2>Create your library</h2>
          <p>A private library is created for you automatically — there's nothing else to set up.</p></>}
        {mode === 'request-reset' && <><p className="eyebrow">Password recovery</p>
          <h2>Request a reset</h2><p>The reset URL will be written to the application logs.</p></>}
        {mode === 'confirm-reset' && <><p className="eyebrow">Password recovery</p>
          <h2>Choose a new password</h2><p>Reset links are single-use and expire automatically.</p></>}

        {notice && <div className={`notice ${notice.type}`}>{notice.text}</div>}
        <form className="auth-form" onSubmit={submit}>
          {(mode === 'login' || mode === 'register') && <label>
            Username
            <input required minLength={mode === 'register' ? 3 : undefined} maxLength="64"
              autoComplete="username" value={form.username} onChange={update('username')} />
          </label>}
          {mode === 'register' && <label>
            Email
            <input required type="email" maxLength="254" autoComplete="email"
              value={form.email} onChange={update('email')} />
          </label>}
          {mode === 'request-reset' && <label>
            Username or email
            <input required maxLength="254" autoComplete="username"
              value={form.account} onChange={update('account')} />
          </label>}
          {mode === 'confirm-reset' && <input type="hidden" value={form.token} />}
          {mode !== 'request-reset' && <label>
            {mode === 'confirm-reset' ? 'New password' : 'Password'}
            <input required type="password" minLength={mode === 'login' ? undefined : 12}
              maxLength="256" autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              value={form.password} onChange={update('password')} />
            {mode !== 'login' && <small>Use at least 12 characters.</small>}
          </label>}
          <button className="primary-button auth-submit" disabled={busy}>
            {busy ? 'Working…' : mode === 'login' ? 'Sign in' : mode === 'register'
              ? 'Create account' : mode === 'request-reset' ? 'Write reset URL' : 'Reset password'}
          </button>
        </form>

        <div className="auth-links">
          {mode !== 'login' && <button onClick={() => setMode('login')}>Back to sign in</button>}
          {mode === 'login' && <button onClick={() => setMode('register')}>Create an account</button>}
          {mode === 'login' && <button onClick={() => setMode('request-reset')}>Forgot password?</button>}
        </div>
      </main>
    </div>
  );
}

function App() {
  const [session, setSession] = useState(null);

  const refreshSession = useCallback(async () => {
    const response = await fetch('/auth/session');
    if (!response.ok) throw new Error('Could not check your session');
    const nextSession = await response.json();
    setSession(nextSession);
    return nextSession;
  }, []);

  useEffect(() => {
    refreshSession().catch(() => setSession({ authenticated: false }));
  }, [refreshSession]);

  async function signOut() {
    await fetch('/auth/logout', {
      method: 'POST', headers: { [session.csrfHeaderName]: session.csrfToken },
    });
    setSession({ authenticated: false });
  }

  if (session === null) return <div className="session-loading">Opening DeckSuggester…</div>;
  if (!session.authenticated) return <AuthChallenge onAuthenticated={refreshSession} />;
  return <LibraryApp session={session} onSignOut={signOut}
    onUnauthenticated={() => setSession({ authenticated: false })} />;
}

export default App;
