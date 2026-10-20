import { Clock } from 'lucide-react';
import { InfoAbschnitt, InfoButton } from '../../components/ui/info-button';
import type { FolderType } from './emailCenterModel';

/** Ordner, die der nächtliche Aufräumlauf nach 30 Tagen endgültig leert (EmailCleanupService). */
const AUTOMATISCH_GELEERT: ReadonlySet<FolderType> = new Set(['trash', 'spam', 'newsletter']);

const DREISSIG_TAGE_HINWEIS = 'E-Mails in diesem Ordner werden nach 30 Tagen automatisch endgültig gelöscht.';

/**
 * Schmale Kopfzeile über der Mailliste, die erklärt, was in einem Ordner
 * besonders ist. Ordner ohne Besonderheit bekommen keine Zeile.
 */
export function OrdnerHinweis({ ordner }: { ordner: FolderType }) {
    if (AUTOMATISCH_GELEERT.has(ordner)) {
        return (
            <p className="flex items-start gap-1.5 border-b border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-500">
                <Clock className="mt-px h-3.5 w-3.5 shrink-0 text-slate-400" aria-hidden="true" />
                {DREISSIG_TAGE_HINWEIS}
            </p>
        );
    }
    if (ordner === 'drafts') {
        return (
            <div className="flex items-center gap-1 border-b border-slate-200 px-3 py-1.5">
                <h2 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Entwürfe</h2>
                <InfoButton titel="Entwürfe">
                    <InfoAbschnitt titel="Was passiert im Hintergrund?">
                        <p>Jeder Entwurf gehört dem Benutzer, der ihn angelegt hat.</p>
                    </InfoAbschnitt>
                    <InfoAbschnitt titel="Was bewirkt das?">
                        <p>Du siehst nur deine eigenen Entwürfe. Admins sehen alle. Ältere Entwürfe von vor dieser Änderung sehen weiterhin alle.</p>
                    </InfoAbschnitt>
                </InfoButton>
            </div>
        );
    }
    if (ordner === 'unassigned') {
        return (
            <div className="flex items-center gap-1 border-b border-slate-200 px-3 py-1.5">
                <h2 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Nicht zugeordnet</h2>
                <InfoButton titel="Nicht zugeordnet">
                    <InfoAbschnitt titel="Was passiert im Hintergrund?">
                        <p>Eingehende Mails werden automatisch zugeordnet: einem Lieferanten, wenn die Absender-Domain zu ihm passt, sonst einem Projekt oder einer Anfrage, wenn die Absender-Adresse beim Kunden hinterlegt ist. Passen mehrere, entscheiden Stichwörter im Betreff und die KI.</p>
                    </InfoAbschnitt>
                    <InfoAbschnitt titel="Was bewirkt das?">
                        <p>Hier landen Mails von bekannten Kunden-Adressen, bei denen das nicht eindeutig ging – z. B. weil ein Kunde mehrere laufende Projekte hat. Mails von unbekannten Absendern bleiben im Posteingang. Ordne sie mit „Zuordnen“ selbst zu.</p>
                    </InfoAbschnitt>
                </InfoButton>
            </div>
        );
    }
    return null;
}
