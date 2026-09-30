import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { KUNDE_MAX } from './telefonTestdaten';
import type { LiveAnruf } from './types';
import { useTelefonLive, VERPASST_ANZEIGEDAUER_MS } from './useTelefonLive';

/** Nachgebaute EventSource: Tests schicken Ereignisse selbst hinein. */
class FakeEventSource {
    static alle: FakeEventSource[] = [];
    url: string;
    readyState = 0;
    onopen: (() => void) | null = null;
    onerror: (() => void) | null = null;
    geschlossen = false;
    private hoerer = new Map<string, Set<(e: MessageEvent) => void>>();

    constructor(url: string) {
        this.url = url;
        FakeEventSource.alle.push(this);
    }
    addEventListener(typ: string, fn: (e: MessageEvent) => void) {
        if (!this.hoerer.has(typ)) this.hoerer.set(typ, new Set());
        this.hoerer.get(typ)!.add(fn);
    }
    removeEventListener(typ: string, fn: (e: MessageEvent) => void) {
        this.hoerer.get(typ)?.delete(fn);
    }
    close() {
        this.geschlossen = true;
        this.readyState = 2;
    }
    sende(anruf: Partial<LiveAnruf>) {
        const daten = { verbindungsId: '1', status: 'KLINGELT', nummer: '0931 1234567', kontakt: null, kandidaten: [], angenommen: false, ...anruf };
        this.hoerer.get('anruf')?.forEach((fn) => fn(new MessageEvent('anruf', { data: JSON.stringify(daten) })));
    }
    brichAb() {
        this.readyState = 2;
        this.onerror?.();
    }
}

const fabrik = (url: string) => new FakeEventSource(url) as unknown as EventSource;

describe('useTelefonLive', () => {
    beforeEach(() => {
        FakeEventSource.alle = [];
        vi.useFakeTimers();
    });
    afterEach(() => {
        vi.useRealTimers();
    });

    it('öffnet ohne Recht keine Verbindung', () => {
        renderHook(() => useTelefonLive(false, fabrik));
        expect(FakeEventSource.alle).toHaveLength(0);
    });

    it('zeigt klingelnde Anrufe, neuester zuerst, und schließt beim Aushängen', () => {
        const { result, unmount } = renderHook(() => useTelefonLive(true, fabrik));
        const quelle = FakeEventSource.alle[0];
        expect(quelle.url).toBe('/api/telefon/live');

        act(() => quelle.sende({ verbindungsId: 'a', kontakt: KUNDE_MAX }));
        act(() => { vi.advanceTimersByTime(10); });
        act(() => quelle.sende({ verbindungsId: 'b', nummer: '0931 9999999' }));
        expect(result.current.anrufe.map((a) => a.verbindungsId)).toEqual(['b', 'a']);
        expect(result.current.anrufe[1].kontakt?.name).toBe('Max Mustermann');

        unmount();
        expect(quelle.geschlossen).toBe(true);
    });

    it('merkt sich den Gesprächsbeginn und entfernt angenommene Anrufe beim Auflegen sofort', () => {
        const refresh = vi.fn();
        window.addEventListener('notifications:refresh', refresh);
        const { result } = renderHook(() => useTelefonLive(true, fabrik));
        const quelle = FakeEventSource.alle[0];

        act(() => quelle.sende({ verbindungsId: 'a' }));
        act(() => quelle.sende({ verbindungsId: 'a', status: 'IM_GESPRAECH', angenommen: true }));
        expect(result.current.anrufe[0].gespraechSeit).not.toBeNull();

        act(() => quelle.sende({ verbindungsId: 'a', status: 'BEENDET', angenommen: true }));
        expect(result.current.anrufe).toHaveLength(0);
        expect(refresh).toHaveBeenCalledTimes(1);
        window.removeEventListener('notifications:refresh', refresh);
    });

    it('zeigt nicht angenommene Anrufe drei Sekunden als verpasst', () => {
        const { result } = renderHook(() => useTelefonLive(true, fabrik));
        const quelle = FakeEventSource.alle[0];
        act(() => quelle.sende({ verbindungsId: 'a' }));
        act(() => quelle.sende({ verbindungsId: 'a', status: 'BEENDET', angenommen: false }));
        expect(result.current.anrufe[0].verpasst).toBe(true);

        act(() => { vi.advanceTimersByTime(VERPASST_ANZEIGEDAUER_MS); });
        expect(result.current.anrufe).toHaveLength(0);
    });

    it('hält einen verpassten Anruf fest, bis er geschlossen wird (z. B. zum Zurückrufen)', () => {
        const { result } = renderHook(() => useTelefonLive(true, fabrik));
        const quelle = FakeEventSource.alle[0];
        act(() => quelle.sende({ verbindungsId: 'a' }));
        act(() => quelle.sende({ verbindungsId: 'a', status: 'BEENDET', angenommen: false }));
        act(() => result.current.festhalten('a'));

        act(() => { vi.advanceTimersByTime(VERPASST_ANZEIGEDAUER_MS * 10); });
        expect(result.current.anrufe).toHaveLength(1);
        expect(result.current.anrufe[0].verpasst).toBe(true);

        act(() => result.current.schliessen('a'));
        expect(result.current.anrufe).toHaveLength(0);
    });

    it('festhalten wirkt nur auf verpasste Anrufe, ein späteres Auflegen startet die Anzeigedauer normal', () => {
        const { result } = renderHook(() => useTelefonLive(true, fabrik));
        const quelle = FakeEventSource.alle[0];
        act(() => quelle.sende({ verbindungsId: 'a' }));
        act(() => result.current.festhalten('a'));
        act(() => quelle.sende({ verbindungsId: 'a', status: 'BEENDET', angenommen: false }));
        act(() => { vi.advanceTimersByTime(VERPASST_ANZEIGEDAUER_MS); });
        expect(result.current.anrufe).toHaveLength(0);
    });

    it('holt einen geschlossenen Anruf bei weiteren Ereignissen nicht zurück', () => {
        const { result } = renderHook(() => useTelefonLive(true, fabrik));
        const quelle = FakeEventSource.alle[0];
        act(() => quelle.sende({ verbindungsId: 'a' }));
        act(() => result.current.schliessen('a'));
        expect(result.current.anrufe).toHaveLength(0);
        act(() => quelle.sende({ verbindungsId: 'a', status: 'IM_GESPRAECH' }));
        expect(result.current.anrufe).toHaveLength(0);
    });

    it('ignoriert kaputte Ereignisse', () => {
        const { result } = renderHook(() => useTelefonLive(true, fabrik));
        const quelle = FakeEventSource.alle[0];
        act(() => {
            (quelle as unknown as { hoerer: Map<string, Set<(e: MessageEvent) => void>> }).hoerer.get('anruf')
                ?.forEach((fn) => fn(new MessageEvent('anruf', { data: 'kein json' })));
        });
        expect(result.current.anrufe).toHaveLength(0);
    });

    it('verbindet sich nach einem endgültigen Abbruch mit Pause neu', () => {
        renderHook(() => useTelefonLive(true, fabrik));
        const erste = FakeEventSource.alle[0];
        act(() => erste.brichAb());
        expect(erste.geschlossen).toBe(true);
        expect(FakeEventSource.alle).toHaveLength(1);

        act(() => { vi.advanceTimersByTime(3000); });
        expect(FakeEventSource.alle).toHaveLength(2);
        expect(FakeEventSource.alle[1].geschlossen).toBe(false);
    });

    it('lässt den Browser selbst neu verbinden, solange er es noch versucht', () => {
        renderHook(() => useTelefonLive(true, fabrik));
        const erste = FakeEventSource.alle[0];
        erste.readyState = 0; // CONNECTING
        act(() => erste.onerror?.());
        act(() => { vi.advanceTimersByTime(60_000); });
        expect(FakeEventSource.alle).toHaveLength(1);
        expect(erste.geschlossen).toBe(false);
    });
});
