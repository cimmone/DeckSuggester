export function Toasts({ toasts, onDismiss }) {
  if (!toasts.length) return null;
  return (
    <div className="toast-stack" role="region" aria-label="Notifications" aria-live="assertive">
      {toasts.map((toast) => (
        <div key={toast.id} className={`toast ${toast.type || 'error'}`} role="alert">
          <span>{toast.text}</span>
          <button type="button" aria-label="Dismiss notification"
            onClick={() => onDismiss(toast.id)}>×</button>
        </div>
      ))}
    </div>
  );
}
