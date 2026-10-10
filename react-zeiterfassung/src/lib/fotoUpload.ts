/**
 * Foto-Upload für die Handy-App: ein Foto pro Anfrage, mit Fortschritt und verständlichen Fehlern.
 *
 * Von außen (Tailscale/Funnel, mobiles Gateway) ist ein Upload auf 25 MiB begrenzt. Mehrere
 * Handyfotos in EINER Multipart-Anfrage sprengen das schnell – deshalb geht jedes Foto einzeln
 * raus und wird vorher auf eine vernünftige Größe gebracht.
 */

export type FotoFehlerGrund =
  | 'zu-gross'
  | 'kein-netz'
  | 'ungueltig'
  | 'anmeldung'
  | 'keine-berechtigung'
  | 'zu-viele-anfragen'
  | 'server'
  | 'unbekannt'

const FEHLER_TEXTE: Record<FotoFehlerGrund, string> = {
  'zu-gross': 'Foto zu groß',
  'kein-netz': 'Keine Verbindung',
  'ungueltig': 'Bildformat nicht unterstützt',
  'anmeldung': 'Bitte neu anmelden',
  'keine-berechtigung': 'Keine Berechtigung',
  'zu-viele-anfragen': 'Zu viele Versuche, bitte kurz warten',
  'server': 'Foto konnte nicht gespeichert werden',
  'unbekannt': 'Hochladen fehlgeschlagen',
}

/** Bei diesen Gründen hat ein weiterer Versuch mit dem nächsten Foto keinen Sinn. */
const ABBRUCH_GRUENDE: ReadonlySet<FotoFehlerGrund> = new Set(['kein-netz', 'anmeldung', 'keine-berechtigung', 'zu-viele-anfragen'])

export function fotoFehlerText(grund: FotoFehlerGrund): string {
  return FEHLER_TEXTE[grund]
}

export class FotoUploadFehler extends Error {
  readonly grund: FotoFehlerGrund
  readonly status?: number
  constructor(grund: FotoFehlerGrund, status?: number) {
    super(FEHLER_TEXTE[grund])
    this.name = 'FotoUploadFehler'
    this.grund = grund
    this.status = status
  }
}

/** Nur der Statuscode zählt: Die 413-Antwort des Gateways hat keinen JSON-Body. */
export function fehlerAusStatus(status: number): FotoUploadFehler {
  if (status === 413) return new FotoUploadFehler('zu-gross', status)
  if (status === 401) return new FotoUploadFehler('anmeldung', status)
  if (status === 403) return new FotoUploadFehler('keine-berechtigung', status)
  if (status === 429) return new FotoUploadFehler('zu-viele-anfragen', status)
  if (status === 400 || status === 415 || status === 422) return new FotoUploadFehler('ungueltig', status)
  if (status >= 500) return new FotoUploadFehler('server', status)
  return new FotoUploadFehler('unbekannt', status)
}

export function fortschrittText(nummer: number, gesamt: number): string {
  return `Foto ${nummer} von ${gesamt} wird hochgeladen`
}

export interface FotoFehlschlag<T> {
  item: T
  fehler: FotoUploadFehler
}

export interface FotoUploadErgebnis<T> {
  erfolgreich: T[]
  fehlgeschlagen: FotoFehlschlag<T>[]
  /** Wurden nach einem Abbruch (Netz weg, abgemeldet, keine Berechtigung) gar nicht erst gesendet. */
  nichtVersucht: T[]
}

export interface FotoUploadOptionen<T> {
  senden: (item: T) => Promise<void>
  beiStart?: (nummer: number, gesamt: number, item: T) => void
  /** Sofort nach jedem Erfolg – damit ein gesendetes Foto nie ein zweites Mal rausgeht. */
  beiErfolg?: (item: T) => void
  beiFehler?: (item: T, fehler: FotoUploadFehler) => void
}

function alsFehler(fehler: unknown): FotoUploadFehler {
  if (fehler instanceof FotoUploadFehler) return fehler
  return new FotoUploadFehler('unbekannt')
}

/** Lädt die Fotos nacheinander hoch. Fehler einzelner Fotos stoppen die übrigen nicht. */
export async function ladeFotosEinzelnHoch<T>(
  items: readonly T[],
  optionen: FotoUploadOptionen<T>,
): Promise<FotoUploadErgebnis<T>> {
  const ergebnis: FotoUploadErgebnis<T> = { erfolgreich: [], fehlgeschlagen: [], nichtVersucht: [] }
  let abgebrochen = false
  for (const [index, item] of items.entries()) {
    if (abgebrochen) {
      ergebnis.nichtVersucht.push(item)
      continue
    }
    optionen.beiStart?.(index + 1, items.length, item)
    try {
      await optionen.senden(item)
      ergebnis.erfolgreich.push(item)
      optionen.beiErfolg?.(item)
    } catch (fehler) {
      const typisiert = alsFehler(fehler)
      ergebnis.fehlgeschlagen.push({ item, fehler: typisiert })
      optionen.beiFehler?.(item, typisiert)
      if (ABBRUCH_GRUENDE.has(typisiert.grund)) abgebrochen = true
    }
  }
  return ergebnis
}

/** Eine Zeile für den Toast nach dem Durchlauf; leer, wenn alles geklappt hat. */
export function fotoErgebnisMeldung<T>(ergebnis: FotoUploadErgebnis<T>, bleibenAusgewaehlt = true): string {
  const offen = ergebnis.fehlgeschlagen.length + ergebnis.nichtVersucht.length
  if (offen === 0) return ''
  const gesamt = ergebnis.erfolgreich.length + offen
  const gruende = [...new Set(ergebnis.fehlgeschlagen.map(f => fotoFehlerText(f.fehler.grund)))]
  const grund = gruende.length ? `: ${gruende.join(', ')}` : ''
  const anzahl = offen === 1 ? '1 Foto konnte' : `${offen} Fotos konnten`
  const angekommen = ergebnis.erfolgreich.length === 1 ? 'ist' : 'sind'
  const hinweis = bleibenAusgewaehlt ? ' Die übrigen bleiben ausgewählt.' : ''
  return `${anzahl} nicht hochgeladen werden (${ergebnis.erfolgreich.length} von ${gesamt} ${angekommen} angekommen)${grund}.${hinweis}`
}

// ---------------------------------------------------------------------------
// Verkleinern

export const MAX_KANTE = 2560
export const JPEG_QUALITAET = 0.85
/** Kleinere Fotos bleiben unangetastet (kein unnötiger Qualitätsverlust). */
export const VERKLEINERN_AB_BYTES = 2 * 1024 * 1024

const VERKLEINERBARE_TYPEN = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif'])

export function zielGroesse(breite: number, hoehe: number, maxKante: number): { breite: number; hoehe: number } {
  const laenge = Math.max(breite, hoehe)
  if (laenge <= maxKante) return { breite, hoehe }
  const faktor = maxKante / laenge
  return { breite: Math.max(1, Math.round(breite * faktor)), hoehe: Math.max(1, Math.round(hoehe * faktor)) }
}

function alsJpegName(name: string): string {
  const ohneEndung = name.replace(/\.[^./\\]+$/, '')
  return `${ohneEndung || 'foto'}.jpg`
}

function canvasZuBlob(canvas: HTMLCanvasElement, qualitaet: number): Promise<Blob | null> {
  return new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', qualitaet))
}

/**
 * Verkleinert ein Foto (lange Kante max. 2560 px, JPEG 0.85). Scheitert irgendetwas –
 * z. B. Format im Browser nicht lesbar – geht das Original unverändert durch.
 */
export async function fotoVerkleinern(file: File): Promise<File> {
  if (!VERKLEINERBARE_TYPEN.has(file.type.toLowerCase()) || typeof createImageBitmap !== 'function') return file
  let bitmap: ImageBitmap | null = null
  try {
    // Ausrichtung ausdrücklich aus den EXIF-Daten übernehmen; kann der Browser das nicht, geht das Original durch.
    bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
    const ziel = zielGroesse(bitmap.width, bitmap.height, MAX_KANTE)
    const veraendert = ziel.breite !== bitmap.width || ziel.hoehe !== bitmap.height
    if (!veraendert && file.size <= VERKLEINERN_AB_BYTES) return file
    const canvas = document.createElement('canvas')
    canvas.width = ziel.breite
    canvas.height = ziel.hoehe
    const kontext = canvas.getContext('2d')
    if (!kontext) return file
    kontext.fillStyle = '#ffffff'
    kontext.fillRect(0, 0, ziel.breite, ziel.hoehe)
    kontext.drawImage(bitmap, 0, 0, ziel.breite, ziel.hoehe)
    const blob = await canvasZuBlob(canvas, JPEG_QUALITAET)
    if (!blob || blob.size === 0 || (!veraendert && blob.size >= file.size)) return file
    return new File([blob], alsJpegName(file.name), { type: 'image/jpeg', lastModified: file.lastModified })
  } catch {
    return file
  } finally {
    bitmap?.close()
  }
}

// ---------------------------------------------------------------------------
// Senden

export interface SendeFotoOptionen {
  headers?: Record<string, string>
  send?: typeof fetch
  verkleinern?: (file: File) => Promise<File>
}

/** Sendet genau ein Foto als Multipart-Anfrage (Feld `datei`) und wirft bei Fehlern einen FotoUploadFehler. */
export async function sendeFoto(url: string, file: File, optionen: SendeFotoOptionen = {}): Promise<void> {
  const { headers, send = fetch, verkleinern = fotoVerkleinern } = optionen
  const datei = await verkleinern(file)
  const formData = new FormData()
  formData.append('datei', datei)
  let res: Response
  try {
    res = await send(url, { method: 'POST', headers, body: formData })
  } catch {
    throw new FotoUploadFehler('kein-netz')
  }
  if (!res.ok) throw fehlerAusStatus(res.status)
}
