import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ZeiterfassungZeitkonten from './ZeiterfassungZeitkonten';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { ToastProvider } from '../components/ui/toast';

const arbeitszeit = { montagStunden: 8, dienstagStunden: 8, mittwochStunden: 8, donnerstagStunden: 8, freitagStunden: 8, samstagStunden: 0, sonntagStunden: 0, buchungStartZeit: '06:00', buchungEndeZeit: '18:00' };
const status = { mitarbeiterId: 7, mitarbeiterVersion: 4, mitarbeiterName: 'Max Mustermann', fuehrtZeitkonto: true, eingerichtet: true, hinweis: null, aktuell: { id: 9, version: 2, mitarbeiterId: 7, gueltigVon: '2026-01-01', gueltigBis: null, vorlageId: 3, arbeitszeit }, letzteVersion: { id: 9, version: 2, mitarbeiterId: 7, gueltigVon: '2026-01-01', gueltigBis: null, vorlageId: 3, arbeitszeit }, historie: [{ id: 8, version: 1, mitarbeiterId: 7, gueltigVon: '2025-01-01', gueltigBis: '2025-12-31', vorlageId: 3, arbeitszeit }, { id: 9, version: 2, mitarbeiterId: 7, gueltigVon: '2026-01-01', gueltigBis: null, vorlageId: 3, arbeitszeit }] };
const modell = { id: 3, version: 6, bezeichnung: 'Werkstatt Vollzeit', arbeitszeit };
const ergebnis = { zeitkonto: status, gueltigVon: '2026-09-09', gespeichert: false, bestehendeAbwesenheiten: 0, hinweis: 'Offene Monate werden neu gerechnet.', monate: [{ jahr: 2026, monat: 8, abgeschlossen: true, saldoVorher: 12, saldoNachher: 12, geaendert: false }, { jahr: 2026, monat: 9, abgeschlossen: false, saldoVorher: 10, saldoNachher: 8, geaendert: true }] };
const json = (body: unknown, statusCode = 200) => ({ ok: statusCode >= 200 && statusCode < 300, status: statusCode, json: async () => body }) as Response;

function renderSeite() { return render(<ToastProvider><ConfirmProvider><ZeiterfassungZeitkonten /></ConfirmProvider></ToastProvider>); }

describe('ZeiterfassungZeitkonten Task 9b', () => {
    const fetchMock = vi.fn();
    beforeEach(() => { fetchMock.mockImplementation((input: RequestInfo | URL) => { const url = String(input); if (url === '/api/zeitverwaltung/zeitkonten') return Promise.resolve(json([status])); if (url.startsWith('/api/zeitverwaltung/zeitkontenmodelle')) return Promise.resolve(json(url.endsWith('zeitkontenmodelle') ? [modell] : modell)); if (url.endsWith('/vorschau')) return Promise.resolve(json(ergebnis)); if (url === '/api/zeitverwaltung/zeitkonten/7') return Promise.resolve(json({ ...ergebnis, gespeichert: true })); return Promise.resolve(json({ message: 'Unbekannte Route' }, 404)); }); vi.stubGlobal('fetch', fetchMock); });
    afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });

    it('liest die aktuelle Arbeitszeit aus dem versionierten Status und zeigt die Historie an', async () => {
        renderSeite();
        expect(await screen.findByText('Max Mustermann')).toBeInTheDocument();
        expect(screen.getByText(/Gültig ab 2026-01-01/)).toBeInTheDocument();
        expect(screen.getByText(/2 Zeitabschnitt/)).toBeInTheDocument();
        expect(screen.getByText('Werkstatt Vollzeit')).toBeInTheDocument();
    });

    it('fordert vor einer persönlichen Übernahme eine Vorschau mit Versionsständen an', async () => {
        const user = userEvent.setup(); renderSeite(); await screen.findByText('Max Mustermann');
        await user.click(screen.getByRole('button', { name: /Arbeitszeit ändern/i }));
        await user.click(screen.getByRole('button', { name: 'Vorschau laden' }));
        await screen.findByText('Abgeschlossen · unverändert');
        const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/zeitkonten/7/vorschau'));
        expect(call).toBeDefined();
        expect(JSON.parse(call![1].body)).toMatchObject({ expectedMitarbeiterVersion: 4, expectedLetzteVersionId: 9, expectedLetzteVersion: 2, vorlageId: 3, expectedVorlageVersion: 6, arbeitszeit });
        expect(screen.getByRole('button', { name: 'Übernehmen' })).toBeEnabled();
    });

    it('behält Vorlage, Versionsstand und abweichende Stunden für die Übernahme bei', async () => {
        const user = userEvent.setup(); renderSeite(); await screen.findByText('Max Mustermann');
        await user.click(screen.getByRole('button', { name: /Arbeitszeit ändern/i }));
        await user.clear(screen.getByRole('spinbutton', { name: 'Freitag Stunden' }));
        await user.type(screen.getByRole('spinbutton', { name: 'Freitag Stunden' }), '6');
        await user.click(screen.getByRole('button', { name: 'Vorschau laden' }));
        await screen.findByText('Abgeschlossen · unverändert');
        await user.click(screen.getByRole('button', { name: 'Übernehmen' }));
        const save = fetchMock.mock.calls.find(([url, init]) => String(url) === '/api/zeitverwaltung/zeitkonten/7' && (init as RequestInit).method === 'PUT');
        expect(JSON.parse((save![1] as RequestInit).body as string)).toMatchObject({ vorlageId: 3, expectedVorlageVersion: 6, arbeitszeit: { freitagStunden: 6 } });
    });

    it('zeigt nach dem Speichern einer Vorlage keine automatische Auswahl zur Übernahme', async () => {
        const user = userEvent.setup(); renderSeite(); await screen.findByText('Werkstatt Vollzeit');
        await user.click(screen.getByRole('button', { name: /Bearbeiten/i }));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        await screen.findByText(/Vorlagenänderung übernehmen/);
        expect(screen.getByRole('checkbox', { name: 'Max Mustermann auswählen' })).not.toBeChecked();
        expect(screen.getByRole('button', { name: /Für Auswahl übernehmen/i })).toBeDisabled();
    });
});
