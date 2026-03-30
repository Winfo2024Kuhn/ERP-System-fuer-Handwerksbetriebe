import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { CategoryMultiSelectModal } from './CategoryMultiSelectModal';

// Mock fetch
const mockFetch = vi.fn();
global.fetch = mockFetch;

const mockHauptkategorien = [
    { id: 1, bezeichnung: 'Metalle', leaf: false, verrechnungseinheit: { name: 'KILOGRAMM', anzeigename: 'kg' } },
    { id: 2, bezeichnung: 'Kunststoffe', leaf: true, verrechnungseinheit: { name: 'STUECK', anzeigename: 'Stück' } },
];

const mockSearchResults = [
    { id: 3, bezeichnung: 'Edelstahl', leaf: true, pfad: 'Metalle > Edelstahl', verrechnungseinheit: { name: 'KILOGRAMM', anzeigename: 'kg' } },
    { id: 4, bezeichnung: 'Baustahl', leaf: true, pfad: 'Metalle > Baustahl', verrechnungseinheit: { name: 'KILOGRAMM', anzeigename: 'kg' } },
];

describe('CategoryMultiSelectModal', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        mockFetch.mockImplementation((url: string) => {
            if (url.includes('/api/produktkategorien/haupt')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve(mockHauptkategorien) });
            }
            if (url.includes('/api/produktkategorien/suche')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve(mockSearchResults) });
            }
            return Promise.resolve({ ok: true, json: () => Promise.resolve([]) });
        });
    });

    it('zeigt Suchfeld an', async () => {
        render(<CategoryMultiSelectModal onConfirm={vi.fn()} onClose={vi.fn()} />);
        expect(screen.getByPlaceholderText('Kategorie suchen...')).toBeInTheDocument();
    });

    it('zeigt Kategoriebaum nach dem Laden', async () => {
        render(<CategoryMultiSelectModal onConfirm={vi.fn()} onClose={vi.fn()} />);
        await waitFor(() => {
            expect(screen.getByText('Metalle')).toBeInTheDocument();
            expect(screen.getByText('Kunststoffe')).toBeInTheDocument();
        });
    });

    it('zeigt Suchergebnisse bei Eingabe', async () => {
        const user = userEvent.setup();
        render(<CategoryMultiSelectModal onConfirm={vi.fn()} onClose={vi.fn()} />);

        await waitFor(() => expect(screen.getByText('Metalle')).toBeInTheDocument());

        const searchInput = screen.getByPlaceholderText('Kategorie suchen...');
        await user.type(searchInput, 'stahl');

        await waitFor(() => {
            expect(screen.getByText('Edelstahl')).toBeInTheDocument();
            expect(screen.getByText('Baustahl')).toBeInTheDocument();
        });
    });

    it('ruft onClose beim Abbrechen auf', async () => {
        const onClose = vi.fn();
        render(<CategoryMultiSelectModal onConfirm={vi.fn()} onClose={onClose} />);
        
        await waitFor(() => expect(screen.getByText('Metalle')).toBeInTheDocument());

        await userEvent.click(screen.getByText('Abbrechen'));
        expect(onClose).toHaveBeenCalledOnce();
    });

    it('ruft onConfirm mit ausgewählten Kategorien auf', async () => {
        const user = userEvent.setup();
        const onConfirm = vi.fn();
        const onClose = vi.fn();
        render(<CategoryMultiSelectModal onConfirm={onConfirm} onClose={onClose} />);

        await waitFor(() => expect(screen.getByText('Kunststoffe')).toBeInTheDocument());

        // Click on leaf category "Kunststoffe"
        await user.click(screen.getByText('Kunststoffe'));

        // Confirm
        await user.click(screen.getByText(/Kategorien übernehmen/));

        expect(onConfirm).toHaveBeenCalledWith(
            expect.arrayContaining([
                expect.objectContaining({ id: 2, bezeichnung: 'Kunststoffe' })
            ])
        );
    });
});
