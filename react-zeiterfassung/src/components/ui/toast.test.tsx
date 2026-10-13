import { act, fireEvent, render, renderHook, screen } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { ToastProvider, useToast } from './toast'

beforeEach(() => vi.useFakeTimers())
afterEach(() => vi.useRealTimers())

const ebene = () => document.querySelector('[data-mobile-toasts]') as HTMLElement

it('schwebt als feste Ebene über dem Inhalt und verschiebt nichts', () => {
    const { result } = renderHook(useToast, {
        wrapper: ({ children }) => <ToastProvider><main>Inhalt</main>{children}</ToastProvider>,
    })
    expect(ebene()).toHaveAttribute('hidden')

    act(() => result.current.error('Speichern fehlgeschlagen'))

    expect(ebene()).not.toHaveAttribute('hidden')
    expect(ebene().className).toContain('fixed')
    expect(ebene().className).toContain('pointer-events-none')
    expect(screen.getByRole('alert').className).toContain('pointer-events-auto')
    // Der Inhalt liegt nicht mehr in derselben Spalte wie die Meldungen – keine Platz-Variable mehr.
    expect(ebene().contains(screen.getByText('Inhalt'))).toBe(false)
    expect(document.documentElement.style.getPropertyValue('--mobile-toast-height')).toBe('')
})

it('blendet Erfolg und Info nach 4 s aus, Warnung und Fehler bleiben bis X', () => {
    const { result } = renderHook(useToast, { wrapper: ToastProvider })
    act(() => {
        result.current.success('Gespeichert')
        result.current.info('Hinweis')
        result.current.warning('Offline')
    })
    expect(screen.getAllByRole('status')).toHaveLength(2)

    act(() => vi.advanceTimersByTime(4000 + 200))
    expect(screen.queryByText('Gespeichert')).not.toBeInTheDocument()
    expect(screen.queryByText('Hinweis')).not.toBeInTheDocument()

    act(() => vi.advanceTimersByTime(60_000))
    expect(screen.getByRole('alert')).toHaveTextContent('Offline')

    fireEvent.click(screen.getByRole('button', { name: 'Meldung schließen' }))
    // Erst sanft ausblenden, dann weg.
    expect(screen.getByRole('alert')).toHaveAttribute('data-geht', 'true')
    act(() => vi.advanceTimersByTime(200))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(ebene()).toHaveAttribute('hidden')
})

it('zeigt höchstens drei Meldungen und nennt die übrigen', () => {
    const { result } = renderHook(useToast, { wrapper: ToastProvider })
    act(() => {
        result.current.error('Fehler 1')
        result.current.error('Fehler 2')
        result.current.error('Fehler 3')
        result.current.error('Fehler 4')
    })
    expect(screen.getAllByRole('alert')).toHaveLength(3)
    expect(screen.queryByText('Fehler 1')).not.toBeInTheDocument()
    expect(screen.getByText('1 weitere Meldung')).toBeInTheDocument()

    fireEvent.click(screen.getAllByRole('button', { name: 'Meldung schließen' })[2])
    act(() => vi.advanceTimersByTime(200))
    expect(screen.getByText('Fehler 1')).toBeInTheDocument()
    expect(screen.queryByText(/weitere Meldung/)).not.toBeInTheDocument()
})

it('schließt bei reduzierter Bewegung sofort und räumt Timer beim Unmount auf', () => {
    const matchMedia = vi.spyOn(window, 'matchMedia').mockReturnValue({ matches: true } as MediaQueryList)
    const { result, unmount } = renderHook(useToast, { wrapper: ToastProvider })
    act(() => { result.current.error('Upload fehlgeschlagen'); result.current.success('Gespeichert') })

    fireEvent.click(screen.getAllByRole('button', { name: 'Meldung schließen' })[0])
    expect(screen.queryByText('Upload fehlgeschlagen')).not.toBeInTheDocument()

    unmount()
    expect(vi.getTimerCount()).toBe(0)
    matchMedia.mockRestore()
})

it('lässt den Inhalt darunter bedienbar', () => {
    const klick = vi.fn()
    const Seite = () => {
        const toast = useToast()
        return <button type="button" onClick={() => { toast.error('Fehler'); klick() }}>Speichern</button>
    }
    render(<ToastProvider><Seite /></ToastProvider>)
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    expect(klick).toHaveBeenCalledTimes(2)
})

it('stapelt identische Meldungen nicht, sondern startet nur ihre Laufzeit neu', () => {
    const { result } = renderHook(useToast, { wrapper: ToastProvider })
    act(() => { result.current.warning('Einen Moment – die letzte Aktion läuft noch.'); result.current.warning('Einen Moment – die letzte Aktion läuft noch.') })
    expect(screen.getAllByRole('alert')).toHaveLength(1)

    // Gleicher Text, andere Art → eigene Meldung.
    act(() => result.current.error('Einen Moment – die letzte Aktion läuft noch.'))
    expect(screen.getAllByRole('alert')).toHaveLength(2)

    act(() => result.current.success('Gespeichert'))
    act(() => vi.advanceTimersByTime(3000))
    act(() => result.current.success('Gespeichert'))
    act(() => vi.advanceTimersByTime(3000))
    // Nach 6 s noch da, weil die zweite Meldung die 4 s neu gestartet hat …
    expect(screen.getAllByRole('status')).toHaveLength(1)
    act(() => vi.advanceTimersByTime(1000 + 200))
    // … und danach weg.
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
})
