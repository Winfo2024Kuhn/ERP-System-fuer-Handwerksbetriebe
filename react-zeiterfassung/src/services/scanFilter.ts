/**
 * Bildverbesserung für gescannte Seiten, ähnlich den Filtern der Apple-
 * Scanner-App. Läuft komplett auf dem Gerät.
 *
 * „Dokument“: Die Helligkeit des Papiers wird kachelweise geschätzt (die
 * hellsten Pixel einer Kachel sind Papier, nicht Schrift) und jedes Pixel
 * durch diesen Hintergrund geteilt. Das macht das Papier gleichmäßig weiß,
 * entfernt Schatten und Lichtverläufe und gleicht pro Farbkanal einen
 * Farbstich (Kunstlicht) aus. Farben wie Stempel oder Logos bleiben.
 * „Schwarz-Weiß“: dasselbe, danach Graustufen mit steiler Kontrastkurve.
 */

export type ScanFilter = 'dokument' | 'schwarzweiss' | 'original'

export const SCAN_FILTER: ReadonlyArray<{ wert: ScanFilter; label: string }> = [
    { wert: 'dokument', label: 'Dokument' },
    { wert: 'schwarzweiss', label: 'Schwarz-Weiß' },
    { wert: 'original', label: 'Original' },
]

export const STANDARD_SCAN_FILTER: ScanFilter = 'dokument'

export interface Pixelbild {
    data: Uint8ClampedArray
    width: number
    height: number
}

/** Anteil der hellsten Pixel einer Kachel, die als Papier gelten. */
const PAPIER_ANTEIL = 0.15
/** Werte unterhalb dieses Anteils des typischen Papiers gelten nicht als Papier (Fotos, schwarze Flächen). */
const MIN_HINTERGRUND_ANTEIL = 0.55

/**
 * Liefert neue RGBA-Pixel; die Eingabe bleibt unverändert.
 */
export function wendeScanFilterAn(bild: Pixelbild, filter: ScanFilter): Uint8ClampedArray {
    if (filter === 'original') return new Uint8ClampedArray(bild.data)
    const hell = gleicheBeleuchtungAus(bild)
    if (filter === 'dokument') return hell
    return zuSchwarzWeiss(hell)
}

function luminanz(r: number, g: number, b: number): number {
    return 0.299 * r + 0.587 * g + 0.114 * b
}

/**
 * Schätzt pro Kachel die Papierfarbe, glättet das Raster und teilt jedes
 * Pixel durch die (bilinear interpolierte) Papierfarbe an seiner Stelle.
 */
function gleicheBeleuchtungAus({ data, width, height }: Pixelbild): Uint8ClampedArray {
    const kachel = Math.max(8, Math.round(Math.min(width, height) / 24))
    const spalten = Math.ceil(width / kachel)
    const zeilen = Math.ceil(height / kachel)
    const raster = new Float32Array(spalten * zeilen * 3)

    const histogramm = new Uint32Array(256)
    for (let ky = 0; ky < zeilen; ky++) {
        for (let kx = 0; kx < spalten; kx++) {
            const x0 = kx * kachel
            const y0 = ky * kachel
            const x1 = Math.min(width, x0 + kachel)
            const y1 = Math.min(height, y0 + kachel)
            histogramm.fill(0)
            for (let y = y0; y < y1; y++) {
                for (let x = x0; x < x1; x++) {
                    const i = (y * width + x) * 4
                    histogramm[Math.round(luminanz(data[i], data[i + 1], data[i + 2]))]++
                }
            }
            // Schwelle: ab hier beginnen die hellsten PAPIER_ANTEIL Pixel
            const ziel = (x1 - x0) * (y1 - y0) * PAPIER_ANTEIL
            let schwelle = 255
            for (let summe = 0; schwelle > 0; schwelle--) {
                summe += histogramm[schwelle]
                if (summe >= ziel) break
            }
            let r = 0, g = 0, b = 0, n = 0
            for (let y = y0; y < y1; y++) {
                for (let x = x0; x < x1; x++) {
                    const i = (y * width + x) * 4
                    if (Math.round(luminanz(data[i], data[i + 1], data[i + 2])) >= schwelle) {
                        r += data[i]; g += data[i + 1]; b += data[i + 2]; n++
                    }
                }
            }
            const k = (ky * spalten + kx) * 3
            raster[k] = r / n
            raster[k + 1] = g / n
            raster[k + 2] = b / n
        }
    }

    const geglaettet = glaetteRaster(raster, spalten, zeilen)

    // Kacheln ohne Papier (dunkles Foto, schwarzer Balken) nicht aufhellen:
    // Untergrenze relativ zum typischen Papier der ganzen Seite.
    const typisch = typischesPapier(geglaettet)
    for (let k = 0; k < geglaettet.length; k += 3) {
        for (let c = 0; c < 3; c++) {
            geglaettet[k + c] = Math.max(geglaettet[k + c], typisch[c] * MIN_HINTERGRUND_ANTEIL, 1)
        }
    }

    const aus = new Uint8ClampedArray(data.length)
    const kurve = dokumentKurve()
    for (let y = 0; y < height; y++) {
        // Kachelmittelpunkte als Stützstellen der Interpolation
        const fy = Math.min(Math.max((y + 0.5) / kachel - 0.5, 0), zeilen - 1)
        const ky0 = Math.floor(fy)
        const ky1 = Math.min(ky0 + 1, zeilen - 1)
        const wy = fy - ky0
        for (let x = 0; x < width; x++) {
            const fx = Math.min(Math.max((x + 0.5) / kachel - 0.5, 0), spalten - 1)
            const kx0 = Math.floor(fx)
            const kx1 = Math.min(kx0 + 1, spalten - 1)
            const wx = fx - kx0
            const a = (ky0 * spalten + kx0) * 3
            const b = (ky0 * spalten + kx1) * 3
            const c = (ky1 * spalten + kx0) * 3
            const d = (ky1 * spalten + kx1) * 3
            const i = (y * width + x) * 4
            for (let ch = 0; ch < 3; ch++) {
                const oben = geglaettet[a + ch] * (1 - wx) + geglaettet[b + ch] * wx
                const unten = geglaettet[c + ch] * (1 - wx) + geglaettet[d + ch] * wx
                const hintergrund = oben * (1 - wy) + unten * wy
                const wert = Math.min(255, Math.round((data[i + ch] / hintergrund) * 255))
                aus[i + ch] = kurve[wert]
            }
            aus[i + 3] = data[i + 3]
        }
    }
    return aus
}

/** 3×3-Median je Kanal (entfernt Ausreißer-Kacheln), danach 3×3-Mittelwert. */
function glaetteRaster(raster: Float32Array, spalten: number, zeilen: number): Float32Array {
    const median = new Float32Array(raster.length)
    const mittel = new Float32Array(raster.length)
    const nachbarn: number[] = []
    const fuerJedeZelle = (quelle: Float32Array, ziel: Float32Array, wert: (werte: number[]) => number) => {
        for (let y = 0; y < zeilen; y++) {
            for (let x = 0; x < spalten; x++) {
                for (let c = 0; c < 3; c++) {
                    nachbarn.length = 0
                    for (let dy = -1; dy <= 1; dy++) {
                        for (let dx = -1; dx <= 1; dx++) {
                            const nx = x + dx
                            const ny = y + dy
                            if (nx >= 0 && ny >= 0 && nx < spalten && ny < zeilen) {
                                nachbarn.push(quelle[(ny * spalten + nx) * 3 + c])
                            }
                        }
                    }
                    ziel[(y * spalten + x) * 3 + c] = wert(nachbarn)
                }
            }
        }
    }
    fuerJedeZelle(raster, median, werte => {
        werte.sort((p, q) => p - q)
        return werte[werte.length >> 1]
    })
    fuerJedeZelle(median, mittel, werte => werte.reduce((s, w) => s + w, 0) / werte.length)
    return mittel
}

/** Obere Quartile der Kachelfarben je Kanal: so hell ist das Papier typischerweise. */
function typischesPapier(raster: Float32Array): [number, number, number] {
    const ergebnis: [number, number, number] = [255, 255, 255]
    const anzahl = raster.length / 3
    for (let c = 0; c < 3; c++) {
        const werte = new Float32Array(anzahl)
        for (let k = 0; k < anzahl; k++) werte[k] = raster[k * 3 + c]
        werte.sort()
        ergebnis[c] = werte[Math.floor(anzahl * 0.75)]
    }
    return ergebnis
}

/**
 * Tonwertkurve nach dem Ausgleich: fast-weißes Papier wird reinweiß,
 * Schrift etwas kräftiger. Als Tabelle, damit pro Pixel nur nachgeschlagen wird.
 */
function dokumentKurve(): Uint8ClampedArray {
    const tabelle = new Uint8ClampedArray(256)
    const schwarz = 25
    const weiss = 225
    for (let v = 0; v < 256; v++) {
        const t = Math.min(1, Math.max(0, (v - schwarz) / (weiss - schwarz)))
        tabelle[v] = Math.round(Math.pow(t, 1.25) * 255)
    }
    return tabelle
}

/** Graustufen mit steiler S-Kurve: klare Schrift, weißes Papier, weiche Kanten. */
function zuSchwarzWeiss(rgba: Uint8ClampedArray): Uint8ClampedArray {
    const aus = new Uint8ClampedArray(rgba.length)
    const kurve = new Uint8ClampedArray(256)
    for (let v = 0; v < 256; v++) {
        kurve[v] = Math.round(255 / (1 + Math.exp(-(v / 255 - 0.6) * 16)))
    }
    for (let i = 0; i < rgba.length; i += 4) {
        const grau = kurve[Math.round(luminanz(rgba[i], rgba[i + 1], rgba[i + 2]))]
        aus[i] = aus[i + 1] = aus[i + 2] = grau
        aus[i + 3] = rgba[i + 3]
    }
    return aus
}
