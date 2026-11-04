export function importStatusText(item) {
  if (item.status === 'FAILED') return item.error || 'Import failed';
  if (item.status === 'COMPLETE') return `${item.completed}/${item.total} Decks Imported`;
  if (item.phase === 'saving') return `Saving ${item.total} deck${item.total === 1 ? '' : 's'}…`;
  if (item.phase === 'fetching') return `${item.completed}/${item.total} Decks Imported`;
  return 'Finding decks…';
}

export function ImportProgressPanel({ imports, onDismiss }) {
  if (!imports.length) return null;
  return (
    <div className="import-progress-stack" role="region" aria-label="Deck imports">
      {imports.map((item) => {
        const indeterminate = item.status === 'RUNNING' && (item.phase !== 'fetching' || !item.total) && item.phase !== 'saving';
        const percent = item.status === 'COMPLETE' || item.phase === 'saving' ? 100
          : item.total ? Math.round((item.completed / item.total) * 100) : 0;
        return (
          <div key={item.id} className={`import-progress ${item.status.toLowerCase()}`}>
            <div className="import-progress-heading">
              <strong title={item.label}>{item.label}</strong>
              {item.status !== 'RUNNING' && (
                <button type="button" aria-label="Dismiss import" onClick={() => onDismiss(item.id)}>×</button>
              )}
            </div>
            <div className={`progress-track${indeterminate ? ' indeterminate' : ''}`}
              role="progressbar" aria-label={`Import progress for ${item.label}`}
              aria-valuemin={0} aria-valuemax={100}
              aria-valuenow={indeterminate ? undefined : percent}>
              <div className="progress-fill" style={indeterminate ? undefined : { width: `${percent}%` }} />
            </div>
            <span className="import-progress-text">{importStatusText(item)}</span>
          </div>
        );
      })}
    </div>
  );
}
