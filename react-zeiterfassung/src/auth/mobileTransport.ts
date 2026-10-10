export function createMobileFetch(
  send: typeof fetch,
  getToken: () => string | null,
  origin: string,
): typeof fetch {
  return (input, init) => {
    const url = new URL(input instanceof Request ? input.url : String(input), origin)
    if (url.origin !== origin || !url.pathname.startsWith('/api/') || url.pathname.startsWith('/api/mitarbeiter/by-token/')) {
      return send(input, init)
    }
    const token = getToken()
    if (!token) return send(input, init)
    const headers = new Headers(init?.headers ?? (input instanceof Request ? input.headers : undefined))
    headers.set('X-Auth-Token', token)
    // Der eigene Token-Header darf bei einer Weiterleitung nicht den Ursprung verlassen.
    return send(input, { ...init, headers, redirect: 'error' })
  }
}

export class TokenLoginError extends Error {
  readonly retryAfterSeconds: number
  constructor(message: string, retryAfterSeconds = 0) {
    super(message)
    this.retryAfterSeconds = retryAfterSeconds
  }
}

export interface TokenEmployee {
  id: number
  vorname: string
  nachname: string
  aktiv: boolean
}

export function tokenRetryError(response: Response): TokenLoginError {
  const rawSeconds = Number(response.headers.get('Retry-After'))
  const seconds = Number.isFinite(rawSeconds) && rawSeconds > 0 ? Math.ceil(rawSeconds) : 2
  return new TokenLoginError('Zu viele Anmeldeversuche. Bitte warte kurz.', seconds)
}

export async function validateLoginToken(token: string, send: typeof fetch = fetch): Promise<TokenEmployee> {
  const response = await send(`/api/mitarbeiter/by-token/${encodeURIComponent(token)}`, { cache: 'no-store' })
  if (response.status === 429) {
    throw tokenRetryError(response)
  }
  if ([401, 403, 404].includes(response.status)) {
    throw new TokenLoginError('Token ungültig. Bitte prüfe die Eingabe oder fordere einen neuen QR-Code an.')
  }
  if (!response.ok) throw new TokenLoginError('Server nicht erreichbar. Bitte später erneut versuchen.')
  const employee: TokenEmployee = await response.json()
  if (!employee.id || employee.aktiv === false) throw new TokenLoginError('Token ungültig. Bitte neuen QR-Code anfordern.')
  return employee
}

export const LOGIN_RETRY_KEY = 'zeiterfassung_login_retry_until'
export function readLoginRetryAt(): number {
  const value = Number(sessionStorage.getItem(LOGIN_RETRY_KEY))
  return Number.isFinite(value) && value > Date.now() ? value : 0
}
