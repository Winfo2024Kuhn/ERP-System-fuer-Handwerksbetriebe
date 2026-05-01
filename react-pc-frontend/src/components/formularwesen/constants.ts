import type { FormBlockType, FormBlockStyles } from '../../types';

// Page Formats - Only DIN A4 is supported
export type PageFormat = 'A4';

export const PAGE_FORMATS: Record<PageFormat, { width: number; height: number; label: string }> = {
    A4: { width: 595, height: 842, label: 'DIN A4 (210 × 297 mm)' }
};

// Constants
export const SNAP_THRESHOLD = 10;
export const MIN_WIDTH = 20;
export const MIN_HEIGHT = 10;

export const SAMPLE_DATA: Record<string, string> = {
    dokumentnummer: 'RE-2025/10/00042',
    projektnummer: 'PRJ-2025-0815',
    kundennummer: 'KD-10023',
    kundenadresse: 'Beispiel GmbH\nFrau Erika Muster\nHauptstraße 12\n97070 Würzburg',
    kundenname: 'Beispiel GmbH',
    dokumenttyp: 'Rechnung',
    datum: '01.12.2025',
    seitenzahl: '1 / 2',
    bauvorhaben: 'Anbau Musterstraße 12',
    anrede: 'Sehr geehrte Frau',
    ansprechpartner: 'Erika Muster',
    bezugsdokument: 'AN-2025-001',
    bezugsdokumentTyp: 'Angebot'
};

/** Pool of realistic sample data sets for the preview */
const SAMPLE_POOL = [
    {
        dokumentnummer: 'RE-2026/02/00187',
        projektnummer: 'PRJ-2026-0312',
        kundennummer: 'KD-10478',
        kundenadresse: 'Müller Bau GmbH\nHerr Thomas Müller\nBahnhofstraße 5\n80335 München',
        kundenname: 'Müller Bau GmbH',
        dokumenttyp: 'Angebot',
        datum: '19.02.2026',
        seitenzahl: '1 / 3',
        bauvorhaben: 'Dachsanierung Lindwurmstr. 88',
        anrede: 'Sehr geehrter Herr',
        ansprechpartner: 'Thomas Müller',
        bezugsdokument: 'AN-2026-044',
        bezugsdokumentTyp: 'Angebot'
    },
    {
        dokumentnummer: 'AB-2026/01/00053',
        projektnummer: 'PRJ-2025-1190',
        kundennummer: 'KD-10291',
        kundenadresse: 'Schmidt & Partner\nFrau Claudia Weber\nKirchenweg 23\n90402 Nürnberg',
        kundenname: 'Schmidt & Partner',
        dokumenttyp: 'Auftragsbestätigung',
        datum: '14.01.2026',
        seitenzahl: '1 / 2',
        bauvorhaben: 'Fassadenarbeiten Kaiserstr. 7',
        anrede: 'Sehr geehrte Frau',
        ansprechpartner: 'Claudia Weber',
        bezugsdokument: 'RE-2025-389',
        bezugsdokumentTyp: 'Rechnung'
    },
    {
        dokumentnummer: 'RE-2026/02/00201',
        projektnummer: 'PRJ-2026-0088',
        kundennummer: 'KD-10655',
        kundenadresse: 'Hausverwaltung Krause\nHerr Markus Krause\nAm Marktplatz 3\n97082 Würzburg',
        kundenname: 'Hausverwaltung Krause',
        dokumenttyp: 'Rechnung',
        datum: '07.02.2026',
        seitenzahl: '1 / 2',
        bauvorhaben: 'Heizungstausch Residenzstr. 14',
        anrede: 'Sehr geehrter Herr',
        ansprechpartner: 'Markus Krause',
        bezugsdokument: 'AB-2026-012',
        bezugsdokumentTyp: 'Auftragsbestätigung'
    },
    {
        dokumentnummer: 'AN-2026/02/00095',
        projektnummer: 'PRJ-2026-0401',
        kundennummer: 'KD-10812',
        kundenadresse: 'Becker Immobilien AG\nFrau Dr. Sabine Becker\nSchloßstraße 18\n70173 Stuttgart',
        kundenname: 'Becker Immobilien AG',
        dokumenttyp: 'Angebot',
        datum: '18.02.2026',
        seitenzahl: '1 / 4',
        bauvorhaben: 'Kernsanierung Bürogebäude Hohenheim',
        anrede: 'Sehr geehrte Frau',
        ansprechpartner: 'Dr. Sabine Becker',
        bezugsdokument: 'AN-2026-091',
        bezugsdokumentTyp: 'Angebot'
    }
];

/** Get a random sample data set for preview rendering */
export function getRandomSampleData(): Record<string, string> {
    return SAMPLE_POOL[Math.floor(Math.random() * SAMPLE_POOL.length)];
}

/** Replace all {{PLACEHOLDER}} tokens in a string with sample data values */
export function replacePlaceholders(text: string, data: Record<string, string>): string {
    return text.replace(/\{\{(\w+)\}\}/g, (_match, key: string) => {
        const lowerKey = key.toLowerCase();
        return data[lowerKey] ?? _match;
    });
}

import { AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN, GESCHAEFTSDOKUMENT_TYPEN } from '../../types';
export const DOCUMENT_TYPES = [
    ...new Set([
        ...AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN.map(d => d.label),
        ...GESCHAEFTSDOKUMENT_TYPEN.map(d => d.label),
    ])
];

export const PLACEHOLDER_MAP: Record<string, string> = {
    '{{DOKUMENTNUMMER}}': SAMPLE_DATA.dokumentnummer,
    '{{PROJEKTNUMMER}}': SAMPLE_DATA.projektnummer,
    '{{BAUVORHABEN}}': SAMPLE_DATA.bauvorhaben,
    '{{KUNDENADRESSE}}': SAMPLE_DATA.kundenadresse,
    '{{KUNDENNAME}}': SAMPLE_DATA.kundenname,
    '{{KUNDENNUMMER}}': SAMPLE_DATA.kundennummer,
    '{{DATUM}}': SAMPLE_DATA.datum,
    '{{SEITENZAHL}}': SAMPLE_DATA.seitenzahl,
    '{{DOKUMENTTYP}}': SAMPLE_DATA.dokumenttyp,
    '{{ANREDE}}': SAMPLE_DATA.anrede,
    '{{ANSPRECHPARTNER}}': SAMPLE_DATA.ansprechpartner,
    '{{BEZUGSDOKUMENT}}': SAMPLE_DATA.bezugsdokument,
    '{{BEZUGSDOKUMENTNUMMER}}': SAMPLE_DATA.bezugsdokument,
    '{{BEZUGSDOKUMENTTYP}}': SAMPLE_DATA.bezugsdokumentTyp
};

export const SIZE_DEFAULTS: Record<FormBlockType, { width: number; height: number }> = {
    heading: { width: 300, height: 72 },
    text: { width: 340, height: 180 },
    doknr: { width: 120, height: 52 },
    projektnr: { width: 120, height: 52 },
    kundennummer: { width: 120, height: 52 },
    kunde: { width: 120, height: 64 },
    adresse: { width: 180, height: 150 },
    dokumenttyp: { width: 140, height: 48 },
    datum: { width: 120, height: 48 },
    seitenzahl: { width: 120, height: 48 },
    logo: { width: 200, height: 120 },
    table: { width: 560, height: 220 }
};

export const STYLE_DEFAULTS: Partial<Record<FormBlockType, FormBlockStyles>> = {
    heading: { fontSize: 20, fontWeight: '700', color: '#111827', textAlign: 'left' },
    text: { fontSize: 14, fontWeight: '400', color: '#111827', textAlign: 'left' },
    doknr: { fontSize: 12, fontWeight: '600', color: '#111827', textAlign: 'left' },
    projektnr: { fontSize: 12, fontWeight: '600', color: '#111827', textAlign: 'left' },
    kundennummer: { fontSize: 12, fontWeight: '600', color: '#111827', textAlign: 'left' },
    kunde: { fontSize: 14, fontWeight: '600', color: '#111827', textAlign: 'left' },
    adresse: { fontSize: 13, fontWeight: '400', color: '#111827', textAlign: 'left' },
    dokumenttyp: { fontSize: 16, fontWeight: '700', color: '#111827', textAlign: 'left' },
    datum: { fontSize: 12, fontWeight: '600', color: '#111827', textAlign: 'left' },
    seitenzahl: { fontSize: 12, fontWeight: '600', color: '#111827', textAlign: 'right' }
};

export const BLOCK_LABELS: Record<FormBlockType, string> = {
    heading: 'Überschrift',
    text: 'Freitext',
    doknr: 'Dokumentnummer',
    projektnr: 'Projektnummer',
    kundennummer: 'Kundennummer',
    kunde: 'Kundenname',
    adresse: 'Kundenadresse',
    dokumenttyp: 'Dokumenttyp',
    datum: 'Datum',
    seitenzahl: 'Seitenzahl',
    logo: 'Logo',
    table: 'Leistungstabelle'
};

export const BLOCK_ICONS: Record<FormBlockType, string> = {
    heading: 'H',
    text: 'T',
    doknr: '#',
    projektnr: 'P',
    kundennummer: 'K#',
    kunde: 'K',
    adresse: '📍',
    dokumenttyp: 'D',
    datum: '📅',
    seitenzahl: '📄',
    logo: '🖼',
    table: '▦'
};

/** Block categories for the sidebar — organized by function */
export const BLOCK_CATEGORIES: { label: string; types: FormBlockType[] }[] = [
    {
        label: 'Dokument-Felder',
        types: ['dokumenttyp', 'doknr', 'datum', 'kundennummer', 'projektnr', 'kunde', 'adresse']
    },
    {
        label: 'Inhalt',
        types: ['heading', 'text', 'table']
    },
    {
        label: 'Sonstiges',
        types: ['logo', 'seitenzahl']
    }
];

export const DEFAULT_TABLE_COLUMNS = {
    pos: 40,
    menge: 50,
    me: 40,
    bezeichnung: 280,
    ep: 70,
    gp: 80
};

export const DEFAULT_ITEMS: Omit<import('../../types').FormBlock, 'id'>[] = [
    // Firmenlogo oben rechts
    { type: 'logo', page: 1, x: 400, y: 24, z: 1, content: '/image001.png', width: 170, height: 80 },
    // Dokumenttyp (z.B. "Rechnung", "Angebot")
    { type: 'dokumenttyp', page: 1, x: 400, y: 110, z: 2, width: 170, height: 36, styles: STYLE_DEFAULTS.dokumenttyp },
    // Dokumentnummer
    { type: 'doknr', page: 1, x: 400, y: 148, z: 3, width: 170, height: 36, styles: STYLE_DEFAULTS.doknr },
    // Datum
    { type: 'datum', page: 1, x: 400, y: 186, z: 4, width: 170, height: 36, styles: STYLE_DEFAULTS.datum },
    // Kundennummer
    { type: 'kundennummer', page: 1, x: 400, y: 224, z: 5, width: 170, height: 36, styles: STYLE_DEFAULTS.kundennummer },
    // Projektnummer
    { type: 'projektnr', page: 1, x: 400, y: 262, z: 6, width: 170, height: 36, styles: STYLE_DEFAULTS.projektnr },
    // Kundenadresse links
    { type: 'adresse', page: 1, x: 24, y: 148, z: 7, width: 280, height: 140, styles: STYLE_DEFAULTS.adresse },
    // Betreff / Freitext
    { type: 'text', page: 1, x: 24, y: 300, z: 8, content: '{{BETREFF}}', width: 340, height: 40, styles: STYLE_DEFAULTS.text },
    // Leistungstabelle
    { type: 'table', page: 1, x: 24, y: 350, z: 9, width: 548, height: 400, tableColumns: DEFAULT_TABLE_COLUMNS },
    // Seitenzahl auf Seite 2 (Folgeseiten)
    { type: 'seitenzahl', page: 2, x: 480, y: 20, z: 1, width: 100, height: 30, styles: STYLE_DEFAULTS.seitenzahl },
    // Leistungstabelle Folgeseiten (größerer Bereich)
    { type: 'table', page: 2, x: 24, y: 60, z: 2, width: 548, height: 720, tableColumns: DEFAULT_TABLE_COLUMNS }
];

export const uid = () => 'blk-' + Math.random().toString(16).slice(2);

export const isTextualType = (type: FormBlockType) =>
    ['heading', 'text', 'doknr', 'projektnr', 'kundennummer', 'kunde', 'adresse', 'dokumenttyp', 'datum', 'seitenzahl'].includes(type);
