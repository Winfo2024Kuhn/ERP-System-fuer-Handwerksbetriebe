import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { SonderzuschnittPicker } from './SonderzuschnittPicker';
import { ToastProvider } from './ui/toast';
afterEach(() => vi.unstubAllGlobals());

describe('Sonderzuschnitt mit aktuellem Formkatalog', () => {
    it('lädt passende Formen und übernimmt deutsche Winkel ohne alte Achsenschnittstelle', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify([{ id: 5, form: 'A', bildUrlSchnittbild: '/schnitt.png' }]), { status: 200 })));
        const onSubmit = vi.fn();
        render(<ToastProvider><SonderzuschnittPicker isOpen onClose={vi.fn()} onSubmit={onSubmit} artikelId={17} /></ToastProvider>);
        fireEvent.click(await screen.findByRole('button', { name: /Schnittbild 5/ }));
        fireEvent.change(screen.getByLabelText('Winkel links'), { target: { value: '45,5' } });
        fireEvent.click(screen.getByRole('button', { name: 'Übernehmen' }));
        expect(onSubmit).toHaveBeenCalledWith({ schnittForm: 'A', schnittbildBildUrl: '/schnitt.png', anschnittWinkelLinks: 45.5, anschnittWinkelRechts: 90 });
        expect(fetch).toHaveBeenCalledWith('/api/schnittbilder?artikelId=17', expect.anything());
        expect(screen.queryByRole('spinbutton')).not.toBeInTheDocument();
    });
    it('leert einen Nullwinkel und weist unvollständige Winkel zurück', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify([{ id: 5, form: 'A', bildUrlSchnittbild: '/schnitt.png' }]), { status: 200 })));
        const onSubmit = vi.fn();
        render(<ToastProvider><SonderzuschnittPicker isOpen onClose={vi.fn()} onSubmit={onSubmit} kategorieId={64} initial={{ schnittForm: 'A', anschnittWinkelLinks: 0 }} /></ToastProvider>);
        await waitFor(() => expect(screen.getByRole('button', { name: 'Übernehmen' })).toBeEnabled());
        fireEvent.focus(screen.getByLabelText('Winkel links'));
        expect(screen.getByLabelText('Winkel links')).toHaveValue('');
        fireEvent.change(screen.getByLabelText('Winkel links'), { target: { value: '45,' } });
        fireEvent.click(screen.getByRole('button', { name: 'Übernehmen' }));
        expect(onSubmit).not.toHaveBeenCalled();
        expect(await screen.findAllByText(/vollständige Zahl mit Dezimalkomma/)).not.toHaveLength(0);
    });
});
