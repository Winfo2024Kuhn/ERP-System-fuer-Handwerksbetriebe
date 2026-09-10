import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ZeiterfassungSteuerberater from './ZeiterfassungSteuerberater';

const alt = { montagStunden: 8, dienstagStunden: 8, mittwochStunden: 8, donnerstagStunden: 8, freitagStunden: 8, samstagStunden: 0, sonntagStunden: 0, buchungStartZeit: '06:00', buchungEndeZeit: '18:00' };
const neu = { ...alt, freitagStunden: 4 };
const konto = { mitarbeiterId: 7, mitarbeiterVersion: 2, mitarbeiterName: 'Max Mustermann', fuehrtZeitkonto: true, eingerichtet: true, hinweis: null, aktuell: { id: 2, version: 2, mitarbeiterId: 7, gueltigVon: '2026-09-01', gueltigBis: null, vorlageId: 3, arbeitszeit: neu }, letzteVersion: { id: 2, version: 2, mitarbeiterId: 7, gueltigVon: '2026-09-01', gueltigBis: null, vorlageId: 3, arbeitszeit: neu }, historie: [{ id: 1, version: 1, mitarbeiterId: 7, gueltigVon: '2026-01-01', gueltigBis: '2026-08-31', vorlageId: 3, arbeitszeit: alt }] };
const json = (body: unknown) => ({ ok: true, status: 200, json: async () => body }) as Response;

describe('ZeiterfassungSteuerberater Task 10', () => {
    const fetchMock = vi.fn();
    beforeEach(() => {
        fetchMock.mockImplementation((input: RequestInfo | URL) => {
            const url = String(input);
            if (url === '/api/zeitverwaltung/zeitkonten') return Promise.resolve(json([konto]));
            if (url.includes('/api/zeitverwaltung/kalender')) return Promise.resolve(json({ sollStundenMonat: 0, tage: [{ datum: '2026-05-01', wochentag: 5, istFeiertag: true, feiertagsStunden: 4, buchungen: [] }] }));
            return Promise.resolve(json({}));
        });
        vi.stubGlobal('fetch', fetchMock);
    });
    afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });

    it('übernimmt halbe Feiertage vom Backend und mittelt das historische Tagessoll', async () => {
        const user = userEvent.setup();
        render(<ZeiterfassungSteuerberater />);
        await screen.findByRole('button', { name: 'Vorschau laden' });
        const [monatAuswahl, jahrAuswahl] = screen.getAllByRole('combobox');
        await user.click(monatAuswahl);
        await user.click(screen.getByRole('option', { name: 'Mai', exact: true }));
        await user.click(jahrAuswahl);
        await user.click(screen.getByRole('option', { name: '2026', exact: true }));
        await user.click(screen.getByRole('button', { name: 'Vorschau laden' }));
        const zeile = (await screen.findByText('Max Mustermann')).closest('tr')!;
        expect(within(zeile).getByText('8', { selector: 'td' })).toBeInTheDocument();
        expect(within(zeile).getByText('4', { selector: 'td' })).toBeInTheDocument();
    });
});
