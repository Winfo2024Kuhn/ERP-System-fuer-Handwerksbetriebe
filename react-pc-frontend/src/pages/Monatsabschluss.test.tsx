import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, it, vi } from 'vitest';
import Monatsabschluss from './Monatsabschluss';
const { toast, confirm } = vi.hoisted(() => ({ toast: { error: vi.fn(), success: vi.fn() }, confirm: vi.fn() }));
vi.mock('../components/ui/toast', () => ({ useToast: () => toast }));
vi.mock('../components/ui/confirm-dialog', () => ({ useConfirm: () => confirm }));
const fetchMock = vi.fn();
const kennzahlen = { istStunden: 120.5, sollStunden: 160, abwesenheitsStunden: 16, feiertagsStunden: 8, korrekturStunden: 1, gesamtIst: 145.5, differenz: -14.5 };
let allowed: boolean;
let calls: string[];
let submitted: { mitarbeiterId: number; jahr: number; monat: number }[][];
function mount(url = '/monatsabschluss') { return render(<MemoryRouter initialEntries={[url]}><Monatsabschluss /></MemoryRouter>); }
async function choose(label: string, option: string) { fireEvent.click(screen.getByRole('combobox', { name: label })); fireEvent.click(await screen.findByRole('option', { name: option, exact: true })); }
beforeEach(() => {
 vi.clearAllMocks(); allowed = true; calls = []; submitted = []; confirm.mockResolvedValue(true);
 fetchMock.mockImplementation(async (input: string, init?: RequestInit) => {
 calls.push(input); const url = new URL(input, 'http://localhost'); const jahr = Number(url.searchParams.get('jahr')); const monat = Number(url.searchParams.get('monat')); let body: unknown = [];
 if (input.endsWith('/berechtigung')) body = { darfMonatAbschliessen: allowed };
 if (input === '/api/mitarbeiter') body = [{ id: 1, vorname: 'Max', nachname: 'Mustermann' }];
 if (input.endsWith('/abteilungen')) body = [{ id: 2, name: 'Werkstatt' }];
 if (url.pathname.endsWith('/uebersicht')) { const page = Number(url.searchParams.get('page')); body = { items: Array.from({ length: 50 }, (_, i) => ({ referenz: { mitarbeiterId: page * 50 + i + 1, jahr, monat }, mitarbeiterName: `Max Mustermann ${page * 50 + i + 1}`, abteilungIds: [2], festgeschrieben: false, version: 3, festgeschriebenAm: null, kennzahlen })), totalElements: 500, page, size: 50, summen: { ...kennzahlen, gesamtIst: 72750 }, auswahl: Array.from({ length: 500 }, (_, i) => ({ mitarbeiterId: i + 1, jahr, monat, version: 3, festgeschrieben: false })) }; }
 if (url.pathname.endsWith('/vergleich')) body = Array.from({ length: 6 }, (_, i) => ({ jahr: 2026, monat: i + 1, summen: kennzahlen, offen: 3, abgeschlossen: 4 }));
 if (input.endsWith('/sammelabschluss')) { const refs = JSON.parse(String(init?.body)).auswahl; submitted.push(refs); body = { ergebnisse: refs.map((referenz: { mitarbeiterId: number }) => ({ referenz, status: referenz.mitarbeiterId === 1 ? 'FEHLGESCHLAGEN' : 'ABGESCHLOSSEN', meldung: referenz.mitarbeiterId === 1 ? 'Zeiten bitte prüfen.' : 'Monat abgeschlossen.' })) }; }
 if (/monatsabschluesse\/\d+\/\d+\/\d+$/.test(input)) body = { audit: [{ id: 1, aktion: 'ABSCHLIESSEN', akteurMitarbeiterId: 1, akteurName: 'Max Mustermann', zeitpunkt: '2026-08-01T10:00:00' }] };
 return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
 }); vi.stubGlobal('fetch', fetchMock);
});
it('ruft ohne Abschlussrecht keine Mitarbeiter oder Monatsdaten ab', async () => { allowed = false; mount(); await screen.findByText(/Keine Berechtigung/); expect(calls).toHaveLength(1); });
it('wählt alle 500 über Seiten hinweg und behält nach Teilerfolg nur Fehler', async () => {
 mount(); await screen.findByText('Max Mustermann 1', { selector: 'th' }); expect(screen.getByText('72.750,00')).toBeVisible();
 fireEvent.click(screen.getByLabelText('Alle gefilterten Mitarbeiter auswählen')); fireEvent.click(screen.getByLabelText('Nächste Seite')); await screen.findByText('Max Mustermann 51', { selector: 'th' }); expect(screen.getByLabelText('Max Mustermann 51 auswählen')).toBeChecked();
 fireEvent.click(screen.getByRole('button', { name: 'Monat jetzt abschließen' })); await waitFor(() => expect(submitted).toHaveLength(1)); expect(submitted[0]).toHaveLength(500); await screen.findByText('1 ausgewählt', { selector: 'p' }); expect(toast.error).toHaveBeenCalled();
});
it('filtert Abteilung und Status, setzt Auswahl zurück und vergleicht alle Stände', async () => {
 mount(); await screen.findByText('Max Mustermann 1'); fireEvent.click(screen.getByRole('checkbox', { name: 'Max Mustermann 1 auswählen' })); await choose('Abteilung', 'Werkstatt'); await screen.findByText('0 ausgewählt'); await choose('Status', 'Noch offen');
 await waitFor(() => expect(calls.some(c => c.includes('status=OFFEN') && c.includes('abteilungId=2'))).toBe(true)); expect(calls.filter(c => c.includes('/vergleich?')).every(c => !c.includes('status='))).toBe(true); expect(screen.getAllByText(/vorläufig/).length).toBeGreaterThanOrEqual(6);
});
it('lädt Verlauf erst auf Klick und verlinkt den genauen Kalendermonat', async () => {
 mount(); await screen.findByText('Max Mustermann 1'); expect(calls.some(c => /monatsabschluesse\/\d+\//.test(c))).toBe(false); expect(screen.getByRole('link', { name: 'Kalender für Max Mustermann 1' }).getAttribute('href')).toMatch(/^\/zeitbuchungen\?mitarbeiterId=1&jahr=\d+&monat=\d+$/); fireEvent.click(screen.getByRole('button', { name: 'Verlauf für Max Mustermann 1' })); await screen.findByText(/Abgeschlossen durch Max Mustermann/);
});
it('verwirft alte Bestätigung bei Monatswechsel und blockiert Doppelklicks', async () => {
 let finish!: (ok: boolean) => void; confirm.mockImplementation(() => new Promise(resolve => { finish = resolve; })); mount(); await screen.findByText('Max Mustermann 1'); fireEvent.click(screen.getByRole('checkbox', { name: 'Max Mustermann 1 auswählen' })); const button = screen.getByRole('button', { name: 'Monat jetzt abschließen' }); fireEvent.click(button); fireEvent.click(button); expect(confirm).toHaveBeenCalledTimes(1);
 const current = screen.getByRole('combobox', { name: 'Monat' }).textContent; await choose('Monat', current === 'Januar' ? 'Februar' : 'Januar'); await act(async () => finish(true)); expect(submitted).toHaveLength(0);
});
it('verwirft verspätete Übersichten und bricht ausstehende Anfragen beim Wechsel ab', async () => {
 const original = fetchMock.getMockImplementation()!; let finish!: (r: Response) => void; let oldSignal: AbortSignal | null | undefined;
 fetchMock.mockImplementation((input: string, init?: RequestInit) => { if (input.includes('/uebersicht?') && !oldSignal) { oldSignal = init?.signal; return new Promise<Response>(resolve => { finish = resolve; }); } return original(input, init); });
 const view = mount(); await screen.findByRole('combobox', { name: 'Monat' }); const current = screen.getByRole('combobox', { name: 'Monat' }).textContent; await choose('Monat', current === 'Januar' ? 'Februar' : 'Januar'); await screen.findByText('Max Mustermann 1'); expect(oldSignal?.aborted).toBe(true);
 await act(async () => finish(new Response(JSON.stringify({ items: [], totalElements: 0, page: 0, size: 50, summen: kennzahlen, auswahl: [] })))); expect(screen.getByText('Max Mustermann 1')).toBeVisible(); view.unmount();
});
it('blockiert den laufenden Monat vor einer Schreibanfrage', async () => {
 mount(); await screen.findByText('Max Mustermann 1'); const now = new Date(); await choose('Jahr', String(now.getFullYear())); await choose('Monat', new Intl.DateTimeFormat('de-DE', { month: 'long' }).format(now)); await screen.findByText('Max Mustermann 1'); fireEvent.click(screen.getByRole('checkbox', { name: 'Max Mustermann 1 auswählen' })); expect(screen.getByRole('button', { name: 'Monat jetzt abschließen' })).toBeDisabled(); expect(submitted).toHaveLength(0);
});
it('übernimmt den tatsächlichen ZIP-Dateinamen und behandelt Downloadkonflikte', async () => {
 const { api } = await import('../features/monatsabschluss/api');
 fetchMock.mockResolvedValueOnce(new Response('zip', { headers: { 'Content-Disposition': 'attachment; filename="lodas-2026-01-bis-2026-02.zip"' } }));
 const result = await api.exportiereDatev({ auswahl: [{ mitarbeiterId: 1, jahr: 2026, monat: 1, version: 3 }], konfigurationVersion: 2 }); expect(result.dateiname).toBe('lodas-2026-01-bis-2026-02.zip'); expect(result.blob.size).toBe(3);
 fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ message: 'Der Monatsstand hat sich geändert.' }), { status: 409 })); await expect(api.exportiereDatev({ auswahl: [], konfigurationVersion: 2 })).rejects.toThrow('Der Monatsstand hat sich geändert.');
});
it('springt nach Abschluss zurück zur ersten Ergebnisseite und schließt den alten Verlauf', async () => {
 mount(); await screen.findByText('Max Mustermann 1'); fireEvent.click(screen.getByRole('button', { name: 'Nächste Seite' })); await screen.findByText('Max Mustermann 51');
 fireEvent.click(screen.getByRole('button', { name: 'Verlauf für Max Mustermann 51' })); await screen.findByText(/Abgeschlossen durch Max Mustermann/);
 fireEvent.click(screen.getByRole('checkbox', { name: 'Max Mustermann 51 auswählen' })); fireEvent.click(screen.getByRole('button', { name: 'Monat jetzt abschließen' }));
 await waitFor(() => expect(submitted).toHaveLength(1)); await screen.findByText('Max Mustermann 1'); expect(screen.queryByRole('region', { name: 'Abschlussverlauf' })).not.toBeInTheDocument();
});

it('sperrt DATEV für offene Version 3 bei Einzel- und seitenübergreifender Auswahl', async () => {
 mount(); await screen.findByText('Max Mustermann 1');
 fireEvent.click(screen.getByRole('checkbox', { name: 'Max Mustermann 1 auswählen' }));
 expect(screen.getByRole('button', { name: 'Für DATEV exportieren' })).toBeDisabled();
 fireEvent.click(screen.getByRole('checkbox', { name: 'Alle gefilterten Mitarbeiter auswählen' }));
 fireEvent.click(screen.getByRole('button', { name: 'Nächste Seite' }));
 await screen.findByText('Max Mustermann 51');
 expect(screen.getByText('500 ausgewählt')).toBeVisible();
 expect(screen.getByRole('button', { name: 'Für DATEV exportieren' })).toBeDisabled();
 expect(calls.some(c => c.includes('/datev/'))).toBe(false);
});

it('öffnet einen abgeschlossenen Monat wieder nach Bestätigung', async () => {
  fetchMock.mockImplementation(async (input: string, init?: RequestInit) => {
    calls.push(input);
    const url = new URL(input, 'http://localhost');
    if (input.endsWith('/berechtigung')) return new Response(JSON.stringify({ darfMonatAbschliessen: true }));
    if (input === '/api/mitarbeiter') return new Response(JSON.stringify([{ id: 1, vorname: 'Max', nachname: 'Mustermann' }]));
    if (input.endsWith('/abteilungen')) return new Response(JSON.stringify([{ id: 2, name: 'Werkstatt' }]));
    if (url.pathname.endsWith('/uebersicht')) {
      return new Response(JSON.stringify({
        items: [{ referenz: { mitarbeiterId: 1, jahr: 2026, monat: 1 }, mitarbeiterName: 'Max Mustermann 1', abteilungIds: [2], festgeschrieben: true, version: 3, festgeschriebenAm: '2026-02-01T10:00:00', kennzahlen }],
        totalElements: 1, page: 0, size: 50, summen: kennzahlen, auswahl: []
      }));
    }
    if (url.pathname.endsWith('/vergleich')) return new Response(JSON.stringify([]));
    if (input.endsWith('/oeffnen') && init?.method === 'POST') {
      return new Response(JSON.stringify({ mitarbeiterId: 1, jahr: 2026, monat: 1, festgeschrieben: false }));
    }
    return new Response(JSON.stringify([]));
  });
  mount('/monatsabschluss?jahr=2026&monat=1&mitarbeiterId=1');
  const oeffnenBtn = await screen.findByRole('button', { name: 'Monat für Max Mustermann 1 wieder öffnen' });
  fireEvent.click(oeffnenBtn);
  expect(confirm).toHaveBeenCalled();
  await waitFor(() => expect(calls.some(c => c.endsWith('/monatsabschluesse/1/2026/1/oeffnen'))).toBe(true));
  expect(toast.success).toHaveBeenCalledWith('Monat für Max Mustermann 1 wieder geöffnet.');
});
