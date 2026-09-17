import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
    leseBenachrichtigungsStatus,
    leseMikrofonStatus,
    leseKameraStatus,
    frageMikrofonAn,
    frageKameraAn,
} from './permissionStatusService'
import { NotificationService } from './NotificationService'
import { getActiveCameraStream, releaseCameraStream } from './cameraStreamService'

interface FakeTrack {
    readyState: MediaStreamTrackState
    stop: () => void
}

function makeFakeTrack(): FakeTrack {
    return {
        readyState: 'live',
        stop() { this.readyState = 'ended' },
    }
}

function makeFakeStream(tracks: FakeTrack[]): MediaStream {
    return {
        getTracks: () => tracks as unknown as MediaStreamTrack[],
        getVideoTracks: () => tracks as unknown as MediaStreamTrack[],
    } as unknown as MediaStream
}

describe('permissionStatusService', () => {
    beforeEach(() => {
        releaseCameraStream()
    })

    afterEach(() => {
        releaseCameraStream()
        vi.restoreAllMocks()
        vi.unstubAllGlobals()
    })

    describe('leseBenachrichtigungsStatus()', () => {
        it('meldet "nicht-verfuegbar", wenn Benachrichtigungen gar nicht unterstuetzt werden', () => {
            vi.spyOn(NotificationService, 'isSupported').mockReturnValue(false)
            expect(leseBenachrichtigungsStatus()).toBe('nicht-verfuegbar')
        })

        it('bildet "granted" auf "erlaubt" ab', () => {
            vi.spyOn(NotificationService, 'isSupported').mockReturnValue(true)
            vi.spyOn(NotificationService, 'getPermissionStatus').mockReturnValue('granted')
            expect(leseBenachrichtigungsStatus()).toBe('erlaubt')
        })

        it('bildet "denied" auf "blockiert" ab', () => {
            vi.spyOn(NotificationService, 'isSupported').mockReturnValue(true)
            vi.spyOn(NotificationService, 'getPermissionStatus').mockReturnValue('denied')
            expect(leseBenachrichtigungsStatus()).toBe('blockiert')
        })

        it('bildet "default" auf "nicht-gefragt" ab', () => {
            vi.spyOn(NotificationService, 'isSupported').mockReturnValue(true)
            vi.spyOn(NotificationService, 'getPermissionStatus').mockReturnValue('default')
            expect(leseBenachrichtigungsStatus()).toBe('nicht-gefragt')
        })

        it('bildet "unsupported" auf "nicht-verfuegbar" ab', () => {
            vi.spyOn(NotificationService, 'isSupported').mockReturnValue(true)
            vi.spyOn(NotificationService, 'getPermissionStatus').mockReturnValue('unsupported')
            expect(leseBenachrichtigungsStatus()).toBe('nicht-verfuegbar')
        })
    })

    describe('leseMikrofonStatus() / leseKameraStatus()', () => {
        it('liefert "erlaubt", wenn permissions.query "granted" liefert', async () => {
            vi.stubGlobal('navigator', {
                mediaDevices: { getUserMedia: vi.fn() },
                permissions: { query: vi.fn().mockResolvedValue({ state: 'granted' }) },
            })
            expect(await leseMikrofonStatus()).toBe('erlaubt')
            expect(await leseKameraStatus()).toBe('erlaubt')
        })

        it('liefert "blockiert", wenn permissions.query "denied" liefert', async () => {
            vi.stubGlobal('navigator', {
                mediaDevices: { getUserMedia: vi.fn() },
                permissions: { query: vi.fn().mockResolvedValue({ state: 'denied' }) },
            })
            expect(await leseMikrofonStatus()).toBe('blockiert')
            expect(await leseKameraStatus()).toBe('blockiert')
        })

        it('liefert "nicht-gefragt", wenn permissions.query "prompt" liefert', async () => {
            vi.stubGlobal('navigator', {
                mediaDevices: { getUserMedia: vi.fn() },
                permissions: { query: vi.fn().mockResolvedValue({ state: 'prompt' }) },
            })
            expect(await leseMikrofonStatus()).toBe('nicht-gefragt')
            expect(await leseKameraStatus()).toBe('nicht-gefragt')
        })

        it('liefert "nicht-gefragt" (NICHT "nicht-verfuegbar"), wenn navigator.permissions ganz fehlt', async () => {
            vi.stubGlobal('navigator', {
                mediaDevices: { getUserMedia: vi.fn() },
            })
            expect(await leseMikrofonStatus()).toBe('nicht-gefragt')
            expect(await leseKameraStatus()).toBe('nicht-gefragt')
        })

        it('liefert "nicht-gefragt", wenn permissions.query wirft', async () => {
            vi.stubGlobal('navigator', {
                mediaDevices: { getUserMedia: vi.fn() },
                permissions: { query: vi.fn().mockRejectedValue(new Error('kaputt')) },
            })
            expect(await leseMikrofonStatus()).toBe('nicht-gefragt')
            expect(await leseKameraStatus()).toBe('nicht-gefragt')
        })

        it('liefert "nicht-gefragt", wenn permissions.query einen unbekannten Wert liefert', async () => {
            vi.stubGlobal('navigator', {
                mediaDevices: { getUserMedia: vi.fn() },
                permissions: { query: vi.fn().mockResolvedValue({ state: 'irgendwas' }) },
            })
            expect(await leseMikrofonStatus()).toBe('nicht-gefragt')
            expect(await leseKameraStatus()).toBe('nicht-gefragt')
        })

        it('liefert "nicht-gefragt", wenn permissions.query mit undefined aufloest', async () => {
            vi.stubGlobal('navigator', {
                mediaDevices: { getUserMedia: vi.fn() },
                permissions: { query: vi.fn().mockResolvedValue(undefined) },
            })
            expect(await leseMikrofonStatus()).toBe('nicht-gefragt')
        })

        it('liefert "nicht-verfuegbar", wenn navigator.mediaDevices ganz fehlt', async () => {
            vi.stubGlobal('navigator', {
                permissions: { query: vi.fn().mockResolvedValue({ state: 'granted' }) },
            })
            expect(await leseMikrofonStatus()).toBe('nicht-verfuegbar')
            expect(await leseKameraStatus()).toBe('nicht-verfuegbar')
        })

        it('liefert "nicht-verfuegbar", wenn getUserMedia auf mediaDevices fehlt', async () => {
            vi.stubGlobal('navigator', {
                mediaDevices: {},
                permissions: { query: vi.fn().mockResolvedValue({ state: 'granted' }) },
            })
            expect(await leseMikrofonStatus()).toBe('nicht-verfuegbar')
        })
    })

    describe('frageMikrofonAn()', () => {
        it('fordert das Mikrofon an und stoppt danach alle Tracks (Kernpruefung)', async () => {
            const trackA = makeFakeTrack()
            const trackB = makeFakeTrack()
            const getUserMedia = vi.fn().mockResolvedValue(makeFakeStream([trackA, trackB]))
            vi.stubGlobal('navigator', { mediaDevices: { getUserMedia } })

            await frageMikrofonAn()

            expect(getUserMedia).toHaveBeenCalledWith({ audio: true })
            expect(trackA.readyState).toBe('ended')
            expect(trackB.readyState).toBe('ended')
        })

        it('stoppt die uebrigen Tracks auch wenn ein Track beim Stoppen wirft', async () => {
            const trackA = makeFakeTrack()
            trackA.stop = () => { throw new Error('Track schon beendet') }
            const trackB = makeFakeTrack()
            const getUserMedia = vi.fn().mockResolvedValue(makeFakeStream([trackA, trackB]))
            vi.stubGlobal('navigator', { mediaDevices: { getUserMedia } })

            await frageMikrofonAn()

            expect(trackB.readyState).toBe('ended')
        })

        it('wirft eine abgelehnte Berechtigung unveraendert weiter, damit die Seite toasten kann', async () => {
            const ablehnung = new Error('Permission denied')
            vi.stubGlobal('navigator', {
                mediaDevices: { getUserMedia: vi.fn().mockRejectedValue(ablehnung) },
            })

            await expect(frageMikrofonAn()).rejects.toBe(ablehnung)
        })
    })

    describe('frageKameraAn()', () => {
        it('fordert die Kamera an und gibt sie danach sofort wieder frei', async () => {
            const track = makeFakeTrack()
            const getUserMedia = vi.fn().mockResolvedValue(makeFakeStream([track]))
            vi.stubGlobal('navigator', { mediaDevices: { getUserMedia } })

            await frageKameraAn()

            expect(getUserMedia).toHaveBeenCalledWith({ video: true })
            expect(getActiveCameraStream()).toBeNull()
            expect(track.readyState).toBe('ended')
        })

        it('wirft eine abgelehnte Berechtigung unveraendert weiter', async () => {
            const ablehnung = new Error('Permission denied')
            vi.stubGlobal('navigator', {
                mediaDevices: { getUserMedia: vi.fn().mockRejectedValue(ablehnung) },
            })

            await expect(frageKameraAn()).rejects.toBe(ablehnung)
            expect(getActiveCameraStream()).toBeNull()
        })
    })
})
