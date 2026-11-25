/**
 * Ansprechpartner eines Lieferanten, wie ihn die Handy-App mit der
 * Lieferanten-Liste bekommt (`/api/zeiterfassung/lieferanten`). Nur Anzeige,
 * bearbeitet wird am PC im Reiter „Ansprechpartner“.
 */
export interface LieferantAnsprechpartnerKurz {
    id: number
    /** Fertiger Name, z. B. „Max Mustermann“. */
    name: string
    /** Freier Text, z. B. „Außendienst“. */
    aufgabe: string | null
    telefon: string | null
    mobiltelefon: string | null
    email: string | null
}
