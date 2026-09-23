import { validateNumberDrafts } from '../../lib/numberDrafts';
import type { Dokumentart, Einheit, PositionSnapshot } from './types';

export interface PositionDraft {
  art: 'ARTIKEL' | 'ZEICHNUNGSTEIL';
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
}

export interface DokumentSoll {
  art: Dokumentart;
  grundlage: string | null;
  grundlageVersion: string | null;
  fachlichBestaetigt: boolean;
}

export type ValidationResult<T> = { valid: true; value: T } | { valid: false; message: string; field: string };

export function toPositionPayload(draft: PositionDraft): ValidationResult<PositionSnapshot> {
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
    if (!draft.anlageVersionIds.length || draft.anlageVersionIds.some(id => !Number.isSafeInteger(id) || id <= 0)) {
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
  if (!numbers.valid) return { valid: false, message: numbers.message, field: 'menge' };

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
    },
  };
}
