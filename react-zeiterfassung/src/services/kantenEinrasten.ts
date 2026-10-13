/**
 * Rastet grob erkannte Dokument-Ecken an den echten Blattkanten ein.
 *
 * Das ML-Modell sieht das Bild nur in 224 × 224 Pixeln; seine Ecken liegen
 * im vollen Foto deshalb oft einige Pixel daneben. Hier wird jede der vier
 * Seiten einzeln nachgeschärft: Entlang der Seite wird an vielen Stellen
 * quer zur Kante nach dem stärksten Helligkeitssprung gesucht, durch diese
 * Treffer eine Gerade gelegt (Ausreißer wie Schriftzeilen fliegen raus) und
 * die Ecken als Schnittpunkte benachbarter Geraden neu berechnet.
 *
 * Findet eine Seite keine klare Kante (z. B. weißes Blatt auf weißem
 * Tisch), bleibt sie, wie sie war – schlechter als die Eingabe wird es nie.
 */
import type { Point } from './scannerOverlay'

export interface Graubild {
    /** Helligkeit 0–255, zeilenweise. */
    daten: Float32Array
    breite: number
    hoehe: number
}

export interface EinrastOptionen {
    /** Suchweite quer zur Kante als Anteil der Bilddiagonale. */
    suchAnteil?: number
    /** Messstellen pro Seite. */
    messpunkte?: number
    /** Mindest-Helligkeitssprung (0–255), damit eine Stelle als Kante zählt. */
    minKontrast?: number
}

const STANDARD: Required<EinrastOptionen> = {
    suchAnteil: 0.02,
    messpunkte: 48,
    minKontrast: 6,
}

/**
 * Ein Sprung zählt als Kante, wenn er deutlich über dem Rauschen liegt und
 * mindestens diesen Anteil des stärksten Sprungs an der Messstelle erreicht.
 * Bewusst niedrig: Eine Blattkante auf hellem Grund kann viel schwächer
 * sein als die Schrift dahinter.
 */
const RAUSCH_FAKTOR = 3
const RELATIVE_SCHWELLE = 0.08

/** Mindestanteil brauchbarer Messstellen, damit eine Seite neu gelegt wird. */
const MIN_TREFFER_ANTEIL = 0.35

export function zuGraubild(bild: { data: Uint8ClampedArray; width: number; height: number }): Graubild {
    const { data, width, height } = bild
    const daten = new Float32Array(width * height)
    for (let i = 0, p = 0; p < daten.length; i += 4, p++) {
        daten[p] = 0.299 * data[i] + 0.587 * data[i + 1] + 0.114 * data[i + 2]
    }
    return { daten, breite: width, hoehe: height }
}

function hellAn(bild: Graubild, x: number, y: number): number {
    const { daten, breite, hoehe } = bild
    const cx = Math.min(Math.max(x, 0), breite - 1.001)
    const cy = Math.min(Math.max(y, 0), hoehe - 1.001)
    const x0 = Math.floor(cx)
    const y0 = Math.floor(cy)
    const dx = cx - x0
    const dy = cy - y0
    const i = y0 * breite + x0
    const oben = daten[i] * (1 - dx) + daten[i + 1] * dx
    const unten = daten[i + breite] * (1 - dx) + daten[i + breite + 1] * dx
    return oben * (1 - dy) + unten * dy
}

interface Gerade {
    /** Punkt auf der Geraden */
    p: Point
    /** Einheits-Richtung */
    r: Point
}

/**
 * Sucht an einer Messstelle quer zur Kante den Helligkeitssprung der
 * Blattkante. `aussen` zeigt vom Blatt weg. Genommen wird nicht der
 * stärkste Sprung, sondern – von außen kommend – der erste deutliche:
 * Die Blattkante ist immer die äußerste Kante des Blatts, Schriftzeilen
 * knapp dahinter sind oft kontrastreicher. Gemessen wird über drei
 * parallele Linien gemittelt, damit Rauschen und Papierstruktur weniger stören.
 */
function blattkanteAn(
    bild: Graubild, p: Point, aussen: Point, richtung: Point, radius: number, minKontrast: number,
): { versatz: number; sprung: number } | null {
    const schritte = Math.floor(radius * 2)
    const profil = new Float32Array(2 * schritte + 1)
    let staerkster = 0
    for (let k = 0; k < profil.length; k++) {
        const s = (k - schritte) / 2
        let sprung = 0
        for (const quer of [-1.5, 0, 1.5]) {
            const x = p.x + aussen.x * s + richtung.x * quer
            const y = p.y + aussen.y * s + richtung.y * quer
            sprung += hellAn(bild, x + aussen.x, y + aussen.y) - hellAn(bild, x - aussen.x, y - aussen.y)
        }
        profil[k] = sprung / 3
        staerkster = Math.max(staerkster, Math.abs(profil[k]))
    }
    // Rauschen = typischer (mittlerer) Sprung entlang der Messlinie
    const rauschen = median(Array.from(profil, Math.abs))
    const schwelle = Math.max(minKontrast, RAUSCH_FAKTOR * rauschen, staerkster * RELATIVE_SCHWELLE)
    // Von außen (großes s) nach innen: erstes lokales Maximum über der Schwelle
    for (let k = profil.length - 1; k >= 0; k--) {
        const betrag = Math.abs(profil[k])
        if (betrag < schwelle) continue
        const davor = k > 0 ? Math.abs(profil[k - 1]) : 0
        const danach = k < profil.length - 1 ? Math.abs(profil[k + 1]) : 0
        if (betrag >= davor && betrag >= danach) return { versatz: (k - schritte) / 2, sprung: profil[k] }
    }
    return null
}

/** Gerade durch Punkte mit kleinster senkrechter Abweichung (Hauptachse). */
function legeGerade(punkte: Point[]): Gerade {
    let mx = 0, my = 0
    for (const q of punkte) { mx += q.x; my += q.y }
    mx /= punkte.length
    my /= punkte.length
    let sxx = 0, syy = 0, sxy = 0
    for (const q of punkte) {
        const dx = q.x - mx
        const dy = q.y - my
        sxx += dx * dx; syy += dy * dy; sxy += dx * dy
    }
    const winkel = 0.5 * Math.atan2(2 * sxy, sxx - syy)
    return { p: { x: mx, y: my }, r: { x: Math.cos(winkel), y: Math.sin(winkel) } }
}

function abstand(g: Gerade, q: Point): number {
    return Math.abs((q.x - g.p.x) * g.r.y - (q.y - g.p.y) * g.r.x)
}

function median(werte: number[]): number {
    const sortiert = [...werte].sort((a, b) => a - b)
    return sortiert[sortiert.length >> 1]
}

/** Legt eine Seite (von a nach b) neu; null, wenn keine verlässliche Kante da ist. */
function verfeinereSeite(
    bild: Graubild, a: Point, b: Point, mitte: Point, radius: number, opt: Required<EinrastOptionen>,
): Gerade | null {
    const laenge = Math.hypot(b.x - a.x, b.y - a.y)
    if (laenge < 8) return null
    const richtung = { x: (b.x - a.x) / laenge, y: (b.y - a.y) / laenge }
    // Senkrechte so drehen, dass sie vom Blatt weg zeigt
    const seitenMitte = { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 }
    const weg = (seitenMitte.x - mitte.x) * -richtung.y + (seitenMitte.y - mitte.y) * richtung.x >= 0 ? 1 : -1
    const aussen = { x: -richtung.y * weg, y: richtung.x * weg }
    const suchRadius = Math.min(radius, laenge * 0.15)

    const treffer: Array<Point & { vorzeichen: number }> = []
    for (let k = 0; k < opt.messpunkte; k++) {
        // Ecken selbst aussparen: dort überlagern sich zwei Kanten.
        const t = 0.1 + (0.8 * k) / (opt.messpunkte - 1)
        const p = { x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t }
        const kante = blattkanteAn(bild, p, aussen, richtung, suchRadius, opt.minKontrast)
        if (!kante) continue
        treffer.push({ x: p.x + aussen.x * kante.versatz, y: p.y + aussen.y * kante.versatz, vorzeichen: Math.sign(kante.sprung) })
    }

    // Ein Blatt hebt sich entlang einer Seite durchgehend gleich ab (heller
    // oder dunkler als der Grund). Treffer mit dem anderen Vorzeichen sind
    // meist Schatten oder Kleinkram am Rand – raus damit.
    const plus = treffer.filter(t => t.vorzeichen > 0)
    const minus = treffer.filter(t => t.vorzeichen < 0)
    let punkte: Point[] = plus.length >= minus.length ? plus : minus
    const minTreffer = Math.max(4, Math.ceil(opt.messpunkte * MIN_TREFFER_ANTEIL))
    if (punkte.length < minTreffer) return null

    // Zweimal: Gerade legen, Ausreißer verwerfen.
    let gerade = legeGerade(punkte)
    for (let runde = 0; runde < 2; runde++) {
        const abstaende = punkte.map(q => abstand(gerade, q))
        const grenze = Math.max(1.5, 2.5 * median(abstaende))
        const behalten = punkte.filter((_, i) => abstaende[i] <= grenze)
        if (behalten.length < minTreffer) return null
        punkte = behalten
        gerade = legeGerade(punkte)
    }

    // Die neue Gerade muss ungefähr die alte Seite sein (Richtung + Lage).
    const parallel = Math.abs(gerade.r.x * richtung.x + gerade.r.y * richtung.y)
    if (parallel < Math.cos((8 * Math.PI) / 180)) return null
    if (abstand(gerade, seitenMitte) > suchRadius) return null
    return gerade
}

function schnittpunkt(g: Gerade, h: Gerade): Point | null {
    const nenner = g.r.x * h.r.y - g.r.y * h.r.x
    if (Math.abs(nenner) < 1e-6) return null
    const t = ((h.p.x - g.p.x) * h.r.y - (h.p.y - g.p.y) * h.r.x) / nenner
    return { x: g.p.x + g.r.x * t, y: g.p.y + g.r.y * t }
}

function istKonvex(ecken: Point[]): boolean {
    let vorzeichen = 0
    for (let i = 0; i < 4; i++) {
        const a = ecken[i]
        const b = ecken[(i + 1) % 4]
        const c = ecken[(i + 2) % 4]
        const kreuz = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        if (Math.abs(kreuz) < 1e-9) return false
        const s = Math.sign(kreuz)
        if (vorzeichen === 0) vorzeichen = s
        else if (s !== vorzeichen) return false
    }
    return true
}

/**
 * Liefert die eingerasteten Ecken (TL, TR, BR, BL) in Pixeln von `bild`.
 * Ungültige Eingaben oder unplausible Ergebnisse geben die Eingabe zurück.
 */
export function rasteKantenEin(bild: Graubild, ecken: Point[], optionen: EinrastOptionen = {}): Point[] {
    if (ecken.length !== 4 || bild.breite < 4 || bild.hoehe < 4) return ecken
    const opt = { ...STANDARD, ...optionen }
    const radius = Math.max(3, opt.suchAnteil * Math.hypot(bild.breite, bild.hoehe))

    const mitte = {
        x: ecken.reduce((s, p) => s + p.x, 0) / 4,
        y: ecken.reduce((s, p) => s + p.y, 0) / 4,
    }
    const seiten = ecken.map((a, i) => verfeinereSeite(bild, a, ecken[(i + 1) % 4], mitte, radius, opt))
    if (seiten.every(s => s === null)) return ecken

    // Ecke i liegt zwischen Seite i-1 (endet in i) und Seite i (beginnt in i).
    // Nicht eingerastete Seiten behalten ihre ursprüngliche Lage.
    const alsGerade = (i: number): Gerade => {
        const s = seiten[i]
        if (s) return s
        const a = ecken[i]
        const b = ecken[(i + 1) % 4]
        const l = Math.hypot(b.x - a.x, b.y - a.y) || 1
        return { p: a, r: { x: (b.x - a.x) / l, y: (b.y - a.y) / l } }
    }
    const ergebnis = ecken.map((alt, i) => {
        const vorher = (i + 3) % 4
        if (!seiten[vorher] && !seiten[i]) return alt
        const neu = schnittpunkt(alsGerade(vorher), alsGerade(i))
        if (!neu || Math.hypot(neu.x - alt.x, neu.y - alt.y) > radius * 2) return alt
        return {
            x: Math.min(Math.max(neu.x, 0), bild.breite),
            y: Math.min(Math.max(neu.y, 0), bild.hoehe),
        }
    })
    return istKonvex(ergebnis) ? ergebnis : ecken
}
