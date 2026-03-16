/**
 * Session Interceptor
 *
 * Patches `window.fetch` globally to:
 * 1. Detect HTTP 401 (Unauthorized) responses from non-authentication endpoints and dispatch
 *    the custom DOM event `auth:session-expired` so that the SessionGuard component can
 *    transparently redirect the user to the login page.
 * 2. Attach the XSRF-TOKEN cookie value as the X-XSRF-TOKEN request header on all
 *    state-changing requests (POST, PUT, PATCH, DELETE) to satisfy Spring Security's
 *    cookie-based CSRF protection.
 *
 * Auth endpoints (/api/auth/*) are intentionally excluded from 401-detection to avoid
 * infinite redirect loops during login, logout, and the initial session check.
 */

const STATE_CHANGING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

let installed = false;

function isAuthEndpoint(url: string): boolean {
    try {
        const { pathname } = new URL(url, window.location.origin);
        return pathname.startsWith('/api/auth/');
    } catch {
        return url.includes('/api/auth/');
    }
}

function getCsrfToken(): string | null {
    const match = document.cookie
        .split(';')
        .map(c => c.trim())
        .find(c => c.startsWith('XSRF-TOKEN='));
    return match ? decodeURIComponent(match.slice('XSRF-TOKEN='.length)) : null;
}

export function installSessionInterceptor(): void {
    if (installed) return;
    installed = true;

    const originalFetch = window.fetch.bind(window);

    window.fetch = async function (
        input: RequestInfo | URL,
        init?: RequestInit,
    ): Promise<Response> {
        const method = (
            init?.method ?? (input instanceof Request ? input.method : 'GET')
        ).toUpperCase();

        let patchedInit = init;
        if (STATE_CHANGING_METHODS.has(method)) {
            const csrfToken = getCsrfToken();
            if (csrfToken) {
                // Merge headers from the Request object (if applicable), then from init.headers,
                // so that pre-existing headers are preserved when adding the CSRF token.
                const headers = new Headers(
                    input instanceof Request ? input.headers : undefined,
                );
                if (init?.headers) {
                    new Headers(init.headers).forEach((value, key) => headers.set(key, value));
                }
                headers.set('X-XSRF-TOKEN', csrfToken);
                patchedInit = { ...init, method, headers };
            }
        }

        const response = await originalFetch(input, patchedInit);

        if (response.status === 401) {
            const url =
                typeof input === 'string'
                    ? input
                    : input instanceof URL
                    ? input.href
                    : (input as Request).url;

            if (!isAuthEndpoint(url)) {
                window.dispatchEvent(new CustomEvent('auth:session-expired'));
            }
        }

        return response;
    };
}
