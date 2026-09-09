import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ZeiterfassungKalender from './ZeiterfassungKalender';
const { toast, confirm } = vi.hoisted(() => ({ toast: { error: vi.fn(), success: vi.fn(), warning: vi.fn() }, confirm: vi.fn() }));
vi.mock('../components/ui/toast', () => ({ useToast: () => toast }));
vi.mock('../components/ui/confirm-dialog', () => ({ useConfirm: () => confirm }));
const mockFetch = vi.fn();
const year = new Date().getFullYear() - 1;
const base = `/api/zeitverwaltung/monatsabschluesse/1/${year}/8`;
const status = (closed = false) => ({ mitarbeiterId: 1, jahr: year, monat: 8, festgeschrieben: closed, version: 1,
    sollStunden: 120, gesamtIst: 125, differenz: 5, audit: closed ? [{ id: 1, aktion: 'ABSCHLIESSEN', akteurName: 'Max Mustermann', zeitpunkt: `${year}-09-01T10:00:00` }] : [] });
let closed: boolean, allowed: boolean, failure: number, statusFailure: boolean;
function setup() {
    mockFetch.mockImplementation(async (input: string, init?: RequestInit) => {
        let body: unknown = [];
        if (input === '/api/mitarbeiter') body = [{ id: 1, vorname: 'Max', nachname: 'Mustermann' }];
        if (input === '/api/zeitverwaltung/monatsabschluesse/berechtigung') body = { darfMonatAbschliessen: allowed };
        if (input.startsWith('/api/zeitverwaltung/kalender')) body = { tage: [], sollStundenMonat: 160, istStundenMonat: 168, differenz: 8 };
        if (input === base) {
            if (statusFailure) return { ok: false, status: 500, json: async () => ({ message: 'Monatsabschluss konnte nicht geladen werden.' }) };
            body = status(closed);
        }
        if (init?.method === 'POST') {
            if (failure) return { ok: false, status: failure, json: async () => ({ message: 'Der Monatsstand hat sich geändert.' }) };
            closed = input.endsWith('/abschliessen'); body = status(closed);
        }
        return { ok: true, json: async () => body };
    });
    vi.stubGlobal('fetch', mockFetch);
}
function mount(url = `/zeitbuchungen?jahr=${year}&monat=8&mitarbeiterId=1`) {
    return render(<MemoryRouter initialEntries={[url]}><ZeiterfassungKalender /></MemoryRouter>);
}
beforeEach(() => { vi.clearAllMocks(); closed = false; allowed = true; failure = 0; statusFailure = false; confirm.mockResolvedValue(true); setup(); });
describe('Monatsabschluss im Kalender', () => {
    it('übernimmt den Deep-Link, zeigt offene Stunden und schließt erst nach Bestätigung', async () => {
        mount();
        const button = await screen.findByRole('button', { name: 'Monat abschließen' });
        await waitFor(() => expect(button).toBeEnabled());
        expect(screen.getByText('168.0h')).toBeVisible();
        expect(mockFetch).toHaveBeenCalledWith(base, expect.anything());
        expect(mockFetch.mock.calls.filter(c => c[1]?.method === 'POST')).toHaveLength(0);
        fireEvent.click(button);
        await screen.findByRole('button', { name: 'Monat wieder öffnen' });
        expect(confirm).toHaveBeenCalled();
        expect(mockFetch).toHaveBeenCalledWith(base + '/abschliessen', { method: 'POST' });
        expect(screen.getByText('125.0h')).toBeVisible();
        expect(screen.getByText(/Verlauf der Monatsabschlüsse/)).toBeInTheDocument();
        expect(toast.success).toHaveBeenCalled();
    });
    it('öffnet wieder und lässt offene Salden sichtbar', async () => {
        closed = true; mount();
        const button = await screen.findByRole('button', { name: 'Monat wieder öffnen' });
        await waitFor(() => expect(button).toBeEnabled());
        fireEvent.click(button);
        await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(base + '/oeffnen', { method: 'POST' }));
        await waitFor(() => expect(screen.getByText('168.0h')).toBeVisible());
    });
    it('zeigt ohne Abschlussrecht weiterhin Zeiten und Status', async () => {
        allowed = false; mount();
        await screen.findByText(/Noch offen/);
        expect(screen.queryByRole('button', { name: 'Monat abschließen' })).not.toBeInTheDocument();
        expect(screen.getByText('168.0h')).toBeVisible();
        expect(mockFetch).toHaveBeenCalledWith('/api/zeitverwaltung/monatsabschluesse/berechtigung', expect.anything());
    });
    it('weist laufende und zukünftige Monate schon vor einem Schreibaufruf ab', async () => {
        const today = new Date();
        mount(`/zeitbuchungen?mitarbeiterId=1&jahr=${today.getFullYear()}&monat=${today.getMonth() + 1}`);
        const button = await screen.findByRole('button', { name: 'Monat abschließen' });
        expect(button).toBeDisabled();
        expect(screen.getByText('Nur vergangene Monate können abgeschlossen werden.')).toBeVisible();
        expect(mockFetch.mock.calls.filter(c => c[1]?.method === 'POST')).toHaveLength(0);
    });
    it('behandelt 409 mit Feedback und ohne falsche Erfolgsmeldung', async () => {
        failure = 409; mount();
        const button = await screen.findByRole('button', { name: 'Monat abschließen' });
        await waitFor(() => expect(button).toBeEnabled()); fireEvent.click(button);
        await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Der Monatsstand hat sich geändert.'));
        expect(toast.error).toHaveBeenCalledTimes(1);
        expect(toast.success).not.toHaveBeenCalled();
        expect(screen.getByText('168.0h')).toBeVisible();
    });
    it('meldet einen fehlgeschlagenen Statusabruf nur einmal und lässt Stunden sichtbar', async () => {
        statusFailure = true; mount();
        await screen.findByRole('button', { name: 'Monatsstand erneut laden' });
        expect(toast.error).toHaveBeenCalledTimes(1);
        expect(screen.getByText('168.0h')).toBeVisible();
    });
    it('Abbrechen schreibt nichts und der Deep-Link bleibt bei automatischer Mitarbeiterauswahl erhalten', async () => {
        confirm.mockResolvedValue(false); mount(`/zeitbuchungen?jahr=${year}&monat=8`);
        const button = await screen.findByRole('button', { name: 'Monat abschließen' });
        await waitFor(() => expect(button).toBeEnabled()); fireEvent.click(button);
        await waitFor(() => expect(confirm).toHaveBeenCalled());
        expect(mockFetch).toHaveBeenCalledWith(base, expect.anything());
        expect(mockFetch.mock.calls.filter(c => c[1]?.method === 'POST')).toHaveLength(0);
    });
});
