import { useCallback, useMemo, useState } from 'react';
import { validateTimeInput } from '../../components/ui/time-input';
import { getNextTempBookingId, type Buchung } from './kalenderTypen';
import {
    entwurfSchluessel, parseZeit, schneidePauseEin, zeitEntwurfAus, zeitenFuerNeueBuchung,
    type ZeitEntwuerfe,
} from './buchungZeiten';
import { hatUngespeicherteAenderungen } from './tagSpeichernRegeln';

const leereBuchung = (id: number, start: string, ende: string): Buchung => ({
    id,
    // Projekt und Tätigkeit wählt der Nutzer bewusst selbst (keine Vorbelegung).
    projektId: 0, arbeitsgangId: 0, startZeit: start, endeZeit: ende,
    projektName: '', arbeitsgangName: '', notiz: '', dauerMinuten: null, dauerFormatiert: null,
});

const vereinige = (bestand: Set<number>, ids: number[]) => (ids.length === 0 ? bestand : new Set([...bestand, ...ids]));

/** Buchungen eines Tages mit Uhrzeit-Entwürfen und der Liste geänderter Buchungen. */
export function useTagesBuchungen(start: Buchung[]) {
    const [buchungen, setBuchungen] = useState<Buchung[]>(start);
    const [zeitEntwuerfe, setZeitEntwuerfe] = useState<ZeitEntwuerfe>({});
    const [geaenderteIds, setGeaenderteIds] = useState<Set<number>>(new Set());
    const zeitEntwurf = useMemo(() => zeitEntwurfAus(zeitEntwuerfe), [zeitEntwuerfe]);

    const merkeEntwuerfe = (neu: ZeitEntwuerfe) => {
        if (Object.keys(neu).length > 0) setZeitEntwuerfe(prev => ({ ...prev, ...neu }));
    };
    const merkeGeaendert = (ids: number[]) => setGeaenderteIds(prev => vereinige(prev, ids.filter(id => id > 0)));

    const neueBuchung = () => {
        const { start: von, ende: bis } = zeitenFuerNeueBuchung(buchungen, zeitEntwurf);
        const id = getNextTempBookingId();
        merkeEntwuerfe({ [entwurfSchluessel(id, 'startZeit')]: von, [entwurfSchluessel(id, 'endeZeit')]: bis });
        setBuchungen(prev => [...prev, leereBuchung(id, von, bis)]);
    };

    /** Neue Pause 12:00–12:30, die überlappende Arbeitszeit automatisch teilt oder kürzt. */
    const neuePause = () => {
        const id = getNextTempBookingId();
        const pause: Buchung = {
            ...leereBuchung(id, '12:00', '12:30'),
            projektId: -1, // Internes Pause-Projekt
            arbeitsgangId: undefined, projektName: '[INTERN] Pause', notiz: 'Pause', typ: 'PAUSE',
            dauerMinuten: 30, dauerFormatiert: '0:30h',
        };
        const schnitt = schneidePauseEin({ buchungen, pauseStart: '12:00', pauseEnde: '12:30', pauseId: id, neuePause: pause, zeitEntwurf });
        merkeEntwuerfe({ [entwurfSchluessel(id, 'startZeit')]: '12:00', [entwurfSchluessel(id, 'endeZeit')]: '12:30', ...schnitt.entwuerfe });
        merkeGeaendert(schnitt.geaenderteIds);
        setBuchungen(schnitt.buchungen);
    };

    /**
     * Ändert eine Buchung lokal – alle Felder eines Patches auf einmal, damit kein
     * zweiter Aufruf mit veraltetem Stand den ersten überschreibt (z. B. Kategorie-ID
     * und -Name aus dem Auswahlfenster). Wird eine Pause verschoben, rücken die
     * Arbeitszeiten drumherum mit.
     */
    const aendern = (id: number, patch: Partial<Buchung>) => {
        const zeitFelder = (['startZeit', 'endeZeit'] as const).filter(feld => feld in patch);
        const entwuerfe: ZeitEntwuerfe = {};
        zeitFelder.forEach(feld => { entwuerfe[entwurfSchluessel(id, feld)] = String(patch[feld] ?? ''); });
        const geaendert: number[] = [id];
        let liste = buchungen.map(b => (b.id === id ? { ...b, ...patch } : b));

        const aktuell = liste.find(b => b.id === id);
        if (aktuell?.typ === 'PAUSE' && zeitFelder.length > 0) {
            const von = 'startZeit' in patch ? String(patch.startZeit ?? '') : zeitEntwurf(aktuell, 'startZeit');
            const bis = 'endeZeit' in patch ? String(patch.endeZeit ?? '') : zeitEntwurf(aktuell, 'endeZeit');
            const vVon = validateTimeInput(von, { label: 'Start' });
            const vBis = validateTimeInput(bis, { label: 'Ende' });
            if (vVon.valid && vBis.valid && vVon.value && vBis.value && parseZeit(vVon.value) < parseZeit(vBis.value)) {
                const altesEnde = zeitEntwurf(aktuell, 'endeZeit');
                if ('endeZeit' in patch && altesEnde && altesEnde !== bis) {
                    // Anschlussbuchung am alten Pausenende verschieben
                    liste = liste.map(b => {
                        if (b.id === id || b.typ === 'PAUSE' || zeitEntwurf(b, 'startZeit') !== altesEnde) return b;
                        geaendert.push(b.id);
                        entwuerfe[entwurfSchluessel(b.id, 'startZeit')] = bis;
                        return { ...b, startZeit: bis };
                    });
                }
                const zwischenstand = zeitEntwurfAus({ ...zeitEntwuerfe, ...entwuerfe });
                const schnitt = schneidePauseEin({ buchungen: liste, pauseStart: vVon.value, pauseEnde: vBis.value, pauseId: id, zeitEntwurf: zwischenstand });
                liste = schnitt.buchungen;
                Object.assign(entwuerfe, schnitt.entwuerfe);
                geaendert.push(...schnitt.geaenderteIds);
            }
        }
        merkeEntwuerfe(entwuerfe);
        merkeGeaendert(geaendert);
        setBuchungen(liste);
    };

    const hinzufuegen = useCallback((buchung: Buchung) => setBuchungen(prev => [...prev, buchung]), []);
    const entfernen = useCallback((id: number) => setBuchungen(prev => prev.filter(b => b.id !== id)), []);
    const idErsetzen = useCallback((alt: number, neu: number) => setBuchungen(prev => prev.map(b => (b.id === alt ? { ...b, id: neu } : b))), []);

    /** Gespeicherte Buchungen gelten wieder als unverändert; ihre Entwürfe sind jetzt der Stand. */
    const alsGespeichertMerken = useCallback((ids: number[]) => {
        if (ids.length === 0) return;
        setGeaenderteIds(prev => new Set([...prev].filter(id => !ids.includes(id))));
        setZeitEntwuerfe(prev => {
            const naechste = { ...prev };
            ids.forEach(id => { delete naechste[entwurfSchluessel(id, 'startZeit')]; delete naechste[entwurfSchluessel(id, 'endeZeit')]; });
            return naechste;
        });
    }, []);

    return {
        buchungen, setBuchungen, zeitEntwuerfe, zeitEntwurf, geaenderteIds,
        neueBuchung, neuePause, aendern, hinzufuegen, entfernen, idErsetzen, alsGespeichertMerken,
        ungespeichert: () => hatUngespeicherteAenderungen(buchungen, geaenderteIds, zeitEntwuerfe),
    };
}

export type TagesBuchungen = ReturnType<typeof useTagesBuchungen>;
