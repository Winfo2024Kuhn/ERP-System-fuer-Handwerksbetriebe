import { describe, expect, it } from 'vitest';
import {
    clampPercent,
    formatEingabe,
    formatEur,
    formatHours,
    leseFehlermeldung,
    parseDecimal,
} from './verrechnungslohnFormat';

/**
 * Tests fuer die Zahlen-Ein- und Ausgabe im Stundensatz-Rechner.
 *
 * Hintergrund: Das Eingabefeld zeigte den Wert mit Punkt als Dezimaltrenner
 * ("1234.56"), waehrend der Parser jeden Punkt als Tausendertrenner entfernte.
 * Reinklicken und wieder Rausklicken machte aus 1.234,56 den Wert 123456 --
 * ohne dass der Nutzer etwas getippt hatte.
 */
describe('parseDecimal', () => {
    it('macht aus einem unveraendert uebernommenen Feldwert nicht das Hundertfache', () => {
        // genau der Weg, den NumberCell beim Fokussieren und Verlassen geht
        const wert = 1234.56;
        expect(parseDecimal(formatEingabe(wert))).toBe(wert);
    });

    it('liest deutsche Schreibweise mit Tausenderpunkt und Komma', () => {
        expect(parseDecimal('1.234,56')).toBe(1234.56);
        expect(parseDecimal('1.234.567,89')).toBe(1234567.89);
        expect(parseDecimal('0,50')).toBe(0.5);
    });

    it('liest Punkte als Tausendertrenner, wenn kein Komma da ist', () => {
        expect(parseDecimal('1.234')).toBe(1234);
        expect(parseDecimal('1.234.567')).toBe(1234567);
    });

    it('liest englische Schreibweise und ganze Zahlen', () => {
        expect(parseDecimal('1234.56')).toBe(1234.56);
        expect(parseDecimal('1234')).toBe(1234);
        expect(parseDecimal('0.5')).toBe(0.5);
    });

    it('liest negative Betraege fuer Abschlaege', () => {
        expect(parseDecimal('-10,00')).toBe(-10);
        expect(parseDecimal('-1.500,50')).toBe(-1500.5);
    });

    it('ignoriert Leerzeichen am Rand', () => {
        expect(parseDecimal('  42,00  ')).toBe(42);
    });

    it('gibt null zurueck, wenn nichts Brauchbares drinsteht', () => {
        expect(parseDecimal('')).toBeNull();
        expect(parseDecimal('   ')).toBeNull();
        expect(parseDecimal('abc')).toBeNull();
        expect(parseDecimal('12abc')).toBeNull();
        expect(parseDecimal('-')).toBeNull();
        expect(parseDecimal(',')).toBeNull();
    });
});

describe('formatEingabe', () => {
    it('schreibt Betraege deutsch mit zwei Nachkommastellen', () => {
        expect(formatEingabe(1234.56)).toBe('1.234,56');
        expect(formatEingabe(0)).toBe('0,00');
        expect(formatEingabe(-10)).toBe('-10,00');
    });

    it('faengt kaputte Zahlen ab, statt "NaN" anzuzeigen', () => {
        expect(formatEingabe(Number.NaN)).toBe('0,00');
        expect(formatEingabe(Number.POSITIVE_INFINITY)).toBe('0,00');
    });

    it('ergibt zusammen mit parseDecimal wieder denselben Wert', () => {
        for (const wert of [0, 0.5, 42, 1234.56, 1234567.89, -75.25]) {
            expect(parseDecimal(formatEingabe(wert))).toBe(wert);
        }
    });
});

describe('formatEur', () => {
    it('schreibt Betraege als Euro in deutscher Schreibweise', () => {
        // NBSP vor dem Euro-Zeichen, deshalb kein harter String-Vergleich
        expect(formatEur(1234.5)).toMatch(/^1\.234,50\s€$/);
        expect(formatEur(0)).toMatch(/^0,00\s€$/);
    });

    it('zeigt 0 statt "NaN €", wenn die Rechnung schiefging', () => {
        expect(formatEur(Number.NaN)).toMatch(/^0,00\s€$/);
        // Division durch 0 in der Selbstkosten-Rechnung
        expect(formatEur(1 / 0)).toMatch(/^0,00\s€$/);
    });
});

describe('formatHours', () => {
    it('haengt die Einheit an und rundet auf eine Nachkommastelle', () => {
        expect(formatHours(1672.5)).toBe('1.672,5 h');
        expect(formatHours(2080)).toBe('2.080 h');
    });

    it('zeigt 0 h statt "NaN h"', () => {
        expect(formatHours(Number.NaN)).toBe('0 h');
    });
});

describe('leseFehlermeldung', () => {
    const antwort = (status: number, json: () => Promise<unknown>) =>
        ({ status, json } as unknown as Response);

    it('nimmt die Klartext-Meldung des Servers', async () => {
        const res = antwort(400, async () => ({ message: 'Der Abschlag ist zu gross.' }));
        expect(await leseFehlermeldung(res, 'Fehlgeschlagen')).toBe('Der Abschlag ist zu gross.');
    });

    it('versteht auch reason und error als Feldnamen', async () => {
        expect(await leseFehlermeldung(antwort(400, async () => ({ reason: 'Grund' })), 'F'))
            .toBe('Grund');
        expect(await leseFehlermeldung(antwort(500, async () => ({ error: 'Kaputt' })), 'F'))
            .toBe('Kaputt');
    });

    it('faellt bei kaputtem JSON auf den Statustext zurueck', async () => {
        const res = antwort(502, async () => {
            throw new Error('kein JSON');
        });
        expect(await leseFehlermeldung(res, 'Übernehmen fehlgeschlagen')).toBe(
            'Übernehmen fehlgeschlagen (Status 502).'
        );
    });

    it('faellt bei leerer oder unbrauchbarer Meldung auf den Statustext zurueck', async () => {
        expect(await leseFehlermeldung(antwort(400, async () => ({})), 'Fehlgeschlagen')).toBe(
            'Fehlgeschlagen (Status 400).'
        );
        expect(
            await leseFehlermeldung(antwort(400, async () => ({ message: '   ' })), 'Fehlgeschlagen')
        ).toBe('Fehlgeschlagen (Status 400).');
        expect(
            await leseFehlermeldung(antwort(400, async () => ({ message: 42 })), 'Fehlgeschlagen')
        ).toBe('Fehlgeschlagen (Status 400).');
        // leerer Body: res.json() liefert null
        expect(await leseFehlermeldung(antwort(503, async () => null), 'Fehlgeschlagen')).toBe(
            'Fehlgeschlagen (Status 503).'
        );
    });
});

describe('clampPercent', () => {
    it('haelt die Prozentwerte zwischen 0 und 100', () => {
        expect(clampPercent('-5')).toBe(0);
        expect(clampPercent('150')).toBe(100);
        expect(clampPercent('20')).toBe(20);
    });

    it('rundet auf ganze Prozent und faengt leere Eingaben ab', () => {
        expect(clampPercent('12,4')).toBe(0); // Komma ist keine gueltige Zahl fuer input[type=number]
        expect(clampPercent('12.6')).toBe(13);
        expect(clampPercent('')).toBe(0);
        expect(clampPercent('abc')).toBe(0);
    });
});
