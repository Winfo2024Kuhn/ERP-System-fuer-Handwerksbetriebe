import { describe, it, expect } from 'vitest';
import { baueAuftragsnummerHinweis, type NaechsteAuftragsnummerResponse } from './auftragsnummerHinweis';

/**
 * Der Hinweistext erklärt dem Benutzer im Anlege-Dialog, wie die vorgeschlagene
 * Auftragsnummer zustande kommt. Er muss ohne Fachbegriffe auskommen.
 */
describe('baueAuftragsnummerHinweis', () => {
    const basis: NaechsteAuftragsnummerResponse = {
        auftragsnummer: '2026/05/00300',
        prefix: '2026/05/',
        zaehler: 300,
        kundenLogik: true,
        neuerKundeImJahr: true,
        kundenSlot: 3,
        auftragImJahr: 1,
    };

    it('erklärt bei einem im Jahr neuen Kunden die frisch vergebene Kundennummer', () => {
        const hinweis = baueAuftragsnummerHinweis(basis, 'Max Mustermann');

        expect(hinweis).toBe(
            'Max Mustermann hat 2026 noch keinen Auftrag — bekommt die Kundennummer 003 und damit den ersten Auftrag.',
        );
    });

    it('zählt bei einem Bestandskunden die bisherigen Aufträge auf', () => {
        const hinweis = baueAuftragsnummerHinweis(
            { ...basis, auftragsnummer: '2026/05/00302', neuerKundeImJahr: false, auftragImJahr: 3 },
            'Max Mustermann',
        );

        expect(hinweis).toBe(
            'Max Mustermann hatte 2026 schon 2 Aufträge (Kundennummer 003) — das hier ist Auftrag Nummer 3.',
        );
    });

    it('nutzt die Einzahl, wenn erst ein Auftrag vorliegt', () => {
        const hinweis = baueAuftragsnummerHinweis(
            { ...basis, neuerKundeImJahr: false, auftragImJahr: 2 },
            'Max Mustermann',
        );

        expect(hinweis).toContain('schon einen Auftrag');
        expect(hinweis).toContain('Auftrag Nummer 2');
    });

    it('schweigt, wenn die Nummer ohne Kundenbezug fortlaufend vergeben wurde', () => {
        const hinweis = baueAuftragsnummerHinweis(
            { ...basis, kundenLogik: false, kundenSlot: 0, auftragImJahr: 0 },
            'Max Mustermann',
        );

        expect(hinweis).toBeNull();
    });

    it('kommt ohne Kundennamen aus', () => {
        const hinweis = baueAuftragsnummerHinweis(basis);

        expect(hinweis).toBe(
            'Der Kunde hat 2026 noch keinen Auftrag — bekommt die Kundennummer 003 und damit den ersten Auftrag.',
        );
    });
});
