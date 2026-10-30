import { readFileSync } from 'node:fs';
import { test, expect } from './hilfen/test';
import { pdfjsHilfsordner } from '../src/lib/pdfjsPfad';

/**
 * PDF.js liest Fax-Bilder (CCITT/JBIG2) nur über nachgeladene Decoder-Dateien. Fehlen sie im
 * Build, überspringt es das Bild still: Bei Fax-PDFs von Lieferanten blieb in der Vorschau nur
 * das leere Formular stehen. Diese Spec prüft, dass der ausgelieferte Build die Dateien unter
 * genau dem Pfad enthält, den die Vorschau PDF.js nennt.
 */
const version: string = JSON.parse(
    readFileSync(new URL('../node_modules/pdfjs-dist/package.json', import.meta.url), 'utf8'),
).version;
const ordner = pdfjsHilfsordner(version);

test.describe('PDF-Vorschau: Hilfsdateien von PDF.js', () => {
    test('liefert den Fax-Decoder als WebAssembly aus', async ({ request }) => {
        const antwort = await request.get(`${ordner}wasm/jbig2.wasm`);

        expect(antwort.status()).toBe(200);
        expect(antwort.headers()['content-type']).toContain('application/wasm');
        // WebAssembly-Dateien beginnen mit "\0asm".
        expect([...(await antwort.body()).subarray(0, 4)]).toEqual([0x00, 0x61, 0x73, 0x6d]);
    });

    test('liefert Farbprofil, Zeichentabellen und Standardschriften aus', async ({ request }) => {
        for (const datei of ['iccs/CGATS001Compat-v2-micro.icc', 'cmaps/78-H.bcmap', 'standard_fonts/FoxitFixed.pfb']) {
            const antwort = await request.get(`${ordner}${datei}`);
            expect(antwort.status(), datei).toBe(200);
            expect(antwort.headers()['content-type'], datei).not.toContain('text/html');
        }
    });

    test('liefert die PDF-Skriptumgebung nicht mit aus', async ({ request }) => {
        const antwort = await request.get(`${ordner}wasm/quickjs-eval.wasm`);

        expect(antwort.headers()['content-type'] ?? '').not.toContain('application/wasm');
    });
});
