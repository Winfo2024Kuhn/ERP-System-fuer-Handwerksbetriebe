import { describe, expect, it } from 'vitest';
import { neueMaterialPosition, materialPositionAusBedarf, materialbedarfPayload } from './materialbedarfAdapter';
import type { BedarfResponse } from './types';

describe('Materialbedarf aus der EN1090-Maske', () => {
  it('legt Freitext mit deutscher Menge ohne Lieferanten für Vorrat an', () => {
    const result = materialbedarfPayload({ ...neueMaterialPosition(), produktname: 'Flachstahl', menge: '12,5', einheit: 'METER' }, null);
    expect(result).toMatchObject({ position: { art: 'FREITEXT', bezeichnung: 'Flachstahl', basis: { menge: 12.5, einheit: 'METER' } }, liefergruppe: { projektId: null, lagerzweck: 'Werkstatt / auf Vorrat' } });
    expect(result).not.toHaveProperty('lieferantId');
  });
  it('verknüpft Katalogartikel mit dem gewählten Projekt und Zuschnitt', () => {
    const result = materialbedarfPayload({ ...neueMaterialPosition(), produktname: 'Profil', artikelId: 17, menge: '4', fixzuschnitt: true, fixmassMm: '1234,5', sonderzuschnitt: true, schnittForm: 'A', winkelLinks: '45,5', winkelRechts: '90' }, 9);
    expect(result).toMatchObject({ position: { art: 'ARTIKEL', artikelId: 17, schnittForm: 'A', winkelLinks: '45,5', basis: { menge: 4, einzelLaengeMm: 1234.5 } }, liefergruppe: { projektId: 9, lagerzweck: null } });
  });
  it.each(['', '0', '-1', '1,', '1.5'])('lehnt ungültige Mengen %s vor dem Schreiben ab', menge => {
    expect(() => materialbedarfPayload({ ...neueMaterialPosition(), produktname: 'Material', menge }, 9)).toThrow();
  });
  it('erhält nicht editierte technische Angaben und Version eines bestehenden Bedarfs', () => {
    const source = { id: 5, version: 7, position: { art: 'ARTIKEL', artikelId: 17, bezeichnung: 'Profil', interneReferenz: 'P-9', zeichnungsnummer: 'Z-9', zeichnungsrevision: 'A', werkstoff: 'S235', abmessung: 'IPE200', basis: { menge: 4, einheit: 'STUECK', stueckzahl: 4, einzelLaengeMm: 1000.5, kgJeMeter: 22.4, faktorQuelle: 'Katalog' }, schnittForm: 'B', winkelLinks: '45', winkelRechts: '90', bearbeitung: 'Bohren', oberflaeche: 'Verzinkt', dokumente: [{ art: 'ZEUGNIS_3_1', grundlage: 'Norm', grundlageVersion: '2024', fachlichBestaetigt: true }], anlageVersionIds: [11], beschaffungsdetails: { lieferantId: 8, kategorieId: 3, schnittbildId: 5, schnittAchseId: 2, externeArtikelnummer: 'MS-4711' } }, liefergruppe: { projektId: 9, lagerzweck: null, lieferadresse: 'Musterstraße 1', bedarfstermin: '2026-10-01' } } as BedarfResponse;
    const result = materialbedarfPayload(materialPositionAusBedarf(source), 9, source);
    expect(result).toMatchObject({ version: 7, position: source.position, liefergruppe: source.liefergruppe });
  });
  it('behält die HiCAD-Positionsnummer und rechnet Gewicht und Mantelfläche bei geänderter Menge anteilig um', () => {
    const source = { id: 6, version: 2, position: { art: 'FREITEXT', artikelId: null, bezeichnung: 'Rohr 76.1x4', positionsnummer: '1101',
      basis: { menge: 40.331, einheit: 'KILOGRAMM', stueckzahl: 2, einzelLaengeMm: 2835.7, kgJeMeter: null, faktorQuelle: null, gesamtgewichtKg: 40.331, mantelflaecheM2: 2.5727 },
      schnittForm: null, winkelLinks: null, winkelRechts: null, dokumente: [], anlageVersionIds: [] }, liefergruppe: { projektId: 9 } } as unknown as BedarfResponse;
    const unveraendert = materialbedarfPayload(materialPositionAusBedarf(source), 9, source);
    expect(unveraendert.position).toMatchObject({ positionsnummer: '1101', basis: { gesamtgewichtKg: 40.331, mantelflaecheM2: 2.5727 } });
    const halbiert = materialbedarfPayload({ ...materialPositionAusBedarf(source), menge: '20,1655' }, 9, source);
    expect(halbiert.position.basis).toMatchObject({ menge: 20.1655, gesamtgewichtKg: 20.166, mantelflaecheM2: 1.2864 });
    const andereEinheit = materialbedarfPayload({ ...materialPositionAusBedarf(source), einheit: 'STUECK', menge: '2' }, 9, source);
    expect(andereEinheit.position.basis).not.toHaveProperty('mantelflaecheM2');
    expect(andereEinheit.position.positionsnummer).toBe('1101');
  });
  describe('Beschaffungsdetails beim Bearbeiten', () => {
    const details = { lieferantId: 8, kategorieId: 3, schnittbildId: 5, schnittAchseId: 2, externeArtikelnummer: 'MS-4711' };
    const source = { id: 5, version: 7, position: { art: 'ARTIKEL', artikelId: 17, bezeichnung: 'Profil', basis: { menge: 4, einheit: 'STUECK', stueckzahl: 4, einzelLaengeMm: null, kgJeMeter: null, faktorQuelle: null }, schnittForm: 'B', winkelLinks: '45', winkelRechts: '90', dokumente: [], anlageVersionIds: [], beschaffungsdetails: details }, liefergruppe: { projektId: 9 } } as unknown as BedarfResponse;
    it('behält sie beim gleichen Artikel', () => {
      expect(materialbedarfPayload({ ...materialPositionAusBedarf(source), menge: '5' }, 9, source).position.beschaffungsdetails).toEqual(details);
    });
    it('verwirft sie bei einem anderen Katalogartikel', () => {
      expect(materialbedarfPayload({ ...materialPositionAusBedarf(source), artikelId: 18, produktname: 'Rohr' }, 9, source).position.beschaffungsdetails).toBeNull();
    });
    it('verwirft sie, wenn der Katalogbezug entfernt wird', () => {
      const result = materialbedarfPayload({ ...materialPositionAusBedarf(source), artikelId: null }, 9, source).position;
      expect(result).toMatchObject({ art: 'FREITEXT', beschaffungsdetails: null });
    });
    it('entfernt Schnittbild und Schnittachse ohne Sonderzuschnitt', () => {
      expect(materialbedarfPayload({ ...materialPositionAusBedarf(source), sonderzuschnitt: false }, 9, source).position.beschaffungsdetails)
        .toEqual({ ...details, schnittbildId: null, schnittAchseId: null });
    });
  });
  it('berechnet bei geänderter Metermenge ganze Profilstücke aus dem erhaltenen Zuschnitt', () => {
    const source = { id: 5, version: 7, position: { art: 'ARTIKEL', artikelId: 17, bezeichnung: 'Profil', basis: { menge: 4, einheit: 'METER', stueckzahl: 2, einzelLaengeMm: 2000, kgJeMeter: 22.4, faktorQuelle: 'Katalog' }, dokumente: [], anlageVersionIds: [] }, liefergruppe: { projektId: 9 } } as unknown as BedarfResponse;
    const draft = { ...materialPositionAusBedarf(source), menge: '6' };
    expect(materialbedarfPayload(draft, 9, source).position.basis).toEqual({ menge: 6, einheit: 'METER', stueckzahl: 3, einzelLaengeMm: 2000, kgJeMeter: 22.4, faktorQuelle: 'Katalog' });
    expect(() => materialbedarfPayload({ ...draft, menge: '5' }, 9, source)).toThrow(/ganze Stückzahl/);
  });
  it('legt Zeugnisse nur nach ausdrücklicher fachlicher Bestätigung an', () => {
    const draft = { ...neueMaterialPosition(), produktname: 'Profil', zeugnis: 'ZEUGNIS_3_1' };
    expect(() => materialbedarfPayload(draft, 9)).toThrow(/Zeugnisanforderung fachlich prüfen und bestätigen/);
    expect(materialbedarfPayload({ ...draft, zeugnisBestaetigt: true }, 9).position.dokumente).toEqual([expect.objectContaining({ art: 'ZEUGNIS_3_1', fachlichBestaetigt: true })]);
  });

  it.each([['A', '45°'], ['A', '45.5°'], ['A', '270'], [null, '45°']])('erhält bestehende Winkel %s / %s unverändert', (schnittForm, winkelLinks) => {
    const source = { id: 5, version: 7, position: { art: 'ARTIKEL', artikelId: 17, bezeichnung: 'Profil', basis: { menge: 2, einheit: 'STUECK', stueckzahl: 2, einzelLaengeMm: 2000 }, schnittForm, winkelLinks, winkelRechts: '90°', dokumente: [], anlageVersionIds: [] }, liefergruppe: { projektId: 9 } } as unknown as BedarfResponse;
    const result = materialbedarfPayload(materialPositionAusBedarf(source), 9, source);
    expect(result.position).toMatchObject({ schnittForm, winkelLinks, winkelRechts: '90°' });
  });

});
