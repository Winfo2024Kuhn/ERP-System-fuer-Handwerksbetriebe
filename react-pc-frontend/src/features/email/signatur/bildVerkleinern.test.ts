import { afterEach, describe, expect, it, vi } from 'vitest';
import {
    BILD_MAX_EINGABE_BYTES,
    BILD_ZIEL_BYTES,
    bildVerkleinern,
    formatBytes,
    pruefeBildDatei,
    zielGroesse,
} from './bildVerkleinern';

describe('pruefeBildDatei', () => {
    it('erlaubt PNG, JPG, GIF, WebP', () => {
        for (const type of ['image/png', 'image/jpeg', 'image/gif', 'image/webp']) {
            expect(pruefeBildDatei({ type, size: 1000 })).toBeNull();
        }
    });
    it('lehnt andere Typen, leere und zu große Dateien ab', () => {
        expect(pruefeBildDatei({ type: 'image/svg+xml', size: 1000 })).toMatch(/PNG, JPG, GIF oder WebP/);
        expect(pruefeBildDatei({ type: 'application/x-msdownload', size: 1000 })).toMatch(/PNG, JPG/);
        expect(pruefeBildDatei({ type: 'image/png', size: 0 })).toBe('Die Bilddatei ist leer.');
        expect(pruefeBildDatei({ type: 'image/png', size: BILD_MAX_EINGABE_BYTES + 1 })).toMatch(/zu groß/);
    });
});

describe('zielGroesse', () => {
    it('verkleinert proportional auf die Höchstbreite', () => {
        expect(zielGroesse(1200, 800, 300)).toEqual({ breite: 300, hoehe: 200 });
    });
    it('vergrößert nie', () => {
        expect(zielGroesse(100, 50, 300)).toEqual({ breite: 100, hoehe: 50 });
    });
    it('ungültige Maße ergeben 0', () => {
        expect(zielGroesse(0, 50, 300)).toEqual({ breite: 0, hoehe: 0 });
    });
});

describe('formatBytes', () => {
    it('KB und MB mit Komma', () => {
        expect(formatBytes(86_000)).toBe('84 KB');
        expect(formatBytes(10)).toBe('1 KB');
        expect(formatBytes(1.5 * 1024 * 1024)).toBe('1,5 MB');
    });
});

/** Canvas und Bild-Laden gibt es in jsdom nicht – beides wird hier nachgestellt. */
function stelleBrowserNach(optionen: { breite: number; hoehe: number; alpha?: number; groessen?: number[]; ladeFehler?: boolean }) {
    const groessen = [...(optionen.groessen ?? [10_000])];
    const aufrufe: { typ: string; breite: number }[] = [];
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:test'), revokeObjectURL: vi.fn() }));
    class TestBild {
        onload: (() => void) | null = null;
        onerror: (() => void) | null = null;
        naturalWidth = optionen.breite;
        naturalHeight = optionen.hoehe;
        width = optionen.breite;
        height = optionen.hoehe;
        set src(_wert: string) {
            setTimeout(() => (optionen.ladeFehler ? this.onerror?.() : this.onload?.()), 0);
        }
    }
    vi.stubGlobal('Image', TestBild);
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockImplementation(function (this: HTMLCanvasElement) {
        return {
            fillStyle: '', imageSmoothingEnabled: true, imageSmoothingQuality: 'high',
            fillRect: vi.fn(), drawImage: vi.fn(),
            getImageData: () => ({ data: new Uint8ClampedArray([0, 0, 0, optionen.alpha ?? 255, 0, 0, 0, 255]) }),
            canvas: this,
        } as unknown as CanvasRenderingContext2D;
    } as typeof HTMLCanvasElement.prototype.getContext);
    vi.spyOn(HTMLCanvasElement.prototype, 'toDataURL').mockImplementation(function (this: HTMLCanvasElement, typ?: string) {
        aufrufe.push({ typ: typ ?? '', breite: this.width });
        const bytes = groessen.length > 1 ? groessen.shift()! : groessen[0];
        const base64 = 'A'.repeat(Math.ceil(bytes / 3) * 4);
        return `data:${typ ?? 'image/png'};base64,${base64}`;
    });
    return aufrufe;
}

const datei = (type: string, size = 5000) => new File([new Uint8Array(size)], 'bild', { type });

describe('bildVerkleinern', () => {
    afterEach(() => {
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
    });

    it('Foto ohne Transparenz: JPEG mit 300 px Breite', async () => {
        const aufrufe = stelleBrowserNach({ breite: 1200, hoehe: 1200 });
        const ergebnis = await bildVerkleinern(datei('image/png'), 'foto');
        expect(ergebnis.dataUri.startsWith('data:image/jpeg;base64,')).toBe(true);
        expect(ergebnis).toMatchObject({ breite: 300, hoehe: 300 });
        expect(aufrufe[0]).toEqual({ typ: 'image/jpeg', breite: 300 });
    });

    it('Logo mit Transparenz bleibt PNG', async () => {
        stelleBrowserNach({ breite: 600, hoehe: 200, alpha: 0 });
        const ergebnis = await bildVerkleinern(datei('image/png'), 'logo');
        expect(ergebnis.dataUri.startsWith('data:image/png;base64,')).toBe(true);
        expect(ergebnis.breite).toBe(300);
    });

    it('zu groß: erst Qualität, dann Größe runter, bis unter 300 KB', async () => {
        const zuGross = BILD_ZIEL_BYTES + 50_000;
        const aufrufe = stelleBrowserNach({ breite: 600, hoehe: 300, groessen: [zuGross, zuGross, zuGross, zuGross, 100_000] });
        const ergebnis = await bildVerkleinern(datei('image/jpeg'), 'banner');
        expect(aufrufe).toHaveLength(5);
        expect(aufrufe[4].breite).toBe(480);
        expect(ergebnis.bytes).toBeLessThanOrEqual(BILD_ZIEL_BYTES);
    });

    it('unerlaubte Datei: Fehlermeldung ohne Laden', async () => {
        await expect(bildVerkleinern(datei('image/svg+xml'), 'logo')).rejects.toThrow(/PNG, JPG, GIF oder WebP/);
    });

    it('beschädigtes Bild: verständliche Meldung', async () => {
        stelleBrowserNach({ breite: 100, hoehe: 100, ladeFehler: true });
        await expect(bildVerkleinern(datei('image/png'), 'logo')).rejects.toThrow(/nicht gelesen werden/);
    });

    it('auch winzig noch über 1 MB: Fehlermeldung', async () => {
        stelleBrowserNach({ breite: 50, hoehe: 50, alpha: 0, groessen: [2 * 1024 * 1024] });
        await expect(bildVerkleinern(datei('image/png'), 'logo')).rejects.toThrow(/zu groß/);
    });
});
