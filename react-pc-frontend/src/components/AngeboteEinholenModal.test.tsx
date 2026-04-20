import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AngeboteEinholenModal, type BedarfPosition } from './AngeboteEinholenModal';
import { ToastProvider } from './ui/toast';

const mockFetch = vi.fn();
// Typed global fetch override for test environment
(globalThis as unknown as { fetch: typeof fetch }).fetch = mockFetch as unknown as typeof fetch;

const LIEFERANT_A = {
    id: 101,
    lieferantenname: 'Stahlhandel A',
    ort: 'Musterstadt',
    istAktiv: true,
    kundenEmails: ['einkauf@stahl-a.example'],
};
const LIEFERANT_B = {
    id: 102,
    lieferantenname: 'Stahlhandel B',
    ort: 'Musterdorf',
    istAktiv: true,
    kundenEmails: ['haupt@stahl-b.example', 'zweig@stahl-b.example'],
};
const LIEFERANT_C = {
    id: 103,
    lieferantenname: 'Stahlhandel C',
    ort: 'Musterhausen',
    istAktiv: true,
    kundenEmails: ['info@stahl-c.example'],
};
const LIEFERANTEN = [LIEFERANT_A, LIEFERANT_B, LIEFERANT_C];

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
        if (url.startsWith('/api/projekte/simple')) {
            return Promise.resolve({ ok: true, json: () => Promise.resolve([]) });
        }
        return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
    });
}

async function lieferantHinzufuegen(user: ReturnType<typeof userEvent.setup>, name: string) {
    await user.click(screen.getByRole('button', { name: /Lieferant hinzufügen/i }));
    // Im Such-Modal: Klick auf den Listen-Button mit dem Lieferantennamen.
    // Der Name kommt im Such-Modal innerhalb eines <button> vor — das
    // unterscheidet ihn sowohl von der Liste unten (<p>) als auch vom
    // Entfernen-Button (aria-label "… entfernen").
    const suchDialog = await waitFor(() => {
        const heading = screen.getByRole('heading', { name: /Lieferant auswählen/i });
        const container = heading.closest('.fixed');
        if (!container) throw new Error('Such-Modal nicht gefunden');
        return container as HTMLElement;
    });
    const eintrag = await waitFor(() => within(suchDialog).getByText(name));
    await user.click(eintrag);
}

describe('AngeboteEinholenModal', () => {
    beforeEach(() => {
        mockFetch.mockReset();
        mockDefaultFetch();
    });

    it('Submit-Button ist disabled, solange kein Lieferant ausgewählt ist', () => {
        renderModal();
        const submitButton = screen.getByRole('button', { name: /Anfrage versenden/i });
        expect(submitButton).toBeDisabled();
    });

    it('erlaubt das Hinzufügen mehrerer Lieferanten über das Such-Modal', async () => {
        const user = userEvent.setup();
        renderModal();

        await lieferantHinzufuegen(user, 'Stahlhandel A');
        await lieferantHinzufuegen(user, 'Stahlhandel B');

        // Beide erscheinen in der Liste der gewählten Lieferanten
        // (1x in der Liste; beim 2. Klick ist das Such-Modal wieder geschlossen)
        expect(screen.getByText('Stahlhandel A')).toBeInTheDocument();
        expect(screen.getByText('Stahlhandel B')).toBeInTheDocument();

        const headings = screen.getAllByRole('heading', { level: 3 });
        const lieferantenHeading = headings.find(h => /Lieferanten/i.test(h.textContent ?? ''));
        expect(lieferantenHeading?.textContent).toMatch(/2 ausgewählt/);

        const submitButton = screen.getByRole('button', { name: /Anfrage versenden/i });
        expect(submitButton).toBeEnabled();
    });

    it('zeigt Dropdown nur bei mehreren E-Mails und liefert die Auswahl im Payload', async () => {
        const user = userEvent.setup();
        const callOrder: string[] = [];
        let capturedBody: unknown = null;
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
            if (url.startsWith('/api/projekte/simple')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve([]) });
            }
            if (url === '/api/preisanfragen' && init?.method === 'POST') {
                callOrder.push('create');
                capturedBody = JSON.parse(init.body as string);
                return Promise.resolve({
                    ok: true, status: 201, headers: new Headers(),
                    json: () => Promise.resolve({ id: 4711, nummer: 'PA-2026-001' }),
                });
            }
            if (url === '/api/preisanfragen/4711/versenden' && init?.method === 'POST') {
                callOrder.push('send');
                return Promise.resolve({ ok: true, status: 200, headers: new Headers(), json: () => Promise.resolve({}) });
            }
            return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
        });

        renderModal();

        // A (1 E-Mail) + B (2 E-Mails) hinzufügen
        await lieferantHinzufuegen(user, 'Stahlhandel A');
        await lieferantHinzufuegen(user, 'Stahlhandel B');

        // Kein Dropdown fuer A (nur 1 Mail) — die Adresse erscheint als Text
        expect(screen.getByText('einkauf@stahl-a.example')).toBeInTheDocument();
        expect(screen.queryByRole('combobox', { name: /Empfänger-Adresse für Stahlhandel A/i })).toBeNull();

        // Dropdown fuer B existiert und default ist die erste Mail
        const selectB = screen.getByRole('combobox', { name: /Empfänger-Adresse für Stahlhandel B/i });
        expect((selectB as HTMLSelectElement).value).toBe('haupt@stahl-b.example');

        await user.selectOptions(selectB, 'zweig@stahl-b.example');
        expect((selectB as HTMLSelectElement).value).toBe('zweig@stahl-b.example');

        const submitButton = screen.getByRole('button', { name: /Anfrage versenden/i });
        await user.click(submitButton);

        await waitFor(() => expect(callOrder).toEqual(['create', 'send']));

        const body = capturedBody as { lieferantIds: number[]; empfaengerProLieferant: Record<string, string> };
        expect(body.lieferantIds).toEqual([101, 102]);
        expect(body.empfaengerProLieferant).toEqual({
            '101': 'einkauf@stahl-a.example',
            '102': 'zweig@stahl-b.example',
        });
    });

    it('zeigt alle vorausgewählten Positionen als vorausgehakt an', () => {
        renderModal();
        const checkbox1 = screen.getByRole('checkbox', { name: /Stahlprofil IPE 200/i });
        const checkbox2 = screen.getByRole('checkbox', { name: /Flachstahl 50x5/i });
        expect(checkbox1).toBeChecked();
        expect(checkbox2).toBeChecked();
    });

    it('deaktiviert den Submit-Button, wenn alle Positionen abgewählt sind', async () => {
        const user = userEvent.setup();
        renderModal();

        await lieferantHinzufuegen(user, 'Stahlhandel A');

        const alleAbwaehlenButton = screen.getByRole('button', { name: /Alle abwählen/i });
        await user.click(alleAbwaehlenButton);

        const submitButton = screen.getByRole('button', { name: /Anfrage versenden/i });
        expect(submitButton).toBeDisabled();
    });

    it('entfernt einen hinzugefügten Lieferanten wieder', async () => {
        const user = userEvent.setup();
        renderModal();

        await lieferantHinzufuegen(user, 'Stahlhandel A');
        expect(screen.getByText('Stahlhandel A')).toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: /Stahlhandel A entfernen/i }));
        expect(screen.queryByText('Stahlhandel A')).not.toBeInTheDocument();
    });

    it('zeigt die Rückmeldefrist als Pflichtfeld an', () => {
        renderModal();
        const label = screen.getByText(/Rückmeldefrist/);
        const wrapper = label.closest('div');
        expect(wrapper).not.toBeNull();
        if (wrapper) {
            expect(within(wrapper).getByText('*')).toBeInTheDocument();
        }
    });

    it('fügt denselben Lieferanten nicht doppelt hinzu', async () => {
        const user = userEvent.setup();
        renderModal();

        await lieferantHinzufuegen(user, 'Stahlhandel A');
        await lieferantHinzufuegen(user, 'Stahlhandel A');

        // Nur 1x in der Liste (Such-Modal ist jetzt zu)
        expect(screen.getAllByText('Stahlhandel A')).toHaveLength(1);
    });
});
