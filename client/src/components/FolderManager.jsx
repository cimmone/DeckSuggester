import { useEffect, useState } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faChevronUp, faChevronDown, faFolder, faFolderOpen, faPlus, faTrash, faArrowRight,
} from '@fortawesome/free-solid-svg-icons';
import { API_BASE } from '../lib/api';

/** A tree view of the user's folders with create/rename/reorder/move/delete controls. */
export function FolderManager({ securedFetch, folders, decks, onChange, onClose }) {
  const [name, setName] = useState('');
  const [parentId, setParentId] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [confirming, setConfirming] = useState(null);
  const [renaming, setRenaming] = useState(null);
  const [renameValue, setRenameValue] = useState('');
  const [moving, setMoving] = useState(null);
  const [targetFolderId, setTargetFolderId] = useState('');

  useEffect(() => {
    function onKey(event) { if (event.key === 'Escape') onClose(); }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  const childrenOf = (underParentId) => folders
    .filter((folder) => (folder.parentId || null) === underParentId)
    .sort((a, b) => (a.order || 0) - (b.order || 0));

  const decksIn = (folderId) => decks.filter((deck) => deck.folderRef === folderId);

  async function create(event) {
    event.preventDefault();
    setError(null);
    if (!name.trim()) return;
    setBusy(true);
    try {
      const response = await securedFetch(`${API_BASE}/folders`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: name.trim(), parentId: parentId || null }),
      });
      if (!response.ok) throw new Error('Could not create the folder');
      setName('');
      await onChange();
    } catch (err) { setError(err.message); } finally { setBusy(false); }
  }

  async function move(folderId, targetFolderId, order) {
    setBusy(true);
    setError(null);
    try {
      const response = await securedFetch(`${API_BASE}/folders/${encodeURIComponent(folderId)}/move`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ parentId: targetFolderId === undefined ? null : targetFolderId, order }),
      });
      if (!response.ok) throw new Error('Could not move the folder');
      await onChange();
    } catch (err) { setError(err.message); } finally { setBusy(false); }
  }

  async function confirmDelete(folderId) {
    setBusy(true);
    setError(null);
    try {
      const response = await securedFetch(`${API_BASE}/folders/${encodeURIComponent(folderId)}`,
        { method: 'DELETE' });
      if (!response.ok) throw new Error('Could not delete the folder');
      setConfirming(null);
      await onChange();
    } catch (err) { setError(err.message); } finally { setBusy(false); }
  }

  async function commitRename(folder) {
    if (!renameValue.trim()) { setRenaming(null); return; }
    setBusy(true);
    setError(null);
    try {
      const response = await securedFetch(`${API_BASE}/folders/${encodeURIComponent(folder.id)}`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: renameValue.trim() }),
      });
      if (!response.ok) throw new Error('Could not rename the folder');
      setRenaming(null);
      await onChange();
    } catch (err) { setError(err.message); } finally { setBusy(false); }
  }

  function startMove(folder) {
    setMoving(folder.id);
    setTargetFolderId(folder.parentId || '');
  }

  async function commitMove(folder) {
    await move(folder.id, targetFolderId || null, folder.order || 0);
    setMoving(null);
  }

  function renderTree(underParentId, depth = 0) {
    const children = childrenOf(underParentId);
    if (children.length === 0) return null;
    return children.map((folder, index) => {
      const siblings = children;
      const childDecks = decksIn(folder.id);
      return (
        <li key={folder.id} className="folder-tree-node" style={{ paddingLeft: `${depth * 18}px` }}>
          <div className="folder-tree-row">
            <FontAwesomeIcon icon={childDecks.length || childrenOf(folder.id).length ? faFolderOpen : faFolder} />
            {renaming === folder.id ? (
              <input value={renameValue} autoFocus
                onChange={(event) => setRenameValue(event.target.value)}
                onBlur={() => commitRename(folder)}
                onKeyDown={(event) => event.key === 'Enter' && commitRename(folder)} />
            ) : (
              <strong>{folder.name}</strong>
            )}
            <span className="folder-tree-counts">
              {childDecks.length > 0 && <em>{childDecks.length} decks</em>}
              {childrenOf(folder.id).length > 0 && <em>{childrenOf(folder.id).length} subfolders</em>}
            </span>
            <div className="folder-tree-actions">
              <button type="button" title="Move up" disabled={busy || index === 0}
                onClick={() => move(folder.id, folder.parentId || null, (folder.order || 0) - 1)}>
                <FontAwesomeIcon icon={faChevronUp} />
              </button>
              <button type="button" title="Move down" disabled={busy || index === siblings.length - 1}
                onClick={() => move(folder.id, folder.parentId || null, (folder.order || 0) + 1)}>
                <FontAwesomeIcon icon={faChevronDown} />
              </button>
              <button type="button" title="Rename"
                disabled={busy}
                onClick={() => { setRenaming(folder.id); setRenameValue(folder.name); }}>
                Rename
              </button>
              <button type="button" title="Move into folder"
                disabled={busy}
                onClick={() => startMove(folder)}>
                <FontAwesomeIcon icon={faArrowRight} /> Move
              </button>
              <button type="button" className="danger-text" title="Delete folder and contents"
                disabled={busy}
                onClick={() => setConfirming(folder)}>
                <FontAwesomeIcon icon={faTrash} /> Delete
              </button>
            </div>
          </div>
          {moving === folder.id && (
            <div className="folder-move-row">
              <span>Move <strong>{folder.name}</strong> into:</span>
              <select value={targetFolderId} onChange={(event) => setTargetFolderId(event.target.value)}>
                <option value="">Library root</option>
                {folders.filter((f) => f.id !== folder.id).map((f) => (
                  <option key={f.id} value={f.id}>{f.name}</option>
                ))}
              </select>
              <button type="button" className="small-button primary"
                disabled={busy} onClick={() => commitMove(folder)}>Apply</button>
              <button type="button" className="small-button"
                disabled={busy} onClick={() => setMoving(null)}>Cancel</button>
            </div>
          )}
          {renderTree(folder.id, depth + 1)}
        </li>
      );
    });
  }

  const rootFolders = childrenOf(null);

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="modal folder-manager" onClick={(event) => event.stopPropagation()}>
        <div className="modal-heading">
          <div>
            <p className="eyebrow">Organise your library</p>
            <h2>Folders</h2>
          </div>
          <button className="close-button" onClick={onClose} aria-label="Close">&times;</button>
        </div>

        <form className="folder-create-form" onSubmit={create}>
          <input value={name} placeholder="New folder name"
            onChange={(event) => setName(event.target.value)} />
          <select value={parentId} onChange={(event) => setParentId(event.target.value)}>
            <option value="">Library root</option>
            {folders.map((folder) => (
              <option key={folder.id} value={folder.id}>{folder.name}</option>
            ))}
          </select>
          <button type="submit" className="primary-button" disabled={busy || !name.trim()}>
            <FontAwesomeIcon icon={faPlus} /> Create
          </button>
        </form>

        {error && <div className="notice error"><span>{error}</span></div>}

        <div className="folder-tree-scroll">
          {rootFolders.length === 0 ? (
            <div className="empty-state">No folders yet. Create one above to start organising decks.</div>
          ) : (
            <ul className="folder-tree">{renderTree(null)}</ul>
          )}
        </div>

        {confirming && (
          <div className="confirm-dialog" role="alertdialog" aria-modal="true">
            <p>
              Delete <strong>{confirming.name}</strong> and every folder and deck inside it?
              This cannot be undone.
            </p>
            <div className="confirm-actions">
              <button type="button" className="small-button"
                disabled={busy} onClick={() => setConfirming(null)}>Cancel</button>
              <button type="button" className="danger-button"
                disabled={busy} onClick={() => confirmDelete(confirming.id)}>
                {busy ? 'Deleting\u2026' : 'Yes, delete everything'}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
