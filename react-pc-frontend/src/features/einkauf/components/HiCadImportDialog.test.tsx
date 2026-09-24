import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { ToastProvider } from '../../../components/ui/toast';
import { HiCadImportDialog } from './HiCadImportDialog';

vi.mock('./StammdatenAuswahl', () => ({ StammdatenAuswahl: ({ art, onChange }: { art: string; onChange: (value: { id: number; name: string }) => void }) => <button type="button" onClick={() => onChange({ id: 19, name: 'Projekt 19' })}>{art} auswählen</button> }));

it('übernimmt ausschließlich bestätigte HiCAD-Zeilen und behält bereits importierte Teilmengen', async () => {
  const calls: { path: string; body?: unknown }[] = [];
  global.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input); calls.push({ path, body: init?.body });
    if (path.includes('/vorschau?')) return { ok: true, status: 201, json: async () => ({ id: 3, dateiHash: 'hash', dateiSchonImportiert: false, zeilen: [
      { zeilennummer: 1, rohtext: 'Profil 40x40;10', vorschlag: { art: 'ARTIKEL', bezeichnung: 'Profil 40x40', basis: { menge: 10, einheit: 'STUECK' }, dokumente: [], anlageVersionIds: [] }, artikelKandidaten: [], bereitsUebernommen: false, hinweise: [], bilder: [] },
      { zeilennummer: 2, rohtext: 'Blech;5', vorschlag: { art: 'ARTIKEL', bezeichnung: 'Blech', basis: { menge: 5, einheit: 'STUECK' }, dokumente: [], anlageVersionIds: [] }, artikelKandidaten: [], bereitsUebernommen: true, hinweise: ['Bereits teilweise übernommen'], bilder: [] },
    ] }) } as Response;
    if (path.endsWith('/hicad/3')) return { ok: true, json: async () => ({ id: 3, version: 1, duplikat: false, zeilen: [
      { zeilennummer: 1, gesamtmenge: 10, uebernommeneMenge: 0, verbleibendeMenge: 10, vollstaendigUebernommen: false },
      { zeilennummer: 2, gesamtmenge: 5, uebernommeneMenge: 2, verbleibendeMenge: 3, vollstaendigUebernommen: false },
    ] }) } as Response;
    if (path.endsWith('/uebernehmen')) return { ok: true, status: 200, json: async () => [] } as Response;
    return { ok: true, json: async () => [] } as Response;
  });
  render(<ToastProvider><HiCadImportDialog onClose={vi.fn()} onImported={vi.fn()} /></ToastProvider>);
  fireEvent.click(screen.getByRole('button', { name: 'Projekt auswählen' }));
  fireEvent.change(screen.getByLabelText('HiCAD-Exceldatei'), { target: { files: [new File(['Profil'], 'material.xlsx')] } });
  fireEvent.click(screen.getByRole('button', { name: 'Vorschau laden' }));
  await screen.findByRole('heading', { name: /Profil 40x40/ });
  expect(screen.getByLabelText('Zeile 1 übernehmen')).toBeChecked();
  fireEvent.click(screen.getByRole('button', { name: 'Ausgewählte Zeilen übernehmen' }));
  await waitFor(() => expect(calls.some(call => call.path.endsWith('/uebernehmen'))).toBe(true));
  expect(screen.getByText(/bereits teilweise übernommen/i)).toBeInTheDocument();
});

it('zeigt bei einer ungültigen Teilmenge Feldfehler und Toast ohne Übernahmeaufruf', async () => {
  const antwort = vi.fn(async (eingabe: RequestInfo | URL) => {
    const pfad = String(eingabe);
    if (pfad.includes('/vorschau?')) return { ok: true, json: async () => ({ id: 3, dateiHash: 'hash', dateiSchonImportiert: false, zeilen: [{ zeilennummer: 1, rohtext: 'Profil;10', vorschlag: { art: 'ARTIKEL', artikelId: 1, bezeichnung: 'Profil', basis: { menge: 10, einheit: 'STUECK' }, dokumente: [], anlageVersionIds: [] }, artikelKandidaten: [], bereitsUebernommen: false, hinweise: [], bilder: [] }] }) } as Response;
    if (pfad.endsWith('/hicad/3')) return { ok: true, json: async () => ({ id: 3, version: 1, duplikat: false, zeilen: [{ zeilennummer: 1, gesamtmenge: 10, uebernommeneMenge: 0, verbleibendeMenge: 10, vollstaendigUebernommen: false }] }) } as Response;
    return { ok: true, json: async () => ({}) } as Response;
  });
  global.fetch = antwort;
  render(<ToastProvider><HiCadImportDialog onClose={vi.fn()} onImported={vi.fn()} /></ToastProvider>);
  fireEvent.click(screen.getByRole('button', { name: 'Projekt auswählen' }));
  fireEvent.change(screen.getByLabelText('HiCAD-Exceldatei'), { target: { files: [new File(['Profil'], 'material.xls')] } });
  fireEvent.click(screen.getByRole('button', { name: 'Vorschau laden' }));
  await screen.findByRole('heading', { name: /Zeile 1/ });
  fireEvent.change(screen.getByLabelText('Menge Zeile 1'), { target: { value: '11' } });
  fireEvent.click(screen.getByRole('button', { name: 'Ausgewählte Zeilen übernehmen' }));
  expect((await screen.findAllByRole('alert')).some(meldung => meldung.textContent?.includes('gültige Teilmenge'))).toBe(true);
  expect((await screen.findAllByText(/Bitte eine gültige Teilmenge/)).length).toBeGreaterThanOrEqual(2);
  expect(antwort.mock.calls.some(([pfad]) => String(pfad).endsWith('/uebernehmen'))).toBe(false);
});
