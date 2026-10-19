import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import TerminKalender from './TerminKalender';
const { toast } = vi.hoisted(() => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('../components/ui/toast', () => ({ useToast: () => toast }));
vi.mock('../components/ui/confirm-dialog', () => ({ useConfirm: () => vi.fn().mockResolvedValue(true) }));
const fetchMock = vi.fn();
beforeEach(() => { vi.clearAllMocks(); fetchMock.mockResolvedValue({ ok: true, json: async () => [] }); vi.stubGlobal('fetch', fetchMock); });
describe('Terminuhrzeiten im eigenen Eingabefeld', () => {
    it('bewahrt ungültige Entwürfe und sendet erst vollständige Uhrzeiten', async () => {
        render(<MemoryRouter><TerminKalender /></MemoryRouter>);
        fireEvent.click(await screen.findByRole('button', { name: 'Neuer Termin' }));
        fireEvent.change(screen.getByPlaceholderText('z.B. Montage, Besprechung...'), { target: { value: 'Testtermin' } });
        const von = screen.getByRole('textbox', { name: 'Von' });
        const bis = screen.getByRole('textbox', { name: 'Bis' });
        fireEvent.change(von, { target: { value: '25:99' } });
        fireEvent.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(von).toHaveValue('25:99');
        expect(fetchMock.mock.calls.filter(call => call[1]?.method === 'POST')).toHaveLength(0);
        expect(toast.error).toHaveBeenCalled();
        fireEvent.change(von, { target: { value: '09:05' } });
        fireEvent.change(bis, { target: { value: '' } });
        fireEvent.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(fetchMock.mock.calls.filter(call => call[1]?.method === 'POST')).toHaveLength(0);
        fireEvent.change(bis, { target: { value: '10:15' } });
        fireEvent.click(screen.getByRole('button', { name: 'Speichern' }));
        await waitFor(() => expect(fetchMock.mock.calls.filter(call => call[1]?.method === 'POST')).toHaveLength(1));
        const request = fetchMock.mock.calls.find(call => call[1]?.method === 'POST');
        expect(JSON.parse(request![1].body)).toMatchObject({ startZeit: '09:05:00', endeZeit: '10:15:00' });
    });
});

describe('Team-Abwesenheiten ohne Einblick (#268)', () => {
    it('zeigt „ABWESEND" als neutrales „Abwesend" statt auf Urlaub zurückzufallen', async () => {
        const heute = new Date();
        const datum = `${heute.getFullYear()}-${String(heute.getMonth() + 1).padStart(2, '0')}-15`;
        fetchMock.mockImplementation(async (input: string) => {
            if (input.startsWith('/api/abwesenheit/team')) {
                return { ok: true, json: async () => [{ id: 7, datum, typ: 'ABWESEND', stunden: 8, mitarbeiterId: 3, mitarbeiterName: 'Hans Beispiel' }] };
            }
            return { ok: true, json: async () => [] };
        });
        render(<MemoryRouter><TerminKalender /></MemoryRouter>);

        const eintrag = await screen.findByTitle('Hans Beispiel – Abwesend');
        // Neutrale slate-Farbe wie Krankheit, nicht das Urlaubs-Blau.
        expect(eintrag.className).toContain('text-slate-600');
        expect(eintrag.className).not.toContain('text-sky-700');

        // Die Legende erklärt den neuen Eintrag.
        expect(screen.getByText('Legende:').parentElement).toHaveTextContent('Abwesend');
    });
});

// ---------------------------------------------------------------------------
// Issue #267: sichtbarer Zeitraum, Nur-Lese-Einträge, Teilnehmer, Rechte
// ---------------------------------------------------------------------------

/** Dummy-Termin (DSGVO: nur Mustermann-Daten). */
const termin = (teil: Partial<Record<string, unknown>> = {}) => ({
    id: 11, titel: 'Montage Treppe', beschreibung: null, datum: '2026-09-30', startZeit: '09:00:00', endeZeit: '11:00:00',
    ganztaegig: false, farbe: '#7c3aed', projektId: null, projektName: null, kundeId: null, kundeName: null,
    lieferantId: null, lieferantName: null, anfrageId: null, anfrageBetreff: null, erstellerId: 1, erstellerName: 'Max Mustermann',
    teilnehmer: [] as { id: number; name: string }[], darfBearbeiten: true, ...teil,
});

interface Stand {
    termine?: ReturnType<typeof termin>[];
    feiertage?: { datum: string; bezeichnung: string }[];
    abwesenheiten?: { id: number; datum: string; typ: string; stunden: number; mitarbeiterId: number; mitarbeiterName: string }[];
    kalender?: () => { ok: boolean; status?: number; json: () => Promise<unknown> };
    schreiben?: () => { ok: boolean; status?: number; json: () => Promise<unknown> };
}

function stubKalender(stand: Stand = {}) {
    fetchMock.mockImplementation(async (input: string, init?: RequestInit) => {
        if (init?.method && init.method !== 'GET') return stand.schreiben?.() ?? { ok: true, json: async () => ({ id: 11 }) };
        if (input.startsWith('/api/kalender')) return stand.kalender?.() ?? { ok: true, json: async () => stand.termine ?? [] };
        if (input.startsWith('/api/zeiterfassung/feiertage')) {
            const jahr = new URL(input, 'http://localhost').searchParams.get('jahr');
            return { ok: true, json: async () => (stand.feiertage ?? []).filter(f => f.datum.startsWith(`${jahr}-`)) };
        }
        if (input.startsWith('/api/abwesenheit/team')) return { ok: true, json: async () => stand.abwesenheiten ?? [] };
        if (input === '/api/mitarbeiter') return { ok: true, json: async () => [
            { id: 1, vorname: 'Max', nachname: 'Mustermann', aktiv: true },
            { id: 2, vorname: 'Erika', nachname: 'Musterfrau', aktiv: true },
        ] };
        return { ok: true, json: async () => [] };
    });
}

const zeigeAm = (datum: string) => render(<MemoryRouter initialEntries={[`/kalender?date=${datum}`]}><TerminKalender /></MemoryRouter>);
const kalenderAbrufe = () => fetchMock.mock.calls.map(([url]) => String(url)).filter(url => url.startsWith('/api/kalender?'));
const schreibAufrufe = () => fetchMock.mock.calls.filter(([, init]) => init?.method && init.method !== 'GET');

describe('Sichtbarer Zeitraum (#267)', () => {
    it('lädt in Tag, Woche und Monat genau den sichtbaren Zeitraum – auch über die Monatsgrenze', async () => {
        stubKalender();
        zeigeAm('2026-09-30');
        await waitFor(() => expect(kalenderAbrufe()).toContain('/api/kalender?von=2026-09-30&bis=2026-09-30'));

        fireEvent.click(screen.getByRole('button', { name: /Woche/ }));
        await waitFor(() => expect(kalenderAbrufe()).toContain('/api/kalender?von=2026-09-28&bis=2026-10-04'));

        fireEvent.click(await screen.findByRole('button', { name: 'Nächste Woche' }));
        await waitFor(() => expect(kalenderAbrufe()).toContain('/api/kalender?von=2026-10-05&bis=2026-10-11'));
        // Das Feld „Monat“ folgt der Woche.
        expect(screen.getByRole('button', { name: 'Monat: Oktober' })).toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: 'Monat' }));
        await waitFor(() => expect(kalenderAbrufe()).toContain('/api/kalender?von=2026-09-28&bis=2026-11-01'));
        // Kein altes jahr/monat-Muster mehr.
        expect(kalenderAbrufe().some(url => url.includes('jahr='))).toBe(false);
    });

    it('lädt am Jahreswechsel die Feiertage beider Jahre', async () => {
        stubKalender({ feiertage: [{ datum: '2027-01-01', bezeichnung: 'Neujahr' }] });
        zeigeAm('2026-12-30');
        fireEvent.click(screen.getByRole('button', { name: /Woche/ }));
        await waitFor(() => expect(kalenderAbrufe()).toContain('/api/kalender?von=2026-12-28&bis=2027-01-03'));
        const feiertagAbrufe = fetchMock.mock.calls.map(([url]) => String(url)).filter(url => url.startsWith('/api/zeiterfassung/feiertage'));
        expect(feiertagAbrufe).toEqual(expect.arrayContaining(['/api/zeiterfassung/feiertage?jahr=2026', '/api/zeiterfassung/feiertage?jahr=2027']));
        expect(await screen.findByText('Neujahr')).toBeInTheDocument();
    });

    it('zeigt einen Ladefehler sichtbar mit der Meldung vom Server', async () => {
        stubKalender({ kalender: () => ({ ok: false, status: 400, json: async () => ({ status: 400, message: 'Der Zeitraum darf höchstens 100 Tage umfassen.' }) }) });
        zeigeAm('2026-09-30');
        expect(await screen.findByRole('alert')).toHaveTextContent('Der Zeitraum darf höchstens 100 Tage umfassen.');
        expect(toast.error).toHaveBeenCalledWith('Der Zeitraum darf höchstens 100 Tage umfassen.');
        expect(screen.getByRole('button', { name: /Erneut versuchen/ })).toBeInTheDocument();
    });
});

describe('Feiertage und Abwesenheiten sind keine Termine (#267)', () => {
    it('öffnet beim Klick keinen Termin-Dialog und legt keinen neuen an', async () => {
        stubKalender({
            feiertage: [{ datum: '2026-10-03', bezeichnung: 'Tag der Deutschen Einheit' }],
            abwesenheiten: [{ id: 5, datum: '2026-10-01', typ: 'URLAUB', stunden: 8, mitarbeiterId: 2, mitarbeiterName: 'Erika Musterfrau' }],
        });
        zeigeAm('2026-10-01');
        fireEvent.click(screen.getByRole('button', { name: /Woche/ }));

        const feiertag = await screen.findByTitle('Tag der Deutschen Einheit – Gesetzlicher Feiertag');
        fireEvent.click(feiertag);
        fireEvent.click(screen.getByTitle('Erika Musterfrau – Urlaub'));

        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(schreibAufrufe()).toHaveLength(0);
    });
});

describe('Termin-Dialog (#267)', () => {
    beforeEach(() => localStorage.setItem('frontendUserSelection', JSON.stringify({ mitarbeiterId: 1 })));

    it('sendet beim Entfernen aller Teilnehmer eine leere Liste und keinen Ersteller', async () => {
        stubKalender({ termine: [termin({ teilnehmer: [{ id: 1, name: 'Max Mustermann' }] })] });
        zeigeAm('2026-09-30');
        fireEvent.click(await screen.findByRole('button', { name: /Montage Treppe/ }));
        const dialog = screen.getByRole('dialog', { name: 'Termin bearbeiten' });
        const max = await within(dialog).findByRole('checkbox', { name: 'Max Mustermann' });
        expect(max).toBeChecked();
        fireEvent.click(max);
        expect(within(dialog).getByText('Keine Teilnehmer ausgewählt')).toBeInTheDocument();
        fireEvent.click(within(dialog).getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(schreibAufrufe()).toHaveLength(1));
        const [url, init] = schreibAufrufe()[0];
        expect(url).toBe('/api/kalender/11');
        expect(init.method).toBe('PUT');
        const body = JSON.parse(init.body);
        expect(body.teilnehmerIds).toEqual([]);
        expect(body).not.toHaveProperty('erstellerId');
        expect(body).not.toHaveProperty('mitarbeiterId');
    });

    it('wählt den eigenen Benutzer nur bei neuen Terminen vor', async () => {
        stubKalender({ termine: [termin()] });
        zeigeAm('2026-09-30');
        fireEvent.click(await screen.findByRole('button', { name: /Montage Treppe/ }));
        expect(await within(screen.getByRole('dialog')).findByRole('checkbox', { name: 'Max Mustermann' })).not.toBeChecked();
        fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }));

        fireEvent.click(screen.getByRole('button', { name: 'Neuer Termin' }));
        expect(await within(screen.getByRole('dialog', { name: 'Neuer Termin' })).findByRole('checkbox', { name: 'Max Mustermann' })).toBeChecked();
    });

    it('zeigt fremde Termine nur lesend', async () => {
        stubKalender({ termine: [termin({ darfBearbeiten: false, erstellerName: 'Erika Musterfrau' })] });
        zeigeAm('2026-09-30');
        fireEvent.click(await screen.findByRole('button', { name: /Montage Treppe/ }));
        const dialog = screen.getByRole('dialog', { name: 'Termin ansehen' });
        expect(dialog).toHaveTextContent('Diesen Termin können nur der Ersteller, die Teilnehmer und Admins ändern.');
        expect(dialog).toHaveTextContent('Erstellt von Erika Musterfrau');
        expect(within(dialog).getByLabelText(/Titel/)).toBeDisabled();
        expect(await within(dialog).findByRole('checkbox', { name: 'Max Mustermann' })).toBeDisabled();
        expect(within(dialog).queryByRole('button', { name: 'Speichern' })).not.toBeInTheDocument();
        expect(within(dialog).queryByRole('button', { name: /Löschen/ })).not.toBeInTheDocument();
        // Der Info-Knopf bleibt trotz gesperrter Felder bedienbar.
        expect(within(dialog).getByRole('button', { name: 'Mehr zu: Teilnehmer' })).toBeEnabled();
        fireEvent.click(within(dialog).getByRole('button', { name: 'Schließen' }));
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(schreibAufrufe()).toHaveLength(0);
    });

    it('zeigt die 403-Meldung vom Server', async () => {
        stubKalender({
            termine: [termin()],
            schreiben: () => ({ ok: false, status: 403, json: async () => ({ status: 403, message: 'Diesen Termin können nur der Ersteller, die Teilnehmer und Admins ändern.' }) }),
        });
        zeigeAm('2026-09-30');
        fireEvent.click(await screen.findByRole('button', { name: /Montage Treppe/ }));
        fireEvent.click(screen.getByRole('button', { name: 'Speichern' }));
        await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Diesen Termin können nur der Ersteller, die Teilnehmer und Admins ändern.'));
        expect(screen.getByRole('dialog')).toBeInTheDocument();
    });

    it('sperrt Speichern, wenn das Ende vor dem Beginn liegt', async () => {
        stubKalender();
        zeigeAm('2026-09-30');
        fireEvent.click(await screen.findByRole('button', { name: 'Neuer Termin' }));
        fireEvent.change(screen.getByPlaceholderText('z.B. Montage, Besprechung...'), { target: { value: 'Aufmaß' } });
        fireEvent.change(screen.getByRole('textbox', { name: 'Von' }), { target: { value: '10:00' } });
        fireEvent.change(screen.getByRole('textbox', { name: 'Bis' }), { target: { value: '09:00' } });
        expect(screen.getByText('Das Ende liegt vor dem Beginn.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled();
        fireEvent.change(screen.getByRole('textbox', { name: 'Bis' }), { target: { value: '10:30' } });
        expect(screen.queryByText('Das Ende liegt vor dem Beginn.')).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Speichern' })).toBeEnabled();
    });
});

describe('Darstellung (#267)', () => {
    it('zeigt die gewählte Farbe Rot statt Violett', async () => {
        stubKalender({ termine: [termin({ farbe: '#dc2626' })] });
        zeigeAm('2026-09-30');
        const knopf = await screen.findByRole('button', { name: /Montage Treppe/ });
        expect(knopf.className).toContain('bg-red-100');
        expect(knopf.className).not.toContain('bg-violet-100');
    });

    it('„+N weitere“ ist ein Knopf und öffnet die Tagesansicht, Termine stehen vor Abwesenheiten', async () => {
        const termine = [1, 2, 3].map(i => termin({ id: i, titel: `Termin ${i}`, startZeit: `0${i + 6}:00:00`, endeZeit: `0${i + 7}:00:00`, datum: '2026-10-14' }));
        stubKalender({
            termine,
            abwesenheiten: [1, 2].map(i => ({ id: i, datum: '2026-10-14', typ: 'URLAUB', stunden: 8, mitarbeiterId: i, mitarbeiterName: `Urlauber ${i}` })),
        });
        zeigeAm('2026-10-14');
        fireEvent.click(screen.getByRole('button', { name: 'Monat' }));
        const weitere = await screen.findByRole('button', { name: '+2 weitere' });
        // Alle drei Termine sind sichtbar, die Abwesenheiten rutschen in „weitere“.
        expect(screen.getByRole('button', { name: /Termin 3/ })).toBeInTheDocument();
        expect(screen.queryByTitle('Urlauber 1 – Urlaub')).not.toBeInTheDocument();
        fireEvent.click(weitere);
        expect(await screen.findByText('Mittwoch, 14. Oktober 2026')).toBeInTheDocument();
        expect(screen.getByTitle('Urlauber 1 – Urlaub')).toBeInTheDocument();
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('erweitert die Tagesansicht für frühe und späte Termine', async () => {
        stubKalender({ termine: [termin({ startZeit: '05:30:00', endeZeit: '06:00:00' }), termin({ id: 12, titel: 'Abendtermin', startZeit: '20:00:00', endeZeit: '21:30:00' })] });
        zeigeAm('2026-09-30');
        expect(await screen.findByText('05:00')).toBeInTheDocument();
        expect(screen.getByText('21:00')).toBeInTheDocument();
        expect(screen.queryByText('04:00')).not.toBeInTheDocument();
    });
});
