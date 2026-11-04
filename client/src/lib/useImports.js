import { useCallback, useEffect, useRef, useState } from 'react';
import { API_BASE } from './api';

const POLL_INTERVAL_MS = 700;
const DISMISS_AFTER_MS = 6000;

function newImportId() {
  return globalThis.crypto?.randomUUID?.() ?? `import-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

/**
 * Runs deck imports and tracks each one's progress. Imports run independently,
 * so any number can be in flight at once; each is polled by its own id while
 * its (long-running) POST is still pending.
 */
export function useImports(securedFetch, { onImported, onError } = {}) {
  const [imports, setImports] = useState([]);
  const callbacks = useRef({ onImported, onError });
  callbacks.current = { onImported, onError };

  const update = useCallback((id, changes) => {
    setImports((current) => current.map((item) => (item.id === id ? { ...item, ...changes } : item)));
  }, []);

  const dismiss = useCallback((id) => {
    setImports((current) => current.filter((item) => item.id !== id));
  }, []);

  const startImport = useCallback(async (url) => {
    const id = newImportId();
    setImports((current) => [...current,
      { id, label: url, status: 'RUNNING', phase: 'starting', total: 0, completed: 0 }]);
    try {
      const response = await securedFetch(`${API_BASE}/decks/`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url, importId: id }),
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body.message || 'Import failed');
      update(id, { status: 'COMPLETE', phase: 'done', total: body.decksImported, completed: body.decksImported });
      await callbacks.current.onImported?.(body);
      setTimeout(() => dismiss(id), DISMISS_AFTER_MS);
    } catch (error) {
      update(id, { status: 'FAILED', error: error.message });
      callbacks.current.onError?.(error);
    }
  }, [dismiss, securedFetch, update]);

  const running = imports.filter((item) => item.status === 'RUNNING').map((item) => item.id);
  const runningKey = running.join(',');

  useEffect(() => {
    if (!runningKey) return undefined;
    let stopped = false;
    const timer = setInterval(() => {
      runningKey.split(',').forEach(async (id) => {
        try {
          const response = await securedFetch(`${API_BASE}/decks/imports/${encodeURIComponent(id)}`);
          if (!response.ok || stopped) return; // not registered yet; try again next tick
          const snapshot = await response.json();
          // The POST response is authoritative for finishing; polling only reports progress.
          if (!stopped && snapshot.status === 'RUNNING') {
            update(id, { phase: snapshot.phase, total: snapshot.total, completed: snapshot.completed,
              ...(snapshot.label ? { label: snapshot.label } : {}) });
          }
        } catch { /* a missed poll is harmless */ }
      });
    }, POLL_INTERVAL_MS);
    return () => { stopped = true; clearInterval(timer); };
  }, [runningKey, securedFetch, update]);

  return { imports, startImport, dismiss };
}
