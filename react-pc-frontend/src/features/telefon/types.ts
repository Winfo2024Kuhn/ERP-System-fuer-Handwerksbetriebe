/**
 * Datentypen der Telefon-Anbindung (FRITZ!Box): Anrufliste,
 * Anrufbeantworter und Live-Anruf-Fenster. Spiegeln die DTOs aus
 * {@code dto/Telefon/*} im Backend.
 */

/** Kontakte mit eigener Akte (Kundenakte, Lieferantenakte). */
export type AktenTyp = 'KUNDE' | 'LIEFERANT';

/** Wer anrufen kann: Kunde, Lieferant oder Steuerberater (Steuerberater ohne eigene Akte). */
export type KontaktTyp = AktenTyp | 'STEUERBERATER';

export interface KontaktKurz {
    typ: KontaktTyp;
    id: number;
    name: string;
    /** Kundennummer (nur bei Kunden). */
    nummer: string | null;
    ort: string | null;
    /** Nur bei Steuerberatern: die Person, bei der genau diese Nummer hinterlegt ist. */
    ansprechpartner?: string | null;
}

/** Ansprechpartner einer Kanzlei zur Auswahl beim Zuordnen. */
export interface AuswahlAnsprechpartner {
    id: number;
    name: string;
    /** Schon hinterlegte Nummer – die wird nie überschrieben. */
    telefon: string | null;
}

/** Kanzlei zur Auswahl beim Zuordnen, mit ihren Ansprechpartnern. */
export interface SteuerberaterAuswahl {
    id: number;
    name: string;
    ansprechpartner: AuswahlAnsprechpartner[];
}

/** Projekt eines Anrufers im Anruf-Fenster. */
export interface UeberblickProjekt {
    id: number;
    bauvorhaben: string | null;
    auftragsnummer: string | null;
    ort: string | null;
    abgeschlossen: boolean;
}

/** Anfrage eines Anrufers im Anruf-Fenster. */
export interface UeberblickAnfrage {
    id: number;
    bauvorhaben: string | null;
    /** Nummer des Angebots, falls zur Anfrage schon eins geschrieben ist. */
    angebotsnummer: string | null;
    ort: string | null;
    abgeschlossen: boolean;
}

/**
 * Alles, was das Anruf-Fenster über den Anrufer zeigt. Projekte und
 * Anfragen: offene zuerst, dann die neuesten; bei Lieferanten leer.
 */
export interface KontaktUeberblick {
    typ: KontaktTyp;
    id: number;
    name: string;
    /** Kundennummer (nur bei Kunden). */
    nummer: string | null;
    /** Beim Kunden der Ansprechpartner, beim Lieferanten der Vertreter. */
    ansprechpartner: string | null;
    strasse: string | null;
    plz: string | null;
    ort: string | null;
    /** Höchstens 50 – die Gesamtzahl steht in {@link projekteGesamt}. */
    projekte: UeberblickProjekt[];
    projekteGesamt: number;
    /** Höchstens 50 – die Gesamtzahl steht in {@link anfragenGesamt}. */
    anfragen: UeberblickAnfrage[];
    anfragenGesamt: number;
}

export type Zuordnung = 'AUTOMATISCH' | 'MANUELL' | 'KEINE';

export type AnrufArt = 'ANGENOMMEN' | 'ANRUFBEANTWORTER' | 'VERPASST' | 'AUSGEHEND' | 'ABGEWIESEN';

export interface TelefonAnruf {
    id: number;
    /** ISO-Zeitpunkt in Ortszeit, z. B. "2026-09-29T11:55:00". */
    zeitpunkt: string;
    art: AnrufArt;
    /** Index des Anrufbeantworters, wenn art = ANRUFBEANTWORTER. */
    anrufbeantworter: number | null;
    /** Leer = Nummer unterdrückt. */
    nummer: string;
    eigeneNummer: string;
    dauerMinuten: number;
    nameFritzbox: string | null;
    zuordnung: Zuordnung;
    kontakt: KontaktKurz | null;
    kandidaten: KontaktKurz[];
    sprachnachrichtId: number | null;
}

export interface Sprachnachricht {
    id: number;
    anrufbeantworter: number;
    zeitpunkt: string;
    nummer: string;
    dauerSekunden: number;
    neu: boolean;
    abgehoertAm: string | null;
    abgehoertVon: string | null;
    zuordnung: Zuordnung;
    kontakt: KontaktKurz | null;
    kandidaten: KontaktKurz[];
    nameFritzbox: string | null;
}

/** Gemeinsame Felder, die die „Wer"-Anzeige und das Zuordnen brauchen. */
export type Zuordenbar = Pick<TelefonAnruf, 'nummer' | 'nameFritzbox' | 'kontakt' | 'kandidaten' | 'zuordnung'>;

export interface Anrufbeantworter {
    index: number;
    name: string;
}

export interface TelefonStatus {
    eingerichtet: boolean;
    anrufbeantworter: Anrufbeantworter[];
    letzteAbholung: string | null;
    letzterFehler: string | null;
    neueSprachnachrichten: number;
}

export interface AbholErgebnis {
    erfolgreich: boolean;
    meldung: string;
    neueAnrufe: number;
    neueSprachnachrichten: number;
    nachtraeglichZugeordnet: number;
}

export interface KontaktRufnummer {
    id: number;
    nummer: string;
}

export type LiveStatus = 'KLINGELT' | 'IM_GESPRAECH' | 'ANRUFBEANTWORTER' | 'BEENDET';

export interface LiveAnruf {
    verbindungsId: string;
    status: LiveStatus;
    nummer: string;
    kontakt: KontaktKurz | null;
    kandidaten: KontaktKurz[];
    angenommen: boolean;
}

export interface TelefonEinstellungen {
    aktiv: boolean;
    host: string | null;
    benutzer: string | null;
    passwortGesetzt: boolean;
    verschluesselungEingerichtet: boolean;
    geschaeftsnummern: string[];
    anrufbeantworter: Anrufbeantworter[];
    aufbewahrungAnrufeMonate: number;
    aufbewahrungSprachnachrichtenMonate: number;
    landesvorwahl: string | null;
    ortsvorwahl: string | null;
    letzteAbholung: string | null;
    letzterFehler: string | null;
    anrufmonitorVerbunden: boolean;
}

export interface TelefonVerbindungstest {
    erfolgreich: boolean;
    meldung: string;
    eigeneNummern: string[];
    anrufbeantworter: Anrufbeantworter[];
    landesvorwahl: string | null;
    ortsvorwahl: string | null;
}

/** Seite einer Spring-Page, auf das Nötige reduziert. */
export interface Seite<T> {
    inhalt: T[];
    gesamt: number;
    seiten: number;
}

/** Wohin ein Zuordnen-Aufruf geht. */
export type ZuordnenZiel = { art: 'anruf'; id: number } | { art: 'sprachnachricht'; id: number };
