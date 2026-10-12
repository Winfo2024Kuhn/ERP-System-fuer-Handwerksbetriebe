import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { scrolleMittigInDenBlick } from './scrollInDenBlick';

interface Geometrie {
    oben: number;
    hoehe: number;
    inhalt?: number;
    scrollTop?: number;
    rahmenOben?: number;
}

/** Box mit fester Lage im Fenster; scrollTo wird nur aufgezeichnet, die Lage bleibt stehen. */
function box(overflowY: string | null, geo: Geometrie, eltern?: HTMLElement): HTMLElement {
    const el = document.createElement('div');
    if (overflowY) el.style.overflowY = overflowY;
    el.getBoundingClientRect = () => ({ top: geo.oben, height: geo.hoehe } as DOMRect);
    Object.defineProperties(el, {
        clientHeight: { value: geo.hoehe },
        scrollHeight: { value: geo.inhalt ?? geo.hoehe },
        clientTop: { value: geo.rahmenOben ?? 0 },
        scrollTop: { value: geo.scrollTop ?? 0, writable: true },
    });
    el.scrollTo = vi.fn() as unknown as typeof el.scrollTo;
    (eltern ?? document.body).appendChild(el);
    return el;
}

function reduzierteBewegung(an: boolean) {
    window.matchMedia = vi.fn().mockReturnValue({ matches: an }) as unknown as typeof window.matchMedia;
}

describe('scrolleMittigInDenBlick', () => {
    const originalMatchMedia = window.matchMedia;

    beforeEach(() => reduzierteBewegung(false));
    afterEach(() => {
        window.matchMedia = originalMatchMedia;
        document.body.innerHTML = '';
    });

    it('holt das Element mittig in den scrollbaren Container', () => {
        // Sichtbereich 100–500 (400 hoch), schon 50 gescrollt; Element bei 700, 100 hoch.
        const liste = box('auto', { oben: 100, hoehe: 400, inhalt: 2000, scrollTop: 50 });
        const eintrag = box(null, { oben: 700, hoehe: 100 }, liste);

        scrolleMittigInDenBlick(eintrag);

        // 50 + (700 - 100) - (400 - 100) / 2 = 500
        expect(liste.scrollTo).toHaveBeenCalledWith({ top: 500, behavior: 'smooth' });
    });

    it('lässt overflow-hidden-Hüllen stehen – die kann der Nutzer nicht zurückscrollen', () => {
        const huelle = box('hidden', { oben: 0, hoehe: 800, inhalt: 1200 });
        const liste = box('auto', { oben: 100, hoehe: 400, inhalt: 2000 }, huelle);
        const eintrag = box(null, { oben: 450, hoehe: 100 }, liste);

        scrolleMittigInDenBlick(eintrag);

        expect(liste.scrollTo).toHaveBeenCalledWith({ top: 200, behavior: 'smooth' });
        expect(huelle.scrollTo).not.toHaveBeenCalled();
    });

    it('überspringt Container ohne Überhang und scrollt den nächsten äußeren', () => {
        const seite = box('scroll', { oben: 0, hoehe: 600, inhalt: 1500 });
        const karte = box('auto', { oben: 900, hoehe: 200 }, seite);
        const eintrag = box(null, { oben: 950, hoehe: 50 }, karte);

        scrolleMittigInDenBlick(eintrag);

        expect(karte.scrollTo).not.toHaveBeenCalled();
        // 0 + 950 - 0 - (600 - 50) / 2 = 675
        expect(seite.scrollTo).toHaveBeenCalledWith({ top: 675, behavior: 'smooth' });
    });

    it('begrenzt auf den Anfang und das Ende des Containers', () => {
        const oben = box('auto', { oben: 0, hoehe: 400, inhalt: 1000 });
        scrolleMittigInDenBlick(box(null, { oben: 10, hoehe: 20 }, oben));
        expect(oben.scrollTo).toHaveBeenCalledWith({ top: 0, behavior: 'smooth' });

        const unten = box('auto', { oben: 0, hoehe: 400, inhalt: 1000, scrollTop: 500 });
        scrolleMittigInDenBlick(box(null, { oben: 380, hoehe: 20 }, unten));
        expect(unten.scrollTo).toHaveBeenCalledWith({ top: 600, behavior: 'smooth' });
    });

    it('rechnet bei verschachtelten Containern die Verschiebung des inneren mit ein', () => {
        const seite = box('auto', { oben: 0, hoehe: 600, inhalt: 3000 });
        const liste = box('auto', { oben: 1000, hoehe: 200, inhalt: 400, rahmenOben: 2 }, seite);
        const eintrag = box(null, { oben: 1302, hoehe: 100 }, liste);

        scrolleMittigInDenBlick(eintrag);

        // Liste: 0 + 1302 - 1002 - 50 = 250 → begrenzt auf 200 → Eintrag rückt um 200 hoch auf 1102.
        expect(liste.scrollTo).toHaveBeenCalledWith({ top: 200, behavior: 'smooth' });
        // Seite: 0 + 1102 - 0 - (600 - 100) / 2 = 852
        expect(seite.scrollTo).toHaveBeenCalledWith({ top: 852, behavior: 'smooth' });
    });

    it('hört an einem fixierten Dialog auf – die Seite dahinter bleibt stehen', () => {
        const seite = box('auto', { oben: 0, hoehe: 800, inhalt: 4000, scrollTop: 500 });
        const dialog = box(null, { oben: 0, hoehe: 800 }, seite);
        dialog.style.position = 'fixed';
        const verlauf = box('auto', { oben: 100, hoehe: 400, inhalt: 2000 }, dialog);
        const nachricht = box(null, { oben: 700, hoehe: 100 }, verlauf);

        scrolleMittigInDenBlick(nachricht);

        expect(verlauf.scrollTo).toHaveBeenCalledWith({ top: 450, behavior: 'smooth' });
        expect(seite.scrollTo).not.toHaveBeenCalled();
    });

    it('scrollt nichts, wenn das Element selbst fixiert ist', () => {
        const seite = box('auto', { oben: 0, hoehe: 800, inhalt: 4000 });
        const leiste = box(null, { oben: 900, hoehe: 50 }, seite);
        leiste.style.position = 'fixed';

        scrolleMittigInDenBlick(leiste);

        expect(seite.scrollTo).not.toHaveBeenCalled();
    });

    it('scrollt das Dokument selbst, wenn es der scrollende Bereich ist', () => {
        const dokument = document.documentElement;
        const scrollTo = vi.fn();
        const vorher = Object.getOwnPropertyDescriptor(document, 'scrollingElement');
        Object.defineProperty(document, 'scrollingElement', { value: dokument, configurable: true });
        Object.defineProperties(dokument, {
            clientHeight: { value: 800, configurable: true },
            scrollHeight: { value: 3000, configurable: true },
            scrollTop: { value: 100, configurable: true },
            scrollTo: { value: scrollTo, configurable: true },
        });
        try {
            scrolleMittigInDenBlick(box(null, { oben: 900, hoehe: 100 }));
            // 100 + 900 - 0 - (800 - 100) / 2 = 650
            expect(scrollTo).toHaveBeenCalledWith({ top: 650, behavior: 'smooth' });
        } finally {
            for (const prop of ['clientHeight', 'scrollHeight', 'scrollTop', 'scrollTo']) {
                delete (dokument as unknown as Record<string, unknown>)[prop];
            }
            if (vorher) Object.defineProperty(document, 'scrollingElement', vorher);
            else delete (document as unknown as Record<string, unknown>).scrollingElement;
        }
    });

    it('springt ohne Animation, wenn „Bewegung reduzieren“ eingestellt ist', () => {
        reduzierteBewegung(true);
        const liste = box('auto', { oben: 0, hoehe: 400, inhalt: 2000 });
        scrolleMittigInDenBlick(box(null, { oben: 600, hoehe: 100 }, liste));
        expect(liste.scrollTo).toHaveBeenCalledWith({ top: 450, behavior: 'auto' });
    });

    it('läuft auch ohne matchMedia und ohne scrollTo des Browsers fehlerfrei durch', () => {
        window.matchMedia = undefined as unknown as typeof window.matchMedia;
        const liste = box('auto', { oben: 0, hoehe: 400, inhalt: 2000 });
        const ohneScrollTo = box('auto', { oben: 0, hoehe: 200, inhalt: 800 }, liste);
        (ohneScrollTo as unknown as { scrollTo?: unknown }).scrollTo = undefined;

        expect(() => scrolleMittigInDenBlick(box(null, { oben: 600, hoehe: 100 }, ohneScrollTo))).not.toThrow();
        expect(liste.scrollTo).toHaveBeenCalledWith({ top: 450, behavior: 'smooth' });
    });
});
