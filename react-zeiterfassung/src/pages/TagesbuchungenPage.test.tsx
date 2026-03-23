import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import TagesbuchungenPage from './TagesbuchungenPage'
import { OfflineService } from '../services/OfflineService'

vi.mock('../services/OfflineService', () => ({
    OfflineService: {
        getTagesbuchungen: vi.fn(),
        getPendingEntries: vi.fn(),
        getProjekte: vi.fn(),
        getArbeitsgaenge: vi.fn(),
    },
}))

const mockedOfflineService = vi.mocked(OfflineService)

const toDateKey = (date: Date): string => {
    const year = date.getFullYear()
    const month = `${date.getMonth() + 1}`.padStart(2, '0')
    const day = `${date.getDate()}`.padStart(2, '0')
    return `${year}-${month}-${day}`
}

const buildLocalIso = (baseDate: Date, hours: number, minutes: number): string => {
    return new Date(
        baseDate.getFullYear(),
        baseDate.getMonth(),
        baseDate.getDate(),
        hours,
        minutes,
        0,
        0,
    ).toISOString()
}

function createMemoryStorage(): Storage {
    const store = new Map<string, string>()

    return {
        get length() {
            return store.size
        },
        clear() {
            store.clear()
        },
        getItem(key: string) {
            return store.has(key) ? store.get(key)! : null
        },
        key(index: number) {
            return Array.from(store.keys())[index] ?? null
        },
        removeItem(key: string) {
            store.delete(key)
        },
        setItem(key: string, value: string) {
            store.set(key, value)
        },
    }
}

describe('TagesbuchungenPage', () => {
    beforeEach(() => {
        vi.stubGlobal('localStorage', createMemoryStorage())

        localStorage.clear()
        localStorage.setItem('zeiterfassung_token', 'tok-123')

        mockedOfflineService.getProjekte.mockResolvedValue([
            { id: 77, name: 'Offline Projekt', projektNummer: 'P-77', kundenName: 'Musterkunde' },
            { id: 88, name: 'Projekt Dublette', projektNummer: 'P-88', kundenName: 'Dedupe GmbH' },
        ])
        mockedOfflineService.getArbeitsgaenge.mockResolvedValue([
            { id: 11, beschreibung: 'Montage' },
            { id: 12, beschreibung: 'Service' },
        ])
        mockedOfflineService.getPendingEntries.mockResolvedValue([])
        mockedOfflineService.getTagesbuchungen.mockResolvedValue({ buchungen: [], fromCache: false })
    })

    afterEach(() => {
        vi.clearAllMocks()
        localStorage.clear()
        vi.unstubAllGlobals()
    })

    it('zeigt gecachte Serverdaten und abgeschlossene Offline-Buchungen für den ausgewählten Tag', async () => {
        const user = userEvent.setup()
        const today = new Date()
        const previousDay = new Date(today)
        previousDay.setDate(previousDay.getDate() - 1)
        const previousDayKey = toDateKey(previousDay)

        mockedOfflineService.getTagesbuchungen.mockImplementation(async (_token, datum) => {
            if (datum === previousDayKey) {
                return {
                    buchungen: [
                        {
                            id: 1,
                            startMinuten: 480,
                            endeMinuten: 540,
                            dauerMinuten: 60,
                            projektId: 99,
                            projektNummer: 'P-99',
                            projektName: 'Server Projekt',
                            kundenName: 'Serverkunde',
                            arbeitsgangId: 12,
                            taetigkeit: 'Service',
                        },
                    ],
                    fromCache: true,
                }
            }

            return { buchungen: [], fromCache: false }
        })

        mockedOfflineService.getPendingEntries.mockResolvedValue([
            {
                id: 'offline-start',
                type: 'start',
                data: { projektId: 77, arbeitsgangId: 11 },
                timestamp: buildLocalIso(today, 10, 0),
                originalTime: buildLocalIso(previousDay, 9, 0),
            },
            {
                id: 'offline-stop',
                type: 'stop',
                data: {},
                timestamp: buildLocalIso(today, 10, 5),
                originalTime: buildLocalIso(previousDay, 10, 15),
            },
        ])

        render(
            <MemoryRouter>
                <TagesbuchungenPage />
            </MemoryRouter>
        )

        await user.click(screen.getByLabelText('Vorheriger Tag'))

        expect(await screen.findByText('P-99')).toBeInTheDocument()
        expect(await screen.findByText('P-77')).toBeInTheDocument()
        expect(screen.getByText('Offline')).toBeInTheDocument()
        expect(screen.getByText('09:00 - 10:15')).toBeInTheDocument()
        expect(screen.getByText('1h 15min')).toBeInTheDocument()
    })

    it('zeigt Buchungen aus Cache und Pending-Queue nicht doppelt an', async () => {
        const today = new Date()

        mockedOfflineService.getTagesbuchungen.mockResolvedValue({
            buchungen: [
                {
                    id: 5,
                    startMinuten: 480,
                    endeMinuten: 540,
                    dauerMinuten: 60,
                    projektId: 88,
                    projektNummer: 'P-88',
                    projektName: 'Projekt Dublette',
                    kundenName: 'Dedupe GmbH',
                    arbeitsgangId: 12,
                    taetigkeit: 'Service',
                },
            ],
            fromCache: false,
        })

        mockedOfflineService.getPendingEntries.mockResolvedValue([
            {
                id: 'start-dup',
                type: 'start',
                data: { projektId: 88, arbeitsgangId: 12 },
                timestamp: buildLocalIso(today, 8, 0),
                originalTime: buildLocalIso(today, 8, 0),
            },
            {
                id: 'stop-dup',
                type: 'stop',
                data: {},
                timestamp: buildLocalIso(today, 9, 0),
                originalTime: buildLocalIso(today, 9, 0),
            },
        ])

        render(
            <MemoryRouter>
                <TagesbuchungenPage />
            </MemoryRouter>
        )

        await waitFor(() => {
            expect(screen.getAllByText('P-88')).toHaveLength(1)
        })
    })
})