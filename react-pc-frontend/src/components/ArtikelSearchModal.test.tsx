import { fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ArtikelSearchModal } from './ArtikelSearchModal';
import { ToastProvider } from './ui/toast';

afterEach(() => vi.unstubAllGlobals());

describe('ArtikelSearchModal', () => {
    it('hat genau ein Schließen-X, das den Dialog schließt', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ artikel: [], gesamt: 0 }), { status: 200, headers: { 'Content-Type': 'application/json' } })));
        const onClose = vi.fn();
        render(<ToastProvider><ArtikelSearchModal isOpen onClose={onClose} onSelect={vi.fn()} /></ToastProvider>);
        const dialog = await screen.findByRole('dialog', { name: 'Artikel auswählen' });
        const schliessen = within(dialog).getAllByRole('button', { name: /schließen/i });
        expect(schliessen).toHaveLength(1);
        // Kein zusätzliches Icon-X ohne Beschriftung neben dem Dialog-X.
        expect(within(dialog).queryAllByRole('button', { name: '' })).toHaveLength(0);
        fireEvent.click(schliessen[0]);
        expect(onClose).toHaveBeenCalledTimes(1);
    });
});
