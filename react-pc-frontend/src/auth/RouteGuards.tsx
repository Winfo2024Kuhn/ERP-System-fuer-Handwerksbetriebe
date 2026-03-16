import { Navigate, useLocation } from 'react-router-dom';
import { RefreshCw } from 'lucide-react';
import { useAuth } from './AuthContext';

function FullscreenLoading() {
    return (
        <div className="min-h-screen bg-slate-50 flex items-center justify-center">
            <div className="flex items-center gap-2 text-slate-500">
                <RefreshCw className="w-4 h-4 animate-spin" />
                Anmeldung wird geprüft ...
            </div>
        </div>
    );
}

export function RequireAuth({ children }: { children: React.ReactElement }) {
    const { loading, isAuthenticated, user, isAdmin } = useAuth();
    const location = useLocation();

    if (loading) {
        return <FullscreenLoading />;
    }

    if (!isAuthenticated) {
        return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
    }

    if (isAdmin && user?.requiresInitialSetup && location.pathname !== '/onboarding') {
        return <Navigate to="/onboarding" replace />;
    }

    return children;
}

export function RequireAdmin({ children }: { children: React.ReactElement }) {
    const { loading, isAuthenticated, isAdmin } = useAuth();

    if (loading) {
        return <FullscreenLoading />;
    }

    if (!isAuthenticated) {
        return <Navigate to="/login" replace />;
    }

    if (!isAdmin) {
        return <Navigate to="/projekte" replace />;
    }

    return children;
}
