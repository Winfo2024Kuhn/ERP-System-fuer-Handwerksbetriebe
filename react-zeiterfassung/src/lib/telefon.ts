/**
 * Unter dieser Ziffernzahl steht keine vollständige Rufnummer im Feld, sondern nur die
 * Ortsvorwahl, die das Büro beim Eingeben der Adresse vorschlägt (z. B. „0931 “).
 * Gleiche Grenze wie `KundeDuplikatService.normalisiereTelefon` im Backend.
 */
const MINDEST_ZIFFERN = 7

/**
 * Liefert die Nummer nur, wenn man sie auch anrufen kann. Leere Felder und eine reine
 * Vorwahl ergeben `undefined` – die App zeigt dann „Keine Telefonnummer hinterlegt“.
 */
export function anrufbareNummer(nummer: string | null | undefined): string | undefined {
    const eingabe = nummer?.trim()
    if (!eingabe) return undefined
    // +49 / 0049 wie die führende 0 behandeln, sonst zählt die Ländervorwahl als Rufnummer mit.
    const ziffern = eingabe.replace(/^(?:\+|00)49/, '0').replace(/\D/g, '')
    return ziffern.length >= MINDEST_ZIFFERN ? eingabe : undefined
}
