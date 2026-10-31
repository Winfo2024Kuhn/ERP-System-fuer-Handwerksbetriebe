/**
 * Anmeldeformular für den Lieferanten-Shop. Nur Dummy-Zugangsdaten.
 */
import { describe, expect, it, vi } from 'vitest';
import { istErlaubteShopAdresse, sendeShopFormular, zeigeWarteTextImTab } from './shopFormular';

function tabMitDokument(): { tab: Window; doc: Document; submit: ReturnType<typeof vi.fn> } {
    const doc = document.implementation.createHTMLDocument('leer');
    const submit = vi.fn();
    vi.spyOn(HTMLFormElement.prototype, 'submit').mockImplementation(submit);
    const tab = { document: doc, opener: {} } as unknown as Window;
    return { tab, doc, submit };
}

describe('istErlaubteShopAdresse', () => {
    it('erlaubt https und http nur auf dem eigenen Rechner', () => {
        expect(istErlaubteShopAdresse('https://shop.example.de/ids')).toBe(true);
        expect(istErlaubteShopAdresse('http://localhost:9000/ids')).toBe(true);
        expect(istErlaubteShopAdresse('http://127.0.0.1/ids')).toBe(true);
        expect(istErlaubteShopAdresse('http://[::1]/ids')).toBe(true);
        expect(istErlaubteShopAdresse('http://shop.example.de/ids')).toBe(false);
        expect(istErlaubteShopAdresse('javascript:alert(1)')).toBe(false);
        expect(istErlaubteShopAdresse('data:text/html,x')).toBe(false);
        expect(istErlaubteShopAdresse('kein link')).toBe(false);
    });
});

describe('sendeShopFormular', () => {
    it('baut ein verstecktes POST-Formular per DOM und schickt es ab', () => {
        const { tab, doc, submit } = tabMitDokument();
        sendeShopFormular(tab, {
            action: 'https://shop.example.de/ids',
            enctype: 'multipart/form-data',
            felder: { KND_NR: '12345', PW: 'geheim"><script>alert(1)</script>' },
        });
        const form = doc.querySelector('form')!;
        expect(form.method).toBe('post');
        expect(form.action).toBe('https://shop.example.de/ids');
        expect(form.enctype).toBe('multipart/form-data');
        const felder = Array.from(form.querySelectorAll('input'));
        expect(felder.map((f) => [f.type, f.name, f.value])).toEqual([
            ['hidden', 'KND_NR', '12345'],
            ['hidden', 'PW', 'geheim"><script>alert(1)</script>'],
        ]);
        // Kein eingeschleustes Skript-Element.
        expect(doc.querySelector('script')).toBeNull();
        expect(tab.opener).toBeNull();
        expect(submit).toHaveBeenCalledTimes(1);
    });

    it('fällt bei unbekanntem enctype auf das Standardformat zurück', () => {
        const { tab, doc } = tabMitDokument();
        sendeShopFormular(tab, { action: 'https://shop.example.de/ids', enctype: 'text/evil', felder: {} });
        expect(doc.querySelector('form')!.enctype).toBe('application/x-www-form-urlencoded');
    });

    it('lehnt unsichere Adressen ab, ohne etwas abzuschicken', () => {
        const { tab, doc, submit } = tabMitDokument();
        expect(() => sendeShopFormular(tab, { action: 'http://shop.example.de', enctype: '', felder: { PW: 'x' } }))
            .toThrow(/https/);
        expect(doc.querySelector('form')).toBeNull();
        expect(submit).not.toHaveBeenCalled();
    });

    it('verkraftet einen Tab, dessen opener sich nicht setzen lässt', () => {
        const doc = document.implementation.createHTMLDocument('leer');
        vi.spyOn(HTMLFormElement.prototype, 'submit').mockImplementation(() => undefined);
        const tab = { document: doc } as unknown as Window;
        Object.defineProperty(tab, 'opener', { set() { throw new Error('nein'); } });
        expect(() => sendeShopFormular(tab, { action: 'https://shop.example.de', enctype: '', felder: {} })).not.toThrow();
    });
});

describe('zeigeWarteTextImTab', () => {
    it('schreibt den Text als reinen Text in den Tab', () => {
        const { tab, doc } = tabMitDokument();
        zeigeWarteTextImTab(tab, '<b>Shop wird geöffnet</b>');
        expect(doc.title).toBe('<b>Shop wird geöffnet</b>');
        expect(doc.body.querySelector('b')).toBeNull();
        expect(doc.body.textContent).toContain('<b>Shop wird geöffnet</b>');
    });

    it('bleibt still, wenn der Tab nicht zugänglich ist', () => {
        const tab = {} as Window;
        Object.defineProperty(tab, 'document', { get() { throw new Error('fremd'); } });
        expect(() => zeigeWarteTextImTab(tab, 'x')).not.toThrow();
    });
});
