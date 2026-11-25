import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { AnsprechpartnerKarte } from './AnsprechpartnerKarte'
import type { LieferantAnsprechpartnerKurz } from './ansprechpartner'

/** Dummy-Daten (DSGVO): Max Mustermann, Erika Musterfrau. */
const MAX: LieferantAnsprechpartnerKurz = {
    id: 1, name: 'Max Mustermann', aufgabe: 'Außendienst', telefon: null, mobiltelefon: '0171 1234567', email: 'max@muster.de',
}
const ERIKA: LieferantAnsprechpartnerKurz = {
    id: 2, name: 'Erika Musterfrau', aufgabe: null, telefon: '0511 123-12', mobiltelefon: null, email: null,
}

describe('AnsprechpartnerKarte (Handy)', () => {
    it('zeigt jede Person mit Aufgabe, Anruf- und Mail-Link', () => {
        render(<AnsprechpartnerKarte ansprechpartner={[MAX, ERIKA]} />)
        expect(screen.getByRole('heading', { name: 'Ansprechpartner' })).toBeInTheDocument()

        const max = within(screen.getByRole('listitem', { name: 'Max Mustermann' }))
        expect(max.getByText('Außendienst')).toBeInTheDocument()
        const handy = max.getByRole('link', { name: 'Max Mustermann anrufen: Handy 0171 1234567' })
        expect(handy).toHaveAttribute('href', 'tel:0171 1234567')
        // Tippfläche mindestens 44 px hoch
        expect(handy).toHaveClass('min-h-11')
        expect(max.getByRole('link', { name: 'E-Mail an Max Mustermann: max@muster.de' })).toHaveAttribute('href', 'mailto:max@muster.de')

        const erika = within(screen.getByRole('listitem', { name: 'Erika Musterfrau' }))
        expect(erika.getByRole('link', { name: 'Erika Musterfrau anrufen: Telefon 0511 123-12' })).toHaveAttribute('href', 'tel:0511 123-12')
        expect(erika.queryByRole('link', { name: /E-Mail/ })).toBeNull()
    })

    it('lässt reine Vorwahlen weg, weil man sie nicht wählen kann', () => {
        render(<AnsprechpartnerKarte ansprechpartner={[{ ...ERIKA, telefon: '0511 ' }]} />)
        expect(screen.queryByRole('link')).toBeNull()
    })

    it('zeigt ohne Ansprechpartner nichts', () => {
        const { container } = render(<AnsprechpartnerKarte ansprechpartner={[]} />)
        expect(container).toBeEmptyDOMElement()
    })
})
