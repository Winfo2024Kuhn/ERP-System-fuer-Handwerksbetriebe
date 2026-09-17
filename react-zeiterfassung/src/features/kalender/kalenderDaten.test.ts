import { describe, expect, it } from 'vitest'
import type { KalenderEintrag } from './typen'
import {
    addiereMonate,
    formatLocalDate,
    formatMonatsTitel,
    formatZeitraum,
    gruppiereProTag,
    istGueltigesIsoDatum,
    monateImBereich,
    monatsRaster,
    parseLocalDate,
    punktFarben,
    relativerTag,
    sichtbarerBereich,
    sortiereTermine,
    terminAusAbwesenheit,
    terminAusEintrag,
    terminAusFeiertag,
} from './kalenderDaten'

// Dummy-Daten (DSGVO): ausschließlich Max Mustermann & Co.
const eintrag = (teil: Partial<KalenderEintrag> = {}): KalenderEintrag => ({
    id: 1,
    titel: 'Baustelle Musterstraße',
    beschreibung: null,
    datum: '2026-09-17',
    startZeit: '09:00:00',
    endeZeit: '10:30:00',
    ganztaegig: false,
    farbe: null,
    projektId: null,
    projektName: null,
    kundeId: null,
    kundeName: null,
    lieferantId: null,
    lieferantName: null,
    anfrageId: null,
    anfrageBetreff: null,
    erstellerId: null,
    erstellerName: null,
    teilnehmer: [],
    ...teil,
})

describe('kalenderDaten – Datum', () => {
    it('formatLocalDate und parseLocalDate sind Umkehrfunktionen ohne UTC-Versatz', () => {
        expect(formatLocalDate(parseLocalDate('2026-09-17'))).toBe('2026-09-17')
        expect(parseLocalDate('2026-03-29').getDate()).toBe(29)
        expect(parseLocalDate('2026-01-05').getDay()).toBe(1)
    })

    it('monatsRaster beginnt am Montag vor dem 1. und endet am Sonntag nach dem Letzten', () => {
        const raster = monatsRaster(2026, 8)
        expect(raster[0]).toMatchObject({ datum: '2026-08-31', imMonat: false, wochenende: false })
        expect(raster[1]).toMatchObject({ datum: '2026-09-01', tag: 1, imMonat: true })
        expect(raster[raster.length - 1]).toMatchObject({ datum: '2026-10-04', imMonat: false, wochenende: true })
        expect(raster.length % 7).toBe(0)
    })

    it('sichtbarerBereich deckt genau das Monatsraster inklusive Randtage ab', () => {
        expect(sichtbarerBereich('2026-09-17')).toEqual({ von: '2026-08-31', bis: '2026-10-04' })
        expect(sichtbarerBereich('2026-02-10')).toEqual({ von: '2026-01-26', bis: '2026-03-01' })
    })

    it('monateImBereich liefert alle berührten Monate chronologisch', () => {
        expect(monateImBereich({ von: '2026-08-31', bis: '2026-10-04' })).toEqual([
            { jahr: 2026, monat: 8 },
            { jahr: 2026, monat: 9 },
            { jahr: 2026, monat: 10 },
        ])
        expect(monateImBereich({ von: '2026-12-28', bis: '2027-01-03' })).toEqual([
            { jahr: 2026, monat: 12 },
            { jahr: 2027, monat: 1 },
        ])
    })

    it('addiereMonate klemmt den Tag auf den Monatsletzten', () => {
        expect(addiereMonate('2026-01-31', 1)).toBe('2026-02-28')
        expect(addiereMonate('2026-03-31', -1)).toBe('2026-02-28')
        expect(addiereMonate('2026-12-15', 1)).toBe('2027-01-15')
    })

    it('formatMonatsTitel nennt Monat und Jahr', () => {
        expect(formatMonatsTitel('2026-09-17')).toBe('September 2026')
        expect(formatMonatsTitel('2027-01-01')).toBe('Januar 2027')
    })

    it('istGueltigesIsoDatum akzeptiert nur echte Kalendertage in ISO-Form', () => {
        expect(istGueltigesIsoDatum('2026-09-17')).toBe(true)
        expect(istGueltigesIsoDatum('2026-02-30')).toBe(false)
        expect(istGueltigesIsoDatum('17.09.2026')).toBe(false)
        expect(istGueltigesIsoDatum('abc')).toBe(false)
        expect(istGueltigesIsoDatum(null)).toBe(false)
        expect(istGueltigesIsoDatum(undefined)).toBe(false)
    })

    it('relativerTag kennt Heute, Morgen und Gestern', () => {
        expect(relativerTag('2026-09-17', '2026-09-17')).toBe('Heute')
        expect(relativerTag('2026-09-18', '2026-09-17')).toBe('Morgen')
        expect(relativerTag('2026-09-16', '2026-09-17')).toBe('Gestern')
        expect(relativerTag('2026-09-20', '2026-09-17')).toBeNull()
    })
})

describe('kalenderDaten – Anzeigemodell', () => {
    it('terminAusEintrag kürzt Zeiten, zieht Rot auf Violett und sammelt Verknüpfungen', () => {
        const termin = terminAusEintrag(eintrag({ farbe: '#dc2626', projektId: 7, projektName: 'Dachsanierung', kundeId: 3, kundeName: 'Max Mustermann' }))
        expect(termin.key).toBe('t-1')
        expect(termin.terminId).toBe(1)
        expect(termin.startZeit).toBe('09:00')
        expect(termin.endeZeit).toBe('10:30')
        expect(termin.farbe).toBe('#7c3aed')
        expect(termin.untertitel).toBe('Dachsanierung')
        expect(termin.verknuepfungen).toEqual([
            { art: 'projekt', id: 7, name: 'Dachsanierung' },
            { art: 'kunde', id: 3, name: 'Max Mustermann' },
        ])
        expect(formatZeitraum(termin)).toBe('09:00 – 10:30 Uhr')
    })

    it('terminAusEintrag behält eigene Farben und beschriftet Termine ohne Verknüpfung', () => {
        const termin = terminAusEintrag(eintrag({ farbe: '#2563eb' }))
        expect(termin.farbe).toBe('#2563eb')
        expect(termin.untertitel).toBe('Termin')
    })

    it('ganztägige Termine haben keine Zeiten', () => {
        const termin = terminAusEintrag(eintrag({ ganztaegig: true }))
        expect(termin.startZeit).toBeNull()
        expect(formatZeitraum(termin)).toBe('Ganztägig')
        expect(terminAusEintrag(eintrag({ startZeit: null, endeZeit: null })).ganztaegig).toBe(true)
    })

    it('Abwesenheiten und Feiertage bekommen Art, Farbe und Beschriftung', () => {
        const urlaub = terminAusAbwesenheit({ id: 5, datum: '2026-09-01', typ: 'URLAUB', stunden: 8, mitarbeiterId: 2, mitarbeiterName: 'Erika Musterfrau' })
        expect(urlaub).toMatchObject({ key: 'a-5', art: 'URLAUB', titel: 'Erika Musterfrau', untertitel: 'Urlaub', farbe: '#0ea5e9', ganztaegig: true })
        const feiertag = terminAusFeiertag({ datum: '2026-10-03', bezeichnung: 'Tag der Deutschen Einheit' })
        expect(feiertag).toMatchObject({ art: 'FEIERTAG', titel: 'Tag der Deutschen Einheit', farbe: '#f43f5e', terminId: null })
    })

    it('sortiereTermine: Feiertag, Abwesenheiten, ganztägige Termine, dann nach Uhrzeit', () => {
        const sortiert = sortiereTermine([
            terminAusEintrag(eintrag({ id: 1, titel: 'Spät', startZeit: '15:00:00', endeZeit: null })),
            terminAusEintrag(eintrag({ id: 2, titel: 'Früh', startZeit: '08:00:00', endeZeit: null })),
            terminAusEintrag(eintrag({ id: 3, titel: 'Ganztags', ganztaegig: true })),
            terminAusAbwesenheit({ id: 4, datum: '2026-09-17', typ: 'KRANKHEIT', stunden: 8, mitarbeiterId: 1, mitarbeiterName: 'Max Mustermann' }),
            terminAusFeiertag({ datum: '2026-09-17', bezeichnung: 'Testfeiertag' }),
        ])
        expect(sortiert.map(t => t.titel)).toEqual(['Testfeiertag', 'Max Mustermann', 'Ganztags', 'Früh', 'Spät'])
    })

    it('gruppiereProTag sortiert jede Tagesliste und punktFarben liefert höchstens drei Farben', () => {
        const gruppen = gruppiereProTag([
            terminAusEintrag(eintrag({ id: 1, datum: '2026-09-17', startZeit: '15:00:00', farbe: '#2563eb' })),
            terminAusEintrag(eintrag({ id: 2, datum: '2026-09-17', startZeit: '08:00:00', farbe: '#2563eb' })),
            terminAusEintrag(eintrag({ id: 3, datum: '2026-09-18' })),
            terminAusAbwesenheit({ id: 4, datum: '2026-09-17', typ: 'URLAUB', stunden: 8, mitarbeiterId: 1, mitarbeiterName: 'Max Mustermann' }),
            terminAusAbwesenheit({ id: 5, datum: '2026-09-17', typ: 'FORTBILDUNG', stunden: 8, mitarbeiterId: 2, mitarbeiterName: 'Erika Musterfrau' }),
            terminAusFeiertag({ datum: '2026-09-17', bezeichnung: 'Testfeiertag' }),
        ])
        expect(Array.from(gruppen.keys())).toEqual(['2026-09-17', '2026-09-18'])
        expect(gruppen.get('2026-09-17')!.map(t => t.key)).toEqual(['f-2026-09-17-Testfeiertag', 'a-4', 'a-5', 't-2', 't-1'])
        expect(punktFarben(gruppen.get('2026-09-17')!)).toEqual(['#f43f5e', '#0ea5e9', '#10b981'])
    })

})
