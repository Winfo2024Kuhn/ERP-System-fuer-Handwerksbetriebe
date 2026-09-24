import type { BestellungDetail, BestellRevision } from './types';

/** Erweiterte Bestell-DTOs aus EinkaufBestellungDto inklusive Versandnachweisen. */
export interface BestellungRevision extends BestellRevision {
  angenommenAm: string | null;
  externerNachweis: Record<string, unknown> | null;
}

export interface BestellungMitNachweisen extends Omit<BestellungDetail, 'revisionen'> {
  revisionen: BestellungRevision[];
}

export interface Bestellmenge {
  bedarfId: number;
  reserviert: number;
  bestellt: number;
  geliefert: number;
  storniert: number;
  offen: number;
}

export interface Lieferung {
  id: number;
  bestellungId: number;
  revisionId: number;
  lieferscheinId: number | null;
  eingang: string;
  positionen: Array<{
    id: number;
    bestellPositionId: number;
    menge: number;
    charge: string | null;
    schmelznummer: string | null;
    projektAnteile: Array<{ bedarfId: number; version: number; menge: number }>;
    chargen: Array<{ id: number; kennung: string | null; schmelznummer: string | null }>;
  }>;
}

export interface BestellVorschau {
  version: number; vorschauHash: string; subject: string; htmlBody: string; empfaenger: string;
  pdfDateiId: number | null; anlageVersionIds: number[]; revisionsNummer: number | null;
  eigeneKundennummer: string | null; antwortfrist: string | null; liefertermin: string | null;
  anlagen: Array<{ id: number; dateiId: number; bedarfId: number; revision: string; dateiname: string; mimeTyp: string; byteAnzahl: number; sha256: string; freigegeben: boolean; versendet: boolean; hochgeladenAm: string }>
}
