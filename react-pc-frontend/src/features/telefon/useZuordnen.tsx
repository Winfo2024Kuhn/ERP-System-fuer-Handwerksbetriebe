import { useCallback, useState } from 'react';
import type { ReactNode } from 'react';
import { useToast } from '../../components/ui/toast';
import { refreshNotifications } from '../../lib/notificationRefresh';
import { hebeZuordnungAuf, ordneZu, zuordnenDaten } from './api';
import { TelefonZuordnenDialog } from './TelefonZuordnenDialog';
import type { KontaktKurz, Sprachnachricht, TelefonAnruf, ZuordnenZiel } from './types';

/**
 * Die drei Zuordnungs-Aktionen, die Anrufliste, Anrufbeantworter und der
 * Reiter „Anrufe" in der Akte gleichermaßen brauchen: Dialog öffnen, einen
 * der möglichen Kontakte direkt übernehmen, Zuordnung aufheben.
 *
 * <p>Das Ergebnis vom Server geht an `onAktualisiert`, damit die Liste den
 * geänderten Eintrag ersetzt – ohne alles neu zu laden.</p>
 */
export function useZuordnen<T extends TelefonAnruf | Sprachnachricht>(onAktualisiert: (ziel: ZuordnenZiel, ergebnis: T) => void) {
    const toast = useToast();
    const [dialog, setDialog] = useState<{ ziel: ZuordnenZiel; nummer: string } | null>(null);
    const [beschaeftigt, setBeschaeftigt] = useState<string | null>(null);

    const schluessel = (ziel: ZuordnenZiel) => `${ziel.art}-${ziel.id}`;

    const oeffneDialog = useCallback((ziel: ZuordnenZiel, nummer: string) => setDialog({ ziel, nummer }), []);

    const kandidatWaehlen = useCallback(async (ziel: ZuordnenZiel, kontakt: KontaktKurz) => {
        setBeschaeftigt(schluessel(ziel));
        try {
            // Die Nummer steht bei allen Kandidaten schon – nichts zu merken.
            const ergebnis = await ordneZu<T>(ziel, zuordnenDaten(kontakt.typ, kontakt.id, false));
            onAktualisiert(ziel, ergebnis);
            refreshNotifications();
            toast.success(`${kontakt.name} zugeordnet.`);
        } catch (fehler) {
            toast.error(fehler instanceof Error ? fehler.message : 'Der Kontakt konnte nicht zugeordnet werden.');
        } finally {
            setBeschaeftigt(null);
        }
    }, [onAktualisiert, toast]);

    const aufheben = useCallback(async (ziel: ZuordnenZiel) => {
        setBeschaeftigt(schluessel(ziel));
        try {
            const ergebnis = await hebeZuordnungAuf<T>(ziel);
            onAktualisiert(ziel, ergebnis);
            refreshNotifications();
            toast.success('Zuordnung aufgehoben.');
        } catch (fehler) {
            toast.error(fehler instanceof Error ? fehler.message : 'Die Zuordnung konnte nicht aufgehoben werden.');
        } finally {
            setBeschaeftigt(null);
        }
    }, [onAktualisiert, toast]);

    const dialogElement: ReactNode = dialog ? (
        <TelefonZuordnenDialog<T>
            key={schluessel(dialog.ziel)}
            offen
            ziel={dialog.ziel}
            nummer={dialog.nummer}
            onSchliessen={() => setDialog(null)}
            onZugeordnet={(ergebnis) => onAktualisiert(dialog.ziel, ergebnis)}
        />
    ) : null;

    const istBeschaeftigt = (ziel: ZuordnenZiel) => beschaeftigt === schluessel(ziel);

    return { oeffneDialog, kandidatWaehlen, aufheben, dialogElement, istBeschaeftigt };
}
