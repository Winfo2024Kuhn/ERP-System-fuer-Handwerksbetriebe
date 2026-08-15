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
export const formatCurrency = (val?: number) =>
    new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(val || 0);
