import { act, render, renderHook, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { useGemerkterWert } from './useGemerkterWert';

const STANDARD = { nurBilder: false };
const istFilter = (wert: unknown): wert is typeof STANDARD =>
    !!wert && typeof wert === 'object' && typeof (wert as typeof STANDARD).nurBilder === 'boolean';

function Anzeige({ name }: { name: string }) {
    const [wert, setWert] = useGemerkterWert('test-filter', STANDARD, istFilter);
    return (
        <button type="button" aria-pressed={wert.nurBilder} onClick={() => setWert({ nurBilder: !wert.nurBilder })}>
            {name}
        </button>
    );
}

describe('useGemerkterWert', () => {
    it('beginnt mit dem Standard und merkt sich Änderungen im Browser', () => {
        const { result } = renderHook(() => useGemerkterWert('test-filter', STANDARD, istFilter));
        expect(result.current[0]).toEqual({ nurBilder: false });
        act(() => result.current[1]({ nurBilder: true }));
        expect(result.current[0]).toEqual({ nurBilder: true });
        expect(localStorage.getItem('erp.gemerkt.test-filter')).toBe('{"nurBilder":true}');
    });

    it('liest den gemerkten Wert beim nächsten Öffnen wieder', () => {
        localStorage.setItem('erp.gemerkt.test-filter', '{"nurBilder":true}');
        const { result } = renderHook(() => useGemerkterWert('test-filter', STANDARD, istFilter));
        expect(result.current[0]).toEqual({ nurBilder: true });
    });

    it('verwirft kaputte oder unpassende Einträge', () => {
        localStorage.setItem('erp.gemerkt.test-filter', '{kaputt');
        expect(renderHook(() => useGemerkterWert('test-filter', STANDARD, istFilter)).result.current[0]).toEqual(STANDARD);
        localStorage.setItem('erp.gemerkt.test-filter', '{"nurBilder":"ja"}');
        expect(renderHook(() => useGemerkterWert('test-filter', STANDARD, istFilter)).result.current[0]).toEqual(STANDARD);
        // Ohne Prüfung wird übernommen, was gespeichert ist.
        localStorage.setItem('erp.gemerkt.ohne', '42');
        expect(renderHook(() => useGemerkterWert('ohne', 0)).result.current[0]).toBe(42);
    });

    it('gilt seitenweit: alle Stellen mit demselben Schlüssel ziehen sofort mit', async () => {
        const user = userEvent.setup();
        render(<><Anzeige name="Erste" /><Anzeige name="Zweite" /></>);
        await user.click(screen.getByRole('button', { name: 'Erste' }));
        expect(screen.getByRole('button', { name: 'Zweite' })).toHaveAttribute('aria-pressed', 'true');
    });

    it('zieht bei Änderungen aus einem anderen Tab mit und ignoriert fremde Schlüssel', () => {
        const { result } = renderHook(() => useGemerkterWert('test-filter', STANDARD, istFilter));
        localStorage.setItem('erp.gemerkt.test-filter', '{"nurBilder":true}');
        act(() => { window.dispatchEvent(new StorageEvent('storage', { key: 'erp.gemerkt.anderes' })); });
        expect(result.current[0]).toEqual(STANDARD);
        act(() => { window.dispatchEvent(new StorageEvent('storage', { key: 'erp.gemerkt.test-filter' })); });
        expect(result.current[0]).toEqual({ nurBilder: true });
    });

    it('arbeitet weiter, wenn der Browser-Speicher gesperrt ist', () => {
        const lesen = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('gesperrt'); });
        const schreiben = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('gesperrt'); });
        try {
            const { result } = renderHook(() => useGemerkterWert('test-filter', STANDARD, istFilter));
            expect(result.current[0]).toEqual(STANDARD);
            act(() => result.current[1]({ nurBilder: true }));
            expect(result.current[0]).toEqual({ nurBilder: true });
        } finally {
            lesen.mockRestore();
            schreiben.mockRestore();
        }
    });
});
