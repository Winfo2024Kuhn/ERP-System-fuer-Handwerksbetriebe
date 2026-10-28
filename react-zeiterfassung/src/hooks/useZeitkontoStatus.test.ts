import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { OfflineService } from '../services/OfflineService'
import type { ZeitkontoStatus } from '../types/zeitkonto'
import { useZeitkontoStatus } from './useZeitkontoStatus'

const eingerichtet: ZeitkontoStatus = { fuehrtZeitkonto: true, eingerichtet: true, hinweis: null }
const nichtEingerichtet: ZeitkontoStatus = {
    fuehrtZeitkonto: true, eingerichtet: false, hinweis: 'Für Sie ist noch keine Arbeitszeit eingerichtet.',
}

describe('useZeitkontoStatus', () => {
    beforeEach(() => {
        localStorage.setItem('zeiterfassung_token', 'token-max-mustermann')
    })

    afterEach(() => {
        vi.restoreAllMocks()
        localStorage.clear()
    })

    it('liefert den Stand und gibt das Buchen frei', async () => {
        const holen = vi.spyOn(OfflineService, 'getZeitkontoStatus').mockResolvedValue({ status: eingerichtet, ausCache: false })

        const { result } = renderHook(() => useZeitkontoStatus())

        expect(result.current.geprueft).toBe(false)
        await waitFor(() => expect(result.current.geprueft).toBe(true))
        expect(result.current).toMatchObject({ status: eingerichtet, ausCache: false, gesperrt: false, eingerichtet: true })
        expect(holen).toHaveBeenCalledWith('token-max-mustermann')
    })

    it('ohne Netz gilt der gemerkte Stand', async () => {
        vi.spyOn(OfflineService, 'getZeitkontoStatus').mockResolvedValue({ status: eingerichtet, ausCache: true })

        const { result } = renderHook(() => useZeitkontoStatus())

        await waitFor(() => expect(result.current.geprueft).toBe(true))
        expect(result.current).toMatchObject({ ausCache: true, gesperrt: false, eingerichtet: true })
    })

    it('ein unbekannter Stand sperrt nicht, ist aber auch nicht „eingerichtet“', async () => {
        vi.spyOn(OfflineService, 'getZeitkontoStatus').mockResolvedValue({ status: null, ausCache: false })

        const { result } = renderHook(() => useZeitkontoStatus())

        await waitFor(() => expect(result.current.geprueft).toBe(true))
        expect(result.current).toMatchObject({ status: null, gesperrt: false, eingerichtet: false })
    })

    it('sperrt nur bei ausdrücklichem „nicht eingerichtet“', async () => {
        vi.spyOn(OfflineService, 'getZeitkontoStatus').mockResolvedValue({ status: nichtEingerichtet, ausCache: false })

        const { result } = renderHook(() => useZeitkontoStatus())

        await waitFor(() => expect(result.current.gesperrt).toBe(true))
        expect(result.current.eingerichtet).toBe(false)
    })

    it('unlesbarer Handy-Speicher gilt als unbekannt', async () => {
        vi.spyOn(OfflineService, 'getZeitkontoStatus').mockRejectedValue(new Error('IndexedDB weg'))

        const { result } = renderHook(() => useZeitkontoStatus())

        await waitFor(() => expect(result.current.geprueft).toBe(true))
        expect(result.current).toMatchObject({ status: null, gesperrt: false })
    })

    it('ohne Anmeldung wird nichts abgefragt', async () => {
        localStorage.clear()
        const holen = vi.spyOn(OfflineService, 'getZeitkontoStatus')

        const { result } = renderHook(() => useZeitkontoStatus())

        await waitFor(() => expect(result.current.geprueft).toBe(true))
        expect(holen).not.toHaveBeenCalled()
        expect(result.current.gesperrt).toBe(false)
    })

    it('prüft neu bei neuem Auslöser und auf Knopfdruck, ohne „geprüft“ zurückzusetzen', async () => {
        const holen = vi.spyOn(OfflineService, 'getZeitkontoStatus')
            .mockResolvedValueOnce({ status: nichtEingerichtet, ausCache: false })
            .mockResolvedValueOnce({ status: eingerichtet, ausCache: false })
            .mockResolvedValueOnce({ status: nichtEingerichtet, ausCache: false })

        const { result, rerender } = renderHook(({ ausloeser }) => useZeitkontoStatus(ausloeser), {
            initialProps: { ausloeser: 1 },
        })
        await waitFor(() => expect(result.current.gesperrt).toBe(true))

        rerender({ ausloeser: 2 })
        expect(result.current.geprueft).toBe(true)
        await waitFor(() => expect(result.current.gesperrt).toBe(false))

        act(() => result.current.neuLaden())
        await waitFor(() => expect(result.current.gesperrt).toBe(true))
        expect(holen).toHaveBeenCalledTimes(3)
    })

    it('eine langsame ältere Antwort überschreibt die neuere nicht', async () => {
        let alteAntwort: (wert: { status: ZeitkontoStatus | null; ausCache: boolean }) => void = () => {}
        vi.spyOn(OfflineService, 'getZeitkontoStatus')
            .mockImplementationOnce(() => new Promise(resolve => { alteAntwort = resolve }))
            .mockResolvedValueOnce({ status: eingerichtet, ausCache: false })

        const { result, rerender } = renderHook(({ ausloeser }) => useZeitkontoStatus(ausloeser), {
            initialProps: { ausloeser: 1 },
        })
        rerender({ ausloeser: 2 })
        await waitFor(() => expect(result.current.eingerichtet).toBe(true))

        await act(async () => alteAntwort({ status: nichtEingerichtet, ausCache: false }))
        expect(result.current.eingerichtet).toBe(true)
        expect(result.current.gesperrt).toBe(false)
    })
})

describe('useZeitkontoStatus – Knopf „Erneut prüfen“', () => {
    beforeEach(() => localStorage.setItem('zeiterfassung_token', 'token-max-mustermann'))
    afterEach(() => { vi.restoreAllMocks(); localStorage.clear() })

    it('meldet „lädt“ nur für die per Knopf angestoßene Prüfung', async () => {
        let antwort: (wert: { status: ZeitkontoStatus | null; ausCache: boolean }) => void = () => {}
        vi.spyOn(OfflineService, 'getZeitkontoStatus')
            .mockResolvedValueOnce({ status: nichtEingerichtet, ausCache: false })
            .mockImplementationOnce(() => new Promise(resolve => { antwort = resolve }))

        const { result } = renderHook(() => useZeitkontoStatus())
        await waitFor(() => expect(result.current.geprueft).toBe(true))
        expect(result.current.laedt).toBe(false)

        act(() => result.current.neuLaden())
        expect(result.current.laedt).toBe(true)

        await act(async () => antwort({ status: eingerichtet, ausCache: false }))
        expect(result.current.laedt).toBe(false)
        expect(result.current.gesperrt).toBe(false)
    })
})
