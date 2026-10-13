import { useCallback, useLayoutEffect, useState, type RefObject } from 'react';

export const MIN_ZOOM = 0.3;
export const MAX_ZOOM = 2.5;
const SCHRITT = 0.1;
/** Luft um die Seite, damit Schatten und Griffe nicht am Rand kleben. */
const RAND = 48;

const begrenzen = (z: number) => Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, z));

/** Zoom, bei dem der Inhalt ganz in den Bereich passt. */
export function einpassZoom(bereich: { breite: number; hoehe: number }, inhalt: { breite: number; hoehe: number }): number {
    if (bereich.breite <= 0 || bereich.hoehe <= 0) return 1;
    const z = Math.min((bereich.breite - RAND) / inhalt.breite, (bereich.hoehe - RAND) / inhalt.hoehe);
    return begrenzen(Math.floor(z * 100) / 100);
}

export interface SeitenZoom {
    zoom: number;
    /** Folgt der Zoom gerade der Fenstergröße („Seite einpassen“)? */
    eingepasst: boolean;
    vergroessern: () => void;
    verkleinern: () => void;
    einpassen: () => void;
}

/**
 * Zoom für die A4-Seite(n): startet eingepasst und folgt dann der Größe des
 * Bereichs, bis man selbst zoomt.
 */
export function useSeitenZoom(bereichRef: RefObject<HTMLElement | null>, inhalt: { breite: number; hoehe: number }): SeitenZoom {
    const [eingepasst, setEingepasst] = useState(true);
    const [eigenerZoom, setEigenerZoom] = useState(1);
    const [passend, setPassend] = useState(1);
    const { breite, hoehe } = inhalt;

    useLayoutEffect(() => {
        const el = bereichRef.current;
        if (!el) return;
        const messen = () => setPassend(einpassZoom({ breite: el.clientWidth, hoehe: el.clientHeight }, { breite, hoehe }));
        messen();
        if (typeof ResizeObserver === 'undefined') return;
        const beobachter = new ResizeObserver(messen);
        beobachter.observe(el);
        return () => beobachter.disconnect();
    }, [bereichRef, breite, hoehe]);

    const zoom = eingepasst ? passend : eigenerZoom;

    const vergroessern = useCallback(() => {
        setEigenerZoom(begrenzen(Math.round((zoom + SCHRITT) * 10) / 10));
        setEingepasst(false);
    }, [zoom]);
    const verkleinern = useCallback(() => {
        setEigenerZoom(begrenzen(Math.round((zoom - SCHRITT) * 10) / 10));
        setEingepasst(false);
    }, [zoom]);
    const einpassen = useCallback(() => setEingepasst(true), []);

    return { zoom, eingepasst, vergroessern, verkleinern, einpassen };
}
