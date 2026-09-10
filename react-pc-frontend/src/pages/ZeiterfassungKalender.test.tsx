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
const status = (closed = false) => ({
    mitarbeiterId: 1,
    jahr: year,
    monat: 8,
    festgeschrieben: closed,
    version: 1,
    sollStunden: 120,
    gesamtIst: 125,
    differenz: 5,
    audit: closed ? [{ id: 1, aktion: 'ABSCHLIESSEN', akteurName: 'Max Mustermann', zeitpunkt: `${year}-09-01T10:00:00` }] : []
});
let tage: unknown[];
let closed: boolean, statusFailure: boolean;

function setup() {
    mockFetch.mockImplementation(async (input: string, init?: RequestInit) => {
        let body: unknown = [];
        if (input === '/api/mitarbeiter') body = [{ id: 1, vorname: 'Max', nachname: 'Mustermann' }];
        if (input.startsWith('/api/zeitverwaltung/kalender')) body = { tage, sollStundenMonat: 160, istStundenMonat: 168, differenz: 8 };
        if (input === base) {
            if (statusFailure) return { ok: false, status: 500, json: async () => ({ message: 'Monatsabschluss konnte nicht geladen werden.' }) };
            body = status(closed);
        }
        if (input === '/api/abwesenheit' && init?.method === 'POST') {
            const req = JSON.parse(String(init.body));
            return {
                ok: true,
                json: async () => ({ id: 42, datum: req.datum, typ: req.typ, stunden: 8, notiz: 'Genehmigt' })
            };
        }
        return { ok: true, json: async () => body };
    });
    vi.stubGlobal('fetch', mockFetch);
}

function mount(url = `/zeitbuchungen?jahr=${year}&monat=8&mitarbeiterId=1`) {
    return render(<MemoryRouter initialEntries={[url]}><ZeiterfassungKalender /></MemoryRouter>);
}

beforeEach(() => {
    vi.clearAllMocks();
    tage = [];
    closed = false;
    statusFailure = false;
    confirm.mockResolvedValue(true);
    setup();
});

describe('Monatsabschluss im Kalender', () => {
    it('übernimmt den Deep-Link, zeigt offene Stunden und verlinkt zur zentralen Monatsabschluss-Seite', async () => {
        mount();
        const link = await screen.findByRole('link', { name: 'Zum Monatsabschluss' });
        expect(link).toBeVisible();
        expect(link.getAttribute('href')).toBe(`/monatsabschluss?jahr=${year}&monat=8&mitarbeiterId=1`);
        expect(screen.getByText('168,0h')).toBeVisible();
        expect(screen.getByText(/Noch offen/)).toBeVisible();
        expect(screen.queryByRole('button', { name: 'Monat abschließen' })).not.toBeInTheDocument();
    });

    it('zeigt bei festgeschriebenem Stand die festgehaltenen Stunden und den Verlauf', async () => {
        closed = true;
        mount();
        await screen.findByRole('link', { name: 'Zum Monatsabschluss' });
        expect(screen.getByText('125,0h')).toBeVisible();
        expect(screen.getByText(/Abgeschlossen – die Monatssummen/)).toBeVisible();
        expect(screen.getByText(/Verlauf der Monatsabschlüsse/)).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Monat wieder öffnen' })).not.toBeInTheDocument();
    });

    it('meldet einen fehlgeschlagenen Statusabruf nur einmal und lässt Stunden sichtbar', async () => {
        statusFailure = true;
        mount();
        await screen.findByRole('button', { name: 'Monatsstand erneut laden' });
        expect(toast.error).toHaveBeenCalledTimes(1);
        expect(screen.getByText('168,0h')).toBeVisible();
    });
});

describe('Tageserfassung und smarte Buchungslogik', () => {
    it('setzt bei Neuer Buchung den Beginn automatisch auf das Ende der vorherigen Buchung', async () => {
        tage = [{
            datum: `${year}-08-03`,
            wochentag: 1,
            istFeiertag: false,
            feiertagName: null,
            sollStunden: 8,
            istStunden: 4,
            buchungen: [{
                id: 1,
                projektId: 1,
                projektName: 'Testprojekt',
                arbeitsgangName: '',
                startZeit: '08:00',
                endeZeit: '12:00',
                dauerMinuten: 240,
                dauerFormatiert: '4:00',
                notiz: ''
            }]
        }];
        mount();
        fireEvent.doubleClick(await screen.findByText('3', { selector: 'span' }));
        expect(screen.getByRole('dialog', { name: 'Tageserfassung' })).toBeVisible();

        fireEvent.click(screen.getByRole('button', { name: /Neue Buchung/ }));
        const inputs = screen.getAllByRole('textbox', { name: /Von Buchung/ });
        expect(inputs).toHaveLength(2);
        // Neue Buchung beginnt um 12:00 (Ende der vorherigen Buchung)
        expect(inputs[1]).toHaveValue('12:00');
    });

    it('schneidet eine Pause automatisch in eine überlappende Arbeitszeit ein', async () => {
        tage = [{
            datum: `${year}-08-03`,
            wochentag: 1,
            istFeiertag: false,
            feiertagName: null,
            sollStunden: 8,
            istStunden: 9,
            buchungen: [{
                id: 1,
                projektId: 1,
                projektName: 'Ganztagsauftrag',
                arbeitsgangName: 'Montage',
                startZeit: '08:00',
                endeZeit: '17:00',
                dauerMinuten: 540,
                dauerFormatiert: '9:00',
                notiz: ''
            }]
        }];
        mount();
        fireEvent.doubleClick(await screen.findByText('3', { selector: 'span' }));

        fireEvent.click(screen.getByRole('button', { name: /Pause/ }));

        // 3 Zeilen: Arbeitszeit vor Pause, Pause, Arbeitszeit nach Pause
        const vonInputs = screen.getAllByRole('textbox', { name: /Von Buchung/ });
        const bisInputs = screen.getAllByRole('textbox', { name: /Bis Buchung/ });

        expect(vonInputs[0]).toHaveValue('08:00');
        expect(bisInputs[0]).toHaveValue('12:00');

        expect(vonInputs[1]).toHaveValue('12:00');
        expect(bisInputs[1]).toHaveValue('12:30');

        expect(vonInputs[2]).toHaveValue('12:30');
        expect(bisInputs[2]).toHaveValue('17:00');
    });

    it('bietet Buttons für Urlaub, Krankheit und Zeitausgleich und bucht über /api/abwesenheit', async () => {
        tage = [{
            datum: `${year}-08-03`,
            wochentag: 1,
            istFeiertag: false,
            feiertagName: null,
            sollStunden: 8,
            istStunden: 0,
            buchungen: []
        }];
        mount();
        fireEvent.doubleClick(await screen.findByText('3', { selector: 'span' }));

        const urlaubBtn = screen.getByRole('button', { name: /Urlaub/ });
        const krankheitBtn = screen.getByRole('button', { name: /Krankheit/ });
        const zeitausgleichBtn = screen.getByRole('button', { name: /Zeitausgleich/ });

        expect(urlaubBtn).toBeVisible();
        expect(krankheitBtn).toBeVisible();
        expect(zeitausgleichBtn).toBeVisible();

        fireEvent.click(urlaubBtn);
        await waitFor(() => {
            expect(mockFetch).toHaveBeenCalledWith('/api/abwesenheit', expect.objectContaining({
                method: 'POST',
                body: JSON.stringify({ mitarbeiterId: 1, datum: `${year}-08-03`, typ: 'URLAUB', halberTag: false })
            }));
        });
        expect(await screen.findByText('Urlaub', { selector: 'p' })).toBeVisible();

        fireEvent.click(zeitausgleichBtn);
        await waitFor(() => {
            expect(mockFetch).toHaveBeenCalledWith('/api/abwesenheit', expect.objectContaining({
                method: 'POST',
                body: JSON.stringify({ mitarbeiterId: 1, datum: `${year}-08-03`, typ: 'ZEITAUSGLEICH', halberTag: false })
            }));
        });
        expect(await screen.findByText('Zeitausgleich', { selector: 'p' })).toBeVisible();
    });

    it('prüft alle Uhrzeitentwürfe vor dem ersten Speichern und erhält unveränderte Sekunden', async () => {
        tage = [{
            datum: `${year}-08-03`,
            wochentag: 1,
            istFeiertag: false,
            feiertagName: null,
            sollStunden: 8,
            istStunden: 8,
            buchungen: [1, 2].map(id => ({
                id,
                projektId: 1,
                projektName: 'Testprojekt',
                arbeitsgangName: '',
                startZeit: '08:00:15',
                endeZeit: '16:00:30',
                dauerMinuten: 480,
                dauerFormatiert: '8:00',
                notiz: ''
            }))
        }];
        mount();
        fireEvent.doubleClick(await screen.findByText('3', { selector: 'span' }));
        const first = screen.getByRole('textbox', { name: 'Von Buchung 1' });
        const second = screen.getByRole('textbox', { name: 'Von Buchung 2' });
        fireEvent.change(first, { target: { value: '09:05' } });
        fireEvent.change(second, { target: { value: '25:99' } });
        fireEvent.click(screen.getByRole('button', { name: 'Alle Speichern' }));
        expect(second).toHaveValue('25:99');
        expect(mockFetch.mock.calls.filter(call => call[1]?.method === 'PUT')).toHaveLength(0);
        expect(toast.error).toHaveBeenCalled();
        fireEvent.change(second, { target: { value: '10:05' } });
        fireEvent.change(screen.getByRole('textbox', { name: 'Bis Buchung 2' }), { target: { value: '   ' } });
        fireEvent.click(screen.getByRole('button', { name: 'Alle Speichern' }));
        await waitFor(() => expect(mockFetch.mock.calls.filter(call => call[1]?.method === 'PUT')).toHaveLength(2));
        const request = mockFetch.mock.calls.find(call => call[0] === '/api/zeitverwaltung/buchungen/1');
        expect(JSON.parse(request![1].body)).toMatchObject({ startZeit: `${year}-08-03T09:05:00`, endeZeit: `${year}-08-03T16:00:30` });
        const optional = mockFetch.mock.calls.find(call => call[0] === '/api/zeitverwaltung/buchungen/2');
        expect(JSON.parse(optional![1].body).endeZeit).toBeNull();
    });
});
