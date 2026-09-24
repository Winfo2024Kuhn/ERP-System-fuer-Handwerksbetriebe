/**
 * Umsatzsteuer-Rechnen für die Belegerfassung.
 *
 * Ausgelagert aus dem Prüf-Dialog (BelegeKasseEditor), wo die Rechnung inline
 * in einem State-Updater steckte und deshalb nicht testbar war. Bei Geldbeträgen
 * ist das genau die Stelle, an der sich ein Rundungsfehler am teuersten rächt:
 * Er landet unbemerkt im Kassenbuch und ist nach der Festschreibung nur noch
 * per Storno korrigierbar.
 *
 * Gerechnet wird durchgehend in Cent-genauen Werten mit kaufmännischer Rundung
 * (0,5 wird aufgerundet) — so, wie es auf jeder Quittung steht.
 */

/**
 * Auf zwei Nachkommastellen kaufmännisch runden — halbe Cent immer vom
 * Nullpunkt weg, wie es auf jeder Quittung steht.
 *
 * Der Umweg über die Exponentialschreibweise (`8.165` → `"8.165e2"`) ist kein
 * Selbstzweck: `Math.round(8.165 * 100)` liefert 816 statt 817, weil 8,165 als
 * Fließkommazahl minimal kleiner gespeichert ist als der Dezimalwert. Über den
 * String verschiebt die Sprache das Komma dagegen dezimal und trifft die
 * Rundung so, wie ein Mensch sie erwartet.
 */
export function aufCent(betrag: number): number {
    if (!Number.isFinite(betrag)) return NaN;

    const vorzeichen = betrag < 0 ? -1 : 1;
    const absolut = Math.abs(betrag);

    // Bei sehr großen oder sehr kleinen Zahlen schreibt JavaScript den Wert
    // selbst schon in Exponentialschreibweise ("1e-7"); dann funktioniert der
    // Trick nicht und wir nehmen den geraden Weg. Für Geldbeträge kommt das
    // in der Praxis nicht vor.
    const alsText = String(absolut);
    if (alsText.includes('e') || alsText.includes('E')) {
        const ergebnis = (vorzeichen * Math.round(absolut * 100)) / 100;
        return ergebnis === 0 ? 0 : ergebnis;
    }

    const verschoben = Number(`${alsText}e2`);
    if (!Number.isFinite(verschoben)) return NaN;
    const ergebnis = vorzeichen * Number(`${Math.round(verschoben)}e-2`);
    // -0 wäre technisch korrekt, sieht in der Oberfläche aber falsch aus.
    return ergebnis === 0 ? 0 : ergebnis;
}

/**
 * Rechnet aus einem Bruttobetrag den Nettobetrag zum angegebenen Steuersatz
 * heraus.
 *
 * @param brutto      Bruttobetrag in Euro
 * @param satzProzent Steuersatz in Prozent (19, 7 oder 0)
 * @returns Nettobetrag auf Cent gerundet, oder `null` wenn die Eingaben keine
 *          sinnvolle Rechnung zulassen (leeres Feld, Text, negativer Betrag).
 */
export function nettoAusBrutto(brutto: number, satzProzent: number): number | null {
    if (!istRechenbar(brutto, satzProzent)) return null;
    return aufCent(brutto / (1 + satzProzent / 100));
}

/**
 * Steueranteil eines Bruttobetrags: brutto minus netto.
 *
 * Bewusst als Differenz und nicht über `brutto * satz / (100 + satz)`
 * berechnet — so passt der Steuerbetrag immer exakt zu dem Netto, das
 * {@link nettoAusBrutto} liefert, und die drei Zahlen auf dem Beleg gehen
 * garantiert auf.
 */
export function mwstAusBrutto(brutto: number, satzProzent: number): number | null {
    const netto = nettoAusBrutto(brutto, satzProzent);
    if (netto === null) return null;
    return aufCent(brutto - netto);
}

/** Gegenrichtung: aus einem Nettobetrag den Bruttobetrag errechnen. */
export function bruttoAusNetto(netto: number, satzProzent: number): number | null {
    if (!istRechenbar(netto, satzProzent)) return null;
    return aufCent(netto * (1 + satzProzent / 100));
}

/**
 * Aufschlüsselung eines Bruttobetrags in seine drei Bestandteile — das, was
 * der Prüf-Dialog unter dem Betragsfeld anzeigt.
 *
 * Liefert `null`, solange die Eingaben unvollständig sind; dann zeigt die
 * Oberfläche schlicht nichts an, statt "NaN €" auszugeben.
 */
export interface MwstAufschluesselung {
    brutto: number;
    netto: number;
    mwst: number;
    satzProzent: number;
}

export function schluesseleAuf(brutto: number, satzProzent: number): MwstAufschluesselung | null {
    const netto = nettoAusBrutto(brutto, satzProzent);
    if (netto === null) return null;
    return {
        brutto: aufCent(brutto),
        netto,
        mwst: aufCent(brutto - netto),
        satzProzent,
    };
}

/**
 * Wandelt eine Formulareingabe in eine Zahl. Akzeptiert das deutsche Komma,
 * weil niemand im Betrieb "19.99" tippt.
 *
 * @returns die Zahl, oder `null` bei leerem Feld oder unlesbarer Eingabe
 */
export function zuZahl(eingabe: string | number | null | undefined): number | null {
    if (eingabe === null || eingabe === undefined || eingabe === '') return null;
    if (typeof eingabe === 'number') return Number.isFinite(eingabe) ? eingabe : null;
    const normalisiert = eingabe.trim().replace(/\s/g, '').replace(',', '.');
    if (normalisiert === '') return null;
    const zahl = Number(normalisiert);
    return Number.isFinite(zahl) ? zahl : null;
}

/**
 * Prüft, ob sich aus Betrag und Satz überhaupt etwas rechnen lässt.
 *
 * Ein Betrag von 0 ist bewusst nicht rechenbar: Er kommt praktisch nur
 * vor, während der Nutzer noch tippt, und würde sonst ein Netto von 0,00 €
 * ins Feld schreiben, das anschließend wie ein bestätigter Wert aussieht.
 */
function istRechenbar(betrag: number, satzProzent: number): boolean {
    return Number.isFinite(betrag) && betrag > 0
        && Number.isFinite(satzProzent) && satzProzent >= 0;
}
