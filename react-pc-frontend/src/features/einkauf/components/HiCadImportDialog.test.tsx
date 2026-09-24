import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { ToastProvider } from '../../../components/ui/toast';
import { HiCadImportDialog } from './HiCadImportDialog';

vi.mock('./StammdatenAuswahl', () => ({ StammdatenAuswahl: ({ art, onChange: geändert }: { art: string; onChange: (wert: { id: number; name: string }) => void }) => <button type="button" onClick={() => geändert({ id: 19, name: 'Projekt 19' })}>{art} auswählen</button> }));

it('übernimmt ausschließlich bestätigte HiCAD-Zeilen und behält bereits importierte Teilmengen', async () => {
  const aufrufe: { path: string; body?: unknown }[] = [];
  global.fetch = vi.fn(async (eingabe: RequestInfo | URL, optionen?: RequestInit) => {
    const pfad = String(eingabe); aufrufe.push({ path: pfad, body: optionen?.body });
    if (pfad.includes('/vorschau?')) return { ok: true, status: 201, json: async () => ({ id: 3, dateiHash: 'hash', dateiSchonImportiert: false, zeilen: [
      { zeilennummer: 1, rohtext: 'Profil 40x40;10', vorschlag: { art: 'ARTIKEL', bezeichnung: 'Profil 40x40', basis: { menge: 10, einheit: 'STUECK' }, dokumente: [], anlageVersionIds: [] }, artikelKandidaten: [], bereitsUebernommen: false, hinweise: [], bilder: [] },
      { zeilennummer: 2, rohtext: 'Blech;5', vorschlag: { art: 'ARTIKEL', bezeichnung: 'Blech', basis: { menge: 5, einheit: 'STUECK' }, dokumente: [], anlageVersionIds: [] }, artikelKandidaten: [], bereitsUebernommen: true, hinweise: ['Bereits teilweise übernommen'], bilder: [] },
    ] }) } as Response;
    if (pfad.endsWith('/hicad/3')) return { ok: true, json: async () => ({ id: 3, version: 1, duplikat: false, zeilen: [
      { zeilennummer: 1, gesamtmenge: 10, uebernommeneMenge: 0, verbleibendeMenge: 10, vollstaendigUebernommen: false },
      { zeilennummer: 2, gesamtmenge: 5, uebernommeneMenge: 2, verbleibendeMenge: 3, vollstaendigUebernommen: false },
    ] }) } as Response;
    if (pfad.endsWith('/uebernehmen')) return { ok: true, status: 200, json: async () => [] } as Response;
    return { ok: true, json: async () => [] } as Response;
  });
  render(<ToastProvider><HiCadImportDialog schließen={vi.fn()} übernommen={vi.fn()} /></ToastProvider>);
  fireEvent.click(screen.getByRole('button', { name: 'Projekt auswählen' }));
  fireEvent.change(screen.getByLabelText('HiCAD-Exceldatei'), { target: { files: [new File(['Profil'], 'material.xlsx')] } });
  fireEvent.click(screen.getByRole('button', { name: 'Vorschau laden' }));
  await screen.findByRole('heading', { name: /Profil 40x40/ });
  expect(screen.getByLabelText('Zeile 1 übernehmen')).toBeChecked();
  fireEvent.click(screen.getByRole('button', { name: 'Ausgewählte Zeilen übernehmen' }));
  await waitFor(() => expect(aufrufe.some(aufruf => aufruf.path.endsWith('/uebernehmen'))).toBe(true));
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
  render(<ToastProvider><HiCadImportDialog schließen={vi.fn()} übernommen={vi.fn()} /></ToastProvider>);
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

it('erlaubt eine manuelle Artikelzuordnung ohne Kandidaten oder eingebettetes Bild', async () => {
  const übernommen = vi.fn();
  const anfragen: { pfad: string; inhalt?: BodyInit | null }[] = [];
  global.fetch = vi.fn(async (eingabe: RequestInfo | URL, optionen?: RequestInit) => {
    const pfad = String(eingabe); anfragen.push({ pfad, inhalt: optionen?.body });
    if (pfad.includes('/vorschau?')) return { ok: true, json: async () => ({ id: 3, dateiSchonImportiert: false, zeilen: [{ zeilennummer: 1, rohtext: 'Unbekanntes Profil', vorschlag: { art: 'ZEICHNUNGSTEIL', artikelId: null, bezeichnung: 'Profil', basis: { menge: 10, einheit: 'STUECK' }, dokumente: [], anlageVersionIds: [] }, artikelKandidaten: [], bereitsUebernommen: false, hinweise: [], bilder: [] }] }) } as Response;
    if (pfad.endsWith('/hicad/3')) return { ok: true, json: async () => ({ id: 3, version: 1, zeilen: [{ zeilennummer: 1, verbleibendeMenge: 10 }] }) } as Response;
    return { ok: true, status: 200, json: async () => [] } as Response;
  });
  render(<ToastProvider><HiCadImportDialog schließen={vi.fn()} übernommen={übernommen} /></ToastProvider>);
  fireEvent.click(screen.getByRole('button', { name: 'Projekt auswählen' }));
  fireEvent.change(screen.getByLabelText('HiCAD-Exceldatei'), { target: { files: [new File(['Profil'], 'teile.xlsx')] } });
  fireEvent.click(screen.getByRole('button', { name: 'Vorschau laden' }));
  fireEvent.click(await screen.findByRole('button', { name: 'Artikel auswählen' }));
  fireEvent.click(screen.getByRole('button', { name: 'Ausgewählte Zeilen übernehmen' }));
  await waitFor(() => expect(übernommen).toHaveBeenCalledOnce());
  const übernahme = anfragen.find(eintrag => eintrag.pfad.endsWith('/uebernehmen'));
  expect(JSON.parse(String(übernahme?.inhalt)).zeilen[0].korrigiert).toMatchObject({ art: 'ARTIKEL', artikelId: 19 });
});

it('ergänzt eine Zeichnungsanlage und übernimmt sie erst nach ausdrücklicher Freigabe', async () => {
  const übernommen = vi.fn();
  const aufrufe: { pfad: string; inhalt?: BodyInit | null }[] = [];
  global.fetch = vi.fn(async (eingabe: RequestInfo | URL, optionen?: RequestInit) => {
    const pfad = String(eingabe); aufrufe.push({ pfad, inhalt: optionen?.body });
    if (pfad.includes('/vorschau?')) return { ok: true, json: async () => ({ id: 3, dateiSchonImportiert: false, zeilen: [{ zeilennummer: 1, rohtext: 'Sonderteil', vorschlag: { art: 'ZEICHNUNGSTEIL', artikelId: null, interneReferenz: 'ZT-1', zeichnungsnummer: 'Z-1', zeichnungsrevision: 'A', bezeichnung: 'Sonderteil', basis: { menge: 10, einheit: 'STUECK' }, dokumente: [], anlageVersionIds: [] }, artikelKandidaten: [], bereitsUebernommen: false, hinweise: [], bilder: [] }] }) } as Response;
    if (pfad.endsWith('/hicad/3')) return { ok: true, json: async () => ({ id: 3, version: 1, zeilen: [{ zeilennummer: 1, verbleibendeMenge: 10 }] }) } as Response;
    if (pfad.endsWith('/anlagen')) return { ok: true, json: async () => ({ dateiId: 81, dateiname: 'zeichnung.pdf', mimeTyp: 'application/pdf', byteAnzahl: 15, url: '/api/einkauf/hicad/3/bilder/81' }) } as Response;
    return { ok: true, status: 200, json: async () => [] } as Response;
  });
  render(<ToastProvider><HiCadImportDialog schließen={vi.fn()} übernommen={übernommen} /></ToastProvider>);
  fireEvent.click(screen.getByRole('button', { name: 'Projekt auswählen' }));
  fireEvent.change(screen.getByLabelText('HiCAD-Exceldatei'), { target: { files: [new File(['Profil'], 'teile.xlsx')] } });
  fireEvent.click(screen.getByRole('button', { name: 'Vorschau laden' }));
  fireEvent.change(await screen.findByLabelText('Technische Anlage Zeile 1'), { target: { files: [new File(['%PDF-1.7 Dummy'], 'zeichnung.pdf', { type: 'application/pdf' })] } });
  const freigabe = await screen.findByLabelText('Anlage freigeben: zeichnung.pdf');
  fireEvent.click(screen.getByRole('button', { name: 'Ausgewählte Zeilen übernehmen' }));
  expect(aufrufe.some(aufruf => aufruf.pfad.endsWith('/uebernehmen'))).toBe(false);
  fireEvent.click(freigabe);
  fireEvent.click(screen.getByRole('button', { name: 'Ausgewählte Zeilen übernehmen' }));
  await waitFor(() => expect(übernommen).toHaveBeenCalledOnce());
  const übernahme = aufrufe.find(aufruf => aufruf.pfad.endsWith('/uebernehmen'));
  expect(JSON.parse(String(übernahme?.inhalt)).zeilen[0].bestaetigteBildDateiIds).toEqual([81]);
});
