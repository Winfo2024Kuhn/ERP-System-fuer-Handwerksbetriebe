import { describe, expect, it } from 'vitest';
import type { FormBlock } from '../../types';
import { DEFAULT_ITEMS, STYLE_DEFAULTS } from './constants';
import { leseBloecke, leseBriefform, leseVorlage, serialisiereVorlage, standardBloecke } from './vorlagenFormat';

/**
 * Genau so hat FormularwesenMain.serialize() vor der Überarbeitung gespeichert
 * (Stand vor Oktober 2026). Dient als feste Referenz: das Speicherformat darf
 * sich nicht ändern, weil das Backend die PDFs daraus druckt.
 */
function altesSerialisieren(items: FormBlock[], backgroundImage: string | null, backgroundImagePage2: string | null): string {
    const css = `body{font-family:Inter,Arial,sans-serif;}`;
    let metadata = `<meta name="page-format" content="A4" />`;
    if (backgroundImage) metadata += `<meta name="background-image" content="${encodeURIComponent(backgroundImage)}" />`;
    if (backgroundImagePage2) metadata += `<meta name="background-image-page2" content="${encodeURIComponent(backgroundImagePage2)}" />`;
    const htmlPages = [1, 2].map(page => {
        const blocks = items.filter(i => i.page === page).sort((a, b) => a.z - b.z).map(i => {
            const encodedContent = i.content ? ` data-content="${encodeURIComponent(i.content)}"` : '';
            const encodedStyle = i.styles ? ` data-style="${encodeURIComponent(JSON.stringify(i.styles))}"` : '';
            return `<div data-block-type="${i.type}" data-page="${i.page}" data-x="${i.x}" data-y="${i.y}" data-z="${i.z}" data-width="${i.width}" data-height="${i.height}"${encodedContent}${encodedStyle}></div>`;
        }).join('');
        return `<div class="page">${blocks}</div>`;
    }).join('');
    return `${metadata}<style>${css}</style>${htmlPages}`;
}

const BRIEFPAPIER = 'data:image/png;base64,iVBORw0KGgo=';
const ALTE_BLOECKE: FormBlock[] = [
    { id: '1', type: 'logo', page: 1, x: 400, y: 24, z: 1, width: 170, height: 80, content: '/image001.png' },
    { id: '2', type: 'text', page: 1, x: 56.5, y: 148, z: 3, width: 280.25, height: 140, content: 'Max Mustermann\n{{KUNDENADRESSE}} & "Zitat" <b>', styles: { fontSize: 11, fontWeight: '400', color: '#111827', textAlign: 'left' } },
    { id: '3', type: 'adresse', page: 1, x: 24, y: 300, z: 2, width: 200, height: 80, styles: STYLE_DEFAULTS.adresse },
    { id: '4', type: 'table', page: 1, x: 24, y: 350, z: 4, width: 548, height: 400 },
    { id: '5', type: 'table', page: 2, x: 24, y: 60, z: 1, width: 548, height: 720 },
];
const ALTE_VORLAGE = altesSerialisieren(ALTE_BLOECKE, BRIEFPAPIER, '/briefpapier-folgeseite.png');

describe('Speicherformat', () => {
    it('schreibt eine alte Vorlage ohne Änderung Zeichen für Zeichen gleich zurück', () => {
        expect(serialisiereVorlage(leseVorlage(ALTE_VORLAGE))).toBe(ALTE_VORLAGE);
    });

    it('ergänzt bei Bausteinen ohne Stil keinen Stil (sonst druckte das PDF größer)', () => {
        const ohneStil = '<meta name="page-format" content="A4" /><style>body{font-family:Inter,Arial,sans-serif;}</style>'
            + '<div class="page"><div data-block-type="text" data-page="1" data-x="57" data-y="178" data-z="1" data-width="240" data-height="77" data-content="%7B%7BKUNDENADRESSE%7D%7D"></div></div><div class="page"></div>';
        expect(serialisiereVorlage(leseVorlage(ohneStil))).toBe(ohneStil);
    });

    it('serialisiert wie das alte Verfahren, solange keine Briefform gewählt ist', () => {
        expect(serialisiereVorlage({ items: ALTE_BLOECKE, backgroundImage: null, backgroundImagePage2: null, briefform: null }))
            .toBe(altesSerialisieren(ALTE_BLOECKE, null, null));
    });

    it('liest Bausteine, Briefpapier und fehlende Briefform einer alten Vorlage', () => {
        const stand = leseVorlage(ALTE_VORLAGE);
        expect(stand.backgroundImage).toBe(BRIEFPAPIER);
        expect(stand.backgroundImagePage2).toBe('/briefpapier-folgeseite.png');
        expect(stand.briefform).toBeNull();
        const ohneId = (b: FormBlock) => ({ ...b, id: undefined });
        // in Speicher-Reihenfolge: Seite 1 nach z, dann Seite 2
        expect(stand.items.map(ohneId)).toEqual(
            [ALTE_BLOECKE[0], ALTE_BLOECKE[2], ALTE_BLOECKE[1], ALTE_BLOECKE[3], ALTE_BLOECKE[4]].map(ohneId),
        );
    });

    it('schreibt die gewählte Briefform als zusätzliches Meta und liest sie wieder', () => {
        const html = serialisiereVorlage({ ...leseVorlage(ALTE_VORLAGE), briefform: 'A' });
        expect(html).toContain('<meta name="briefform" content="A" />');
        expect(html.replace('<meta name="briefform" content="A" />', '')).toBe(ALTE_VORLAGE);
        expect(leseBriefform(html)).toBe('A');
        expect(leseVorlage(html).briefform).toBe('A');
        expect(leseBriefform(serialisiereVorlage({ ...leseVorlage(ALTE_VORLAGE), briefform: 'B' }))).toBe('B');
    });

    it('kommt mit fehlendem HTML klar', () => {
        expect(leseVorlage(undefined as unknown as string)).toMatchObject({ backgroundImage: null, briefform: null });
    });

    it('ignoriert unbekannte Briefform-Werte', () => {
        expect(leseBriefform('<meta name="briefform" content="C" />')).toBeNull();
        expect(leseBriefform('')).toBeNull();
    });

    it('nimmt bei unlesbar kodierten Meta-Werten den Rohwert', () => {
        expect(leseVorlage('<meta name="background-image" content="%E0%A4%A" /><div class="page"></div>').backgroundImage).toBe('%E0%A4%A');
    });
});

describe('leseBloecke', () => {
    it('ohne Bausteine und ohne Seiten: Standard-Layout', () => {
        const bloecke = leseBloecke('');
        expect(bloecke).toHaveLength(DEFAULT_ITEMS.length);
        expect(new Set(bloecke.map(b => b.id)).size).toBe(DEFAULT_ITEMS.length);
    });

    it('leere Seiten bleiben leer', () => {
        expect(leseBloecke('<div class="page"></div><div class="page"></div>')).toEqual([]);
    });

    it('füllt fehlende Maße mit Standardgrößen; ohne (lesbaren) Stil bleibt der Stil leer', () => {
        const [b] = leseBloecke('<div data-block-type="datum" data-style="%7Bkaputt"></div>');
        expect(b).toMatchObject({ type: 'datum', page: 1, x: 32, y: 32, z: 1, width: 120, height: 48 });
        expect(b.styles).toBeUndefined();
        const [c] = leseBloecke('<div data-block-type="unbekannt"></div>');
        expect(c).toMatchObject({ width: 200, height: 80 });
    });

    it('stürzt bei kaputt kodiertem Inhalt nicht ab und behält den Rohwert', () => {
        const [b] = leseBloecke('<div data-block-type="text" data-content="100%25 sicher %E0%A4%A"></div>');
        expect(b.content).toBe('100%25 sicher %E0%A4%A');
    });

    it('führt keine Skripte aus dem gespeicherten HTML aus', () => {
        const bloecke = leseBloecke('<img src=x onerror="window.__boese=1"><div data-block-type="text" data-content="%3Cscript%3E"></div>');
        expect((window as unknown as { __boese?: number }).__boese).toBeUndefined();
        expect(bloecke[0].content).toBe('<script>');
    });

    it('standardBloecke vergibt frische IDs', () => {
        expect(standardBloecke()[0].id).not.toBe(standardBloecke()[0].id);
    });
});
