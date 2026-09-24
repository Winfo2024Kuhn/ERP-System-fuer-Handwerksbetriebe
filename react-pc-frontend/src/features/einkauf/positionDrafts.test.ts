import { describe, expect, it } from 'vitest';
import { positionsWertText } from './konfliktAbgleich';
import { fromPositionSnapshot, toPositionPayload, type PositionDraft } from './positionDrafts';
import type { PositionSnapshot } from './types';

const draft = (overrides: Partial<PositionDraft> = {}): PositionDraft => ({
  art: 'ZEICHNUNGSTEIL', artikelId: null, interneReferenz: 'ZT-014', zeichnungsnummer: 'W-14',
  zeichnungsrevision: 'B', bezeichnung: 'Träger', werkstoff: 'S235JR', abmessung: 'IPE 120',
  menge: '2,5', einheit: 'METER', stueckzahl: '2', einzelLaengeMm: '1250', kgJeMeter: '10,4',
  faktorQuelle: 'Datenblatt', schnittForm: 'GEHRUNG', winkelLinks: '45', winkelRechts: '90',
  bearbeitung: 'Bohren', oberflaeche: 'Verzinkt', dokumente: [], anlageVersionIds: [41],
  ...overrides,
});

describe('toPositionPayload', () => {
  it('verlangt auch bei freiem Material eine Bezeichnung', () => {
    expect(toPositionPayload(draft({ art: 'FREITEXT', bezeichnung: ' ' }))).toMatchObject({ valid: false, field: 'bezeichnung' });
    expect(toPositionPayload(draft({ art: 'FREITEXT', bezeichnung: 'Dichtband', interneReferenz: '', zeichnungsnummer: '', zeichnungsrevision: '', anlageVersionIds: [] }))).toMatchObject({ valid: true, value: { art: 'FREITEXT', bezeichnung: 'Dichtband' } });
  });
  it('weist widersprüchliche Meter- und Zuschnittmengen vor Übernahme ab', () => {
    expect(toPositionPayload(draft({ menge: '5' }))).toMatchObject({ valid: false, field: 'menge' });
    expect(toPositionPayload(draft({ menge: '2,5' }))).toMatchObject({ valid: true });
  });

  it('reicht HiCAD-Positionsnummer, Gewicht und Mantelfläche unverändert durch den Bearbeiten-Dialog', () => {
    const gespeichert = toPositionPayload(draft({ positionsnummer: '1200', gesamtwerte: { menge: 2.5, einheit: 'METER', gesamtgewichtKg: 26, mantelflaecheM2: 1.5 } }));
    expect(gespeichert).toMatchObject({ valid: true, value: { positionsnummer: '1200', basis: { gesamtgewichtKg: 26, mantelflaecheM2: 1.5 } } });
    const zurueck = fromPositionSnapshot((gespeichert as { value: PositionSnapshot }).value);
    expect(zurueck).toMatchObject({ positionsnummer: '1200', gesamtwerte: { gesamtgewichtKg: 26, mantelflaecheM2: 1.5 } });
    expect(positionsWertText('gesamtwerte', zurueck.gesamtwerte, [])).toBe('26 kg · Mantelfläche 1,5 m²');
    expect(fromPositionSnapshot({ ...(gespeichert as { value: PositionSnapshot }).value, positionsnummer: null,
      basis: { menge: 1, einheit: 'STUECK', stueckzahl: 1, einzelLaengeMm: null, kgJeMeter: null, faktorQuelle: null } })).not.toHaveProperty('gesamtwerte');
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
      oberflaeche: 'Verzinkt', dokumente: [], anlageVersionIds: [41], beschaffungsdetails: null,
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

  it('erlaubt eine anlagenfreie Vorbereitung nur für den atomaren Zeichnungsteil-Erstupload', () => {
    const ohneAnlage = draft({ anlageVersionIds: [] });
    expect(toPositionPayload(ohneAnlage)).toMatchObject({ valid: false, field: 'anlageVersionIds' });
    expect(toPositionPayload(ohneAnlage, { anlageBeiErstanlage: true })).toMatchObject({ valid: true, value: { anlageVersionIds: [] } });
  });

  it('requires the document basis and its version before creating a requirement', () => {
    expect(toPositionPayload(draft({ dokumente: [{ art: 'ZEUGNIS_3_1', grundlage: '', grundlageVersion: '', fachlichBestaetigt: false }] }))).toMatchObject({ valid: false, field: 'dokumente' });
  });

  it('erhält Lieferant, Kategorie, Schnittbild, Schnittachse und Lieferanten-Artikelnummer beim Bearbeiten', () => {
    const gespeichert: PositionSnapshot = {
      art: 'ARTIKEL', artikelId: 58, interneReferenz: 'MAT-58', zeichnungsnummer: null, zeichnungsrevision: null,
      bezeichnung: 'Flachstahl', werkstoff: 'S235JR', abmessung: '40x5', basis: { menge: 6, einheit: 'METER', stueckzahl: 2, einzelLaengeMm: 3000, kgJeMeter: 1.57, faktorQuelle: 'Artikelstamm' },
      schnittForm: 'GEHRUNG', winkelLinks: '45', winkelRechts: '90', bearbeitung: null, oberflaeche: null, dokumente: [], anlageVersionIds: [],
      beschaffungsdetails: { lieferantId: 8, kategorieId: 3, schnittbildId: 5, schnittAchseId: 2, externeArtikelnummer: 'MS-4711' },
    };
    const bearbeitet = { ...fromPositionSnapshot(gespeichert), bezeichnung: 'Flachstahl verzinkt' };
    const result = toPositionPayload(bearbeitet);
    expect(result).toMatchObject({ valid: true, value: { bezeichnung: 'Flachstahl verzinkt', beschaffungsdetails: gespeichert.beschaffungsdetails } });
  });

  describe('Beschaffungsdetails nach Wechsel von Artikel oder Zuschnitt', () => {
    const details = { lieferantId: 8, kategorieId: 3, schnittbildId: 5, schnittAchseId: 2, externeArtikelnummer: 'MS-4711' };
    const gespeichert: PositionSnapshot = {
      art: 'ARTIKEL', artikelId: 58, interneReferenz: null, zeichnungsnummer: null, zeichnungsrevision: null,
      bezeichnung: 'Flachstahl', werkstoff: 'S235JR', abmessung: '40x5', basis: { menge: 6, einheit: 'METER', stueckzahl: 2, einzelLaengeMm: 3000, kgJeMeter: 1.57, faktorQuelle: 'Artikelstamm' },
      schnittForm: 'GEHRUNG', winkelLinks: '45', winkelRechts: '90', bearbeitung: null, oberflaeche: null, dokumente: [], anlageVersionIds: [],
      beschaffungsdetails: details,
    };
    const speichern = (aenderung: Partial<PositionDraft>) => {
      const result = toPositionPayload({ ...fromPositionSnapshot(gespeichert), ...aenderung });
      if (!result.valid) throw new Error(result.message);
      return result.value.beschaffungsdetails;
    };

    it('behält sie beim gleichen Artikel und gleichen Zuschnitt', () => {
      expect(speichern({ menge: '6', bearbeitung: 'Entgraten' })).toEqual(details);
    });
    it('verwirft sie, wenn ein anderer Katalogartikel gewählt wird', () => {
      expect(speichern({ artikelId: 59, bezeichnung: 'Rundrohr' })).toBeNull();
    });
    it('verwirft sie, wenn aus dem Katalogartikel freies Material wird', () => {
      expect(speichern({ art: 'FREITEXT', artikelId: null })).toBeNull();
    });
    it('entfernt nur Schnittbild und Schnittachse, wenn der Sonderzuschnitt ausgeschaltet wird', () => {
      expect(speichern({ schnittForm: '', winkelLinks: '', winkelRechts: '' })).toEqual({ ...details, schnittbildId: null, schnittAchseId: null });
    });
    it('zeigt im Abgleich keine internen Kennungen, sondern nur lesbare Angaben', () => {
      const text = positionsWertText('beschaffungsdetails', fromPositionSnapshot(gespeichert).beschaffungsdetails, []);
      expect(text).toBe('Lieferantenangaben hinterlegt\nSchnittbild für den Zuschnitt hinterlegt\nArtikelnummer beim Lieferanten: MS-4711');
      expect(text).not.toMatch(/Nr\./);
    });
  });

});
