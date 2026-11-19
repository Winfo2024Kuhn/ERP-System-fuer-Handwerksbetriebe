import { formatCurrency } from '../helpers';

/**
 * Rechenweg des Abrechnungsstands im Block "Abschluss" (aus ClosureBlock
 * herausgelöst). Spiegelt RechnungPdfService: Editor und PDF müssen dieselben
 * Zahlen zeigen.
 */

/**
 * Steuer zu einem Nettobetrag, fest 19 % wie im PDF.
 *
 * In Cent rechnen, nicht mit 0,19 multiplizieren: 42,50 * 0,19 ergibt in
 * Gleitkomma 8,074999... und rundet auf 8,07, während das PDF mit BigDecimal
 * HALF_UP auf 8,08 kommt. Math.round rundet negative Halbe zur Null hin,
 * BigDecimal HALF_UP von ihr weg, deshalb über den Betrag runden und das
 * Vorzeichen wieder dranhängen.
 */
export function steuerBetrag(netto: number): number {
    const steuerCent = Math.round(netto * 100) * 19 / 100;
    return (Math.sign(steuerCent) * Math.round(Math.abs(steuerCent))) / 100;
}

export function mitSteuer(netto: number): number {
    return netto + steuerBetrag(netto);
}

export interface AbrechnungsstandEingabe {
    dokumentTyp?: string;
    /** Positionssumme ohne Pauschalrabatt (summary.gesamtNetto). */
    positionenNetto: number;
    /** Positionssumme nach Pauschalrabatt. */
    gesamtNettoEffektiv: number;
    abschlagBetragNetto?: number | null;
    bereitsAbgerechnetDurchAndere?: number | null;
    basisdokumentBetragNetto?: number | null;
    /** Einfache Rechnung, die den Auftrag abschliesst (schliesstEinfacheRechnungAuftragAb). */
    schliesstAuftragAb?: boolean;
}

export interface Abrechnungsstand {
    anzeigen: boolean;
    istAbschlag: boolean;
    auftragNetto: number;
    auftragBrutto: number;
    bereitsAbgerechnet: number;
    dieseLabel: string;
    dieseNetto: number;
    dieseSteuer: number;
    dieseBrutto: number;
    zeigeDifferenz: boolean;
    differenzNetto: number;
    differenzEntfallen: boolean;
    differenzBrutto: number;
    restNetto: number;
    restBrutto: number;
    /** Zeile „Noch offen“ zeigen (Regel aus RechnungAbrechnungsstandPdf#nochOffen). */
    zeigeRest: boolean;
    prozent: number;
}

/** Automatischer Text wie im PDF (RechnungAbrechnungsstandPdf#dieseRechnung), klein geschrieben. */
function dieseLabelFuer(istAbschlag: boolean, dokumentTyp?: string): string {
    if (istAbschlag) return 'diese Abschlagsrechnung';
    if (dokumentTyp === 'SCHLUSSRECHNUNG') return 'diese Schlussrechnung';
    return 'diese Rechnung';
}

/** Zeigt der Abschluss den Abrechnungsstand? Gleiche Regel steuert, ob Anpassungen ans PDF gehen. */
export function abrechnungsstandSichtbar(e: Pick<AbrechnungsstandEingabe,
    'dokumentTyp' | 'positionenNetto' | 'abschlagBetragNetto' | 'basisdokumentBetragNetto' | 'schliesstAuftragAb'>): boolean {
    const istAbschlag = e.dokumentTyp === 'ABSCHLAGSRECHNUNG' && e.abschlagBetragNetto != null;
    return (istAbschlag || e.dokumentTyp === 'TEILRECHNUNG' || istAbschliessend(e)) && e.positionenNetto > 0;
}

function istAbschliessend(e: Pick<AbrechnungsstandEingabe, 'dokumentTyp' | 'basisdokumentBetragNetto' | 'schliesstAuftragAb'>): boolean {
    // Die einfache Rechnung zeigt den Stand nur wie das PDF: wenn sie den Auftrag
    // abschliesst und eine Auftragssumme bekannt ist.
    return e.dokumentTyp === 'SCHLUSSRECHNUNG'
        || (e.dokumentTyp === 'RECHNUNG' && e.schliesstAuftragAb === true && (e.basisdokumentBetragNetto ?? 0) > 0);
}

export interface AbschlagInfo {
    modus: string;
    eingabeWert: number | null;
}

/** Deutsche Zahl mit 0 bis `max` Nachkommastellen. */
function zahl(wert: number, max: number): string {
    return wert.toLocaleString('de-DE', { minimumFractionDigits: 0, maximumFractionDigits: max });
}

/**
 * Unterzeile der Abschlagsrechnung wie im PDF (RechnungAbrechnungsstandPdf#abschlagUnterzeile):
 * eingegebener Prozentsatz, eingegebener Bruttobetrag oder der gerechnete Anteil.
 */
export function abschlagUnterzeile(
    stand: Pick<Abrechnungsstand, 'istAbschlag' | 'dieseNetto' | 'auftragNetto'>,
    info: AbschlagInfo | null | undefined,
): string | null {
    if (!stand.istAbschlag) return null;
    if (info && info.eingabeWert != null) {
        if (info.modus === 'prozent') return `ca. ${String(info.eingabeWert).replace('.', ',')} % der Auftragssumme`;
        if (info.modus === 'brutto') return `Eingabe: ${formatCurrency(info.eingabeWert)} € brutto`;
    }
    if (stand.auftragNetto <= 0) return null;
    const anteil = Math.round((stand.dieseNetto * 100 / stand.auftragNetto) * 10) / 10;
    return `ca. ${zahl(anteil, 1)} % der Auftragssumme`;
}

export function berechneAbrechnungsstand(e: AbrechnungsstandEingabe): Abrechnungsstand {
    const istAbschlag = e.dokumentTyp === 'ABSCHLAGSRECHNUNG' && e.abschlagBetragNetto != null;
    const istSchluss = e.dokumentTyp === 'SCHLUSSRECHNUNG';
    const abschliessend = istAbschliessend(e);
    const anzeigen = abrechnungsstandSichtbar(e);

    const auftragNetto = e.basisdokumentBetragNetto ?? e.gesamtNettoEffektiv;
    const bereitsAbgerechnet = e.bereitsAbgerechnetDurchAndere ?? 0;

    // Abschlag: der eingegebene Betrag ist schon der Endbetrag (kein Rabatt).
    // Schlussrechnung: nur, was in ihr steht, abzueglich der gestellten Rechnungen,
    // nicht der rechnerische Rest. Nicht auf 0 begrenzen (ueberzahlter Auftrag).
    const dieseNetto = istAbschlag
        ? (e.abschlagBetragNetto ?? 0)
        : istSchluss
            ? e.gesamtNettoEffektiv - bereitsAbgerechnet
            : e.gesamtNettoEffektiv;
    const dieseSteuer = steuerBetrag(dieseNetto);

    // Differenz als Ausgleichsgroesse, damit die Spalte immer aufgeht:
    // > 0 Leistungen entfallen, < 0 Zusatzleistungen. Nur mit Auftragssumme.
    const differenzNetto = abschliessend && auftragNetto > 0
        ? auftragNetto - bereitsAbgerechnet - dieseNetto
        : 0;

    // Brutto aus dem Nettorest, sonst bleibt bei krummen Betraegen "0,01 €" offen.
    const restNetto = auftragNetto - bereitsAbgerechnet - dieseNetto - differenzNetto;

    // Wer den Auftrag abschliesst, hat ihn per Definition zu 100 % abgerechnet.
    const prozent = abschliessend
        ? 100
        : auftragNetto > 0
            ? Math.min(100, Math.max(0, ((bereitsAbgerechnet + dieseNetto) / auftragNetto) * 100))
            : 0;

    return {
        anzeigen,
        istAbschlag,
        auftragNetto,
        auftragBrutto: mitSteuer(auftragNetto),
        bereitsAbgerechnet,
        dieseLabel: dieseLabelFuer(istAbschlag, e.dokumentTyp),
        dieseNetto,
        dieseSteuer,
        dieseBrutto: dieseNetto + dieseSteuer,
        zeigeDifferenz: Math.abs(differenzNetto) >= 0.01,
        differenzNetto,
        differenzEntfallen: differenzNetto > 0,
        differenzBrutto: mitSteuer(Math.abs(differenzNetto)),
        restNetto,
        restBrutto: mitSteuer(restNetto),
        // Wie im PDF: rechnet die Rechnung alles ohne Vorrechnung ab, ist der Rest
        // per Definition 0 und die Zeile entfaellt.
        zeigeRest: istAbschlag || istSchluss || bereitsAbgerechnet > 0 || Math.abs(restNetto) >= 0.01,
        prozent,
    };
}
