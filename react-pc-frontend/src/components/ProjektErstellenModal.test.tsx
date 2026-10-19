import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ProjektErstellenModal } from './ProjektErstellenModal';

/** DSGVO: nur Fantasie-Daten. */
const anfrage = {
    id: 42,
    anfragesnummer: 'A-2026-007',
    bauvorhaben: 'Geländer Musterstraße',
    kundenId: 7,
    kundenName: 'Max Mustermann',
    kundennummer: '10007',
    projektStrasse: 'Musterstraße 1',
    projektPlz: '12345',
    projektOrt: 'Musterstadt',
};

const kunde = { id: 8, name: 'Erika Musterfrau', kundennummer: '10008', kundenEmails: [] };

function antwort(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function postBody(): Record<string, unknown> {
    const aufruf = vi.mocked(fetch).mock.calls.find(([url, init]) =>
        String(url) === '/api/projekte' && (init as RequestInit | undefined)?.method === 'POST');
    expect(aufruf).toBeDefined();
    return JSON.parse((aufruf![1] as RequestInit).body as string);
}

function zeige() {
    const onSuccess = vi.fn();
    const onClose = vi.fn();
    render(<ProjektErstellenModal isOpen onClose={onClose} onSuccess={onSuccess} />);
    return { onSuccess, onClose };
}

describe('ProjektErstellenModal – Anfrage übernehmen', () => {
    beforeEach(() => {
        vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
            const url = String(input);
            if (url.startsWith('/api/anfragen?')) return antwort([anfrage]);
            if (url.includes('/produktkategorien-vorschlag')) return antwort([]);
            if (url === '/api/kunden/7') return antwort({ id: 7, name: 'Max Mustermann', kundennummer: '10007', kundenEmails: [] });
            if (url.startsWith('/api/kunden?')) return antwort([kunde]);
            if (url.startsWith('/api/projekte/naechste-auftragsnummer')) return antwort({ prefix: '2026/10/', zaehler: 1 });
            if (url.startsWith('/api/projekte/auftragsnummer-verfuegbar')) return antwort({ verfuegbar: true });
            if (url === '/api/projekte' && init?.method === 'POST') return antwort({ id: 99 });
            return antwort({}, 404);
        }));
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('zeigt beim Übernehmen den Hinweis, dass die Anfrage in das Projekt übergeht', async () => {
        zeige();
        await userEvent.click(await screen.findByText('Geländer Musterstraße'));

        expect(screen.getByText(/Nach dem Anlegen gehört die Anfrage zum Projekt/)).toBeInTheDocument();
    });

    it('schickt die gewählte Anfrage beim Übernehmen mit', async () => {
        const { onSuccess } = zeige();
        await userEvent.click(await screen.findByText('Geländer Musterstraße'));
        await screen.findByText('(10007)');

        await userEvent.click(screen.getByRole('button', { name: 'Projekt anlegen' }));

        await waitFor(() => expect(onSuccess).toHaveBeenCalledWith(99));
        expect(postBody().anfrageIds).toEqual([42]);
    });

    it('übernimmt die Anfrage nicht mehr, wenn danach auf „Ohne Anfrage anlegen“ gewechselt wird', async () => {
        const { onSuccess } = zeige();
        await userEvent.click(await screen.findByText('Geländer Musterstraße'));
        await screen.findByText('(10007)');

        await userEvent.click(screen.getByRole('button', { name: /Ohne Anfrage anlegen/ }));

        // Alles aus der Anfrage ist wieder leer: kein Hinweis, kein Kunde, kein Bauvorhaben.
        expect(screen.queryByText(/Basierend auf Anfrage/)).not.toBeInTheDocument();
        expect(screen.getByText('Kein Kunde ausgewählt')).toBeInTheDocument();
        expect(screen.getByPlaceholderText('z.B. Neubau Einfamilienhaus')).toHaveValue('');

        await userEvent.type(screen.getByPlaceholderText('z.B. Neubau Einfamilienhaus'), 'Carport Musterweg');
        await userEvent.click(screen.getByRole('button', { name: /Kunde auswählen/ }));
        await userEvent.click(await screen.findByText('Erika Musterfrau'));
        await userEvent.click(screen.getByRole('button', { name: 'Projekt anlegen' }));

        await waitFor(() => expect(onSuccess).toHaveBeenCalledWith(99));
        const body = postBody();
        expect(body).not.toHaveProperty('anfrageIds');
        expect(body.bauvorhaben).toBe('Carport Musterweg');
        expect(body.kundenId).toBe(8);
    });
});
