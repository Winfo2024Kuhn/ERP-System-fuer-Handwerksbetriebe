/**
 * Tests fuer die gemeinsamen HTML-Bausteine der Mail-Editoren.
 * Schwerpunkt: nichts Ungeprueftes landet im angezeigten Editor oder im Versand.
 *
 * DSGVO: ausschliesslich Dummy-Daten.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
    formuliereMitKi,
    hatZitiertenVerlauf,
    KEIN_VERLAUF_MELDUNG,
    htmlFuerVersand,
    ladeStandardSignatur,
    setzeEditorHtml,
    signaturAlsEditorHtml,
    zerlegeEditorHtml,
} from './mailEditorHtml';

const BOESE = '<img src="x" onerror="alert(1)"><script>alert(2)</script>';
const SIGNATUR = '<div class="email-signature"><p>Max Mustermann</p></div>';

let fetchMock: ReturnType<typeof vi.fn>;

function antwort(daten: unknown, status = 200) {
    return Promise.resolve(new Response(status === 204 ? null : JSON.stringify(daten), { status }));
}

beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe('signaturAlsEditorHtml', () => {
    it('bereinigt und rahmt eine Signatur ein', () => {
        const html = signaturAlsEditorHtml(`<p>Max Mustermann</p>${BOESE}`);
        expect(html).toMatch(/^<div class="email-signature" style="[^"]+">/);
        expect(html).toContain('<p>Max Mustermann</p>');
        expect(html).not.toMatch(/onerror|<script/);
    });

    it('rahmt eine schon eingerahmte Signatur nicht doppelt ein', () => {
        expect(signaturAlsEditorHtml(SIGNATUR)).toBe(SIGNATUR);
    });

    it('behaelt eingebettete Logos (data:image)', () => {
        const logo = '<img src="data:image/png;base64,AAAA" alt="Logo">';
        expect(signaturAlsEditorHtml(logo)).toContain('src="data:image/png;base64,AAAA"');
    });

    it('liefert bei leerer Signatur einen leeren String', () => {
        expect(signaturAlsEditorHtml('   ')).toBe('');
        expect(signaturAlsEditorHtml(null)).toBe('');
    });
});

describe('ladeStandardSignatur', () => {
    it('fragt mit Benutzer-ID an und liefert die bereinigte Signatur', async () => {
        fetchMock.mockReturnValue(antwort({ html: `<p>Max Mustermann</p>${BOESE}` }));
        const sig = await ladeStandardSignatur(7);
        expect(fetchMock).toHaveBeenCalledWith('/api/email/signatures/default?frontendUserId=7');
        expect(sig).toContain('Max Mustermann');
        expect(sig).not.toMatch(/onerror|<script/);
    });

    it('fragt ohne Benutzer die allgemeine Signatur an', async () => {
        fetchMock.mockReturnValue(antwort({ html: '<p>Gruß</p>' }));
        await ladeStandardSignatur(null);
        expect(fetchMock).toHaveBeenCalledWith('/api/email/signatures/default');
    });

    it('liefert leer bei 204, Fehlerstatus oder Netzwerkfehler', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => {});
        fetchMock.mockReturnValueOnce(antwort(null, 204));
        expect(await ladeStandardSignatur(1)).toBe('');
        fetchMock.mockReturnValueOnce(antwort({}, 500));
        expect(await ladeStandardSignatur(1)).toBe('');
        fetchMock.mockRejectedValueOnce(new Error('offline'));
        expect(await ladeStandardSignatur(1)).toBe('');
    });
});

describe('setzeEditorHtml', () => {
    it('schreibt nur bereinigtes HTML in den Editor und liefert es zurueck', () => {
        const editor = document.createElement('div');
        const gesetzt = setzeEditorHtml(editor, `<p>Hallo</p>${BOESE}`);
        expect(gesetzt).toBe('<p>Hallo</p><img src="x">');
        expect(editor.innerHTML).toBe(gesetzt);
    });

    it('liefert ohne Editor trotzdem das bereinigte HTML', () => {
        expect(setzeEditorHtml(null, BOESE)).toBe('<img src="x">');
    });
});

describe('htmlFuerVersand', () => {
    it('entfernt Skripte, Handler, style-Bloecke und contenteditable', () => {
        const html = htmlFuerVersand(`<p contenteditable="true">Hallo</p><style>p{color:red}</style>${BOESE}<a href="javascript:alert(1)">Link</a>`);
        expect(html).not.toMatch(/onerror|<script|<style|contenteditable|javascript:/);
        expect(html).toContain('<p>Hallo</p>');
        expect(html).toContain('Link');
    });

    it('behaelt Tabellen mit Inline-Stil und eingebettete Bilder', () => {
        const tabelle = '<table style="border-collapse: collapse;"><tbody><tr><td style="padding: 8px;">8,0</td></tr></tbody></table>';
        const logo = '<img src="data:image/png;base64,AAAA">';
        expect(htmlFuerVersand(tabelle + logo)).toBe(tabelle + logo);
    });

    it('liefert bei leerer Eingabe einen leeren String', () => {
        expect(htmlFuerVersand(undefined)).toBe('');
    });

    it('macht nackte Adressen zu Links, ohne den Punkt am Satzende', () => {
        expect(htmlFuerVersand('<p>siehe https://onedrive.example.com/f/c/abc?e=x.</p>'))
            .toBe('<p>siehe <a href="https://onedrive.example.com/f/c/abc?e=x">https://onedrive.example.com/f/c/abc?e=x</a>.</p>');
    });

    it('verlinkt vorhandene Links und Signatur-Links nicht doppelt', () => {
        const html = htmlFuerVersand(
            '<p><a href="https://onedrive.example.com/f/c/abc">Hier klicken</a></p>'
            + '<div class="email-signature"><a href="mailto:max@example.com">max@example.com</a> · www.example.com</div>',
        );
        expect(html).toBe(
            '<p><a href="https://onedrive.example.com/f/c/abc">Hier klicken</a></p>'
            + '<div class="email-signature"><a href="mailto:max@example.com">max@example.com</a> · <a href="https://www.example.com">www.example.com</a></div>',
        );
    });

    it('verlinkt keine gefährlichen Adressen und lässt maskiertes HTML Text', () => {
        const html = htmlFuerVersand('<p>javascript:alert(1) &lt;img src=x onerror=alert(1)&gt;</p>');
        expect(html).toBe('<p>javascript:alert(1) &lt;img src=x onerror=alert(1)&gt;</p>');
    });
});

describe('zerlegeEditorHtml', () => {
    it('trennt eigenen Text als HTML, Signatur und Zitat', () => {
        const teile = zerlegeEditorHtml(
            `<p>Sehr geehrte Damen und Herren,</p><p><br></p><p>bitte um <b>Angebot</b>.<br>Danke</p>${SIGNATUR}<div class="email-quote">Alte Nachricht</div>`,
        );
        expect(teile.eigenerHtml).toBe('<p>Sehr geehrte Damen und Herren,</p><p><br></p><p>bitte um <b>Angebot</b>.<br>Danke</p>');
        expect(teile.signaturHtml).toBe(SIGNATUR);
        expect(teile.zitatHtml).toBe('<div class="email-quote">Alte Nachricht</div>');
        expect(teile.zitatText).toBe('Alte Nachricht');
    });

    it('behält Tabellen im eigenen HTML, leere Zeilen vor Signatur und Zitat fallen weg', () => {
        const tabelle = '<table><tbody><tr><td>Träger</td><td>2 Stk</td></tr></tbody></table>';
        expect(zerlegeEditorHtml(`<p>Positionen:</p>${tabelle}<p><br></p>\n${SIGNATUR}<br><div class="email-quote">Alt</div>`).eigenerHtml)
            .toBe(`<p>Positionen:</p>${tabelle}`);
    });

    it('erkennt Zitate am Rand-Stil, wenn die Klasse fehlt', () => {
        const teile = zerlegeEditorHtml('<p>Text</p><div style="border-left: 3px solid #ccc">Zitat</div>');
        expect(teile.zitatText).toBe('Zitat');
        expect(teile.eigenerHtml).toBe('<p>Text</p>');
    });
});

describe('formuliereMitKi', () => {
    it('meldet fehlenden eigenen Text, ohne die KI zu fragen', async () => {
        expect(await formuliereMitKi(SIGNATUR)).toEqual({ status: 'kein-text' });
        expect(await formuliereMitKi(`<p><br></p><p>&nbsp;</p>${SIGNATUR}`)).toEqual({ status: 'kein-text' });
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it('schickt den eigenen Text als HTML mit Kontext, setzt Vorschlag, Signatur und Zitat zusammen', async () => {
        fetchMock.mockReturnValue(antwort({ body: '<p>Guten Tag,</p><p>wir bitten um ein Angebot.</p>' }));
        const ergebnis = await formuliereMitKi(
            `<p>hallo</p><p><b>angebot</b> bitte</p>${SIGNATUR}<div class="email-quote">Alte Nachricht</div>`,
            { kontext: 'Bestellung' },
        );

        const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
        expect(url).toBe('/api/email/beautify');
        expect(JSON.parse(String(init.body))).toEqual({ body: '<p>hallo</p><p><b>angebot</b> bitte</p>', context: 'Alte Nachricht' });
        expect(ergebnis).toEqual({
            status: 'ok',
            html: `<p>Guten Tag,</p><p>wir bitten um ein Angebot.</p>${SIGNATUR}<br><div class="email-quote">Alte Nachricht</div>`,
        });
    });

    it('nutzt ohne Zitat den Kontext und ohne Signatur im Editor die Ersatz-Signatur', async () => {
        fetchMock.mockReturnValue(antwort({ body: '<p>Neu.</p>' }));
        const ergebnis = await formuliereMitKi('<p>alt</p>', { kontext: 'Bestellung', ersatzSignaturHtml: SIGNATUR });
        expect(JSON.parse(String((fetchMock.mock.calls[0][1] as RequestInit).body)).context).toBe('Bestellung');
        expect(ergebnis).toEqual({ status: 'ok', html: `<p>Neu.</p>${SIGNATUR}` });
    });

    it('uebernimmt HTML-Vorschlaege nur bereinigt', async () => {
        fetchMock.mockReturnValue(antwort({ body: `<p>Fertig.</p>${BOESE}` }));
        const ergebnis = await formuliereMitKi('<p>alt</p>');
        expect(ergebnis).toEqual({ status: 'ok', html: '<p>Fertig.</p><img src="x">' });
    });

    it('behält Formatierung und Tabelle der Antwort', async () => {
        const tabelle = '<table><tbody><tr><td style="border:1px solid #cbd5e1">Träger</td></tr></tbody></table>';
        fetchMock.mockReturnValue(antwort({ body: `<p>Bitte <strong>bis Freitag</strong>, Preis &lt; 100 €.</p>${tabelle}` }));
        const ergebnis = await formuliereMitKi(`<p>bitte bis freitag</p>${tabelle}`);
        expect(JSON.parse(String((fetchMock.mock.calls[0][1] as RequestInit).body)).body).toBe(`<p>bitte bis freitag</p>${tabelle}`);
        expect(ergebnis).toEqual({ status: 'ok', html: `<p>Bitte <strong>bis Freitag</strong>, Preis &lt; 100 €.</p>${tabelle}` });
    });

    it('meldet einen leeren Vorschlag', async () => {
        fetchMock.mockReturnValue(antwort({ body: '   ' }));
        expect(await formuliereMitKi('<p>alt</p>')).toEqual({ status: 'kein-vorschlag' });
    });

    it('wirft bei einem Fehler der KI', async () => {
        fetchMock.mockReturnValue(antwort({}, 502));
        await expect(formuliereMitKi('<p>alt</p>')).rejects.toThrow('HTTP 502');
    });

    it('schickt den gewaehlten Stil mit, ohne Stil bleibt das Feld weg', async () => {
        fetchMock.mockImplementation(() => antwort({ body: 'Neu.' }));
        await formuliereMitKi('<p>alt</p>', { kontext: 'Projekt', stil: 'LOCKER' });
        expect(JSON.parse(String((fetchMock.mock.calls[0][1] as RequestInit).body)))
            .toEqual({ body: '<p>alt</p>', context: 'Projekt', stil: 'LOCKER' });
        await formuliereMitKi('<p>alt</p>');
        expect(JSON.parse(String((fetchMock.mock.calls[1][1] as RequestInit).body))).not.toHaveProperty('stil');
    });

    it('schickt bei VERLAUF nur die zitierte Mail als Kontext', async () => {
        fetchMock.mockReturnValue(antwort({ body: 'Passt, bis Donnerstag.' }));
        await formuliereMitKi('<p>ja passt</p><div class="email-quote">Hi, passt Donnerstag?</div>',
            { kontext: 'Projekt/Anfrage: Carport', stil: 'VERLAUF' });
        expect(JSON.parse(String((fetchMock.mock.calls[0][1] as RequestInit).body)))
            .toEqual({ body: '<p>ja passt</p>', context: 'Hi, passt Donnerstag?', stil: 'VERLAUF' });
    });

    it('fragt bei VERLAUF ohne Zitat gar nicht erst, auch nicht mit Projekt-Kontext', async () => {
        expect(await formuliereMitKi('<p>alt</p><div class="email-quote">  </div>', { kontext: 'Projekt', stil: 'VERLAUF' }))
            .toEqual({ status: 'abgelehnt', meldung: KEIN_VERLAUF_MELDUNG });
        expect(KEIN_VERLAUF_MELDUNG).toBe('Kein Mail-Verlauf vorhanden. Bitte einen anderen Stil wählen.');
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it('gibt die Meldung einer begruendeten Ablehnung weiter', async () => {
        fetchMock.mockReturnValue(antwort({ message: 'Unbekannter Schreibstil.' }, 400));
        expect(await formuliereMitKi('<p>alt</p>', { stil: 'FOERMLICH' }))
            .toEqual({ status: 'abgelehnt', meldung: 'Unbekannter Schreibstil.' });
    });

    it('wirft bei 400 ohne brauchbare Meldung', async () => {
        fetchMock.mockReturnValue(antwort({ message: '  ' }, 400));
        await expect(formuliereMitKi('<p>alt</p>')).rejects.toThrow('HTTP 400');
        fetchMock.mockReturnValue(Promise.resolve(new Response('kein json', { status: 400 })));
        await expect(formuliereMitKi('<p>alt</p>')).rejects.toThrow('HTTP 400');
    });
});

describe('hatZitiertenVerlauf', () => {
    it('erkennt eine zitierte Mail mit Text', () => {
        expect(hatZitiertenVerlauf(`<p>Text</p>${SIGNATUR}<div class="email-quote">Alte Nachricht</div>`)).toBe(true);
        expect(hatZitiertenVerlauf(`<p>Text</p>${SIGNATUR}`)).toBe(false);
        expect(hatZitiertenVerlauf('<p>Text</p><div class="email-quote"> </div>')).toBe(false);
    });
});
