import { useConfirm } from '../../components/ui/confirm-dialog';
import { useToast } from '../../components/ui/toast';
import { fehlerText } from '../../lib/apiFehler';
import type { Baum } from '../../lib/ordnerBaum';
import { verschiebeOrdner } from './api';
import { rueckgaengigMoeglich, sichtbarkeitsRueckfrage } from './ordnerSichtbarkeit';
import type { FirmenOrdner } from './typen';

const FEHLER = 'Der Ordner konnte nicht verschoben werden.';

/**
 * Verschiebt einen Ordner (samt Unterordnern und Dokumenten) in einen anderen
 * Ordner oder – mit `ziel = null` – nach ganz oben. Ändert sich dadurch, wer
 * ihn sehen darf, fragt vorher `useConfirm`. Danach Erfolg als Toast mit
 * „Rückgängig“ (zurück in den alten Ordner) und `onVerschoben` (Baum neu
 * laden; die Auswahl bleibt, sie steht in der Adresse). Berührt ein
 * Hauptordner dabei einen eingeschränkten Zweig, gibt es kein „Rückgängig“
 * (siehe `rueckgaengigMoeglich`): Er käme sonst mit falschen Rechten zurück.
 *
 * - `perZiehen`: Fehler als Toast, Ergebnis `true`/`false`.
 * - `verschieben`: für den Dialog „Verschieben …“ – wirft Fehler, damit er sie
 *   selbst anzeigt; `false` heißt Rückfrage abgebrochen.
 */
export function useOrdnerVerschieben(baum: Baum<FirmenOrdner>, onVerschoben: () => void) {
    const toast = useToast();
    const confirm = useConfirm();

    const zurueck = (ordner: FirmenOrdner, alteElternId: number | null) => {
        const alteEltern = alteElternId === null ? undefined : baum.byId.get(alteElternId);
        verschiebeOrdner(ordner.id, alteElternId)
            .then(() => {
                toast.success(alteEltern ? `„${ordner.name}“ ist wieder in „${alteEltern.name}“.` : `„${ordner.name}“ ist wieder ein Hauptordner.`);
                onVerschoben();
            })
            .catch((err: unknown) => toast.error(fehlerText(err, 'Der Ordner konnte nicht zurückgeschoben werden.')));
    };

    const verschieben = async (ordner: FirmenOrdner, ziel: FirmenOrdner | null): Promise<boolean> => {
        const frage = sichtbarkeitsRueckfrage(baum, ordner.id, ziel?.id ?? null);
        if (frage && !(await confirm({ title: 'Ordner verschieben?', message: frage, confirmLabel: 'Verschieben', variant: 'warning' }))) return false;
        const alteElternId = ordner.parentId;
        const zuruecknehmbar = rueckgaengigMoeglich(baum, ordner.id, ziel?.id ?? null);
        await verschiebeOrdner(ordner.id, ziel?.id ?? null);
        toast.success(ziel ? `„${ordner.name}“ nach „${ziel.name}“ verschoben.` : `„${ordner.name}“ ist jetzt ein Hauptordner.`, zuruecknehmbar
            ? { aktion: { label: 'Rückgängig', onClick: () => zurueck(ordner, alteElternId) } }
            : undefined);
        onVerschoben();
        return true;
    };

    const perZiehen = (ordner: FirmenOrdner, ziel: FirmenOrdner | null): Promise<boolean> => verschieben(ordner, ziel)
        .catch((err: unknown) => {
            toast.error(fehlerText(err, FEHLER));
            return false;
        });

    return { verschieben, perZiehen };
}
