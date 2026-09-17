import type { KalenderTermin } from './typen'

/** Pfad der Termin-Unterseite für einen Eintrag. */
export function terminPfad(termin: Pick<KalenderTermin, 'datum' | 'key'>): string {
    return `/kalender/termin/${encodeURIComponent(termin.datum)}/${encodeURIComponent(termin.key)}`
}

/** Kalender-URL, die den Tag mit geöffnetem Tages-Sheet zeigt – Rückweg von der Unterseite. */
export function kalenderPfadMitSheet(datum: string): string {
    return `/kalender?datum=${encodeURIComponent(datum)}&sheet=tag`
}
