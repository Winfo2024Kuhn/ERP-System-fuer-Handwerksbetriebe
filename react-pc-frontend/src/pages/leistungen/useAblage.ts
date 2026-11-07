import { useState, type DragEvent } from 'react';
import { ersteDatei } from './ordnerLogik';
import type { ZiehObjekt } from './typen';

function hatDateien(event: DragEvent) {
    return Array.from(event.dataTransfer.types).includes('Files');
}

/**
 * Gemeinsame Handler für eine Zeile im Ordnerbaum, auf die etwas gezogen
 * werden kann: ein Ordner oder eine Leistung aus der Seite (`ziehen`) oder eine
 * Bilddatei aus dem Finder (wird zum Ordnerbild). Ungültige Ziele nehmen nichts an.
 */
export function useAblage(optionen: {
    ziehen: ZiehObjekt | null;
    erlaubt: boolean;
    bilderErlaubt: boolean;
    onAblegen: () => void;
    onBild?: (datei: File) => void;
}) {
    const [drueber, setDrueber] = useState<'objekt' | 'bild' | null>(null);
    const handler = {
        onDragOver: (event: DragEvent<HTMLDivElement>) => {
            if (optionen.ziehen) {
                if (!optionen.erlaubt) { event.dataTransfer.dropEffect = 'none'; return; }
                event.preventDefault();
                event.dataTransfer.dropEffect = 'move';
                setDrueber('objekt');
            } else if (optionen.bilderErlaubt && hatDateien(event)) {
                event.preventDefault();
                event.dataTransfer.dropEffect = 'copy';
                setDrueber('bild');
            }
        },
        onDragLeave: (event: DragEvent<HTMLDivElement>) => {
            if (event.currentTarget.contains(event.relatedTarget as Node | null)) return;
            setDrueber(null);
        },
        onDrop: (event: DragEvent<HTMLDivElement>) => {
            event.preventDefault();
            setDrueber(null);
            if (optionen.ziehen) {
                if (optionen.erlaubt) optionen.onAblegen();
                return;
            }
            const datei = ersteDatei(event.dataTransfer.files);
            if (datei && optionen.onBild) optionen.onBild(datei);
        },
    };
    return { drueber: optionen.ziehen && !optionen.erlaubt ? null : drueber, handler };
}
