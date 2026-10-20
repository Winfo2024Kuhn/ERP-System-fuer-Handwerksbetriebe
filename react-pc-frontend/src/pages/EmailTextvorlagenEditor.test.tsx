import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import EmailTextvorlagenEditor from './EmailTextvorlagenEditor';

const auth = vi.hoisted(() => ({ isAdmin: true }));
vi.mock('../auth/AuthContext', () => ({ useOptionalAuth: () => ({ isAdmin: auth.isAdmin }) }));
// Der Tiptap-Editor wird hier nicht gebraucht – nur die Rechte-Logik der Seite.
vi.mock('../components/TiptapEditor', () => ({ TiptapEditor: () => <div data-testid="editor" /> }));

// DSGVO: nur Dummy-Daten.
const VORLAGE = {
    id: 1,
    dokumentTyp: 'RECHNUNG',
    kategorie: 'DOKUMENT',
    name: 'Rechnung Standard',
    subjectTemplate: 'Rechnung {{DOKUMENTNUMMER}}',
    htmlBody: '<p>{{ANREDE}}, anbei die Rechnung.</p>',
    aktiv: true,
};

function antwort(body: unknown, status = 200) {
    return new Response(body === null ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

let fetchMock: ReturnType<typeof vi.fn>;
let loeschStatus = 204;

function rendere() {
    return render(
        <MemoryRouter>
            <ToastProvider>
                <ConfirmProvider>
                    <EmailTextvorlagenEditor />
                </ConfirmProvider>
            </ToastProvider>
        </MemoryRouter>
    );
}

describe('EmailTextvorlagenEditor – Mailtexte für Dokumente', () => {
    beforeEach(() => {
        auth.isAdmin = true;
        loeschStatus = 204;
        fetchMock = vi.fn((url: string, init?: RequestInit) => {
            if (init?.method === 'DELETE') return Promise.resolve(antwort(loeschStatus === 204 ? null : { message: 'verboten' }, loeschStatus));
            if (url === '/api/email-textvorlagen') return Promise.resolve(antwort([VORLAGE]));
            if (url === '/api/email-textvorlagen/dokumenttypen') {
                return Promise.resolve(antwort([{ value: 'RECHNUNG', label: 'Rechnung', kategorie: 'DOKUMENT', kategorieLabel: 'Dokumente' }]));
            }
            return Promise.resolve(antwort([]));
        });
        vi.stubGlobal('fetch', fetchMock);
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('heißt „Mailtexte für Dokumente“ und erklärt sich per Info-Knopf', async () => {
        const user = userEvent.setup();
        rendere();

        expect(screen.getByRole('heading', { level: 1, name: 'Mailtexte für Dokumente' })).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Mehr zu: Mailtexte für Dokumente' }));
        expect(await screen.findByText(/Bereits verschickte Mails bleiben unverändert/)).toBeInTheDocument();
    });

    it('zeigt Admins Neu, Bearbeiten und Löschen', async () => {
        rendere();

        expect(await screen.findByRole('heading', { name: 'Rechnung Standard' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Neue Vorlage/ })).toBeInTheDocument();
        expect(screen.getAllByRole('button', { name: /Bearbeiten/ }).length).toBeGreaterThan(0);
        expect(screen.getByRole('button', { name: 'Vorlage löschen' })).toBeInTheDocument();
        expect(screen.queryByText('Nur Admins können Mailtexte ändern.')).toBeNull();
    });

    it('blendet für Nicht-Admins alle Änderungs-Knöpfe aus und sagt warum', async () => {
        auth.isAdmin = false;
        rendere();

        expect(await screen.findByRole('heading', { name: 'Rechnung Standard' })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Neue Vorlage/ })).toBeNull();
        expect(screen.queryByRole('button', { name: /Bearbeiten/ })).toBeNull();
        expect(screen.queryByRole('button', { name: 'Vorlage löschen' })).toBeNull();
        expect(screen.queryByTitle('Löschen')).toBeNull();
        expect(screen.getAllByText('Nur Admins können Mailtexte ändern.').length).toBeGreaterThan(0);
    });

    it('fragt vor dem Löschen im eigenen Dialog nach und löscht erst nach Bestätigung', async () => {
        const user = userEvent.setup();
        rendere();

        await user.click(await screen.findByRole('button', { name: 'Vorlage löschen' }));
        const dialog = await screen.findByRole('dialog');
        await user.click(within(dialog).getByRole('button', { name: 'Abbrechen' }));
        expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false);

        await user.click(screen.getByRole('button', { name: 'Vorlage löschen' }));
        await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Löschen' }));

        await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/email-textvorlagen/1', { method: 'DELETE' }));
        expect(await screen.findByText('Vorlage gelöscht.')).toBeInTheDocument();
    });

    it('meldet ein 403 vom Server verständlich', async () => {
        loeschStatus = 403;
        const user = userEvent.setup();
        rendere();

        await user.click(await screen.findByRole('button', { name: 'Vorlage löschen' }));
        await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Löschen' }));

        expect(await screen.findByText('Nur Admins können Mailtexte ändern.')).toBeInTheDocument();
    });
});
