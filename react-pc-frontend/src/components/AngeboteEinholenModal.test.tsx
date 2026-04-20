import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AngeboteEinholenModal, type BedarfPosition } from './AngeboteEinholenModal';
import { ToastProvider } from './ui/toast';

const mockFetch = vi.fn();
// Typed global fetch override for test environment
(globalThis as unknown as { fetch: typeof fetch }).fetch = mockFetch as unknown as typeof fetch;

const LIEFERANTEN = [
    { id: 101, lieferantenname: 'Stahlhandel A', ort: 'Musterstadt', istAktiv: true },
    { id: 102, lieferantenname: 'Stahlhandel B', ort: 'Musterdorf', istAktiv: true },
    { id: 103, lieferantenname: 'Stahlhandel C', ort: 'Musterhausen', istAktiv: true },
];

const VORAUSGEWAEHLT: BedarfPosition[] = [
    {
        id: 501,
        produktname: 'Stahlprofil IPE 200',
        werkstoffName: 'S235JR',
        menge: 12,
        einheit: 'Stk',
        projektId: 9,
        projektName: 'Stahlbau Musterstraße',
    },
    {
        id: 502,
        produktname: 'Flachstahl 50x5',
        werkstoffName: 'S235JR',
        menge: 20,
        einheit: 'm',
        projektId: 9,
        projektName: 'Stahlbau Musterstraße',
    },
];

function renderModal(props: Partial<React.ComponentProps<typeof AngeboteEinholenModal>> = {}) {
    const onClose = vi.fn();
    const utils = render(
        <MemoryRouter>
            <ToastProvider>
                <AngeboteEinholenModal
                    open
                    onClose={onClose}
                    vorausgewaehltePositionen={VORAUSGEWAEHLT}
                    {...props}
                />
            </ToastProvider>
        </MemoryRouter>,
    );
    return { ...utils, onClose };
}

function mockDefaultFetch() {
    mockFetch.mockImplementation((input: RequestInfo | URL) => {
        const url = typeof input === 'string' ? input : input.toString();
        if (url.startsWith('/api/lieferanten')) {
            return Promise.resolve({
                ok: true,
                json: () => Promise.resolve({ lieferanten: LIEFERANTEN, gesamt: LIEFERANTEN.length }),
            });
        }
        if (url === '/api/bestellungen/offen') {
            return Promise.resolve({ ok: true, json: () => Promise.resolve([]) });
        }
        return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
    });
}

describe('AngeboteEinholenModal', () => {
    beforeEach(() => {
        mockFetch.mockReset();
        mockDefaultFetch();
    });

    it('Submit-Button ist disabled, solange kein Lieferant ausgewählt ist', async () => {
        renderModal();

        await waitFor(() => {
            expect(screen.getByText('Stahlhandel A')).toBeInTheDocument();
        });

        // Positionen sind bereits vorausgewählt, Lieferanten noch nicht
        const submitButton = screen.getByRole('button', { name: /Anfrage versenden/i });
        expect(submitButton).toBeDisabled();
    });

    it('erlaubt die Auswahl mehrerer Lieferanten', async () => {
        const user = userEvent.setup();
        renderModal();

        await waitFor(() => {
            expect(screen.getByText('Stahlhandel A')).toBeInTheDocument();
        });

        const checkboxA = screen.getByRole('checkbox', { name: /Stahlhandel A/i });
        const checkboxB = screen.getByRole('checkbox', { name: /Stahlhandel B/i });

        await user.click(checkboxA);
        await user.click(checkboxB);

        expect(checkboxA).toBeChecked();
        expect(checkboxB).toBeChecked();

        // Zähler im Lieferanten-Heading muss "(2 ausgewählt)" anzeigen
        const headings = screen.getAllByRole('heading', { level: 3 });
        const lieferantenHeading = headings.find(h => /Lieferanten/i.test(h.textContent ?? ''));
        expect(lieferantenHeading?.textContent).toMatch(/2 ausgewählt/);

        // Submit-Button ist jetzt aktiv (Positionen + Lieferanten + Frist = default gesetzt)
        const submitButton = screen.getByRole('button', { name: /Anfrage versenden/i });
        expect(submitButton).toBeEnabled();
    });

    it('ruft beim Submit erst POST /api/preisanfragen und dann /versenden auf', async () => {
        const user = userEvent.setup();

        // Spezifischer Mock mit Reihenfolge-Tracking
        const callOrder: string[] = [];
        mockFetch.mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
            const url = typeof input === 'string' ? input : input.toString();
            if (url.startsWith('/api/lieferanten')) {
                return Promise.resolve({
                    ok: true,
                    json: () => Promise.resolve({ lieferanten: LIEFERANTEN, gesamt: LIEFERANTEN.length }),
                });
            }
            if (url === '/api/bestellungen/offen') {
                return Promise.resolve({ ok: true, json: () => Promise.resolve([]) });
            }
            if (url === '/api/preisanfragen' && init?.method === 'POST') {
                callOrder.push('create');
                return Promise.resolve({
                    ok: true,
                    status: 201,
                    headers: new Headers(),
                    json: () => Promise.resolve({ id: 4711, nummer: 'PA-2026-001' }),
                });
            }
            if (url === '/api/preisanfragen/4711/versenden' && init?.method === 'POST') {
                callOrder.push('send');
                return Promise.resolve({
                    ok: true,
                    status: 200,
                    headers: new Headers(),
                    json: () => Promise.resolve({}),
                });
            }
            return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
        });

        const { onClose } = renderModal();

        await waitFor(() => {
            expect(screen.getByText('Stahlhandel A')).toBeInTheDocument();
        });

        // Zwei Lieferanten wählen
        await user.click(screen.getByRole('checkbox', { name: /Stahlhandel A/i }));
        await user.click(screen.getByRole('checkbox', { name: /Stahlhandel C/i }));

        // Senden
        const submitButton = screen.getByRole('button', { name: /Anfrage versenden/i });
        expect(submitButton).toBeEnabled();
        await user.click(submitButton);

        await waitFor(() => {
            expect(callOrder).toEqual(['create', 'send']);
        });

        // Body des ersten POST-Requests pruefen
        const createCall = mockFetch.mock.calls.find(call => {
            const url = typeof call[0] === 'string' ? call[0] : String(call[0]);
            return url === '/api/preisanfragen';
        });
        expect(createCall).toBeDefined();
        const body = JSON.parse((createCall?.[1] as RequestInit).body as string);
        expect(body.lieferantIds).toEqual([101, 103]);
        expect(body.positionen).toHaveLength(2);
        expect(body.positionen[0].artikelInProjektId).toBe(501);

        // onClose wurde nach Erfolg aufgerufen
        await waitFor(() => expect(onClose).toHaveBeenCalled());
    });

    it('zeigt alle vorausgewählten Positionen als vorausgehakt an', async () => {
        renderModal();

        await waitFor(() => {
            expect(screen.getByText('Stahlprofil IPE 200')).toBeInTheDocument();
        });

        const checkbox1 = screen.getByRole('checkbox', { name: /Stahlprofil IPE 200/i });
        const checkbox2 = screen.getByRole('checkbox', { name: /Flachstahl 50x5/i });
        expect(checkbox1).toBeChecked();
        expect(checkbox2).toBeChecked();
    });

    it('deaktiviert den Submit-Button, wenn alle Positionen abgewählt sind', async () => {
        const user = userEvent.setup();
        renderModal();

        await waitFor(() => {
            expect(screen.getByText('Stahlhandel A')).toBeInTheDocument();
        });

        // Lieferanten wählen, dann alle Positionen abwählen
        await user.click(screen.getByRole('checkbox', { name: /Stahlhandel A/i }));
        const alleAbwaehlenButton = screen.getByRole('button', { name: /Alle abwählen/i });
        await user.click(alleAbwaehlenButton);

        const submitButton = screen.getByRole('button', { name: /Anfrage versenden/i });
        expect(submitButton).toBeDisabled();
    });

    it('erlaubt die Suche in der Lieferantenliste', async () => {
        const user = userEvent.setup();
        renderModal();

        await waitFor(() => {
            expect(screen.getByText('Stahlhandel A')).toBeInTheDocument();
        });

        const suche = screen.getByPlaceholderText(/Nach Name, Ort oder PLZ suchen/i);
        await user.type(suche, 'Handel B');

        // Nur B darf sichtbar bleiben
        expect(screen.getByText('Stahlhandel B')).toBeInTheDocument();
        expect(screen.queryByText('Stahlhandel A')).not.toBeInTheDocument();
        expect(screen.queryByText('Stahlhandel C')).not.toBeInTheDocument();
    });

    it('zeigt die Rückmeldefrist als Pflichtfeld an', async () => {
        renderModal();

        await waitFor(() => {
            expect(screen.getByText('Stahlhandel A')).toBeInTheDocument();
        });

        const label = screen.getByText(/Rückmeldefrist/);
        // Das Sternchen "*" markiert Pflichtfeld
        const wrapper = label.closest('div');
        expect(wrapper).not.toBeNull();
        if (wrapper) {
            expect(within(wrapper).getByText('*')).toBeInTheDocument();
        }
    });
});
