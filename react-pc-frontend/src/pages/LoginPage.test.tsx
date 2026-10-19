import { afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import LoginPage from './LoginPage';

const { auth, toast } = vi.hoisted(() => ({
    auth: {
        login: vi.fn(),
        register: vi.fn(),
        isAuthenticated: false,
        user: null,
        loading: false,
    },
    toast: { error: vi.fn(), success: vi.fn() },
}));
vi.mock('../auth/AuthContext', () => ({ useAuth: () => auth }));
vi.mock('../components/ui/toast', () => ({ useToast: () => toast }));

afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });

function stubStatus(antwort: () => Promise<Partial<Response>>) {
    const fetchMock = vi.fn(async () => antwort());
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

const zeige = () => render(<MemoryRouter><LoginPage /></MemoryRouter>);

describe('Anmeldeseite: „Konto anlegen“ nur beim allerersten Benutzer', () => {
    it('zeigt nur das Anmelden, sobald es Benutzer gibt', async () => {
        const fetchMock = stubStatus(async () => ({ ok: true, json: async () => ({ hasLoginUsers: true, setupRequired: false }) }));
        zeige();
        await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/auth/bootstrap-status'));
        expect(screen.getByRole('button', { name: /Anmelden/ })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Konto anlegen/ })).not.toBeInTheDocument();
    });

    it('bietet „Konto anlegen“ an, solange noch kein Benutzer existiert', async () => {
        stubStatus(async () => ({ ok: true, json: async () => ({ hasLoginUsers: false, setupRequired: true }) }));
        auth.register.mockResolvedValue({ success: true });
        zeige();
        fireEvent.click(await screen.findByRole('button', { name: /Konto anlegen/ }));
        expect(screen.getByRole('button', { name: /Konto erstellen/ })).toBeInTheDocument();
    });

    it('blendet „Konto anlegen“ nach dem ersten Konto aus', async () => {
        stubStatus(async () => ({ ok: true, json: async () => ({ hasLoginUsers: false, setupRequired: true }) }));
        auth.register.mockResolvedValue({ success: true });
        const { container } = zeige();
        fireEvent.click(await screen.findByRole('button', { name: /Konto anlegen/ }));
        const felder = container.querySelectorAll('input');
        fireEvent.change(felder[0], { target: { value: 'Max Mustermann' } });
        fireEvent.change(felder[1], { target: { value: 'max.mustermann' } });
        fireEvent.change(felder[2], { target: { value: 'geheim-genug' } });
        fireEvent.click(screen.getByRole('button', { name: /Konto erstellen/ }));
        await waitFor(() => expect(toast.success).toHaveBeenCalled());
        expect(screen.queryByRole('button', { name: /Konto anlegen/ })).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Anmelden/ })).toBeInTheDocument();
    });

    it('zeigt nur das Anmelden, wenn der Status nicht geladen werden kann', async () => {
        const fetchMock = stubStatus(async () => { throw new TypeError('Failed to fetch'); });
        zeige();
        await waitFor(() => expect(fetchMock).toHaveBeenCalled());
        expect(screen.getByRole('button', { name: /Anmelden/ })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Konto anlegen/ })).not.toBeInTheDocument();
    });

    it('zeigt nur das Anmelden bei einer Fehlerantwort', async () => {
        stubStatus(async () => ({ ok: false, status: 500, json: async () => ({}) }));
        zeige();
        await waitFor(() => expect(screen.getByRole('button', { name: /Anmelden/ })).toBeInTheDocument());
        expect(screen.queryByRole('button', { name: /Konto anlegen/ })).not.toBeInTheDocument();
    });
});
