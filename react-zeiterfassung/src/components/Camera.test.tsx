import { createRef } from 'react'
import { render, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Camera, { type CameraHandle } from './Camera'
import { canvasZuBlob, gibCanvasFrei } from '../services/scanBildHilfen'

const stream = vi.hoisted(() => ({ getVideoTracks: () => [], getTracks: () => [] }))
vi.mock('../services/cameraStreamService', () => ({
    acquireCameraStream: vi.fn(async () => stream),
    getActiveCameraStream: vi.fn(() => null),
}))
vi.mock('../services/scanBildHilfen', () => ({
    canvasZuBlob: vi.fn(async () => new Blob(['foto'], { type: 'image/jpeg' })),
    gibCanvasFrei: vi.fn(),
}))

const drawImage = vi.fn()

function setzeVideo(video: HTMLVideoElement, readyState: number) {
    Object.defineProperty(video, 'readyState', { configurable: true, value: readyState })
    Object.defineProperty(video, 'videoWidth', { configurable: true, value: 1920 })
    Object.defineProperty(video, 'videoHeight', { configurable: true, value: 1080 })
}

beforeEach(() => {
    vi.clearAllMocks()
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined)
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({ drawImage } as unknown as CanvasRenderingContext2D)
})
afterEach(() => vi.restoreAllMocks())

function zeigeKamera() {
    const ref = createRef<CameraHandle>()
    const { container } = render(<Camera ref={ref} videoConstraints={{ facingMode: 'environment' }} screenshotQuality={0.9} />)
    return { ref, video: container.querySelector('video') as HTMLVideoElement }
}

describe('Camera.getScreenshotBlob', () => {
    it('liefert das aktuelle Bild als JPEG in voller Auflösung und gibt die Zwischen-Canvas frei', async () => {
        const { ref, video } = zeigeKamera()
        await waitFor(() => expect(video.srcObject).toBe(stream))
        setzeVideo(video, 4)

        const blob = await ref.current!.getScreenshotBlob()

        expect(blob).toBeInstanceOf(Blob)
        expect(drawImage).toHaveBeenCalledWith(video, 0, 0, 1920, 1080)
        expect(canvasZuBlob).toHaveBeenCalledWith(expect.objectContaining({ width: 1920, height: 1080 }), 0.9)
        expect(gibCanvasFrei).toHaveBeenCalledTimes(1)
    })

    it('liefert nichts, solange das Video noch nicht läuft', async () => {
        const { ref, video } = zeigeKamera()
        setzeVideo(video, 1)
        expect(await ref.current!.getScreenshotBlob()).toBeNull()
        expect(canvasZuBlob).not.toHaveBeenCalled()
    })

    it('liefert nichts und gibt die Canvas trotzdem frei, wenn das Speichern scheitert', async () => {
        vi.mocked(canvasZuBlob).mockRejectedValueOnce(new Error('kein Bild'))
        const { ref, video } = zeigeKamera()
        setzeVideo(video, 4)
        expect(await ref.current!.getScreenshotBlob()).toBeNull()
        expect(gibCanvasFrei).toHaveBeenCalledTimes(1)
    })
})
