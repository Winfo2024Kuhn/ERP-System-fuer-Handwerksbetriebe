import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

/**
 * Struktureller Test, bewusst als solcher.
 *
 * <p>Die Zusage lautet: Die App fragt beim Start NIE ungefragt nach der
 * Benachrichtigungs-Erlaubnis. Gefragt wird nur noch bewusst ueber die
 * Einstellungsseite. Grund: Der ungefragte Systemdialog fuehrt zu
 * reflexhaftem "Nicht erlauben", und das laesst sich danach von keiner
 * Webseite mehr zuruecknehmen.
 *
 * <p>Dass die "fragt nie"-Variante wirklich nie fragt, pruefen die
 * Verhaltenstests in `notificationBootstrap.test.ts`. Ungeprueft bliebe
 * sonst, ob `App.tsx` an BEIDEN Aufrufstellen die richtige Funktion nimmt —
 * bei der Session-Wiederherstellung UND beim QR-Login. Genau dort lag der
 * Fehler schon einmal: Eine fruehere Beschreibung nannte nur eine Stelle,
 * wodurch der Prompt ueber die andere bestehen geblieben waere.
 *
 * <p>Der saubere Weg waere ein Playwright-Test, der `requestPermission`
 * mitzaehlt. Solange es den nicht gibt, haelt dieser Test hier wenigstens
 * fest, dass niemand den alten Aufruf versehentlich wieder einbaut.
 */
const quelle = readFileSync(resolve(process.cwd(), 'src/App.tsx'), 'utf-8')

describe('App.tsx fragt nicht ungefragt nach Benachrichtigungen', () => {
    it('ruft requestPermission nirgends direkt auf', () => {
        expect(quelle).not.toMatch(/requestPermission/)
    })

    it('nutzt an beiden Aufrufstellen die Variante, die nur bei vorhandener Erlaubnis einrichtet', () => {
        const treffer = quelle.match(/starteBenachrichtigungenFallsErlaubt\(/g) ?? []
        // Zweimal aufgerufen plus einmal importiert.
        expect(treffer).toHaveLength(2)
    })

    it('hat die alte komponenten-lokale Kette entfernt', () => {
        expect(quelle).not.toMatch(/initializeNotifications/)
        expect(quelle).not.toMatch(/notificationIntervalRef/)
    })

    it('beendet das Intervall beim Abmelden ueber das ausgelagerte Modul', () => {
        expect(quelle).toMatch(/stoppeBenachrichtigungsIntervall\(\)/)
    })
})
