import { useCallback, useEffect, useRef, useState } from 'react';
import type { GeladenesPdf } from './api';

export interface ErstellterBericht {
    id: number;
    titel: string;
    dateiname: string;
    groesse: number;
    /** `blob:`-Adresse zum Öffnen und Herunterladen. */
    url: string;
    erstelltUm: Date;
}

/**
 * „Zuletzt erstellt“: PDFs dieser Sitzung, nur im Speicher des Browsers.
 * Die `blob:`-Adressen werden beim Verlassen der Seite freigegeben.
 */
export function useZuletztErstellt() {
    const [liste, setListe] = useState<ErstellterBericht[]>([]);
    const urls = useRef<string[]>([]);
    const naechsteId = useRef(1);

    useEffect(() => () => {
        urls.current.forEach(url => URL.revokeObjectURL(url));
        urls.current = [];
    }, []);

    const hinzufuegen = useCallback((pdf: GeladenesPdf, titel: string): string => {
        const url = URL.createObjectURL(pdf.blob);
        urls.current.push(url);
        const eintrag: ErstellterBericht = {
            id: naechsteId.current++, titel, dateiname: pdf.dateiname, groesse: pdf.blob.size, url, erstelltUm: new Date(),
        };
        setListe(alt => [eintrag, ...alt]);
        return url;
    }, []);

    return { liste, hinzufuegen };
}
