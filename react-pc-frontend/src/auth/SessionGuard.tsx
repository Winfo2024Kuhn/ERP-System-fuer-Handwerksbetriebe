import { useEffect, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { useToast } from '../components/ui/toast';

/**
 * SessionGuard
 *
 * Listens for the `auth:session-expired` custom event dispatched by the
 * global fetch interceptor (sessionInterceptor.ts). When the event fires
 * while the user is authenticated, it:
 *   1. Calls logout() to clear the session state
 *   2. Shows a warning toast
 *   3. Redirects to /login, preserving the current path for post-login redirect
 *
 * Must be rendered inside <BrowserRouter>, <AuthProvider>, and <ToastProvider>.
 */
export function SessionGuard() {
    const { user, logout } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const toast = useToast();

    // Whether the user has been authenticated at any point in this session
    const wasAuthenticatedRef = useRef(false);
    // Guard against multiple concurrent redirects
    const handlingRef = useRef(false);
    // Always-current snapshot of the pathname (avoids stale closures)
    const locationRef = useRef(location.pathname);

    useEffect(() => {
        locationRef.current = location.pathname;
    }, [location.pathname]);

    useEffect(() => {
        if (user) {
            wasAuthenticatedRef.current = true;
        }
    }, [user]);

    useEffect(() => {
        const handleSessionExpired = () => {
            // Only act when the user was previously logged in
            if (!wasAuthenticatedRef.current) return;
            // Prevent duplicate handling
            if (handlingRef.current) return;
            // Already on the login page — nothing to do
            if (locationRef.current === '/login') return;

            handlingRef.current = true;
            const fromPath = locationRef.current;

            logout().finally(() => {
                toast.warning(
                    'Ihre Sitzung ist abgelaufen. Bitte melden Sie sich erneut an.',
                );
                navigate('/login', {
                    replace: true,
                    state: { from: fromPath, sessionExpired: true },
                });

                // Reset guards after navigation has settled
                setTimeout(() => {
                    handlingRef.current = false;
                    wasAuthenticatedRef.current = false;
                }, 2_000);
            });
        };

        window.addEventListener('auth:session-expired', handleSessionExpired);
        return () =>
            window.removeEventListener('auth:session-expired', handleSessionExpired);
    }, [logout, navigate, toast]);

    return null;
}
