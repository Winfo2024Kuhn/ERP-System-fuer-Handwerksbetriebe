import { afterEach, describe, expect, it, vi } from 'vitest';
import { neueOriginalPosition, originalMaterialPayload, speichereOriginalMaterial } from './originalMaterialApi';
import { materialPositionAusBedarf } from './materialbedarfAdapter';
import type { BedarfResponse } from './types';
vi.mock('./originalBedarfApi', () => ({ nutztEchtesBackend: true }));
afterEach(() => vi.unstubAllGlobals());
describe('Originalmaterial mit echter Bedarfspersistenz', () => {
 it('speichert Freitext, Werkstoff und Originalauswahl mit deutscher Menge', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response('{}', { status: 200 })));
  const p = { ...neueOriginalPosition(), produktname: 'Profil', werkstoffName: 'S235', menge: '2,5', einheit: 'METER' as const, kategorieId: 64, externeArtikelnummer: '0017' };
  const payload = originalMaterialPayload(p, 9, 7);
  await speichereOriginalMaterial(p, payload);
  expect(fetch).toHaveBeenCalledWith('/api/einkauf/bedarf', expect.objectContaining({ method: 'POST' }));
  expect(payload).toMatchObject({ position: { art: 'FREITEXT', werkstoff: 'S235', basis: { menge: 2.5 }, beschaffungsdetails: { lieferantId: 7, kategorieId: 64, externeArtikelnummer: '0017' } }, liefergruppe: { projektId: 9 } });
 });
 it('behält den vollständigen Snapshot und die Ausgangsversion beim Bearbeiten', async () => {
  const bedarf = { id: 12, version: 3, position: { art: 'ARTIKEL', artikelId: 17, bezeichnung: 'Profil', basis: { menge: 4, einheit: 'STUECK', stueckzahl: 4, einzelLaengeMm: 1000, kgJeMeter: 20, faktorQuelle: 'Stamm' }, werkstoff: 'S235', schnittForm: null, winkelLinks: '45°', winkelRechts: null, zeichnungsnummer: 'Z1', zeichnungsrevision: 'A', interneReferenz: 'R1', oberflaeche: 'Verzinkt', dokumente: [], anlageVersionIds: [41] }, liefergruppe: { projektId: 9, lieferadresse: 'Musterstraße 1', bedarfstermin: null, lagerzweck: null } } as unknown as BedarfResponse;
  const p = { ...neueOriginalPosition(), ...materialPositionAusBedarf(bedarf), bedarf, originalId: 12 };
  const payload = originalMaterialPayload(p, 9, null);
  expect(payload).toMatchObject({ version: 3, position: { winkelLinks: '45°', zeichnungsnummer: 'Z1', oberflaeche: 'Verzinkt', anlageVersionIds: [41] } });
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ message: 'Bedarf wurde geändert.' }), { status: 409 })));
  await expect(speichereOriginalMaterial(p, payload)).rejects.toThrow('Bedarf wurde geändert.');
  expect(fetch).toHaveBeenCalledOnce();
  expect(fetch).toHaveBeenCalledWith('/api/einkauf/bedarf/12', expect.objectContaining({ method: 'PUT' }));
 });
 it('lehnt Bearbeiten ohne Ausgangssnapshot und ungültige Positionen ab', () => {
  expect(() => originalMaterialPayload({ ...neueOriginalPosition(), originalId: 12, produktname: 'Profil' }, 9, null)).toThrow(/erneut laden/);
  expect(() => originalMaterialPayload(neueOriginalPosition(), 9, null)).toThrow(/Produktnamen/);
  expect(() => originalMaterialPayload({ ...neueOriginalPosition(), produktname: 'Profil', menge: '2,' }, 9, null)).toThrow(/vollständige Zahl/);
 });
 it('verlangt die manuelle Zeugnisbestätigung und erhält Schnittbild-IDs', () => {
  const p = { ...neueOriginalPosition(), produktname: 'Profil', zeugnis: 'ZEUGNIS_3_1', fixzuschnitt: true, fixmassMm: '1500,5', sonderzuschnitt: true, schnittbildId: 3, schnittAchseId: 2, winkelLinks: '45,5', winkelRechts: '' };
  expect(() => originalMaterialPayload(p, 9, null)).toThrow(/fachlich prüfen/);
  expect(originalMaterialPayload({ ...p, zeugnisBestaetigt: true }, 9, null)).toMatchObject({ position: { winkelLinks: '45,5', winkelRechts: '90', dokumente: [{ fachlichBestaetigt: true }], beschaffungsdetails: { schnittbildId: 3, schnittAchseId: 2 } } });
 });
});
