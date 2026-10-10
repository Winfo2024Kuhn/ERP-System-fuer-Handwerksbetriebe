import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import BenutzerEditor from './BenutzerEditor';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';

/** Dummy-Daten (DSGVO). */
const benutzer = {
    id: 7, displayName: 'Max Mustermann', username: 'max', shortCode: 'MM', roles: ['USER'], active: true,
    defaultSignature: null, mitarbeiter: null,
    emailAbsender: { id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: 'Musterbetrieb' },
};
const postfach = (id: number, emailAdresse: string, anzeigename: string | null, aktiv = true) => ({
    id, emailAdresse, anzeigename, aktiv, sortierung: id, hauptpostfach: id === 3, fuerGeschaeftsdokumente: false,
    benutzername: emailAdresse, passwortGesetzt: true, smtpHost: null, smtpPort: null, imapHost: null, imapPort: null,
    abrufAktiv: false, letzterAbrufAm: null, letzterAbrufFehler: null, zugewieseneBenutzer: [],
});

function stubFetch(postfaecherOk = true, speichernOk = true) {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
        if (url === '/api/frontend-users' && !init?.method) return json([benutzer, { ...benutzer, id: 8, displayName: 'Erika Musterfrau', username: null, shortCode: null, roles: ['ADMIN'], active: false, emailAbsender: null }]);
        if (url === '/api/frontend-users') return json(benutzer, speichernOk ? 200 : 400);
        if (url.startsWith('/api/frontend-users/')) return json({}, speichernOk ? 200 : 500);
        if (url === '/api/email/signatures') return json([{ id: 1, name: 'Standard' }]);
        if (url === '/api/mitarbeiter') return json([{ id: 2, vorname: 'Max', nachname: 'Mustermann' }]);
        if (url === '/api/postfaecher') {
            return postfaecherOk
                ? json([postfach(3, 'info@musterbetrieb.example', 'Musterbetrieb'), postfach(9, 'max@musterbetrieb.example', null, false)])
                : json({ message: 'Nur für Administratoren.' }, 403);
        }
        return json([]);
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

function renderEditor() {
    render(<MemoryRouter><ToastProvider><ConfirmProvider><BenutzerEditor /></ConfirmProvider></ToastProvider></MemoryRouter>);
}

afterEach(() => vi.unstubAllGlobals());

describe('BenutzerEditor – Eigenes Postfach', () => {
    it('lädt die Postfächer aus /api/postfaecher und speichert die Wahl als emailAbsenderId', async () => {
        const user = userEvent.setup();
        const fetchMock = stubFetch();
        renderEditor();

        await user.click(await screen.findByText('Max Mustermann'));
        const feld = screen.getByRole('combobox', { name: 'Eigenes Postfach' });
        await waitFor(() => expect(feld).toHaveTextContent('Musterbetrieb <info@musterbetrieb.example>'));
        await user.click(feld);
        expect(screen.getByRole('option', { name: 'Kein eigenes Postfach (Hauptpostfach)' })).toBeInTheDocument();
        await user.click(screen.getByRole('option', { name: 'max@musterbetrieb.example (ausgeschaltet)' }));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(fetchMock.mock.calls.some(([url, init]) => String(url) === '/api/frontend-users' && init?.method === 'POST')).toBe(true));
        const call = fetchMock.mock.calls.find(([url, init]) => String(url) === '/api/frontend-users' && init?.method === 'POST')!;
        expect(JSON.parse(String(call[1]?.body))).toMatchObject({ id: 7, emailAbsenderId: 9 });
        expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/api/firma/email-absender'))).toBe(false);
    });

    it('meldet, wenn die Postfächer nicht geladen werden können', async () => {
        stubFetch(false);
        renderEditor();
        expect(await screen.findByText('Postfächer konnten nicht geladen werden.')).toBeInTheDocument();
    });
});

describe('BenutzerEditor – Anlegen und Löschen', () => {
    it('legt einen neuen Benutzer mit eigenem Postfach an und prüft das Passwort', async () => {
        const user = userEvent.setup();
        const fetchMock = stubFetch();
        renderEditor();
        await screen.findByText('Erika Musterfrau');
        expect(screen.getByText('kein Login')).toBeInTheDocument();
        expect(screen.getByText('Inaktiv')).toBeInTheDocument();

        await user.type(screen.getByLabelText('Anzeigename *'), 'Max Mustermann');
        await user.type(screen.getByLabelText('Benutzername *'), 'max');
        await user.type(screen.getByLabelText(/Passwort/), 'kurz');
        // Zu kurzes Passwort: Erstellen bleibt gesperrt.
        expect(screen.getByRole('button', { name: 'Erstellen' })).toBeDisabled();

        await user.type(screen.getByLabelText(/Passwort/), '-und-lang');
        await user.type(screen.getByLabelText('Kürzel (optional)'), 'MM');
        await user.click(screen.getByRole('checkbox', { name: 'Aktiv' }));
        await user.click(screen.getByRole('combobox', { name: 'Eigenes Postfach' }));
        await user.click(screen.getByRole('option', { name: 'Musterbetrieb <info@musterbetrieb.example>' }));
        await user.click(screen.getByRole('button', { name: 'Erstellen' }));

        await waitFor(() => expect(fetchMock.mock.calls.some(([url, init]) => String(url) === '/api/frontend-users' && init?.method === 'POST')).toBe(true));
        const call = fetchMock.mock.calls.find(([url, init]) => String(url) === '/api/frontend-users' && init?.method === 'POST')!;
        expect(JSON.parse(String(call[1]?.body))).toMatchObject({ id: null, emailAbsenderId: 3, active: false, shortCode: 'MM', password: 'kurz-und-lang' });
    });

    it('meldet Speicherfehler', async () => {
        const user = userEvent.setup();
        stubFetch(true, false);
        renderEditor();
        await user.click(await screen.findByText('Max Mustermann'));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByText('Fehler beim Speichern.')).toBeInTheDocument();
    });

    it('löscht nach Bestätigung und meldet Fehler', async () => {
        const user = userEvent.setup();
        const fetchMock = stubFetch(true, false);
        renderEditor();
        await screen.findByText('Erika Musterfrau');
        await user.click(screen.getAllByTitle('Benutzer löschen')[1]);
        await user.click(await screen.findByRole('button', { name: 'Löschen' }));
        await waitFor(() => expect(fetchMock.mock.calls.some(([url, init]) => String(url) === '/api/frontend-users/8' && init?.method === 'DELETE')).toBe(true));
        expect(await screen.findByText('Fehler beim Löschen des Benutzers.')).toBeInTheDocument();
    });
});

describe('BenutzerEditor – Laden und erfolgreiches Löschen', () => {
    it('meldet Netzwerkfehler beim Laden', async () => {
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
        renderEditor();
        expect(await screen.findByText('Benutzer konnten nicht geladen werden.')).toBeInTheDocument();
        expect(screen.getByText('Keine Benutzer vorhanden')).toBeInTheDocument();
    });

    it('löscht erfolgreich und bricht ohne Bestätigung ab', async () => {
        const user = userEvent.setup();
        const fetchMock = stubFetch();
        renderEditor();
        await screen.findByText('Erika Musterfrau');
        await user.click(screen.getAllByTitle('Benutzer löschen')[1]);
        await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Abbrechen' }));
        expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false);

        await user.click(screen.getAllByTitle('Benutzer löschen')[1]);
        await user.click(await screen.findByRole('button', { name: 'Löschen' }));
        await waitFor(() => expect(fetchMock.mock.calls.some(([url, init]) => String(url) === '/api/frontend-users/8' && init?.method === 'DELETE')).toBe(true));
        expect(screen.queryByText('Fehler beim Löschen des Benutzers.')).not.toBeInTheDocument();
    });
});
