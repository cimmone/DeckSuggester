import { useEffect, useState } from 'react';
import { API_BASE } from '../lib/api';
import { navigate } from '../lib/router';

/** Popup for pasting a raw decklist plus a signature card name to create a new deck. */
export function ManualDeckImportDialog({ session, securedFetch, folders, onClose }) {
  const [name, setName] = useState('');
  const [signatureName, setSignatureName] = useState('');
  const [cardList, setCardList] = useState('');
  const [folderId, setFolderId] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    function onKey(event) { if (event.key === 'Escape') onClose(); }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  async function save(event) {
    event.preventDefault();
    setError(null);
    if (!name.trim()) { setError('A deck name is required.'); return; }
    if (!signatureName.trim()) { setError('A signature card (commander) name is required.'); return; }
    setBusy(true);
    try {
      const response = await securedFetch(`${API_BASE}/decks/manual`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: name.trim(),
          signatureCardName: signatureName.trim(),
          cardList,
          folderId: folderId || null,
        }),
      });
      const body = await response.json();
      if (!response.ok) throw new Error(body.message || 'Could not save the deck');
      onClose();
      navigate(`/ui/decks/${encodeURIComponent(body.id)}`);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="modal manual-import" onClick={(event) => event.stopPropagation()}>
        <div className="modal-heading">
          <div>
            <p className="eyebrow">Paste a decklist</p>
            <h2>Create a deck from a card list</h2>
          </div>
          <button className="close-button" onClick={onClose} aria-label="Close">&times;</button>
        </div>

        <form className="manual-import-form" onSubmit={save}>
          <label>
            Deck name
            <input value={name} required maxLength="120"
              onChange={(event) => setName(event.target.value)}
              placeholder="My new deck" autoFocus />
          </label>
          <label>
            Signature card (commander) &mdash; required
            <input value={signatureName} required
              onChange={(event) => setSignatureName(event.target.value)}
              placeholder="e.g. Atraxa, Praetors' Voice" />
          </label>
          <label>
            Save to folder
            <select value={folderId} onChange={(event) => setFolderId(event.target.value)}>
              <option value="">Library root</option>
              {folders.map((folder) => (
                <option key={folder.id} value={folder.id}>{folder.name}</option>
              ))}
            </select>
          </label>
          <label>
            Card list &mdash; one card per line (optional leading quantity)
            <textarea value={cardList} rows="14"
              onChange={(event) => setCardList(event.target.value)}
              placeholder={`1 Sol Ring\n1 Arcane Signet\n2 Forest\n...`} />
          </label>
          {error && <div className="notice error"><span>{error}</span></div>}
          <div className="manual-import-actions">
            <button type="button" className="small-button" onClick={onClose}>Cancel</button>
            <button type="submit" className="primary-button" disabled={busy}>
              {busy ? 'Saving\u2026' : 'Save and edit'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
