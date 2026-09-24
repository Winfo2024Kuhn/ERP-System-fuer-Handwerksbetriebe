import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { ToastProvider } from '../../../components/ui/toast';
import { BedarfDialog } from './BedarfDialog';
import type { BedarfResponse } from '../types';

vi.mock('./StammdatenAuswahl', () => ({ StammdatenAuswahl: ({ art, value, onChange }: { art: string; value: { id: number; name: string } | null; onChange: (value: { id: number; name: string } | null) => void }) => <button type="button" onClick={() => onChange(value ? null : { id: 4, name: 'Projekt Nord · Halle' })}>{art}: {value?.name ?? 'auswählen'}</button> }));

it('meldet ungültige Eingaben mit Feldfehler und Toast vor dem Speichern', async () => {
  const bedarf = { id: 44, version: 3, position: { art: 'ARTIKEL', artikelId: 9, bezeichnung: 'Stahlträger', basis: { menge: 12, einheit: 'STUECK' }, dokumente: [], anlageVersionIds: [] }, liefergruppe: { lieferadresse: null, bedarfstermin: null, projektId: null, lagerzweck: 'Werkstatt' }, mengen: { bedarf: 12, lagergedeckt: 0, angefragt: 0, reserviert: 0, bestellt: 0, geliefert: 0, storniert: 0, ungedeckt: 12, disponierbar: 12 }, nachpflegeErforderlich: false, historischerHinweis: null } as BedarfResponse;
  global.fetch = vi.fn(async () => ({ ok: true, json: async () => [] }) as Response);
  render(<ToastProvider><BedarfDialog offen schliessen={vi.fn()} gespeichert={vi.fn()} ausgangsbedarf={bedarf} /></ToastProvider>);
  fireEvent.change(screen.getByLabelText('Menge *'), { target: { value: '0' } });
  fireEvent.click(screen.getByRole('button', { name: 'Bedarf speichern' }));
  expect((await screen.findAllByRole('alert')).some(meldung => meldung.textContent?.includes('Menge'))).toBe(true);
  expect((global.fetch as unknown as ReturnType<typeof vi.fn>).mock.calls.some(([, optionen]) => optionen?.method === 'PUT')).toBe(false);
});

it('erhält beim Bearbeiten Dokumente, Schnittdaten, Bearbeitung, Oberfläche und Lieferdaten', async () => {
  const bedarf: BedarfResponse = { id: 44, version: 3, position: { art: 'ARTIKEL', artikelId: 9, interneReferenz: 'MAT-9', zeichnungsnummer: 'Z-9', zeichnungsrevision: 'C', bezeichnung: 'Stahlträger', werkstoff: 'S355', abmessung: 'IPE 200', basis: { menge: 12, einheit: 'METER', stueckzahl: 3, einzelLaengeMm: 4000, kgJeMeter: 22, faktorQuelle: 'Datenblatt' }, schnittForm: 'GEHRUNG', winkelLinks: '30', winkelRechts: '45', bearbeitung: 'Entgraten', oberflaeche: 'Verzinkt', dokumente: [{ art: 'ZEUGNIS_3_1', grundlage: 'EN 10204', grundlageVersion: '2025', fachlichBestaetigt: true }], anlageVersionIds: [81] }, liefergruppe: { lieferadresse: 'Hof Nord', bedarfstermin: '2026-10-02', projektId: 4, lagerzweck: null }, mengen: { bedarf: 12, lagergedeckt: 0, angefragt: 0, reserviert: 0, bestellt: 0, geliefert: 0, storniert: 0, ungedeckt: 12, disponierbar: 12 }, nachpflegeErforderlich: false, historischerHinweis: null };
  let gespeicherterInhalt: Record<string, unknown> | null = null;
  global.fetch = vi.fn(async (eingabe: RequestInfo | URL, optionen?: RequestInit) => {
    const pfad = String(eingabe);
    if (pfad.includes('/anlagen')) return { ok: true, json: async () => [{ id: 81, dateiname: 'Zeichnung.pdf', revision: 'C', freigegeben: true }] } as Response;
    if (pfad.includes('/api/projekte/simple')) return { ok: true, json: async () => [{ id: 4, auftragsnummer: 'Projekt Nord', bauvorhaben: 'Halle' }] } as Response;
    if (optionen?.method === 'PUT') { gespeicherterInhalt = JSON.parse(String(optionen.body)) as Record<string, unknown>; return { ok: true, json: async () => bedarf } as Response; }
    return { ok: true, json: async () => bedarf } as Response;
  });
  render(<ToastProvider><BedarfDialog offen schliessen={vi.fn()} gespeichert={vi.fn()} ausgangsbedarf={bedarf} /></ToastProvider>);
  fireEvent.click(screen.getByRole('button', { name: 'Projekt: auswählen' }));
  fireEvent.click(screen.getByRole('button', { name: 'Bedarf speichern' }));
  await waitFor(() => expect(gespeicherterInhalt).not.toBeNull());
  expect(gespeicherterInhalt).toMatchObject({ version: 3, position: { dokumente: bedarf.position.dokumente, schnittForm: 'GEHRUNG', winkelLinks: '30', winkelRechts: '45', bearbeitung: 'Entgraten', oberflaeche: 'Verzinkt', basis: { menge: 12, stueckzahl: 3, einzelLaengeMm: 4000, kgJeMeter: 22, faktorQuelle: 'Datenblatt' }, anlageVersionIds: [81] }, liefergruppe: { lieferadresse: 'Hof Nord', bedarfstermin: '2026-10-02', projektId: 4 } });
});
