import { Suspense, type ReactNode } from 'react';
import { ErrorBoundary } from '../ErrorBoundary';
import { LadeAnzeige } from '../ui/lade-anzeige';

/**
 * Rahmen für jede Route: Die Seite wird erst beim Öffnen nachgeladen (React.lazy in App.tsx),
 * bis dahin bleibt das Menü stehen und der Inhaltsbereich zeigt eine Ladeanzeige.
 * Fehler – auch eine nicht ladbare Seitendatei – fängt die ErrorBoundary ab.
 */
export function Seite({ children, vollbild = false }: { children: ReactNode; vollbild?: boolean }) {
    return (
        <ErrorBoundary>
            <Suspense fallback={<LadeAnzeige text="Seite wird geladen …" vollbild={vollbild} />}>
                {children}
            </Suspense>
        </ErrorBoundary>
    );
}
