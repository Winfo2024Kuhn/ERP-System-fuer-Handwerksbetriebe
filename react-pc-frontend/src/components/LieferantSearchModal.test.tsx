import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { LieferantSearchModal } from './LieferantSearchModal';

afterEach(() => vi.unstubAllGlobals());

describe('LieferantSearchModal', () => {
    it('übernimmt einen optionalen Startsuchtext beim Öffnen', async () => {
        const fetchMock = vi.fn(async () => ({ ok: true, json: async () => ({ lieferanten: [] }) }));
        vi.stubGlobal('fetch', fetchMock);
        render(<LieferantSearchModal isOpen initialSearch="KI Baustoffe GmbH" onClose={vi.fn()} onSelect={vi.fn()} />);
        expect(screen.getByPlaceholderText('Suche nach Name, Ort, Typ, Vertreter...')).toHaveValue('KI Baustoffe GmbH');
        await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('q=KI+Baustoffe+GmbH'), expect.anything()));
    });
});
