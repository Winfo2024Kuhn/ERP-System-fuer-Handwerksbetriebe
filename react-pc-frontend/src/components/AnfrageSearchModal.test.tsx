import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AnfrageSearchModal } from './AnfrageSearchModal';

const mockFetch = vi.fn();
global.fetch = mockFetch as unknown as typeof fetch;

function jsonResponse(body: unknown): Response {
    return {
        ok: true,
        json: async () => body,
    } as Response;
}

describe('AnfrageSearchModal', () => {
    beforeEach(() => {
        mockFetch.mockReset();
        mockFetch.mockResolvedValue(jsonResponse([
            {
                id: 10,
                kundenId: 5,
                kundenName: 'Max Mustermann',
                bauvorhaben: 'Carport',
                anfragesnummer: 'AN-10',
            },
            {
                id: 11,
                kundenId: 6,
                kundenName: 'Erika Musterfrau',
                bauvorhaben: 'Geländer',
                anfragesnummer: 'AN-11',
            },
        ]));
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it('zeigt nur offene Anfragen des übergebenen Kunden und übernimmt die Auswahl', async () => {
        const user = userEvent.setup();
        const onSelect = vi.fn();
        const onClose = vi.fn();

        render(
            <AnfrageSearchModal
                isOpen
                kundenId={5}
                onSelect={onSelect}
                onClose={onClose}
            />
        );

        await waitFor(() => expect(screen.getByText('Carport')).toBeInTheDocument());
        expect(screen.queryByText('Geländer')).not.toBeInTheDocument();

        await user.click(screen.getByText('Carport'));

        expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({ id: 10 }));
        expect(onClose).toHaveBeenCalledOnce();
    });

    it('sendet den Suchbegriff nach dem Debounce an das Backend', async () => {
        vi.useFakeTimers({ shouldAdvanceTime: true });

        render(
            <AnfrageSearchModal
                isOpen
                kundenId={5}
                onSelect={vi.fn()}
                onClose={vi.fn()}
            />
        );

        const search = screen.getByPlaceholderText(/Freitext suchen/);
        await userEvent.setup({ advanceTimers: vi.advanceTimersByTime }).type(search, 'Carport');
        await act(async () => {
            await vi.advanceTimersByTimeAsync(350);
        });

        await waitFor(() => {
            expect(mockFetch.mock.calls.some(([url]) =>
                String(url).includes('q=Carport')
            )).toBe(true);
        });
    });
});
