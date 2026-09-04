import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useIdleTimer } from './useIdleTimer';

// Reine Untaetigkeits-Erkennung: keine Locks, keine Netzwerkaufrufe, keine UI.
// Deshalb kommen in diesen Tests auch keine personenbezogenen Daten vor
// (DSGVO-Vorgabe insofern nicht einschlaegig fuer diesen Baustein).

const FUENF_MINUTEN_MS = 300_000;
const SECHZIG_SEKUNDEN_MS = 60_000;

function neueCallbacks() {
    return { onWarn: vi.fn(), onIdle: vi.fn() };
}

describe('useIdleTimer', () => {
    beforeEach(() => {
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it('feuert onWarn bei 4:00 und onIdle bei 5:00 (Standardwerte)', () => {
        const { onWarn, onIdle } = neueCallbacks();
        const { result } = renderHook(() => useIdleTimer({ onWarn, onIdle, enabled: true }));

        expect(result.current.verbleibendeSekunden).toBeNull();

        act(() => {
            vi.advanceTimersByTime(FUENF_MINUTEN_MS - SECHZIG_SEKUNDEN_MS);
        });
        expect(onWarn).toHaveBeenCalledTimes(1);
        expect(onIdle).not.toHaveBeenCalled();
        expect(result.current.verbleibendeSekunden).toBe(60);

        act(() => {
            vi.advanceTimersByTime(SECHZIG_SEKUNDEN_MS);
        });
        expect(onIdle).toHaveBeenCalledTimes(1);
        expect(result.current.verbleibendeSekunden).toBeNull();
    });

    it('der Countdown zaehlt in der Vorwarnung sekundenweise herunter', () => {
        const { onWarn, onIdle } = neueCallbacks();
        const { result } = renderHook(() => useIdleTimer({ onWarn, onIdle, enabled: true }));

        act(() => {
            vi.advanceTimersByTime(FUENF_MINUTEN_MS - SECHZIG_SEKUNDEN_MS);
        });
        expect(result.current.verbleibendeSekunden).toBe(60);

        act(() => {
            vi.advanceTimersByTime(1_000);
        });
        expect(result.current.verbleibendeSekunden).toBe(59);

        act(() => {
            vi.advanceTimersByTime(5_000);
        });
        expect(result.current.verbleibendeSekunden).toBe(54);
    });

    it('Aktivitaet vor der Vorwarnung setzt den Countdown zurueck (kein onWarn/onIdle an der urspruenglichen Marke)', () => {
        const { onWarn, onIdle } = neueCallbacks();
        const { result } = renderHook(() => useIdleTimer({ onWarn, onIdle, enabled: true }));

        act(() => {
            vi.advanceTimersByTime(120_000); // 2:00 - deutlich vor der Vorwarnung.
        });
        act(() => {
            window.dispatchEvent(new Event('keydown'));
        });
        expect(result.current.verbleibendeSekunden).toBeNull();

        // Bis 1ms vor die URSPRUENGLICHE 4:00-Marke (ab Mount gerechnet) vorspulen.
        act(() => {
            vi.advanceTimersByTime(FUENF_MINUTEN_MS - SECHZIG_SEKUNDEN_MS - 1);
        });

        expect(onWarn).not.toHaveBeenCalled();
        expect(onIdle).not.toHaveBeenCalled();
    });

    it('Aktivitaet waehrend der Vorwarnung setzt den Countdown zurueck; onIdle bleibt an der urspruenglichen Marke aus, onWarn darf im neuen Zyklus erneut feuern', () => {
        const { onWarn, onIdle } = neueCallbacks();
        const { result } = renderHook(() => useIdleTimer({ onWarn, onIdle, enabled: true }));

        act(() => {
            vi.advanceTimersByTime(FUENF_MINUTEN_MS - SECHZIG_SEKUNDEN_MS);
        });
        expect(onWarn).toHaveBeenCalledTimes(1);
        expect(result.current.verbleibendeSekunden).toBe(60);

        act(() => {
            window.dispatchEvent(new Event('pointermove'));
        });
        expect(result.current.verbleibendeSekunden).toBeNull();

        // Die URSPRUENGLICHE 5:00-Marke liegt 60s nach dem Reset -> onIdle darf hier nicht feuern.
        act(() => {
            vi.advanceTimersByTime(SECHZIG_SEKUNDEN_MS);
        });
        expect(onIdle).not.toHaveBeenCalled();

        // Der NEUE Vorwarn-Zeitpunkt liegt 4:00 nach dem Reset, also weitere 3:00 ab jetzt.
        act(() => {
            vi.advanceTimersByTime(FUENF_MINUTEN_MS - SECHZIG_SEKUNDEN_MS - SECHZIG_SEKUNDEN_MS);
        });
        expect(onWarn).toHaveBeenCalledTimes(2);
        expect(onIdle).not.toHaveBeenCalled();
    });

    it.each([
        ['pointermove', () => window.dispatchEvent(new Event('pointermove'))],
        ['keydown', () => window.dispatchEvent(new Event('keydown'))],
        ['scroll', () => window.dispatchEvent(new Event('scroll'))],
        ['click', () => window.dispatchEvent(new Event('click'))],
        ['wheel', () => window.dispatchEvent(new Event('wheel'))],
        ['touchstart', () => window.dispatchEvent(new Event('touchstart'))],
        ['visibilitychange', () => document.dispatchEvent(new Event('visibilitychange'))],
    ] as const)('Aktivitaetsereignis %s setzt den Countdown zurueck', (_name, ausloesen) => {
        const { onWarn, onIdle } = neueCallbacks();
        renderHook(() => useIdleTimer({ onWarn, onIdle, enabled: true }));

        act(() => {
            vi.advanceTimersByTime(FUENF_MINUTEN_MS - 100);
        });
        act(() => {
            ausloesen();
        });
        act(() => {
            vi.advanceTimersByTime(200);
        });

        expect(onIdle).not.toHaveBeenCalled();
    });

    it('drosselt den Reset auf hoechstens 1x/Sekunde: 50 pointermove-Ereignisse in einer Sekunde loesen hoechstens ein State-Update aus', () => {
        const { onWarn, onIdle } = neueCallbacks();
        let renderCount = 0;
        const { result } = renderHook(() => {
            renderCount += 1;
            return useIdleTimer({ onWarn, onIdle, enabled: true });
        });

        // In die Vorwarnung vorspulen, damit ein Reset den Countdown WIRKLICH
        // aendert (60 -> null) - nur dann ist ein State-Update ueberhaupt zu erwarten.
        act(() => {
            vi.advanceTimersByTime(FUENF_MINUTEN_MS - SECHZIG_SEKUNDEN_MS);
        });
        expect(result.current.verbleibendeSekunden).toBe(60);

        const renderCountVorBurst = renderCount;

        // Jedes Ereignis einzeln in act() feuern, damit React nicht durch
        // automatisches Batching mehrere Zustandsaenderungen zu einem einzigen
        // Render zusammenfasst und den Drossel-Effekt so verdeckt.
        for (let i = 0; i < 50; i += 1) {
            act(() => {
                window.dispatchEvent(new Event('pointermove'));
            });
        }

        expect(renderCount - renderCountVorBurst).toBeLessThanOrEqual(1);
        expect(result.current.verbleibendeSekunden).toBeNull();
        expect(onIdle).not.toHaveBeenCalled();
    });

    it('enabled: false meldet keine Listener an window/document an und feuert keine Callbacks', () => {
        const addWindowSpy = vi.spyOn(window, 'addEventListener');
        const addDocumentSpy = vi.spyOn(document, 'addEventListener');
        const { onWarn, onIdle } = neueCallbacks();

        const { result } = renderHook(() => useIdleTimer({ onWarn, onIdle, enabled: false }));

        const beobachteteEreignisse = [
            'pointermove',
            'keydown',
            'scroll',
            'click',
            'wheel',
            'touchstart',
            'visibilitychange',
        ];
        const registrierteTypen = [
            ...addWindowSpy.mock.calls.map(call => call[0]),
            ...addDocumentSpy.mock.calls.map(call => call[0]),
        ];
        beobachteteEreignisse.forEach(typ => {
            expect(registrierteTypen).not.toContain(typ);
        });

        expect(result.current.verbleibendeSekunden).toBeNull();

        act(() => {
            vi.advanceTimersByTime(FUENF_MINUTEN_MS);
        });
        expect(onWarn).not.toHaveBeenCalled();
        expect(onIdle).not.toHaveBeenCalled();
    });

    it('raeumt beim Unmount alle Listener und Timer ab', () => {
        const removeWindowSpy = vi.spyOn(window, 'removeEventListener');
        const removeDocumentSpy = vi.spyOn(document, 'removeEventListener');
        const { onWarn, onIdle } = neueCallbacks();

        const { unmount } = renderHook(() => useIdleTimer({ onWarn, onIdle, enabled: true }));

        unmount();

        const erwarteteFensterEreignisse = ['pointermove', 'keydown', 'scroll', 'click', 'wheel', 'touchstart'];
        erwarteteFensterEreignisse.forEach(typ => {
            expect(removeWindowSpy.mock.calls.some(call => call[0] === typ)).toBe(true);
        });
        expect(removeDocumentSpy.mock.calls.some(call => call[0] === 'visibilitychange')).toBe(true);

        // Nach dem Unmount duerfen auch keine Timer mehr im Hintergrund laufen.
        act(() => {
            vi.advanceTimersByTime(FUENF_MINUTEN_MS);
        });
        expect(onWarn).not.toHaveBeenCalled();
        expect(onIdle).not.toHaveBeenCalled();
    });

    it('zuruecksetzen() setzt den Countdown manuell zurueck wie eine Aktivitaet', () => {
        const { onWarn, onIdle } = neueCallbacks();
        const { result } = renderHook(() => useIdleTimer({ onWarn, onIdle, enabled: true }));

        act(() => {
            vi.advanceTimersByTime(FUENF_MINUTEN_MS - SECHZIG_SEKUNDEN_MS);
        });
        expect(result.current.verbleibendeSekunden).toBe(60);

        act(() => {
            result.current.zuruecksetzen();
        });
        expect(result.current.verbleibendeSekunden).toBeNull();

        act(() => {
            vi.advanceTimersByTime(SECHZIG_SEKUNDEN_MS);
        });
        expect(onIdle).not.toHaveBeenCalled();
    });

    it('respektiert benutzerdefinierte timeoutMs/warnMs statt der Standardwerte', () => {
        const { onWarn, onIdle } = neueCallbacks();
        const { result } = renderHook(() =>
            useIdleTimer({ timeoutMs: 10_000, warnMs: 4_000, onWarn, onIdle, enabled: true })
        );

        act(() => {
            vi.advanceTimersByTime(6_000);
        });
        expect(onWarn).toHaveBeenCalledTimes(1);
        expect(onIdle).not.toHaveBeenCalled();
        expect(result.current.verbleibendeSekunden).toBe(4);

        act(() => {
            vi.advanceTimersByTime(4_000);
        });
        expect(onIdle).toHaveBeenCalledTimes(1);
    });
});
