import { describe, expect, it, vi } from 'vitest';
import {
    ENTWURF_PRUEFEN_DAUER_MS,
    MITSTORNIERT_DAUER_MS,
    stornoHinweiseAus,
    zeigeStornoHinweise,
} from './stornoHinweise';

const ENTWURF = { art: 'ENTWURF_PRUEFEN' as const, text: 'Die Schlussrechnung SR-2026-001 ist noch ein Entwurf.' };
const MIT = { art: 'MITSTORNIERT' as const, text: 'Die Schlussrechnung SR-2026-002 wurde mitstorniert.' };

describe('stornoHinweiseAus', () => {
    it('liest gültige Hinweise in Reihenfolge', () => {
        expect(stornoHinweiseAus({ dokumentNummer: 'ST-1', hinweise: [MIT, ENTWURF] })).toEqual([MIT, ENTWURF]);
    });

    it.each([null, undefined, 'text', 42, {}, { hinweise: null }, { hinweise: 'kaputt' }])(
        'liefert eine leere Liste für %p',
        (antwort) => {
            expect(stornoHinweiseAus(antwort)).toEqual([]);
        },
    );

    it('verwirft unbekannte Arten, leere Texte und Fremdwerte', () => {
        expect(
            stornoHinweiseAus({
                hinweise: [null, 'x', { art: 'UNBEKANNT', text: 'a' }, { art: 'MITSTORNIERT', text: '  ' }, { art: 'ENTWURF_PRUEFEN', text: 3 }, ENTWURF],
            }),
        ).toEqual([ENTWURF]);
    });
});

describe('zeigeStornoHinweise', () => {
    it('zeigt Entwurf als Warnung und Mitstorniert als Info, je ein Toast', () => {
        const toast = { warning: vi.fn(), info: vi.fn() };

        zeigeStornoHinweise(toast, [MIT, ENTWURF]);

        expect(toast.info).toHaveBeenCalledWith(MIT.text, MITSTORNIERT_DAUER_MS);
        expect(toast.warning).toHaveBeenCalledWith(ENTWURF.text, ENTWURF_PRUEFEN_DAUER_MS);
        expect(toast.info).toHaveBeenCalledTimes(1);
        expect(toast.warning).toHaveBeenCalledTimes(1);
    });

    it('zeigt Warnungen zuletzt, damit sie unten im Meldungsstapel sichtbar bleiben', () => {
        const reihenfolge: string[] = [];
        const toast = {
            warning: vi.fn((text: string) => { reihenfolge.push(text); }),
            info: vi.fn((text: string) => { reihenfolge.push(text); }),
        };

        zeigeStornoHinweise(toast, [ENTWURF, MIT]);

        expect(reihenfolge).toEqual([MIT.text, ENTWURF.text]);
    });

    it('zeigt ohne Hinweise nichts', () => {
        const toast = { warning: vi.fn(), info: vi.fn() };

        zeigeStornoHinweise(toast, []);

        expect(toast.warning).not.toHaveBeenCalled();
        expect(toast.info).not.toHaveBeenCalled();
    });
});
