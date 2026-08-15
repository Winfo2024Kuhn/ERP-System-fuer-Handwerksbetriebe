import type { Artikel } from "../../types";

/**
 * Klartext-Bezeichnung fuer die Liste. Der Produktname enthaelt oft nur das Mass
 * ("0,75 mm") - erst die Form davor sagt dem Anwender, was er vor sich hat:
 * "Blech 0,75 mm". Steht die Form schon im Namen, wird sie nicht doppelt gesetzt.
 *
 * Eigene Datei wie `formatCurrency` nebenan: Neben dem Komponenten-Export in
 * `ArtikelSuche.tsx` duldet ESLint (react-refresh/only-export-components) keinen
 * Funktions-Export. Die Auswahlfenster brauchen die Bezeichnung aber, um ihre
 * Bedienelemente so zu benennen, wie die Zeile daneben aussieht - ein
 * Screenreader darf nicht "100x100x4 auswaehlen" sagen, wo sichtbar
 * "Quadratrohr 100 x 100 x 4" steht.
 */
export const artikelBezeichnung = (artikel: Artikel) => {
    const form = artikel.profilform?.anzeigename;
    const mass = artikel.abmessung || artikel.produktname;
    if (!form) return artikel.produktname;
    if (mass && mass.toLowerCase().includes(form.toLowerCase())) return mass;
    return mass ? `${form} ${mass}` : form;
};
