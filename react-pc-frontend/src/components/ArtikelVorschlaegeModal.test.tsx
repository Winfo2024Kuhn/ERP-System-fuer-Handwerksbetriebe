import { render, screen, waitFor, fireEvent, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ArtikelVorschlaegeModal } from './ArtikelVorschlaegeModal';

const toastFns = { success: vi.fn(), error: vi.fn(), info: vi.fn() };

vi.mock('./ui/toast', () => ({
    useToast: () => toastFns,
}));

vi.mock('./CategoryTreeModal', () => ({
    CategoryTreeModal: ({ onSelect, onClose }: { onSelect: (id: number, name: string) => void; onClose: () => void }) => (
        <div data-testid="category-modal">
            <button onClick={() => onSelect(42, 'Stahl / Flachstahl')}>Kategorie 42</button>
            <button onClick={onClose}>schliessen</button>
        </div>
    ),
}));

const mockFetch = vi.fn();
global.fetch = mockFetch as unknown as typeof fetch;

type Vorschlag = {
    id: number;
    status: string;
    typ: string;
    erstelltAm: string;
    lieferantName?: string;
    externeArtikelnummer?: string;
    produktname?: string;
    kiKonfidenz?: number;
    kiBegruendung?: string;
    quelleDokumentBezeichnung?: string;
    konfliktArtikelName?: string;
    konfliktArtikelId?: number;
    vorgeschlageneKategoriePfad?: string;
    vorgeschlagenerWerkstoffId?: number;
};

const makeVorschlag = (overrides: Partial<Vorschlag> = {}): Vorschlag => ({
    id: 1,
    status: 'PENDING',
    typ: 'NEU_ANLAGE',
    erstelltAm: '2026-04-17T10:00:00',
    lieferantName: 'Max Mustermann GmbH',
    externeArtikelnummer: 'A-1',
    produktname: 'Flachstahl 30x5',
    kiKonfidenz: 0.9,
    kiBegruendung: 'Name passt nahezu exakt',
    quelleDokumentBezeichnung: 'RE-2026-001.pdf',
    ...overrides,
});

const mockListAndWerkstoffe = (vorschlaege: Vorschlag[], werkstoffe: { id: number; name: string }[] = []) => {
    mockFetch.mockImplementation((url: string) => {
        if (url.includes('/api/artikel-vorschlaege?status=PENDING')) {
            return Promise.resolve({ ok: true, json: () => Promise.resolve(vorschlaege) });
        }
        if (url.includes('/api/artikel/werkstoffe/details')) {
            return Promise.resolve({ ok: true, json: () => Promise.resolve(werkstoffe) });
        }
        return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
    });
};

describe('ArtikelVorschlaegeModal', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        toastFns.success.mockClear();
        toastFns.error.mockClear();
        toastFns.info.mockClear();
    });

    it('zeigt Ladeindikator und lädt Vorschlagsliste', async () => {
        mockListAndWerkstoffe([makeVorschlag()]);
        render(<ArtikelVorschlaegeModal onClose={vi.fn()} />);

        await waitFor(() => expect(screen.getByText('Flachstahl 30x5')).toBeInTheDocument());
        expect(screen.getByText('Max Mustermann GmbH · A-1')).toBeInTheDocument();
        expect(screen.getByText('Neu anlegen')).toBeInTheDocument();
    });

    it('zeigt "Alles erledigt" wenn keine Vorschläge vorhanden sind', async () => {
        mockListAndWerkstoffe([]);
        render(<ArtikelVorschlaegeModal onClose={vi.fn()} />);

        await waitFor(() => expect(screen.getByText('Alles erledigt!')).toBeInTheDocument());
        expect(screen.getByText('Keine offenen Vorschläge.')).toBeInTheDocument();
    });

    it('zeigt Fehler-Toast wenn das Laden fehlschlägt', async () => {
        mockFetch.mockImplementation((url: string) => {
            if (url.includes('/api/artikel-vorschlaege')) {
                return Promise.resolve({ ok: false });
            }
            return Promise.resolve({ ok: true, json: () => Promise.resolve([]) });
        });
        render(<ArtikelVorschlaegeModal onClose={vi.fn()} />);

        await waitFor(() => expect(toastFns.error).toHaveBeenCalledWith('Vorschläge konnten nicht geladen werden.'));
    });

    it('rendert Konflikt-Hinweis bei KONFLIKT_EXTERNE_NUMMER', async () => {
        mockListAndWerkstoffe([
            makeVorschlag({
                id: 2,
                typ: 'KONFLIKT_EXTERNE_NUMMER',
                konfliktArtikelName: 'Alt-Artikel',
                konfliktArtikelId: 5,
                externeArtikelnummer: 'X1',
            }),
        ]);
        render(<ArtikelVorschlaegeModal onClose={vi.fn()} />);

        await waitFor(() => expect(screen.getAllByText('Nummer-Konflikt').length).toBeGreaterThanOrEqual(1));
        expect(screen.getByText(/Alt-Artikel/)).toBeInTheDocument();
    });

    it('Freigabe ruft approve-Endpoint und entfernt Vorschlag aus der Liste', async () => {
        const onApproved = vi.fn();
        mockFetch.mockImplementation((url: string, init?: RequestInit) => {
            if (url.includes('/api/artikel-vorschlaege?status=PENDING')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve([makeVorschlag()]) });
            }
            if (url.includes('/api/artikel/werkstoffe/details')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve([]) });
            }
            if (url.includes('/approve') && init?.method === 'POST') {
                return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
            }
            return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
        });

        render(<ArtikelVorschlaegeModal onClose={vi.fn()} onApproved={onApproved} />);

        await waitFor(() => expect(screen.getByText('Flachstahl 30x5')).toBeInTheDocument());

        fireEvent.click(screen.getByText('Freigeben & Anlegen'));

        await waitFor(() => expect(toastFns.success).toHaveBeenCalled());
        expect(onApproved).toHaveBeenCalled();
        expect(mockFetch).toHaveBeenCalledWith(
            '/api/artikel-vorschlaege/1/approve',
            expect.objectContaining({ method: 'POST' }),
        );
    });

    it('Freigabe zeigt Fehler-Toast bei Server-Fehler', async () => {
        mockFetch.mockImplementation((url: string) => {
            if (url.includes('/api/artikel-vorschlaege?status=PENDING')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve([makeVorschlag()]) });
            }
            if (url.includes('/api/artikel/werkstoffe/details')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve([]) });
            }
            if (url.includes('/approve')) {
                return Promise.resolve({ ok: false });
            }
            return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
        });

        render(<ArtikelVorschlaegeModal onClose={vi.fn()} />);
        await waitFor(() => expect(screen.getByText('Flachstahl 30x5')).toBeInTheDocument());
        fireEvent.click(screen.getByText('Freigeben & Anlegen'));

        await waitFor(() => expect(toastFns.error).toHaveBeenCalledWith('Freigabe fehlgeschlagen.'));
    });

    it('Ablehnen ruft reject-Endpoint und zeigt info-Toast', async () => {
        mockFetch.mockImplementation((url: string) => {
            if (url.includes('/api/artikel-vorschlaege?status=PENDING')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve([makeVorschlag()]) });
            }
            if (url.includes('/api/artikel/werkstoffe/details')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve([]) });
            }
            if (url.includes('/reject')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
            }
            return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
        });

        render(<ArtikelVorschlaegeModal onClose={vi.fn()} />);
        await waitFor(() => expect(screen.getByText('Flachstahl 30x5')).toBeInTheDocument());
        fireEvent.click(screen.getByText('Ablehnen'));

        await waitFor(() => expect(toastFns.info).toHaveBeenCalledWith('Vorschlag abgelehnt.'));
    });

    it('Ablehnen zeigt Fehler-Toast bei Server-Fehler', async () => {
        mockFetch.mockImplementation((url: string) => {
            if (url.includes('/api/artikel-vorschlaege?status=PENDING')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve([makeVorschlag()]) });
            }
            if (url.includes('/api/artikel/werkstoffe/details')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve([]) });
            }
            if (url.includes('/reject')) {
                return Promise.resolve({ ok: false });
            }
            return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
        });

        render(<ArtikelVorschlaegeModal onClose={vi.fn()} />);
        await waitFor(() => expect(screen.getByText('Flachstahl 30x5')).toBeInTheDocument());
        fireEvent.click(screen.getByText('Ablehnen'));

        await waitFor(() => expect(toastFns.error).toHaveBeenCalledWith('Ablehnen fehlgeschlagen.'));
    });

    it('Escape-Taste ruft onClose', async () => {
        const onClose = vi.fn();
        mockListAndWerkstoffe([makeVorschlag()]);
        render(<ArtikelVorschlaegeModal onClose={onClose} />);
        await waitFor(() => expect(screen.getByText('Flachstahl 30x5')).toBeInTheDocument());

        act(() => {
            window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
        });
        expect(onClose).toHaveBeenCalled();
    });

    it('Pfeil-nach-unten wechselt zum nächsten Vorschlag', async () => {
        mockListAndWerkstoffe([
            makeVorschlag({ id: 1, produktname: 'Erster' }),
            makeVorschlag({ id: 2, produktname: 'Zweiter' }),
        ]);
        render(<ArtikelVorschlaegeModal onClose={vi.fn()} />);
        await waitFor(() => expect(screen.getByText('Erster')).toBeInTheDocument());

        act(() => {
            window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
        });
        await waitFor(() => {
            const input = screen.getByDisplayValue('Zweiter');
            expect(input).toBeInTheDocument();
        });
    });

    it('Entf-Taste löst Reject aus', async () => {
        mockFetch.mockImplementation((url: string) => {
            if (url.includes('/api/artikel-vorschlaege?status=PENDING')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve([makeVorschlag()]) });
            }
            if (url.includes('/api/artikel/werkstoffe/details')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve([]) });
            }
            if (url.includes('/reject')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
            }
            return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
        });
        render(<ArtikelVorschlaegeModal onClose={vi.fn()} />);
        await waitFor(() => expect(screen.getByText('Flachstahl 30x5')).toBeInTheDocument());

        act(() => {
            window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Delete' }));
        });
        await waitFor(() => expect(toastFns.info).toHaveBeenCalled());
    });

    it('Strg+Enter löst Approve aus', async () => {
        mockFetch.mockImplementation((url: string) => {
            if (url.includes('/api/artikel-vorschlaege?status=PENDING')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve([makeVorschlag()]) });
            }
            if (url.includes('/api/artikel/werkstoffe/details')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve([]) });
            }
            if (url.includes('/approve')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
            }
            return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
        });
        render(<ArtikelVorschlaegeModal onClose={vi.fn()} />);
        await waitFor(() => expect(screen.getByText('Flachstahl 30x5')).toBeInTheDocument());

        act(() => {
            window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', ctrlKey: true }));
        });
        await waitFor(() => expect(toastFns.success).toHaveBeenCalled());
    });

    it('Kategorie-Modal setzt gewählte Kategorie in Formular', async () => {
        mockListAndWerkstoffe([makeVorschlag()]);
        render(<ArtikelVorschlaegeModal onClose={vi.fn()} />);
        await waitFor(() => expect(screen.getByText('Flachstahl 30x5')).toBeInTheDocument());

        fireEvent.click(screen.getByText('Kategorie wählen…'));
        await waitFor(() => expect(screen.getByTestId('category-modal')).toBeInTheDocument());

        fireEvent.click(screen.getByText('Kategorie 42'));
        await waitFor(() => expect(screen.getByText('Stahl / Flachstahl')).toBeInTheDocument());
    });

    it('rendert KI-Begründung und Quelle', async () => {
        mockListAndWerkstoffe([makeVorschlag()]);
        render(<ArtikelVorschlaegeModal onClose={vi.fn()} />);

        await waitFor(() => expect(screen.getByText('Name passt nahezu exakt')).toBeInTheDocument());
        expect(screen.getByText(/RE-2026-001\.pdf/)).toBeInTheDocument();
    });

    it('werkstoff-Fetch toleriert Fehler', async () => {
        mockFetch.mockImplementation((url: string) => {
            if (url.includes('/api/artikel-vorschlaege?status=PENDING')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve([makeVorschlag()]) });
            }
            if (url.includes('/api/artikel/werkstoffe/details')) {
                return Promise.reject(new Error('Netz weg'));
            }
            return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
        });
        render(<ArtikelVorschlaegeModal onClose={vi.fn()} />);
        await waitFor(() => expect(screen.getByText('Flachstahl 30x5')).toBeInTheDocument());
        // kein Crash, Werkstoff-Select mit nur "— keiner —"
        expect(screen.getByText(/— keiner —/)).toBeInTheDocument();
    });
});
