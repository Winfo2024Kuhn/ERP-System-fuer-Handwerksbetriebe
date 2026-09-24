import { validateNumberDrafts } from '../../lib/numberDrafts';
import type { Beschaffungsdetails, Dokumentart, Einheit, PositionSnapshot, Positionsart } from './types';

export interface PositionDraft {
  art: Positionsart;
  artikelId: number | null;
  interneReferenz: string;
  zeichnungsnummer: string;
  zeichnungsrevision: string;
  bezeichnung: string;
  werkstoff: string;
  abmessung: string;
  menge: string;
  einheit: Einheit;
  stueckzahl: string;
  einzelLaengeMm: string;
  kgJeMeter: string;
  faktorQuelle: string;
  schnittForm: string;
  winkelLinks: string;
  winkelRechts: string;
  bearbeitung: string;
  oberflaeche: string;
  dokumente: DokumentSoll[];
  anlageVersionIds: number[];
  /**
   * Nicht im Formular bearbeitet (Lieferant, Kategorie, Schnittbild …). Die Angaben bleiben an den Artikel und
   * Zuschnitt gebunden, zu dem sie erfasst wurden, damit sie nach einem Wechsel nicht an fremdem Material hängen.
   */
  beschaffungsdetails?: GebundeneBeschaffungsdetails | null;
}

/** Artikel und Zuschnitt, zu denen Beschaffungsdetails erfasst wurden. */
export interface BeschaffungsBezug { art: Positionsart; artikelId: number | null; schnittForm: string | null }
export interface GebundeneBeschaffungsdetails { werte: Beschaffungsdetails; erfasstFuer: BeschaffungsBezug }

/**
 * Liefert die Beschaffungsdetails nur, solange Positionsart und Katalogartikel unverändert sind. Schnittbild und
 * Schnittachse gelten zusätzlich nur für denselben Sonderzuschnitt; sonst werden sie verworfen.
 */
export function passendeBeschaffungsdetails(details: Beschaffungsdetails | null | undefined, erfasstFuer: BeschaffungsBezug, jetzt: BeschaffungsBezug): Beschaffungsdetails | null {
  if (!details || jetzt.art !== erfasstFuer.art || (jetzt.artikelId ?? null) !== (erfasstFuer.artikelId ?? null)) return null;
  if ((jetzt.schnittForm || '') === (erfasstFuer.schnittForm || '')) return details;
  return { ...details, schnittbildId: null, schnittAchseId: null };
}

export interface DokumentSoll {
  art: Dokumentart;
  grundlage: string | null;
  grundlageVersion: string | null;
  fachlichBestaetigt: boolean;
}

export type ValidationResult<T> = { valid: true; value: T } | { valid: false; message: string; field: string };

export function toPositionPayload(draft: PositionDraft, optionen: { anlageBeiErstanlage?: boolean } = {}): ValidationResult<PositionSnapshot> {
  if (draft.art === 'FREITEXT' && !draft.bezeichnung.trim()) {
    return { valid: false, message: 'Bitte geben Sie eine Bezeichnung für das Material ein.', field: 'bezeichnung' };
  }
  if (draft.art === 'ARTIKEL' && (!Number.isSafeInteger(draft.artikelId) || (draft.artikelId ?? 0) <= 0)) {
    return { valid: false, message: 'Bitte wählen Sie einen Artikel aus dem Katalog.', field: 'artikelId' };
  }
  if (draft.art === 'ZEICHNUNGSTEIL') {
    const pflicht: Array<[string, string, string]> = [
      ['bezeichnung', draft.bezeichnung, 'Bitte geben Sie eine Bezeichnung für das Zeichnungsteil ein.'],
      ['interneReferenz', draft.interneReferenz, 'Ein Zeichnungsteil braucht eine eindeutige Projektkennung.'],
      ['zeichnungsnummer', draft.zeichnungsnummer, 'Bitte geben Sie die Zeichnungsnummer ein.'],
      ['zeichnungsrevision', draft.zeichnungsrevision, 'Bitte geben Sie die Zeichnungsrevision ein.'],
    ];
    const fehlend = pflicht.find(([, value]) => !value.trim());
    if (fehlend) return { valid: false, field: fehlend[0], message: fehlend[2] };
    if ((!draft.anlageVersionIds.length && !optionen.anlageBeiErstanlage) || draft.anlageVersionIds.some(id => !Number.isSafeInteger(id) || id <= 0)) {
      return { valid: false, field: 'anlageVersionIds', message: 'Für ein Zeichnungsteil ist mindestens eine gültige Anlagenversion erforderlich.' };
    }
  }
  if (draft.dokumente.some(dokument => !dokument.art || !dokument.grundlage?.trim() || !dokument.grundlageVersion?.trim())) {
    return { valid: false, message: 'Bitte geben Sie für jeden geforderten Nachweis Grundlage und Grundlagenversion an.', field: 'dokumente' };
  }
  const numbers = validateNumberDrafts(
    { menge: draft.menge, stueckzahl: draft.stueckzahl, einzelLaengeMm: draft.einzelLaengeMm, kgJeMeter: draft.kgJeMeter, winkelLinks: draft.winkelLinks, winkelRechts: draft.winkelRechts },
    {
      menge: { label: 'Menge', required: true, min: 0.000001, maxDecimalPlaces: 6, integer: draft.einheit === 'STUECK' },
      stueckzahl: { label: 'Stückzahl', required: false, min: 1, integer: true },
      einzelLaengeMm: { label: 'Einzellänge', required: false, min: 0.000001, maxDecimalPlaces: 6 },
      kgJeMeter: { label: 'Gewicht je Meter', required: false, min: 0.000001, maxDecimalPlaces: 6 },
      winkelLinks: { label: 'Linker Winkel', required: false, min: 0, max: 360, maxDecimalPlaces: 2 },
      winkelRechts: { label: 'Rechter Winkel', required: false, min: 0, max: 360, maxDecimalPlaces: 2 },
    },
  );
  if (!numbers.valid) return { valid: false, message: numbers.message, field: numbers.field };
  if (draft.einheit === 'METER' && numbers.values.stueckzahl !== null && numbers.values.einzelLaengeMm !== null) {
    const meter = Math.round(numbers.values.stueckzahl * numbers.values.einzelLaengeMm * 1000) / 1000000;
    if (Math.abs(numbers.values.menge! - meter) > 0.0000001) {
      return { valid: false, field: 'menge', message: `Stückzahl und Einzellänge ergeben ${meter.toLocaleString('de-DE', { maximumFractionDigits: 6 })} m. Bitte passen Sie die Menge oder den Zuschnitt an.` };
    }
  }

  return {
    valid: true,
    value: {
      art: draft.art,
      artikelId: draft.artikelId,
      interneReferenz: draft.interneReferenz.trim() || null,
      zeichnungsnummer: draft.zeichnungsnummer.trim() || null,
      zeichnungsrevision: draft.zeichnungsrevision.trim() || null,
      bezeichnung: draft.bezeichnung.trim(),
      werkstoff: draft.werkstoff.trim() || null,
      abmessung: draft.abmessung.trim() || null,
      basis: {
        menge: numbers.values.menge!,
        einheit: draft.einheit,
        stueckzahl: draft.einheit === 'STUECK' ? numbers.values.menge! : numbers.values.stueckzahl,
        einzelLaengeMm: numbers.values.einzelLaengeMm,
        kgJeMeter: numbers.values.kgJeMeter,
        faktorQuelle: draft.faktorQuelle.trim() || null,
      },
      schnittForm: draft.schnittForm || null,
      winkelLinks: draft.winkelLinks.trim() || null,
      winkelRechts: draft.winkelRechts.trim() || null,
      bearbeitung: draft.bearbeitung.trim() || null,
      oberflaeche: draft.oberflaeche.trim() || null,
      dokumente: draft.dokumente,
      anlageVersionIds: draft.anlageVersionIds,
      beschaffungsdetails: draft.beschaffungsdetails ? passendeBeschaffungsdetails(draft.beschaffungsdetails.werte,
        draft.beschaffungsdetails.erfasstFuer, { art: draft.art, artikelId: draft.artikelId, schnittForm: draft.schnittForm }) : null,
    },
  };
}

/** Converts a persisted technical snapshot without inventing zero quantities. */
export function fromPositionSnapshot(position: PositionSnapshot): PositionDraft {
  const zahl = (value: number | null | undefined) => value == null ? '' : String(value).replace('.', ',');
  return { art: position.art, artikelId: position.artikelId, interneReferenz: position.interneReferenz ?? '',
    zeichnungsnummer: position.zeichnungsnummer ?? '', zeichnungsrevision: position.zeichnungsrevision ?? '',
    bezeichnung: position.bezeichnung ?? '', werkstoff: position.werkstoff ?? '', abmessung: position.abmessung ?? '',
    menge: zahl(position.basis?.menge), einheit: position.basis?.einheit ?? 'STUECK',
    stueckzahl: zahl(position.basis?.stueckzahl), einzelLaengeMm: zahl(position.basis?.einzelLaengeMm),
    kgJeMeter: zahl(position.basis?.kgJeMeter), faktorQuelle: position.basis?.faktorQuelle ?? '',
    schnittForm: position.schnittForm ?? '', winkelLinks: position.winkelLinks ?? '', winkelRechts: position.winkelRechts ?? '',
    bearbeitung: position.bearbeitung ?? '', oberflaeche: position.oberflaeche ?? '',
    dokumente: position.dokumente ?? [], anlageVersionIds: position.anlageVersionIds ?? [],
    beschaffungsdetails: position.beschaffungsdetails ? { werte: position.beschaffungsdetails,
      erfasstFuer: { art: position.art, artikelId: position.artikelId, schnittForm: position.schnittForm } } : null };
}
