import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { DatevBereich } from './DatevBereich';
import type { Konfiguration, AuswahlStand } from './types';
const { toast } = vi.hoisted(() => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('../../components/ui/toast', () => ({ useToast: () => toast }));
const ids: AuswahlStand[] = [{ mitarbeiterId: 1, jahr: 2026, monat: 8, version: 3, festgeschrieben: true }];
const exportIds = ids.map(({ mitarbeiterId, jahr, monat, version }) => ({ mitarbeiterId, jahr, monat, version }));
const mitarbeiter = [{ id: 1, name: 'Max Mustermann' }, { id: 2, name: 'Erika Mustermann' }];
const kategorien = ['ARBEIT', 'FEIERTAG', 'URLAUB', 'KRANKHEIT', 'FORTBILDUNG', 'ZEITAUSGLEICH', 'KRANKENGELD', 'WIEDEREINGLIEDERUNG'];
let config: Konfiguration; let requests: { url: string; body: unknown }[]; let exclusions: boolean; let saveConflict: boolean; let exportConflict: boolean;
const fetchMock = vi.fn();
const response = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
const view = (auswahl = ids) => <DatevBereich auswahl={auswahl} mitarbeiter={mitarbeiter} />;
async function openExport() { fireEvent.click(screen.getByRole('button', { name: 'Für DATEV exportieren' })); const button = await screen.findByRole('button', { name: 'Vorprüfung starten' }); await waitFor(() => expect(button).toBeEnabled()); return button; }
async function check() { fireEvent.click(await openExport()); await screen.findByText('Die Vorprüfung ist erfolgreich.'); }
beforeEach(() => {
 vi.clearAllMocks(); requests = []; exclusions = true; saveConflict = false; exportConflict = false;
 config = { version: 2, ziel: 'LODAS', beraterNr: '001234', mandantenNr: '0012', zuordnungen: kategorien.map(kategorie => ({ kategorie, ausgeschlossen: kategorie !== 'ARBEIT', lohnart: kategorie === 'ARBEIT' ? '0200' : '' })), personalnummern: [{ mitarbeiterId: 1, personalnummer: '00014' }, { mitarbeiterId: 2, personalnummer: '00015' }] };
 fetchMock.mockImplementation(async (url: string, init?: RequestInit) => {
  const body = init?.body ? JSON.parse(String(init.body)) : undefined; requests.push({ url, body });
  if (url.endsWith('/konfiguration')) {
   if (init?.method === 'PUT') { if (saveConflict) return response({ message: 'Einstellungen wurden geändert. Bitte neu laden.' }, 409); config = { ...body, version: 3 }; }
   return response(config);
  }
  if (url.endsWith('/vorpruefung')) return response({ gueltig: true, fehler: [], ausschluesse: exclusions ? [{ referenz: ids[0], kategorie: 'KORREKTUR', stunden: 1.5, meldung: 'Zeitkontokorrekturen werden nicht ausgezahlt.' }] : [], auswahl: body.auswahl, konfigurationVersion: body.konfigurationVersion });
  if (url.endsWith('/export')) return exportConflict ? response({ message: 'Der Monatsstand wurde wieder geöffnet.' }, 409) : new Response('3;01/08/2026;120,50;01;200;14;\r\n', { headers: { 'Content-Disposition': 'attachment; filename="lodas-2026-08.txt"' } });
  throw new Error(url);
 }); vi.stubGlobal('fetch', fetchMock);
 vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:datev-test'), revokeObjectURL: vi.fn() }));
 vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
});
it('lädt erst bei Bedarf und speichert Nummern mit führenden Nullen ohne zusätzliche Exportauswahl', async () => {
 render(view()); expect(requests).toHaveLength(0); fireEvent.click(screen.getByRole('button', { name: 'DATEV einrichten' }));
 const adviser = await screen.findByRole('textbox', { name: 'Beraternummer' }); expect(adviser).toHaveValue('001234'); fireEvent.focus(adviser); expect(adviser).toHaveValue('001234');
 fireEvent.change(screen.getByRole('textbox', { name: 'Personalnummer für Erika Mustermann' }), { target: { value: '00016' } });
 fireEvent.click(screen.getByRole('button', { name: 'Einstellungen speichern' })); await waitFor(() => expect(config.personalnummern[1].personalnummer).toBe('00016'));
 expect(config.beraterNr).toBe('001234'); expect(config.zuordnungen[0].lohnart).toBe('0200'); expect(requests.filter(r => r.url.endsWith('/export'))).toHaveLength(0);
});
it('kann unvollständige Einstellungen speichern und lehnt ungültige oder doppelte Nummern ab', async () => {
 config = { version: 0, ziel: 'LODAS', beraterNr: '', mandantenNr: '', zuordnungen: [], personalnummern: [] }; render(view()); fireEvent.click(screen.getByRole('button', { name: 'DATEV einrichten' })); await screen.findByRole('textbox', { name: 'Beraternummer' });
 fireEvent.click(screen.getByRole('button', { name: 'Einstellungen speichern' })); await waitFor(() => expect(requests.some(r => r.body && r.url.endsWith('/konfiguration'))).toBe(true)); const before = requests.length;
 fireEvent.change(screen.getByRole('textbox', { name: 'Beraternummer' }), { target: { value: '12,5' } }); fireEvent.click(screen.getByRole('button', { name: 'Einstellungen speichern' })); expect(requests).toHaveLength(before); expect(toast.error).toHaveBeenCalled();
 fireEvent.change(screen.getByRole('textbox', { name: 'Beraternummer' }), { target: { value: '' } });
 fireEvent.change(screen.getByRole('textbox', { name: 'Personalnummer für Max Mustermann' }), { target: { value: '00014' } }); fireEvent.change(screen.getByRole('textbox', { name: 'Personalnummer für Erika Mustermann' }), { target: { value: '14' } }); fireEvent.click(screen.getByRole('button', { name: 'Einstellungen speichern' })); expect(requests).toHaveLength(before); await screen.findByText(/mehrfach vergeben/);
});
it('prüft nur explizite Stände, zeigt ausgeschlossene Stunden und verlangt deren Bestätigung', async () => {
 render(view()); await check(); const dialog = screen.getByRole('dialog', { name: 'DATEV-Export prüfen' }); expect(within(dialog).getByText('Max Mustermann')).toBeVisible(); expect(within(dialog).queryByText('Erika Mustermann')).not.toBeInTheDocument(); expect(within(dialog).getByText('1,50 h')).toBeVisible(); expect(within(dialog).getAllByText(/08\/2026/)).toHaveLength(2);
 expect(requests.find(r => r.url.endsWith('/vorpruefung'))?.body).toEqual({ auswahl: exportIds, konfigurationVersion: 2 });
 expect(screen.getByRole('button', { name: 'Datei herunterladen' })).toBeDisabled(); fireEvent.click(screen.getByRole('checkbox', { name: /ausgeschlossenen Stunden/ })); expect(screen.getByRole('button', { name: 'Datei herunterladen' })).toBeEnabled();
});
it('lädt den Server-Dateinamen herunter und gibt die Blob-URL wieder frei', async () => {
 exclusions = false; render(view()); await check(); fireEvent.click(screen.getByRole('button', { name: 'Datei herunterladen' }));
 await waitFor(() => expect(URL.createObjectURL).toHaveBeenCalled()); expect(HTMLAnchorElement.prototype.click).toHaveBeenCalled(); expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:datev-test'); expect(toast.success).toHaveBeenCalledWith('Datei heruntergeladen – bitte im Steuerbüro importieren.');
 const anchor = vi.mocked(HTMLAnchorElement.prototype.click).mock.instances[0] as HTMLAnchorElement; expect(anchor.download).toBe('lodas-2026-08.txt'); expect(anchor.href).toBe('blob:datev-test');
});
it('verwirft die Freigabe sofort bei geänderter Auswahl oder Versionsstand', async () => {
 const page = render(view()); await check(); fireEvent.click(screen.getByRole('checkbox', { name: /ausgeschlossenen Stunden/ })); page.rerender(view([{ ...ids[0], version: 4 }])); expect(screen.getByRole('button', { name: 'Datei herunterladen' })).toBeDisabled(); expect(screen.queryByText('Die Vorprüfung ist erfolgreich.')).not.toBeInTheDocument();
});
it('verwirft eine verspätete Vorprüfung nach Auswahlwechsel', async () => {
 const normal = fetchMock.getMockImplementation()!; let finish!: (value: Response) => void;
 fetchMock.mockImplementation((url: string, init?: RequestInit) => url.endsWith('/vorpruefung') ? new Promise(resolve => { finish = resolve; }) : normal(url, init));
 const page = render(view()); fireEvent.click(await openExport()); page.rerender(view([{ ...ids[0], mitarbeiterId: 2 }]));
 await act(async () => finish(response({ gueltig: true, fehler: [], ausschluesse: [], auswahl: exportIds, konfigurationVersion: 2 }))); expect(screen.getByRole('button', { name: 'Datei herunterladen' })).toBeDisabled();
});
it('erteilt nach Downloadkonflikt keine automatische neue Freigabe', async () => {
 exclusions = false; exportConflict = true; render(view()); await check(); fireEvent.click(screen.getByRole('button', { name: 'Datei herunterladen' })); await waitFor(() => expect(toast.error).toHaveBeenCalled()); expect(screen.getByRole('button', { name: 'Datei herunterladen' })).toBeDisabled(); expect(URL.createObjectURL).not.toHaveBeenCalled(); expect(requests.filter(r => r.url.endsWith('/vorpruefung'))).toHaveLength(1);
});
it('zeigt beim Speicherkonflikt den Entwurf und lädt neue Einstellungen nur ausdrücklich', async () => {
 saveConflict = true; render(view()); fireEvent.click(screen.getByRole('button', { name: 'DATEV einrichten' })); const input = await screen.findByRole('textbox', { name: 'Mandantennummer' }); fireEvent.change(input, { target: { value: '00021' } }); fireEvent.click(screen.getByRole('button', { name: 'Einstellungen speichern' })); await waitFor(() => expect(toast.error).toHaveBeenCalled()); expect(input).toHaveValue('00021'); expect(requests.filter(r => r.body === undefined)).toHaveLength(1);
});
it('weist mehrere Monate und fehlende Versionsstände ohne stille Teilmenge zurück', () => {
 const page = render(view([ids[0], { ...ids[0], monat: 7 }])); expect(screen.getByRole('button', { name: 'Für DATEV exportieren' })).toBeDisabled(); expect(screen.getByText(/einen Monat/)).toBeVisible(); page.rerender(view([{ ...ids[0], version: null }])); expect(screen.getByRole('button', { name: 'Für DATEV exportieren' })).toBeDisabled();
});
it('gibt einen früheren Auswahlstand nach Hin- und Rückwechsel nicht erneut frei', async () => {
 exclusions = false; const page = render(view()); await check(); page.rerender(view([{ ...ids[0], version: 4 }])); page.rerender(view()); expect(screen.getByRole('button', { name: 'Datei herunterladen' })).toBeDisabled();
});
it('erteilt bei fachlichen Vorprüfungsfehlern keine Downloadfreigabe', async () => {
 const normal = fetchMock.getMockImplementation()!;
 fetchMock.mockImplementation((url: string, init?: RequestInit) => url.endsWith('/vorpruefung') ? response({ gueltig: false, fehler: [{ referenz: ids[0], kategorie: 'STAND', stunden: null, meldung: 'Monat ist noch offen.' }], ausschluesse: [], auswahl: exportIds, konfigurationVersion: 2 }) : normal(url, init));
 render(view()); fireEvent.click(await openExport()); await screen.findByText('Monat ist noch offen.'); expect(screen.getByRole('button', { name: 'Datei herunterladen' })).toBeDisabled();
});
it('verwirft einen verspäteten Download nach Schließen des Dialogs', async () => {
 exclusions = false; const normal = fetchMock.getMockImplementation()!; let finish!: (value: Response) => void;
 fetchMock.mockImplementation((url: string, init?: RequestInit) => url.endsWith('/export') ? new Promise(resolve => { finish = resolve; }) : normal(url, init));
 render(view()); await check(); fireEvent.click(screen.getByRole('button', { name: 'Datei herunterladen' })); fireEvent.keyDown(document.activeElement!, { key: 'Escape' });
 await act(async () => finish(new Response('test', { headers: { 'Content-Disposition': 'attachment; filename="lodas-2026-08.txt"' } }))); expect(URL.createObjectURL).not.toHaveBeenCalled(); expect(toast.success).not.toHaveBeenCalled();
});
it('verlangt nach Änderung der Einstellungen erst Speichern und eine neue Prüfung', async () => {
 exclusions = false; render(view()); fireEvent.click(screen.getByRole('button', { name: 'DATEV einrichten' })); await screen.findByRole('textbox', { name: 'Beraternummer' }); await check();
 fireEvent.keyDown(document.activeElement!, { key: 'Escape' }); fireEvent.change(screen.getByRole('textbox', { name: 'Mandantennummer' }), { target: { value: '00021' } });
 fireEvent.click(screen.getByRole('button', { name: 'Für DATEV exportieren' })); expect(screen.getByRole('button', { name: 'Vorprüfung starten' })).toBeDisabled(); expect(screen.getByRole('button', { name: 'Datei herunterladen' })).toBeDisabled(); expect(screen.getByText(/zuerst die geänderten/)).toBeVisible();
});

it('sperrt offene Salden mit echter Version und akzeptiert dieselbe Version erst nach Abschluss', () => {
 const page = render(view([{ ...ids[0], festgeschrieben: false }]));
 expect(screen.getByRole('button', { name: 'Für DATEV exportieren' })).toBeDisabled();
 expect(screen.getByText(/zuerst abschließen/)).toBeVisible();
 page.rerender(view([{ ...ids[0], festgeschrieben: true }]));
 expect(screen.getByRole('button', { name: 'Für DATEV exportieren' })).toBeEnabled();
});
