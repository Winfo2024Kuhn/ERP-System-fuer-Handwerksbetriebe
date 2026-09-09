import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { ToastProvider } from '../components/ui/toast';
import MitarbeiterEditor from './MitarbeiterEditor';

const MITARBEITER = {
    id: 42, vorname: 'Max', nachname: 'Mustermann', strasse: null, plz: null, ort: null,
    email: 'max.mustermann@example.invalid', telefon: null, festnetz: null, qualifikation: null,
    stundenlohn: null, geburtstag: null, eintrittsdatum: '2026-01-01', aktiv: true,
    fuehrtZeitkonto: true, abteilungIds: [], abteilungNames: null, loginToken: null, jahresUrlaub: 30,
};

const ARBEITSZEIT = {
    montagStunden: 8, dienstagStunden: 8, mittwochStunden: 8, donnerstagStunden: 8,
    freitagStunden: 8, samstagStunden: 0, sonntagStunden: 0, buchungStartZeit: null, buchungEndeZeit: null,
};

const STATUS = {
    mitarbeiterId: 42, mitarbeiterVersion: 7, mitarbeiterName: 'Max Mustermann', fuehrtZeitkonto: true,
    eingerichtet: false, hinweis: 'Bitte zuerst die Arbeitszeit einrichten.', aktuell: null, letzteVersion: null, historie: [],
};

function response(body: unknown, ok = true) {
    return Promise.resolve({ ok, json: () => Promise.resolve(body) });
}

function renderEditor() {
    return render(<MemoryRouter><ConfirmProvider><ToastProvider><MitarbeiterEditor /></ToastProvider></ConfirmProvider></MemoryRouter>);
}

describe('MitarbeiterEditor Task 8 – atomare Arbeitszeit', () => {
    let fetchMock: ReturnType<typeof vi.fn>;
    const requests: { url: string; init?: RequestInit }[] = [];

    beforeEach(() => {
        fetchMock = vi.fn((url: string, init?: RequestInit) => {
            requests.push({ url, init });
            if (url === '/api/mitarbeiter') return response([MITARBEITER]);
            if (url === '/api/abteilungen') return response([]);
            if (url.includes('/dokumente') || url.includes('/notizen') || url.includes('/lohnabrechnungen')) return response([]);
            if (url === '/api/zeitverwaltung/zeitkonten/42') {
                if (init?.method === 'PUT') {
                    const status = {
                        ...STATUS, eingerichtet: true, aktuell: { id: 11, version: 1, mitarbeiterId: 42, gueltigVon: '2026-09-09', gueltigBis: null, vorlageId: 3, arbeitszeit: ARBEITSZEIT },
                        letzteVersion: { id: 11, version: 1, mitarbeiterId: 42, gueltigVon: '2026-09-09', gueltigBis: null, vorlageId: 3, arbeitszeit: ARBEITSZEIT },
                        historie: [{ id: 11, version: 1, mitarbeiterId: 42, gueltigVon: '2026-09-09', gueltigBis: null, vorlageId: 3, arbeitszeit: ARBEITSZEIT }],
                    };
                    return response({ zeitkonto: status, gueltigVon: '2026-09-09', gespeichert: true, bestehendeAbwesenheiten: 1, hinweis: 'Arbeitszeit übernommen.', monate: [] });
                }
                return response(STATUS);
            }
            if (url === '/api/zeitverwaltung/zeitkontenmodelle') return response([{ id: 3, version: 2, bezeichnung: 'Vollzeit Werkstatt', arbeitszeit: ARBEITSZEIT }]);
            if (url === '/api/zeitverwaltung/zeitkonten/42/vorschau') return response({
                zeitkonto: STATUS, gueltigVon: '2026-09-09', gespeichert: false, bestehendeAbwesenheiten: 1,
                hinweis: 'Offene Monate werden neu gerechnet.', monate: [{ jahr: 2026, monat: 9, abgeschlossen: false, saldoVorher: 2, saldoNachher: null, geaendert: false }],
            });
            return response([]);
        });
        global.fetch = fetchMock as unknown as typeof fetch;
    });

    afterEach(() => vi.restoreAllMocks());

    it('zeigt die fehlende Einrichtung, erstellt eine Vorschau und speichert nur über den Wechsel-Endpunkt', async () => {
        const user = userEvent.setup();
        renderEditor();
        await user.click(await screen.findByText('Mustermann', { exact: true }));
        await user.click(screen.getByRole('button', { name: 'Bearbeiten' }));
        await screen.findByText('Bitte zuerst die Arbeitszeit einrichten.');

        await user.click(screen.getByRole('button', { name: 'Arbeitszeit einrichten' }));
        await user.click(screen.getByText('Vorlage auswählen'));
        await user.click(await screen.findByRole('option', { name: 'Vollzeit Werkstatt' }));
        await user.click(screen.getByRole('button', { name: 'Vorschau anzeigen' }));

        await screen.findByText('Offene Monate werden neu gerechnet.');
        const previewRequest = requests.find(request => request.url.endsWith('/vorschau'));
        expect(previewRequest?.init?.method).toBe('POST');
        expect(JSON.parse(String(previewRequest?.init?.body))).toMatchObject({
            expectedMitarbeiterVersion: 7, expectedLetzteVersionId: null, expectedLetzteVersion: null,
            vorlageId: 3, expectedVorlageVersion: 2, arbeitszeit: null,
        });

        await user.click(screen.getByRole('button', { name: 'Jetzt übernehmen' }));
        await screen.findByText('Arbeitszeit wurde übernommen.');
        await waitFor(() => expect(requests.some(request => request.url === '/api/zeitverwaltung/zeitkonten/42' && request.init?.method === 'PUT')).toBe(true));
        expect(requests.some(request => request.url === '/api/mitarbeiter/42' && request.init?.method === 'PUT')).toBe(false);
    });
});
