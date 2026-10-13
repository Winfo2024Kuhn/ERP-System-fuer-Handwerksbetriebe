import type { FormBlock, FormBlockStyles } from '../../types';

/**
 * Schrift eines Bausteins so, wie sie der PDF-Dienst druckt
 * (RechnungPdfService.parseBlockFont): ohne Stil 10 pt normal in Textfarbe,
 * Farbe nur als Hex, fett bei „700“/„bold“, Betreff immer Montserrat 11 pt.
 * Eine Text-Ausrichtung kennt der Druck nicht – Text steht immer links.
 */
export const PDF_TEXTFARBE = '#44403c';
export const PDF_SCHRIFTGROESSE = 10;
const BETREFF_GROESSE = 11;

export interface PdfStil {
    schriftgroesse: number;
    fett: boolean;
    farbe: string;
    /** {{BETREFF}}: feste Betreff-Schrift, Größe und Farbe aus der Vorlage gelten nicht. */
    betreff: boolean;
}

export function pdfStil(block: Pick<FormBlock, 'content' | 'styles'>): PdfStil {
    if ((block.content ?? '').includes('{{BETREFF}}')) {
        return { schriftgroesse: BETREFF_GROESSE, fett: true, farbe: PDF_TEXTFARBE, betreff: true };
    }
    const s = block.styles;
    if (!s || Object.keys(s).length === 0) {
        return { schriftgroesse: PDF_SCHRIFTGROESSE, fett: false, farbe: PDF_TEXTFARBE, betreff: false };
    }
    const gewicht = String(s.fontWeight ?? '');
    return {
        schriftgroesse: typeof s.fontSize === 'number' && s.fontSize > 0 ? s.fontSize : PDF_SCHRIFTGROESSE,
        fett: gewicht === '700' || gewicht.toLowerCase() === 'bold',
        farbe: typeof s.color === 'string' && s.color.startsWith('#') ? s.color : PDF_TEXTFARBE,
        betreff: false,
    };
}

/**
 * Neuer Stil nach einer Änderung im Eigenschaften-Feld: Was bisher gedruckt
 * wurde, wird ausgeschrieben, damit sich außer der Änderung nichts verschiebt.
 * Unbekannte gespeicherte Felder (z. B. textAlign) bleiben erhalten.
 */
export function stilMitAenderung(block: Pick<FormBlock, 'content' | 'styles'>, aenderung: Partial<FormBlockStyles>): FormBlockStyles {
    const jetzt = pdfStil({ styles: block.styles });
    return {
        ...block.styles,
        fontSize: jetzt.schriftgroesse,
        fontWeight: jetzt.fett ? '700' : '400',
        color: jetzt.farbe,
        ...aenderung,
    };
}
