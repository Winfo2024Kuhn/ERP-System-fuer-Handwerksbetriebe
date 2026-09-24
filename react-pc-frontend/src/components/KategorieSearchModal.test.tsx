import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { KategorieSearchModal } from './KategorieSearchModal';
import { ToastProvider } from './ui/toast';

const antwort = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } });
afterEach(() => vi.unstubAllGlobals());

describe('Kategorien aus dem Artikelstamm', () => {
    it('lädt den tatsächlichen API-Vertrag und wählt Unterkategorien mit ihrem Pfad', async () => {
        const fetchMock = vi.fn(async (url: RequestInfo | URL) => String(url) === '/api/artikel/kategorien/alle'
            ? antwort([{ id: 1, bezeichnung: 'Stahl', parentId: null, leaf: false }, { id: 2, bezeichnung: 'Profile', parentId: 1, leaf: true }])
            : antwort({}, 404));
        vi.stubGlobal('fetch', fetchMock);
        const onSelect = vi.fn();
        render(<ToastProvider><KategorieSearchModal isOpen onClose={vi.fn()} onSelect={onSelect} /></ToastProvider>);
        expect(await screen.findByRole('button', { name: 'Stahl', exact: true })).toBeVisible();
        fireEvent.click(screen.getByRole('button', { name: 'Ausklappen' }));
        fireEvent.click(screen.getByRole('button', { name: 'Profile', exact: true }));
        expect(onSelect).toHaveBeenCalledWith({ id: 2, beschreibung: 'Profile', parentId: 1 }, 'Stahl › Profile');
    });

    it('zeigt Ladefehler mit Wiederholung statt einer scheinbar leeren Liste', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(antwort({}, 503)).mockResolvedValueOnce(antwort([{ id: 1, bezeichnung: 'Stahl', parentId: null, leaf: true }])));
        render(<ToastProvider><KategorieSearchModal isOpen onClose={vi.fn()} onSelect={vi.fn()} /></ToastProvider>);
        await waitFor(() => expect(screen.getByRole('button', { name: 'Erneut laden' })).toBeVisible());
        expect(screen.queryByText('Keine Kategorien verfügbar')).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Erneut laden' }));
        expect(await screen.findByRole('button', { name: 'Stahl', exact: true })).toBeVisible();
    });

    it('hat genau ein Schließen-X, das den Dialog schließt', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => antwort([])));
        const onClose = vi.fn();
        render(<ToastProvider><KategorieSearchModal isOpen onClose={onClose} onSelect={vi.fn()} /></ToastProvider>);
        const dialog = await screen.findByRole('dialog', { name: 'Kategorie auswählen' });
        const schliessen = within(dialog).getAllByRole('button', { name: /schließen/i });
        expect(schliessen).toHaveLength(1);
        fireEvent.click(schliessen[0]);
        expect(onClose).toHaveBeenCalledTimes(1);
    });
});
