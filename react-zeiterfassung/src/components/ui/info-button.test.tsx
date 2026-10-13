import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { InfoAbschnitt, InfoButton } from './info-button'

describe('InfoButton (Handy)', () => {
    it('öffnet auf Tipp ein Sheet mit der Erklärung und schließt es mit Escape', async () => {
        const user = userEvent.setup()
        render(
            <InfoButton titel="Zurückziehen">
                <InfoAbschnitt titel="Was passiert?">
                    <p>Zurückziehen geht nur, solange der Antrag offen ist.</p>
                </InfoAbschnitt>
            </InfoButton>,
        )

        const knopf = screen.getByRole('button', { name: 'Mehr zu: Zurückziehen' })
        expect(knopf).toHaveAttribute('aria-expanded', 'false')
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

        await user.click(knopf)

        const sheet = await screen.findByRole('dialog')
        expect(sheet).toHaveTextContent('Zurückziehen')
        expect(sheet).toHaveTextContent('Was passiert?')
        expect(sheet).toHaveTextContent('Zurückziehen geht nur, solange der Antrag offen ist.')
        expect(knopf).toHaveAttribute('aria-expanded', 'true')

        await user.keyboard('{Escape}')
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
        expect(knopf).toHaveAttribute('aria-expanded', 'false')
    })
})
