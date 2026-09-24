import type { Angebot, AngebotVersion } from './types';

export type AngebotVersionMitPositionsIds = Omit<AngebotVersion, 'positionen'> & {
  positionen: Array<AngebotVersion['positionen'][number] & { id?: number | null }>;
};

export interface AnfrageAngeboteEintrag {
  lieferantId: number;
  lieferantenname: string;
  angebot: Angebot;
}

export interface AngebotsVergleichDaten {
  anfrage: { angezeigteRevisionId: number; historisch: boolean; positionen: Array<{ id: number; snapshot: { interneReferenz: string | null; bezeichnung: string | null; werkstoff?: string | null; abmessung?: string | null; basis?: { einheit: string | null; menge: number | null } | null }; herkuenfte: Array<{ bedarfId: number; version: number; menge: number }> }> };
  anbieter: AnfrageAngeboteEintrag[];
  vergleich: Vergleich;
}

export interface VergleichsRechenschritt { key: string; formel: string; basis: number | null; ergebnis: number | null; quellenbezug: string | null }
export interface Angebotssumme {
  angebotVersionId: number; nettoGesamt: number | null; vollstaendig: boolean; technischGeeignet: boolean;
  gueltig: boolean; hindernisse: string[]; rechnung: VergleichsRechenschritt[];
}
export interface Vergleich { anfrageId: number; stichtag: string; angebote: Angebotssumme[]; bestesAngebotVersionId: number | null }
