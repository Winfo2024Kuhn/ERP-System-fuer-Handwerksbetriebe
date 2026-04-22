import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PreisanfrageVergleichModal } from './PreisanfrageVergleichModal';
import { ToastProvider } from './ui/toast';
import { ConfirmProvider } from './ui/confirm-dialog';

const mockFetch = vi.fn();
(globalThis as unknown as { fetch: typeof fetch }).fetch = mockFetch as unknown as typeof fetch;

const VERGLEICH_DTO = {
    preisanfrageId: 1,
    nummer: 'PA-2026-001',
    bauvorhaben: 'Stahlbau Musterstraße',
    lieferanten: [
        { preisanfrageLieferantId: 10, lieferantId: 100, lieferantenname: 'Stahl A', status: 'BEANTWORTET' },
        { preisanfrageLieferantId: 11, lieferantId: 101, lieferantenname: 'Stahl B', status: 'BEANTWORTET' },
    ],
    positionen: [
        {
            preisanfragePositionId: 501,
            produktname: 'Flachstahl 30x5',
            menge: 100,
            einheit: 'kg',
            guenstigsterPreisanfrageLieferantId: 11,
            zellen: [
                {
                    preisanfrageLieferantId: 10,
                    einzelpreis: 2.1,
                    gesamtpreis: null,
                    mwstProzent: 19,
                    lieferzeitTage: 10,
                    bemerkung: null,
                    guenstigster: false,
                },
                {
                    preisanfrageLieferantId: 11,
                    einzelpreis: 1.85,
                    gesamtpreis: null,
                    mwstProzent: 19,
                    lieferzeitTage: 14,
                    bemerkung: null,
                    guenstigster: true,
                },
            ],
        },
        {
            preisanfragePositionId: 502,
            produktname: 'Rundrohr 25',
            menge: 20,
            einheit: 'm',
            guenstigsterPreisanfrageLieferantId: 10,
            zellen: [
                {
                    preisanfrageLieferantId: 10,
                    einzelpreis: 12.5,
                    gesamtpreis: null,
                    mwstProzent: 19,
                    lieferzeitTage: 7,
                    bemerkung: 'Staffelpreis ab 10 m',
                    guenstigster: true,
                },
                {
                    preisanfrageLieferantId: 11,
                    einzelpreis: 13.2,
                    gesamtpreis: null,
                    mwstProzent: 19,
                    lieferzeitTage: 14,
                    bemerkung: null,
                    guenstigster: false,
                },
            ],
        },
    ],
};

function renderModal(props: Partial<React.ComponentProps<typeof PreisanfrageVergleichModal>> = {}) {
    const onClose = vi.fn();
    const onVergeben = vi.fn();
    const onPreiseEintragen = vi.fn();
    const utils = render(
        <ToastProvider>
            <ConfirmProvider>
                <PreisanfrageVergleichModal
                    open
                    onClose={onClose}
                    preisanfrageId={1}
                    nummerHint="PA-2026-001"
                    onVergeben={onVergeben}
                    onPreiseEintragen={onPreiseEintragen}
                    {...props}
                />
            </ConfirmProvider>
        </ToastProvider>,
    );
    return { ...utils, onClose, onVergeben, onPreiseEintragen };
}

function mockDefaultFetch() {
    mockFetch.mockImplementation((input: RequestInfo | URL) => {
        const url = typeof input === 'string' ? input : input.toString();
        if (url.includes('/vergleich')) {
            return Promise.resolve({ ok: true, json: () => Promise.resolve(VERGLEICH_DTO) });
        }
        return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
    });
}

describe('PreisanfrageVergleichModal', () => {
    beforeEach(() => {
        mockFetch.mockReset();
        mockDefaultFetch();
    });

    it('laedt die Matrix und markiert den guenstigsten Preis pro Zeile', async () => {
        renderModal();

        // Header ist sofort sichtbar (nummerHint)
        expect(screen.getByText(/Vergleich PA-2026-001/)).toBeInTheDocument();

        await waitFor(() => {
            expect(screen.getByText('Flachstahl 30x5')).toBeInTheDocument();
            expect(screen.getByText('Rundrohr 25')).toBeInTheDocument();
        });

        // Pro Zeile muss GENAU EINE Zelle als guenstigster markiert sein
        const guenstigsterCells = document.querySelectorAll('td[data-guenstigster="true"]');
        expect(guenstigsterCells).toHaveLength(2);

        // Die guenstigste Zelle muss die Hervorhebung tragen (Trophy-Icon als aria-label)
        const trophies = screen.getAllByLabelText('günstigster Preis');
        expect(trophies).toHaveLength(2);
    });

    it('Beauftragen-Button triggert Vergabe nach Confirm', async () => {
        const user = userEvent.setup();
        const { onVergeben, onClose } = renderModal();

        await waitFor(() => expect(screen.getByText('Flachstahl 30x5')).toBeInTheDocument());

        mockFetch.mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
            const url = typeof input === 'string' ? input : input.toString();
            if (url.includes('/vergeben/') && init?.method === 'POST') {
                return Promise.resolve({ ok: true, json: () => Promise.resolve({ id: 1 }) });
            }
            if (url.includes('/vergleich')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve(VERGLEICH_DTO) });
            }
            return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
        });

        const buttons = screen.getAllByRole('button', { name: /beauftragen/i });
        // Zwei Lieferanten = zwei Beauftragen-Buttons
        expect(buttons).toHaveLength(2);
        await user.click(buttons[0]);

        // Confirm-Dialog erscheint
        const confirmBtn = await screen.findByRole('button', { name: /auftrag vergeben/i });
        await user.click(confirmBtn);

        // Nach der Vergabe erscheint die PDF-Vorschau des neu angelegten Auftrags;
        // onClose kommt erst, wenn der Nutzer die Vorschau schließt.
        await waitFor(() => {
            expect(onVergeben).toHaveBeenCalledTimes(1);
            expect(screen.getByText(/Bestellung für Stahl A/)).toBeInTheDocument();
        });
        expect(onClose).not.toHaveBeenCalled();

        const vergabeCall = mockFetch.mock.calls.find(
            c => String(c[0]).includes('/vergeben/'),
        );
        expect(vergabeCall?.[0]).toContain('/api/preisanfragen/1/vergeben/10');

        // Vorschau per Escape schließen → onClose wird nachgezogen
        await user.keyboard('{Escape}');
        await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
    });

    it('KI-Preise auslesen triggert POST /angebote/extrahieren und laedt neu', async () => {
        const user = userEvent.setup();
        renderModal();

        await waitFor(() => expect(screen.getByText('Flachstahl 30x5')).toBeInTheDocument());

        mockFetch.mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
            const url = typeof input === 'string' ? input : input.toString();
            if (url.includes('/angebote/extrahieren') && init?.method === 'POST') {
                return Promise.resolve({
                    ok: true,
                    json: () => Promise.resolve({
                        verarbeiteteLieferanten: 2,
                        extrahierteAngebote: 3,
                        fehler: 0,
                        hinweise: [],
                    }),
                });
            }
            if (url.includes('/vergleich')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve(VERGLEICH_DTO) });
            }
            return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
        });

        const extrahiereBtn = screen.getByRole('button', { name: /ki-preise auslesen/i });
        await user.click(extrahiereBtn);

        await waitFor(() => {
            const call = mockFetch.mock.calls.find(
                c => String(c[0]).includes('/angebote/extrahieren'),
            );
            expect(call).toBeDefined();
            expect(call?.[1]?.method).toBe('POST');
        });
    });

    it('ruft onPreiseEintragen mit palId beim Klick auf Preise-eintragen-Button', async () => {
        const user = userEvent.setup();
        const { onPreiseEintragen } = renderModal();

        await waitFor(() => expect(screen.getByText('Flachstahl 30x5')).toBeInTheDocument());

        const buttons = screen.getAllByRole('button', { name: /preise eintragen/i });
        await user.click(buttons[1]); // zweiter Lieferant

        expect(onPreiseEintragen).toHaveBeenCalledWith(11);
    });

    it('rendert Summenzeile pro Lieferant', async () => {
        renderModal();
        await waitFor(() => expect(screen.getByText('Flachstahl 30x5')).toBeInTheDocument());

        const summenZeile = screen.getByText('Summe (netto)').closest('tr');
        expect(summenZeile).toBeTruthy();
        // A: 2.10 * 100 + 12.50 * 20 = 460.00
        // B: 1.85 * 100 + 13.20 * 20 = 449.00
        if (summenZeile) {
            const tds = within(summenZeile as HTMLElement).getAllByRole('cell');
            expect(tds[1].textContent).toContain('460');
            expect(tds[2].textContent).toContain('449');
        }
    });
});
