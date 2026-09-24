import type { AbrechnungsverlaufDto, AusgangsGeschaeftsDokumentTyp } from '../types';

const RECHNUNGS_TYPEN: AusgangsGeschaeftsDokumentTyp[] = [
    'RECHNUNG', 'TEILRECHNUNG', 'ABSCHLAGSRECHNUNG', 'SCHLUSSRECHNUNG',
];

/** Nur die Felder, die für die Sperr-Regel gebraucht werden. */
type FolgeDokument = { typ: AusgangsGeschaeftsDokumentTyp; storniert?: boolean };

/**
 * Ein Angebot oder Nachtragsangebot trägt höchstens eine aktive
 * Auftragsbestätigung. Zwei ABs am selben Angebot hätten je einen eigenen
 * Abrechnungsverlauf über denselben Auftragswert — keiner der beiden
 * Restbeträge stimmte dann. Mehr Volumen gehört in ein Nachtragsangebot mit
 * eigener Kette.
 *
 * Spiegelt `validiereEinzigeAuftragsbestaetigung()` im
 * `AusgangsGeschaeftsDokumentService`.
 */
export function hatAktiveAuftragsbestaetigung(folgeDokumente: FolgeDokument[]): boolean {
    return folgeDokumente.some(d => d.typ === 'AUFTRAGSBESTAETIGUNG' && !d.storniert);
}

/**
 * Abgerechnet wird am untersten Dokument eines Vorgangs: Hängt unter einem
 * Angebot oder Nachtragsangebot bereits eine Auftragsbestätigung, entstehen die
 * Rechnungen dort. Sonst liefen zwei Abrechnungsstände nebeneinander — der
 * Abrechnungsverlauf zählt nur die *direkten* Nachfolger eines Basisdokuments,
 * der Restbetrag stimmte also in keinem von beiden.
 *
 * Ausnahme für Bestandsvorgänge: Wurde am Vorgänger schon abgerechnet und erst
 * danach eine AB angelegt, bleibt die Abrechnung dort. An der AB fehlten diese
 * Beträge im Verlauf, und die Schlussrechnung würde still zu hoch vorbefüllt.
 *
 * Spiegelt `validiereAbrechnungsbasis()` im `AusgangsGeschaeftsDokumentService` —
 * beide Seiten müssen dieselbe Antwort geben, sonst läuft der Nutzer in einen
 * Dialog, den der POST danach mit 400 abweist.
 *
 * @param folgeDokumente die direkten Nachfolger des Dokuments, an dem der
 *        Nutzer die Rechnung anlegen will
 */
export function istRechnungAmVorgaengerGesperrt(folgeDokumente: FolgeDokument[]): boolean {
    if (!hatAktiveAuftragsbestaetigung(folgeDokumente)) return false;

    const bereitsAbgerechnet = folgeDokumente.some(
        d => !d.storniert && RECHNUNGS_TYPEN.includes(d.typ)
    );
    return !bereitsAbgerechnet;
}

/**
 * "Einfache Rechnung" darf nur dann angeboten werden, wenn auf dem Basisdokument
 * noch keine *aktive* Folgerechnung existiert. Stornierte Folgerechnungen zählen
 * nicht, weil ihr Betrag im Restbetrag bereits wieder freigegeben wurde — sonst
 * gibt es nach einer Storno-Aktion keinen Weg zurück zur einfachen Rechnung
 * (Bug: nur Teilrechnung/Abschlagsrechnung wären sichtbar).
 */
export function canCreateEinfacheRechnung(
    abrechnungsverlauf: Pick<AbrechnungsverlaufDto, 'positionen'> | null | undefined,
): boolean {
    if (!abrechnungsverlauf) return false;
    return abrechnungsverlauf.positionen.every(p => p.storniert);
}
