import type { ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { EyeOff } from 'lucide-react';
import { Card } from '../../components/ui/card';
import { istZeiterfassungPfad } from '../../components/layout/navigation';
import { useZeiterfassungSichtbarkeit } from './useZeiterfassungSichtbarkeit';

/**
 * Schützt alle Seiten des Menü-Bereichs „Zeiterfassung“ – auch den direkten
 * Aufruf per Adresse oder gespeichertem Link.
 *
 * <p>Welche Seiten dazugehören, kommt aus dem Menü selbst
 * ({@link istZeiterfassungPfad}); alle anderen Seiten laufen unverändert durch.
 * Solange das Recht noch geprüft wird, bleibt es neutral (kein Aufblitzen der
 * Seite), ohne Recht erscheint ein freundlicher Hinweis statt eines Fehlers.</p>
 */
export function ZeiterfassungSchutz({ children }: { children: ReactNode }) {
    const { pathname } = useLocation();
    const darf = useZeiterfassungSichtbarkeit();

    if (!istZeiterfassungPfad(pathname) || darf === true) return <>{children}</>;

    if (darf === null) {
        return (
            <p role="status" className="rounded-lg bg-slate-100 p-6 text-slate-600 motion-safe:animate-pulse">
                Zugang wird geprüft …
            </p>
        );
    }

    return (
        <Card className="mx-auto mt-8 flex max-w-xl flex-col items-center gap-3 p-10 text-center">
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-500">
                <EyeOff aria-hidden="true" className="h-6 w-6" />
            </div>
            <h2 className="text-lg font-semibold text-slate-900">Die Zeiterfassung ist für deinen Zugang ausgeblendet.</h2>
            <p className="text-slate-500">Frag im Büro nach, wenn du sie brauchst.</p>
        </Card>
    );
}
