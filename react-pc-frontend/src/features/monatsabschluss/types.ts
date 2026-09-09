export interface Referenz { mitarbeiterId: number; jahr: number; monat: number }
export interface Stand extends Referenz { version: number | null }
export interface Kennzahlen { istStunden: number; sollStunden: number; abwesenheitsStunden: number; feiertagsStunden: number; korrekturStunden: number; gesamtIst: number; differenz: number }
export interface Zeile { referenz: Referenz; mitarbeiterName: string; abteilungIds: number[]; festgeschrieben: boolean; version: number | null; festgeschriebenAm: string | null; kennzahlen: Kennzahlen }
export interface Filter { jahr: number; monat: number; mitarbeiterId?: number; abteilungId?: number; status: 'ALLE' | 'OFFEN' | 'ABGESCHLOSSEN'; page: number; size: number }
export interface Uebersicht { items: Zeile[]; totalElements: number; page: number; size: number; summen: Kennzahlen; auswahl: Stand[] }
export interface Vergleichsmonat { jahr: number; monat: number; summen: Kennzahlen; offen: number; abgeschlossen: number }
export interface SammelRequest { auswahl: Referenz[] }
export interface Einzelergebnis { referenz: Referenz; status: 'ABGESCHLOSSEN' | 'BEREITS_ABGESCHLOSSEN' | 'FEHLGESCHLAGEN'; meldung: string }
export interface SammelResponse { ergebnisse: Einzelergebnis[] }
export interface Verlauf extends Referenz, Kennzahlen { festgeschrieben: boolean; version: number | null; festgeschriebenAm: string | null; festgeschriebenVonMitarbeiterId: number | null; audit: { id: number; aktion: string; akteurMitarbeiterId: number; akteurName: string; zeitpunkt: string }[] }
export interface Zuordnung { kategorie: string; ausgeschlossen: boolean; lohnart: string | null }
export interface Personalnummer { mitarbeiterId: number; personalnummer: string }
export interface Konfiguration { version: number | null; ziel: string; beraterNr: string | null; mandantenNr: string | null; zuordnungen: Zuordnung[]; personalnummern: Personalnummer[] }
export interface ExportRequest { auswahl: Stand[]; konfigurationVersion: number | null }
export interface Hinweis { referenz: Referenz | null; kategorie: string; stunden: number | null; meldung: string }
export interface Vorpruefung { gueltig: boolean; fehler: Hinweis[]; ausschluesse: Hinweis[]; auswahl: Stand[]; konfigurationVersion: number | null }
