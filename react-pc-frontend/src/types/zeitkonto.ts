export interface Arbeitszeit {
    montagStunden: number; dienstagStunden: number; mittwochStunden: number;
    donnerstagStunden: number; freitagStunden: number; samstagStunden: number; sonntagStunden: number;
    buchungStartZeit: string | null; buchungEndeZeit: string | null;
}
export interface Zeitkontenmodell {
    id: number; version: number; bezeichnung: string; arbeitszeit: Arbeitszeit;
}
export interface ZeitkontoVersion {
    id: number; version: number; mitarbeiterId: number;
    gueltigVon: string; gueltigBis: string | null; vorlageId: number | null; arbeitszeit: Arbeitszeit;
}
export interface ZeitkontoStatus {
    mitarbeiterId: number; mitarbeiterVersion: number; mitarbeiterName: string;
    fuehrtZeitkonto: boolean; eingerichtet: boolean; hinweis: string | null;
    aktuell: ZeitkontoVersion | null; letzteVersion: ZeitkontoVersion | null; historie: ZeitkontoVersion[];
}
export interface ZeitkontoWechsel {
    gueltigVon: string; expectedMitarbeiterVersion: number;
    expectedLetzteVersionId: number | null; expectedLetzteVersion: number | null;
    vorlageId: number | null; expectedVorlageVersion: number | null; arbeitszeit: Arbeitszeit | null;
}
export interface ZeitkontoWechselErgebnis {
    zeitkonto: ZeitkontoStatus; gueltigVon: string; gespeichert: boolean;
    bestehendeAbwesenheiten: number; hinweis: string;
    monate: { jahr: number; monat: number; abgeschlossen: boolean;
        saldoVorher: number; saldoNachher: number | null; geaendert: boolean }[];
}
