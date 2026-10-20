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
    abrechnungsverlauf: Pick<AbrechnungsverlaufDto, 'positionen' | 'abschliessendeRechnungNummer'> | null | undefined,
): boolean {
    if (!abrechnungsverlauf) return false;
    // Am anderen Ende der Kette Angebot <-> AB kann schon abgeschlossen sein,
    // ohne dass hier eine eigene Position steht.
    if (abrechnungsverlauf.abschliessendeRechnungNummer) return false;
    return abrechnungsverlauf.positionen.every(p => p.storniert);
}

/**
 * Schliesst diese einfache Rechnung den Auftrag ab?
 *
 * Eine einfache Rechnung (Typ `RECHNUNG`) ohne andere aktive Rechnung am
 * Basisdokument rechnet alles auf einmal ab — was darin fehlt, ist nicht
 * angefallen und nicht "noch offen". Das PDF rechnet sie dann wie eine
 * Schlussrechnung: Balken 100 %, Differenzzeile "Nicht angefallen" bzw.
 * "Zusätzliche Leistungen", Rest 0,00 €.
 *
 * Offene Rechnungen entscheiden live. Gesperrte (gebucht, storniert, digital
 * angenommen) nur noch über das beim Speichern im positionenJson abgelegte
 * Kennzeichen: So druckt eine neue Rechnung auch nach dem Buchen weiter
 * "abgeschlossen", während Bestandsrechnungen das Feld nicht haben und beim
 * Nachdruck genau so aussehen wie das damals verschickte PDF.
 *
 * @param andereAktiveRechnungen Anzahl der nicht stornierten Rechnungen am
 *        Basisdokument ohne die aktuelle (nur bei offenen Rechnungen relevant)
 * @param gespeichert Kennzeichen `schliesstAuftragAb` aus dem positionenJson
 */
export function schliesstEinfacheRechnungAuftragAb(
    dokumentTyp: AusgangsGeschaeftsDokumentTyp,
    andereAktiveRechnungen: number,
    isLocked: boolean,
    gespeichert: boolean = false,
): boolean {
    if (dokumentTyp !== 'RECHNUNG') return false;
    // Gesperrt zaehlt allein das Gespeicherte: Entsteht spaeter doch noch eine
    // Rechnung am Angebot, darf der Nachdruck nicht kippen.
    return isLocked ? gespeichert : andereAktiveRechnungen === 0;
}

/**
 * Hinweis, warum am Vorgang keine Rechnung mehr angelegt werden kann – oder
 * null, wenn es noch geht.
 *
 * Eine einfache Rechnung oder Schlussrechnung schliesst den Auftrag ab: Danach
 * ist erst wieder eine Rechnung moeglich, wenn sie storniert oder ihr Entwurf
 * geloescht wurde. Eine Auftragsbestaetigung bleibt erlaubt. Ohne abschliessende
 * Rechnung gilt der bisherige Betragstext bei aufgebrauchtem Restbetrag.
 * Spiegelt die Pruefung beim Anlegen im `AusgangsGeschaeftsDokumentService`.
 */
export function keineWeitereRechnungHinweis(
    abrechnungsverlauf: Pick<AbrechnungsverlaufDto, 'restbetrag' | 'abschliessendeRechnungNummer'> | null | undefined,
): string | null {
    if (!abrechnungsverlauf) return null;
    const nummer = abrechnungsverlauf.abschliessendeRechnungNummer;
    if (nummer) {
        return `Dieser Auftrag ist mit Rechnung ${nummer} abgeschlossen. Eine weitere Rechnung geht erst, `
            + 'wenn diese storniert oder ihr Entwurf gelöscht wird. Eine Auftragsbestätigung können Sie weiterhin anlegen.';
    }
    if (abrechnungsverlauf.restbetrag <= 0) {
        return 'Der Basisbetrag ist vollständig abgerechnet. Es kann keine weitere Rechnung erstellt werden.';
    }
    return null;
}
