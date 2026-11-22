import { useEffect, useState } from 'react';
import { getNextTempBookingId, type Buchung } from './kalenderTypen';
import type { ZeitEntwurf } from './buchungZeiten';

interface Optionen {
    buchungen: Buchung[];
    fokusIndex: number;
    setFokusIndex: (index: number) => void;
    zeitEntwurf: ZeitEntwurf;
    hinzufuegen: (buchung: Buchung) => void;
    aktiv: boolean;
}

const kopie = (quelle: Partial<Buchung>, ohneTaetigkeit: boolean): Buchung => ({
    id: getNextTempBookingId(),
    // Nur übernehmen, was kopiert wurde – nie stillschweigend das erste Projekt vorbelegen.
    projektId: quelle.projektId || 0,
    arbeitsgangId: ohneTaetigkeit ? 0 : quelle.arbeitsgangId || 0,
    startZeit: quelle.startZeit ?? '08:00',
    endeZeit: quelle.endeZeit ?? '16:00',
    projektName: '', arbeitsgangName: '',
    notiz: ohneTaetigkeit ? '' : quelle.notiz || '',
    dauerMinuten: null, dauerFormatiert: null,
});

/** Strg+C kopiert die gewählte Zeile, Strg+V fügt sie ein, Strg+D dupliziert mit leerer Tätigkeit. */
export function useTagTastenkuerzel({ buchungen, fokusIndex, setFokusIndex, zeitEntwurf, hinzufuegen, aktiv }: Optionen) {
    const [zwischenablage, setZwischenablage] = useState<Partial<Buchung> | null>(null);

    useEffect(() => {
        if (!aktiv) return;
        const beiTaste = (e: KeyboardEvent) => {
            const ziel = e.target as HTMLElement;
            if (ziel.tagName === 'INPUT' || ziel.tagName === 'TEXTAREA' || ziel.tagName === 'SELECT') return;
            if (!e.ctrlKey || !['c', 'v', 'd'].includes(e.key)) return;
            e.preventDefault();
            const gewaehlt = buchungen[fokusIndex];
            const zeiten = gewaehlt && { startZeit: zeitEntwurf(gewaehlt, 'startZeit'), endeZeit: zeitEntwurf(gewaehlt, 'endeZeit') };
            if (e.key === 'c' && gewaehlt) {
                setZwischenablage({ projektId: gewaehlt.projektId, arbeitsgangId: gewaehlt.arbeitsgangId, notiz: gewaehlt.notiz, ...zeiten });
            } else if (e.key === 'v' && zwischenablage) {
                hinzufuegen(kopie(zwischenablage, false));
                setFokusIndex(buchungen.length);
            } else if (e.key === 'd' && gewaehlt) {
                hinzufuegen(kopie({ projektId: gewaehlt.projektId, ...zeiten }, true));
                setFokusIndex(buchungen.length);
            }
        };
        window.addEventListener('keydown', beiTaste);
        return () => window.removeEventListener('keydown', beiTaste);
    }, [aktiv, buchungen, fokusIndex, setFokusIndex, zeitEntwurf, hinzufuegen, zwischenablage]);

    return zwischenablage;
}
