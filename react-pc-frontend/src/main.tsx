import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import App from './App';

// CSRF protection: intercept all state-changing fetch requests and inject X-XSRF-TOKEN header.
// Spring Security sets the XSRF-TOKEN cookie (non-HttpOnly) via CookieCsrfTokenRepository.
// A global interceptor is used here to cover all fetch calls without modifying each one.
(function installCsrfInterceptor() {
    const STATE_CHANGING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);
    const originalFetch = window.fetch.bind(window);

    function readXsrfCookie(): string | undefined {
        const match = document.cookie
            .split('; ')
            .find(row => row.startsWith('XSRF-TOKEN='));
        return match ? decodeURIComponent(match.split('=')[1]) : undefined;
    }

    window.fetch = (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
        const method = ((init?.method) || 'GET').toUpperCase();
        if (STATE_CHANGING_METHODS.has(method)) {
            const token = readXsrfCookie();
            if (token) {
                // Normalize existing headers to a plain object to safely merge the CSRF token.
                const existingHeaders = init?.headers instanceof Headers
                    ? Object.fromEntries(init.headers.entries())
                    : (init?.headers as Record<string, string> | undefined) ?? {};
                init = {
                    ...init,
                    headers: {
                        ...existingHeaders,
                        'X-XSRF-TOKEN': token,
                    },
                };
            }
        }
        return originalFetch(input, init);
    };
})();

const rootElement = document.getElementById('root');

if (rootElement) {
  createRoot(rootElement).render(
    <StrictMode>
      <App />
    </StrictMode>
  );
}
