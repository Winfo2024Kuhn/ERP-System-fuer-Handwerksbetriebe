import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { LieferantSearchModal } from './LieferantSearchModal';

afterEach(() => vi.unstubAllGlobals());

describe('LieferantSearchModal', () => {
    it('übernimmt einen optionalen Startsuchtext beim Öffnen', async () => {
        const fetchMock = vi.fn(async () => ({ ok: true, json: async () => ({ lieferanten: [] }) }));
        vi.stubGlobal('fetch', fetchMock);
        render(<LieferantSearchModal isOpen initialSearch="KI Baustoffe GmbH" onClose={vi.fn()} onSelect={vi.fn()} />);
        expect(screen.getByPlaceholderText('Suche nach Name, Ort, Typ, Ansprechpartner …')).toHaveValue('KI Baustoffe GmbH');
        await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('q=KI+Baustoffe+GmbH'), expect.anything()));
    });

    it('zeigt den Ansprechpartner-Hinweis aus der Suche statt eines Vertreters', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => ({
            ok: true,
            json: async () => ({ lieferanten: [{ id: 1, lieferantenname: 'Musterstahl GmbH', ort: 'Musterstadt', ansprechpartnerHinweis: 'Max Mustermann +2' }] }),
        })));
        render(<LieferantSearchModal isOpen onClose={vi.fn()} onSelect={vi.fn()} />);
        expect(await screen.findByText(/Max Mustermann \+2/)).toBeInTheDocument();
    });
});
