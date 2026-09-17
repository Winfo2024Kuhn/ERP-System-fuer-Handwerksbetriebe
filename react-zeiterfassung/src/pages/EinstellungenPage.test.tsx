import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import EinstellungenPage from './EinstellungenPage'
import { ToastProvider } from '../components/ui/toast'
import {
    leseBenachrichtigungsStatus,
    leseKameraStatus,
    leseMikrofonStatus,
    frageKameraAn,
    frageMikrofonAn,
} from '../services/permissionStatusService'
import { frageBenachrichtigungenAn } from '../services/notificationBootstrap'

vi.mock('../services/permissionStatusService', () => ({
    leseBenachrichtigungsStatus: vi.fn(),
    leseMikrofonStatus: vi.fn(),
    leseKameraStatus: vi.fn(),
    frageMikrofonAn: vi.fn(),
    frageKameraAn: vi.fn(),
}))
vi.mock('../services/notificationBootstrap', () => ({
    frageBenachrichtigungenAn: vi.fn(),
}))

const benachrichtigungMock = vi.mocked(leseBenachrichtigungsStatus)
const mikrofonMock = vi.mocked(leseMikrofonStatus)
const kameraMock = vi.mocked(leseKameraStatus)
const frageMikrofonAnMock = vi.mocked(frageMikrofonAn)
const frageKameraAnMock = vi.mocked(frageKameraAn)
const frageBenachrichtigungenAnMock = vi.mocked(frageBenachrichtigungenAn)

function zeige() {
    render(
        <MemoryRouter><ToastProvider><EinstellungenPage /></ToastProvider></MemoryRouter>,
    )
}

beforeEach(() => {
    localStorage.setItem('zeiterfassung_token', 'test-token')
    benachrichtigungMock.mockReturnValue('nicht-gefragt')
    mikrofonMock.mockResolvedValue('nicht-gefragt')
    kameraMock.mockResolvedValue('nicht-gefragt')
    frageMikrofonAnMock.mockResolvedValue(undefined)
    frageKameraAnMock.mockResolvedValue(undefined)
    frageBenachrichtigungenAnMock.mockResolvedValue(true)
})

afterEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
})

describe('EinstellungenPage', () => {
    it('zeigt alle drei Bereiche mit Titel und Erklärung', async () => {
        zeige()

        expect(await screen.findByText('Benachrichtigungen')).toBeInTheDocument()
        expect(screen.getByText('Damit du Termine und Freigaben aufs Handy bekommst')).toBeInTheDocument()
        expect(screen.getByText('Mikrofon')).toBeInTheDocument()
        expect(screen.getByText('Damit du Einträge diktieren kannst statt sie zu tippen')).toBeInTheDocument()
        expect(screen.getByText('Kamera')).toBeInTheDocument()
        expect(screen.getByText('Für Fotos im Bautagebuch und zum Belege scannen')).toBeInTheDocument()
    })

    it('zeigt die dauerhafte Datenschutz-Zeile beim Mikrofon', async () => {
        zeige()

        expect(await screen.findByText(/danach sofort gelöscht/)).toBeInTheDocument()
    })

    it('zeigt je Status das passende Abzeichen', async () => {
        benachrichtigungMock.mockReturnValue('erlaubt')
        mikrofonMock.mockResolvedValue('blockiert')
        kameraMock.mockResolvedValue('nicht-verfuegbar')
        zeige()

        expect(await screen.findByText('Erlaubt')).toBeInTheDocument()
        expect(screen.getByText('Blockiert')).toBeInTheDocument()
        expect(screen.getByText('Gerät kann das nicht')).toBeInTheDocument()
    })

    it('bietet bei "blockiert" keinen Erlauben-Knopf, sondern eine Anleitung', async () => {
        benachrichtigungMock.mockReturnValue('erlaubt')
        mikrofonMock.mockResolvedValue('blockiert')
        kameraMock.mockResolvedValue('erlaubt')
        const user = userEvent.setup()
        zeige()

        await screen.findByText('Blockiert')
        expect(screen.queryByRole('button', { name: 'Erlauben' })).not.toBeInTheDocument()

        await user.click(screen.getByRole('button', { name: /Wie schalte ich das wieder ein/ }))
        expect(screen.getByText(/Einstellungen → Safari/)).toBeInTheDocument()
        expect(screen.getByText(/Schloss-Symbol/)).toBeInTheDocument()
    })

    it('bietet bei "Gerät kann das nicht" keinen Knopf an', async () => {
        benachrichtigungMock.mockReturnValue('nicht-verfuegbar')
        mikrofonMock.mockResolvedValue('nicht-verfuegbar')
        kameraMock.mockResolvedValue('nicht-verfuegbar')
        zeige()

        await screen.findAllByText('Gerät kann das nicht')
        expect(screen.queryByRole('button', { name: 'Erlauben' })).not.toBeInTheDocument()
    })

    it('fragt das Mikrofon an und liest den Status danach neu', async () => {
        benachrichtigungMock.mockReturnValue('erlaubt')
        mikrofonMock.mockResolvedValue('nicht-gefragt')
        kameraMock.mockResolvedValue('erlaubt')
        const user = userEvent.setup()
        zeige()

        await user.click(await screen.findByRole('button', { name: 'Erlauben' }))

        await waitFor(() => expect(frageMikrofonAnMock).toHaveBeenCalled())
        expect(frageKameraAnMock).not.toHaveBeenCalled()
        expect(frageBenachrichtigungenAnMock).not.toHaveBeenCalled()
        // Einmal beim Mount, einmal nach der Anfrage.
        await waitFor(() => expect(mikrofonMock).toHaveBeenCalledTimes(2))
    })

    it('fragt Benachrichtigungen mit dem Token an', async () => {
        benachrichtigungMock.mockReturnValue('nicht-gefragt')
        mikrofonMock.mockResolvedValue('erlaubt')
        kameraMock.mockResolvedValue('erlaubt')
        const user = userEvent.setup()
        zeige()

        await user.click(await screen.findByRole('button', { name: 'Erlauben' }))

        await waitFor(() => expect(frageBenachrichtigungenAnMock).toHaveBeenCalledWith('test-token'))
    })

    it('meldet eine abgelehnte Anfrage als Fehler', async () => {
        benachrichtigungMock.mockReturnValue('erlaubt')
        mikrofonMock.mockResolvedValue('nicht-gefragt')
        kameraMock.mockResolvedValue('erlaubt')
        frageMikrofonAnMock.mockRejectedValue(new Error('abgelehnt'))
        const user = userEvent.setup()
        zeige()

        await user.click(await screen.findByRole('button', { name: 'Erlauben' }))

        expect(await screen.findByRole('alert')).toHaveTextContent('Das Mikrofon wurde nicht erlaubt.')
    })

    it('meldet abgelehnte Benachrichtigungen auch ohne geworfenen Fehler', async () => {
        benachrichtigungMock.mockReturnValue('nicht-gefragt')
        mikrofonMock.mockResolvedValue('erlaubt')
        kameraMock.mockResolvedValue('erlaubt')
        frageBenachrichtigungenAnMock.mockResolvedValue(false)
        const user = userEvent.setup()
        zeige()

        await user.click(await screen.findByRole('button', { name: 'Erlauben' }))

        expect(await screen.findByRole('alert')).toHaveTextContent('Benachrichtigungen wurden nicht erlaubt.')
    })

    it('rät keinen Status, solange er noch geladen wird', () => {
        mikrofonMock.mockReturnValue(new Promise(() => {}) as Promise<never>)
        zeige()

        expect(screen.getByText('Wird geprüft…')).toBeInTheDocument()
        expect(screen.queryByText('Noch nicht gefragt')).not.toBeInTheDocument()
    })
})
