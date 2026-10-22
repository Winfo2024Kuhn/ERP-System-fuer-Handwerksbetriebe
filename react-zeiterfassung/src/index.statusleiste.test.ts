import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

/**
 * Struktureller Test, bewusst als solcher.
 *
 * <p>Beide Fehler treten nur in der installierten iOS-PWA auf (Standalone,
 * iOS 26/27) – in jsdom und im Desktop-Browser ist nichts davon zu sehen.
 * Deshalb hält dieser Test die CSS-Regeln fest, die sie verhindern:
 *
 * <ul>
 *   <li>Oben: iOS färbt die opake Statusleiste in der Seitenfarbe (body) und
 *       blendet sie mit einem Verlauf in den Inhalt über. War die Seitenfarbe
 *       slate-50, stand über der weißen Kopfleiste ein grauer Streifen mit
 *       Verlauf. Die Seitenfarbe muss deshalb weiß sein wie die Kopfleisten.</li>
 *   <li>Unten: {@code #root} muss bis zur Gerätekante reichen. {@code 100dvh}
 *       lässt die Home-Indicator-Zone aus, und {@code height: 100%} ist bei
 *       alten Installationen ("black-translucent") unter iOS 27 um die
 *       Statusleiste zu kurz. Nur {@code position: fixed; inset: 0} folgt dem
 *       echten Fenster.</li>
 * </ul>
 */
const css = readFileSync(resolve(process.cwd(), 'src/index.css'), 'utf-8')
    // Windows-Checkouts mit autocrlf: Zeilenenden vereinheitlichen.
    .replace(/\r\n/g, '\n')
    // Kommentare raus, damit die Begründungstexte nicht mitgeprüft werden.
    .replace(/\/\*[\s\S]*?\*\//g, '')

function regelnFuer(selektor: string): string {
    const muster = new RegExp(`(?:^|[{}])\\s*${selektor}\\s*\\{([^}]*)\\}`, 'g')
    return [...css.matchAll(muster)].map(treffer => treffer[1]).join('\n')
}

describe('index.css: kein Balken an der Statusleiste oder unten', () => {
    it('gibt der Seite eine weiße Hintergrundfarbe wie den Kopfleisten', () => {
        const body = regelnFuer('body')
        expect(body).toMatch(/background(?:-color)?\s*:\s*(?:#fff(?:fff)?|white)\s*;/i)
        expect(body).not.toMatch(/#f8fafc/i)
    })

    it('heftet #root an alle vier Fensterkanten statt eine Höhe zu rechnen', () => {
        const root = regelnFuer('#root')
        expect(root).toMatch(/position\s*:\s*fixed\s*;/)
        expect(root).toMatch(/inset\s*:\s*0\s*;/)
        expect(root).not.toMatch(/dvh/)
        expect(root).not.toMatch(/height\s*:/)
    })

    it('verlängert html nicht um einen Inset (sonst schnitte es unten ab)', () => {
        expect(regelnFuer('html')).not.toMatch(/safe-area-inset/)
    })
})
