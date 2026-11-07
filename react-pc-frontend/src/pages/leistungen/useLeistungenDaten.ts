import { useCallback, useEffect, useMemo, useState } from 'react';
import { useToast } from '../../components/ui/toast';
import { useVerzoegertesLoeschen } from '../../hooks/useVerzoegertesLoeschen';
import { ladeLeistungen, ladeOrdner, speichereLeistung } from './api';
import { baueBaum, leistungsZaehler } from './ordnerLogik';
import type { Leistung, Ordner } from './typen';

export type LadeStatus = 'laedt' | 'fertig' | 'fehler';

/**
 * Daten der Seite „Leistungen & Kategorien“: lädt Ordner und Leistungen,
 * blendet zum Löschen geplante Einträge aus (`planen`, mit „Rückgängig“) und
 * leitet daraus Baum und Zähler ab. Änderungen gehen über die Helfer hier,
 * damit die Seite nie an den Rohlisten vorbeischreibt.
 */
export function useLeistungenDaten() {
    const toast = useToast();
    const [ordnerRoh, setOrdnerRoh] = useState<Ordner[]>([]);
    const [leistungenRoh, setLeistungenRoh] = useState<Leistung[]>([]);
    const [ladeStatus, setLadeStatus] = useState<LadeStatus>('laedt');
    const [ladeFehler, setLadeFehler] = useState<string | null>(null);
    const [ladeVersuch, setLadeVersuch] = useState(0);

    const { ausgeblendet, planen } = useVerzoegertesLoeschen();
    const ordner = useMemo(() => ordnerRoh.filter((o) => !ausgeblendet.has(`ordner:${o.id}`)), [ordnerRoh, ausgeblendet]);
    const leistungen = useMemo(() => leistungenRoh.filter((l) => !ausgeblendet.has(`leistung:${l.id}`)), [leistungenRoh, ausgeblendet]);
    const baum = useMemo(() => baueBaum(ordner), [ordner]);
    const zaehler = useMemo(() => leistungsZaehler(baum, leistungen), [baum, leistungen]);

    // Als Promise-Kette, nicht mit await: setState nach await im Effekt meldet der React-Compiler-Lint.
    useEffect(() => {
        let gueltig = true;
        Promise.all([ladeOrdner(), ladeLeistungen()])
            .then(([neueOrdner, neueLeistungen]) => {
                if (!gueltig) return;
                setOrdnerRoh(neueOrdner);
                setLeistungenRoh(neueLeistungen);
                setLadeStatus('fertig');
            })
            .catch((err: unknown) => {
                if (!gueltig) return;
                setLadeFehler(err instanceof Error ? err.message : 'Die Daten konnten nicht geladen werden.');
                setLadeStatus('fehler');
            });
        return () => { gueltig = false; };
    }, [ladeVersuch]);

    const erneutLaden = () => {
        setLadeStatus('laedt');
        setLadeFehler(null);
        setLadeVersuch((v) => v + 1);
    };

    /** Nach Änderungen im Hintergrund neu laden – ohne Ladeanzeige; ein Fehler kommt als Meldung. */
    const neuLaden = async () => {
        try {
            const [neueOrdner, neueLeistungen] = await Promise.all([ladeOrdner(), ladeLeistungen()]);
            setOrdnerRoh(neueOrdner);
            setLeistungenRoh(neueLeistungen);
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Die Daten konnten nicht neu geladen werden.');
        }
    };

    /** Gespeicherten Ordner übernehmen; die Projektzahl bleibt, wenn die Antwort keine mitbringt. */
    const ersetzeOrdner = useCallback((neu: Ordner) => setOrdnerRoh((alt) => alt.map((o) => (
        o.id === neu.id ? { ...neu, projektAnzahl: neu.projektAnzahl || o.projektAnzahl } : o
    ))), []);
    const entferneOrdner = useCallback((id: number) => setOrdnerRoh((alt) => alt.filter((o) => o.id !== id)), []);

    /** Neue Leistung anhängen, bestehende ersetzen. */
    const uebernimmLeistung = useCallback((neu: Leistung) => setLeistungenRoh((alt) => (
        alt.some((l) => l.id === neu.id) ? alt.map((l) => (l.id === neu.id ? neu : l)) : [...alt, neu]
    )), []);
    const entferneLeistung = useCallback((id: number) => setLeistungenRoh((alt) => alt.filter((l) => l.id !== id)), []);

    /** Speichert eine Leistung und übernimmt sie in die Liste. Wirft bei einem Fehler. */
    const leistungSpeichern = async (leistung: Omit<Leistung, 'id'> & { id?: number }) => {
        const neu = await speichereLeistung(leistung);
        uebernimmLeistung(neu);
        return neu;
    };

    return {
        ordnerRoh, ordner, leistungen, baum, zaehler, ladeStatus, ladeFehler,
        erneutLaden, neuLaden, planen,
        ersetzeOrdner, entferneOrdner, uebernimmLeistung, entferneLeistung, leistungSpeichern,
    };
}
