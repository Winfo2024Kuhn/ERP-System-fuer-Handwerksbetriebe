import { describe, expect, it } from 'vitest'
import {
    shouldIncludeCurrentSessionMinutes,
    shouldIncludeOfflineCompletedMinutes,
} from './DashboardPage'

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

    it('nutzt aktuelle lokale Session wenn nur Cache verfügbar ist', () => {
        expect(shouldIncludeCurrentSessionMinutes(true, 0, false)).toBe(true)
    })

    it('nutzt aktuelle lokale Session solange unsynced entries existieren', () => {
        expect(shouldIncludeCurrentSessionMinutes(false, 1, false)).toBe(true)
    })

    it('nutzt aktuelle lokale Session während start-sync cooldown', () => {
        expect(shouldIncludeCurrentSessionMinutes(false, 0, true)).toBe(true)
    })

    it('nutzt aktuelle lokale Session NICHT bei frischen Serverdaten ohne Pending oder Cooldown', () => {
        expect(shouldIncludeCurrentSessionMinutes(false, 0, false)).toBe(false)
    })
})