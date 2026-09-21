async function request(url, { method = 'GET', body, headers, errorMessage } = {}) {
  const response = await fetch(url, {
    method,
    headers: body !== undefined ? { 'Content-Type': 'application/json', ...headers } : headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(errorMessage || payload.message || 'The request could not be completed');
  return payload;
}

export function jsonRequest(url, body) {
  return request(url, { method: 'POST', body });
}

export function getJson(url, errorMessage) {
  return request(url, { errorMessage });
}

// Wraps fetch with the app's CSRF header (required on every non-GET request)
// and redirects to the sign-in screen on an expired session (401).
export function createSecuredFetch(session, onUnauthenticated) {
  return async function securedFetch(url, options = {}) {
    const headers = new Headers(options.headers || {});
    if (options.method && options.method !== 'GET') {
      headers.set(session.csrfHeaderName, session.csrfToken);
    }
    const response = await fetch(url, { ...options, headers });
    if (response.status === 401) onUnauthenticated();
    return response;
  };
}
