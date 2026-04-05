/**
 * Session Interceptor
 *
 * Patches `window.fetch` globally to detect HTTP 401 (Unauthorized) responses
 * from non-authentication endpoints. When a 401 is detected it dispatches the
 * custom DOM event `auth:session-expired` so that the SessionGuard component
 * can transparently redirect the user to the login page.
 *
 * Auth endpoints (/api/auth/*) are intentionally excluded to avoid
 * infinite redirect loops during login, logout, and the initial session check.
 */

let installed = false;

function isAuthEndpoint(url: string): boolean {
    try {
        const { pathname } = new URL(url, window.location.origin);
        return pathname.startsWith('/api/auth/');
    } catch {
        return url.includes('/api/auth/');
    }
}

export function installSessionInterceptor(): void {
    if (installed) return;
    installed = true;

    const originalFetch = window.fetch.bind(window);

    window.fetch = async function (
        input: RequestInfo | URL,
        init?: RequestInit,
    ): Promise<Response> {
        const response = await originalFetch(input, init);

        // Only treat 401 (Unauthorized) as session expired.
        // 403 (Forbidden) can be a legitimate permission denial (e.g. Abteilung check)
        // and should NOT trigger a session-expired redirect.
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
