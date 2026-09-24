/**
 * Waehrungsformatierung der Artikel-Trefferliste.
 *
 * Steht bewusst in einer eigenen Datei neben ArtikelSuche.tsx: Das
 * Auswahlfenster im Dokument-Editor zeigt dieselben Preise wie die
 * Artikelverwaltung und muss sie gleich schreiben. Ein Export aus
 * ArtikelSuche.tsx heraus ginge nicht - dort duldet ESLint
 * (react-refresh/only-export-components) neben der Komponente keinen
 * Funktions-Export.
 */

/** Uebliche Cent-Darstellung - alles ab einem halben Cent bleibt dabei. */
const NACHKOMMA_STANDARD = 2;

/**
 * Obergrenze fuer Kleinstpreise. Sechs Stellen entsprechen einem
 * Millionstel Euro - darunter ist der Einzelpreis auch fuer die
 * Kalkulation ohne Aussage.
 */
const NACHKOMMA_MAX = 6;

/**
 * Ab hier rundet die Cent-Darstellung auf 0,00 - und eine Schraube fuer
 * 0,0043 EUR saehe aus, als waere sie umsonst.
 */
const CENT_SCHWELLE = 0.005;

/**
 * Wie viele Nachkommastellen dieser Betrag braucht, um ueberhaupt etwas
 * auszusagen.
 *
 * Kleinteile (Schrauben, Muttern, Scheiben) kosten im Einkauf oft Bruchteile
 * eines Cents. Mit fest zwei Nachkommastellen steht in der Liste ueberall
 * "0,00 EUR" - der Handwerker kann dann weder Lieferanten vergleichen noch
 * erkennen, ob ueberhaupt ein Preis gepflegt ist. Deshalb wird die
 * Genauigkeit nur bei solchen Kleinstbetraegen so weit aufgedreht, bis die
 * erste von null verschiedene Ziffer sichtbar wird, plus eine Stelle zum
 * Vergleichen. Alle normalen Preise bleiben unveraendert bei Cent-Genauigkeit.
 */
export function nachkommastellenFuerPreis(wert: number): number {
    const betrag = Math.abs(wert);
    if (!Number.isFinite(betrag) || betrag === 0) return NACHKOMMA_STANDARD;
    if (betrag >= CENT_SCHWELLE) return NACHKOMMA_STANDARD;

    // 0,0043 -> log10 = -2,37 -> erste Ziffer steht an Stelle 3 -> 4 Stellen.
    const ersteZifferAnStelle = Math.floor(-Math.log10(betrag)) + 1;
    return Math.min(NACHKOMMA_MAX, ersteZifferAnStelle + 1);
}

/**
 * Preis in Euro, deutsch formatiert.
 *
 * Untergrenze bleiben die gewohnten zwei Nachkommastellen ("12,50 EUR"),
 * nach oben wird bei Kleinstbetraegen automatisch aufgedreht
 * (siehe {@link nachkommastellenFuerPreis}).
 */
export const formatCurrency = (val?: number) => {
    const betrag = val || 0;
    return new Intl.NumberFormat('de-DE', {
        style: 'currency',
        currency: 'EUR',
        minimumFractionDigits: NACHKOMMA_STANDARD,
        maximumFractionDigits: nachkommastellenFuerPreis(betrag),
    }).format(betrag);
};
