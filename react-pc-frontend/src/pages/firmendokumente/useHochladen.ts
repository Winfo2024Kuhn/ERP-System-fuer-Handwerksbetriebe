import { useState } from 'react';
import { teileDateien } from './firmendokumenteLogik';

interface Eintrag {
    datei: File;
    /** Ordner, auf dem die Datei abgelegt bzw. in dem sie ausgewählt wurde. */
    ordnerId: number;
}

interface Warteschlange {
    dateien: Eintrag[];
    /** Wie viele Dateien zu Beginn in der Schlange standen (für „Datei 2 von 3“). */
    anzahl: number;
}

/**
 * Warteschlange beim Hochladen: Ausgewählte oder abgelegte Dateien werden
 * geprüft (leer, zu groß, Programmdatei), Ablehnungen gehen an `meldeFehler`. Danach kommt je Datei
 * ein Dialog, eins nach dem anderen. Jede Datei kommt in den Ordner, auf dem
 * sie abgelegt bzw. in dem sie ausgewählt wurde.
 */
export function useHochladen(meldeFehler: (text: string) => void) {
    const [schlange, setSchlange] = useState<Warteschlange | null>(null);

    const annehmen = (dateien: ArrayLike<File> | null | undefined, ordnerId: number) => {
        const { dateien: brauchbar, meldungen } = teileDateien(dateien);
        meldungen.forEach((text) => meldeFehler(text));
        if (brauchbar.length === 0) return;
        const neu = brauchbar.map((datei) => ({ datei, ordnerId }));
        setSchlange((alt) => (alt
            // Kommen während des Hochladens weitere Dateien, reihen sie sich hinten an.
            ? { dateien: [...alt.dateien, ...neu], anzahl: alt.anzahl + neu.length }
            : { dateien: neu, anzahl: neu.length }));
    };

    /** Nächste Datei der Schlange (oder fertig). */
    const weiter = () => setSchlange((alt) => (alt && alt.dateien.length > 1 ? { ...alt, dateien: alt.dateien.slice(1) } : null));
    const abbrechen = () => setSchlange(null);

    const aktuelle = schlange
        ? { ...schlange.dateien[0], nummer: schlange.anzahl - schlange.dateien.length + 1, anzahl: schlange.anzahl }
        : null;

    return { aktuelle, annehmen, weiter, abbrechen };
}
