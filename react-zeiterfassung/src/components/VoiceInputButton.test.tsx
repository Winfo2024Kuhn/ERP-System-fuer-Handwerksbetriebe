import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import VoiceInputButton from './VoiceInputButton'
import { ToastProvider } from './ui/toast'
import { MikrofonNichtErlaubtError } from '../services/audioRecorderService'
import { starteAufnahme, mikrofonWirdUnterstuetzt } from '../services/audioRecorderService'
import { transkribiere } from '../services/spracheingabeService'

vi.mock('../services/audioRecorderService', async () => {
    const echt = await vi.importActual<typeof import('../services/audioRecorderService')>('../services/audioRecorderService')
    return {
        ...echt,
        starteAufnahme: vi.fn(),
        mikrofonWirdUnterstuetzt: vi.fn(() => true),
    }
})
vi.mock('../services/spracheingabeService', () => ({
    transkribiere: vi.fn(),
    SpracheingabeFehler: class extends Error {},
}))

const starteAufnahmeMock = vi.mocked(starteAufnahme)
const unterstuetztMock = vi.mocked(mikrofonWirdUnterstuetzt)
const transkribiereMock = vi.mocked(transkribiere)

/**
 * `navigator.onLine` umschalten. `writable` statt `configurable` wie in
 * NetworkStatusBadge.test.tsx: die Eigenschaft ist in dieser Umgebung bereits
 * nicht konfigurierbar, ihr Wert laesst sich aber weiterhin neu setzen.
 */
function setzeOnline(wert: boolean) {
    Object.defineProperty(navigator, 'onLine', { writable: true, value: wert })
}

/** Sitzung, die so tut, als haette sie `dauerMs` lang aufgenommen. */
function fakeSitzung(dauerMs = 5000) {
    return {
        mimeType: 'audio/webm',
        gestartetAm: Date.now() - dauerMs,
        stoppe: vi.fn(async () => new Blob(['x'], { type: 'audio/webm' })),
        brichAb: vi.fn(),
    }
}

function zeige(props: Partial<React.ComponentProps<typeof VoiceInputButton>> = {}) {
    const onErgebnis = vi.fn()
    render(
        <ToastProvider>
            <VoiceInputButton wert="" onErgebnis={onErgebnis} feldName="Eintrag" {...props} />
        </ToastProvider>,
    )
    return { onErgebnis }
}

beforeEach(() => {
    localStorage.clear()
    // Der Datenschutz-Hinweis ist in den meisten Tests nicht der Prüfgegenstand.
    localStorage.setItem('zeiterfassung_spracheingabe_hinweis', '1')
    localStorage.setItem('zeiterfassung_token', 'test-token')
    unterstuetztMock.mockReturnValue(true)
    starteAufnahmeMock.mockResolvedValue(fakeSitzung())
    transkribiereMock.mockResolvedValue('Neuer Text')
    setzeOnline(true)
})

afterEach(() => {
    // Ohne das reisst ein Test mit gefaelschten Timern alle folgenden mit sich:
    // sie warten dann auf eine Uhr, die niemand mehr weiterdreht.
    vi.useRealTimers()
    vi.clearAllMocks()
    localStorage.clear()
})

describe('VoiceInputButton', () => {
    it('durchläuft bereit → nimmt auf → verarbeitet → bereit', async () => {
        const user = userEvent.setup()
        zeige()

        await user.click(screen.getByRole('button', { name: 'Eintrag diktieren' }))
        const stopp = await screen.findByRole('button', { name: 'Aufnahme beenden' })

        await user.click(stopp)
        await waitFor(() => expect(screen.getByRole('button', { name: 'Eintrag diktieren' })).toBeInTheDocument())
    })

    it('hängt an vorhandenen Text an, statt ihn zu ersetzen', async () => {
        const user = userEvent.setup()
        const { onErgebnis } = zeige({ wert: 'Alter Text' })

        await user.click(screen.getByRole('button', { name: 'Eintrag diktieren' }))
        await user.click(await screen.findByRole('button', { name: 'Aufnahme beenden' }))

        await waitFor(() => expect(onErgebnis).toHaveBeenCalledWith('Alter Text\n\nNeuer Text'))
    })

    it('schreibt bei leerem Feld nur das Diktat', async () => {
        const user = userEvent.setup()
        const { onErgebnis } = zeige({ wert: '' })

        await user.click(screen.getByRole('button', { name: 'Eintrag diktieren' }))
        await user.click(await screen.findByRole('button', { name: 'Aufnahme beenden' }))

        await waitFor(() => expect(onErgebnis).toHaveBeenCalledWith('Neuer Text'))
    })

    it('lässt bei vorhandenen Zeilenumbrüchen genau eine Leerzeile stehen', async () => {
        const user = userEvent.setup()
        const { onErgebnis } = zeige({ wert: 'Alter Text\n\n' })

        await user.click(screen.getByRole('button', { name: 'Eintrag diktieren' }))
        await user.click(await screen.findByRole('button', { name: 'Aufnahme beenden' }))

        await waitFor(() => expect(onErgebnis).toHaveBeenCalledWith('Alter Text\n\nNeuer Text'))
    })

    it('sperrt ohne Netz und nennt den Grund', async () => {
        setzeOnline(false)
        zeige()

        expect(screen.getByRole('button', { name: 'Eintrag diktieren' })).toBeDisabled()
        expect(screen.getByText('Spracheingabe braucht Internet.')).toBeInTheDocument()
        expect(starteAufnahmeMock).not.toHaveBeenCalled()
    })

    it('sperrt auf Geräten ohne Mikrofon und nennt den Grund', () => {
        unterstuetztMock.mockReturnValue(false)
        zeige()

        expect(screen.getByRole('button', { name: 'Eintrag diktieren' })).toBeDisabled()
        expect(screen.getByText('Dieses Gerät kann nicht diktieren.')).toBeInTheDocument()
    })

    it('zeigt beim ersten Antippen den Datenschutz-Hinweis und nimmt noch nicht auf', async () => {
        localStorage.removeItem('zeiterfassung_spracheingabe_hinweis')
        const user = userEvent.setup()
        zeige()

        await user.click(screen.getByRole('button', { name: 'Eintrag diktieren' }))

        expect(screen.getByRole('dialog')).toBeInTheDocument()
        expect(starteAufnahmeMock).not.toHaveBeenCalled()
    })

    it('nimmt nach "Verstanden" auf und zeigt den Hinweis danach nie wieder', async () => {
        localStorage.removeItem('zeiterfassung_spracheingabe_hinweis')
        const user = userEvent.setup()
        const { unmount } = render(
            <ToastProvider><VoiceInputButton wert="" onErgebnis={vi.fn()} feldName="Eintrag" /></ToastProvider>,
        )

        await user.click(screen.getByRole('button', { name: 'Eintrag diktieren' }))
        await user.click(screen.getByRole('button', { name: 'Verstanden' }))
        await waitFor(() => expect(starteAufnahmeMock).toHaveBeenCalledTimes(1))

        unmount()
        starteAufnahmeMock.mockResolvedValue(fakeSitzung())
        zeige()
        await user.click(screen.getByRole('button', { name: 'Eintrag diktieren' }))

        expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })

    it('verwirft Aufnahmen unter einer Sekunde ohne Fehler-Toast', async () => {
        starteAufnahmeMock.mockResolvedValue(fakeSitzung(300))
        const user = userEvent.setup()
        const { onErgebnis } = zeige()

        await user.click(screen.getByRole('button', { name: 'Eintrag diktieren' }))
        await user.click(await screen.findByRole('button', { name: 'Aufnahme beenden' }))

        await waitFor(() => expect(screen.getByText(/Zu kurz/)).toBeInTheDocument())
        expect(transkribiereMock).not.toHaveBeenCalled()
        expect(onErgebnis).not.toHaveBeenCalled()
        expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })

    it('stoppt nach zwei Minuten von selbst und verarbeitet das Gesagte', async () => {
        vi.useFakeTimers()
        const { onErgebnis } = zeige()

        // fireEvent statt userEvent: userEvent wartet intern auf echte Timer und
        // verklemmt sich mit der gefaelschten Uhr.
        fireEvent.click(screen.getByRole('button', { name: 'Eintrag diktieren' }))
        await vi.waitFor(() => expect(starteAufnahmeMock).toHaveBeenCalled())

        await vi.advanceTimersByTimeAsync(120_000)

        await vi.waitFor(() => expect(transkribiereMock).toHaveBeenCalled())
        await vi.waitFor(() => expect(onErgebnis).toHaveBeenCalledWith('Neuer Text'))
    })

    it('meldet fehlende Mikrofon-Erlaubnis mit Verweis auf die Einstellungen', async () => {
        starteAufnahmeMock.mockRejectedValue(new MikrofonNichtErlaubtError('abgelehnt'))
        const user = userEvent.setup()
        const { onErgebnis } = zeige()

        await user.click(screen.getByRole('button', { name: 'Eintrag diktieren' }))

        const meldung = await screen.findByRole('alert')
        expect(meldung).toHaveTextContent(/Einstellungen/)
        expect(onErgebnis).not.toHaveBeenCalled()
    })

    it('lässt das Feld unangetastet, wenn das Aufschreiben fehlschlägt', async () => {
        transkribiereMock.mockRejectedValue(new Error('Serverfehler beim Aufschreiben.'))
        const user = userEvent.setup()
        const { onErgebnis } = zeige({ wert: 'Alter Text' })

        await user.click(screen.getByRole('button', { name: 'Eintrag diktieren' }))
        await user.click(await screen.findByRole('button', { name: 'Aufnahme beenden' }))

        expect(await screen.findByRole('alert')).toHaveTextContent('Serverfehler beim Aufschreiben.')
        expect(onErgebnis).not.toHaveBeenCalled()
    })

    it('gibt das Mikrofon frei, wenn die Seite während der Aufnahme verlassen wird', async () => {
        const sitzung = fakeSitzung()
        starteAufnahmeMock.mockResolvedValue(sitzung)
        const user = userEvent.setup()
        const { unmount } = render(
            <ToastProvider><VoiceInputButton wert="" onErgebnis={vi.fn()} feldName="Eintrag" /></ToastProvider>,
        )

        await user.click(screen.getByRole('button', { name: 'Eintrag diktieren' }))
        await screen.findByRole('button', { name: 'Aufnahme beenden' })

        unmount()

        expect(sitzung.brichAb).toHaveBeenCalled()
    })

    it('meldet fehlendes Token, statt stumm zu scheitern', async () => {
        localStorage.removeItem('zeiterfassung_token')
        const user = userEvent.setup()
        const { onErgebnis } = zeige()

        await user.click(screen.getByRole('button', { name: 'Eintrag diktieren' }))
        await user.click(await screen.findByRole('button', { name: 'Aufnahme beenden' }))

        expect(await screen.findByRole('alert')).toHaveTextContent(/QR-Code/)
        expect(transkribiereMock).not.toHaveBeenCalled()
        expect(onErgebnis).not.toHaveBeenCalled()
    })
})
