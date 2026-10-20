import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ZeiterfassungZeitkonten from './ZeiterfassungZeitkonten';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { ToastProvider } from '../components/ui/toast';

const arbeitszeit = { montagStunden: 8, dienstagStunden: 8, mittwochStunden: 8, donnerstagStunden: 8, freitagStunden: 7.5, samstagStunden: 0, sonntagStunden: 0, buchungStartZeit: '06:00:00', buchungEndeZeit: '18:00:00' };
const ohneEnde = { ...arbeitszeit, buchungEndeZeit: null };
const version = { id: 9, version: 2, mitarbeiterId: 7, gueltigVon: '2026-03-15', gueltigBis: null, vorlageId: 3, arbeitszeit };
const status = { mitarbeiterId: 7, mitarbeiterVersion: 4, mitarbeiterName: 'Max Mustermann', fuehrtZeitkonto: true, eingerichtet: true, hinweis: null, aktuell: version, letzteVersion: version, historie: [version] };
const vergeben = { id: 3, version: 6, bezeichnung: 'Werkstatt Vollzeit', arbeitszeit, zugewiesen: true };
const frei = { id: 4, version: 1, bezeichnung: 'Teilzeit Büro', arbeitszeit: ohneEnde, zugewiesen: false };
const ergebnis = { zeitkonto: status, gueltigVon: '2026-09-09', gespeichert: false, bestehendeAbwesenheiten: 0, hinweis: 'Offene Monate werden neu gerechnet.', monate: [
    { jahr: 2026, monat: 9, abgeschlossen: false, saldoVorher: 10.5, saldoNachher: 8.25, geaendert: true },
    { jahr: 2026, monat: 10, abgeschlossen: false, saldoVorher: 1.333, saldoNachher: null, geaendert: true },
] };
const json = (body: unknown, statusCode = 200) => ({ ok: statusCode >= 200 && statusCode < 300, status: statusCode, json: async () => body }) as Response;

function renderSeite() { return render(<ToastProvider><ConfirmProvider><ZeiterfassungZeitkonten /></ConfirmProvider></ToastProvider>); }

describe('ZeiterfassungZeitkonten – Issue #270', () => {
    const fetchMock = vi.fn();
    beforeEach(() => {
        fetchMock.mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
            const url = String(input);
            if (url === '/api/zeitverwaltung/zeitkonten') return Promise.resolve(json([status]));
            if (url === '/api/zeitverwaltung/zeitkontenmodelle') return Promise.resolve(json([vergeben, frei]));
            if (url.startsWith('/api/zeitverwaltung/zeitkontenmodelle/4') && init?.method === 'DELETE') return Promise.resolve(json(null, 204));
            if (url.endsWith('/vorschau')) return Promise.resolve(json(ergebnis));
            return Promise.resolve(json({ message: 'Unbekannte Route' }, 404));
        });
        vi.stubGlobal('fetch', fetchMock);
    });
    afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });

    it('zeigt deutsches Datum, nur noch das späteste Ende und sperrt das Löschen zugewiesener Vorlagen', async () => {
        renderSeite();
        expect(await screen.findByText('Max Mustermann')).toBeInTheDocument();
        expect(screen.getByText(/Gültig ab 15\.3\.2026/)).toBeInTheDocument();
        expect(screen.getByText(/Spätestes Ende 18:00/)).toBeInTheDocument();
        expect(screen.queryByText(/Zeitfenster/)).not.toBeInTheDocument();
        expect(screen.getByText(/^39,5 h\/Woche$/)).toBeInTheDocument();

        const gesperrt = screen.getByRole('button', { name: 'Werkstatt Vollzeit löschen' });
        expect(gesperrt).toBeDisabled();
        expect(gesperrt).toHaveAccessibleDescription('Diese Vorlage war schon einem Mitarbeiter zugewiesen und gehört zur Arbeitszeit-Historie. Deshalb lässt sie sich nicht löschen.');
        expect(screen.getByRole('button', { name: 'Teilzeit Büro löschen' })).toBeEnabled();
    });

    it('fragt bei freien Vorlagen ohne falsches Versprechen nach und löscht danach', async () => {
        const user = userEvent.setup(); renderSeite(); await screen.findByText('Max Mustermann');
        await user.click(screen.getByRole('button', { name: 'Teilzeit Büro löschen' }));
        const dialog = await screen.findByRole('dialog', { name: 'Arbeitszeit-Vorlage löschen' });
        expect(within(dialog).getByText('„Teilzeit Büro“ wirklich löschen?')).toBeVisible();
        await user.click(within(dialog).getByRole('button', { name: 'Löschen' }));
        await waitFor(() => expect(fetchMock.mock.calls.some(([url, init]) => String(url) === '/api/zeitverwaltung/zeitkontenmodelle/4?expectedVersion=1' && init?.method === 'DELETE')).toBe(true));
    });

    it('zeigt die Salden der Monats-Vorschau mit Dezimalkomma', async () => {
        const user = userEvent.setup(); renderSeite(); await screen.findByText('Max Mustermann');
        await user.click(screen.getByRole('button', { name: /Arbeitszeit ändern/i }));
        await user.click(screen.getByRole('button', { name: 'Vorschau laden' }));
        expect(await screen.findByText('10,5 h → 8,25 h')).toBeVisible();
        expect(screen.getByText('1,33 h → — h')).toBeVisible();
    });
});
