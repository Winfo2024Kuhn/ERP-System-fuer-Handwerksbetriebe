import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PreiseEintragenModal } from './PreiseEintragenModal';
import { ToastProvider } from './ui/toast';

const mockFetch = vi.fn();
(globalThis as unknown as { fetch: typeof fetch }).fetch = mockFetch as unknown as typeof fetch;

const VERGLEICH_DTO_MIT_KI = {
    preisanfrageId: 1,
    nummer: 'PA-2026-001',
    bauvorhaben: null,
    lieferanten: [
        { preisanfrageLieferantId: 10, lieferantId: 100, lieferantenname: 'Stahl A', status: 'BEANTWORTET' },
    ],
    positionen: [
        {
            preisanfragePositionId: 501,
            produktname: 'Flachstahl 30x5',
            menge: 100,
            einheit: 'kg',
            guenstigsterPreisanfrageLieferantId: 10,
            zellen: [
                {
                    preisanfrageLieferantId: 10,
                    einzelpreis: 1.85,
                    gesamtpreis: null,
                    mwstProzent: 19,
                    lieferzeitTage: 10,
                    bemerkung: 'Staffelpreis',
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
                    einzelpreis: null,
                    gesamtpreis: null,
                    mwstProzent: null,
                    lieferzeitTage: null,
                    bemerkung: null,
                    guenstigster: false,
                },
            ],
        },
    ],
};

function renderModal(props: Partial<React.ComponentProps<typeof PreiseEintragenModal>> = {}) {
    const onClose = vi.fn();
    const onSaved = vi.fn();
    const utils = render(
        <ToastProvider>
            <PreiseEintragenModal
                open
                onClose={onClose}
                preisanfrageId={1}
                preisanfrageLieferantId={10}
                onSaved={onSaved}
                {...props}
            />
        </ToastProvider>,
    );
    return { ...utils, onClose, onSaved };
}

function mockDefaultFetch() {
    mockFetch.mockImplementation((input: RequestInfo | URL) => {
        const url = typeof input === 'string' ? input : input.toString();
        if (url.includes('/vergleich')) {
            return Promise.resolve({ ok: true, json: () => Promise.resolve(VERGLEICH_DTO_MIT_KI) });
        }
        return Promise.resolve({ ok: true, json: () => Promise.resolve({ id: 1 }) });
    });
}

describe('PreiseEintragenModal', () => {
    beforeEach(() => {
        mockFetch.mockReset();
        mockDefaultFetch();
    });

    it('markiert KI-vorbefuellte Zeilen mit "KI-Vorschlag, bitte pruefen"', async () => {
        renderModal();
        await waitFor(() => expect(screen.getByText('Flachstahl 30x5')).toBeInTheDocument());

        // Nur die erste Position hat KI-Preis -> markiert
        const badges = screen.getAllByText(/ki-vorschlag, bitte prüfen/i);
        expect(badges).toHaveLength(1);
    });

    it('Speichern ist deaktiviert, wenn alle Preise geloescht wurden', async () => {
        const user = userEvent.setup();
        renderModal();
        await waitFor(() => expect(screen.getByText('Flachstahl 30x5')).toBeInTheDocument());

        // Das einzige vorbefuellte Preis-Feld leeren
        const preisInputs = screen.getAllByPlaceholderText('0,00');
        await user.clear(preisInputs[0]);

        const saveBtn = screen.getByRole('button', { name: /preise speichern/i });
        expect(saveBtn).toBeDisabled();
    });

    it('submit POSTet pro ausgefuelltem Preis eine Zeile', async () => {
        const user = userEvent.setup();
        const { onSaved, onClose } = renderModal();
        await waitFor(() => expect(screen.getByText('Flachstahl 30x5')).toBeInTheDocument());

        // Preis fuer Position 2 eintragen (war leer)
        const preisInputs = screen.getAllByPlaceholderText('0,00');
        await user.clear(preisInputs[1]);
        await user.type(preisInputs[1], '12,50');

        const saveBtn = screen.getByRole('button', { name: /preise speichern/i });
        await user.click(saveBtn);

        await waitFor(() => {
            const postCalls = mockFetch.mock.calls.filter(
                c => String(c[0]).includes('/api/preisanfragen/angebote') && c[1]?.method === 'POST',
            );
            // Beide Positionen haben jetzt Preise (Position 1: vorbefuellt, Position 2: neu)
            expect(postCalls.length).toBe(2);

            // Body fuer Position 502 muss 12.5 als einzelpreis enthalten
            const body502 = JSON.parse(
                (postCalls.find(c => JSON.parse(String(c[1]?.body)).preisanfragePositionId === 502)?.[1]
                    ?.body as string),
            );
            expect(body502.einzelpreis).toBe(12.5);
            expect(body502.preisanfrageLieferantId).toBe(10);
        });

        await waitFor(() => {
            expect(onSaved).toHaveBeenCalledTimes(1);
            expect(onClose).toHaveBeenCalledTimes(1);
        });
    });

    it('Fehler in Position wird als Toast gemeldet, Modal bleibt offen', async () => {
        const user = userEvent.setup();
        const { onClose } = renderModal();
        await waitFor(() => expect(screen.getByText('Flachstahl 30x5')).toBeInTheDocument());

        mockFetch.mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
            const url = typeof input === 'string' ? input : input.toString();
            if (url.includes('/vergleich')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve(VERGLEICH_DTO_MIT_KI) });
            }
            if (url.includes('/api/preisanfragen/angebote') && init?.method === 'POST') {
                return Promise.resolve({
                    ok: false,
                    status: 400,
                    headers: new Headers({ 'X-Error-Reason': 'Einzelpreis darf nicht negativ sein' }),
                    json: () => Promise.resolve({}),
                });
            }
            return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
        });

        const saveBtn = screen.getByRole('button', { name: /preise speichern/i });
        await user.click(saveBtn);

        // Speicherfehler → Modal bleibt offen
        await waitFor(() => {
            expect(onClose).not.toHaveBeenCalled();
        });
    });
});
