import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useEntprellterWert } from './useEntprellterWert';

describe('useEntprellterWert', () => {
    beforeEach(() => { vi.useFakeTimers(); });
    afterEach(() => { vi.useRealTimers(); });

    it('übernimmt den Wert erst nach der Ruhezeit und nur den letzten', () => {
        const { result, rerender } = renderHook(({ wert }) => useEntprellterWert(wert, 300), { initialProps: { wert: '' } });
        rerender({ wert: 'to' });
        act(() => { vi.advanceTimersByTime(200); });
        rerender({ wert: 'tor' });
        act(() => { vi.advanceTimersByTime(200); });
        expect(result.current).toBe('');
        act(() => { vi.advanceTimersByTime(100); });
        expect(result.current).toBe('tor');
    });
});
