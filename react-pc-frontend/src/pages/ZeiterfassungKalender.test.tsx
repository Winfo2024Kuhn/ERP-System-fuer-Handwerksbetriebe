import { render, screen, waitFor, fireEvent, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ZeiterfassungKalender from './ZeiterfassungKalender';
const { toast, confirm } = vi.hoisted(() => ({ toast: { error: vi.fn(), success: vi.fn(), warning: vi.fn(), info: vi.fn() }, confirm: vi.fn() }));
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
let saldoAntworten: Record<number, unknown>;
let darfMonatAbschliessen: boolean;
let berechtigungHaengt: boolean;

function setup() {
    mockFetch.mockImplementation(async (input: string, init?: RequestInit) => {
        let body: unknown = [];
        if (input === '/api/mitarbeiter') body = [
            { id: 1, vorname: 'Max', nachname: 'Mustermann', aktiv: true },
            { id: 2, vorname: 'Anna', nachname: 'Inaktiv', aktiv: false },
            { id: 3, vorname: 'Chef', nachname: 'Boss', aktiv: true, istGeschaeftsfuehrer: true }
        ];
        // Saldo kommt über die Mitarbeiter-ID – das Büro braucht keinen fremden Anmelde-Code.
        const saldoAbruf = /^\/api\/zeitverwaltung\/mitarbeiter\/(\d+)\/saldo\?/.exec(input);
        if (saldoAbruf) body = saldoAntworten[Number(saldoAbruf[1])];
        if (input.startsWith('/api/zeitverwaltung/kalender')) body = { tage, sollStundenMonat: 160, istStundenMonat: 168, differenz: 8 };
        if (input === '/api/zeitverwaltung/monatsabschluesse/berechtigung') {
            if (berechtigungHaengt) return new Promise(() => {});
            body = { darfMonatAbschliessen };
        }
        if (input === base) {
            if (statusFailure) return { ok: false, status: 500, json: async () => ({ message: 'Monatsabschluss konnte nicht geladen werden.' }) };
            body = status(closed);
        }
        if (input.endsWith('/oeffnen') && init?.method === 'POST') {
            closed = false;
            return { ok: true, json: async () => status(false) };
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
    saldoAntworten = {};
    darfMonatAbschliessen = true;
    berechtigungHaengt = false;
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
        expect(await screen.findByRole('heading', { name: 'Urlaub buchen' })).toBeVisible();
        fireEvent.click(screen.getByRole('button', { name: '1,0 Tag buchen' }));
        await waitFor(() => {
            expect(mockFetch).toHaveBeenCalledWith('/api/abwesenheit', expect.objectContaining({
                method: 'POST',
                body: JSON.stringify({ mitarbeiterId: 1, datum: `${year}-08-03`, typ: 'URLAUB', halberTag: false })
            }));
        });
        expect(await screen.findByText('Urlaub', { selector: 'p' })).toBeVisible();

        fireEvent.click(zeitausgleichBtn);
        expect(await screen.findByRole('heading', { name: 'Zeitausgleich buchen' })).toBeVisible();
        fireEvent.click(screen.getByRole('button', { name: 'Zeitausgleich buchen' }));
        await waitFor(() => {
            expect(mockFetch).toHaveBeenCalledWith('/api/abwesenheit', expect.objectContaining({
                method: 'POST',
                body: JSON.stringify({ mitarbeiterId: 1, datum: `${year}-08-03`, typ: 'ZEITAUSGLEICH', halberTag: false, stunden: 8 })
            }));
        });
        expect(await screen.findByText('Zeitausgleich', { selector: 'p' })).toBeVisible();
    });

    it('bucht halben Urlaubstag über das Dialogfenster', async () => {
        tage = [{
            datum: `${year}-08-04`,
            wochentag: 2,
            istFeiertag: false,
            feiertagName: null,
            sollStunden: 8,
            istStunden: 0,
            buchungen: []
        }];
        mount();
        fireEvent.doubleClick(await screen.findByText('4', { selector: 'span' }));
        fireEvent.click(screen.getByRole('button', { name: /Urlaub/ }));
        expect(await screen.findByRole('heading', { name: 'Urlaub buchen' })).toBeVisible();
        fireEvent.click(screen.getByRole('button', { name: 'Halber Tag Urlaub auswählen' }));
        fireEvent.click(screen.getByRole('button', { name: '0,5 Tage buchen' }));
        await waitFor(() => {
            expect(mockFetch).toHaveBeenCalledWith('/api/abwesenheit', expect.objectContaining({
                method: 'POST',
                body: JSON.stringify({ mitarbeiterId: 1, datum: `${year}-08-04`, typ: 'URLAUB', halberTag: true })
            }));
        });
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
                arbeitsgangId: 1,
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

    it('zeigt die Server-Meldung einmal, wenn der angemeldete Benutzer keinem Mitarbeiter zugeordnet ist', async () => {
        const meldung = 'Ihr Benutzer ist keinem Mitarbeiter zugeordnet.';
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
                arbeitsgangId: 1,
                projektName: 'Testprojekt',
                arbeitsgangName: '',
                startZeit: '08:00:00',
                endeZeit: '16:00:00',
                dauerMinuten: 480,
                dauerFormatiert: '8:00',
                notiz: ''
            }))
        }];
        const standard = mockFetch.getMockImplementation()!;
        mockFetch.mockImplementation(async (input: string, init?: RequestInit) => init?.method === 'PUT'
            ? { ok: false, status: 403, json: async () => ({ status: 403, message: meldung }) }
            : standard(input, init));
        mount();
        fireEvent.doubleClick(await screen.findByText('3', { selector: 'span' }));
        fireEvent.change(screen.getByRole('textbox', { name: 'Von Buchung 1' }), { target: { value: '09:00' } });
        fireEvent.change(screen.getByRole('textbox', { name: 'Von Buchung 2' }), { target: { value: '09:30' } });
        fireEvent.click(screen.getByRole('button', { name: 'Alle Speichern' }));
        await waitFor(() => expect(mockFetch.mock.calls.filter(call => call[1]?.method === 'PUT')).toHaveLength(2));
        await waitFor(() => expect(toast.error).toHaveBeenCalledWith(meldung));
        // Zwei abgelehnte Buchungen → die gleiche Meldung nur einmal, keine Sammelwarnung.
        expect(toast.error).toHaveBeenCalledTimes(1);
        expect(toast.warning).not.toHaveBeenCalled();
        const put = mockFetch.mock.calls.find(call => call[1]?.method === 'PUT');
        expect(JSON.parse(put![1].body)).not.toHaveProperty('bearbeiterId');
    });
});

describe('Mitarbeiter-Filter und Geschäftsführer-Ansicht', () => {
    it('filtert aktive und nicht aktive Mitarbeiter im Dropdown', async () => {
        mount();
        await screen.findByRole('combobox', { name: 'Mitarbeiter-Filter' });
        // Default is AKTIV: Max Mustermann and Chef Boss (aktiv)
        fireEvent.click(screen.getByRole('combobox', { name: 'Mitarbeiter' }));
        expect(await screen.findByRole('option', { name: /Max Mustermann/ })).toBeInTheDocument();
        expect(screen.queryByRole('option', { name: /Anna Inaktiv/ })).not.toBeInTheDocument();
        // Close dropdown by selecting Max
        fireEvent.click(screen.getByRole('option', { name: /Max Mustermann/ }));

        // Switch to INAKTIV
        fireEvent.click(screen.getByRole('combobox', { name: 'Mitarbeiter-Filter' }));
        fireEvent.click(await screen.findByRole('option', { name: 'Nicht aktive Mitarbeiter' }));

        // Open Mitarbeiter dropdown again
        fireEvent.click(screen.getByRole('combobox', { name: 'Mitarbeiter' }));
        expect(await screen.findByRole('option', { name: /Anna Inaktiv/ })).toBeInTheDocument();
        expect(screen.queryByRole('option', { name: /Max Mustermann/ })).not.toBeInTheDocument();
    });

    it('zeigt für Geschäftsführer reine Ist-Stunden und blendet Monatsabschluss und Korrekturen aus', async () => {
        mount(`/zeitbuchungen?jahr=${year}&monat=8&mitarbeiterId=3`);
        await screen.findByText(/ZEITERFASSUNG KALENDER/);
        expect(screen.queryByRole('region', { name: 'Monatsabschluss' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Korrekturen/ })).not.toBeInTheDocument();
        expect(screen.getByText(/Erfasste Arbeitszeit/)).toBeVisible();
        expect(screen.queryByText(/Soll-Stunden/)).not.toBeInTheDocument();
    });
});

describe('Gesamtstundenkonto beim Mitarbeiterwechsel', () => {
    const vollerSaldo = {
        urlaub: { jahresanspruch: 30, genommen: 10, geplant: 0, verbleibend: 20 },
        gesamt: { istStunden: 1210, sollStunden: 1200, saldo: 10 }
    };

    async function waehleMitarbeiter(name: RegExp) {
        fireEvent.click(await screen.findByRole('combobox', { name: 'Mitarbeiter' }));
        fireEvent.click(await screen.findByRole('option', { name }));
    }

    it('stürzt nicht ab, wenn nach der Geschäftsführung ein Mitarbeiter mit Zeitkonto gewählt wird', async () => {
        saldoAntworten = { 1: vollerSaldo, 3: { urlaub: { genommen: 0, geplant: 0 }, monat: {} } };
        mount(`/zeitbuchungen?jahr=${year}&monat=8&mitarbeiterId=3`);
        await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringContaining('/api/zeitverwaltung/mitarbeiter/3/saldo')));

        await waehleMitarbeiter(/Max Mustermann/);

        expect(await screen.findByText(`Gesamtstundenkonto ${year}`)).toBeVisible();
        expect(screen.getByText('20 Tage')).toBeVisible();
    });

    it('blendet das Gesamtstundenkonto aus, wenn der Saldo ohne Gesamtwerte zurückkommt', async () => {
        saldoAntworten = { 1: { error: 'Mitarbeiter nicht gefunden' } };
        mount();
        await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringContaining('/api/zeitverwaltung/mitarbeiter/1/saldo')));
        await screen.findByText(/ZEITERFASSUNG KALENDER/);
        expect(screen.queryByText(`Gesamtstundenkonto ${year}`)).not.toBeInTheDocument();
    });

    it('überschreibt den Saldo nicht mit einer verspäteten Antwort des vorher gewählten Mitarbeiters', async () => {
        let spaeteAntwortFreigeben: () => void = () => {};
        const standardFetch = mockFetch.getMockImplementation()!;
        saldoAntworten = { 1: vollerSaldo, 3: { urlaub: { genommen: 0, geplant: 0 } } };
        mockFetch.mockImplementation(async (input: string, init?: RequestInit) => {
            if (input.startsWith('/api/zeitverwaltung/mitarbeiter/3/saldo')) {
                await new Promise<void>(resolve => { spaeteAntwortFreigeben = resolve; });
            }
            return standardFetch(input, init);
        });
        mount(`/zeitbuchungen?jahr=${year}&monat=8&mitarbeiterId=3`);
        await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringContaining('/api/zeitverwaltung/mitarbeiter/3/saldo')));

        await waehleMitarbeiter(/Max Mustermann/);
        expect(await screen.findByText(`Gesamtstundenkonto ${year}`)).toBeVisible();

        spaeteAntwortFreigeben();
        await new Promise(resolve => setTimeout(resolve, 0));
        expect(screen.getByText(`Gesamtstundenkonto ${year}`)).toBeVisible();
    });
});

describe('Festgeschriebener Monat Sperre und Freigabe-Dialog', () => {
    it('öffnet bei festgeschriebenem Monat einen Dialog und erlaubt Nur-Ansehen-Modus', async () => {
        closed = true;
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
                projektName: 'Testauftrag',
                arbeitsgangName: 'Montage',
                startZeit: '08:00',
                endeZeit: '12:00',
                dauerMinuten: 240,
                dauerFormatiert: '4:00',
                notiz: ''
            }]
        }];
        mount();
        await screen.findByText('125,0h');
        fireEvent.doubleClick(await screen.findByText('3', { selector: 'span' }));

        // Dialog must appear
        expect(screen.getByRole('dialog', { name: 'Monatsabschluss ist festgeschrieben' })).toBeVisible();
        expect(screen.getByRole('button', { name: /Monatsabschluss zurücksetzen/ })).toBeVisible();

        // Click 'Nur ansehen'
        fireEvent.click(screen.getByRole('button', { name: /Nur ansehen/ }));
        expect(screen.getByRole('dialog', { name: 'Tageserfassung' })).toBeVisible();
        expect(screen.getAllByText(/schreibgeschützt/i).length).toBeGreaterThan(0);

        // Inputs must be disabled and save button hidden
        const vonInput = screen.getByRole('textbox', { name: 'Von Buchung 1' });
        expect(vonInput).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Alle Speichern' })).not.toBeInTheDocument();
    });

    it('setzt den Monatsabschluss über den Freigabe-Dialog zurück', async () => {
        closed = true;
        tage = [{
            datum: `${year}-08-03`,
            wochentag: 1,
            istFeiertag: false,
            feiertagName: null,
            sollStunden: 8,
            istStunden: 4,
            buchungen: []
        }];
        mount();
        await screen.findByText('125,0h');
        fireEvent.doubleClick(await screen.findByText('3', { selector: 'span' }));

        const resetBtn = screen.getByRole('button', { name: /Monatsabschluss zurücksetzen/ });
        fireEvent.click(resetBtn);

        await waitFor(() => {
            expect(mockFetch).toHaveBeenCalledWith(
                expect.stringMatching(/\/api\/zeitverwaltung\/monatsabschluesse\/1\/\d+\/8\/oeffnen$/),
                expect.objectContaining({ method: 'POST' })
            );
        });
        expect(toast.success).toHaveBeenCalledWith(expect.stringMatching(/zurückgesetzt/));
    });
});


/** Ein Arbeitstag ohne Buchungen für die Rechtsklick-Tests. */
const arbeitstag = (tag: number, wochentag: number, buchungen: unknown[] = []) => ({
    datum: `${year}-08-${String(tag).padStart(2, '0')}`,
    wochentag,
    istFeiertag: false,
    feiertagName: null,
    sollStunden: wochentag < 6 ? 8 : 0,
    istStunden: 0,
    buchungen
});

const tagZelle = (tag: string) => screen.getByText(tag, { selector: 'span' }).closest('[class*="min-h-32"]') as HTMLElement;

describe('Rechtsklick-Abwesenheit (#269)', () => {
    it('bucht den rechtsgeklickten Tag, nicht den vorher angeklickten', async () => {
        tage = [arbeitstag(9, 1), arbeitstag(14, 6 - 2)];
        mount();
        await screen.findByText('9', { selector: 'span' });

        // Erst linker Klick auf den 9. (markiert ihn) …
        fireEvent.mouseDown(tagZelle('9'), { button: 0 });
        fireEvent.mouseUp(tagZelle('9'));
        // … dann Rechtsklick auf den 14.
        fireEvent.contextMenu(tagZelle('14'));

        fireEvent.click(await screen.findByRole('button', { name: 'Urlaub (ganzer Tag)' }));

        await waitFor(() => {
            const posts = mockFetch.mock.calls.filter(call => call[0] === '/api/abwesenheit' && call[1]?.method === 'POST');
            expect(posts).toHaveLength(1);
            expect(JSON.parse(String(posts[0][1].body))).toMatchObject({ datum: `${year}-08-14`, typ: 'URLAUB', halberTag: false });
        });
    });

    it('behält die Mehrfachauswahl, wenn der Rechtsklick innerhalb der Markierung liegt', async () => {
        tage = [arbeitstag(3, 1), arbeitstag(4, 2), arbeitstag(5, 3)];
        mount();
        await screen.findByText('3', { selector: 'span' });

        fireEvent.mouseDown(tagZelle('3'), { button: 0 });
        fireEvent.mouseEnter(tagZelle('4'));
        fireEvent.mouseEnter(tagZelle('5'));
        fireEvent.mouseUp(tagZelle('5'));
        fireEvent.contextMenu(tagZelle('4'));

        fireEvent.click(await screen.findByRole('button', { name: 'Krankheit (3 Tage)' }));

        await waitFor(() => {
            const posts = mockFetch.mock.calls.filter(call => call[0] === '/api/abwesenheit' && call[1]?.method === 'POST');
            expect(posts.map(call => JSON.parse(String(call[1].body)).datum)).toEqual([`${year}-08-03`, `${year}-08-04`, `${year}-08-05`]);
        });
    });

    it('sagt Bescheid, wenn im markierten Zeitraum kein Arbeitstag liegt', async () => {
        tage = [arbeitstag(1, 6), arbeitstag(2, 7)];
        mount();
        await screen.findByText('1', { selector: 'span' });

        fireEvent.contextMenu(tagZelle('1'));
        fireEvent.click(await screen.findByRole('button', { name: 'Urlaub (ganzer Tag)' }));

        await waitFor(() => expect(toast.info).toHaveBeenCalledWith('Im markierten Zeitraum liegt kein Arbeitstag.'));
        expect(mockFetch.mock.calls.filter(call => call[0] === '/api/abwesenheit')).toHaveLength(0);
    });

    it('zeigt die Server-Meldung, wenn der Monat festgeschrieben ist (409)', async () => {
        tage = [arbeitstag(3, 1)];
        const meldung = 'Dieser Monat ist bereits festgeschrieben. Bitte setzen Sie zuerst den Monatsabschluss zurück.';
        const standard = mockFetch.getMockImplementation()!;
        mockFetch.mockImplementation(async (input: string, init?: RequestInit) => input === '/api/abwesenheit' && init?.method === 'POST'
            ? { ok: false, status: 409, json: async () => ({ status: 409, message: meldung }) }
            : standard(input, init));
        mount();
        await screen.findByText('3', { selector: 'span' });

        fireEvent.contextMenu(tagZelle('3'));
        fireEvent.click(await screen.findByRole('button', { name: 'Urlaub (ganzer Tag)' }));

        await waitFor(() => expect(toast.error).toHaveBeenCalledWith(meldung));
    });
});

describe('Monatsabschluss zurücksetzen nur mit Recht (#269)', () => {
    it('blendet den Zurücksetzen-Knopf ohne Abschlussrecht aus und erklärt warum', async () => {
        closed = true;
        darfMonatAbschliessen = false;
        tage = [arbeitstag(3, 1, [{
            id: 1, projektId: 1, projektName: 'Testauftrag', arbeitsgangName: 'Montage',
            startZeit: '08:00', endeZeit: '12:00', dauerMinuten: 240, dauerFormatiert: '4:00', notiz: ''
        }])];
        mount();
        await screen.findByText('125,0h');
        fireEvent.doubleClick(await screen.findByText('3', { selector: 'span' }));

        const dialog = screen.getByRole('dialog', { name: 'Monatsabschluss ist festgeschrieben' });
        expect(within(dialog).queryByRole('button', { name: /Monatsabschluss zurücksetzen/ })).not.toBeInTheDocument();
        expect(within(dialog).getByText(/fehlt Ihnen das Abschlussrecht/)).toBeInTheDocument();

        fireEvent.click(within(dialog).getByRole('button', { name: /Nur ansehen/ }));
        expect(screen.getByRole('dialog', { name: 'Tageserfassung' })).toBeVisible();
        expect(screen.queryByRole('button', { name: /Monatsabschluss zurücksetzen/ })).not.toBeInTheDocument();
    });
});

describe('Monatsabschluss-Recht wird noch geprüft', () => {
    it('zeigt weder Zurücksetzen-Knopf noch „Recht fehlt“, solange die Antwort aussteht', async () => {
        closed = true;
        berechtigungHaengt = true;
        tage = [arbeitstag(3, 1)];
        mount();
        await screen.findByText('125,0h');
        fireEvent.doubleClick(await screen.findByText('3', { selector: 'span' }));

        const dialog = screen.getByRole('dialog', { name: 'Monatsabschluss ist festgeschrieben' });
        expect(within(dialog).queryByRole('button', { name: /Monatsabschluss zurücksetzen/ })).not.toBeInTheDocument();
        expect(within(dialog).queryByText(/fehlt Ihnen das Abschlussrecht/)).not.toBeInTheDocument();
        expect(within(dialog).getByText('Die bestehenden Buchungen können Sie im Nur-Lese-Modus ansehen.')).toBeInTheDocument();
    });
});

describe('Resturlaub mit halben Tagen', () => {
    it('zeigt halbe Urlaubstage mit Komma und erklärt die Rechnung über den Info-Knopf', async () => {
        saldoAntworten = { 1: { urlaub: { jahresanspruch: 30, genommen: 10.5, geplant: 7, verbleibend: 12.5 }, gesamt: { istStunden: 100, sollStunden: 90, saldo: 10 } } };
        mount();

        expect(await screen.findByText('12,5 Tage')).toBeVisible();
        expect(screen.getByText('Genommen: 10,5')).toBeVisible();
        expect(screen.getByText('Geplant: 7')).toBeVisible();

        fireEvent.click(screen.getByRole('button', { name: 'Mehr zu: Resturlaub' }));
        const popover = await screen.findByRole('dialog', { name: 'Resturlaub' });
        expect(within(popover).getByText(/Halbe Urlaubstage zählen als 0,5 Tage/)).toBeInTheDocument();
        expect(within(popover).getByText(/Jahresanspruch plus Übertrag aus dem Vorjahr minus verfallene, genommene und geplante Tage plus Korrekturen/)).toBeInTheDocument();
        expect(screen.queryByText(/^davon .* aus dem Vorjahr/)).toBeNull();
    });

    it('zeigt den Rest aus dem Vorjahr getrennt mit Verfallsdatum', async () => {
        saldoAntworten = { 1: { urlaub: { jahresanspruch: 30, genommen: 2, geplant: 0, verbleibend: 31.5, uebertragVorjahr: 3.5, ausVorjahrOffen: 1.5, verfallen: 0, verfallsdatum: '2026-03-31' }, gesamt: { istStunden: 100, sollStunden: 90, saldo: 10 } } };
        mount();

        expect(await screen.findByText('31,5 Tage')).toBeVisible();
        expect(screen.getByText('davon 1,5 Tage aus dem Vorjahr – bis 31.03.2026 nehmen, danach verfallen sie')).toBeVisible();
        expect(screen.getByText(/^Aus \d{4}: 3,5$/)).toBeVisible();
    });
});

describe('Tageserfassung: Kategorie und Speichern (#269)', () => {
    const buchung = {
        id: 1, projektId: 1, arbeitsgangId: 1, projektName: 'Testprojekt', arbeitsgangName: 'Montage',
        startZeit: '08:00:00', endeZeit: '16:00:00', dauerMinuten: 480, dauerFormatiert: '8:00', notiz: ''
    };

    it('übernimmt Produktkategorie-ID und -Name in einem Schritt', async () => {
        tage = [arbeitstag(3, 1, [buchung])];
        const standard = mockFetch.getMockImplementation()!;
        mockFetch.mockImplementation(async (input: string, init?: RequestInit) => {
            if (input !== '/api/zeiterfassung/kategorien/1') return standard(input, init);
            // Wie im Browser kommt die Antwort erst nach dem Lade-Tick des Auswahlfensters.
            await new Promise(resolve => setTimeout(resolve, 5));
            return { ok: true, json: async () => [{ id: 5, name: 'Geländer' }] };
        });
        mount();
        fireEvent.doubleClick(await screen.findByText('3', { selector: 'span' }));

        fireEvent.click(screen.getByRole('button', { name: /Kategorie wählen/ }));
        fireEvent.click(await screen.findByText('Geländer'));

        // Der Name steht sofort im Knopf …
        expect(await screen.findByRole('button', { name: /Geländer/ })).toBeVisible();
        // … und die ID geht mit dem Speichern an den Server – beides aus demselben Patch.
        fireEvent.click(screen.getByRole('button', { name: 'Alle Speichern' }));
        await waitFor(() => {
            const put = mockFetch.mock.calls.find(call => call[0] === '/api/zeitverwaltung/buchungen/1');
            expect(put).toBeDefined();
            expect(JSON.parse(String(put![1].body))).toMatchObject({ produktkategorieId: 5 });
        });
    });

    it('vergisst gespeicherte Änderungen, statt sie beim nächsten Speichern erneut zu schicken', async () => {
        tage = [arbeitstag(3, 1, [buchung])];
        mount();
        fireEvent.doubleClick(await screen.findByText('3', { selector: 'span' }));

        fireEvent.change(screen.getByRole('textbox', { name: 'Von Buchung 1' }), { target: { value: '09:00' } });
        fireEvent.click(screen.getByRole('button', { name: 'Alle Speichern' }));
        await waitFor(() => expect(mockFetch.mock.calls.filter(call => call[1]?.method === 'PUT')).toHaveLength(1));

        fireEvent.click(screen.getByRole('button', { name: /Alle Speichern|Gespeichert/ }));
        await new Promise(resolve => setTimeout(resolve, 20));
        expect(mockFetch.mock.calls.filter(call => call[1]?.method === 'PUT')).toHaveLength(1);
        // Die Anzeige bleibt beim gespeicherten Wert.
        expect(screen.getByRole('textbox', { name: 'Von Buchung 1' })).toHaveValue('09:00');
    });
});

describe('Tageserfassung: Schließen, Uhrzeiten, Vorbelegung, Verlauf (#269)', () => {
    const gespeichert = {
        id: 1, projektId: 1, arbeitsgangId: 1, projektName: 'Testprojekt', arbeitsgangName: 'Montage',
        startZeit: '08:00:00', endeZeit: '12:00:00', dauerMinuten: 240, dauerFormatiert: '4:00', notiz: ''
    };
    const oeffneTag = async () => {
        mount();
        fireEvent.doubleClick(await screen.findByText('3', { selector: 'span' }));
        return screen.getByRole('dialog', { name: 'Tageserfassung' });
    };

    it('schließt ohne Rückfrage, solange nichts geändert wurde', async () => {
        tage = [arbeitstag(3, 1, [gespeichert])];
        await oeffneTag();
        fireEvent.click(screen.getByRole('button', { name: 'Tageserfassung schließen' }));
        await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Tageserfassung' })).not.toBeInTheDocument());
        expect(confirm).not.toHaveBeenCalled();
    });

    it('fragt bei ungespeicherten Änderungen nach und schließt erst nach „Verwerfen“', async () => {
        tage = [arbeitstag(3, 1, [gespeichert])];
        await oeffneTag();
        fireEvent.change(screen.getByRole('textbox', { name: 'Von Buchung 1' }), { target: { value: '07:30' } });

        confirm.mockResolvedValueOnce(false);
        fireEvent.click(screen.getByRole('button', { name: 'Schließen' }));
        await waitFor(() => expect(confirm).toHaveBeenCalledWith(expect.objectContaining({
            title: 'Ungespeicherte Änderungen verwerfen?', confirmLabel: 'Verwerfen', variant: 'danger'
        })));
        expect(screen.getByRole('dialog', { name: 'Tageserfassung' })).toBeVisible();

        confirm.mockResolvedValueOnce(true);
        fireEvent.click(screen.getByRole('button', { name: 'Tageserfassung schließen' }));
        await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Tageserfassung' })).not.toBeInTheDocument());
    });

    it('fragt auch bei einer neuen, noch nicht gespeicherten Buchung nach', async () => {
        tage = [arbeitstag(3, 1)];
        await oeffneTag();
        fireEvent.click(screen.getByRole('button', { name: /Neue Buchung/ }));
        confirm.mockResolvedValueOnce(false);
        fireEvent.click(screen.getByRole('button', { name: 'Schließen' }));
        await waitFor(() => expect(confirm).toHaveBeenCalledTimes(1));
        expect(screen.getByRole('dialog', { name: 'Tageserfassung' })).toBeVisible();
    });

    it('markiert „Ende vor Beginn“, rechnet nicht über Mitternacht und sperrt das Speichern', async () => {
        tage = [arbeitstag(3, 1, [gespeichert])];
        await oeffneTag();
        fireEvent.change(screen.getByRole('textbox', { name: 'Bis Buchung 1' }), { target: { value: '07:00' } });

        expect(screen.getByText('Ende vor Beginn')).toBeVisible();
        expect(screen.queryByText('23:00h')).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Alle Speichern' }));
        expect(toast.error).toHaveBeenCalledWith('Bei einer Buchung liegt das Ende vor dem Beginn. Buchungen über Mitternacht bitte auf zwei Tage aufteilen.');
        expect(mockFetch.mock.calls.filter(call => call[1]?.method === 'PUT')).toHaveLength(0);

        // Gleiche Uhrzeit zählt ebenfalls als ungültig.
        fireEvent.change(screen.getByRole('textbox', { name: 'Bis Buchung 1' }), { target: { value: '08:00' } });
        expect(screen.getByText('Ende vor Beginn')).toBeVisible();
        fireEvent.change(screen.getByRole('textbox', { name: 'Bis Buchung 1' }), { target: { value: '12:15' } });
        expect(screen.queryByText('Ende vor Beginn')).not.toBeInTheDocument();
        expect(screen.getByText('4:15h')).toBeVisible();
    });

    it('zeigt die Ablehnung des Servers (400) als Meldung', async () => {
        tage = [arbeitstag(3, 1, [gespeichert])];
        const meldung = 'Das Ende der Buchung muss nach dem Beginn liegen.';
        const standard = mockFetch.getMockImplementation()!;
        mockFetch.mockImplementation(async (input: string, init?: RequestInit) => init?.method === 'PUT'
            ? { ok: false, status: 400, json: async () => ({ status: 400, message: meldung }) }
            : standard(input, init));
        await oeffneTag();
        fireEvent.change(screen.getByRole('textbox', { name: 'Von Buchung 1' }), { target: { value: '09:00' } });
        fireEvent.click(screen.getByRole('button', { name: 'Alle Speichern' }));
        await waitFor(() => expect(toast.error).toHaveBeenCalledWith(meldung));
    });

    it('belegt eine neue Buchung nicht mit Projekt und Tätigkeit vor und verlangt beides vor dem Speichern', async () => {
        tage = [arbeitstag(3, 1)];
        const standard = mockFetch.getMockImplementation()!;
        mockFetch.mockImplementation(async (input: string, init?: RequestInit) => {
            if (input.startsWith('/api/projekte/simple')) return { ok: true, json: async () => [{ id: 9, bauvorhaben: 'Treppe Musterweg', auftragsnummer: 'A-1' }] };
            if (input === '/api/arbeitsgaenge') return { ok: true, json: async () => [{ id: 4, beschreibung: 'Schweißen' }] };
            return standard(input, init);
        });
        await oeffneTag();
        fireEvent.click(screen.getByRole('button', { name: /Neue Buchung/ }));

        expect(screen.getByText('Projekt wählen…')).toBeVisible();
        expect(screen.getByRole('combobox', { name: 'Tätigkeit Buchung 1' })).toHaveTextContent('Tätigkeit wählen…');
        fireEvent.click(screen.getByRole('button', { name: 'Alle Speichern' }));
        expect(toast.error).toHaveBeenCalledWith('Bitte für jede geänderte Buchung ein Projekt und eine Tätigkeit wählen.');
        expect(mockFetch.mock.calls.filter(call => call[1]?.method === 'POST')).toHaveLength(0);
    });

    it('bucht Fortbildung direkt aus der Tageserfassung', async () => {
        tage = [arbeitstag(3, 1)];
        await oeffneTag();
        fireEvent.click(screen.getByRole('button', { name: /Fortbildung/ }));
        await waitFor(() => {
            const post = mockFetch.mock.calls.find(call => call[0] === '/api/abwesenheit' && call[1]?.method === 'POST');
            expect(JSON.parse(String(post![1].body))).toMatchObject({ typ: 'FORTBILDUNG', datum: `${year}-08-03` });
        });
        expect(await screen.findByText('Fortbildung', { selector: 'p' })).toBeVisible();
        expect(toast.success).toHaveBeenCalledWith('Fortbildung hinzugefügt.');
    });

    it('öffnet den Verlauf nur für gespeicherte Buchungen', async () => {
        tage = [arbeitstag(3, 1, [gespeichert])];
        const standard = mockFetch.getMockImplementation()!;
        mockFetch.mockImplementation(async (input: string, init?: RequestInit) => input === '/api/zeitverwaltung/buchungen/1/historie'
            ? { ok: true, json: async () => [{ id: 11, version: 1, aktion: 'ERSTELLT', startZeit: `${year}-08-03T08:00:00`, endeZeit: `${year}-08-03T12:00:00`, anzahlInStunden: 4, notiz: null, geaendertVon: 'Max Mustermann', geaendertAm: `${year}-08-03T12:01:00`, geaendertVia: 'MOBILE_APP', aenderungsgrund: null }] }
            : standard(input, init));
        await oeffneTag();
        fireEvent.click(screen.getByRole('button', { name: /Neue Buchung/ }));
        expect(screen.getAllByRole('button', { name: /Verlauf Buchung/ })).toHaveLength(1);

        fireEvent.click(screen.getByRole('button', { name: 'Verlauf Buchung 1' }));
        expect(await screen.findByText('Angelegt')).toBeVisible();
        expect(screen.getByText(/über Handy-App/)).toBeVisible();
    });

    it('bucht per Rechtsklick auch einen Samstag mit Soll-Stunden, überspringt freie Tage', async () => {
        tage = [arbeitstag(7, 5), { ...arbeitstag(8, 6), sollStunden: 4 }, arbeitstag(9, 7)];
        mount();
        await screen.findByText('7', { selector: 'span' });
        fireEvent.mouseDown(tagZelle('7'), { button: 0 });
        fireEvent.mouseEnter(tagZelle('8'));
        fireEvent.mouseEnter(tagZelle('9'));
        fireEvent.mouseUp(tagZelle('9'));
        fireEvent.contextMenu(tagZelle('8'));
        fireEvent.click(await screen.findByRole('button', { name: 'Urlaub (2 Tage)' }));
        await waitFor(() => {
            const posts = mockFetch.mock.calls.filter(call => call[0] === '/api/abwesenheit' && call[1]?.method === 'POST');
            expect(posts.map(call => JSON.parse(String(call[1].body)).datum)).toEqual([`${year}-08-07`, `${year}-08-08`]);
        });
    });

    it('erklärt die Mehrfachauswahl über den Info-Knopf am Kalender', async () => {
        mount();
        fireEvent.click(await screen.findByRole('button', { name: 'Mehr zu: Tipps zur Bedienung' }));
        expect(screen.getByText(/mit gedrückter Umschalttaste/)).toBeVisible();
        expect(screen.getByText(/Tage ohne Soll-Stunden/)).toBeVisible();
    });
});
