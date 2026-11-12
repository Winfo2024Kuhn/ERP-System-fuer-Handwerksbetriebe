import { useState } from 'react';
import { teileDateien } from './firmendokumenteLogik';
import { useStapelHochladen } from './useStapelHochladen';

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

interface Optionen {
    /** Abgelehnte Dateien (leer, zu groß, Programmdatei) – je eine Meldung. */
    meldeFehler: (text: string) => void;
    /** Nach einem Stapel: Liste und Zähler einmal neu laden. */
    onStapelFertig: () => void;
    /** Name des Ordners für die Meldung am Ende eines Stapels. */
    ordnerName: (ordnerId: number) => string | undefined;
}

/**
 * Hochladen ausgewählter oder abgelegter Dateien. Erst die Prüfung (leer, zu
 * groß, Programmdatei), Ablehnungen gehen an `meldeFehler`. Eine einzelne
 * Datei fragt per Dialog nach Name und Fällig am (`aktuelle`); ab zwei
 * Dateien – und solange ein Stapel läuft – geht alles ohne Dialog hoch
 * (`stapel` zeigt den Fortschritt). Jede Datei kommt in den Ordner, auf dem
 * sie abgelegt bzw. in dem sie ausgewählt wurde.
 */
export function useHochladen({ meldeFehler, onStapelFertig, ordnerName }: Optionen) {
    const [schlange, setSchlange] = useState<Warteschlange | null>(null);
    const stapel = useStapelHochladen({ onFertig: onStapelFertig, ordnerName });

    const annehmen = (dateien: ArrayLike<File> | null | undefined, ordnerId: number) => {
        const { dateien: brauchbar, meldungen } = teileDateien(dateien);
        meldungen.forEach((text) => meldeFehler(text));
        if (brauchbar.length === 0) return;
        if (brauchbar.length > 1 || stapel.laeuft) {
            stapel.starten(brauchbar, ordnerId);
            return;
        }
        const neu = { datei: brauchbar[0], ordnerId };
        setSchlange((alt) => (alt
            // Kommt während des Dialogs eine weitere einzelne Datei, reiht sie sich hinten an.
            ? { dateien: [...alt.dateien, neu], anzahl: alt.anzahl + 1 }
            : { dateien: [neu], anzahl: 1 }));
    };

    /** Nächste Datei der Schlange (oder fertig). */
    const weiter = () => setSchlange((alt) => (alt && alt.dateien.length > 1 ? { ...alt, dateien: alt.dateien.slice(1) } : null));
    const abbrechen = () => setSchlange(null);

    const aktuelle = schlange
        ? { ...schlange.dateien[0], nummer: schlange.anzahl - schlange.dateien.length + 1, anzahl: schlange.anzahl }
        : null;

    return { aktuelle, annehmen, weiter, abbrechen, stapel: stapel.stand, stapelAbbrechen: stapel.abbrechen };
}
