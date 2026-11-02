import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useKiChatSpeicher } from './useKiChatSpeicher';

const speichere = (key: string, wert: unknown) => localStorage.setItem(key, JSON.stringify(wert));

describe('useKiChatSpeicher', () => {
    beforeEach(() => localStorage.clear());
    afterEach(() => { vi.useRealTimers(); localStorage.clear(); });

    it('liest und speichert den Chat je Benutzer', () => {
        speichere('frontendUserSelection', { id: 7 });
        speichere('ki-hilfe-messages-7', [{ role: 'user', text: 'Hallo' }]);
        speichere('ki-hilfe-open-7', true);
        const { result } = renderHook(() => useKiChatSpeicher());
        expect(result.current.userId).toBe(7);
        expect(result.current.open).toBe(true);
        expect(result.current.messages).toEqual([{ role: 'user', text: 'Hallo' }]);
        act(() => result.current.setMinimized(true));
        expect(localStorage.getItem('ki-hilfe-minimized-7')).toBe('true');
        act(() => result.current.leeren());
        expect(result.current.messages).toEqual([]);
        expect(localStorage.getItem('ki-hilfe-messages-7')).toBe('[]');
    });

    it('nimmt ohne oder mit kaputtem Benutzer und Speicher die Vorgaben', () => {
        localStorage.setItem('frontendUserSelection', '{kaputt');
        localStorage.setItem('ki-hilfe-messages', '{kaputt');
        const { result } = renderHook(() => useKiChatSpeicher());
        expect(result.current.userId).toBeNull();
        expect(result.current.messages).toEqual([]);
        expect(result.current.open).toBe(false);
    });

    it('ignoriert einen Benutzer ohne Zahlen-ID', () => {
        speichere('frontendUserSelection', { id: '7' });
        const { result } = renderHook(() => useKiChatSpeicher());
        expect(result.current.userId).toBeNull();
    });

    it('wechselt beim Benutzerwechsel in dessen Chat (anderer Tab und alle 2 s)', () => {
        vi.useFakeTimers();
        speichere('ki-hilfe-messages-2', [{ role: 'user', text: 'Von Erika' }]);
        const { result } = renderHook(() => useKiChatSpeicher());
        act(() => { vi.advanceTimersByTime(2000); });
        expect(result.current.userId).toBeNull();
        speichere('frontendUserSelection', { id: 2 });
        act(() => { window.dispatchEvent(new StorageEvent('storage', { key: 'frontendUserSelection' })); });
        expect(result.current.userId).toBe(2);
        expect(result.current.messages).toEqual([{ role: 'user', text: 'Von Erika' }]);
        speichere('frontendUserSelection', { id: 3 });
        act(() => { window.dispatchEvent(new StorageEvent('storage', { key: 'etwas-anderes' })); });
        expect(result.current.userId).toBe(2);
        act(() => { vi.advanceTimersByTime(2000); });
        expect(result.current.userId).toBe(3);
        expect(result.current.messages).toEqual([]);
    });

    it('gleicht andere Tabs ab und überspringt leere oder kaputte Werte', () => {
        const { result } = renderHook(() => useKiChatSpeicher());
        const tab = (key: string, newValue: string | null) => act(() => { window.dispatchEvent(new StorageEvent('storage', { key, newValue })); });
        tab('ki-hilfe-messages', JSON.stringify([{ role: 'assistant', text: 'Antwort' }]));
        tab('ki-hilfe-open', 'true');
        tab('ki-hilfe-minimized', 'true');
        tab('ki-hilfe-open', null);
        tab('ki-hilfe-open', '{kaputt');
        expect(result.current.messages).toEqual([{ role: 'assistant', text: 'Antwort' }]);
        expect(result.current.open).toBe(true);
        expect(result.current.minimized).toBe(true);
    });

    it('öffnet sich auf das Ereignis „ki-hilfe-open“', () => {
        speichere('ki-hilfe-minimized', true);
        const { result } = renderHook(() => useKiChatSpeicher());
        act(() => { window.dispatchEvent(new Event('ki-hilfe-open')); });
        expect(result.current.open).toBe(true);
        expect(result.current.minimized).toBe(false);
    });
});
