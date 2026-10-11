import { afterEach, describe, expect, it, vi } from 'vitest';
import { kopiereSignatur, signaturAlsText } from './zwischenablage';

const HTML = '<table><tr><td><p style="margin:0">Max&nbsp;Mustermann</p><p>Metallbau &amp; Stahlbau</p></td></tr>'
    + '<tr><td><a href="tel:0931">0931 123456</a> · <span>max@example.com</span><br>Musterstadt</td></tr></table>';

describe('signaturAlsText', () => {
    it('macht lesbaren Text mit Zeilenumbrüchen', () => {
        expect(signaturAlsText(HTML)).toBe('Max Mustermann\nMetallbau & Stahlbau\n0931 123456 · max@example.com\nMusterstadt');
    });
    it('entfernt Skripte samt Tags vollständig', () => {
        expect(signaturAlsText('<scr<script>ipt>alert(1)</script>Max')).not.toContain('<');
    });
});

describe('kopiereSignatur', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('kopiert formatiert (HTML + Text), wenn der Browser es kann', async () => {
        const write = vi.fn(async () => undefined);
        vi.stubGlobal('navigator', { clipboard: { write, writeText: vi.fn() } });
        class TestItem { constructor(public daten: Record<string, Blob>) {} }
        vi.stubGlobal('ClipboardItem', TestItem);
        await expect(kopiereSignatur(HTML)).resolves.toBe('formatiert');
        const item = (write.mock.calls[0] as unknown as [TestItem[]])[0][0];
        expect(Object.keys(item.daten)).toEqual(['text/html', 'text/plain']);
        expect(await item.daten['text/html'].text()).toBe(HTML);
    });

    it('fällt auf reinen Text zurück', async () => {
        const writeText = vi.fn(async () => undefined);
        vi.stubGlobal('navigator', { clipboard: { writeText } });
        vi.stubGlobal('ClipboardItem', undefined);
        await expect(kopiereSignatur(HTML)).resolves.toBe('nurText');
        expect(writeText).toHaveBeenCalledWith(signaturAlsText(HTML));
    });

    it('fällt auf Text zurück, wenn formatiertes Kopieren abgelehnt wird', async () => {
        const writeText = vi.fn(async () => undefined);
        vi.stubGlobal('navigator', { clipboard: { write: vi.fn(async () => { throw new Error('verweigert'); }), writeText } });
        vi.stubGlobal('ClipboardItem', class { constructor(public d: unknown) {} });
        await expect(kopiereSignatur(HTML)).resolves.toBe('nurText');
    });

    it('ohne Zwischenablage: verständlicher Fehler', async () => {
        vi.stubGlobal('navigator', {});
        await expect(kopiereSignatur(HTML)).rejects.toThrow(/erlaubt das Kopieren nicht/);
    });
});
