import { describe, it, expect } from 'vitest';
import { baueMailText, lieferantDokumentUrl } from './LieferantReklamationenTab';
import type { LieferantReklamation } from '../types';

/** Dummy-Reklamation (DSGVO: keine echten Personen- oder Firmendaten). */
function reklamation(overrides: Partial<LieferantReklamation> = {}): LieferantReklamation {
    return {
        id: 1,
        lieferantId: 7,
        lieferantName: 'Muster Baustoffe GmbH',
        erstellerName: 'Max Mustermann',
        erstelltAm: '2026-03-04T10:15:00',
        beschreibung: 'Zwei Platten sind an der Kante gebrochen.',
        status: 'OFFEN',
        bilder: [],
        ...overrides,
    };
}

describe('baueMailText – Betreff', () => {
    it('nennt die Lieferscheinnummer, wenn es eine gibt', () => {
        const { betreff } = baueMailText(reklamation({ lieferscheinNummer: 'LS-4711' }), true, 2);
        expect(betreff).toBe('Reklamation zu Lieferschein LS-4711');
    });

    it('weicht auf den Dateinamen aus, wenn keine Nummer erkannt wurde', () => {
        const { betreff } = baueMailText(reklamation({ lieferscheinDateiname: 'scan-03.pdf' }), true, 0);
        expect(betreff).toBe('Reklamation zu Lieferschein scan-03.pdf');
    });

    it('nennt ohne Lieferschein das Datum der Reklamation', () => {
        const { betreff } = baueMailText(reklamation(), false, 1);
        expect(betreff).toBe('Reklamation vom 4.3.2026');
    });
});

// Kern von Finding 5 aus dem Review: Der Text darf nur ankündigen, was wirklich
// dranhängt – sonst sucht der Lieferant nach einem Anhang, den es nicht gibt.
describe('baueMailText – kündigt nur tatsächliche Anhänge an', () => {
    it('erwähnt den Lieferschein als Anhang, wenn er dranhängt', () => {
        const { text } = baueMailText(reklamation({ lieferscheinNummer: 'LS-4711' }), true, 3);
        expect(text).toContain('LS-4711');
        expect(text).toContain('hängt zusammen mit den Fotos an dieser E-Mail an');
    });

    it('nennt den Lieferschein, behauptet aber nicht, dass er anhängt', () => {
        const { text } = baueMailText(reklamation({ lieferscheinNummer: 'LS-4711' }), false, 3);
        expect(text).toContain('LS-4711');
        expect(text).not.toContain('Er hängt an dieser E-Mail an');
        expect(text).not.toContain('hängt zusammen mit den Fotos');
        expect(text).toContain('unsere 3 Fotos dazu hängen an dieser E-Mail an');
    });

    it('spricht von einem einzelnen Foto, wenn nur eines dranhängt', () => {
        const { text } = baueMailText(reklamation(), false, 1);
        expect(text).toContain('haben wir ein Foto angehängt');
    });

    it('erwähnt gar keine Anhänge, wenn nichts geladen werden konnte', () => {
        const { text } = baueMailText(reklamation(), false, 0);
        expect(text).not.toContain('angehängt');
        expect(text).not.toContain('E-Mail an');
    });

    it('nennt nur den Lieferschein, wenn Fotos fehlen', () => {
        const { text } = baueMailText(reklamation({ lieferscheinNummer: 'LS-4711' }), true, 0);
        expect(text).toContain('Er hängt an dieser E-Mail an');
        expect(text).not.toContain('Foto');
    });
});

describe('baueMailText – Inhalt', () => {
    it('übernimmt die vor Ort erfasste Beschreibung', () => {
        const { text } = baueMailText(reklamation(), true, 1);
        expect(text).toContain('Zwei Platten sind an der Kante gebrochen.');
    });

    it('lässt den Beschreibungsblock weg, wenn nichts erfasst wurde', () => {
        const { text } = baueMailText(reklamation({ beschreibung: '   ' }), true, 1);
        expect(text).not.toContain('Das ist uns aufgefallen');
    });

    it('macht aus Zeilenumbrüchen in der Beschreibung HTML-Umbrüche', () => {
        const { text } = baueMailText(reklamation({ beschreibung: 'Erste Zeile\nZweite Zeile' }), true, 1);
        expect(text).toContain('Erste Zeile<br>Zweite Zeile');
    });

    it('lässt Platz für die Ergänzung durch den Benutzer', () => {
        const { text } = baueMailText(reklamation(), true, 1);
        expect(text).toContain('Bitte hier noch ergänzen, woran es genau liegt');
    });

    // Beschreibung und Lieferscheinnummer stammen aus Benutzereingaben bzw. aus der
    // Dokumentenerkennung – als HTML interpretiert wären sie ein XSS-Vektor.
    it('maskiert HTML in Beschreibung und Lieferscheinnummer', () => {
        const { text } = baueMailText(reklamation({
            beschreibung: '<script>alert(1)</script>',
            lieferscheinNummer: '<img src=x onerror=alert(1)>',
        }), true, 1);

        expect(text).not.toContain('<script>');
        expect(text).not.toContain('<img src=x');
        expect(text).toContain('&lt;script&gt;');
    });
});

describe('lieferantDokumentUrl', () => {
    // Regression: Vorher zeigte der Lieferschein-Link auf /api/dokumente/{dateiname}.
    // Dieser Endpunkt kennt nur die Projekt- und Anfrage-Ordner, Lieferanten-
    // Dokumente liegen woanders – der Server antwortete darauf mit einem Fehler.
    it('adressiert das Dokument über Lieferant und Dokument-ID', () => {
        expect(lieferantDokumentUrl(3, 1473))
            .toBe('/api/lieferanten/3/dokumente/1473/download');
    });
});
