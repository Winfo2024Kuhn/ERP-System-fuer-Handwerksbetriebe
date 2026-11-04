import { describe, expect, it } from 'vitest';
import {
    SEITEN_GROESSE, SUCHE_MAX_LAENGE, listenSchluessel, normalisiereReiterSeite, reiterUrl, wirksameSuche,
} from './emailsTabListe';

/** Reine Hilfen der seitenweisen Mailliste im E-Mail-Reiter. Dummy-Daten (DSGVO): Max Mustermann. */
describe('wirksameSuche', () => {
    it('trimmt und lässt unter 2 Zeichen keine Suche übrig', () => {
        expect(wirksameSuche('  Geländer ')).toBe('Geländer');
        expect(wirksameSuche(' a ')).toBe('');
        expect(wirksameSuche('')).toBe('');
        expect(wirksameSuche('ab')).toBe('ab');
    });

    it('schneidet über 200 Zeichen ab, statt den Server mit einem Fehler antworten zu lassen', () => {
        expect(wirksameSuche('x'.repeat(250))).toHaveLength(SUCHE_MAX_LAENGE);
    });
});

describe('reiterUrl', () => {
    it('baut Seite und Größe, ohne Suche und Richtung bei „Alle“', () => {
        expect(reiterUrl({ bezug: 'projekt', bezugId: 12, suche: '', filter: 'all' }, 0))
            .toBe(`/api/emails/reiter/projekt/12?page=0&size=${SEITEN_GROESSE}`);
    });

    it('schickt Suche und Richtung nur, wenn sie wirken', () => {
        expect(reiterUrl({ bezug: 'kunde', bezugId: 3, suche: 'Müller & Söhne', filter: 'in' }, 2))
            .toBe('/api/emails/reiter/kunde/3?page=2&size=25&q=M%C3%BCller+%26+S%C3%B6hne&richtung=IN');
        expect(reiterUrl({ bezug: 'lieferant', bezugId: 5, suche: 'x', filter: 'out' }, 0))
            .toBe('/api/emails/reiter/lieferant/5?page=0&size=25&richtung=OUT');
    });

    it('kodiert die Kennung im Pfad', () => {
        expect(reiterUrl({ bezug: 'anfrage', bezugId: '1/../2' as unknown as number, suche: '', filter: 'all' }, 0))
            .toBe('/api/emails/reiter/anfrage/1%2F..%2F2?page=0&size=25');
    });
});

describe('listenSchluessel', () => {
    it('ändert sich mit Bezug, Suche und Filter', () => {
        const basis = { bezug: 'projekt' as const, bezugId: 1, suche: '', filter: 'all' as const };
        const schluessel = listenSchluessel(basis);
        expect(listenSchluessel({ ...basis, bezugId: 2 })).not.toBe(schluessel);
        expect(listenSchluessel({ ...basis, suche: 'Angebot' })).not.toBe(schluessel);
        expect(listenSchluessel({ ...basis, filter: 'in' })).not.toBe(schluessel);
    });

    it('bleibt gleich, solange die Suche nicht wirkt (unter 2 Zeichen, Leerzeichen)', () => {
        const basis = { bezug: 'projekt' as const, bezugId: 1, suche: '', filter: 'all' as const };
        expect(listenSchluessel({ ...basis, suche: 'a' })).toBe(listenSchluessel(basis));
        expect(listenSchluessel({ ...basis, suche: ' Angebot ' })).toBe(listenSchluessel({ ...basis, suche: 'Angebot' }));
    });

    it('lädt ohne gültige Kennung nichts', () => {
        expect(listenSchluessel({ bezug: 'kunde', bezugId: 0, suche: '', filter: 'all' })).toBeNull();
        expect(listenSchluessel({ bezug: 'kunde', bezugId: Number.NaN, suche: '', filter: 'all' })).toBeNull();
    });
});

describe('normalisiereReiterSeite', () => {
    it('übernimmt Einträge und Gesamtzahl und berechnet die Vorschau ohne Zitat', () => {
        const seite = normalisiereReiterSeite({
            eintraege: [{
                id: 123, subject: 'AW: Angebot Geländer', fromAddress: 'max.mustermann@example.com',
                recipient: 'info@example.com', direction: 'IN', sentAt: '2026-11-03T10:15:00',
                bodyHtml: '<p>Passt so</p><blockquote class="gmail_quote">ALT</blockquote>',
                nachrichtenAnzahl: 3, mitAnhang: true, zustellStatus: 'UNZUSTELLBAR', zustellFehler: 'unknown user',
            }],
            gesamt: 140, seite: 0, seitenGroesse: 25,
        });
        expect(seite.gesamt).toBe(140);
        expect(seite.eintraege).toEqual([{
            id: 123, subject: 'AW: Angebot Geländer', fromAddress: 'max.mustermann@example.com',
            recipient: 'info@example.com', direction: 'IN', sentAt: '2026-11-03T10:15:00',
            vorschau: 'Passt so', nachrichtenAnzahl: 3, mitAnhang: true,
            zustellStatus: 'UNZUSTELLBAR', zustellFehler: 'unknown user',
        }]);
    });

    it('füllt fehlende Felder sicher auf', () => {
        const seite = normalisiereReiterSeite({
            eintraege: [{ id: 5, direction: 'OUT', bodyHtml: null, nachrichtenAnzahl: 0, zustellStatus: null }],
            gesamt: 1,
        });
        expect(seite.eintraege[0]).toMatchObject({
            id: 5, direction: 'OUT', vorschau: '', nachrichtenAnzahl: 1, mitAnhang: false, zustellStatus: null,
        });
    });

    it('wirft kaputte Einträge weg und macht aus Unsinn eine leere Seite', () => {
        expect(normalisiereReiterSeite(null)).toEqual({ eintraege: [], gesamt: 0 });
        expect(normalisiereReiterSeite({ eintraege: 'x', gesamt: -3 })).toEqual({ eintraege: [], gesamt: 0 });
        const seite = normalisiereReiterSeite({ eintraege: [null, { id: 'a' }, { id: 9, direction: 'IN' }], gesamt: 3 });
        expect(seite.eintraege.map(e => e.id)).toEqual([9]);
    });

    it('zählt nie weniger als geladen', () => {
        expect(normalisiereReiterSeite({ eintraege: [{ id: 1, direction: 'IN' }, { id: 2, direction: 'IN' }] }).gesamt).toBe(2);
    });

    it('nimmt nur Text in die Vorschau, kein Skript', () => {
        const seite = normalisiereReiterSeite({
            eintraege: [{ id: 1, direction: 'IN', bodyHtml: '<img src=x onerror="alert(1)"><script>alert(2)</script><p>Hallo Max</p>' }],
            gesamt: 1,
        });
        expect(seite.eintraege[0].vorschau).toBe('Hallo Max');
    });
});
