import { useEffect, useRef, useState } from 'react';
import { toSafeResourceUrl } from '../../../lib/htmlSanitizer';
import { isImageAttachment } from './anhangGrenze';

/**
 * Offene Vorschau eines Anhangs: PDF oder Bild (Blob-Adresse der Datei). Blob-Adressen
 * werden beim Schließen, beim Wechsel und beim Abbauen wieder freigegeben.
 */
export function useAnhangVorschau() {
    const [pdfUrl, setPdfUrl] = useState<string | null>(null);
    const [bild, setBild] = useState<{ url: string; name: string } | null>(null);
    const offen = useRef<{ pdf: string | null; bild: string | null }>({ pdf: null, bild: null });

    useEffect(() => {
        const adressen = offen.current;
        return () => {
            if (adressen.pdf) URL.revokeObjectURL(adressen.pdf);
            if (adressen.bild) URL.revokeObjectURL(adressen.bild);
        };
    }, []);

    const setzePdf = (url: string | null) => {
        if (offen.current.pdf && offen.current.pdf !== url) URL.revokeObjectURL(offen.current.pdf);
        offen.current.pdf = url;
        setPdfUrl(url);
    };
    const setzeBild = (neu: { url: string; name: string } | null) => {
        if (offen.current.bild && offen.current.bild !== neu?.url) URL.revokeObjectURL(offen.current.bild);
        offen.current.bild = neu?.url ?? null;
        setBild(neu);
    };

    const zeigeDatei = (datei: File) => {
        const sicher = toSafeResourceUrl(URL.createObjectURL(datei));
        if (!sicher) return;
        if (isImageAttachment(datei)) setzeBild({ url: sicher, name: datei.name });
        else setzePdf(sicher);
    };

    return {
        pdfUrl, bild, zeigeDatei,
        zeigePdf: (datei: File) => setzePdf(URL.createObjectURL(datei)),
        schliessePdf: () => setzePdf(null),
        schliesseBild: () => setzeBild(null),
    };
}

export type AnhangVorschauStand = ReturnType<typeof useAnhangVorschau>;
