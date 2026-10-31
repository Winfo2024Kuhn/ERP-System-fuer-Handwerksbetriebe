/**
 * Shop-Besuch: Tab öffnen, Formular abschicken, nach dem Warenkorb fragen.
 * Nur Dummy-Daten.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { IdsApiFehler, type IdsVorgang } from './api';

const starteIdsVorgang = vi.fn();
const ladeIdsVorgang = vi.fn();
vi.mock('./api', async (original) => ({
    ...(await original<typeof import('./api')>()),
    starteIdsVorgang: (...a: unknown[]) => starteIdsVorgang(...a),
    ladeIdsVorgang: (...a: unknown[]) => ladeIdsVorgang(...a),
}));
const sendeShopFormular = vi.fn();
vi.mock('./shopFormular', () => ({
    sendeShopFormular: (...a: unknown[]) => sendeShopFormular(...a),
    zeigeWarteTextImTab: vi.fn(),
}));

import { ABFRAGE_ALLE_MS, HOECHSTDAUER_MS, IDS_KANAL, POPUP_BLOCKIERT, useIdsShopBesuch } from './useIdsShopBesuch';

const vorgang = (status: IdsVorgang['status'], teil: Partial<IdsVorgang> = {}): IdsVorgang => ({
    vorgangId: 42, lieferantId: 7, lieferantName: 'Muster GmbH', status, fehlertext: null,
    mitBestellung: false, empfangenAm: null, standardAufschlagProzent: 20, positionen: [], ...teil,
});

let tab: { close: ReturnType<typeof vi.fn> };

/** Lässt ausstehende Promises durchlaufen (bei Fake-Timern). */
const durchlaufen = () => act(async () => { await Promise.resolve(); await Promise.resolve(); });

beforeEach(() => {
    vi.useFakeTimers();
    tab = { close: vi.fn() };
    vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window);
    starteIdsVorgang.mockResolvedValue({ vorgangId: 42, formular: { action: 'https://shop.example.de', enctype: '', felder: { PW: 'geheim' } } });
});

afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    starteIdsVorgang.mockReset();
    ladeIdsVorgang.mockReset();
    sendeShopFormular.mockReset();
});

async function gestartet() {
    const hook = renderHook(() => useIdsShopBesuch());
    act(() => hook.result.current.starte(7));
    expect(hook.result.current.phase).toBe('startet');
    await durchlaufen();
    return hook;
}

describe('useIdsShopBesuch', () => {
    it('öffnet den Tab synchron, schickt das Formular ab und wartet', async () => {
        const { result } = await gestartet();
        expect(window.open).toHaveBeenCalledWith('about:blank', '_blank');
        expect(starteIdsVorgang).toHaveBeenCalledWith(7);
        expect(sendeShopFormular).toHaveBeenCalledWith(tab, expect.objectContaining({ felder: { PW: 'geheim' } }));
        expect(result.current.phase).toBe('wartet');
        // Das Formular mit Passwort steht nirgends im Zustand.
        expect(JSON.stringify(result.current)).not.toContain('geheim');
    });

    it('fragt alle 3 Sekunden nach und hört bei EMPFANGEN auf', async () => {
        const { result } = await gestartet();
        ladeIdsVorgang.mockResolvedValueOnce(vorgang('WARTET')).mockResolvedValueOnce(vorgang('EMPFANGEN', { positionen: [] }));
        await act(async () => { await vi.advanceTimersByTimeAsync(ABFRAGE_ALLE_MS); });
        expect(ladeIdsVorgang).toHaveBeenCalledTimes(1);
        expect(result.current.phase).toBe('wartet');
        await act(async () => { await vi.advanceTimersByTimeAsync(ABFRAGE_ALLE_MS); });
        expect(ladeIdsVorgang).toHaveBeenCalledTimes(2);
        expect(result.current.phase).toBe('empfangen');
        expect(result.current.vorgang?.vorgangId).toBe(42);
        await act(async () => { await vi.advanceTimersByTimeAsync(ABFRAGE_ALLE_MS * 3); });
        expect(ladeIdsVorgang).toHaveBeenCalledTimes(2);
    });

    it('meldet ABGELAUFEN und FEHLER mit dem Text des Servers', async () => {
        const erster = await gestartet();
        ladeIdsVorgang.mockResolvedValueOnce(vorgang('ABGELAUFEN'));
        await act(async () => { await vi.advanceTimersByTimeAsync(ABFRAGE_ALLE_MS); });
        expect(erster.result.current.phase).toBe('abgelaufen');
        expect(erster.result.current.fehlertext).toMatch(/60 Minuten/);

        const zweiter = await gestartet();
        ladeIdsVorgang.mockResolvedValueOnce(vorgang('FEHLER', { fehlertext: 'Der Shop hat Unsinn geschickt.' }));
        await act(async () => { await vi.advanceTimersByTimeAsync(ABFRAGE_ALLE_MS); });
        expect(zweiter.result.current.phase).toBe('fehler');
        expect(zweiter.result.current.fehlertext).toBe('Der Shop hat Unsinn geschickt.');
    });

    it('macht bei einem Netz-Aussetzer weiter, bricht bei 404 ab', async () => {
        const { result } = await gestartet();
        ladeIdsVorgang.mockRejectedValueOnce(new TypeError('Failed to fetch'))
            .mockRejectedValueOnce(new IdsApiFehler(404, 'Dieser Shop-Besuch ist nicht bekannt.'));
        await act(async () => { await vi.advanceTimersByTimeAsync(ABFRAGE_ALLE_MS); });
        expect(result.current.phase).toBe('wartet');
        await act(async () => { await vi.advanceTimersByTimeAsync(ABFRAGE_ALLE_MS); });
        expect(result.current.phase).toBe('fehler');
        expect(result.current.fehlertext).toBe('Dieser Shop-Besuch ist nicht bekannt.');
    });

    it('gibt nach 60 Minuten von selbst auf', async () => {
        const { result } = await gestartet();
        ladeIdsVorgang.mockResolvedValue(vorgang('WARTET'));
        await act(async () => { await vi.advanceTimersByTimeAsync(HOECHSTDAUER_MS + ABFRAGE_ALLE_MS); });
        expect(result.current.phase).toBe('abgelaufen');
        const anzahl = ladeIdsVorgang.mock.calls.length;
        await act(async () => { await vi.advanceTimersByTimeAsync(ABFRAGE_ALLE_MS * 3); });
        expect(ladeIdsVorgang.mock.calls.length).toBe(anzahl);
    });

    it('hört beim Abbrechen und beim Verlassen auf zu fragen', async () => {
        const { result, unmount } = await gestartet();
        ladeIdsVorgang.mockResolvedValue(vorgang('WARTET'));
        act(() => result.current.abbrechen());
        expect(result.current.phase).toBe('bereit');
        await act(async () => { await vi.advanceTimersByTimeAsync(ABFRAGE_ALLE_MS * 3); });
        expect(ladeIdsVorgang).not.toHaveBeenCalled();

        act(() => result.current.starte(7));
        await durchlaufen();
        unmount();
        await act(async () => { await vi.advanceTimersByTimeAsync(ABFRAGE_ALLE_MS * 3); });
        expect(ladeIdsVorgang).not.toHaveBeenCalled();
    });

    it('meldet einen blockierten Tab verständlich', () => {
        vi.mocked(window.open).mockReturnValue(null);
        const { result } = renderHook(() => useIdsShopBesuch());
        act(() => result.current.starte(7));
        expect(result.current.phase).toBe('fehler');
        expect(result.current.fehlertext).toBe(POPUP_BLOCKIERT);
        expect(starteIdsVorgang).not.toHaveBeenCalled();
    });

    it('schließt den Tab und zeigt die Meldung, wenn der Server ablehnt', async () => {
        starteIdsVorgang.mockRejectedValueOnce(new IdsApiFehler(400, 'Der Shop-Zugang ist nicht aktiv.'));
        const { result } = renderHook(() => useIdsShopBesuch());
        act(() => result.current.starte(7));
        await durchlaufen();
        expect(tab.close).toHaveBeenCalled();
        expect(result.current.phase).toBe('fehler');
        expect(result.current.fehlertext).toBe('Der Shop-Zugang ist nicht aktiv.');
    });

    it('schließt den Tab, wenn inzwischen abgebrochen wurde', async () => {
        const { result } = renderHook(() => useIdsShopBesuch());
        act(() => result.current.starte(7));
        act(() => result.current.abbrechen());
        await durchlaufen();
        expect(tab.close).toHaveBeenCalled();
        expect(sendeShopFormular).not.toHaveBeenCalled();
        expect(result.current.phase).toBe('bereit');
    });

    it('fragt sofort nach, wenn die Rücksprung-Seite sich meldet', async () => {
        vi.useRealTimers();
        const { result } = renderHook(() => useIdsShopBesuch());
        act(() => result.current.starte(7));
        await act(async () => { await new Promise((r) => setTimeout(r, 0)); });
        expect(result.current.phase).toBe('wartet');
        ladeIdsVorgang.mockResolvedValue(vorgang('EMPFANGEN'));
        const sender = new BroadcastChannel(IDS_KANAL);
        sender.postMessage({ vorgangId: 99 });
        sender.postMessage({ vorgangId: 42 });
        await vi.waitFor(() => expect(result.current.phase).toBe('empfangen'));
        expect(ladeIdsVorgang).toHaveBeenCalledTimes(1);
        sender.close();
        act(() => result.current.abbrechen());
    });
});
