import { act, renderHook } from '@testing-library/react';
import type { DragEvent } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { baueBaum } from '../../lib/ordnerBaum';
import { schreibeGezogenesDokument } from './firmendokumenteLogik';
import { schreibeGezogenenOrdner } from './ordnerZiehen';
import type { FirmenOrdner } from './typen';
import { useAblageRegeln, useAblageZiel, type AblageRegeln } from './useAblageZiel';

const o = (id: number, name: string, parentId: number | null, extra: Partial<FirmenOrdner> = {}): FirmenOrdner => ({
    id, name, parentId, anzahlDokumente: 0, anzahlUeberfaellig: 0, anzahlBaldFaellig: 0, darfAendern: true, eingeschraenkt: false, ...extra,
});
// Geschäftsleitung ist eingeschränkt (mit „Ändern“), Archiv nur zum Ansehen.
const BAUM = baueBaum([
    o(1, 'Prüfungen', null), o(2, 'Tore', 1),
    o(6, 'Geschäftsleitung', null, { eingeschraenkt: true }), o(7, 'Verträge', 6, { eingeschraenkt: true }),
    o(8, 'Archiv', null, { darfAendern: false }),
]);

function regeln(): AblageRegeln {
    return { baum: BAUM, istAdmin: false, onDateien: vi.fn(), onDokument: vi.fn(), onOrdner: vi.fn(), onAbgelehnt: vi.fn() };
}

/** Zieh-Ereignis mit allem, was der Hook liest. */
function ereignis(schreiben: (t: { setData: (typ: string, wert: string) => void }) => void, files: File[] = []) {
    const daten = new Map<string, string>();
    schreiben({ setData: (typ, wert) => { daten.set(typ, wert); } });
    const dataTransfer = { types: files.length ? ['Files'] : [...daten.keys()], files, getData: (typ: string) => daten.get(typ) ?? '', dropEffect: 'none' };
    return { dataTransfer, preventDefault: vi.fn(), currentTarget: { contains: () => false }, relatedTarget: null } as unknown as DragEvent & { preventDefault: ReturnType<typeof vi.fn> };
}
const dokument = (ordnerId: number) => ereignis((t) => schreibeGezogenesDokument(t, { id: 10, ordnerId, name: 'Torprüfung', faelligAm: null }));
const ordner = (id: number) => ereignis((t) => schreibeGezogenenOrdner(t, { id, name: 'egal' }));

describe('useAblageZiel', () => {
    it('leuchtet für ein Dokument aus dem eigenen Ordner oder für Ordner ohne „Ändern“ gar nicht erst auf', () => {
        const { result: tore } = renderHook(() => useAblageZiel(regeln(), 2));
        const eigen = dokument(2);
        act(() => tore.current.onDragOver(eigen));
        expect(eigen.preventDefault).not.toHaveBeenCalled();
        expect(tore.current.aktiv).toBe(false);

        const { result: archiv } = renderHook(() => useAblageZiel(regeln(), 8));
        const fremd = dokument(1);
        act(() => archiv.current.onDragOver(fremd));
        expect(fremd.preventDefault).not.toHaveBeenCalled();
        expect(archiv.current.aktiv).toBe(false);
    });

    it('nimmt ein Dokument aus einem anderen Ordner an und verschiebt es beim Loslassen', () => {
        const r = regeln();
        const { result } = renderHook(() => useAblageZiel(r, 1));
        const e = dokument(2);
        act(() => result.current.onDragOver(e));
        expect(e.preventDefault).toHaveBeenCalled();
        expect(e.dataTransfer.dropEffect).toBe('move');
        expect(result.current.aktiv).toBe(true);
        act(() => result.current.onDrop(dokument(2)));
        expect(r.onDokument).toHaveBeenCalledWith({ id: 10, ordnerId: 2, name: 'Torprüfung', faelligAm: null }, expect.objectContaining({ id: 1 }));
        expect(result.current.aktiv).toBe(false);
    });

    it('meldet die Admin-Sperre erst beim Loslassen – Dokument und Ordner', () => {
        const r = regeln();
        const { result } = renderHook(() => useAblageZiel(r, 1));
        const e = dokument(7);
        act(() => result.current.onDragOver(e));
        expect(e.preventDefault).toHaveBeenCalled();
        act(() => result.current.onDrop(dokument(7)));
        act(() => result.current.onDrop(ordner(7)));
        expect(r.onDokument).not.toHaveBeenCalled();
        expect(r.onOrdner).not.toHaveBeenCalled();
        expect(r.onAbgelehnt).toHaveBeenCalledTimes(2);
        expect(r.onAbgelehnt).toHaveBeenCalledWith('Aus einem eingeschränkten Ordner kann nur ein Admin in einen anderen Hauptordner verschieben.');
    });

    it('nimmt Dateien mit „Kopieren“ an und legt sie im Ordner ab', () => {
        const r = regeln();
        const { result } = renderHook(() => useAblageZiel(r, 2));
        const dateien = [new File(['x'], 'foto.jpg')];
        const e = ereignis(() => {}, dateien);
        act(() => result.current.onDragOver(e));
        expect(e.dataTransfer.dropEffect).toBe('copy');
        act(() => result.current.onDrop(e));
        expect(r.onDateien).toHaveBeenCalledWith(2, dateien);
    });
});

describe('useAblageRegeln', () => {
    it('bleibt dasselbe Objekt, solange Baum und Admin gleich sind – ruft aber immer die neuesten Aufgaben', () => {
        const erste = vi.fn();
        const zweite = vi.fn();
        const { result, rerender } = renderHook(({ onDateien, baum }) => useAblageRegeln({
            baum, istAdmin: false, onDateien, onDokument: vi.fn(), onOrdner: vi.fn(), onAbgelehnt: vi.fn(),
        }), { initialProps: { onDateien: erste, baum: BAUM } });
        const vorher = result.current;
        rerender({ onDateien: zweite, baum: BAUM });
        expect(result.current).toBe(vorher);
        const dateien = [] as unknown as FileList;
        result.current.onDateien(2, dateien);
        expect(zweite).toHaveBeenCalledWith(2, dateien);
        expect(erste).not.toHaveBeenCalled();
        rerender({ onDateien: zweite, baum: baueBaum([o(1, 'Prüfungen', null)]) });
        expect(result.current).not.toBe(vorher);
    });
});
