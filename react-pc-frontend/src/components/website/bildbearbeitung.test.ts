import { describe, it, expect, vi } from 'vitest';
import {
    STANDARD_BEARBEITUNG,
    berechneAusgabeMasse,
    zeichne,
    type Bildbearbeitung,
} from './bildbearbeitung';

const quelle = { breite: 4000, hoehe: 3000 };

describe('berechneAusgabeMasse', () => {
    it('verkleinert auf die Hoechstbreite und behaelt das Verhaeltnis', () => {
        expect(berechneAusgabeMasse(quelle, STANDARD_BEARBEITUNG, 1600))
            .toEqual({ breite: 1600, hoehe: 1200 });
    });

    it('vergroessert kleine Bilder nicht', () => {
        expect(berechneAusgabeMasse({ breite: 800, hoehe: 600 }, STANDARD_BEARBEITUNG, 1600))
            .toEqual({ breite: 800, hoehe: 600 });
    });

    it('rechnet mit dem Zuschnitt statt mit dem ganzen Bild', () => {
        const mitZuschnitt: Bildbearbeitung = {
            ...STANDARD_BEARBEITUNG,
            zuschnitt: { x: 0, y: 0, breite: 2000, hoehe: 1000 },
        };

        expect(berechneAusgabeMasse(quelle, mitZuschnitt, 1600))
            .toEqual({ breite: 1600, hoehe: 800 });
    });

    it('tauscht Breite und Hoehe bei 90 Grad', () => {
        const gedreht: Bildbearbeitung = { ...STANDARD_BEARBEITUNG, drehung: 90 };

        expect(berechneAusgabeMasse(quelle, gedreht, 1600))
            .toEqual({ breite: 1200, hoehe: 1600 });
    });

    it('tauscht bei 270 Grad ebenso', () => {
        const gedreht: Bildbearbeitung = { ...STANDARD_BEARBEITUNG, drehung: 270 };

        expect(berechneAusgabeMasse(quelle, gedreht, 1600).breite).toBe(1200);
    });

    it('laesst bei 90 Grad die Ausgabebreite bewusst ueber maxBreite, nur die Pixelzahl bleibt gleich', () => {
        // Nachbedingung aus dem Kommentar an der Funktion: maxBreite begrenzt
        // nur die Breite vor der Drehung. Nach dem Tausch bei 90/270 Grad
        // kann die Ausgabebreite darueber liegen. Das ist gewollt, damit der
        // Verkleinerungsfaktor nicht von der Drehung abhaengt.
        const hochformat = { breite: 1600, hoehe: 4000 };
        const gedreht: Bildbearbeitung = { ...STANDARD_BEARBEITUNG, drehung: 90 };

        expect(berechneAusgabeMasse(hochformat, gedreht, 1024))
            .toEqual({ breite: 2560, hoehe: 1024 });
    });

    it('laesst 180 Grad die Masse unveraendert', () => {
        const gedreht: Bildbearbeitung = { ...STANDARD_BEARBEITUNG, drehung: 180 };

        expect(berechneAusgabeMasse(quelle, gedreht, 1600))
            .toEqual({ breite: 1600, hoehe: 1200 });
    });
});

/** Minimaler Ersatz fuer den Canvas-Kontext, der nur mitschreibt. */
function fakeKontext() {
    return {
        canvas: { width: 0, height: 0 },
        filter: '',
        save: vi.fn(),
        restore: vi.fn(),
        translate: vi.fn(),
        rotate: vi.fn(),
        scale: vi.fn(),
        clearRect: vi.fn(),
        drawImage: vi.fn(),
    };
}

describe('zeichne', () => {
    const bild = { width: 4000, height: 3000 } as unknown as HTMLImageElement;

    it('setzt Helligkeit und Kontrast als Filter', () => {
        const ctx = fakeKontext();

        zeichne(ctx as never, bild, { ...STANDARD_BEARBEITUNG, helligkeit: 120, kontrast: 90 }, 1600);

        expect(ctx.filter).toContain('brightness(120%)');
        expect(ctx.filter).toContain('contrast(90%)');
    });

    it('spiegelt waagerecht ueber eine negative Skalierung', () => {
        const ctx = fakeKontext();

        zeichne(ctx as never, bild, { ...STANDARD_BEARBEITUNG, spiegelnX: true }, 1600);

        expect(ctx.scale).toHaveBeenCalledWith(-1, 1);
    });

    it('dreht um den Bogenmass-Wert', () => {
        const ctx = fakeKontext();

        zeichne(ctx as never, bild, { ...STANDARD_BEARBEITUNG, drehung: 90 }, 1600);

        expect(ctx.rotate).toHaveBeenCalledWith(Math.PI / 2);
    });

    it('zeichnet nur den Zuschnitt', () => {
        const ctx = fakeKontext();
        const zuschnitt = { x: 100, y: 200, breite: 1000, hoehe: 500 };

        zeichne(ctx as never, bild, { ...STANDARD_BEARBEITUNG, zuschnitt }, 1600);

        const args = ctx.drawImage.mock.calls[0];
        expect(args[1]).toBe(100);
        expect(args[2]).toBe(200);
        expect(args[3]).toBe(1000);
        expect(args[4]).toBe(500);
    });

    it('setzt die Canvas-Groesse auf die Ausgabemasse', () => {
        const ctx = fakeKontext();

        zeichne(ctx as never, bild, STANDARD_BEARBEITUNG, 1600);

        expect(ctx.canvas.width).toBe(1600);
        expect(ctx.canvas.height).toBe(1200);
    });
});
