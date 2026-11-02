import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useNachladeListe, type ListenSeite, type SeitenLader } from './useNachladeListe';

type Eintrag = { id: number; name: string };
const seite = (ids: number[], gesamt: number): ListenSeite<Eintrag> => ({ eintraege: ids.map((id) => ({ id, name: `E${id}` })), gesamt });

/** Lader, dessen Antworten der Test selbst auslöst. */
function steuerbarerLader() {
    const offen: { seite: number; signal: AbortSignal; loesen: (s: ListenSeite<Eintrag>) => void; ablehnen: (e: unknown) => void }[] = [];
    const lader: SeitenLader<Eintrag> = (nr, signal) => new Promise((loesen, ablehnen) => { offen.push({ seite: nr, signal, loesen, ablehnen }); });
    return { lader, offen };
}

describe('useNachladeListe', () => {
    it('lädt die erste Seite, lädt nach und hängt nur Neues an', async () => {
        const { lader, offen } = steuerbarerLader();
        const melde = vi.fn();
        const { result } = renderHook(() => useNachladeListe('a', lader, melde));
        expect(result.current.laedt).toBe(true);
        await act(async () => { offen[0].loesen(seite([1, 2], 3)); });
        expect(result.current.eintraege.map((e) => e.id)).toEqual([1, 2]);
        expect(result.current.laedt).toBe(false);

        act(() => result.current.mehrLaden());
        expect(result.current.laedtMehr).toBe(true);
        expect(offen[1].seite).toBe(1);
        act(() => result.current.mehrLaden()); // Doppelklick: keine zweite Abfrage
        expect(offen).toHaveLength(2);
        await act(async () => { offen[1].loesen(seite([2, 3], 3)); });
        expect(result.current.eintraege.map((e) => e.id)).toEqual([1, 2, 3]);
        expect(result.current.laedtMehr).toBe(false);

        act(() => result.current.mehrLaden()); // alles da: nichts mehr zu holen
        expect(offen).toHaveLength(2);
        expect(melde).not.toHaveBeenCalled();
    });

    it('beginnt bei neuem Schlüssel leer und verwirft späte Antworten', async () => {
        const { lader, offen } = steuerbarerLader();
        const { result, rerender } = renderHook(({ k }) => useNachladeListe(k, lader, vi.fn()), { initialProps: { k: 'ordner:1' as string | null } });
        rerender({ k: 'ordner:2' });
        expect(offen[0].signal.aborted).toBe(true);
        await act(async () => { offen[0].loesen(seite([9], 1)); });
        expect(result.current.eintraege).toEqual([]);
        await act(async () => { offen[1].loesen(seite([5], 1)); });
        expect(result.current.eintraege.map((e) => e.id)).toEqual([5]);

        rerender({ k: null });
        expect(result.current.eintraege).toEqual([]);
        expect(result.current.laedt).toBe(false);
    });

    it('meldet Fehler beim ersten Laden und beim Nachladen; die Liste bleibt', async () => {
        const { lader, offen } = steuerbarerLader();
        const melde = vi.fn();
        const { result } = renderHook(() => useNachladeListe('a', lader, melde));
        await act(async () => { offen[0].ablehnen(new Error('Ordner fehlt.')); });
        expect(result.current.fehler).toBe('Ordner fehlt.');
        expect(melde).toHaveBeenCalledWith('Ordner fehlt.');

        act(() => result.current.neuLaden());
        expect(result.current.laedt).toBe(true);
        expect(result.current.fehler).toBeNull();
        await waitFor(() => expect(offen).toHaveLength(2));
        await act(async () => { offen[1].loesen(seite([1], 2)); });

        act(() => result.current.mehrLaden());
        await act(async () => { offen[2].ablehnen('kaputt'); });
        expect(melde).toHaveBeenLastCalledWith('Weitere Einträge konnten nicht geladen werden.');
        expect(result.current.eintraege.map((e) => e.id)).toEqual([1]);
        expect(result.current.laedtMehr).toBe(false);
    });

    it('nimmt erstes Laden mit Nicht-Error als Ersatztext', async () => {
        const { lader, offen } = steuerbarerLader();
        const { result } = renderHook(() => useNachladeListe('a', lader, vi.fn()));
        await act(async () => { offen[0].ablehnen('weg'); });
        expect(result.current.fehler).toBe('Die Liste konnte nicht geladen werden.');
    });

    it('bricht Nachladen beim Wechsel ab und ignoriert dessen Ergebnis', async () => {
        const { lader, offen } = steuerbarerLader();
        const melde = vi.fn();
        const { result, rerender } = renderHook(({ k }) => useNachladeListe(k, lader, melde), { initialProps: { k: 'a' } });
        await act(async () => { offen[0].loesen(seite([1], 5)); });
        act(() => result.current.mehrLaden());
        rerender({ k: 'b' });
        expect(offen[1].signal.aborted).toBe(true);
        await act(async () => { offen[1].loesen(seite([2], 5)); offen[1].ablehnen(new Error('x')); });
        await act(async () => { offen[2].ablehnen(new Error('später')); });
        expect(result.current.eintraege).toEqual([]);
        expect(melde).toHaveBeenCalledTimes(1);
    });

    it('ignoriert Fehler abgebrochener Abfragen', async () => {
        const { lader, offen } = steuerbarerLader();
        const melde = vi.fn();
        const { result, rerender } = renderHook(({ k }) => useNachladeListe(k, lader, melde), { initialProps: { k: 'a' } });
        rerender({ k: 'b' });
        await act(async () => { offen[0].ablehnen(new Error('abgebrochen')); });
        await act(async () => { offen[1].loesen(seite([1], 5)); });
        act(() => result.current.mehrLaden());
        rerender({ k: 'c' });
        await act(async () => { offen[2].ablehnen(new Error('abgebrochen')); });
        expect(melde).not.toHaveBeenCalled();
        expect(result.current.fehler).toBeNull();
    });

    it('lädt nach dem Löschen die erste Seite neu und lässt die alte Liste bis zur Antwort stehen', async () => {
        const { lader, offen } = steuerbarerLader();
        const { result } = renderHook(() => useNachladeListe('a', lader, vi.fn()));
        await act(async () => { offen[0].loesen(seite([1, 2], 51)); });
        act(() => result.current.mehrLaden());
        await act(async () => { offen[1].loesen(seite([3], 51)); });
        // Eintrag 2 wurde gelöscht: neu laden statt lokal herausnehmen, damit die Seitenzählung stimmt.
        act(() => result.current.neuLaden());
        expect(result.current.eintraege.map((e) => e.id)).toEqual([1, 2, 3]);
        await waitFor(() => expect(offen).toHaveLength(3));
        expect(offen[2].seite).toBe(0);
        await act(async () => { offen[2].loesen(seite([1, 3], 50)); });
        expect(result.current.eintraege.map((e) => e.id)).toEqual([1, 3]);
        expect(result.current.gesamt).toBe(50);
        act(() => result.current.mehrLaden());
        expect(offen[3].seite).toBe(1);
    });
});
