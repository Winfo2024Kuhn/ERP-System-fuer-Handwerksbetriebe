import { describe, it, expect } from 'vitest';
import { loesePlatzhalterAuf, type PlatzhalterQuelle } from './platzhalterAufloesen';
import type { KontextDaten } from './types';

function quelle(kontext: KontextDaten = {}, weitere: Partial<PlatzhalterQuelle> = {}): PlatzhalterQuelle {
    return {
        kontext,
        dokumentTyp: 'ANGEBOT',
        dokumentNummer: 'AN-2026-0001',
        datumIso: '2026-11-04',
        betreff: 'Geländer Treppenhaus',
        ...weitere,
    };
}

describe('loesePlatzhalterAuf: Briefanrede', () => {
    it('Behörde ohne Ansprechpartner: „Sehr geehrte Damen und Herren,“ statt DAMEN_HERREN', () => {
        const text = '<p>{{Anrede}} {{Ansprechpartner}},</p>';
        const ergebnis = loesePlatzhalterAuf(text, quelle({ anrede: 'DAMEN_HERREN', ansprechpartner: '' }));
        expect(ergebnis).toBe('<p>Sehr geehrte Damen und Herren,</p>');
    });

    it('Herr mit Ansprechpartner wird zur vollen Briefanrede', () => {
        const text = '<p>{{Anrede}} {{Ansprechpartner}},</p>';
        const ergebnis = loesePlatzhalterAuf(text, quelle({ anrede: 'HERR', ansprechpartner: 'Mustermann' }));
        expect(ergebnis).toBe('<p>Sehr geehrter Herr Mustermann,</p>');
    });

    it('Damen und Herren hängt keinen Namen an', () => {
        const text = '{{Anrede}} {{Ansprechpartner}},';
        const ergebnis = loesePlatzhalterAuf(text, quelle({ anrede: 'DAMEN_HERREN', ansprechpartner: 'Mustermann' }));
        expect(ergebnis).toBe('Sehr geehrte Damen und Herren,');
    });

    it('Frau ohne Ansprechpartner: „Damen und Herren“ ohne Leerzeichen vor dem Komma', () => {
        const text = '{{Anrede}} {{Ansprechpartner}},';
        const ergebnis = loesePlatzhalterAuf(text, quelle({ anrede: 'FRAU' }));
        expect(ergebnis).toBe('Sehr geehrte Damen und Herren,');
    });

    it('erkennt das Paar auch mit Kundenname, &nbsp;, geschütztem Leerzeichen, ohne und mit viel Leerraum', () => {
        const kontext: KontextDaten = { anrede: 'familie', ansprechpartner: 'Mustermann', kundenName: 'Mustermann' };
        const erwartet = 'Sehr geehrte Familie Mustermann,';
        expect(loesePlatzhalterAuf('{{ANREDE}}&nbsp;{{KUNDENNAME}},', quelle(kontext))).toBe(erwartet);
        expect(loesePlatzhalterAuf('{{Anrede}} {{Ansprechpartner}},', quelle(kontext))).toBe(erwartet);
        expect(loesePlatzhalterAuf('{{Anrede}}{{Ansprechpartner}},', quelle(kontext))).toBe(erwartet);
        expect(loesePlatzhalterAuf('{{ anrede }}  &nbsp; \n{{ ansprechpartner }},', quelle(kontext))).toBe(erwartet);
    });

    it('nimmt den Wert des zweiten Platzhalters als Namen', () => {
        const kontext: KontextDaten = { anrede: 'HERR', ansprechpartner: 'Mustermann', kundenName: 'Max Mustermann' };
        expect(loesePlatzhalterAuf('{{Anrede}} {{KUNDENNAME}},', quelle(kontext))).toBe('Sehr geehrter Herr Max Mustermann,');
    });

    it('{{Anrede}} allein ergibt den Anredetext, nie den Rohwert', () => {
        expect(loesePlatzhalterAuf('{{Anrede}},', quelle({ anrede: 'DAMEN_HERREN' }))).toBe('Sehr geehrte Damen und Herren,');
        expect(loesePlatzhalterAuf('{{Anrede}}', quelle({ anrede: 'HERR' }))).toBe('Sehr geehrter Herr');
        expect(loesePlatzhalterAuf('{{Anrede}}', quelle({}))).toBe('Sehr geehrte Damen und Herren');
    });

    it('trennt Anrede und Ansprechpartner, wenn mehr als Leerraum dazwischen steht', () => {
        const kontext: KontextDaten = { anrede: 'HERR', ansprechpartner: 'Mustermann' };
        expect(loesePlatzhalterAuf('{{Anrede}}, {{Ansprechpartner}}', quelle(kontext))).toBe('Sehr geehrter Herr, Mustermann');
        expect(loesePlatzhalterAuf('Ansprechpartner: {{Ansprechpartner}}', quelle(kontext))).toBe('Ansprechpartner: Mustermann');
    });

    it('löst mehrere Paare im selben Text auf', () => {
        const kontext: KontextDaten = { anrede: 'HERR', ansprechpartner: 'Mustermann' };
        expect(loesePlatzhalterAuf('{{Anrede}} {{Ansprechpartner}} / {{Anrede}} {{Ansprechpartner}}', quelle(kontext)))
            .toBe('Sehr geehrter Herr Mustermann / Sehr geehrter Herr Mustermann');
    });
});

describe('loesePlatzhalterAuf: übrige Platzhalter unverändert', () => {
    const kontext: KontextDaten = {
        kundenName: 'Max Mustermann',
        rechnungsadresse: 'Max Mustermann\nMusterweg 1\n12345 Musterstadt',
        kundennummer: 'K-100',
        projektnummer: 'P-200',
        projektBauvorhaben: 'Balkon',
        bezugsdokument: 'AN-2026-0001',
        bezugsdokumentTyp: 'Angebot',
        bezugsdokumentDatum: '01.11.2026',
        zahlungsziel: 14,
    };

    it('setzt Kunden-, Projekt- und Bezugswerte ein', () => {
        const text = '{{KUNDENNAME}}|{{kundenadresse}}|{{KUNDENNUMMER}}|{{PROJEKTNUMMER}}|{{BAUVORHABEN}}|{{BEZUGSDOKUMENT}}|{{BEZUGSDOKUMENTNUMMER}}|{{BEZUGSDOKUMENTTYP}}|{{BEZUGSDOKUMENTDATUM}}';
        expect(loesePlatzhalterAuf(text, quelle(kontext))).toBe(
            'Max Mustermann|Max Mustermann\nMusterweg 1\n12345 Musterstadt|K-100|P-200|Balkon|AN-2026-0001|AN-2026-0001|Angebot|01.11.2026',
        );
    });

    it('setzt Dokumentwerte ein und fällt beim Bauvorhaben auf den Betreff zurück', () => {
        const text = '{{DOKUMENTNUMMER}}|{{RECHNUNGSNUMMER}}|{{DATUM}}|{{BETREFF}}|{{DOKUMENTTYP}}|{{BAUVORHABEN}}';
        expect(loesePlatzhalterAuf(text, quelle({}))).toBe(
            'AN-2026-0001|AN-2026-0001|4.11.2026|Geländer Treppenhaus|Angebot|Geländer Treppenhaus',
        );
    });

    it('ohne Dokumentnummer: VORSCHAU in der Vorschau, sonst ENTWURF', () => {
        expect(loesePlatzhalterAuf('{{DOKUMENTNUMMER}}', quelle({}, { dokumentNummer: '' }))).toBe('VORSCHAU');
        expect(loesePlatzhalterAuf('{{DOKUMENTNUMMER}}', quelle({}, { dokumentNummer: '' }), { vorschau: false })).toBe('ENTWURF');
    });

    it('löst Zahlungsziel auf oder lässt es auf Wunsch stehen', () => {
        expect(loesePlatzhalterAuf('{{ZAHLUNGSZIEL_TAGE}} Tage bis {{ZAHLUNGSZIEL}}', quelle(kontext))).toBe('14 Tage bis 18.11.2026');
        expect(loesePlatzhalterAuf('{{ZAHLUNGSZIEL_TAGE}} Tage bis {{ZAHLUNGSZIEL}}', quelle(kontext), { zahlungszielPlatzhalterBehalten: true }))
            .toBe('{{ZAHLUNGSZIEL_TAGE}} Tage bis {{ZAHLUNGSZIEL}}');
    });

    it('wandelt einen Zahlungsziel-Chip zurück und löst ihn neu auf', () => {
        const text = 'bis <span data-zahlungsziel-chip="datum">01.01.2020</span>';
        expect(loesePlatzhalterAuf(text, quelle(kontext))).toBe('bis 18.11.2026');
    });

    it('lässt unbekannte Platzhalter stehen und leere Texte unverändert', () => {
        expect(loesePlatzhalterAuf('{{GIBTESNICHT}}', quelle(kontext))).toBe('{{GIBTESNICHT}}');
        expect(loesePlatzhalterAuf('', quelle(kontext))).toBe('');
    });

    it('ersetzt eingesetzte Werte nicht noch einmal', () => {
        const kontext: KontextDaten = { anrede: 'HERR', ansprechpartner: '{{KUNDENNAME}}', kundenName: 'Max Mustermann' };
        expect(loesePlatzhalterAuf('{{Anrede}} {{Ansprechpartner}}', quelle(kontext))).toBe('Sehr geehrter Herr {{KUNDENNAME}}');
        expect(loesePlatzhalterAuf('{{Ansprechpartner}}', quelle(kontext))).toBe('{{KUNDENNAME}}');
    });

    it('leere Kontextwerte werden zu leerem Text', () => {
        expect(loesePlatzhalterAuf('[{{KUNDENNAME}}][{{ANSPRECHPARTNER}}]', quelle({}))).toBe('[][]');
    });
});
