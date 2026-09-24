/**
 * Vitest-Suite fuer useDokumentVerlauf -- die Bindung des generischen
 * Aenderungsverlaufs (aenderungsVerlauf.ts) an den Editor-Zustand.
 *
 * `leseStand`/`schreibeStand` werden ueber ein einfaches Fake-Objekt
 * nachgebildet (kein echter Editor-State noetig, siehe Plan). DSGVO: nur
 * Dummy-Daten (Max Mustermann / Musterweg 1).
 */
import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useDokumentVerlauf, type AenderungsAuftrag, type DokumentStand } from './useDokumentVerlauf';
import type { DocBlock } from './types';

function neuerBlock(id: string, title: string): DocBlock {
    return { id, type: 'SERVICE', title, quantity: 1, unit: 'Stk', price: 100 };
}

function neuerStand(overrides: Partial<DokumentStand> = {}): DokumentStand {
    return {
        blocks: [neuerBlock('a', 'Leistung A')],
        globalRabatt: 0,
        datum: '2026-09-01',
        zahlungsziel: 8,
        rechnungsadresse: 'Max Mustermann\nMusterweg 1\n12345 Musterstadt',
        balkenAnzeigen: true,
        ...overrides,
    };
}

describe('useDokumentVerlauf', () => {
    beforeEach(() => {
        vi.useFakeTimers();
    });
    afterEach(() => {
        vi.useRealTimers();
    });

    function aufbauen(initial?: Partial<DokumentStand>, gesperrt = false) {
        let stand = neuerStand(initial);
        const leseStand = vi.fn(() => stand);
        const schreibeStand = vi.fn((werte: Partial<DokumentStand>) => {
            stand = { ...stand, ...werte };
        });
        const hook = renderHook(() => useDokumentVerlauf({ leseStand, schreibeStand, gesperrt }));
        return { hook, leseStand, schreibeStand, getStand: () => stand };
    }

    it('aendern schreibt den Stand und meldet true', () => {
        const { hook, schreibeStand } = aufbauen();
        const auftrag: AenderungsAuftrag = {
            bezeichnung: 'Rabatt geändert',
            berechne: () => ({ globalRabatt: 10 }),
        };

        let ergebnis!: boolean;
        act(() => {
            ergebnis = hook.result.current.aendern(auftrag);
        });

        expect(ergebnis).toBe(true);
        expect(schreibeStand).toHaveBeenCalledWith({ globalRabatt: 10 });
        expect(hook.result.current.kannRueckgaengig).toBe(true);
        expect(hook.result.current.naechstesRueckgaengig).toBe('Rabatt geändert');
    });

    it('eine unveraenderte Berechnung meldet false und erzeugt keinen Schritt', () => {
        const { hook, schreibeStand } = aufbauen({ globalRabatt: 10 });
        const auftrag: AenderungsAuftrag = {
            bezeichnung: 'Rabatt geändert',
            berechne: () => ({ globalRabatt: 10 }),
        };

        let ergebnis!: boolean;
        act(() => {
            ergebnis = hook.result.current.aendern(auftrag);
        });

        expect(ergebnis).toBe(false);
        expect(schreibeStand).not.toHaveBeenCalled();
        expect(hook.result.current.kannRueckgaengig).toBe(false);
    });

    it('gesperrt: true verhindert aendern (false) und rueckgaengig (null)', () => {
        const { hook, schreibeStand } = aufbauen({}, true);

        let ergebnis!: boolean;
        act(() => {
            ergebnis = hook.result.current.aendern({ bezeichnung: 'x', berechne: () => ({ globalRabatt: 5 }) });
        });
        expect(ergebnis).toBe(false);
        expect(schreibeStand).not.toHaveBeenCalled();

        let meldung: ReturnType<typeof hook.result.current.rueckgaengig>;
        act(() => {
            meldung = hook.result.current.rueckgaengig();
        });
        expect(meldung).toBeNull();
    });

    it('buendelt aufeinanderfolgende Aenderungen mit gleichem buendelSchluessel innerhalb der Pause', () => {
        const { hook } = aufbauen();

        act(() => {
            hook.result.current.aendern({
                bezeichnung: 'Rabatt geändert',
                berechne: () => ({ globalRabatt: 1 }),
                buendelSchluessel: 'rabatt',
            });
        });
        act(() => {
            vi.advanceTimersByTime(1000);
        });
        act(() => {
            hook.result.current.aendern({
                bezeichnung: 'Rabatt geändert',
                berechne: () => ({ globalRabatt: 2 }),
                buendelSchluessel: 'rabatt',
            });
        });

        expect(hook.result.current.schritte).toHaveLength(1);
    });

    it('rueckgaengig setzt nur die betroffenen Felder zurueck (feldgenau) -- ein zwischenzeitlich von aussen geaendertes Feld bleibt stehen', () => {
        const { hook, getStand } = aufbauen();

        act(() => {
            hook.result.current.aendern({ bezeichnung: 'Rabatt geändert', berechne: () => ({ globalRabatt: 10 }) });
        });
        act(() => {
            hook.result.current.aendern({ bezeichnung: 'Datum geändert', berechne: () => ({ datum: '2026-09-15' }) });
        });

        let meldung: ReturnType<typeof hook.result.current.rueckgaengig>;
        act(() => {
            meldung = hook.result.current.rueckgaengig(1);
        });

        expect(meldung).toEqual({ bezeichnung: 'Datum geändert', anzahl: 1, ziel: null });
        expect(getStand().datum).toBe('2026-09-01'); // zurueckgesetzt
        expect(getStand().globalRabatt).toBe(10); // bleibt stehen -- eigener, nicht zurueckgenommener Schritt
    });

    it('rueckgaengig(3) liefert eine gebuendelte Meldung', () => {
        const { hook } = aufbauen();

        act(() => {
            hook.result.current.aendern({ bezeichnung: 'A', berechne: () => ({ globalRabatt: 1 }) });
        });
        act(() => { vi.advanceTimersByTime(3000); });
        act(() => {
            hook.result.current.aendern({ bezeichnung: 'B', berechne: () => ({ datum: '2026-09-05' }) });
        });
        act(() => { vi.advanceTimersByTime(3000); });
        act(() => {
            hook.result.current.aendern({ bezeichnung: 'C', berechne: () => ({ balkenAnzeigen: false }) });
        });

        let meldung: ReturnType<typeof hook.result.current.rueckgaengig>;
        act(() => {
            meldung = hook.result.current.rueckgaengig(3);
        });

        expect(meldung).toEqual({ anzahl: 3, bezeichnung: '3 Schritte', ziel: null });
    });

    it('wiederholen() nach rueckgaengig() stellt den Stand wieder her', () => {
        const { hook, getStand } = aufbauen();

        act(() => {
            hook.result.current.aendern({ bezeichnung: 'Rabatt geändert', berechne: () => ({ globalRabatt: 10 }) });
        });
        act(() => {
            hook.result.current.rueckgaengig();
        });
        expect(getStand().globalRabatt).toBe(0);

        let meldung: ReturnType<typeof hook.result.current.wiederholen>;
        act(() => {
            meldung = hook.result.current.wiederholen();
        });

        expect(meldung?.bezeichnung).toBe('Rabatt geändert');
        expect(getStand().globalRabatt).toBe(10);
    });

    it('leeren() setzt kannRueckgaengig und kannWiederholen auf false', () => {
        const { hook } = aufbauen();

        act(() => {
            hook.result.current.aendern({ bezeichnung: 'Rabatt geändert', berechne: () => ({ globalRabatt: 10 }) });
        });
        expect(hook.result.current.kannRueckgaengig).toBe(true);

        act(() => {
            hook.result.current.leeren();
        });

        expect(hook.result.current.kannRueckgaengig).toBe(false);
        expect(hook.result.current.kannWiederholen).toBe(false);
    });
});
