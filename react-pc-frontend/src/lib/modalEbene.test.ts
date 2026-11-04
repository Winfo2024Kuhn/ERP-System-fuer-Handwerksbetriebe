import { afterEach, describe, expect, it } from 'vitest';
import { DIALOG_Z_BASIS, fokussierbareIn, meldungsBereiche, obersteModaleEbene, sichtbar, VORSCHAU_Z_INDEX, vorschauZIndex } from './modalEbene';

/** Baut eine modale Ebene wie Dialog bzw. Vorschau: role="dialog" aria-modal, z-index inline. */
function ebene(zIndex: number, ...kinder: HTMLElement[]): HTMLElement {
    const element = document.createElement('div');
    element.setAttribute('role', 'dialog');
    element.setAttribute('aria-modal', 'true');
    element.style.zIndex = String(zIndex);
    element.append(...kinder);
    document.body.appendChild(element);
    return element;
}

/** Element mit id und Attributen, ohne HTML-Text zu parsen. */
function el(tag: string, id: string, attribute: Record<string, string> = {}): HTMLElement {
    const element = document.createElement(tag);
    element.id = id;
    for (const [name, wert] of Object.entries(attribute)) element.setAttribute(name, wert);
    return element;
}

afterEach(() => { document.body.replaceChildren(); });

describe('obersteModaleEbene', () => {
    it('liefert nichts, wenn keine modale Ebene offen ist', () => {
        expect(obersteModaleEbene()).toBeUndefined();
    });

    it('nimmt die Ebene mit dem höchsten z-index, auch wenn er an einem Vorfahren steht', () => {
        const dialog = ebene(51);
        const huelle = document.createElement('div');
        huelle.style.zIndex = String(VORSCHAU_Z_INDEX);
        const vorschau = document.createElement('div');
        vorschau.setAttribute('role', 'dialog');
        vorschau.setAttribute('aria-modal', 'true');
        huelle.appendChild(vorschau);
        document.body.prepend(huelle);
        expect(obersteModaleEbene()).toBe(vorschau);
        expect(obersteModaleEbene(vorschau)).toBe(dialog);
    });

    it('bei Gleichstand gewinnt die spätere Ebene im DOM', () => {
        ebene(50);
        const spaeter = ebene(50);
        expect(obersteModaleEbene()).toBe(spaeter);
    });

    it('überspringt versteckte Ebenen', () => {
        const unten = ebene(50);
        const oben = ebene(100);
        oben.hidden = true;
        expect(obersteModaleEbene()).toBe(unten);
    });
});

describe('sichtbar', () => {
    it('erkennt hidden, inert und aria-hidden an Vorfahren', () => {
        const aussen = document.createElement('div');
        const innen = document.createElement('button');
        aussen.appendChild(innen);
        document.body.appendChild(aussen);
        expect(sichtbar(innen)).toBe(true);
        aussen.setAttribute('aria-hidden', 'true');
        expect(sichtbar(innen)).toBe(false);
        aussen.removeAttribute('aria-hidden');
        aussen.setAttribute('inert', '');
        expect(sichtbar(innen)).toBe(false);
        aussen.removeAttribute('inert');
        aussen.style.display = 'none';
        expect(sichtbar(innen)).toBe(false);
    });
});

describe('fokussierbareIn', () => {
    it('liefert nur erreichbare, aktive und sichtbare Elemente in Reihenfolge', () => {
        const fenster = ebene(100,
            el('button', 'a'),
            el('button', 'b', { disabled: '' }),
            el('a', 'c', { href: '/x' }),
            el('span', 'd', { tabindex: '-1' }),
            el('button', 'e', { hidden: '' }),
            el('input', 'f'));
        expect(fokussierbareIn(fenster).map(element => element.id)).toEqual(['a', 'c', 'f']);
    });
});

describe('vorschauZIndex', () => {
    it('liegt über normalen Dialogen und unter dem Anruf-Fenster (70)', () => {
        expect(vorschauZIndex(0)).toBe(VORSCHAU_Z_INDEX);
        expect(VORSCHAU_Z_INDEX).toBeLessThan(70);
        // Dialog der Tiefe 0–2 liegt bei 50–52, seine Kinder sehen Tiefe 1–3.
        for (const tiefe of [1, 2, 3]) {
            expect(vorschauZIndex(tiefe)).toBeGreaterThan(DIALOG_Z_BASIS + tiefe - 1);
            expect(vorschauZIndex(tiefe)).toBeLessThan(70);
        }
    });

    it('liegt über einem Dialog, der aus dem Anruf-Fenster geöffnet wurde', () => {
        // DialogEbene ueberZIndex 70 → Tiefe 21 → solche Dialoge liegen bei 71.
        expect(vorschauZIndex(22)).toBeGreaterThan(71);
    });
});

describe('meldungsBereiche', () => {
    it('liefert nur sichtbare Toast-Bereiche', () => {
        const sichtbarer = el('div', 't1', { 'data-pc-toasts': '' });
        const versteckter = el('div', 't2', { 'data-pc-toasts': '', hidden: '' });
        document.body.append(sichtbarer, versteckter);
        expect(meldungsBereiche()).toEqual([sichtbarer]);
    });
});
