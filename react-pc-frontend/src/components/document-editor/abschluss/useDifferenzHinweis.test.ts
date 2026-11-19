import { describe, it, expect } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useDifferenzHinweis, type DifferenzHinweisEingabe } from './useDifferenzHinweis';
import type { DocBlock } from '../types';

const leistung = (id: string, title: string, price: number): DocBlock =>
    ({ id, type: 'SERVICE', title, quantity: 1, unit: 'psch', price } as DocBlock);
const BASIS = [leistung('s1', 'Geländer', 9000), leistung('s3', 'Rüstkosten', 1000)];

function eingabe(extra: Partial<DifferenzHinweisEingabe> = {}): DifferenzHinweisEingabe {
    return {
        dokumentTyp: 'RECHNUNG',
        isLocked: false,
        andereAktiveRechnungen: 0,
        gespeichertSchliesstAuftragAb: false,
        basisdokumentBlocks: BASIS,
        basisdokumentBetragNetto: 10000,
        blocks: [leistung('s1', 'Geländer', 9000)],
        globalRabatt: 0,
        ...extra,
    };
}

describe('useDifferenzHinweis', () => {
    it('nennt bei der abschliessenden einfachen Rechnung die entfallene Leistung', () => {
        const { result } = renderHook(() => useDifferenzHinweis(eingabe()));
        expect(result.current).toEqual({ einfacheRechnungSchliesstAb: true, differenzHinweis: 'Rüstkosten' });
    });

    it('nennt zusaetzliche Leistungen der Schlussrechnung', () => {
        const blocks = [...BASIS, leistung('s9', 'Kernbohrung', 500)];
        const { result } = renderHook(() => useDifferenzHinweis(eingabe({ dokumentTyp: 'SCHLUSSRECHNUNG', blocks, andereAktiveRechnungen: null })));
        expect(result.current).toEqual({ einfacheRechnungSchliesstAb: false, differenzHinweis: 'Kernbohrung' });
    });

    it('bleibt leer, wenn die einfache Rechnung nicht abschliesst oder nichts geladen ist', () => {
        expect(renderHook(() => useDifferenzHinweis(eingabe({ andereAktiveRechnungen: 1 }))).result.current)
            .toEqual({ einfacheRechnungSchliesstAb: false, differenzHinweis: '' });
        expect(renderHook(() => useDifferenzHinweis(eingabe({ andereAktiveRechnungen: null }))).result.current)
            .toEqual({ einfacheRechnungSchliesstAb: false, differenzHinweis: '' });
    });

    it('zaehlt gesperrt nur das gespeicherte Kennzeichen', () => {
        expect(renderHook(() => useDifferenzHinweis(eingabe({ isLocked: true, andereAktiveRechnungen: null, gespeichertSchliesstAuftragAb: true }))).result.current.einfacheRechnungSchliesstAb).toBe(true);
        expect(renderHook(() => useDifferenzHinweis(eingabe({ isLocked: true, gespeichertSchliesstAuftragAb: false }))).result.current.einfacheRechnungSchliesstAb).toBe(false);
    });

    it('bleibt ohne Abweichung leer', () => {
        const { result } = renderHook(() => useDifferenzHinweis(eingabe({ blocks: BASIS })));
        expect(result.current.differenzHinweis).toBe('');
    });
});
