import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, it, vi } from 'vitest';
import { DatevBereich } from './DatevBereich';
import type { Konfiguration, AuswahlStand } from './types';
const { toast } = vi.hoisted(() => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('../../components/ui/toast', () => ({ useToast: () => toast }));
const ids: AuswahlStand[] = [{ mitarbeiterId: 1, jahr: 2026, monat: 8, version: 3, festgeschrieben: true }];
const exportIds = ids.map(({ mitarbeiterId, jahr, monat, version }) => ({ mitarbeiterId, jahr, monat, version }));
const mitarbeiter = [{ id: 1, name: 'Max Mustermann' }, { id: 2, name: 'Erika Mustermann' }];
const kategorien = ['ARBEIT', 'FEIERTAG', 'URLAUB', 'KRANKHEIT', 'FORTBILDUNG', 'ZEITAUSGLEICH', 'KRANKENGELD', 'WIEDEREINGLIEDERUNG'];
let config: Konfiguration; let requests: { url: string; method: string; body: unknown }[]; let exclusions: boolean; let exportConflict: boolean;
const fetchMock = vi.fn();
const response = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
const view = (auswahl = ids, darfEinrichten = true) => <MemoryRouter><DatevBereich auswahl={auswahl} mitarbeiter={mitarbeiter} darfEinrichten={darfEinrichten} /></MemoryRouter>;
async function openExport() { fireEvent.click(screen.getByRole('button', { name: 'Für DATEV exportieren' })); const button = await screen.findByRole('button', { name: 'Vorprüfung starten' }); await waitFor(() => expect(button).toBeEnabled()); return button; }
async function check() { fireEvent.click(await openExport()); await screen.findByText('Die Vorprüfung ist erfolgreich.'); }
beforeEach(() => {
 vi.clearAllMocks(); requests = []; exclusions = true; exportConflict = false;
 config = { version: 2, ziel: 'LODAS', beraterNr: '001234', mandantenNr: '0012', stundenart: 'SOLL', zuordnungen: kategorien.map(kategorie => ({ kategorie, ausgeschlossen: kategorie !== 'ARBEIT', lohnart: kategorie === 'ARBEIT' ? '0200' : '' })), personalnummern: [{ mitarbeiterId: 1, personalnummer: '00014', stundenart: null }, { mitarbeiterId: 2, personalnummer: '00015', stundenart: null }] };
 fetchMock.mockImplementation(async (url: string, init?: RequestInit) => {
  const body = init?.body ? JSON.parse(String(init.body)) : undefined; requests.push({ url, method: init?.method ?? 'GET', body });
  if (url.endsWith('/datev/konfiguration')) return response(config);
  if (url.endsWith('/vorpruefung')) return response({ gueltig: true, fehler: [], ausschluesse: exclusions ? [{ referenz: ids[0], kategorie: 'KORREKTUR', stunden: 1.5, meldung: 'Zeitkontokorrekturen werden nicht ausgezahlt.' }] : [], auswahl: body.auswahl, konfigurationVersion: body.konfigurationVersion });
  if (url.endsWith('/export')) return exportConflict ? response({ message: 'Der Monatsstand wurde wieder geöffnet.' }, 409) : new Response('3;01/08/2026;120,50;01;200;14;\r\n', { headers: { 'Content-Disposition': 'attachment; filename="lodas-2026-08.txt"' } });
  throw new Error(url);
 }); vi.stubGlobal('fetch', fetchMock);
 vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:datev-test'), revokeObjectURL: vi.fn() }));
 vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
});
it('führt Admins über „DATEV einrichten“ in die Einstellungen statt ein Formular aufzuklappen', () => {
 render(view());
 const link = screen.getByRole('link', { name: 'DATEV einrichten' });
 expect(link).toHaveAttribute('href', '/einstellungen#zeiterfassung');
 fireEvent.click(link);
 expect(screen.queryByRole('textbox', { name: 'Beraternummer' })).not.toBeInTheDocument();
 expect(screen.queryByText('DATEV richtet ein Admin unter Einstellungen → Zeiterfassung ein.')).not.toBeInTheDocument();
 expect(requests).toHaveLength(0);
});
it('zeigt Nicht-Admins statt des Knopfs, wer DATEV einrichtet – exportieren geht trotzdem', async () => {
 exclusions = false; render(view(ids, false));
 expect(screen.queryByRole('link', { name: 'DATEV einrichten' })).not.toBeInTheDocument();
 expect(screen.queryByRole('button', { name: 'DATEV einrichten' })).not.toBeInTheDocument();
 expect(screen.getByText('DATEV richtet ein Admin unter Einstellungen → Zeiterfassung ein.')).toBeVisible();
 await check(); fireEvent.click(screen.getByRole('button', { name: 'Datei herunterladen' }));
 await waitFor(() => expect(toast.success).toHaveBeenCalledWith('Datei heruntergeladen – bitte im Steuerbüro importieren.'));
});
it('liest die Einstellungen nur noch und schreibt nie auf die alte Monatsabschluss-Adresse', async () => {
 exclusions = false; render(view()); await check();
 expect(requests.filter(r => r.url.endsWith('/datev/konfiguration')).map(r => r.method)).toEqual(['GET']);
 expect(requests.some(r => r.method === 'PUT')).toBe(false);
 expect(screen.getByText('Als „Arbeit“ gehen die Soll-Stunden (laut Arbeitszeitkonto) ans Steuerbüro.')).toBeVisible();
});
it('nennt abweichende Stunden einzelner Mitarbeiter im Export-Dialog', async () => {
 config = { ...config, stundenart: 'IST', personalnummern: [{ mitarbeiterId: 1, personalnummer: '00014', stundenart: 'SOLL' }] };
 render(view()); await openExport();
 expect(screen.getByText('Als „Arbeit“ gehen die Ist-Stunden (tatsächlich gearbeitet) ans Steuerbüro – bei einzelnen Mitarbeitern abweichend.')).toBeVisible();
});
it('weist bei fehlender Berater- oder Mandantennummer auf die Einstellungen hin und sperrt die Vorprüfung', async () => {
 config = { ...config, beraterNr: '', mandantenNr: null };
 const page = render(view()); fireEvent.click(screen.getByRole('button', { name: 'Für DATEV exportieren' }));
 const dialog = await screen.findByRole('dialog', { name: 'DATEV-Export prüfen' });
 expect(await within(dialog).findByText(/Beraternummer und Mandantennummer fehlen noch/)).toBeVisible();
 expect(within(dialog).getByRole('link', { name: 'Jetzt in den Einstellungen eintragen' })).toHaveAttribute('href', '/einstellungen#zeiterfassung');
 expect(within(dialog).getByRole('button', { name: 'Vorprüfung starten' })).toBeDisabled();
 expect(requests.some(r => r.url.endsWith('/vorpruefung'))).toBe(false);
 page.unmount(); render(view(ids, false)); fireEvent.click(screen.getByRole('button', { name: 'Für DATEV exportieren' }));
 const ohneRecht = await screen.findByRole('dialog', { name: 'DATEV-Export prüfen' });
 await within(ohneRecht).findByText(/Beraternummer und Mandantennummer fehlen noch/);
 expect(within(ohneRecht).queryByRole('link')).not.toBeInTheDocument();
 expect(within(ohneRecht).getByText('DATEV richtet ein Admin unter Einstellungen → Zeiterfassung ein.')).toBeVisible();
});
it('lädt die Einstellungen bei jedem Öffnen neu und prüft gegen die aktuelle Version', async () => {
 exclusions = false; render(view()); await check();
 fireEvent.keyDown(document.activeElement!, { key: 'Escape' });
 config = { ...config, version: 5 };
 fireEvent.click(await openExport());
 await screen.findByText('Die Vorprüfung ist erfolgreich.');
 expect(requests.filter(r => r.url.endsWith('/datev/konfiguration'))).toHaveLength(2);
 expect(requests.filter(r => r.url.endsWith('/vorpruefung')).map(r => (r.body as { konfigurationVersion: number }).konfigurationVersion)).toEqual([2, 5]);
});
it('erklärt per Info-Knopf, was „Für DATEV exportieren“ tut und dass nichts automatisch verschickt wird', () => {
 render(view(ids, false));
 fireEvent.click(screen.getByRole('button', { name: 'Mehr zu: Für DATEV exportieren' }));
 const info = screen.getByRole('dialog', { name: 'Für DATEV exportieren' });
 expect(within(info).getByText(/Danach laden Sie eine LODAS-Textdatei herunter/)).toBeVisible();
 expect(within(info).getByText(/Es wird nichts automatisch verschickt/)).toBeVisible();
 expect(requests).toHaveLength(0);
});
it('zeigt Arbeitsstunden ohne Soll als Ausschluss, der bestätigt werden muss', async () => {
 const normal = fetchMock.getMockImplementation()!;
 fetchMock.mockImplementation((url: string, init?: RequestInit) => url.endsWith('/vorpruefung') ? response({ gueltig: true, fehler: [], ausschluesse: [{ referenz: ids[0], kategorie: 'ARBEIT', stunden: 12, meldung: 'Kein Soll hinterlegt – bitte den Mitarbeiter auf Ist-Stunden stellen.' }], auswahl: exportIds, konfigurationVersion: 2 }) : normal(url, init));
 render(view()); fireEvent.click(await openExport());
 const dialog = await screen.findByRole('dialog', { name: 'DATEV-Export prüfen' });
 expect(await within(dialog).findByText('Kein Soll hinterlegt – bitte den Mitarbeiter auf Ist-Stunden stellen.')).toBeVisible();
 expect(within(dialog).getByText(/Max Mustermann · 08\/2026 · Arbeit/)).toBeVisible();
 expect(within(dialog).getByText('12,00 h')).toBeVisible();
 expect(screen.getByRole('button', { name: 'Datei herunterladen' })).toBeDisabled();
 fireEvent.click(screen.getByRole('checkbox', { name: /ausgeschlossenen Stunden/ }));
 expect(screen.getByRole('button', { name: 'Datei herunterladen' })).toBeEnabled();
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

it('sperrt offene Salden mit echter Version und akzeptiert dieselbe Version erst nach Abschluss', () => {
 const page = render(view([{ ...ids[0], festgeschrieben: false }]));
 expect(screen.getByRole('button', { name: 'Für DATEV exportieren' })).toBeDisabled();
 expect(screen.getByText(/zuerst abschließen/)).toBeVisible();
 page.rerender(view([{ ...ids[0], festgeschrieben: true }]));
 expect(screen.getByRole('button', { name: 'Für DATEV exportieren' })).toBeEnabled();
});
