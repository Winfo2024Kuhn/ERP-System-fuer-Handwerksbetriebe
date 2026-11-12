import { useEffect, useRef, useState } from 'react';
import { useToast } from '../../components/ui/toast';
import { fehlerText } from '../../lib/apiFehler';
import { ladeHoch } from './api';
import { nameAusDateiname } from './firmendokumenteLogik';
import { GLEICHZEITIG, stapelMeldung, type StapelFehler } from './stapelHochladen';

/** Fortschritt eines laufenden Stapels („12 von 40“). */
export interface StapelStand {
    gesamt: number;
    fertig: number;
    /** „Abbrechen“ gedrückt: es startet nichts mehr, Laufendes geht noch zu Ende. */
    abgebrochen?: boolean;
}

interface Optionen {
    /** Nach der letzten Datei: Liste und Zähler einmal neu laden. */
    onFertig: () => void;
    /** Name des Ordners für die Meldung am Ende. */
    ordnerName: (ordnerId: number) => string | undefined;
}

/**
 * Lädt mehrere Dateien ohne Dialog hoch: Name aus dem Dateinamen, kein
 * Fällig am, höchstens drei gleichzeitig. Kommen während des Laufs weitere
 * Dateien, reihen sie sich ein. `abbrechen` leert die Warteschlange, laufende
 * Uploads gehen zu Ende. Am Ende genau eine Meldung (Erfolg, Abbruch oder,
 * bei Teilfehlern, welche Dateien nicht klappten) und einmal `onFertig`.
 */
export function useStapelHochladen({ onFertig, ordnerName }: Optionen) {
    const toast = useToast();
    const [stand, setStand] = useState<StapelStand | null>(null);
    const schlange = useRef<{ datei: File; ordnerId: number }[]>([]);
    const lauf = useRef({ aktiv: 0, gesamt: 0, abgebrochen: 0, fehler: [] as StapelFehler[], ordner: new Set<number>() });
    const neueste = useRef({ onFertig, ordnerName, toast });
    useEffect(() => { neueste.current = { onFertig, ordnerName, toast }; });

    const abschliessen = () => {
        const { gesamt, fehler, ordner, abgebrochen } = lauf.current;
        const einziger = ordner.size === 1 ? neueste.current.ordnerName([...ordner][0]) ?? null : null;
        const meldung = stapelMeldung({ gesamt, fehler, ordnerName: einziger, abgebrochen });
        lauf.current = { aktiv: 0, gesamt: 0, abgebrochen: 0, fehler: [], ordner: new Set() };
        setStand(null);
        const { toast } = neueste.current;
        if (meldung.art === 'erfolg') toast.success(meldung.text);
        else if (meldung.art === 'hinweis') toast.info(meldung.text);
        else toast.error(meldung.text);
        neueste.current.onFertig();
    };

    const weiter = () => {
        while (lauf.current.aktiv < GLEICHZEITIG && schlange.current.length > 0) {
            const { datei, ordnerId } = schlange.current.shift() as { datei: File; ordnerId: number };
            lauf.current.aktiv += 1;
            ladeHoch(datei, { name: nameAusDateiname(datei.name), ordnerId, faelligAm: null })
                .catch((err: unknown) => { lauf.current.fehler.push({ name: datei.name, grund: fehlerText(err, 'Die Datei konnte nicht hochgeladen werden.') }); })
                .finally(() => {
                    lauf.current.aktiv -= 1;
                    setStand((alt) => (alt ? { ...alt, fertig: alt.fertig + 1 } : alt));
                    if (lauf.current.aktiv === 0 && schlange.current.length === 0) abschliessen();
                    else weiter();
                });
        }
    };

    const starten = (dateien: readonly File[], ordnerId: number) => {
        schlange.current.push(...dateien.map((datei) => ({ datei, ordnerId })));
        lauf.current.gesamt += dateien.length;
        lauf.current.ordner.add(ordnerId);
        const gesamt = lauf.current.gesamt;
        // Neue Dateien nach „Abbrechen“ laufen wieder normal: der Knopf heißt wieder „Abbrechen“.
        setStand((alt) => ({ gesamt, fertig: alt?.fertig ?? 0 }));
        weiter();
    };

    const abbrechen = () => {
        lauf.current.abgebrochen += schlange.current.length;
        schlange.current = [];
        setStand((alt) => (alt ? { ...alt, abgebrochen: true } : alt));
        if (lauf.current.aktiv === 0 && lauf.current.gesamt > 0) abschliessen();
    };

    return { stand, laeuft: stand !== null, starten, abbrechen };
}
