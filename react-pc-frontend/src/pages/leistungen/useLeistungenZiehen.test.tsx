import { act, renderHook, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { speichereLeistung, verschiebeOrdner } from './api';
import { testBaum, testLeistung } from './testDaten';
import { useLeistungenZiehen } from './useLeistungenZiehen';

vi.mock('./api', () => ({ verschiebeOrdner: vi.fn(), speichereLeistung: vi.fn() }));

const mitToasts = ({ children }: { children: ReactNode }) => <ToastProvider>{children}</ToastProvider>;

function zeige() {
    const neuLaden = vi.fn(async () => {});
    const aufklappen = vi.fn();
    const uebernimmLeistung = vi.fn();
    const { baum, zaehler, leistungen } = testBaum();
    const hook = renderHook(() => useLeistungenZiehen({ baum, zaehler, leistungen, neuLaden, aufklappen, uebernimmLeistung }), { wrapper: mitToasts });
    return { ...hook, neuLaden, aufklappen, uebernimmLeistung };
}

describe('useLeistungenZiehen', () => {
    beforeEach(() => {
        vi.mocked(verschiebeOrdner).mockReset().mockResolvedValue(undefined);
        vi.mocked(speichereLeistung).mockReset().mockImplementation(async (l) => ({ ...testLeistung(11, 'Glasbrüstung', null), ...l, id: 11 }));
    });

    it('ohne Ziehvorgang nimmt kein Ziel etwas an', () => {
        const { result } = zeige();
        expect(result.current.ablageErlaubt(1)).toBe(false);
    });

    it('prüft Ablageziele für Ordner und Leistungen', () => {
        const { result } = zeige();
        act(() => result.current.setZiehen({ art: 'ordner', id: 4 }));
        expect(result.current.ablageErlaubt(1)).toBe(true);
        expect(result.current.ablageErlaubt(4)).toBe(false); // in sich selbst
        expect(result.current.ablageErlaubt(2)).toBe(false); // Ordner mit Leistungen

        act(() => result.current.setZiehen({ art: 'leistung', id: 11 }));
        expect(result.current.ablageErlaubt(4)).toBe(true);
        expect(result.current.ablageErlaubt(1)).toBe(false); // hat Unterordner
        expect(result.current.ablageErlaubt(null)).toBe(false); // Leistungen nie oberste Ebene
    });

    it('Ordner ablegen: verschiebt, lädt neu, klappt das Ziel auf und bietet „Rückgängig“', async () => {
        const { result, neuLaden, aufklappen } = zeige();
        act(() => result.current.setZiehen({ art: 'ordner', id: 4 }));
        act(() => result.current.ablegen(1));
        expect(result.current.ziehen).toBeNull();
        expect(await screen.findByText('„Holzbau“ liegt jetzt in „Metallbau“.')).toBeInTheDocument();
        expect(verschiebeOrdner).toHaveBeenCalledWith(4, 1);
        expect(neuLaden).toHaveBeenCalled();
        expect(aufklappen).toHaveBeenCalledWith(1, true);

        await userEvent.click(screen.getByRole('button', { name: 'Rückgängig' }));
        expect(await screen.findByText('„Holzbau“ liegt jetzt in der obersten Ebene.')).toBeInTheDocument();
        expect(verschiebeOrdner).toHaveBeenLastCalledWith(4, null);
        expect(screen.queryByRole('button', { name: 'Rückgängig' })).not.toBeInTheDocument();
    });

    it('Leistung verschieben: speichert mit neuem Ordner, übernimmt sie und bietet „Rückgängig“', async () => {
        const { result, uebernimmLeistung } = zeige();
        await act(() => result.current.leistungVerschieben(testLeistung(11, 'Glasbrüstung', 2), 4));
        expect(speichereLeistung).toHaveBeenCalledWith(expect.objectContaining({ id: 11, ordnerId: 4 }));
        expect(uebernimmLeistung).toHaveBeenCalledWith(expect.objectContaining({ id: 11, ordnerId: 4 }));
        expect(await screen.findByText('„Glasbrüstung“ liegt jetzt in „Holzbau“.')).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Rückgängig' }));
        expect(speichereLeistung).toHaveBeenLastCalledWith(expect.objectContaining({ id: 11, ordnerId: 2 }));
    });

    it('meldet einen Fehler beim Ablegen als Toast', async () => {
        vi.mocked(verschiebeOrdner).mockRejectedValue(new Error('Server nicht erreichbar.'));
        const { result } = zeige();
        act(() => result.current.setZiehen({ art: 'ordner', id: 4 }));
        act(() => result.current.ablegen(1));
        expect(await screen.findByText('Server nicht erreichbar.')).toBeInTheDocument();
    });
});
