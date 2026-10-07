let csrfToken: string | null = null;

export type UnauthorizedHandler = () => void;

let unauthorizedHandler: UnauthorizedHandler = () => {
  window.location.hash = '#/login';
};

export function setUnauthorizedHandler(handler: UnauthorizedHandler): void {
  unauthorizedHandler = handler;
}

/**
 * Fetches the CSRF token from the backend. The backend also writes the
 * XSRF-TOKEN cookie, but the header value is read directly from the response.
 */
export async function initCsrf(): Promise<void> {
  const res = await fetch('/api/auth/csrf', { credentials: 'same-origin' });
  if (res.ok) {
    const data = await res.json();
    csrfToken = data.token;
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    ...(options.headers as Record<string, string> | undefined)
  };
  const method = (options.method ?? 'GET').toUpperCase();
  if (method !== 'GET' && method !== 'HEAD') {
    headers['X-XSRF-TOKEN'] = csrfToken ?? '';
  }

  const res = await fetch(path, {
    ...options,
    credentials: 'same-origin',
    headers
  });

  if (res.status === 401) {
    unauthorizedHandler();
    throw new Error('Unauthorized');
  }

  if (!res.ok) {
    let message = `HTTP ${res.status}`;
    try {
      const body = await res.json();
      message = body.detail || body.message || message;
    } catch {
      // response was not JSON, keep the HTTP status message
    }
    throw new Error(message);
  }

  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

export async function login(username: string, password: string): Promise<void> {
  const form = new URLSearchParams();
  form.set('username', username);
  form.set('password', password);
  const headers: Record<string, string> = {
    'Content-Type': 'application/x-www-form-urlencoded',
    'X-XSRF-TOKEN': csrfToken ?? ''
  };
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    credentials: 'same-origin',
    headers,
    body: form.toString()
  });
  if (!res.ok) {
    let message = 'Invalid username or password';
    try {
      const body = await res.json();
      message = body.message || message;
    } catch {
      // ignore
    }
    throw new Error(message);
  }
}

export async function logout(): Promise<void> {
  await request('/api/auth/logout', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({})
  });
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, {
      method: 'POST',
      headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body)
    }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' })
};
