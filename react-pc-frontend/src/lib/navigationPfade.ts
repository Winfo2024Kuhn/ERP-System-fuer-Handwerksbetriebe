/**
 * Pfade in die Detailansichten von Projekt, Anfrage und Lieferant.
 *
 * Die Zielseiten werten den Query-Parameter aus und öffnen den Datensatz
 * direkt. Gemeinsam genutzt von Telefon (Anrufer-Details) und E-Mail-Center
 * (Zuordnung einer E-Mail).
 */

/** Pfad in ein Projekt. */
export function projektPfad(id: number): string {
    return `/projekte?projektId=${encodeURIComponent(String(id))}`;
}

/** Pfad in eine Anfrage. */
export function anfragePfad(id: number): string {
    return `/anfragen?anfrageId=${encodeURIComponent(String(id))}`;
}

/** Pfad in die Lieferantenakte. */
export function lieferantPfad(id: number): string {
    return `/lieferanten?lieferantId=${encodeURIComponent(String(id))}`;
}
