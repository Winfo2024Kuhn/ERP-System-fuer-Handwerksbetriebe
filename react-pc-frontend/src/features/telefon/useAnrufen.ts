import { useCallback, useEffect, useRef, useState } from 'react';
import { useToast } from '../../components/ui/toast';
import { AnrufenFehler, ladeWaehlTelefone, rufeAn } from './api';
import { liesWaehlTelefon, useWaehlTelefon } from './useWaehlTelefon';

/**
 * Ablauf von „Zurückrufen": Telefon an diesem Rechner klingeln lassen, nach
 * dem Abnehmen wählt die FRITZ!Box die Nummer.
 *
 * <ul>
 *   <li>Ist noch kein Telefon gespeichert, öffnet sich zuerst die Auswahl
 *       ({@link auswahlOffen}); nach der Wahl wird gespeichert und sofort
 *       angerufen.</li>
 *   <li>Während der Anfrage ist {@link laeuft} gesetzt – ein zweiter Klick
 *       startet keinen zweiten Anruf.</li>
 *   <li>Fehler zeigt ein Toast mit genau der Meldung des Servers. Lehnt der
 *       Server mit 400 ab und kennt die FRITZ!Box das gespeicherte Telefon
 *       nicht mehr, wird es vergessen und die Auswahl erneut angeboten. (Eine
 *       400 gibt es auch bei einer nicht wählbaren Nummer – dann bleibt das
 *       Telefon stehen.)</li>
 * </ul>
 *
 * @param onGestartet wird nach erfolgreichem Start aufgerufen (z. B. um das
 *   Anruf-Fenster zu schließen).
 */

export const ANRUF_GESTARTET_TEXT = 'Ihr Telefon klingelt – abnehmen, dann wird verbunden.';

async function telefonUnbekannt(telefon: string): Promise<boolean> {
    try {
        const telefone = await ladeWaehlTelefone();
        return !telefone.some((t) => t.name === telefon);
    } catch {
        // Liste nicht ladbar: lieber nichts vergessen – die Meldung steht schon im Toast.
        return false;
    }
}

export function useAnrufen(onGestartet?: () => void) {
    const toast = useToast();
    const { telefon, setzeTelefon, vergiss } = useWaehlTelefon();
    const [laeuft, setLaeuft] = useState(false);
    const laeuftRef = useRef(false);
    /** Nummer, die nach der Telefon-Auswahl gewählt wird; gesetzt = Auswahl offen. */
    const [wartendeNummer, setWartendeNummer] = useState<string | null>(null);
    const onGestartetRef = useRef(onGestartet);

    useEffect(() => {
        onGestartetRef.current = onGestartet;
    }, [onGestartet]);

    const waehle = useCallback(async (mitTelefon: string, nummer: string) => {
        if (laeuftRef.current) return;
        laeuftRef.current = true;
        setLaeuft(true);
        try {
            await rufeAn(mitTelefon, nummer);
            toast.success(ANRUF_GESTARTET_TEXT);
            onGestartetRef.current?.();
        } catch (fehler) {
            toast.error(fehler instanceof Error ? fehler.message : 'Der Anruf konnte nicht gestartet werden.');
            if (fehler instanceof AnrufenFehler && fehler.status === 400 && await telefonUnbekannt(mitTelefon)) {
                vergiss();
                setWartendeNummer(nummer);
            }
        } finally {
            laeuftRef.current = false;
            setLaeuft(false);
        }
    }, [toast, vergiss]);

    const anrufen = useCallback((nummer: string) => {
        if (laeuftRef.current || !nummer.trim()) return;
        // Frisch lesen: ein anderer Knopf kann das Telefon gerade erst gespeichert haben.
        const gespeichert = liesWaehlTelefon();
        if (!gespeichert) {
            setWartendeNummer(nummer);
            return;
        }
        void waehle(gespeichert, nummer);
    }, [waehle]);

    const telefonGewaehlt = useCallback((name: string) => {
        setzeTelefon(name);
        const nummer = wartendeNummer;
        setWartendeNummer(null);
        if (nummer) void waehle(name, nummer);
    }, [setzeTelefon, waehle, wartendeNummer]);

    const auswahlAbbrechen = useCallback(() => setWartendeNummer(null), []);

    return {
        anrufen,
        laeuft,
        telefon,
        auswahlOffen: wartendeNummer !== null,
        telefonGewaehlt,
        auswahlAbbrechen,
    };
}
