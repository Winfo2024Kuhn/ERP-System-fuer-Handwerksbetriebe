import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ZeiterfassungZeitkonten from './ZeiterfassungZeitkonten';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { ToastProvider } from '../components/ui/toast';

const arbeitszeit = { montagStunden: 8, dienstagStunden: 8, mittwochStunden: 8, donnerstagStunden: 8, freitagStunden: 8, samstagStunden: 0, sonntagStunden: 0, buchungStartZeit: '06:00', buchungEndeZeit: '18:00' };
const status = { mitarbeiterId: 7, mitarbeiterVersion: 4, mitarbeiterName: 'Max Mustermann', fuehrtZeitkonto: true, eingerichtet: true, hinweis: null, aktuell: { id: 9, version: 2, mitarbeiterId: 7, gueltigVon: '2026-01-01', gueltigBis: null, vorlageId: 3, arbeitszeit }, letzteVersion: { id: 9, version: 2, mitarbeiterId: 7, gueltigVon: '2026-01-01', gueltigBis: null, vorlageId: 3, arbeitszeit }, historie: [] };
const modell = { id: 3, version: 6, bezeichnung: 'Werkstatt Vollzeit', arbeitszeit };
const ergebnis = { zeitkonto: status, gueltigVon: '2026-10-01', gespeichert: false, bestehendeAbwesenheiten: 0, hinweis: 'Offene Monate werden neu gerechnet.', monate: [] };
const json = (body: unknown, statusCode = 200) => ({ ok: statusCode >= 200 && statusCode < 300, status: statusCode, json: async () => body }) as Response;

function renderSeite() { return render(<ToastProvider><ConfirmProvider><ZeiterfassungZeitkonten /></ConfirmProvider></ToastProvider>); }

describe('ZeiterfassungZeitkonten Task 10', () => {
    const fetchMock = vi.fn();
    beforeEach(() => {
        fetchMock.mockClear();
        fetchMock.mockImplementation((input: RequestInfo | URL) => {
            const url = String(input);
            if (url === '/api/zeitverwaltung/zeitkonten') return Promise.resolve(json([status]));
            if (url.startsWith('/api/zeitverwaltung/zeitkontenmodelle')) return Promise.resolve(json(url.endsWith('zeitkontenmodelle') ? [modell] : modell));
            if (url.endsWith('/vorschau')) return Promise.resolve(json(ergebnis));
            return Promise.resolve(json({ ...ergebnis, gespeichert: true }));
        });
        vi.stubGlobal('fetch', fetchMock);
    });
    afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });

    async function oeffneMehrfachdialog(user: ReturnType<typeof userEvent.setup>) {
        renderSeite();
        await screen.findByText('Werkstatt Vollzeit');
        await user.click(screen.getByRole('button', { name: 'Bearbeiten' }));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        await screen.findByText(/Vorlagenänderung übernehmen/);
    }

    it('verwirft eine verspätete Mehrfachvorschau nach einer Stichtagsänderung', async () => {
        const user = userEvent.setup();
        const resolvePreviews: Array<(response: Response) => void> = [];
        fetchMock.mockImplementation((input: RequestInfo | URL) => {
            const url = String(input);
            if (url === '/api/zeitverwaltung/zeitkonten') return Promise.resolve(json([status]));
            if (url.startsWith('/api/zeitverwaltung/zeitkontenmodelle')) return Promise.resolve(json(url.endsWith('zeitkontenmodelle') ? [modell] : modell));
            if (url.endsWith('/vorschau')) return new Promise<Response>(resolve => { resolvePreviews.push(resolve); });
            return Promise.resolve(json(modell));
        });
        await oeffneMehrfachdialog(user);
        await user.click(screen.getByRole('button', { name: 'Vorschau' }));
        await user.click(screen.getByText(/^\d{2}\.\d{2}\.\d{4}$/));
        await user.click(screen.getByTitle('Nächster Monat'));
        await user.click(screen.getByRole('button', { name: /^01\.\d{2}\.\d{4}$/ }));
        await act(async () => { resolvePreviews[0](json({ ...ergebnis, hinweis: 'Oktober-Antwort' })); });
        await waitFor(() => expect(screen.queryByText('Oktober-Antwort')).not.toBeInTheDocument());
        expect(screen.getByRole('checkbox', { name: 'Max Mustermann auswählen' })).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Vorschau' })).toBeEnabled();
        await user.click(screen.getByRole('button', { name: 'Vorschau' }));
        await waitFor(() => expect(resolvePreviews).toHaveLength(2));
        await act(async () => { resolvePreviews[1](json({ ...ergebnis, hinweis: 'November-Antwort' })); });
        await waitFor(() => expect(screen.getByRole('checkbox', { name: 'Max Mustermann auswählen' })).toBeEnabled());
    });

    it('warnt je Kandidat vor unveränderten gebuchten Urlaubsgutschriften', async () => {
        const user = userEvent.setup();
        fetchMock.mockImplementation((input: RequestInfo | URL) => {
            const url = String(input);
            if (url === '/api/zeitverwaltung/zeitkonten') return Promise.resolve(json([status]));
            if (url.startsWith('/api/zeitverwaltung/zeitkontenmodelle')) return Promise.resolve(json(url.endsWith('zeitkontenmodelle') ? [modell] : modell));
            if (url.endsWith('/vorschau')) return Promise.resolve(json({ ...ergebnis, bestehendeAbwesenheiten: 2, hinweis: 'Urlaub bleibt gebucht.' }));
            return Promise.resolve(json(modell));
        });
        await oeffneMehrfachdialog(user);
        await user.click(screen.getByRole('button', { name: 'Vorschau' }));
        expect(await screen.findByText(/2 bereits gebuchte Abwesenheitsgutschrift/)).toBeInTheDocument();
        expect(screen.getByText(/Urlaub bleibt gebucht/)).toBeInTheDocument();
    });
    it('behält Null-Leerzustände und deutsche Stunden bis zur vollständig geprüften Übernahme',async()=>{
        const user=userEvent.setup();renderSeite();await screen.findByText('Werkstatt Vollzeit');
        await user.click(screen.getByRole('button',{name:'Arbeitszeit ändern'}));
        const montag=screen.getByRole('textbox',{name:'Montag Stunden'});expect(montag).toHaveValue('8');
        const samstag=screen.getByRole('textbox',{name:'Samstag Stunden'});await user.click(samstag);expect(samstag).toHaveValue('');
        await user.click(screen.getByRole('button',{name:'Vorschau laden'}));expect(fetchMock.mock.calls.some(c=>String(c[0]).endsWith('/vorschau'))).toBe(false);
        await user.type(samstag,'8,5');await user.click(screen.getByRole('button',{name:'Vorschau laden'}));
        await screen.findByText('Offene Monate werden neu gerechnet.');
        const call=fetchMock.mock.calls.find(c=>String(c[0]).endsWith('/vorschau'));
        expect(JSON.parse(String(call?.[1]?.body))).toMatchObject({arbeitszeit:{samstagStunden:8.5,montagStunden:8}});
    });

});
