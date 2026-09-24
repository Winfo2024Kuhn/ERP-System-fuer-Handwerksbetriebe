import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import BenutzerEditor from './BenutzerEditor';

const user = { id: 42, displayName: 'Max Mustermann', username: 'max', shortCode: null, roles: ['USER'], active: true,
    defaultSignature: null, mitarbeiter: null, emailAbsender: null };

test('zeigt Profilrechte erst für ein gespeichertes, ausgewähltes Benutzerprofil', async () => {
    const fetchMock = vi.fn<typeof fetch>(async input => {
        if (String(input) === '/api/frontend-users') return new Response(JSON.stringify([user]), { status: 200 });
        if (String(input) === '/api/email/signatures' || String(input) === '/api/mitarbeiter' || String(input) === '/api/firma/email-absender') return new Response('[]', { status: 200 });
        if (String(input) === '/api/settings/einkauf-berechtigungen/42') return new Response(JSON.stringify(['LESEN']), { status: 200 });
        return new Response('{}', { status: 200 });
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<MemoryRouter><ToastProvider><ConfirmProvider><BenutzerEditor /></ConfirmProvider></ToastProvider></MemoryRouter>);
    expect(await screen.findByText('Max Mustermann')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalledWith('/api/settings/einkauf-berechtigungen/42', expect.anything());
    screen.getByText('Max Mustermann').click();
    await waitFor(() => expect(screen.getByLabelText('Einkauf lesen')).toBeChecked());
});

test('lädt Rechte für ein neu gespeichertes Profil erst nach erfolgreicher Anlage', async () => {
    let saved = false;
    const createdUser = { ...user, id: 43, displayName: 'Neues Profil', username: 'neu' };
    const fetchMock = vi.fn<typeof fetch>(async (input, init) => {
        const path = String(input);
        if (path === '/api/frontend-users' && init?.method === 'POST') {
            saved = true;
            return new Response(JSON.stringify({ id: 43 }), { status: 200 });
        }
        if (path === '/api/frontend-users') return new Response(JSON.stringify(saved ? [createdUser] : []), { status: 200 });
        if (path === '/api/settings/einkauf-berechtigungen/43') return new Response(JSON.stringify(['LESEN']), { status: 200 });
        if (path === '/api/email/signatures' || path === '/api/mitarbeiter' || path === '/api/firma/email-absender') return new Response('[]', { status: 200 });
        return new Response('{}', { status: 200 });
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<MemoryRouter><ToastProvider><ConfirmProvider><BenutzerEditor /></ConfirmProvider></ToastProvider></MemoryRouter>);
    expect(screen.queryByLabelText('Einkauf lesen')).not.toBeInTheDocument();
    fireEvent.change(await screen.findByLabelText('Anzeigename *'), { target: { value: 'Neues Profil' } });
    fireEvent.change(screen.getByLabelText('Benutzername *'), { target: { value: 'neu' } });
    fireEvent.change(screen.getByLabelText('Passwort *'), { target: { value: 'DummyPasswort123!' } });
    fireEvent.click(screen.getByRole('button', { name: 'Erstellen' }));
    await waitFor(() => expect(screen.getByLabelText('Einkauf lesen')).toBeChecked());
    expect(fetchMock).toHaveBeenCalledWith('/api/settings/einkauf-berechtigungen/43');
});
