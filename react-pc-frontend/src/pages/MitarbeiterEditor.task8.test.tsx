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
    let vorschauAnzahl: number;
    let alteVorschauAufloesen: ((wert: Awaited<ReturnType<typeof response>>) => void) | null;

    beforeEach(() => {
        // Der Stichtag ist standardmaessig "heute". Nur die Uhr faelschen, nicht
        // die Timer: userEvent und waitFor brauchen weiterhin echte setTimeout.
        vi.useFakeTimers({ toFake: ['Date'] });
        vi.setSystemTime(new Date(2026, 8, 9, 10, 0));
        requests.length = 0;
        vorschauAnzahl = 0;
        alteVorschauAufloesen = null;
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
            if (url === '/api/zeitverwaltung/zeitkonten/42/vorschau') {
                const ergebnis = {
                    zeitkonto: STATUS, gueltigVon: '2026-09-09', gespeichert: false, bestehendeAbwesenheiten: 1,
                    hinweis: 'Offene Monate werden neu gerechnet.', monate: [{ jahr: 2026, monat: 9, abgeschlossen: false, saldoVorher: 2, saldoNachher: null, geaendert: false }],
                };
                vorschauAnzahl += 1;
                if (vorschauAnzahl === 1) return new Promise(resolve => { alteVorschauAufloesen = resolve; });
                return response(ergebnis);
            }
            return response([]);
        });
        global.fetch = fetchMock as unknown as typeof fetch;
    });

    afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); });

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
        alteVorschauAufloesen?.(await response({
            zeitkonto: STATUS, gueltigVon: '2026-09-09', gespeichert: false, bestehendeAbwesenheiten: 1,
            hinweis: 'Offene Monate werden neu gerechnet.', monate: [{ jahr: 2026, monat: 9, abgeschlossen: false, saldoVorher: 2, saldoNachher: null, geaendert: false }],
        }));

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

    it('verwirft eine verspätete Vorschau nach Stichtagwechsel und sendet beim Übernehmen den neuen Stichtag', async () => {
        const user = userEvent.setup();
        renderEditor();
        await user.click(await screen.findByText('Mustermann', { exact: true }));
        await user.click(screen.getByRole('button', { name: 'Bearbeiten' }));
        await user.click(await screen.findByRole('button', { name: 'Arbeitszeit einrichten' }));
        await user.click(screen.getByText('Vorlage auswählen'));
        await user.click(await screen.findByRole('option', { name: 'Vollzeit Werkstatt' }));

        await user.click(screen.getByText('09.09.2026'));
        await user.click(screen.getByTitle('Nächster Monat'));
        await user.click(screen.getByRole('button', { name: '01.10.2026', exact: true }));
        await user.click(screen.getByRole('button', { name: 'Vorschau anzeigen' }));

        await user.click(screen.getByText('01.10.2026'));
        await user.click(screen.getByTitle('Nächster Monat'));
        await user.click(screen.getByRole('button', { name: '01.11.2026', exact: true }));
        alteVorschauAufloesen?.(await response({ zeitkonto: STATUS, gueltigVon: '2026-10-01', gespeichert: false, bestehendeAbwesenheiten: 0, hinweis: 'Alte Vorschau', monate: [] }));
        await waitFor(() => expect(screen.queryByRole('button', { name: 'Jetzt übernehmen' })).not.toBeInTheDocument());

        await user.click(screen.getByRole('button', { name: 'Vorschau anzeigen' }));
        await screen.findByText('Offene Monate werden neu gerechnet.');
        await user.click(screen.getByRole('button', { name: 'Jetzt übernehmen' }));
        const saveRequest = requests.find(request => request.url === '/api/zeitverwaltung/zeitkonten/42' && request.init?.method === 'PUT');
        expect(JSON.parse(String(saveRequest?.init?.body))).toMatchObject({ gueltigVon: '2026-11-01' });
    });

    it('behält die Arbeitszeit-Vorlage bei einer individuellen Abweichung im Wechsel-Request', async () => {
        const user = userEvent.setup();
        renderEditor();
        await user.click(await screen.findByText('Mustermann', { exact: true }));
        await user.click(screen.getByRole('button', { name: 'Bearbeiten' }));
        await user.click(await screen.findByRole('button', { name: 'Arbeitszeit einrichten' }));
        await user.click(screen.getByText('Vorlage auswählen'));
        await user.click(await screen.findByRole('option', { name: 'Vollzeit Werkstatt' }));
        await user.click(screen.getByLabelText('Diese Vorlage für diese Person individuell anpassen'));
        const montag = screen.getByRole('textbox', { name: 'Montag Stunden' });
        await user.clear(montag);
        await user.type(montag, '7,5');
        await user.click(screen.getByRole('button', { name: 'Vorschau anzeigen' }));
        alteVorschauAufloesen?.(await response({ zeitkonto: STATUS, gueltigVon: '2026-09-09', gespeichert: false, bestehendeAbwesenheiten: 0, hinweis: 'Offene Monate werden neu gerechnet.', monate: [] }));
        await screen.findByText('Offene Monate werden neu gerechnet.');
        const previewRequest = requests.find(request => request.url.endsWith('/vorschau'));
        expect(JSON.parse(String(previewRequest?.init?.body))).toMatchObject({
            vorlageId: 3, expectedVorlageVersion: 2, arbeitszeit: { montagStunden: 7.5 },
        });
    });
    it('leert Null bei Klick und Tab und blockiert unvollständige Stunden und Uhrzeiten vor Vorschau', async () => {
        const user=userEvent.setup();renderEditor();
        await user.click(await screen.findByText('Mustermann',{exact:true}));
        await user.click(screen.getByRole('button',{name:'Bearbeiten'}));
        await user.click(await screen.findByRole('button',{name:'Arbeitszeit einrichten'}));
        await user.click(screen.getByText('Vorlage auswählen'));
        await user.click(await screen.findByRole('option',{name:'Vollzeit Werkstatt'}));
        await user.click(screen.getByLabelText('Diese Vorlage für diese Person individuell anpassen'));
        const samstag=screen.getByRole('textbox',{name:'Samstag Stunden'});
        expect(samstag).toHaveValue('0');await user.click(samstag);expect(samstag).toHaveValue('');
        await user.click(screen.getByRole('button',{name:'Vorschau anzeigen'}));
        expect(requests.some(r=>r.url.endsWith('/vorschau'))).toBe(false);
        await user.type(samstag,'24,1');await user.click(screen.getByRole('button',{name:'Vorschau anzeigen'}));
        expect(requests.some(r=>r.url.endsWith('/vorschau'))).toBe(false);
        await user.clear(samstag);await user.type(samstag,'8,5');await user.tab();
        const sonntag=screen.getByRole('textbox',{name:'Sonntag Stunden'});expect(sonntag).toHaveFocus();expect(sonntag).toHaveValue('');
        await user.type(sonntag,'0');
        const start=screen.getByRole('textbox',{name:'Früheste Buchung – optional'});
        await user.type(start,'25:99');await user.click(screen.getByRole('button',{name:'Vorschau anzeigen'}));
        expect(requests.some(r=>r.url.endsWith('/vorschau'))).toBe(false);
        await user.clear(start);await user.type(start,'09:05');await user.click(screen.getByRole('button',{name:'Vorschau anzeigen'}));
        const req=requests.find(r=>r.url.endsWith('/vorschau'));
        expect(JSON.parse(String(req?.init?.body))).toMatchObject({arbeitszeit:{samstagStunden:8.5,sonntagStunden:0,buchungStartZeit:'09:05'}});
    });

    it('erklärt für Geschäftsführer die optionale Arbeitszeit und zeigt Button Arbeitszeit optional einrichten', async () => {
        const gf = { ...MITARBEITER, id: 43, istGeschaeftsfuehrer: true };
        // Entspricht dem echten Backend-Contract (ZeitkontoWechselService.status):
        // Fuer Geschaeftsfuehrer ist eingerichtet immer true, aktuell bleibt ohne
        // hinterlegte Arbeitszeit null.
        const gfStatus = {
            ...STATUS, mitarbeiterId: 43, istGeschaeftsfuehrer: true, eingerichtet: true, aktuell: null,
            hinweis: 'Geschäftsführung: Arbeitszeit kann optional hinterlegt werden, ist für die Zeiterfassung jedoch nicht erforderlich.',
        };
        fetchMock.mockImplementation((url: string) => {
            if (url === '/api/mitarbeiter') return response([gf]);
            if (url === '/api/zeitverwaltung/zeitkonten/43') return response(gfStatus);
            if (url === '/api/abteilungen') return response([]);
            return response([]);
        });
        const user = userEvent.setup();
        renderEditor();
        await user.click(await screen.findByText('Mustermann', { exact: true }));
        await user.click(screen.getByRole('button', { name: 'Bearbeiten' }));

        expect(await screen.findByText(/Hinweis für Geschäftsführer/i)).toBeInTheDocument();
        expect(screen.getByText(/Als Geschäftsführer müssen Sie keine Arbeitszeit einrichten/i)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Arbeitszeit optional einrichten' })).toBeVisible();
    });

});
