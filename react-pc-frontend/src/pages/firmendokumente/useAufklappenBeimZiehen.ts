import { useCallback, useEffect, useRef } from 'react';

/** So lange muss man beim Ziehen über einem zugeklappten Ordner verharren, bis er aufgeht. */
export const AUFKLAPPEN_NACH_MS = 700;

/**
 * Wie im Explorer: Verharrt man beim Ziehen kurz über einem zugeklappten
 * Ordner, klappt er auf – so kommt man an seine Unterordner, ohne loszulassen.
 * `drueber` bei jedem `dragover` aufrufen (startet die Uhr nur einmal),
 * `weg` beim Verlassen oder Ablegen.
 */
export function useAufklappenBeimZiehen(zugeklappt: boolean, aufklappen: () => void) {
    const uhr = useRef<ReturnType<typeof setTimeout> | null>(null);
    const aufklappenRef = useRef(aufklappen);
    useEffect(() => { aufklappenRef.current = aufklappen; });

    const weg = useCallback(() => {
        if (uhr.current === null) return;
        clearTimeout(uhr.current);
        uhr.current = null;
    }, []);
    useEffect(() => weg, [weg]);

    const drueber = useCallback(() => {
        if (!zugeklappt || uhr.current !== null) return;
        uhr.current = setTimeout(() => {
            uhr.current = null;
            aufklappenRef.current();
        }, AUFKLAPPEN_NACH_MS);
    }, [zugeklappt]);

    return { drueber, weg };
}
