import { beforeEach, describe, expect, it, vi } from 'vitest'

const pdfMock = vi.hoisted(() => ({
    konstruktor: vi.fn(),
    addPage: vi.fn(),
    addImage: vi.fn(),
    output: vi.fn(() => new Blob(['%PDF'], { type: 'application/pdf' })),
}))

vi.mock('jspdf', () => ({
    jsPDF: class {
        constructor(optionen: unknown) { pdfMock.konstruktor(optionen) }
        addPage = pdfMock.addPage
        addImage = pdfMock.addImage
        output = pdfMock.output
    },
}))

import { erstelleScanPdf, seitenGeometrie } from './scanPdf'

async function alsDataUrl(blob: Blob): Promise<string> {
    return `data:${blob.type};base64,${btoa(await blob.text())}`
}

beforeEach(() => {
    vi.clearAllMocks()
})

describe('seitenGeometrie', () => {
    it('legt Hochformat-Seiten mit 297 mm Höhe und passender Breite an', () => {
        expect(seitenGeometrie(1000, 2000)).toEqual({ format: [148.5, 297], orientation: 'p', bildBreite: 148.5, bildHoehe: 297 })
    })

    it('gibt das Format auch bei Querformat in Hochformat-Reihenfolge an', () => {
        expect(seitenGeometrie(2000, 1000)).toEqual({ format: [148.5, 297], orientation: 'l', bildBreite: 297, bildHoehe: 148.5 })
    })

    it('behandelt quadratische Seiten als Querformat', () => {
        expect(seitenGeometrie(500, 500).orientation).toBe('l')
    })
})

describe('erstelleScanPdf', () => {
    it('lehnt ein PDF ohne Seiten ab', async () => {
        await expect(erstelleScanPdf([])).rejects.toThrow('Es gibt noch keine Seite für das PDF.')
    })

    it('legt jede Seite in Reihenfolge mit eigenem Seitenformat an', async () => {
        const seite1 = new Blob(['eins'], { type: 'image/jpeg' })
        const seite2 = new Blob(['zwei'], { type: 'image/jpeg' })

        const datei = await erstelleScanPdf([
            { bild: seite1, breite: 1000, hoehe: 2000 },
            { bild: seite2, breite: 2000, hoehe: 1000 },
        ], new Date('2026-10-13T08:15:30.123Z'))

        expect(pdfMock.konstruktor).toHaveBeenCalledWith({ orientation: 'p', unit: 'mm', format: [148.5, 297] })
        expect(pdfMock.addPage).toHaveBeenCalledWith([148.5, 297], 'l')
        expect(pdfMock.addImage).toHaveBeenNthCalledWith(1, await alsDataUrl(seite1), 'JPEG', 0, 0, 148.5, 297)
        expect(pdfMock.addImage).toHaveBeenNthCalledWith(2, await alsDataUrl(seite2), 'JPEG', 0, 0, 297, 148.5)
        expect(datei.name).toBe('Scan_2026-10-13T08-15-30-123Z.pdf')
        expect(datei.type).toBe('application/pdf')
    })

    it('liest die Seiten nacheinander statt alle gleichzeitig', async () => {
        const reihenfolge: string[] = []
        const original = FileReader.prototype.readAsDataURL
        const leser = vi.spyOn(FileReader.prototype, 'readAsDataURL').mockImplementation(function (this: FileReader, blob: Blob) {
            reihenfolge.push(`start ${blob.size}`)
            this.addEventListener('loadend', () => reihenfolge.push(`ende ${blob.size}`))
            original.call(this, blob)
        })
        await erstelleScanPdf([
            { bild: new Blob(['a']), breite: 10, hoehe: 20 },
            { bild: new Blob(['bb']), breite: 10, hoehe: 20 },
        ])
        expect(reihenfolge).toEqual(['start 1', 'ende 1', 'start 2', 'ende 2'])
        leser.mockRestore()
    })

    it('lehnt eine Seite ohne gültige Größe ab', async () => {
        await expect(erstelleScanPdf([{ bild: new Blob(['x']), breite: 0, hoehe: 20 }])).rejects.toThrow('Seite 1 hat keine gültige Größe.')
    })

    it('meldet, welche Seite nicht gelesen werden konnte', async () => {
        const leser = vi.spyOn(FileReader.prototype, 'readAsDataURL').mockImplementation(function (this: FileReader) {
            this.onerror?.(new ProgressEvent('error') as ProgressEvent<FileReader>)
        })
        await expect(erstelleScanPdf([{ bild: new Blob(['x']), breite: 10, hoehe: 20 }])).rejects.toThrow('Seite 1 konnte nicht gelesen werden.')
        leser.mockRestore()
    })
})
