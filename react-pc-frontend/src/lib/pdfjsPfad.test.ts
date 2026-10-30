import { describe, it, expect } from 'vitest';
import { istPdfjsHilfsdatei, pdfjsHilfsdateiAusUrl, pdfjsHilfsordner } from './pdfjsPfad';

const V = '6.4.299';

describe('pdfjsHilfsordner', () => {
    it('legt die Hilfsdateien je Version in einen eigenen Ordner – alte Cache-Stände greifen nicht', () => {
        expect(pdfjsHilfsordner('7.0.1')).toBe('/assets/pdfjs-7.0.1/');
        expect(pdfjsHilfsordner('7.0.1')).not.toBe(pdfjsHilfsordner(V));
    });
});

describe('istPdfjsHilfsdatei', () => {
    it('liefert Decoder, Rückfall-Skripte und Lizenzen mit, aber nicht die PDF-Skriptumgebung', () => {
        expect(istPdfjsHilfsdatei('jbig2.wasm')).toBe(true);
        expect(istPdfjsHilfsdatei('jbig2_nowasm_fallback.js')).toBe(true);
        expect(istPdfjsHilfsdatei('LICENSE_JBIG2')).toBe(true);
        expect(istPdfjsHilfsdatei('quickjs-eval.wasm')).toBe(false);
        expect(istPdfjsHilfsdatei('quickjs-eval.js')).toBe(false);
    });
});

describe('pdfjsHilfsdateiAusUrl', () => {
    it('erkennt den Fax-Decoder, auch mit Abfrage-Teil', () => {
        expect(pdfjsHilfsdateiAusUrl(`/assets/pdfjs-${V}/wasm/jbig2.wasm`, V)).toEqual({ ordner: 'wasm', datei: 'jbig2.wasm' });
        expect(pdfjsHilfsdateiAusUrl(`/assets/pdfjs-${V}/cmaps/Adobe-Japan1-UCS2.bcmap?v=1`, V))
            .toEqual({ ordner: 'cmaps', datei: 'Adobe-Japan1-UCS2.bcmap' });
    });

    it('lässt fremde Pfade und andere Versionen durch an den normalen Dev-Server', () => {
        expect(pdfjsHilfsdateiAusUrl('/assets/index-abc.js', V)).toBeNull();
        expect(pdfjsHilfsdateiAusUrl('/assets/pdfjs-6.0.0/wasm/jbig2.wasm', V)).toBeNull();
    });

    it.each([
        ['Weg nach oben', `/assets/pdfjs-${V}/wasm/../../package.json`],
        ['Ordnername ..', `/assets/pdfjs-${V}/../package.json`],
        ['Datei ..', `/assets/pdfjs-${V}/wasm/..`],
        ['Datei .', `/assets/pdfjs-${V}/wasm/.`],
        ['Windows-Trenner', `/assets/pdfjs-${V}/wasm/..\\..\\package.json`],
        ['Laufwerksangabe', `/assets/pdfjs-${V}/wasm/C:secret`],
        ['unbekannter Ordner', `/assets/pdfjs-${V}/build/pdf.mjs`],
        ['Unterordner', `/assets/pdfjs-${V}/wasm/a/b.wasm`],
        ['ohne Datei', `/assets/pdfjs-${V}/wasm/`],
        ['PDF-Skriptumgebung', `/assets/pdfjs-${V}/wasm/quickjs-eval.wasm`],
    ])('weist %s ab', (_fall, url) => {
        expect(pdfjsHilfsdateiAusUrl(url, V)).toBeNull();
    });
});
