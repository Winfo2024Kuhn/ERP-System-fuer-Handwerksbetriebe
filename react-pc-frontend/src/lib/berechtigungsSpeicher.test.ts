import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { erzeugeBerechtigungsSpeicher } from './berechtigungsSpeicher';

describe('erzeugeBerechtigungsSpeicher', () => {
    it('lädt einmal für alle Stellen, startet mit null und merkt sich das Ergebnis', async () => {
        const lade = vi.fn().mockResolvedValue(true);
        const { useBerechtigung } = erzeugeBerechtigungsSpeicher(lade);
        const a = renderHook(() => useBerechtigung());
        const b = renderHook(() => useBerechtigung());
        expect(a.result.current).toBeNull();
        await waitFor(() => expect(a.result.current).toBe(true));
        expect(b.result.current).toBe(true);
        expect(lade).toHaveBeenCalledTimes(1);
    });

    it('wertet einen Ladefehler als "kein Recht"', async () => {
        const { useBerechtigung } = erzeugeBerechtigungsSpeicher(() => Promise.reject(new Error('Netz weg')));
        const { result } = renderHook(() => useBerechtigung());
        await waitFor(() => expect(result.current).toBe(false));
    });

    it('versucht es nach einem Ladefehler beim nächsten Einbinden erneut', async () => {
        const lade = vi.fn()
            .mockRejectedValueOnce(new Error('Netz weg'))
            .mockResolvedValueOnce(true);
        const { useBerechtigung } = erzeugeBerechtigungsSpeicher(lade);
        const erste = renderHook(() => useBerechtigung());
        await waitFor(() => expect(erste.result.current).toBe(false));

        const zweite = renderHook(() => useBerechtigung());

        await waitFor(() => expect(zweite.result.current).toBe(true));
        expect(lade).toHaveBeenCalledTimes(2);
    });

    it('lädt nach einem echten "kein Recht" nicht bei jedem Einbinden neu', async () => {
        const lade = vi.fn().mockResolvedValue(false);
        const { useBerechtigung } = erzeugeBerechtigungsSpeicher(lade);
        const erste = renderHook(() => useBerechtigung());
        await waitFor(() => expect(erste.result.current).toBe(false));

        renderHook(() => useBerechtigung());

        expect(lade).toHaveBeenCalledTimes(1);
    });

    it('verwirft nach dem Zurücksetzen die Antwort der noch laufenden alten Anfrage', async () => {
        let antwortAlt: (darf: boolean) => void = () => undefined;
        const lade = vi.fn()
            .mockImplementationOnce(() => new Promise<boolean>((ok) => { antwortAlt = ok; }))
            .mockResolvedValueOnce(false);
        const { useBerechtigung, setzeZurueck } = erzeugeBerechtigungsSpeicher(lade);
        renderHook(() => useBerechtigung());
        await waitFor(() => expect(lade).toHaveBeenCalledTimes(1));
        act(() => setzeZurueck()); // z. B. Benutzerwechsel, während die erste Anfrage noch läuft
        const neu = renderHook(() => useBerechtigung());
        await waitFor(() => expect(neu.result.current).toBe(false));
        await act(async () => antwortAlt(true)); // veraltete Antwort des vorigen Benutzers
        expect(neu.result.current).toBe(false);
        expect(lade).toHaveBeenCalledTimes(2);
    });
});
