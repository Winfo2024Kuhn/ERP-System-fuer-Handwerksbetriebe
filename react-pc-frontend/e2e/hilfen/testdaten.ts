/**
 * Testdaten-Bausteine, die mehrere Layout-Specs gleich brauchen.
 *
 * Nachtrag Abschnitt 10: lag bisher identisch dreimal in projekt-detail-layout.spec.ts,
 * anfrage-layout.spec.ts und lieferant-layout.spec.ts.
 */

/**
 * Baut ein langes, garantiert trennstellenloses Wort (kein Leerzeichen, kein
 * Bindestrich, kein Punkt) -- genau das, was eine Umbruch-Zusicherung
 * braucht (siehe kriterien.md: "Testdaten für Umbruch-Fehler brauchen ein
 * langes Wort ohne Trennstellen"). Bindestriche und Punkte sind selbst
 * Umbruchpunkte und würden den Fehler verdecken.
 *
 * @param laenge Zielzeichenzahl des Ergebnisses.
 * @param praefix Optionaler Anfang (z.B. zur Wiedererkennung im Screenshot),
 *   zaehlt zur Zielzeichenzahl.
 */
export function spacelosesWort(laenge: number, praefix = ''): string {
    const stamm = 'Verwaltungskoordinationsbeschaffungsdokumentationsprozessabteilung';
    let ergebnis = praefix;
    while (ergebnis.length < laenge) ergebnis += stamm;
    return ergebnis.slice(0, laenge);
}
