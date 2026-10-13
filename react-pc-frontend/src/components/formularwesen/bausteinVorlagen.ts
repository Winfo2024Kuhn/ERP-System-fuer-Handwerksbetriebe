import { FileDigit, Hash, Image, MapPin, Table2, Type, type LucideIcon } from 'lucide-react';
import type { FormBlock } from '../../types';
import { BLOCK_LABELS, DEFAULT_TABLE_COLUMNS, PAGE_FORMATS, SIZE_DEFAULTS, uid } from './constants';
import { PDF_TEXTFARBE } from './pdfStil';
import { anschriftInsFenster, infoblockPosition, mmZuPt, type Briefform } from './normMasse';

/**
 * Bausteine, die man links in die Vorlage zieht oder anklickt.
 *
 * Anschrift, Nummer & Datum und Seitenzahl sind bewusst Textbausteine mit
 * Platzhaltern: Die füllt jeder Weg zum PDF (Dokument-Editor, automatischer
 * Versand von Auftragsbestätigung und Mahnung) gleich aus.
 */
export type BausteinArt = 'text' | 'anschrift' | 'infoblock' | 'seitenzahl' | 'tabelle' | 'logo';

export interface BausteinVorlage {
    art: BausteinArt;
    titel: string;
    beschreibung: string;
    Icon: LucideIcon;
}

export const BAUSTEINE: BausteinVorlage[] = [
    { art: 'text', titel: 'Text', beschreibung: 'Freier Text, auch mit Platzhaltern', Icon: Type },
    { art: 'anschrift', titel: 'Anschrift', beschreibung: 'Kundenadresse, sitzt gleich im Fenster', Icon: MapPin },
    { art: 'infoblock', titel: 'Nummer & Datum', beschreibung: 'Dokumentnummer, Datum, Kundennummer', Icon: Hash },
    { art: 'tabelle', titel: 'Positionen', beschreibung: 'Bereich für die Leistungstabelle', Icon: Table2 },
    { art: 'seitenzahl', titel: 'Seitenzahl', beschreibung: '„Seite 1 / 2“', Icon: FileDigit },
    { art: 'logo', titel: 'Logo / Bild', beschreibung: 'Firmenlogo einfügen', Icon: Image },
];

/** MIME-Typ beim Ziehen eines Bausteins auf die Seite. */
export const BAUSTEIN_ZIEH_TYP = 'application/x-formular-baustein';

export const istBausteinArt = (wert: string): wert is BausteinArt => BAUSTEINE.some(b => b.art === wert);

interface NeuerBausteinOptionen {
    seite: number;
    items: FormBlock[];
    form: Briefform;
    /** Mittelpunkt in pt, wenn der Baustein auf die Seite gezogen wurde. */
    mitte?: { x: number; y: number };
}

type Grundform = Pick<FormBlock, 'type' | 'x' | 'y' | 'width' | 'height' | 'content' | 'styles' | 'tableColumns'>;

const SEITE = PAGE_FORMATS.A4;
const textStil = (fontSize: number) => ({ fontSize, fontWeight: '400', color: PDF_TEXTFARBE });
const mitte = (breite: number, hoehe: number) => ({ x: Math.round((SEITE.width - breite) / 2), y: Math.round((SEITE.height - hoehe) / 2) });

function grundform(art: BausteinArt, form: Briefform): Grundform {
    switch (art) {
        case 'anschrift':
            return {
                type: 'text', ...anschriftInsFenster(form), content: '{{KUNDENADRESSE}}',
                styles: textStil(10),
            };
        case 'infoblock':
            return {
                type: 'text', ...infoblockPosition(form),
                content: '{{DOKUMENTTYP}} {{DOKUMENTNUMMER}}\nDatum: {{DATUM}}\nKundennr.: {{KUNDENNUMMER}}',
                styles: textStil(9),
            };
        case 'seitenzahl': {
            const breite = 120;
            return {
                type: 'text', x: Math.round(SEITE.width - mmZuPt(20) - breite), y: Math.round(SEITE.height - mmZuPt(15)), width: breite, height: 20,
                content: 'Seite {{SEITENZAHL}}', styles: textStil(9),
            };
        }
        case 'tabelle': {
            const { width, height } = SIZE_DEFAULTS.table;
            return { type: 'table', ...mitte(width, height), width, height, tableColumns: DEFAULT_TABLE_COLUMNS };
        }
        case 'logo': {
            const { width, height } = SIZE_DEFAULTS.logo;
            return { type: 'logo', ...mitte(width, height), width, height, content: '/image001.png' };
        }
        case 'text':
        default: {
            const width = 240;
            const height = 60;
            return { type: 'text', ...mitte(width, height), width, height, content: 'Neuer Text', styles: textStil(10) };
        }
    }
}

/** Neuer Baustein mit Norm- bzw. Standardposition, oder mittig an der Stelle, wo er abgelegt wurde. */
export function neuerBaustein(art: BausteinArt, { seite, items, form, mitte: ablage }: NeuerBausteinOptionen): FormBlock {
    const basis = grundform(art, form);
    const lage = ablage
        ? { x: Math.round(ablage.x - basis.width / 2), y: Math.round(ablage.y - basis.height / 2) }
        : { x: basis.x, y: basis.y };
    return {
        ...basis,
        ...lage,
        id: uid(),
        page: seite,
        z: Math.max(0, ...items.map(i => i.z)) + 1,
    };
}

/** Name eines Bausteins in der Sprache der Werkzeugleiste (für Ebenen und Auswahl). */
export function bausteinName(block: Pick<FormBlock, 'type' | 'content'>): string {
    const inhalt = block.content ?? '';
    switch (block.type) {
        case 'table': return 'Positionen';
        case 'logo': return 'Logo / Bild';
        case 'adresse': return 'Anschrift';
        case 'seitenzahl': return 'Seitenzahl';
        case 'text':
        case 'heading':
            if (inhalt.includes('{{KUNDENADRESSE}}')) return 'Anschrift';
            if (inhalt.includes('{{SEITENZAHL}}')) return 'Seitenzahl';
            if (inhalt.includes('{{DOKUMENTNUMMER}}') || inhalt.includes('{{DATUM}}')) return 'Nummer & Datum';
            return block.type === 'heading' ? 'Überschrift' : 'Text';
        default: return BLOCK_LABELS[block.type] ?? 'Baustein';
    }
}

/** Passendes Symbol zum Namen. */
export function bausteinIcon(block: Pick<FormBlock, 'type' | 'content'>): LucideIcon {
    const name = bausteinName(block);
    return BAUSTEINE.find(b => b.titel === name)?.Icon ?? Type;
}
