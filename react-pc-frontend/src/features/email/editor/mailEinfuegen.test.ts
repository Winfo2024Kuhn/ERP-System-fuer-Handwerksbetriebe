/**
 * Tests für das Vorbereiten eingefügter Inhalte im Mail-Textfeld: Bilder werden
 * Anhänge, Formatierung und Tabellen bleiben, Gefährliches fliegt raus.
 * DSGVO: nur Dummy-Daten.
 */
import { describe, expect, it } from 'vitest';
import {
    KEINE_DATEIEN_MELDUNG,
    anhangMeldung,
    benenneDateien,
    bereiteEinfuegenVor,
    dataUrlAlsDatei,
    hatSichtbarenInhalt,
    nichtUebernommenMeldungen,
} from './mailEinfuegen';

/** 1×1-PNG. */
const PNG = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';

function zaehler(start = 1) {
    let nummer = start;
    return () => nummer++;
}

describe('dataUrlAlsDatei', () => {
    it('macht aus einem Base64-Bild eine Datei mit Typ und Endung', async () => {
        const datei = dataUrlAlsDatei(PNG, 'Bild-1')!;
        expect(datei.name).toBe('Bild-1.png');
        expect(datei.type).toBe('image/png');
        expect(datei.size).toBe(70);
        const kopf = new Uint8Array(await datei.arrayBuffer()).slice(1, 4);
        expect(String.fromCharCode(...kopf)).toBe('PNG');
    });

    it('nimmt jpg, svg und nicht kodierte Bilder an', () => {
        expect(dataUrlAlsDatei('data:image/jpeg;base64,/9j/4A==', 'Bild-2')?.name).toBe('Bild-2.jpg');
        const svg = dataUrlAlsDatei('data:image/svg+xml,%3Csvg%2F%3E', 'Bild-3')!;
        expect(svg.name).toBe('Bild-3.svg');
        expect(svg.size).toBe(6);
    });

    it('lehnt kaputte und fremde Inhalte ab', () => {
        expect(dataUrlAlsDatei('data:text/html;base64,PHNjcmlwdD4=', 'x')).toBeNull();
        expect(dataUrlAlsDatei('data:image/png;base64,%%%', 'x')).toBeNull();
        expect(dataUrlAlsDatei('data:image/png;base64,', 'x')).toBeNull();
        expect(dataUrlAlsDatei('data:image/png', 'x')).toBeNull();
        expect(dataUrlAlsDatei('https://example.com/a.png', 'x')).toBeNull();
    });
});

describe('bereiteEinfuegenVor', () => {
    it('macht eingebettete Bilder zu Anhängen und nimmt sie aus dem Text', () => {
        const ergebnis = bereiteEinfuegenVor(`<p>Foto vom Geländer:</p><p><img src="${PNG}" alt="Geländer"></p>`, { vergibNummer: zaehler(4) });
        expect(ergebnis.html).toBe('<p>Foto vom Geländer:</p><p></p>');
        expect(ergebnis.dateien.map(d => d.name)).toEqual(['Bild-4.png']);
        expect(ergebnis.nichtUebernommen).toEqual({ internet: 0, unlesbar: 0 });
    });

    it('entfernt Bilder aus dem Internet und zählt sie', () => {
        const ergebnis = bereiteEinfuegenVor(
            '<p>Katalog <img src="https://example.com/a.png"> und <img src="http://example.com/b.jpg"></p>',
            { vergibNummer: zaehler() },
        );
        expect(ergebnis.html).toBe('<p>Katalog  und </p>');
        expect(ergebnis.dateien).toEqual([]);
        expect(ergebnis.nichtUebernommen).toEqual({ internet: 2, unlesbar: 0 });
    });

    it('zählt nicht lesbare Bilder (blob, file, cid, kaputt) und entfernt sie', () => {
        const ergebnis = bereiteEinfuegenVor(
            '<p>a<img src="blob:https://example.com/1">b<img src="file:///C:/Bild.png">c<img src="cid:bild1">'
            + '<img src="data:image/png;base64,%%%"><img></p>',
            { vergibNummer: zaehler() },
        );
        expect(ergebnis.html).toBe('<p>abc</p>');
        expect(ergebnis.nichtUebernommen).toEqual({ internet: 0, unlesbar: 4 });
    });

    it('verrechnet Bilder, die als Datei mitkamen, ohne Hinweis und ohne doppelten Anhang', () => {
        const ergebnis = bereiteEinfuegenVor(
            `<img src="https://example.com/a.png"><img src="file:///C:/b.png"><img src="${PNG}"><p>Text</p>`,
            { vergibNummer: zaehler(), gedeckteBilder: 3 },
        );
        expect(ergebnis.nichtUebernommen).toEqual({ internet: 0, unlesbar: 0 });
        expect(ergebnis.dateien).toEqual([]);
        expect(ergebnis.html).toBe('<p>Text</p>');
    });

    it('nimmt Hintergrundbilder und Bild-Knöpfe heraus und zählt sie als Bilder', () => {
        const ergebnis = bereiteEinfuegenVor(
            '<table background="https://example.com/a.png"><tbody><tr><td background="cid:x">Zelle</td></tr></tbody></table>'
            + `<input type="image" src="https://example.com/b.png"><input type="image" src="${PNG}">`
            + '<p>Text</p>',
            { vergibNummer: zaehler() },
        );
        expect(ergebnis.html).toBe('<table><tbody><tr><td>Zelle</td></tr></tbody></table><p>Text</p>');
        expect(ergebnis.nichtUebernommen).toEqual({ internet: 2, unlesbar: 1 });
        expect(ergebnis.dateien.map(d => d.name)).toEqual(['Bild-1.png']);
    });

    it('wirft Formularfelder und Medien raus, ohne sie als Bilder zu zählen', () => {
        const ergebnis = bereiteEinfuegenVor(
            '<p>vor</p><input type="text" value="x"><button>Kaufen</button><select><option>A</option></select>'
            + '<textarea>frei</textarea><video poster="https://example.com/v.png"></video>'
            + '<picture><source srcset="https://example.com/s.png"></picture><object data="https://example.com/o"></object>'
            + '<embed src="https://example.com/e"><iframe src="https://example.com/f"></iframe>'
            + '<form action="https://example.com/x"><p>im Formular</p></form>',
            { vergibNummer: zaehler() },
        );
        expect(ergebnis.html).toBe('<p>vor</p><p>im Formular</p>');
        expect(ergebnis.nichtUebernommen).toEqual({ internet: 0, unlesbar: 0 });
    });

    it('verwirft Stile, die etwas nachladen oder sich tarnen', () => {
        const ergebnis = bereiteEinfuegenVor(
            '<p style="color:#dc2626;background:u\\72l(https://example.com/a.png);font-weight:700">a</p>'
            + '<p style="background-image:image-set(\'https://example.com/b.png\' 1x)">b</p>'
            + '<p style="background-image:-webkit-image-set(url(https://example.com/c.png) 1x)">c</p>'
            + '<p style="background:image(https://example.com/d.png);list-style:cross-fade(url(x), url(y))">d</p>'
            + '<p style="b\\61 ckground:red;color:#16a34a">e</p>',
            { vergibNummer: zaehler() },
        );
        expect(ergebnis.html).toBe('<p style="color:#dc2626;font-weight:700">a</p><p>b</p><p>c</p><p>d</p><p style="color:#16a34a">e</p>');
    });

    it('wirft Skripte und Ereignis-Handler raus', () => {
        const ergebnis = bereiteEinfuegenVor(
            '<p onclick="alert(1)">Hallo<img src=x onerror=alert(1)><script>alert(2)</script>'
            + '<a href="javascript:alert(3)">Link</a></p>',
            { vergibNummer: zaehler() },
        );
        expect(ergebnis.html).toBe('<p>Hallo<a>Link</a></p>');
        expect(ergebnis.nichtUebernommen.unlesbar).toBe(1);
    });

    it('behält Fett, Kursiv, Listen, Farben, Größen und Links', () => {
        const html = '<p><b>fett</b> <i>kursiv</i> <span style="color:#dc2626;font-size:18px">rot</span> '
            + '<a href="https://example.com">Link</a></p><ul><li>eins</li></ul>';
        expect(bereiteEinfuegenVor(html, { vergibNummer: zaehler() }).html).toBe(html);
    });

    it('räumt Word-HTML auf und lässt die Tabelle samt Rahmen stehen', () => {
        const word = `<html xmlns:o="urn:schemas-microsoft-com:office:office"><head><meta name=Generator content="Microsoft Word 15">
<style><!-- p.MsoNormal, li.MsoNormal {margin:0cm;font-size:11.0pt;mso-fareast-font-family:Calibri;} --></style></head>
<body lang=DE><!--StartFragment--><p class=MsoNormal>Positionen:<o:p></o:p></p>
<!--[if gte vml 1]><v:shape><v:imagedata src="file:///C:/x.png"/></v:shape><![endif]-->
<table class=MsoTableGrid border=1 cellspacing=0 cellpadding=0 style='border-collapse:collapse;border:none;mso-yfti-tbllook:1184'>
<tr><td width=302 valign=top style='border:solid windowtext 1.0pt;mso-border-alt:solid windowtext .5pt;padding:0cm 5.4pt 0cm 5.4pt'>
<p class=MsoNormal><b>Träger<o:p></o:p></b></p></td>
<td style='border:solid windowtext 1.0pt;border-left:none;padding:0cm 5.4pt'><p class=MsoNormal>2 Stk<o:p>&nbsp;</o:p></p></td></tr>
</table><!--EndFragment--></body></html>`;
        const ergebnis = bereiteEinfuegenVor(word, { vergibNummer: zaehler() });
        const wrapper = document.createElement('template');
        wrapper.innerHTML = ergebnis.html;
        const inhalt = wrapper.content;

        expect(ergebnis.html).not.toMatch(/mso-|Mso|o:p|<!--|<style|<meta|v:shape|lang=/i);
        const tabelle = inhalt.querySelector('table')!;
        expect(tabelle.getAttribute('style')).toBe('border-collapse:collapse;border:none');
        const zellen = inhalt.querySelectorAll('td');
        expect(zellen).toHaveLength(2);
        expect(zellen[0].getAttribute('style')).toBe('border:solid #000000 1.0pt;padding:0cm 5.4pt 0cm 5.4pt');
        expect(zellen[0].querySelector('b')?.textContent).toBe('Träger');
        expect(zellen[1].textContent?.trim()).toBe('2 Stk');
        // Die Absatz-Vorlage aus dem <style> steht jetzt am Absatz selbst.
        expect(inhalt.querySelector('p')?.getAttribute('style')).toBe('margin:0cm;font-size:11.0pt');
        expect(ergebnis.nichtUebernommen).toEqual({ internet: 0, unlesbar: 0 });
    });

    it('überträgt Excel-Rahmen aus Klassen an die Zellen', () => {
        const excel = `<style><!--table {mso-displayed-decimal-separator:"\\,";}
.xl65 {mso-style-parent:style0; border:.5pt solid windowtext; background:url(https://example.com/x.png);}
td.xl66 {font-weight:700;}
@page {margin:1cm;}--></style>
<table style='border-collapse:collapse;width:96pt'><tr><td class=xl65 style='height:15pt'>Menge</td><td class="xl66 xl65">12</td></tr></table>`;
        const { html } = bereiteEinfuegenVor(excel, { vergibNummer: zaehler() });
        const wrapper = document.createElement('template');
        wrapper.innerHTML = html;
        const zellen = wrapper.content.querySelectorAll('td');
        expect(zellen[0].getAttribute('style')).toBe('border:.5pt solid #000000;height:15pt');
        expect(zellen[1].getAttribute('style')).toBe('border:.5pt solid #000000;font-weight:700');
        expect(html).not.toMatch(/class=|url\(/);
    });
});

describe('hatSichtbarenInhalt', () => {
    it('erkennt Text und Tabellen, nicht aber leere Hüllen', () => {
        expect(hatSichtbarenInhalt('<p>Hallo</p>')).toBe(true);
        expect(hatSichtbarenInhalt('<table><tr><td></td></tr></table>')).toBe(true);
        expect(hatSichtbarenInhalt('<p>&nbsp;</p><meta charset="utf-8"><br>')).toBe(false);
        expect(hatSichtbarenInhalt('')).toBe(false);
    });
});

describe('benenneDateien', () => {
    it('gibt Zwischenablage-Bildern einen eigenen Namen, echte Dateinamen bleiben', () => {
        const nummer = zaehler(2);
        const [bild, ohneName, pdf] = benenneDateien([
            new File(['a'], 'image.png', { type: 'image/png' }),
            new File(['b'], '', { type: 'image/jpeg' }),
            new File(['c'], 'Angebot Mustermann.pdf', { type: 'application/pdf' }),
        ], nummer);
        expect(bild.name).toBe('Bild-2.png');
        expect(bild.type).toBe('image/png');
        expect(ohneName.name).toBe('Bild-3.jpg');
        expect(pdf.name).toBe('Angebot Mustermann.pdf');
    });
});

describe('Meldungen', () => {
    const bild = new File(['a'], 'Bild-1.png', { type: 'image/png' });
    const pdf = new File(['a'], 'Plan.pdf', { type: 'application/pdf' });

    it('meldet, was als Anhang dazukam', () => {
        expect(anhangMeldung([bild])).toBe('Bild als Anhang hinzugefügt.');
        expect(anhangMeldung([pdf])).toBe('Datei als Anhang hinzugefügt.');
        expect(anhangMeldung([bild, pdf, bild])).toBe('3 Dateien als Anhang hinzugefügt.');
    });

    it('meldet nicht übernommene Bilder in Einzahl und Mehrzahl', () => {
        expect(nichtUebernommenMeldungen({ internet: 0, unlesbar: 0 })).toEqual([]);
        expect(nichtUebernommenMeldungen({ internet: 1, unlesbar: 1 })).toEqual([
            '1 Bild aus dem Internet wurde nicht übernommen. Bitte als Datei anhängen.',
            '1 Bild konnte nicht übernommen werden. Bitte als Datei anhängen.',
        ]);
        expect(nichtUebernommenMeldungen({ internet: 2, unlesbar: 3 })).toEqual([
            '2 Bilder aus dem Internet wurden nicht übernommen. Bitte als Datei anhängen.',
            '3 Bilder konnten nicht übernommen werden. Bitte als Datei anhängen.',
        ]);
        expect(KEINE_DATEIEN_MELDUNG).toBe('In dieses Feld lassen sich keine Bilder oder Dateien einfügen.');
    });
});
