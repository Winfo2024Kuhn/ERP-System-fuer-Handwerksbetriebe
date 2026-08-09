/**
 * Klartext zu den Preis-Hinweisen aus dem Backend. Bewusst in
 * Handwerker-Sprache: der Text steht sowohl in der Artikel-Detailseite als
 * auch im Auswahlfenster des DocumentEditors und muss an beiden Stellen
 * gleich klingen.
 */
export type PreisHinweis = 'OK' | 'KEIN_AUFSCHLAG' | 'KEIN_PREIS' | 'KEIN_GEWICHT';

export const preisHinweisText: Record<Exclude<PreisHinweis, 'OK'>, string> = {
    KEIN_AUFSCHLAG: 'Kein Aufschlag hinterlegt — der Preis entspricht dem Einkauf.',
    KEIN_PREIS: 'Kein Preis hinterlegt — bitte selbst eintragen.',
    KEIN_GEWICHT: 'Gewicht je Meter fehlt — bitte in den Artikel-Details pflegen.',
};

/** Kurzform für enge Stellen wie eine Trefferzeile. */
export const preisHinweisKurz: Record<Exclude<PreisHinweis, 'OK'>, string> = {
    KEIN_AUFSCHLAG: 'ohne Aufschlag',
    KEIN_PREIS: 'kein Preis',
    KEIN_GEWICHT: 'kein Gewicht',
};
