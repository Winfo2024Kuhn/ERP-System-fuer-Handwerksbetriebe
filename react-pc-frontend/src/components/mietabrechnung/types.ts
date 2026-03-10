export interface Mietobjekt {
    id: number;
    name: string;
    strasse: string;
    plz: string;
    ort: string;
}

export interface Partei {
    id: number;
    mietobjektId?: number;
    name: string;
    rolle: 'MIETER' | 'EIGENTUEMER';
    email: string;
    telefon: string;
    monatlicherVorschuss?: number | null;
}

export interface Raum {
    id: number;
    mietobjektId?: number;
    name: string;
    beschreibung: string;
    flaecheQuadratmeter: number;
    verbraucher?: Verbraucher[];
}

export interface Verbraucher {
    id: number;
    raumId: number;
    name: string;
    verbrauchsart: 'WASSER' | 'STROM' | 'HEIZUNG' | 'GAS' | 'SONSTIGES';
    einheit: string;
    seriennummer: string;
    aktiv: boolean;
    zaehlerstaende?: Zaehlerstand[];
}

export interface Zaehlerstand {
    id: number;
    verbrauchsgegenstandId?: number;
    abrechnungsJahr: number;
    stichtag: string; // YYYY-MM-DD
    stand: number;
    verbrauch?: number;
    kommentar: string;
}

export interface Verteilungsschluessel {
    id: number;
    mietobjektId?: number;
    name: string;
    beschreibung?: string;
    typ: 'PROZENTUAL' | 'VERBRAUCH' | 'FLAECHE';
    // parameter?: string; // Removed as it's not in DTO or view usage anymore
    eintraege?: any[];
}

export interface Kostenstelle {
    id: number;
    mietobjektId?: number;
    name: string;
    beschreibung?: string;
    umlagefaehig?: boolean;
    standardSchluesselId?: number;
    kostenpositionen?: Kostenposition[];
}

export interface Kostenposition {
    id: number;
    kostenstelleId: number;
    abrechnungsJahr?: number;
    buchungsdatum: string; // YYYY-MM-DD
    betrag: number;
    berechnung?: 'BETRAG' | 'VERBRAUCHSFAKTOR';
    verbrauchsfaktor?: number | null;
    berechneterBetrag?: number;
    verbrauchsmenge?: number | null;
    beschreibung: string;
    belegNummer?: string;
    verteilungsschluesselId?: number | null;
}

export interface Abrechnung {
    id?: number;
    mietobjektId: number;
    jahr: number;
    gesamtkosten: number;
    erstelltAm: string;
}

export interface AnnualAccountingConsumption {
    verbrauchsgegenstandId: number;
    name: string;
    raumName: string | null;
    verbrauchsart: 'WASSER' | 'STROM' | 'HEIZUNG' | 'GAS' | 'SONSTIGES';
    einheit: string;
    verbrauchJahr: number;
    verbrauchVorjahr: number;
    differenz: number;
}

export interface AbrechnungResult {
    mietobjektId: number;
    mietobjektName: string;
    mietobjektStrasse: string | null;
    mietobjektPlz: string | null;
    mietobjektOrt: string | null;
    jahr: number;
    gesamtkosten: number;
    gesamtkostenVorjahr: number;
    gesamtkostenDifferenz: number;
    verbrauchsvergleiche: AnnualAccountingConsumption[];
}
