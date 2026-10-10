import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  FotoUploadFehler,
  fehlerAusStatus,
  fotoErgebnisMeldung,
  fotoFehlerText,
  fotoVerkleinern,
  fortschrittText,
  ladeFotosEinzelnHoch,
  sendeFoto,
  zielGroesse,
} from './fotoUpload'

// Dummy-Daten (DSGVO): nur Mustermann-Projekte und leere Bildbytes.
const foto = (name: string, groesse = 10, typ = 'image/jpeg') =>
  new File([new Uint8Array(groesse)], name, { type: typ })

describe('fehlerAusStatus', () => {
  it('übersetzt 413 in "Foto zu groß", auch ohne JSON-Body', () => {
    const fehler = fehlerAusStatus(413)
    expect(fehler).toBeInstanceOf(FotoUploadFehler)
    expect(fehler.grund).toBe('zu-gross')
    expect(fehler.message).toBe('Foto zu groß')
  })

  it.each([
    [401, 'anmeldung'],
    [403, 'keine-berechtigung'],
    [429, 'zu-viele-anfragen'],
    [400, 'ungueltig'],
    [415, 'ungueltig'],
    [500, 'server'],
    [502, 'server'],
    [418, 'unbekannt'],
  ])('Status %i → %s', (status, grund) => {
    expect(fehlerAusStatus(status).grund).toBe(grund)
  })
})

describe('fortschrittText', () => {
  it('nennt Nummer und Gesamtzahl', () => {
    expect(fortschrittText(3, 8)).toBe('Foto 3 von 8 wird hochgeladen')
  })
})

describe('ladeFotosEinzelnHoch', () => {
  it('sendet jedes Foto genau einmal, nacheinander und meldet den Fortschritt', async () => {
    const fotos = ['a', 'b', 'c'].map(n => foto(`${n}.jpg`))
    const reihenfolge: string[] = []
    let laufend = 0
    let maxParallel = 0
    const fortschritt: string[] = []

    const ergebnis = await ladeFotosEinzelnHoch(fotos, {
      senden: async f => {
        laufend++
        maxParallel = Math.max(maxParallel, laufend)
        await Promise.resolve()
        reihenfolge.push(f.name)
        laufend--
      },
      beiStart: (nummer, gesamt) => fortschritt.push(fortschrittText(nummer, gesamt)),
    })

    expect(reihenfolge).toEqual(['a.jpg', 'b.jpg', 'c.jpg'])
    expect(maxParallel).toBe(1)
    expect(fortschritt).toEqual([
      'Foto 1 von 3 wird hochgeladen',
      'Foto 2 von 3 wird hochgeladen',
      'Foto 3 von 3 wird hochgeladen',
    ])
    expect(ergebnis.erfolgreich).toEqual(fotos)
    expect(ergebnis.fehlgeschlagen).toEqual([])
    expect(ergebnis.nichtVersucht).toEqual([])
  })

  it('meldet jeden Erfolg sofort, damit nichts doppelt gesendet wird', async () => {
    const fotos = [foto('a.jpg'), foto('b.jpg')]
    const erledigt: string[] = []
    const beimSenden: string[][] = []
    await ladeFotosEinzelnHoch(fotos, {
      senden: async () => {
        beimSenden.push([...erledigt])
      },
      beiErfolg: f => erledigt.push(f.name),
    })
    // Beim zweiten Foto ist das erste schon als erledigt gemeldet.
    expect(beimSenden).toEqual([[], ['a.jpg']])
    expect(erledigt).toEqual(['a.jpg', 'b.jpg'])
  })

  it('macht nach einem zu großen Foto mit dem nächsten weiter', async () => {
    const fotos = [foto('a.jpg'), foto('riesig.jpg'), foto('c.jpg')]
    const gesendet: string[] = []
    const fehler: string[] = []
    const ergebnis = await ladeFotosEinzelnHoch(fotos, {
      senden: async f => {
        gesendet.push(f.name)
        if (f.name === 'riesig.jpg') throw fehlerAusStatus(413)
      },
      beiFehler: (f, e) => fehler.push(`${f.name}: ${fotoFehlerText(e.grund)}`),
    })

    expect(gesendet).toEqual(['a.jpg', 'riesig.jpg', 'c.jpg'])
    expect(fehler).toEqual(['riesig.jpg: Foto zu groß'])
    expect(ergebnis.erfolgreich.map(f => f.name)).toEqual(['a.jpg', 'c.jpg'])
    expect(ergebnis.fehlgeschlagen.map(f => f.item.name)).toEqual(['riesig.jpg'])
    expect(ergebnis.nichtVersucht).toEqual([])
  })

  it('hört bei fehlender Verbindung auf und lässt die übrigen Fotos unberührt', async () => {
    const fotos = [foto('a.jpg'), foto('b.jpg'), foto('c.jpg')]
    const gesendet: string[] = []
    const ergebnis = await ladeFotosEinzelnHoch(fotos, {
      senden: async f => {
        gesendet.push(f.name)
        if (f.name === 'b.jpg') throw new FotoUploadFehler('kein-netz')
      },
    })

    expect(gesendet).toEqual(['a.jpg', 'b.jpg'])
    expect(ergebnis.erfolgreich.map(f => f.name)).toEqual(['a.jpg'])
    expect(ergebnis.fehlgeschlagen.map(f => f.item.name)).toEqual(['b.jpg'])
    expect(ergebnis.nichtVersucht.map(f => f.name)).toEqual(['c.jpg'])
  })

  it('behandelt unerwartete Fehler als "unbekannt", ohne abzubrechen', async () => {
    const ergebnis = await ladeFotosEinzelnHoch([foto('a.jpg'), foto('b.jpg')], {
      senden: async f => {
        if (f.name === 'a.jpg') throw new Error('kaputt')
      },
    })
    expect(ergebnis.fehlgeschlagen[0].fehler.grund).toBe('unbekannt')
    expect(ergebnis.erfolgreich.map(f => f.name)).toEqual(['b.jpg'])
  })

  it.each(['anmeldung', 'keine-berechtigung', 'zu-viele-anfragen'] as const)('hört bei "%s" auf, weil die übrigen Fotos genauso scheitern würden', async grund => {
    const gesendet: string[] = []
    const ergebnis = await ladeFotosEinzelnHoch([foto('a.jpg'), foto('b.jpg')], {
      senden: async f => {
        gesendet.push(f.name)
        throw new FotoUploadFehler(grund)
      },
    })
    expect(gesendet).toEqual(['a.jpg'])
    expect(ergebnis.nichtVersucht.map(f => f.name)).toEqual(['b.jpg'])
  })
})

describe('fotoErgebnisMeldung', () => {
  it('ist leer, wenn alles geklappt hat', () => {
    expect(fotoErgebnisMeldung({ erfolgreich: [1, 2], fehlgeschlagen: [], nichtVersucht: [] })).toBe('')
  })

  it('nennt Anzahl und Grund in Handwerker-Sprache', () => {
    const meldung = fotoErgebnisMeldung({
      erfolgreich: [1, 2, 3, 4, 5, 6],
      fehlgeschlagen: [{ item: 7, fehler: fehlerAusStatus(413) }, { item: 8, fehler: fehlerAusStatus(413) }],
      nichtVersucht: [],
    })
    expect(meldung).toContain('2 Fotos konnten nicht hochgeladen werden')
    expect(meldung).toContain('6 von 8 sind angekommen')
    expect(meldung).toContain('Foto zu groß')
    expect(meldung).toContain('bleiben ausgewählt')
  })

  it('zählt nicht versendete Fotos mit und nutzt die Einzahl', () => {
    const meldung = fotoErgebnisMeldung({
      erfolgreich: [1],
      fehlgeschlagen: [{ item: 2, fehler: new FotoUploadFehler('kein-netz') }],
      nichtVersucht: [],
    })
    expect(meldung).toContain('1 Foto konnte nicht hochgeladen werden')
    expect(meldung).toContain('(1 von 2 ist angekommen)')
    expect(meldung).toContain('Keine Verbindung')
  })

  it('lässt den Hinweis zur Auswahl weg, wenn der Nutzer die Seite verlassen hat', () => {
    const meldung = fotoErgebnisMeldung({ erfolgreich: [1], fehlgeschlagen: [{ item: 2, fehler: fehlerAusStatus(413) }], nichtVersucht: [] }, false)
    expect(meldung).not.toContain('bleiben ausgewählt')
  })

  it('kommt ohne Gründe aus, wenn Fotos nur nicht mehr versucht wurden', () => {
    const meldung = fotoErgebnisMeldung({ erfolgreich: [], fehlgeschlagen: [], nichtVersucht: [1, 2] })
    expect(meldung).toBe('2 Fotos konnten nicht hochgeladen werden (0 von 2 sind angekommen). Die übrigen bleiben ausgewählt.')
  })
})

describe('zielGroesse', () => {
  it('lässt kleine Bilder unverändert', () => {
    expect(zielGroesse(2000, 1500, 2560)).toEqual({ breite: 2000, hoehe: 1500 })
  })

  it('verkleinert die lange Kante auf 2560 px und behält das Seitenverhältnis', () => {
    expect(zielGroesse(4032, 3024, 2560)).toEqual({ breite: 2560, hoehe: 1920 })
    expect(zielGroesse(3024, 4032, 2560)).toEqual({ breite: 1920, hoehe: 2560 })
  })
})

describe('fotoVerkleinern', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  function bitmapMock(breite: number, hoehe: number) {
    const bitmap = { width: breite, height: hoehe, close: vi.fn() }
    vi.stubGlobal('createImageBitmap', vi.fn().mockResolvedValue(bitmap))
    return bitmap
  }

  function canvasMock(blob: Blob | null) {
    const kontext = { fillStyle: '', fillRect: vi.fn(), drawImage: vi.fn() }
    const canvas = {
      width: 0,
      height: 0,
      getContext: vi.fn().mockReturnValue(kontext),
      toBlob: vi.fn((callback: (b: Blob | null) => void) => callback(blob)),
    }
    vi.spyOn(document, 'createElement').mockReturnValue(canvas as unknown as HTMLCanvasElement)
    return canvas
  }

  it('lässt Dateien, die keine verkleinerbaren Fotos sind, unangetastet', async () => {
    const gif = foto('animation.gif', 10, 'image/gif')
    expect(await fotoVerkleinern(gif)).toBe(gif)
  })

  it('lässt das Foto unangetastet, wenn der Browser kein createImageBitmap kennt', async () => {
    vi.stubGlobal('createImageBitmap', undefined)
    const original = foto('IMG_0003.jpg', 9_000_000)
    expect(await fotoVerkleinern(original)).toBe(original)
  })

  it('nimmt das Original, wenn kein Zeichenkontext zu bekommen ist', async () => {
    const bitmap = bitmapMock(4032, 3024)
    const canvas = canvasMock(null)
    canvas.getContext.mockReturnValue(null)
    const original = foto('IMG_0004.jpg', 9_000_000)
    expect(await fotoVerkleinern(original)).toBe(original)
    expect(bitmap.close).toHaveBeenCalled()
  })

  it('gibt Dateien ohne Namensteil den Namen "foto.jpg"', async () => {
    bitmapMock(4032, 3024)
    canvasMock(new Blob([new Uint8Array(100)], { type: 'image/jpeg' }))
    expect((await fotoVerkleinern(foto('.jpeg', 9_000_000))).name).toBe('foto.jpg')
  })

  it('liest die Ausrichtung ausdrücklich aus den EXIF-Daten', async () => {
    bitmapMock(1000, 800)
    await fotoVerkleinern(foto('a.jpg', 100))
    expect(createImageBitmap).toHaveBeenCalledWith(expect.any(File), { imageOrientation: 'from-image' })
  })

  it('verkleinert ein großes Foto auf JPEG mit Endung .jpg', async () => {
    const bitmap = bitmapMock(4032, 3024)
    const canvas = canvasMock(new Blob([new Uint8Array(500)], { type: 'image/jpeg' }))
    const original = foto('IMG_0001.HEIC', 9_000_000, 'image/heic')

    const ergebnis = await fotoVerkleinern(original)

    expect(ergebnis).not.toBe(original)
    expect(ergebnis.name).toBe('IMG_0001.jpg')
    expect(ergebnis.type).toBe('image/jpeg')
    expect(ergebnis.size).toBe(500)
    expect(canvas.width).toBe(2560)
    expect(canvas.height).toBe(1920)
    expect(canvas.toBlob).toHaveBeenCalledWith(expect.any(Function), 'image/jpeg', 0.85)
    expect(bitmap.close).toHaveBeenCalled()
  })

  it('lässt ein kleines Foto in passender Größe unverändert', async () => {
    bitmapMock(1600, 1200)
    const klein = foto('klein.jpg', 500_000)
    expect(await fotoVerkleinern(klein)).toBe(klein)
  })

  it('nimmt das Original, wenn der Browser das Bild nicht lesen kann', async () => {
    vi.stubGlobal('createImageBitmap', vi.fn().mockRejectedValue(new Error('nicht lesbar')))
    const original = foto('IMG_0002.heic', 9_000_000, 'image/heic')
    expect(await fotoVerkleinern(original)).toBe(original)
  })

  it('nimmt das Original, wenn das Ergebnis nicht kleiner ist', async () => {
    bitmapMock(2000, 1500)
    canvasMock(new Blob([new Uint8Array(3_000_000)], { type: 'image/jpeg' }))
    const original = foto('gross.jpg', 2_500_000)
    expect(await fotoVerkleinern(original)).toBe(original)
  })
})

describe('sendeFoto', () => {
  const ohneVerkleinern = async (f: File) => f

  it('schickt genau ein Foto im Feld "datei" an die Adresse', async () => {
    const send = vi.fn().mockResolvedValue(new Response(null, { status: 200 }))
    await sendeFoto('/api/projekte/7/dokumente?gruppe=BILDER', foto('a.jpg'), {
      send,
      headers: { 'X-Mitarbeiter-Id': '1' },
      verkleinern: ohneVerkleinern,
    })

    expect(send).toHaveBeenCalledTimes(1)
    const [url, init] = send.mock.calls[0]
    expect(url).toBe('/api/projekte/7/dokumente?gruppe=BILDER')
    expect(init.method).toBe('POST')
    expect(init.headers).toEqual({ 'X-Mitarbeiter-Id': '1' })
    const dateien = (init.body as FormData).getAll('datei')
    expect(dateien).toHaveLength(1)
    expect((dateien[0] as File).name).toBe('a.jpg')
  })

  it('sendet die verkleinerte Fassung', async () => {
    const send = vi.fn().mockResolvedValue(new Response(null, { status: 200 }))
    const klein = foto('klein.jpg', 5)
    await sendeFoto('/x', foto('gross.jpg', 50), { send, verkleinern: async () => klein })
    expect(((send.mock.calls[0][1].body as FormData).get('datei') as File).name).toBe('klein.jpg')
  })

  it('wirft bei 413 "Foto zu groß", auch wenn die Antwort keinen Body hat', async () => {
    const send = vi.fn().mockResolvedValue(new Response('<html>413</html>', { status: 413 }))
    await expect(sendeFoto('/x', foto('a.jpg'), { send, verkleinern: ohneVerkleinern })).rejects.toMatchObject({
      grund: 'zu-gross',
      message: 'Foto zu groß',
    })
  })

  it.each([[500, 'server'], [401, 'anmeldung'], [403, 'keine-berechtigung']])('wirft bei Status %i den Grund %s', async (status, grund) => {
    const send = vi.fn().mockResolvedValue(new Response(null, { status }))
    await expect(sendeFoto('/x', foto('a.jpg'), { send, verkleinern: ohneVerkleinern })).rejects.toMatchObject({ grund })
  })

  it('wirft bei Verbindungsabbruch "Keine Verbindung"', async () => {
    const send = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    await expect(sendeFoto('/x', foto('a.jpg'), { send, verkleinern: ohneVerkleinern })).rejects.toMatchObject({
      grund: 'kein-netz',
    })
  })
})
