import { afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ToastProvider } from '../../../ui/toast';
import { ZugangVonUnterwegsKarte } from './ZugangVonUnterwegsKarte';

interface Stand {
    funnelFreigegeben: boolean;
    letzteFunnelAnfrage: string | null;
    letzteFunnelAnfrageAbgewiesen: boolean;
}

function stubFetch(stand: Stand) {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
        const body = init?.method === 'PUT'
            ? { ...stand, ...JSON.parse(String(init.body)) }
            : stand;
        return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

function renderKarte() {
    return render(
        <ToastProvider>
            <ZugangVonUnterwegsKarte />
        </ToastProvider>,
    );
}

describe('ZugangVonUnterwegsKarte', () => {
    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('zeigt ohne Freigabe die Tailscale-App als gewählt', async () => {
        stubFetch({ funnelFreigegeben: false, letzteFunnelAnfrage: null, letzteFunnelAnfrageAbgewiesen: false });
        renderKarte();

        expect(await screen.findByRole('radio', { name: /Tailscale-App/ })).toBeChecked();
        expect(screen.getByRole('radio', { name: /Funnel freigeben/ })).not.toBeChecked();
        expect(screen.getByText(/keine seit dem Start/)).toBeInTheDocument();
        // Nichts geändert – nichts zu speichern.
        expect(screen.getByRole('button', { name: 'Zugang von unterwegs speichern' })).toBeDisabled();
    });

    it('gibt den Funnel frei und zeigt die Schritte am Server', async () => {
        const fetchMock = stubFetch({ funnelFreigegeben: false, letzteFunnelAnfrage: null, letzteFunnelAnfrageAbgewiesen: false });
        renderKarte();

        fireEvent.click(await screen.findByRole('radio', { name: /Funnel freigeben/ }));
        expect(screen.getByText('tailscale funnel --bg 8080')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Zugang von unterwegs speichern' }));

        await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
            '/api/settings/zeiterfassung-zugang',
            expect.objectContaining({ method: 'PUT', body: JSON.stringify({ funnelFreigegeben: true }) }),
        ));
        await waitFor(() =>
            expect(screen.getByRole('button', { name: 'Zugang von unterwegs speichern' })).toBeDisabled());
    });

    it('warnt, wenn am Server ein nicht freigegebener Funnel läuft', async () => {
        stubFetch({
            funnelFreigegeben: false,
            letzteFunnelAnfrage: '2026-10-10T20:15:00Z',
            letzteFunnelAnfrageAbgewiesen: true,
        });
        renderKarte();

        expect(await screen.findByText(/Am Server läuft ein Funnel, der hier nicht freigegeben ist/)).toBeInTheDocument();
        expect(screen.getByText(/abgewiesen/)).toBeInTheDocument();
    });
});
