import { describe, expect, it } from 'vitest';
import { toPositionPayload, type PositionDraft } from './positionDrafts';

const draft = (overrides: Partial<PositionDraft> = {}): PositionDraft => ({
  art: 'ZEICHNUNGSTEIL', artikelId: null, interneReferenz: 'ZT-014', zeichnungsnummer: 'W-14',
  zeichnungsrevision: 'B', bezeichnung: 'Träger', werkstoff: 'S235JR', abmessung: 'IPE 120',
  menge: '2,5', einheit: 'METER', stueckzahl: '2', einzelLaengeMm: '1250', kgJeMeter: '10,4',
  faktorQuelle: 'Datenblatt', schnittForm: 'GEHRUNG', winkelLinks: '45', winkelRechts: '90',
  bearbeitung: 'Bohren', oberflaeche: 'Verzinkt', dokumente: [], anlageVersionIds: [41],
  ...overrides,
});

describe('toPositionPayload', () => {
  it('weist widersprüchliche Meter- und Zuschnittmengen vor Übernahme ab', () => {
    expect(toPositionPayload(draft({ menge: '5' }))).toMatchObject({ valid: false, field: 'menge' });
    expect(toPositionPayload(draft({ menge: '2,5' }))).toMatchObject({ valid: true });
  });

  it('nennt das tatsächlich ungültige Zahlenfeld', () => {
    expect(toPositionPayload(draft({ winkelLinks: '400' }))).toMatchObject({ valid: false, field: 'winkelLinks' });
  });

  it('wandelt deutsches Komma und beide Winkel in den vollständigen Snapshot um', () => {
    expect(toPositionPayload(draft())).toEqual({ valid: true, value: {
      art: 'ZEICHNUNGSTEIL', artikelId: null, interneReferenz: 'ZT-014', zeichnungsnummer: 'W-14',
      zeichnungsrevision: 'B', bezeichnung: 'Träger', werkstoff: 'S235JR', abmessung: 'IPE 120',
      basis: { menge: 2.5, einheit: 'METER', stueckzahl: 2, einzelLaengeMm: 1250, kgJeMeter: 10.4, faktorQuelle: 'Datenblatt' },
      schnittForm: 'GEHRUNG', winkelLinks: '45', winkelRechts: '90', bearbeitung: 'Bohren',
      oberflaeche: 'Verzinkt', dokumente: [], anlageVersionIds: [41],
    }});
  });

  it('lehnt leere und unvollständige Mengen ab, ohne sie als Null zu speichern', () => {
    expect(toPositionPayload(draft({ menge: '' }))).toMatchObject({ valid: false });
    expect(toPositionPayload(draft({ menge: '2,' }))).toMatchObject({ valid: false });
  });

  it('lehnt negative Mengen und zu viele Dezimalstellen ab', () => {
    expect(toPositionPayload(draft({ menge: '-1' }))).toMatchObject({ valid: false });
    expect(toPositionPayload(draft({ menge: '1,1234567' }))).toMatchObject({ valid: false });
  });

  it('uses the entered piece quantity for both Menge and Stückzahl', () => {
    const result = toPositionPayload(draft({ art: 'ARTIKEL', artikelId: 58, einheit: 'STUECK', menge: '3', stueckzahl: '' }));
    expect(result).toMatchObject({ valid: true, value: { basis: { menge: 3, einheit: 'STUECK', stueckzahl: 3 } } });
  });

  it('requires a selected catalogue article instead of saving an empty article position', () => {
    expect(toPositionPayload(draft({ art: 'ARTIKEL', artikelId: null }))).toMatchObject({ valid: false, field: 'artikelId', message: 'Bitte wählen Sie einen Artikel aus dem Katalog.' });
  });

  it('requires drawing identity and a revisioned attachment for drawing parts', () => {
    expect(toPositionPayload(draft({ interneReferenz: '', zeichnungsnummer: '', zeichnungsrevision: '', anlageVersionIds: [] }))).toMatchObject({ valid: false, field: 'interneReferenz' });
  });

  it('requires the document basis and its version before creating a requirement', () => {
    expect(toPositionPayload(draft({ dokumente: [{ art: 'ZEUGNIS_3_1', grundlage: '', grundlageVersion: '', fachlichBestaetigt: false }] }))).toMatchObject({ valid: false, field: 'dokumente' });
  });
});
