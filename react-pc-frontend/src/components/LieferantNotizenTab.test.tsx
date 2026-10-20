import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { ConfirmProvider } from './ui/confirm-dialog';
import { ToastProvider } from './ui/toast';
import { LieferantNotizenTab } from './LieferantNotizenTab';
import type { LieferantNotiz } from '../types';

const NOTIZ: LieferantNotiz = { id: 5, text: 'Rückruf Max Mustermann', erstelltAm: '2026-10-01T10:00:00' } as LieferantNotiz;

let fetchMock: ReturnType<typeof vi.fn>;

function renderTab(notizen: LieferantNotiz[] = []) {
    return render(
        <ToastProvider>
            <ConfirmProvider>
                <LieferantNotizenTab lieferantId={1} notizen={notizen} />
            </ConfirmProvider>
        </ToastProvider>
    );
}

describe('LieferantNotizenTab – Fehlermeldungen', () => {
    beforeEach(() => {
        fetchMock = vi.fn(() => Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve({}) }));
        vi.stubGlobal('fetch', fetchMock);
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        vi.restoreAllMocks();
    });

    it('meldet, wenn die Notiz nicht gespeichert werden konnte, und behält den Text', async () => {
        const user = userEvent.setup();
        renderTab();

        await user.click(screen.getByRole('button', { name: /Neue Notiz/ }));
        await user.type(screen.getByPlaceholderText('Notiztext eingeben...'), 'Liefertermin klären');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        expect(await screen.findByText('Die Notiz konnte nicht gespeichert werden.')).toBeInTheDocument();
        expect(screen.getByPlaceholderText('Notiztext eingeben...')).toHaveValue('Liefertermin klären');
    });

    it('meldet, wenn die Notiz nicht gelöscht werden konnte, und lässt sie stehen', async () => {
        const user = userEvent.setup();
        renderTab([NOTIZ]);

        await user.click(screen.getByTitle('Notiz löschen'));
        await user.click(await screen.findByRole('button', { name: 'Löschen' }));

        expect(await screen.findByText('Die Notiz konnte nicht gelöscht werden.')).toBeInTheDocument();
        expect(screen.getByText(NOTIZ.text)).toBeInTheDocument();
    });

    it('meldet, wenn die Suche die Notizen nicht laden konnte', async () => {
        const user = userEvent.setup();
        renderTab();

        await user.type(screen.getByPlaceholderText('Notizen durchsuchen...'), 'blech');

        await waitFor(() => {
            expect(screen.getByText('Die Notizen konnten nicht geladen werden.')).toBeInTheDocument();
        });
    });
});
