import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import ZeiterfassungSteuerberater from './ZeiterfassungSteuerberater';

const { toast } = vi.hoisted(() => ({ toast: { error: vi.fn(), success: vi.fn(), warning: vi.fn(), info: vi.fn() } }));
vi.mock('../components/ui/toast', () => ({ useToast: () => toast }));
// Das Versandfenster meldet hier sofort Erfolg – getestet wird nur die Rückmeldung der Seite.
vi.mock('../components/SteuerberaterEmailModal', () => ({
    SteuerberaterEmailModal: ({ onSuccess }: { onSuccess?: () => void }) => (
        <button type="button" onClick={() => onSuccess?.()}>Versand simulieren</button>
    ),
}));

const arbeitszeit = { montagStunden: 8, dienstagStunden: 8, mittwochStunden: 8, donnerstagStunden: 8, freitagStunden: 8, samstagStunden: 0, sonntagStunden: 0, buchungStartZeit: '06:00', buchungEndeZeit: '18:00' };
const version = { id: 1, version: 1, mitarbeiterId: 7, gueltigVon: '2020-01-01', gueltigBis: null, vorlageId: 3, arbeitszeit };
const konto = { mitarbeiterId: 7, mitarbeiterVersion: 1, mitarbeiterName: 'Max Mustermann', fuehrtZeitkonto: true, eingerichtet: true, hinweis: null, aktuell: version, letzteVersion: version, historie: [] };
const json = (body: unknown) => ({ ok: true, status: 200, json: async () => body }) as Response;

beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
        const url = String(input);
        if (url === '/api/zeitverwaltung/zeitkonten') return Promise.resolve(json([konto]));
        if (url.includes('/api/zeitverwaltung/kalender')) return Promise.resolve(json({ sollStundenMonat: 0, tage: [] }));
        if (url.includes('/api/zeitverwaltung/monatsabschluesse/uebersicht')) {
            return Promise.resolve(json({ items: [], auswahl: [{ mitarbeiterId: 7, festgeschrieben: true }] }));
        }
        return Promise.resolve(json({}));
    }));
});
afterEach(() => { vi.unstubAllGlobals(); });

it('bestätigt den erfolgreichen Versand an den Steuerberater', async () => {
    const user = userEvent.setup();
    render(<ZeiterfassungSteuerberater />);
    await user.click(await screen.findByRole('button', { name: 'Vorschau laden' }));
    await user.click(await screen.findByRole('button', { name: /Per E-Mail senden/ }));
    await user.click(screen.getByRole('button', { name: 'Versand simulieren' }));
    expect(toast.success).toHaveBeenCalledWith('E-Mail an den Steuerberater wurde gesendet.');
    expect(screen.queryByRole('button', { name: 'Versand simulieren' })).not.toBeInTheDocument();
});
