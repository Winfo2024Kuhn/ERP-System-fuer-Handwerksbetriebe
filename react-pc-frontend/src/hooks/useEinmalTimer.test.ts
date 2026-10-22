import { renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useEinmalTimer } from './useEinmalTimer';

describe('useEinmalTimer', () => {
    beforeEach(() => {
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it('führt die Aktion nach Ablauf genau einmal aus', () => {
        const aktion = vi.fn();
        const { result } = renderHook(() => useEinmalTimer());

        result.current.starte(aktion, 2000);
        vi.advanceTimersByTime(1999);
        expect(aktion).not.toHaveBeenCalled();

        vi.advanceTimersByTime(1);
        expect(aktion).toHaveBeenCalledTimes(1);
        expect(vi.getTimerCount()).toBe(0);
    });

    it('ersetzt einen noch laufenden Timer beim erneuten Starten', () => {
        const erste = vi.fn();
        const zweite = vi.fn();
        const { result } = renderHook(() => useEinmalTimer());

        result.current.starte(erste, 2000);
        vi.advanceTimersByTime(1500);
        result.current.starte(zweite, 2000);
        vi.advanceTimersByTime(2000);

        expect(erste).not.toHaveBeenCalled();
        expect(zweite).toHaveBeenCalledTimes(1);
    });

    it('bricht mit stoppe() ab und verträgt stoppe() ohne laufenden Timer', () => {
        const aktion = vi.fn();
        const { result } = renderHook(() => useEinmalTimer());

        result.current.stoppe();
        result.current.starte(aktion, 2000);
        result.current.stoppe();
        vi.advanceTimersByTime(2000);

        expect(aktion).not.toHaveBeenCalled();
        expect(vi.getTimerCount()).toBe(0);
    });

    it('räumt den Timer beim Unmount ab', () => {
        const aktion = vi.fn();
        const { result, unmount } = renderHook(() => useEinmalTimer());

        result.current.starte(aktion, 2000);
        unmount();

        expect(vi.getTimerCount()).toBe(0);
        vi.advanceTimersByTime(2000);
        expect(aktion).not.toHaveBeenCalled();
    });

    it('plant nach dem Unmount keinen Timer mehr ein (z. B. Speichern endet nach dem Schließen)', () => {
        const aktion = vi.fn();
        const { result, unmount } = renderHook(() => useEinmalTimer());

        unmount();
        result.current.starte(aktion, 2000);

        expect(vi.getTimerCount()).toBe(0);
        vi.advanceTimersByTime(2000);
        expect(aktion).not.toHaveBeenCalled();
    });

    it('funktioniert nach dem StrictMode-Doppelmount weiter', () => {
        const aktion = vi.fn();
        const { result } = renderHook(() => useEinmalTimer(), { reactStrictMode: true });

        result.current.starte(aktion, 2000);
        vi.advanceTimersByTime(2000);

        expect(aktion).toHaveBeenCalledTimes(1);
    });

    it('liefert über Re-Renders dasselbe Objekt', () => {
        const { result, rerender } = renderHook(() => useEinmalTimer());
        const vorher = result.current;

        rerender();

        expect(result.current).toBe(vorher);
    });
});
