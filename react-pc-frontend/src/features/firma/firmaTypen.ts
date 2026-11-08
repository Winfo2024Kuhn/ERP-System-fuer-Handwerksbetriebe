// Typen der Seite „Firma“ (pages/FirmaEditor.tsx).

export interface Firmeninformation {
    id: number;
    firmenname: string;
    strasse: string;
    plz: string;
    ort: string;
    telefon: string;
    fax: string;
    email: string;
    website: string;
    steuernummer: string;
    ustIdNr: string;
    handelsregister: string;
    handelsregisterNummer: string;
    bankName: string;
    iban: string;
    bic: string;
    logoDateiname: string;
    geschaeftsfuehrer: string;
    fusszeileText: string;
    googleBewertungsLink: string;
    /** Hausfarbe als Hex mit fuehrendem #, z.B. "#500010". Leer = Standardfarbe. */
    firmenfarbe: string;
    mahnverfahrenAktiv: boolean;
    tageBisZahlungserinnerung: number;
    tageBisErsteMahnung: number;
    tageBisZweiteMahnung: number;
    mahnverfahrenNeuesZahlungszielTage: number;
    /** Kürzel des Bundeslands (z. B. "BY"); bestimmt die gesetzlichen Feiertage. */
    bundesland?: string;
    gewerkId?: number | null;
    gewerkName?: string | null;
    bgName?: string | null;
    bgSatzVorschlag?: number | null;
    bgSatzOverride?: number | null;
    bgSatzEffektiv?: number | null;
}

/** Zahlenfelder, die im Formular als Text (Dezimalkomma) bearbeitet werden. */
export type FirmenZahlenFeld = 'tageBisZahlungserinnerung' | 'tageBisErsteMahnung' | 'tageBisZweiteMahnung' | 'mahnverfahrenNeuesZahlungszielTage' | 'bgSatzOverride';
export type FirmenEntwurf = Omit<Firmeninformation, FirmenZahlenFeld> & Record<FirmenZahlenFeld, string>;
/** Ändert einzelne Felder des Entwurfs. */
export type FirmaAendern = (teil: Partial<FirmenEntwurf>) => void;

export interface GewerkOption {
    id: number;
    name: string;
    bgName: string;
    bgSatzProzent: number;
    aktiv: boolean;
}

export interface SteuerberaterAnsprechpartner {
    id?: number;
    anrede: string | null;
    vorname: string;
    nachname: string;
    email: string;
    telefon: string;
    istLohnAnsprechpartner: boolean;
    notizen?: string;
}

export interface SteuerberaterKontakt {
    id: number;
    name: string;
    email: string;
    telefon: string;
    ansprechpartner: string;
    autoProcessEmails: boolean;
    aktiv: boolean;
    notizen: string;
    gueltigAb: string | null;
    gueltigBis: string | null;
    weitereEmails: string[];
    ansprechpartnerListe: SteuerberaterAnsprechpartner[];
}

/** Steuerberater im Dialog: neu (ohne id) oder in Bearbeitung. */
export type SteuerberaterEntwurf = Partial<SteuerberaterKontakt>;

export interface LohnabrechnungDto {
    id: number;
    mitarbeiterId: number;
    mitarbeiterName: string;
    steuerberaterId: number;
    steuerberaterName: string;
    jahr: number;
    monat: number;
    originalDateiname: string;
    downloadUrl: string;
    bruttolohn: number;
    nettolohn: number;
    importDatum: string;
    status: string;
}

export interface BwaUploadDto {
    id: number;
    typ: 'MONATLICH' | 'JAEHRLICH';
    jahr: number;
    monat: number | null;
    originalDateiname: string;
    pdfUrl: string;
    uploadDatum: string;
    aiConfidence: number | null;
    analysiert: boolean;
    freigegeben: boolean;
    gesamtGemeinkosten: number | null;
    steuerberaterName: string;
}

export type FirmaReiter = 'firma' | 'steuerberater' | 'steuerpruefung' | 'lohn-stammdaten' | 'unfallversicherung';
export type SteuerberaterUnterreiter = 'kontakte' | 'lohnabrechnungen' | 'bwa';
