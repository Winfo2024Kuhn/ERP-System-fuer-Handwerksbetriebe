import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
    starteAufnahme,
    mikrofonWirdUnterstuetzt,
    MikrofonNichtErlaubtError,
} from './audioRecorderService'

interface FakeTrack {
    readyState: MediaStreamTrackState
    stop: () => void
    kind: 'audio'
}

function makeFakeStream(): MediaStream {
    const tracks: FakeTrack[] = [
        {
            readyState: 'live',
            kind: 'audio',
            stop() { this.readyState = 'ended' },
        },
    ]
    return {
        getTracks: () => tracks as unknown as MediaStreamTrack[],
    } as unknown as MediaStream
}

type FakeRecorderState = 'inactive' | 'recording' | 'paused'

/**
 * Fake-MediaRecorder im Teststil von `cameraStreamService.test.ts`: kein echtes
 * DOM, nur so viel Verhalten wie der Service braucht (start/stop/ondataavailable/
 * onstop/mimeType) plus ein statisches `instances`-Register, damit Tests an die
 * vom Service intern erzeugte Instanz herankommen.
 */
class FakeMediaRecorder {
    static instances: FakeMediaRecorder[] = []
    static isTypeSupported: ((type: string) => boolean) | undefined = (type: string) =>
        type === 'audio/webm;codecs=opus'

    state: FakeRecorderState = 'inactive'
    mimeType: string
    stream: MediaStream
    ondataavailable: ((event: { data: Blob }) => void) | null = null
    onstop: (() => void) | null = null

    constructor(stream: MediaStream, options?: { mimeType?: string }) {
        this.stream = stream
        this.mimeType = options?.mimeType ?? ''
        FakeMediaRecorder.instances.push(this)
    }

    start(): void {
        this.state = 'recording'
    }

    stop(): void {
        this.state = 'inactive'
        this.onstop?.()
    }
}

describe('audioRecorderService', () => {
    let getUserMedia: ReturnType<typeof vi.fn>

    beforeEach(() => {
        FakeMediaRecorder.instances = []
        FakeMediaRecorder.isTypeSupported = (type: string) => type === 'audio/webm;codecs=opus'
        getUserMedia = vi.fn(async () => makeFakeStream())
        vi.stubGlobal('navigator', {
            mediaDevices: { getUserMedia },
        })
        vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
    })

    afterEach(() => {
        vi.unstubAllGlobals()
    })

    describe('mikrofonWirdUnterstuetzt', () => {
        it('ist true, wenn getUserMedia und MediaRecorder vorhanden sind', () => {
            expect(mikrofonWirdUnterstuetzt()).toBe(true)
        })

        it('ist false, wenn MediaRecorder fehlt', () => {
            vi.stubGlobal('MediaRecorder', undefined)
            expect(mikrofonWirdUnterstuetzt()).toBe(false)
        })

        it('ist false, wenn getUserMedia fehlt', () => {
            vi.stubGlobal('navigator', { mediaDevices: {} })
            expect(mikrofonWirdUnterstuetzt()).toBe(false)
        })
    })

    describe('starteAufnahme / stoppe', () => {
        it('liefert einen Blob mit den gesammelten Chunks und dem gemeldeten mimeType', async () => {
            const sitzung = await starteAufnahme()
            const recorder = FakeMediaRecorder.instances[0]
            const chunk = new Blob(['hallo'], { type: 'audio/webm' })
            recorder.ondataavailable?.({ data: chunk })

            const blob = await sitzung.stoppe()

            expect(blob.size).toBe(chunk.size)
            expect(blob.type).toBe(sitzung.mimeType)
        })

        it('gibt nach stoppe() jeden Mikrofon-Track sofort frei (iOS-Punkt verschwindet)', async () => {
            const sitzung = await starteAufnahme()
            const stream = FakeMediaRecorder.instances[0].stream

            await sitzung.stoppe()

            stream.getTracks().forEach(track => {
                expect(track.readyState).toBe('ended')
            })
        })

        it('zweiter Aufruf von stoppe() liefert dieselbe Promise, ohne den Recorder erneut zu stoppen', async () => {
            const sitzung = await starteAufnahme()
            const recorder = FakeMediaRecorder.instances[0]
            const stopSpy = vi.spyOn(recorder, 'stop')

            const erste = sitzung.stoppe()
            const zweite = sitzung.stoppe()

            expect(zweite).toBe(erste)
            await erste
            expect(stopSpy).toHaveBeenCalledTimes(1)
        })

        it('fordert bei zwei Aufnahmen hintereinander getUserMedia zweimal an (kein Stream-Cache)', async () => {
            const erste = await starteAufnahme()
            await erste.stoppe()

            await starteAufnahme()

            expect(getUserMedia).toHaveBeenCalledTimes(2)
        })

        it('setzt gestartetAm auf einen aktuellen Zeitstempel', async () => {
            const vorher = Date.now()
            const sitzung = await starteAufnahme()
            const nachher = Date.now()

            expect(sitzung.gestartetAm).toBeGreaterThanOrEqual(vorher)
            expect(sitzung.gestartetAm).toBeLessThanOrEqual(nachher)
        })

        it('waehlt den ersten von MediaRecorder unterstuetzten Inhaltstyp', async () => {
            const sitzung = await starteAufnahme()
            expect(sitzung.mimeType).toBe('audio/webm;codecs=opus')
        })

        it('faellt auf audio/webm zurueck, wenn MediaRecorder.isTypeSupported fehlt (iOS/Safari)', async () => {
            FakeMediaRecorder.isTypeSupported = undefined

            const sitzung = await starteAufnahme()

            expect(sitzung.mimeType).toBe('audio/webm')
        })
    })

    describe('brichAb', () => {
        it('stoppt die Tracks und liefert keinen Blob', async () => {
            const sitzung = await starteAufnahme()
            const stream = FakeMediaRecorder.instances[0].stream

            const ergebnis = sitzung.brichAb()

            expect(ergebnis).toBeUndefined()
            stream.getTracks().forEach(track => {
                expect(track.readyState).toBe('ended')
            })
        })

        it('ist mehrfach aufrufbar, ohne zu werfen', async () => {
            const sitzung = await starteAufnahme()

            expect(() => {
                sitzung.brichAb()
                sitzung.brichAb()
            }).not.toThrow()
        })
    })

    describe('Berechtigungsfehler', () => {
        it('wandelt NotAllowedError in MikrofonNichtErlaubtError um', async () => {
            const fehler = Object.assign(new Error('abgelehnt'), { name: 'NotAllowedError' })
            getUserMedia.mockRejectedValueOnce(fehler)

            await expect(starteAufnahme()).rejects.toBeInstanceOf(MikrofonNichtErlaubtError)
        })

        it('wandelt PermissionDeniedError in MikrofonNichtErlaubtError um', async () => {
            const fehler = Object.assign(new Error('abgelehnt'), { name: 'PermissionDeniedError' })
            getUserMedia.mockRejectedValueOnce(fehler)

            await expect(starteAufnahme()).rejects.toBeInstanceOf(MikrofonNichtErlaubtError)
        })

        it('wirft andere Fehler unveraendert weiter', async () => {
            const fehler = new Error('Geraet belegt')
            getUserMedia.mockRejectedValueOnce(fehler)

            await expect(starteAufnahme()).rejects.toBe(fehler)
        })
    })

    describe('Mikrofon-Freigabe, wenn nach getUserMedia noch etwas wirft', () => {
        it('gibt das Mikrofon frei und wirft den Originalfehler unveraendert, wenn der MediaRecorder-Konstruktor wirft', async () => {
            const konstruktorFehler = new Error('NotSupportedError: Konfiguration nicht unterstuetzt')
            class WerfenderKonstruktorRecorder {
                static isTypeSupported = () => true
                constructor() {
                    throw konstruktorFehler
                }
            }
            vi.stubGlobal('MediaRecorder', WerfenderKonstruktorRecorder)

            let erzeugterStream: MediaStream | undefined
            getUserMedia.mockImplementationOnce(async () => {
                erzeugterStream = makeFakeStream()
                return erzeugterStream
            })

            await expect(starteAufnahme()).rejects.toBe(konstruktorFehler)

            expect(erzeugterStream).toBeDefined()
            erzeugterStream?.getTracks().forEach(track => {
                expect(track.readyState).toBe('ended')
            })
        })

        it('gibt das Mikrofon frei und wirft den Originalfehler unveraendert, wenn recorder.start() wirft', async () => {
            const startFehler = new Error('InvalidStateError: falscher Zustand')
            class RecorderMitWerfendemStart extends FakeMediaRecorder {
                start(): void {
                    throw startFehler
                }
            }
            vi.stubGlobal('MediaRecorder', RecorderMitWerfendemStart)

            let erzeugterStream: MediaStream | undefined
            getUserMedia.mockImplementationOnce(async () => {
                erzeugterStream = makeFakeStream()
                return erzeugterStream
            })

            await expect(starteAufnahme()).rejects.toBe(startFehler)

            expect(erzeugterStream).toBeDefined()
            erzeugterStream?.getTracks().forEach(track => {
                expect(track.readyState).toBe('ended')
            })
        })

        it('gibt das Mikrofon frei, wenn MediaRecorder global gar nicht existiert (altes iOS)', async () => {
            vi.stubGlobal('MediaRecorder', undefined)

            let erzeugterStream: MediaStream | undefined
            getUserMedia.mockImplementationOnce(async () => {
                erzeugterStream = makeFakeStream()
                return erzeugterStream
            })

            await expect(starteAufnahme()).rejects.toThrow()

            expect(erzeugterStream).toBeDefined()
            erzeugterStream?.getTracks().forEach(track => {
                expect(track.readyState).toBe('ended')
            })
        })
    })
})
