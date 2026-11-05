import { Clock } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useOptionalAuth } from '../../auth/AuthContext';
import { STUNDENART_EINSTELLUNG_PFAD, stundenartName, type Stundenart } from './stundenart';

/**
 * Gut sichtbar: Welche Stunden gehen an den Steuerberater? Bewusst kein
 * Umschalter – die Wahl ist eine Admin-Einstellung, damit niemand
 * versehentlich das Falsche schickt.
 */
export function StundenartHinweis({ art }: { art: Stundenart }) {
    const istAdmin = useOptionalAuth()?.isAdmin ?? false;
    return (
        <div className="flex items-start gap-3 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5">
            <Clock aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-rose-600" />
            <div className="text-sm">
                <p className="text-slate-700">
                    Übermittelt werden: <strong className="text-slate-900">{stundenartName(art)}</strong>
                </p>
                <p className="text-xs text-slate-500">
                    {istAdmin
                        ? <>Ändern unter <Link to={STUNDENART_EINSTELLUNG_PFAD} className="font-medium text-rose-700 underline-offset-2 hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 rounded">Einstellungen → Zeiterfassung</Link></>
                        : 'Ein Admin kann das unter Einstellungen → Zeiterfassung ändern.'}
                </p>
            </div>
        </div>
    );
}
