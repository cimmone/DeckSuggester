import { useEffect, useState } from 'react';

export function useLocationPath() {
  const [path, setPath] = useState(window.location.pathname);
  useEffect(() => {
    const onPop = () => setPath(window.location.pathname);
    window.addEventListener('popstate', onPop);
    return () => window.removeEventListener('popstate', onPop);
  }, []);
  return path;
}

export function navigate(path) {
  window.history.pushState({}, '', path);
  window.dispatchEvent(new PopStateEvent('popstate'));
}

export function deckIdFromPath(path) {
  const match = path.match(/^\/ui\/decks\/([^/]+)\/?$/);
  return match ? decodeURIComponent(match[1]) : null;
}
