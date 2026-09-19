const origin = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080'
type Tokens = { accessToken: string; refreshToken: string }
let refresh: Promise<Tokens> | null = null
export const session = {
  get(): Tokens | null { try { return JSON.parse(sessionStorage.getItem('sentinel-session') || 'null') } catch { return null } },
  set(tokens: Tokens) { sessionStorage.setItem('sentinel-session', JSON.stringify(tokens)) },
  clear() { sessionStorage.removeItem('sentinel-session') },
}
export function roles(): string[] {
  try { const part = session.get()!.accessToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'); return JSON.parse(atob(part)).roles || [] } catch { return [] }
}
export async function api<T>(path: string, options: RequestInit = {}, retry = true): Promise<T> {
  const headers = new Headers(options.headers)
  if (!(options.body instanceof FormData) && options.body) headers.set('Content-Type', 'application/json')
  if (session.get()) headers.set('Authorization', `Bearer ${session.get()!.accessToken}`)
  const response = await fetch(`${origin}/api/v1${path}`, { ...options, headers })
  if (response.status === 401 && retry && session.get()) {
    if (!refresh) refresh = fetch(`${origin}/api/v1/auth/refresh`, { method: 'POST', headers: { refresh_token: `Bearer ${session.get()!.refreshToken}` } }).then(async r => { if (!r.ok) throw new Error('Session expired. Please reconnect.'); const tokens = await r.json(); session.set(tokens); return tokens }).catch(e => { session.clear(); throw e }).finally(() => { refresh = null })
    await refresh
    return api<T>(path, options, false)
  }
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    const request = response.headers.get('X-Request-ID')
    throw new Error(`${error.message || `Request failed (${response.status})`}${response.status === 429 ? `. Retry after ${response.headers.get('Retry-After') || '60'} seconds` : ''}${request ? ` · Request ID: ${request}` : ''}`)
  }
  return response.status === 204 ? undefined as T : response.json()
}
export async function login(username: string, password: string) {
  const response = await fetch(`${origin}/api/v1/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password }) })
  if (!response.ok) { const e = await response.json().catch(() => ({})); throw new Error(e.message || 'Unable to sign in') }
  session.set(await response.json())
}
