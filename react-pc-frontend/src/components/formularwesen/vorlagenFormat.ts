import type { FormBlock, FormBlockStyles, FormBlockType } from '../../types';
import { parseHtmlInert } from '../../lib/htmlSanitizer';
import { DEFAULT_ITEMS, SIZE_DEFAULTS, uid } from './constants';
import type { Briefform } from './normMasse';

/**
 * Speicherformat der Formular-Vorlagen.
 *
 * Das Backend druckt die PDFs aus genau diesem HTML (RechnungPdfService,
 * AutoAuftragsbestaetigungVersandService.parseVorlagenHtml), der Dokument-
 * Editor liest es ebenfalls. Deshalb ist das Format eingefroren: ein Block ist
 * ein `<div>` mit `data-block-type`, `data-page`, `data-x/y/z/width/height`
 * (pt, Ursprung oben links, A4 = 595 × 842) und optional `data-content` und
 * `data-style`; Briefpapier steckt in `<meta name="background-image[-page2]">`.
 *
 * Einzige Ergänzung: `<meta name="briefform" content="A|B">`. Es wird nur
 * geschrieben, wenn die Briefform in dieser Vorlage gewählt wurde – eine alte
 * Vorlage ohne Meta wird darum Zeichen für Zeichen gleich zurückgeschrieben
 * und gilt als Form B.
 */

/** URL-kodierten Wert lesen; ist er kaputt (alte Vorlage), bleibt der Rohwert stehen statt abzustürzen. */
function entschluesseln(wert: string): string {
    try { return decodeURIComponent(wert); } catch { return wert; }
}

export interface VorlagenStand {
    items: FormBlock[];
    backgroundImage: string | null;
    backgroundImagePage2: string | null;
    /** `null` = in der Vorlage nicht festgelegt (alte Vorlage) → Form B. */
    briefform: Briefform | null;
}

const CSS = 'body{font-family:Inter,Arial,sans-serif;}';

export function serialisiereVorlage({ items, backgroundImage, backgroundImagePage2, briefform }: VorlagenStand): string {
    let metadata = '<meta name="page-format" content="A4" />';
    if (backgroundImage) metadata += `<meta name="background-image" content="${encodeURIComponent(backgroundImage)}" />`;
    if (backgroundImagePage2) metadata += `<meta name="background-image-page2" content="${encodeURIComponent(backgroundImagePage2)}" />`;
    if (briefform) metadata += `<meta name="briefform" content="${briefform}" />`;
    const htmlPages = [1, 2].map(page => {
        const blocks = items.filter(i => i.page === page).sort((a, b) => a.z - b.z).map(i => {
            const encodedContent = i.content ? ` data-content="${encodeURIComponent(i.content)}"` : '';
            const encodedStyle = i.styles ? ` data-style="${encodeURIComponent(JSON.stringify(i.styles))}"` : '';
            return `<div data-block-type="${i.type}" data-page="${i.page}" data-x="${i.x}" data-y="${i.y}" data-z="${i.z}" data-width="${i.width}" data-height="${i.height}"${encodedContent}${encodedStyle}></div>`;
        }).join('');
        return `<div class="page">${blocks}</div>`;
    }).join('');
    return `${metadata}<style>${CSS}</style>${htmlPages}`;
}

/** Standard-Layout einer neuen Vorlage, mit frischen IDs. */
export const standardBloecke = (): FormBlock[] => DEFAULT_ITEMS.map(i => ({ ...i, id: uid() }));

export function leseBloecke(html: string): FormBlock[] {
    const wrapper = parseHtmlInert(html || '');
    const found = wrapper.querySelectorAll('[data-block-type]');
    if (!found.length && !(html || '').includes('<div class="page">')) return standardBloecke();
    if (!found.length) return [];
    return Array.from(found).map((el, idx) => {
        const dataset = (el as HTMLElement).dataset;
        const type = dataset.blockType as FormBlockType;
        // Ohne gespeicherten Stil bleibt der Stil leer: so druckt das PDF weiter
        // wie bisher (10 pt), und die Vorlage wird unverändert zurückgeschrieben.
        let styles: FormBlockStyles | undefined;
        if (dataset.style) {
            try { styles = JSON.parse(decodeURIComponent(dataset.style)); } catch { /* unlesbarer Stil: wie ohne Stil */ }
        }
        return {
            id: uid(),
            type,
            page: Number(dataset.page || 1),
            x: Number(dataset.x || 32),
            y: Number(dataset.y || 32),
            z: Number(dataset.z || idx + 1),
            width: Number(dataset.width) || SIZE_DEFAULTS[type]?.width || 200,
            height: Number(dataset.height) || SIZE_DEFAULTS[type]?.height || 80,
            content: dataset.content ? entschluesseln(dataset.content) : undefined,
            styles,
        };
    });
}

const metaInhalt = (wrapper: ParentNode, name: string): string | null => {
    const wert = wrapper.querySelector(`meta[name="${name}"]`)?.getAttribute('content');
    return wert ? entschluesseln(wert) : null;
};

export function leseBriefform(html: string): Briefform | null {
    const wert = metaInhalt(parseHtmlInert(html || ''), 'briefform');
    return wert === 'A' || wert === 'B' ? wert : null;
}

export function leseVorlage(html: string): VorlagenStand {
    const wrapper = parseHtmlInert(html || '');
    return {
        items: leseBloecke(html),
        backgroundImage: metaInhalt(wrapper, 'background-image'),
        backgroundImagePage2: metaInhalt(wrapper, 'background-image-page2'),
        briefform: leseBriefform(html),
    };
}
