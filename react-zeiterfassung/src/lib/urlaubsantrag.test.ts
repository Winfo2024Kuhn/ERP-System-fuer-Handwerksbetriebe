import { describe, expect, it } from 'vitest'
import { anspruchHinweis, entscheidungsText, formatIsoDatumDe, formatUrlaubstage, statusLabel, tageMitEinheit, typLabel, vorjahresHinweis, zurueckziehenFrage } from './urlaubsantrag'

describe('anspruchHinweis', () => {
    it('erklärt abweichende Ansprüche wie am PC', () => {
        expect(anspruchHinweis('ANTEILIG_EINTRITT', '2027-08-01')).toBe('anteilig wegen Eintritt am 01.08.2027')
        expect(anspruchHinweis('ANTEILIG_EINTRITT', null)).toBe('anteilig wegen Eintritt im Jahr')
        expect(anspruchHinweis('MINDESTURLAUB_EINTRITT', '2027-06-01')).toBe('gesetzlicher Mindesturlaub (Eintritt am 01.06.2027)')
        expect(anspruchHinweis('MINDESTURLAUB_EINTRITT', null)).toBe('gesetzlicher Mindesturlaub')
        expect(anspruchHinweis('MINDESTURLAUB', null)).toBe('gesetzlicher Mindesturlaub')
        expect(anspruchHinweis('VOR_EINTRITT', '2028-02-01')).toBe('Eintritt erst am 01.02.2028')
        expect(anspruchHinweis('VOR_EINTRITT', null)).toBe('vor dem Eintritt')
        expect(anspruchHinweis('VOLL', null)).toBeNull()
    })
})

describe('tageMitEinheit', () => {
    it('schreibt Tag in der Einzahl', () => {
        expect(tageMitEinheit(1)).toBe('1 Tag')
        expect(tageMitEinheit(2.5)).toBe('2,5 Tage')
    })
})

describe('vorjahresHinweis', () => {
    it('nennt offenen Vorjahresrest mit oder ohne Verfall', () => {
        expect(vorjahresHinweis({ ausVorjahrOffen: 2.5, verfallsdatum: '2027-03-31' }))
            .toBe('davon 2,5 Tage aus dem Vorjahr – bis 31.03.2027 nehmen, danach verfallen sie')
        expect(vorjahresHinweis({ ausVorjahrOffen: 1, verfallsdatum: null }))
            .toBe('davon 1 Tag aus dem Vorjahr, verfallen nicht')
    })

    it('meldet verfallene Tage und schweigt ohne Vorjahresrest', () => {
        expect(vorjahresHinweis({ ausVorjahrOffen: 0, verfallen: 1, verfallsdatum: '2027-03-31' }))
            .toBe('1 Tag aus dem Vorjahr ist nach dem 31.03.2027 verfallen')
        expect(vorjahresHinweis({ ausVorjahrOffen: 0, verfallen: 3, verfallsdatum: '2027-03-31' }))
            .toBe('3 Tage aus dem Vorjahr sind nach dem 31.03.2027 verfallen')
        expect(vorjahresHinweis({ ausVorjahrOffen: 0, verfallen: 3, verfallsdatum: null })).toBeNull()
        expect(vorjahresHinweis({})).toBeNull()
        expect(vorjahresHinweis(null)).toBeNull()
    })
})

describe('formatUrlaubstage', () => {
    it('zeigt ganze Tage ohne und halbe Tage mit Komma', () => {
        expect(formatUrlaubstage(12)).toBe('12')
        expect(formatUrlaubstage(12.5)).toBe('12,5')
        expect(formatUrlaubstage(0.5)).toBe('0,5')
    })

    it('fällt bei ungültigen Werten auf 0 zurück', () => {
        expect(formatUrlaubstage(null)).toBe('0')
        expect(formatUrlaubstage(Number.NaN)).toBe('0')
    })
})

describe('statusLabel / typLabel', () => {
    it('übersetzt Rohwerte in Klartext', () => {
        expect(statusLabel('STORNIERT')).toBe('Storniert')
        expect(statusLabel('OFFEN')).toBe('Offen')
        expect(statusLabel('WAS_AUCH_IMMER')).toBe('Unbekannt')
        expect(typLabel('ZEITAUSGLEICH')).toBe('Zeitausgleich')
        expect(typLabel('UNBEKANNT')).toBe('Abwesenheit')
    })
})

describe('entscheidungsText', () => {
    it('formuliert Genehmigung mit deutschem Datum', () => {
        expect(entscheidungsText({ vonDatum: '2026-08-03', bisDatum: '2026-08-14', status: 'GENEHMIGT', typ: 'URLAUB' }))
            .toBe('Dein Urlaub vom 03.08.2026 bis 14.08.2026 wurde genehmigt.')
    })

    it('formuliert Ablehnung und passt das Typ-Wort an', () => {
        expect(entscheidungsText({ vonDatum: '2026-09-01', bisDatum: '2026-09-02', status: 'ABGELEHNT', typ: 'ZEITAUSGLEICH' }))
            .toBe('Dein Zeitausgleich vom 01.09.2026 bis 02.09.2026 wurde abgelehnt.')
        expect(entscheidungsText({ vonDatum: '2026-09-01', bisDatum: '2026-09-02', status: 'GENEHMIGT', typ: 'KRANKHEIT' }))
            .toBe('Deine Krankmeldung vom 01.09.2026 bis 02.09.2026 wurde genehmigt.')
        expect(entscheidungsText({ vonDatum: '2026-09-01', bisDatum: '2026-09-02', status: 'GENEHMIGT', typ: 'FORTBILDUNG' }))
            .toBe('Deine Fortbildung vom 01.09.2026 bis 02.09.2026 wurde genehmigt.')
    })

    it('nennt bei einem einzelnen Tag nur dieses Datum', () => {
        expect(entscheidungsText({ vonDatum: '2026-10-05', bisDatum: '2026-10-05', status: 'GENEHMIGT', typ: 'URLAUB' }))
            .toBe('Dein Urlaub am 05.10.2026 wurde genehmigt.')
    })

    it('formatIsoDatumDe lässt Zeitanteile weg', () => {
        expect(formatIsoDatumDe('2026-10-05T00:00:00')).toBe('05.10.2026')
    })
})

describe('entscheidungsText – Storno durch das Büro', () => {
    it('nennt das Büro und behält den Artikel je Art', () => {
        expect(entscheidungsText({ vonDatum: '2026-08-03', bisDatum: '2026-08-14', status: 'STORNIERT', typ: 'URLAUB' }))
            .toBe('Dein Urlaub vom 03.08.2026 bis 14.08.2026 wurde vom Büro storniert.')
        expect(entscheidungsText({ vonDatum: '2026-08-03', bisDatum: '2026-08-03', status: 'STORNIERT', typ: 'KRANKHEIT' }))
            .toBe('Deine Krankmeldung am 03.08.2026 wurde vom Büro storniert.')
    })
})

describe('zurueckziehenFrage', () => {
    it('formuliert die Rückfrage mit Artikel je Art', () => {
        expect(zurueckziehenFrage({ typ: 'URLAUB', vonDatum: '2026-08-03', bisDatum: '2026-08-14' }))
            .toBe('Deinen Urlaubsantrag vom 03.08.2026 bis 14.08.2026 zurückziehen?')
        expect(zurueckziehenFrage({ typ: 'KRANKHEIT', vonDatum: '2026-08-03', bisDatum: '2026-08-03' }))
            .toBe('Deine Krankmeldung am 03.08.2026 zurückziehen?')
        expect(zurueckziehenFrage({ typ: 'FORTBILDUNG', vonDatum: '2026-08-03', bisDatum: '2026-08-04' }))
            .toBe('Deinen Fortbildungsantrag vom 03.08.2026 bis 04.08.2026 zurückziehen?')
        expect(zurueckziehenFrage({ typ: 'ZEITAUSGLEICH', vonDatum: '2026-08-03', bisDatum: '2026-08-03' }))
            .toBe('Deinen Zeitausgleichsantrag am 03.08.2026 zurückziehen?')
        expect(zurueckziehenFrage({ typ: 'ARBEIT', vonDatum: '2026-08-03', bisDatum: '2026-08-03' }))
            .toBe('Deinen Urlaubsantrag am 03.08.2026 zurückziehen?')
    })
})
