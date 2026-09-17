/**
 * Datenmodell der Vor-Kalkulation einer Leistung.
 *
 * Alle Zahlen liegen als **Entwurfs-String** im deutschen Format (Dezimalkomma)
 * vor, nicht als `number`. Das ist Absicht und folgt der Vorgabe aus
 * FRONTEND_UI.md: Ein halb getippter Wert ("12," oder "") muss stehen bleiben
 * duerfen, statt von `Number(x) || 0` zu einer stillen Null zu werden. Erst
 * kurz vor einer echten Aktion (Preis uebernehmen) wird alles geprueft und in
 * Zahlen gewandelt — siehe `berechnung.ts`.
 */

/**
 * Bezugsgroesse einer Materialzeile. Bewusst dieselben vier Werte wie
 * `VERRECHNUNGSEINHEIT` in `src/types.ts`, damit eine aus dem Materialstamm
 * uebernommene Zeile ohne Umrechnungstabelle hierher passt.
 */
export type Mengeneinheit = 'STUECK' | 'METER' | 'KILOGRAMM' | 'QUADRATMETER';

export const MENGENEINHEITEN: { wert: Mengeneinheit; kurz: string; lang: string }[] = [
    { wert: 'STUECK', kurz: 'Stk', lang: 'Stück' },
    { wert: 'METER', kurz: 'm', lang: 'Meter' },
    { wert: 'KILOGRAMM', kurz: 'kg', lang: 'Kilogramm' },
    { wert: 'QUADRATMETER', kurz: 'm²', lang: 'Quadratmeter' },
];

/**
 * Worauf sich der eingetragene Preis bezieht.
 *
 * Stahlbau rechnet beides: Rohre kauft man je Meter, Profile oft je Kilogramm.
 * Die Excel-Mappe hat dafuer zwei getrennte Spalten ("Preis pro mtr." und
 * "Preis pro Kg"), und der Backend-Preisrechner
 * (`ArtikelPositionsPreisService.umrechnungsfaktor`) kennt denselben Fall.
 * Deshalb ein Umschalter statt zweier Felder, von denen immer eins leer bleibt.
 */
export type Preisbezug = 'EINHEIT' | 'KILOGRAMM';

/** Feuerverzinken kostet je nach Warenart unterschiedlich viel pro Kilogramm. */
export type Verzinkungsart = 'SCHLOSSERWARE' | 'TRAEGERWARE';

export interface MaterialPosition {
    id: string;
    /** Gesetzt, wenn die Zeile aus dem Materialstamm kommt. Handzeilen: `null`. */
    artikelId: number | null;
    /** Lieferanten-/Artikelnummer. Bei Handzeilen frei eintragbar. */
    artikelnummer: string;
    bezeichnung: string;
    menge: string;
    einheit: Mengeneinheit;
    preis: string;
    preisbezug: Preisbezug;
    /**
     * Kilogramm je Einheit. Aus dem Stamm `kgProMeter` bzw. `kgProQm`, bei
     * Handzeilen von Hand. Ohne diesen Wert laesst sich weder ein Kilo-Preis
     * noch die Verzinkung rechnen.
     */
    kgJeEinheit: string;
    /**
     * Zu beschichtende Oberflaeche je Einheit in m². Aus dem Stamm
     * `mantelflaeche`. Bei Blech steht dort 2 — beide Seiten.
     */
    qmJeEinheit: string;
    /** Vorschlag aus den Stammdaten (`verzinkungsgeeignet`). Nur Vorbelegung. */
    verzinkbar: boolean;
    /** Vorschlag aus den Stammdaten (`pulverbeschichtungsgeeignet`). */
    pulverbeschichtbar: boolean;
    /** Entscheidung fuer *diese* Kalkulation — unabhaengig von der Eignung. */
    verzinken: boolean;
    pulverbeschichten: boolean;
    verzinkungsart: Verzinkungsart;
    /** Liegt das Material im Lager oder muss es bestellt werden? */
    beschaffung: 'LAGER' | 'BESTELLEN';
}

/** Hand- und Maschinenstunden laufen in der Mappe in getrennten Zeilen. */
export type Arbeitsart = 'HAND' | 'MASCHINE';

export interface ArbeitszeitPosition {
    id: string;
    /** ID aus `/api/arbeitsgaenge`, oder `null` bei frei getipptem Arbeitsgang. */
    arbeitsgangId: number | null;
    arbeitsgang: string;
    beschreibung: string;
    stunden: string;
    stundensatz: string;
    art: Arbeitsart;
}

/** Freie Kostenzeile: Fremdleistung, Werkstoffzeugnis, Fracht, Sprit … */
export interface Zusatzkostenzeile {
    id: string;
    bezeichnung: string;
    betrag: string;
}

export interface VorkalkulationDaten {
    material: MaterialPosition[];
    /** Gemeinkostenzuschlag auf die Materialkosten, in Prozent. */
    gkzMaterialProzent: string;

    arbeitszeit: ArbeitszeitPosition[];
    gkzHandProzent: string;
    gkzMaschineProzent: string;

    zusatzkosten: Zusatzkostenzeile[];

    verzinkenSchlosserwareJeKg: string;
    verzinkenTraegerwareJeKg: string;
    verzinkenFracht: string;
    /** Feinverputzen der verzinkten Teile — schlaegt prozentual oben drauf. */
    feinverputzen: boolean;
    feinverputzenAufschlagProzent: string;
    pulverbeschichtenJeQm: string;

    /**
     * Zuschlaege der Schlusskette. Heute Handeingabe; spaeter sollen
     * Verwaltung/Vertrieb und Wagnis/Gewinn aus der Nachkalkulation
     * hergeleitet werden. Deshalb stehen sie hier als Daten und nicht als
     * Konstanten im Rechenkern — dann tauscht die spaetere Herleitung nur
     * die Quelle des Werts, nicht die Formel.
     */
    verwaltungVertriebProzent: string;
    wagnisGewinnProzent: string;
    skontoProzent: string;
}

/** Eine gespeicherte Kalkulation, wie sie der Reiter "Vor-Kalkulation" auflistet. */
export interface VorkalkulationEintrag {
    id: string;
    dokument: string;
    position: string;
    titel: string;
    herstellkosten: number;
    verkaufspreis: number;
    geaendertAm: string;
    /** Aus welcher Anfrage sie stammt — sie bleibt im Projekt bearbeitbar. */
    herkunft: 'ANFRAGE' | 'PROJEKT';
}
