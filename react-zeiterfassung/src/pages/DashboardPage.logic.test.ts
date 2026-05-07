import { describe, expect, it } from 'vitest'
import { shouldIncludeOfflineCompletedMinutes } from './DashboardPage.logic'

describe('DashboardPage heute_gearbeitet merge logic', () => {
    it('nutzt offline completed minutes wenn nur Cache verfügbar ist', () => {
        expect(shouldIncludeOfflineCompletedMinutes(true, 0)).toBe(true)
    })

    it('nutzt offline completed minutes solange unsynced entries existieren', () => {
        expect(shouldIncludeOfflineCompletedMinutes(false, 2)).toBe(true)
    })

    it('nutzt offline completed minutes NICHT bei frischen Serverdaten ohne Pending-Queue', () => {
        expect(shouldIncludeOfflineCompletedMinutes(false, 0)).toBe(false)
    })
})