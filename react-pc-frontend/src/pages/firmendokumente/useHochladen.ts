import { useState } from 'react';
import { teilePdfDateien } from './firmendokumenteLogik';

interface Warteschlange {
    dateien: File[];
    ordnerId: number;
    /** Wie viele PDFs zu Beginn in der Schlange standen (für „Datei 2 von 3“). */
    anzahl: number;
}

/**
 * Warteschlange beim Hochladen: Ausgewählte oder abgelegte Dateien werden
 * geprüft (nur PDFs), Ablehnungen gehen an `meldeFehler`. Danach kommt je PDF
 * ein Dialog, eins nach dem anderen.
 */
export function useHochladen(meldeFehler: (text: string) => void) {
    const [schlange, setSchlange] = useState<Warteschlange | null>(null);

    const annehmen = (dateien: ArrayLike<File> | null | undefined, ordnerId: number) => {
        const { pdfs, meldungen } = teilePdfDateien(dateien);
        meldungen.forEach((text) => meldeFehler(text));
        if (pdfs.length === 0) return;
        setSchlange((alt) => (alt
            // Kommen während des Hochladens weitere Dateien, reihen sie sich hinten an.
            ? { ...alt, dateien: [...alt.dateien, ...pdfs], anzahl: alt.anzahl + pdfs.length }
            : { dateien: pdfs, ordnerId, anzahl: pdfs.length }));
    };

    /** Nächste Datei; sie startet im zuletzt gewählten Ordner. */
    const weiter = (ordnerId: number) => setSchlange((alt) => (alt && alt.dateien.length > 1 ? { ...alt, ordnerId, dateien: alt.dateien.slice(1) } : null));
    const abbrechen = () => setSchlange(null);

    const aktuelle = schlange
        ? { datei: schlange.dateien[0], ordnerId: schlange.ordnerId, nummer: schlange.anzahl - schlange.dateien.length + 1, anzahl: schlange.anzahl }
        : null;

    return { aktuelle, annehmen, weiter, abbrechen };
}
