import { useState } from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MaterialbestellungModal } from './MaterialbestellungModal';
import type { BedarfResponse } from '../features/einkauf/types';
import { ToastProvider } from './ui/toast';
const backend = vi.hoisted(() => ({ connected: false }));
vi.mock('../features/einkauf/originalBedarfApi', () => ({ get nutztEchtesBackend() { return backend.connected; } }));
const ok = (body: unknown) => new Response(JSON.stringify(body), { status: 200 });
const writes = () => vi.mocked(fetch).mock.calls.filter(([, init]) => ['POST', 'PUT'].includes(init?.method ?? ''));
function open(props = {}) { return render(<ToastProvider><MaterialbestellungModal isOpen projektSperren onClose={vi.fn()} {...props} /></ToastProvider>); }
function fill(name: string, quantity = '4') {
  fireEvent.change(screen.getByPlaceholderText('z. B. IPE 200, S235'), { target: { value: name } });
  fireEvent.change(screen.getByPlaceholderText('1'), { target: { value: quantity } });
}
beforeEach(() => { backend.connected = false; vi.stubGlobal('fetch', vi.fn(async () => ok([]))); });
afterEach(() => vi.unstubAllGlobals());
describe('Original-1090-Materialmaske', () => {
  it('speichert freien Bedarf ohne Projekt und ohne Warengruppe über den Originalvertrag', async () => {
    const onSuccess = vi.fn(); open({ onSuccess }); fill('Werkstattmaterial');
    fireEvent.click(screen.getByRole('button', { name: 'Alle speichern' }));
    await waitFor(() => expect(onSuccess).toHaveBeenCalledOnce());
    expect(writes()[0][0]).toBe('/api/bestellungen/manuell');
    expect(JSON.parse(String(writes()[0][1]?.body))).toMatchObject({ projektId: null, lieferantId: null, produktname: 'Werkstattmaterial', menge: 4 });
    expect(screen.queryByText('Warengruppe')).not.toBeInTheDocument();
  });
  it('behält den Projektbezug beim Anlegen', async () => {
    open({ initialProjekt: { id: 101, bauvorhaben: 'Musterhalle' } }); fill('Projektmaterial');
    fireEvent.click(screen.getByRole('button', { name: 'Alle speichern' }));
    await waitFor(() => expect(writes()).toHaveLength(1));
    expect(JSON.parse(String(writes()[0][1]?.body)).projektId).toBe(101);
  });
  it('speichert keine leere Position', () => {
    open(); fireEvent.click(screen.getByRole('button', { name: 'Alle speichern' }));
    expect(writes()).toHaveLength(0);
  });
  it('behält die Eingaben bei einem Serverfehler', async () => {
    vi.mocked(fetch).mockImplementation(async (_url, init) => init?.method === 'POST' ? new Response(JSON.stringify({ message: 'Speichern fehlgeschlagen' }), { status: 500 }) : ok([]));
    const onClose = vi.fn(); open({ onClose }); fill('Werkstattmaterial');
    fireEvent.click(screen.getByRole('button', { name: 'Alle speichern' }));
    expect(await screen.findAllByText('Speichern fehlgeschlagen')).not.toHaveLength(0);
    expect(screen.getByPlaceholderText('z. B. IPE 200, S235')).toHaveValue('Werkstattmaterial');
    expect(onClose).not.toHaveBeenCalled();
  });
  it('aktualisiert eine vorhandene Position statt sie erneut anzulegen', async () => {
    open({ editPosition: { id: 77, produktname: 'Alt', menge: 2, einheit: 'Stück', projektId: 101 } });
    fill('Geändert', '5'); fireEvent.click(screen.getByRole('button', { name: 'Änderungen speichern' }));
    await waitFor(() => expect(writes()).toHaveLength(1));
    expect(writes()[0][0]).toBe('/api/bestellungen/77');
    expect(writes()[0][1]?.method).toBe('PUT');
    expect(JSON.parse(String(writes()[0][1]?.body))).toMatchObject({ menge: 5, produktname: 'Geändert', projektId: 101 });
  });
  it('löst die Artikel-Verknüpfung über einen eigenen Knopf neben dem Suchfeld', () => {
    open({ editPosition: { id: 77, produktname: 'Winkel', menge: 2, einheit: 'm', projektId: 101, artikelId: 71, externeArtikelnummer: 'M-0071' } });
    const entfernen = screen.getByRole('button', { name: 'Artikel-Verknüpfung entfernen' });
    expect(entfernen.parentElement?.closest('button')).toBeNull();
    expect(screen.getByRole('button', { name: '#M-0071' })).toBeInTheDocument();
    fireEvent.click(entfernen);
    expect(screen.getByRole('button', { name: 'Artikel suchen...' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Artikel-Verknüpfung entfernen' })).not.toBeInTheDocument();
  });
  it('lädt die bestehende Artikelsuche', async () => {
    vi.mocked(fetch).mockImplementation(async url => ok(String(url).startsWith('/api/artikel?') ? { artikel: [{ id: 71, produktname: 'IPE 200', verrechnungseinheit: { name: 'STUECK' } }], gesamt: 1 } : []));
    open(); fireEvent.click(screen.getByRole('button', { name: 'Artikel suchen...' }));
    expect(await screen.findByText('IPE 200')).toBeInTheDocument();
  });
  it('speichert im echten Betrieb Vorratsbedarf mit Kommawert und ohne alte Zeugnisanfrage', async () => {
    backend.connected = true; open({ initialProjekt: { id: 101, excKlasse: 'EXC_2' } }); fill('Flachstahl', '2,5');
    fireEvent.click(screen.getByRole('combobox', { name: 'Einheit Position 1' }));
    fireEvent.click(screen.getByRole('option', { name: 'm (Meter)' }));
    fireEvent.click(screen.getByRole('button', { name: 'Alle speichern' }));
    await waitFor(() => expect(writes()).toHaveLength(1));
    expect(writes()[0][0]).toBe('/api/einkauf/bedarf');
    expect(JSON.parse(String(writes()[0][1]?.body))).toMatchObject({ position: { art: 'FREITEXT', basis: { menge: 2.5, einheit: 'METER' } }, liefergruppe: { projektId: 101 } });
    expect(vi.mocked(fetch).mock.calls.some(([url]) => String(url).includes('zeugnis-default'))).toBe(false);
    expect(screen.queryByRole('spinbutton')).not.toBeInTheDocument();
  });
  it('validiert auch leere weitere Zeilen vor dem ersten Schreibzugriff', () => {
    backend.connected = true; open(); fill('Erste Position');
    fireEvent.click(screen.getByRole('button', { name: 'Leere Zeile' }));
    fireEvent.click(screen.getByRole('button', { name: 'Alle speichern' }));
    expect(writes()).toHaveLength(0);
  });
  it('hält bei Teilfehlern den tatsächlichen Eltern-Dialog offen und legt gespeicherte Zeilen nicht doppelt an', async () => {
    backend.connected = true;
    let anzahl = 0;
    vi.mocked(fetch).mockImplementation(async (_url, init) => {
      if (init?.method !== 'POST') return ok([]);
      return ++anzahl === 2 ? new Response(JSON.stringify({ message: 'Zweite Position fehlgeschlagen.' }), { status: 500 }) : ok({});
    });
    function Parent() { const [offen, setOffen] = useState(true); return <ToastProvider>{offen && <MaterialbestellungModal isOpen projektSperren onClose={() => setOffen(false)} onSuccess={() => setOffen(false)} />}</ToastProvider>; }
    render(<Parent />); fill('Erste Position');
    fireEvent.click(screen.getByRole('button', { name: 'Leere Zeile' }));
    fireEvent.change(screen.getByLabelText('Produktname Position 2'), { target: { value: 'Zweite Position' } });
    fireEvent.click(screen.getByRole('button', { name: 'Alle speichern' }));
    await waitFor(() => expect(screen.getByLabelText('Produktname Position 1')).toHaveValue('Zweite Position'));
    fireEvent.click(screen.getByRole('button', { name: 'Alle speichern' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(writes().map(([, init]) => JSON.parse(String(init?.body)).position.bezeichnung)).toEqual(['Erste Position', 'Zweite Position', 'Zweite Position']);
  });
  it('bestätigt eine gewählte Zeugnisanforderung ausschließlich manuell', async () => {
    backend.connected = true; open(); fill('Profil');
    fireEvent.click(screen.getByRole('combobox', { name: 'Zeugnis Position 1' }));
    fireEvent.click(screen.getByRole('option', { name: 'Abnahmeprüfzeugnis 3.1' }));
    const feld = screen.getByRole('checkbox', { name: 'Zeugnisanforderung fachlich geprüft' });
    expect(feld).not.toBeChecked();
    fireEvent.click(screen.getByRole('button', { name: 'Alle speichern' }));
    expect(writes()).toHaveLength(0);
    fireEvent.click(feld); fireEvent.click(screen.getByRole('button', { name: 'Alle speichern' }));
    await waitFor(() => expect(writes()).toHaveLength(1));
    expect(JSON.parse(String(writes()[0][1]?.body)).position.dokumente[0].fachlichBestaetigt).toBe(true);
  });

  it('erhält bei ausgeblendetem Lieferantfeld den vollständigen gespeicherten Bezug', async () => {
    backend.connected = true;
    const bedarf = { id: 77, version: 3, position: { art: 'FREITEXT', artikelId: null, bezeichnung: 'Profil', werkstoff: 'S235', basis: { menge: 4, einheit: 'STUECK', stueckzahl: 4, einzelLaengeMm: 1000 }, schnittForm: null, winkelLinks: '45°', winkelRechts: '90°', dokumente: [], anlageVersionIds: [41], beschaffungsdetails: { lieferantId: 7, kategorieId: 64, externeArtikelnummer: '0017', schnittbildId: 8, schnittAchseId: 7 } }, liefergruppe: { projektId: 101, lieferadresse: 'Musterstraße 1', bedarfstermin: null, lagerzweck: null } } as unknown as BedarfResponse;
    open({ editPosition: { id: 77, bedarf } });
    fireEvent.click(screen.getByRole('button', { name: 'Änderungen speichern' }));
    await waitFor(() => expect(writes()).toHaveLength(1));
    expect(writes()[0][0]).toBe('/api/einkauf/bedarf/77');
    expect(JSON.parse(String(writes()[0][1]?.body))).toMatchObject({ version: 3, liefergruppe: { projektId: 101 }, position: { werkstoff: 'S235', anlageVersionIds: [41], winkelLinks: '45°', winkelRechts: '90°', beschaffungsdetails: { lieferantId: 7, kategorieId: 64, externeArtikelnummer: '0017', schnittbildId: 8, schnittAchseId: 7 } } });
  });

});
