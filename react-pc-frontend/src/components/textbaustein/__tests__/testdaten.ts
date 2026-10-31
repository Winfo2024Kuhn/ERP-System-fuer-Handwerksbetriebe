/**
 * Gemeinsame Dummy-Daten für die Vitest-Tests der Textbausteine (DSGVO: nur
 * Musterwerte). Wird nur von Tests importiert.
 */
import type { DokumenttypGruppe, Platzhalter, TextbausteinDto } from '../typen';

export const TEST_KATALOG: Platzhalter[] = [
    { token: '{{Anrede}}', label: 'Anrede', beschreibung: 'Briefanrede', beispiel: 'Sehr geehrter Herr', gruppe: 'Kunde' },
    { token: '{{KUNDENNAME}}', label: 'Kundenname', beschreibung: 'Name bzw. Firma des Kunden', beispiel: 'Max Mustermann', gruppe: 'Kunde' },
    { token: '{{Ansprechpartner}}', label: 'Ansprechpartner', beschreibung: 'Ansprechpartner beim Kunden', beispiel: 'Erika Musterfrau', gruppe: 'Kunde' },
    { token: '{{DOKUMENTNUMMER}}', label: 'Dokumentnummer', beschreibung: 'Nummer des Dokuments', beispiel: 'RE-2026-0108', gruppe: 'Dokument' },
    { token: '{{ZAHLUNGSZIEL}}', label: 'Zahlungsziel', beschreibung: 'Fälligkeitsdatum', beispiel: '29.03.2026', gruppe: 'Zahlung' },
];

export const TEST_GRUPPEN: DokumenttypGruppe[] = [
    { gruppe: 'Angebote & Aufträge', dokumenttypen: ['Angebot', 'Nachtragsangebot', 'Auftragsbestätigung'] },
    { gruppe: 'Rechnungen', dokumenttypen: ['Rechnung', 'Teilrechnung', 'Abschlagsrechnung', 'Schlussrechnung'] },
    { gruppe: 'Mahnungen', dokumenttypen: ['Zahlungserinnerung', '1. Mahnung', '2. Mahnung'] },
    { gruppe: 'Korrekturen', dokumenttypen: ['Stornorechnung', 'Gutschrift'] },
];

export function testBaustein(teil: Partial<TextbausteinDto> & Pick<TextbausteinDto, 'id' | 'name'>): TextbausteinDto {
    return {
        typ: 'VORTEXT',
        beschreibung: 'Einleitung',
        html: '<p>Guten Tag</p>',
        placeholders: [],
        dokumenttypen: [],
        sortOrder: 3,
        ...teil,
    };
}
