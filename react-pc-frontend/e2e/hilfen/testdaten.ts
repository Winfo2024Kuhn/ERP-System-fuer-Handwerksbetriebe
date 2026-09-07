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

/**
 * Erwartete Spaltenzahl eines Kartenrasters bei einer gegebenen Fensterbreite.
 *
 * Nachtrag Abschnitt 10 (Task 10b, Design-Review Abschnitt 6): die
 * Kartenraster-Zusicherungen in anfrage-/kunde-/lieferant-layout.spec.ts,
 * projekt-uebersicht-layout.spec.ts und uebersichten-layout.spec.ts leiteten
 * die erwartete Spaltenzahl bisher aus `testInfo.project.name` ab
 * (`=== 'pc-monitor' ? 4 : 3`) statt aus der tatsaechlichen Fensterbreite.
 * Das haelt nur, solange es genau zwei Projekte gibt -- bei `pc-uebergang`
 * (1536x960) greift Tailwinds "2xl:"-Breakpoint (min-width: 1536px) bereits,
 * dort sind also VIER Karten richtig, nicht drei. Ein Probelauf ueber alle
 * Specs bei 1536px ergab deshalb 18 rote Faelle, obwohl das Raster korrekt
 * arbeitet -- nur die Zusicherung war zu starr.
 *
 * Alle vier Kartenraster (Projekt/Anfrage/Kunde/Lieferant) nutzen dieselbe
 * Klasse `grid-cols-1 md:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4`
 * (Tailwind-Standardbreakpoints, kein eigenes `screens` in
 * tailwind.config.js: md=768px, lg=1024px, 2xl=1536px).
 */
export function erwarteteKartenspalten(fensterbreite: number): number {
    if (fensterbreite >= 1536) return 4;
    if (fensterbreite >= 1024) return 3;
    if (fensterbreite >= 768) return 2;
    return 1;
}
