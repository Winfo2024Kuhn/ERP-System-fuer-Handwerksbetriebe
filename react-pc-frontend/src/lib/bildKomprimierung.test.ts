import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
    istKomprimierbaresBild,
    berechneZielmasse,
    jpegDateiname,
    komprimiereBildFuerEmail,
    komprimiereBilderFuerEmail,
    MAX_KANTE_PX,
    MINDESTGROESSE_BYTES,
} from './bildKomprimierung';

/** Baut eine Datei mit vorgegebener Größe, ohne dafür echte Bytes zu erzeugen. */
function datei(name: string, typ: string, groesse: number): File {
    const f = new File(['x'], name, { type: typ });
    Object.defineProperty(f, 'size', { value: groesse });
    return f;
}

const GROSSES_FOTO = 4 * 1024 * 1024;

describe('istKomprimierbaresBild', () => {
    it('nimmt große JPEGs, PNGs und WebPs', () => {
        expect(istKomprimierbaresBild(datei('foto.jpg', 'image/jpeg', GROSSES_FOTO))).toBe(true);
        expect(istKomprimierbaresBild(datei('plan.png', 'image/png', GROSSES_FOTO))).toBe(true);
        expect(istKomprimierbaresBild(datei('bild.webp', 'image/webp', GROSSES_FOTO))).toBe(true);
    });

    it('lässt kleine Bilder in Ruhe', () => {
        expect(istKomprimierbaresBild(datei('klein.jpg', 'image/jpeg', MINDESTGROESSE_BYTES - 1))).toBe(false);
    });

    it('lässt PDFs und andere Anhänge unangetastet', () => {
        expect(istKomprimierbaresBild(datei('lieferschein.pdf', 'application/pdf', GROSSES_FOTO))).toBe(false);
    });

    it('lässt GIF und SVG aus: Animation bzw. Vektor gingen verloren', () => {
        expect(istKomprimierbaresBild(datei('logo.gif', 'image/gif', GROSSES_FOTO))).toBe(false);
        expect(istKomprimierbaresBild(datei('logo.svg', 'image/svg+xml', GROSSES_FOTO))).toBe(false);
    });

    it('entscheidet über die Dateiendung, wenn der Browser keinen Typ liefert', () => {
        expect(istKomprimierbaresBild(datei('scan.JPEG', '', GROSSES_FOTO))).toBe(true);
        expect(istKomprimierbaresBild(datei('scan.pdf', '', GROSSES_FOTO))).toBe(false);
    });
});

describe('berechneZielmasse', () => {
    it('verkleinert ein Querformat auf die maximale Kante', () => {
        expect(berechneZielmasse(4000, 3000)).toEqual({ breite: MAX_KANTE_PX, hoehe: 1500 });
    });

    it('verkleinert ein Hochformat auf die maximale Kante', () => {
        expect(berechneZielmasse(3000, 4000)).toEqual({ breite: 1500, hoehe: MAX_KANTE_PX });
    });

    it('vergrößert kleinere Bilder nicht', () => {
        expect(berechneZielmasse(800, 600)).toEqual({ breite: 800, hoehe: 600 });
    });

    it('lässt bei extremem Seitenverhältnis mindestens einen Pixel stehen', () => {
        expect(berechneZielmasse(40000, 3).hoehe).toBe(1);
    });
});

describe('jpegDateiname', () => {
    it('tauscht die Endung gegen .jpg', () => {
        expect(jpegDateiname('foto.png')).toBe('foto.jpg');
        expect(jpegDateiname('Scan 2026-09-02.JPEG')).toBe('Scan 2026-09-02.jpg');
    });

    it('hängt .jpg an, wenn keine Endung da ist', () => {
        expect(jpegDateiname('foto')).toBe('foto.jpg');
    });

    it('erfindet einen Namen, wenn nur eine Endung da ist', () => {
        expect(jpegDateiname('.png')).toBe('bild.jpg');
    });
});

describe('komprimiereBildFuerEmail', () => {
    // jsdom kann weder Bilder dekodieren noch Canvas zeichnen. Beides wird durch
    // die Mindest-Attrappen ersetzt, die diese Funktion tatsächlich anfasst.
    let toBlobErgebnis: Blob | null;
    let bitmapGeschlossen: boolean;

    beforeEach(() => {
        toBlobErgebnis = new Blob([new Uint8Array(300 * 1024)], { type: 'image/jpeg' });
        bitmapGeschlossen = false;

        vi.stubGlobal('createImageBitmap', vi.fn(async () => ({
            width: 4000,
            height: 3000,
            close: () => { bitmapGeschlossen = true; },
        })));

        vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
            fillStyle: '',
            fillRect: vi.fn(),
            drawImage: vi.fn(),
        } as unknown as CanvasRenderingContext2D);

        HTMLCanvasElement.prototype.toBlob = function (rueckruf: BlobCallback) {
            rueckruf(toBlobErgebnis);
        };
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        vi.restoreAllMocks();
    });

    it('liefert ein kleineres JPEG zurück', async () => {
        const ergebnis = await komprimiereBildFuerEmail(datei('foto.jpg', 'image/jpeg', GROSSES_FOTO));

        expect(ergebnis.size).toBe(300 * 1024);
        expect(ergebnis.type).toBe('image/jpeg');
        expect(ergebnis.name).toBe('foto.jpg');
    });

    it('rechnet die EXIF-Drehung fest ins Bild, damit Handyfotos nicht quer liegen', async () => {
        await komprimiereBildFuerEmail(datei('foto.jpg', 'image/jpeg', GROSSES_FOTO));

        expect(createImageBitmap).toHaveBeenCalledWith(expect.anything(), { imageOrientation: 'from-image' });
    });

    it('gibt das Bitmap wieder frei', async () => {
        await komprimiereBildFuerEmail(datei('foto.jpg', 'image/jpeg', GROSSES_FOTO));

        expect(bitmapGeschlossen).toBe(true);
    });

    it('benennt ein neu kodiertes PNG in .jpg um', async () => {
        const ergebnis = await komprimiereBildFuerEmail(datei('plan.png', 'image/png', GROSSES_FOTO));

        expect(ergebnis.name).toBe('plan.jpg');
    });

    it('behält das Original, wenn die Komprimierung nichts spart', async () => {
        toBlobErgebnis = new Blob([new Uint8Array(64)], { type: 'image/jpeg' });
        const original = datei('winzig.jpg', 'image/jpeg', 32);

        expect(await komprimiereBildFuerEmail(original)).toBe(original);
    });

    it('behält das Original, wenn das Bild nicht dekodierbar ist', async () => {
        vi.stubGlobal('createImageBitmap', vi.fn(async () => { throw new Error('kaputt'); }));
        vi.spyOn(console, 'warn').mockImplementation(() => { });
        const original = datei('defekt.jpg', 'image/jpeg', GROSSES_FOTO);

        expect(await komprimiereBildFuerEmail(original)).toBe(original);
    });

    it('behält das Original, wenn der Browser keinen Canvas-Kontext gibt', async () => {
        vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);
        const original = datei('foto.jpg', 'image/jpeg', GROSSES_FOTO);

        expect(await komprimiereBildFuerEmail(original)).toBe(original);
    });

    it('reicht PDFs unverändert durch', async () => {
        const original = datei('lieferschein.pdf', 'application/pdf', GROSSES_FOTO);

        expect(await komprimiereBildFuerEmail(original)).toBe(original);
        expect(createImageBitmap).not.toHaveBeenCalled();
    });

    it('komprimiert eine Liste und lässt Nicht-Bilder darin unangetastet', async () => {
        const lieferschein = datei('lieferschein.pdf', 'application/pdf', GROSSES_FOTO);
        const ergebnis = await komprimiereBilderFuerEmail([
            datei('foto.jpg', 'image/jpeg', GROSSES_FOTO),
            lieferschein,
        ]);

        expect(ergebnis[0].size).toBe(300 * 1024);
        expect(ergebnis[1]).toBe(lieferschein);
    });
});
