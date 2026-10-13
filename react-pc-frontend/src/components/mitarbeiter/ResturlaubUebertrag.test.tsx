import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ResturlaubUebertrag } from './ResturlaubUebertrag';
import { ToastProvider } from '../ui/toast';

type Stand = { jahr: number; tage: number; berechnet: number; manuell: boolean; verfallsdatum: string | null };

function server(start: Stand, putAntwort?: { status: number; body: unknown }) {
    let stand = start;
    const puts: unknown[] = [];
    const fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (init?.method === 'PUT') {
            const body = JSON.parse(String(init.body));
            puts.push(body);
            if (putAntwort) return { ok: putAntwort.status < 400, status: putAntwort.status, json: async () => putAntwort.body };
            stand = body.tage === null
                ? { ...stand, tage: stand.berechnet, manuell: false }
                : { ...stand, tage: body.tage, manuell: true };
            return { ok: true, status: 200, json: async () => stand };
        }
        expect(url).toMatch(/^\/api\/urlaub\/uebertrag\?mitarbeiterId=1&jahr=\d{4}$/);
        return { ok: true, status: 200, json: async () => stand };
    });
    vi.stubGlobal('fetch', fetcher);
    return { puts, fetcher };
}

const zeige = () => render(<ToastProvider><ResturlaubUebertrag mitarbeiterId={1} /></ToastProvider>);

afterEach(() => vi.unstubAllGlobals());

describe('ResturlaubUebertrag', () => {
    it('zeigt den ausgerechneten Rest mit Verfallsdatum', async () => {
        server({ jahr: 2026, tage: 4.5, berechnet: 4.5, manuell: false, verfallsdatum: '2026-03-31' });
        zeige();
        expect(await screen.findByText(/Vom Programm ausgerechnet\. Verfällt, wenn nicht bis 31\.03\.2026 genommen\./)).toBeVisible();
        expect(screen.getByRole('textbox', { name: 'Resturlaub aus dem Vorjahr in Tagen' })).toHaveValue('4,5');
        expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled();
        expect(screen.queryByRole('button', { name: /Wieder ausrechnen lassen/ })).toBeNull();
    });

    it('speichert einen Handwert und kann ihn zurücknehmen', async () => {
        const user = userEvent.setup();
        const { puts } = server({ jahr: 2026, tage: 4, berechnet: 4, manuell: false, verfallsdatum: null });
        zeige();
        const feld = await screen.findByRole('textbox', { name: 'Resturlaub aus dem Vorjahr in Tagen' });

        await user.clear(feld);
        await user.type(feld, '2,5');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        expect(await screen.findByText(/Von Hand festgelegt\. Ausgerechnet wären 4 Tage\. Verfällt nicht\./)).toBeVisible();
        expect(puts[0]).toMatchObject({ mitarbeiterId: 1, tage: 2.5 });

        await user.click(screen.getByRole('button', { name: /Wieder ausrechnen lassen/ }));
        await waitFor(() => expect(puts[1]).toMatchObject({ mitarbeiterId: 1, tage: null }));
        expect(await screen.findByText(/Vom Programm ausgerechnet/)).toBeVisible();
    });

    it('lehnt Viertel-Tage ab, ohne zu speichern, und zeigt Serverfehler', async () => {
        const user = userEvent.setup();
        const { puts } = server({ jahr: 2026, tage: 0, berechnet: 0, manuell: false, verfallsdatum: '2026-03-31' },
            { status: 400, body: { message: 'Der Resturlaub muss zwischen 0 und 365 Tagen liegen.' } });
        zeige();
        const feld = await screen.findByRole('textbox', { name: 'Resturlaub aus dem Vorjahr in Tagen' });

        await user.clear(feld);
        await user.type(feld, '1,25');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByText('Bitte ganze oder halbe Tage eingeben (z. B. 3 oder 3,5).')).toBeVisible();
        expect(puts).toHaveLength(0);

        await user.clear(feld);
        await user.type(feld, '3');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByText('Der Resturlaub muss zwischen 0 und 365 Tagen liegen.')).toBeVisible();
    });

    it('zeigt einen Ladefehler bei unbrauchbarer Antwort', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => [] })));
        zeige();
        expect(await screen.findByRole('button', { name: 'Erneut laden' })).toBeVisible();
    });
});
