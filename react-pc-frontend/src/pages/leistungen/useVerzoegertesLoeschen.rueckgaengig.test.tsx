import { act, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { RUECKGAENGIG_MS, useVerzoegertesLoeschen } from './useVerzoegertesLoeschen';

// Die echte Meldung schließt sich beim Senden; um einen verspäteten Klick auf „Rückgängig“
// (Wettlauf zwischen Ablauf und Klick) zu prüfen, wird die Toast-API hier nachgebaut.
const toastApi = vi.hoisted(() => ({
    success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn(), schliessen: vi.fn(),
}));
vi.mock('../../components/ui/toast', () => ({ useToast: () => toastApi }));

function Probe() {
    const { planen } = useVerzoegertesLoeschen();
    return <button onClick={() => planen({
        schluessel: 'leistung:5', url: '/api/leistungen/5', name: 'Glasbrüstung', fehlerText: 'x', onGeloescht: vi.fn(),
    })}>Löschen</button>;
}

describe('„Rückgängig“ nach Ablauf der Frist', () => {
    afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); });

    it('sagt, dass schon gelöscht wurde, statt still nichts zu tun', async () => {
        vi.useFakeTimers();
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, status: 204, json: () => Promise.resolve({}) }));
        render(<Probe />);
        act(() => screen.getByText('Löschen').click());
        const [, optionen] = toastApi.success.mock.calls[0] as unknown as [string, { aktion: { onClick: () => void }; schluessel: string }];
        expect(optionen.schluessel).toBe('leistung:5');
        await act(async () => { await vi.advanceTimersByTimeAsync(RUECKGAENGIG_MS); });
        expect(toastApi.schliessen).toHaveBeenCalledWith('leistung:5');
        act(() => optionen.aktion.onClick());
        expect(toastApi.warning).toHaveBeenCalledWith('„Glasbrüstung“ war schon gelöscht.');
    });
});
