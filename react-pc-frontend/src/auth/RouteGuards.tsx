import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { LadeAnzeige, VerbindungsHinweis } from '../components/ui/lade-anzeige';

function FullscreenLoading() {
    return <LadeAnzeige vollbild text="Anmeldung wird geprüft …" />;
}

/** Statt auf die Anmeldeseite zu springen: Die Sitzung gilt vielleicht noch, nur der Server ist nicht erreichbar. */
function ServerAntwortetNicht() {
    const { erneutPruefen } = useAuth();
    return (
        <div role="alert" className="min-h-screen bg-slate-50 flex items-center justify-center">
            <VerbindungsHinweis
                text="Der Server antwortet gerade nicht. Bitte die Internetverbindung prüfen und es erneut versuchen."
                onErneut={() => void erneutPruefen()}
            />
        </div>
    );
}

export function RequireAuth({ children }: { children: React.ReactElement }) {
    const { loading, isAuthenticated, user, isAdmin, verbindungsProblem } = useAuth();
    const location = useLocation();

    if (loading) {
        return <FullscreenLoading />;
    }

    if (!isAuthenticated && verbindungsProblem) {
        return <ServerAntwortetNicht />;
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
    const { loading, isAuthenticated, isAdmin, verbindungsProblem } = useAuth();

    if (loading) {
        return <FullscreenLoading />;
    }

    if (!isAuthenticated && verbindungsProblem) {
        return <ServerAntwortetNicht />;
    }

    if (!isAuthenticated) {
        return <Navigate to="/login" replace />;
    }

    if (!isAdmin) {
        return <Navigate to="/projekte" replace />;
    }

    return children;
}
