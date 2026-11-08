import { describe, expect, it } from 'vitest';
import { firmenEntwurf, pruefeFirmenEntwurf } from './firmaEntwurf';
import type { FirmenEntwurf, Firmeninformation } from './firmaTypen';

const FIRMA = {
    id: 1, firmenname: 'Mustermann Metallbau GmbH', firmenfarbe: '#500010', mahnverfahrenAktiv: true,
    tageBisZahlungserinnerung: 7, tageBisErsteMahnung: 14, tageBisZweiteMahnung: 21, mahnverfahrenNeuesZahlungszielTage: 10,
    bgSatzOverride: 2.5,
} as Firmeninformation;

const entwurf = (teil: Partial<FirmenEntwurf> = {}): FirmenEntwurf => ({ ...firmenEntwurf(FIRMA), ...teil });

describe('firmenEntwurf', () => {
    it('macht aus den Zahlen Eingabetexte mit Dezimalkomma', () => {
        expect(firmenEntwurf(FIRMA)).toMatchObject({
            firmenname: 'Mustermann Metallbau GmbH',
            tageBisZahlungserinnerung: '7', tageBisErsteMahnung: '14', tageBisZweiteMahnung: '21',
            mahnverfahrenNeuesZahlungszielTage: '10', bgSatzOverride: '2,5',
        });
    });

    it('setzt fehlende Mahntage auf 7 und lässt einen fehlenden BG-Satz leer', () => {
        const leer = { id: 1, firmenname: 'Testbetrieb' } as Firmeninformation;
        expect(firmenEntwurf(leer)).toMatchObject({
            tageBisZahlungserinnerung: '7', tageBisErsteMahnung: '7', tageBisZweiteMahnung: '7',
            mahnverfahrenNeuesZahlungszielTage: '7', bgSatzOverride: '',
        });
        expect(firmenEntwurf({ ...leer, bgSatzOverride: null }).bgSatzOverride).toBe('');
        expect(firmenEntwurf({ ...leer, bgSatzOverride: 0 }).bgSatzOverride).toBe('0');
    });
});

describe('pruefeFirmenEntwurf', () => {
    it('wandelt gültige Entwürfe in Zahlen für das Speichern um', () => {
        const ergebnis = pruefeFirmenEntwurf(entwurf({ bgSatzOverride: '8,5' }));
        expect(ergebnis).toEqual({
            ok: true,
            payload: expect.objectContaining({
                id: 1, firmenname: 'Mustermann Metallbau GmbH', firmenfarbe: '#500010',
                tageBisZahlungserinnerung: 7, tageBisErsteMahnung: 14, tageBisZweiteMahnung: 21,
                mahnverfahrenNeuesZahlungszielTage: 10, bgSatzOverride: 8.5,
            }),
        });
    });

    it('schickt einen leeren BG-Satz als null', () => {
        const ergebnis = pruefeFirmenEntwurf(entwurf({ bgSatzOverride: '' }));
        expect(ergebnis.ok && ergebnis.payload.bgSatzOverride).toBeNull();
        expect(ergebnis.ok && 'bgSatzOverride' in ergebnis.payload).toBe(true);
    });

    it('lehnt leere Mahntage mit Feldnamen ab', () => {
        expect(pruefeFirmenEntwurf(entwurf({ tageBisZahlungserinnerung: '' })))
            .toEqual({ ok: false, meldung: 'Bitte Tage nach Fälligkeit eingeben.' });
        expect(pruefeFirmenEntwurf(entwurf({ mahnverfahrenNeuesZahlungszielTage: ' ' })))
            .toEqual({ ok: false, meldung: 'Bitte Neues Zahlungsziel eingeben.' });
    });

    it('verlangt ganze Tage ab 1', () => {
        expect(pruefeFirmenEntwurf(entwurf({ tageBisErsteMahnung: '8,5' })))
            .toEqual({ ok: false, meldung: 'Bitte Tage nach der Zahlungserinnerung als gültige ganze Zahl eingeben.' });
        expect(pruefeFirmenEntwurf(entwurf({ tageBisZweiteMahnung: '0' })))
            .toEqual({ ok: false, meldung: 'Tage nach der 1. Mahnung muss mindestens 1 sein.' });
    });

    it('lehnt unvollständige Dezimalzahlen ab und nimmt führende Nullen an', () => {
        expect(pruefeFirmenEntwurf(entwurf({ bgSatzOverride: '8,' })))
            .toEqual({ ok: false, meldung: 'Bitte Tatsächlicher BG-Satz als vollständige Zahl mit Dezimalkomma eingeben (z. B. 12,5).' });
        const ergebnis = pruefeFirmenEntwurf(entwurf({ tageBisZahlungserinnerung: '007' }));
        expect(ergebnis.ok && ergebnis.payload.tageBisZahlungserinnerung).toBe(7);
    });

    it('begrenzt den BG-Satz auf 0 bis 100', () => {
        expect(pruefeFirmenEntwurf(entwurf({ bgSatzOverride: '100,5' })))
            .toEqual({ ok: false, meldung: 'Tatsächlicher BG-Satz darf höchstens 100 sein.' });
        expect(pruefeFirmenEntwurf(entwurf({ bgSatzOverride: '-1' })))
            .toEqual({ ok: false, meldung: 'Tatsächlicher BG-Satz muss mindestens 0 sein.' });
    });

    it('prüft die Firmenfarbe als Hex-Wert mit 3 oder 6 Stellen, leer ist erlaubt', () => {
        const meldung = 'Bitte eine gültige Firmenfarbe eingeben, z. B. #500010.';
        for (const ungueltig of ['500010', '#50001', '#5000100', '#xyz', 'rot']) {
            expect(pruefeFirmenEntwurf(entwurf({ firmenfarbe: ungueltig }))).toEqual({ ok: false, meldung });
        }
        for (const gueltig of ['', '#abc', '#ABC', '#e11d48']) {
            expect(pruefeFirmenEntwurf(entwurf({ firmenfarbe: gueltig })).ok).toBe(true);
        }
    });

    it('meldet zuerst die Zahlenfelder, dann die Farbe', () => {
        expect(pruefeFirmenEntwurf(entwurf({ tageBisZahlungserinnerung: '', firmenfarbe: 'rot' })))
            .toEqual({ ok: false, meldung: 'Bitte Tage nach Fälligkeit eingeben.' });
    });
});
