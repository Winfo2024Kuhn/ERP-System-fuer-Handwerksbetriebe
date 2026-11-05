import { useEffect, useState } from 'react';
import { fehlerText } from '../../lib/apiFehler';
import { ladePdf, type GeladenesPdf } from './api';

export type PdfAnhangStand =
    | { art: 'laedt' }
    | { art: 'fertig'; pdf: GeladenesPdf; url: string }
    | { art: 'fehler'; meldung: string };

/**
 * Lädt ein PDF als Mail-Anhang, solange `url` gesetzt ist. Für die Vorschau
 * gibt es eine `blob:`-Adresse, die beim Schließen bzw. Wechsel wieder
 * freigegeben wird. `url = null` heißt: nichts laden (Dialog zu).
 */
export function usePdfAnhang(url: string | null, ersatzName: string): PdfAnhangStand {
    const [stand, setStand] = useState<PdfAnhangStand>({ art: 'laedt' });
    const [fuer, setFuer] = useState(url);
    if (fuer !== url) {
        setFuer(url);
        setStand({ art: 'laedt' });
    }

    useEffect(() => {
        if (!url) return;
        const abbruch = new AbortController();
        let objektUrl: string | null = null;
        ladePdf(url, ersatzName, abbruch.signal)
            .then(pdf => {
                objektUrl = URL.createObjectURL(pdf.blob);
                setStand({ art: 'fertig', pdf, url: objektUrl });
            })
            .catch(err => {
                if (abbruch.signal.aborted) return;
                setStand({ art: 'fehler', meldung: fehlerText(err, 'Das PDF konnte nicht erstellt werden.') });
            });
        return () => {
            abbruch.abort();
            if (objektUrl) URL.revokeObjectURL(objektUrl);
        };
    }, [url, ersatzName]);

    return stand;
}
