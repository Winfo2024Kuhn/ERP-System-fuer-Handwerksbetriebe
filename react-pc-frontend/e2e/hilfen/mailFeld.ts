import type { Locator, Page } from '@playwright/test';
import { expect } from './test';

/** Gemeinsame Hilfen für Specs am Mail-Textfeld (Leiste, Markieren). */

/** Markiert im Feld genau den angegebenen Text (wie mit der Maus). */
export async function markiereText(feld: Locator, text: string) {
    await feld.evaluate((element, gesucht) => {
        const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
        for (let knoten = walker.nextNode(); knoten; knoten = walker.nextNode()) {
            const start = knoten.textContent?.indexOf(gesucht) ?? -1;
            if (start < 0) continue;
            const range = document.createRange();
            range.setStart(knoten, start);
            range.setEnd(knoten, start + gesucht.length);
            window.getSelection()?.removeAllRanges();
            window.getSelection()?.addRange(range);
            return;
        }
        throw new Error(`Text nicht gefunden: ${gesucht}`);
    }, text);
}

/** Kopfzeile „Nachricht“: Leiste und KI-Knopf stehen in einer Zeile, nichts bricht um. */
export async function erwarteEineZeile(page: Page) {
    const leiste = page.getByRole('toolbar', { name: 'Formatierung' });
    const ki = await page.getByRole('button', { name: 'KI-Optimierung' }).boundingBox();
    const label = await page.getByText('Nachricht', { exact: true }).boundingBox();
    const box = await leiste.boundingBox();
    expect(box && ki && label).toBeTruthy();
    expect(Math.abs(box!.y + box!.height / 2 - (ki!.y + ki!.height / 2))).toBeLessThan(4);
    // Gleiche Zeile wie „Nachricht“ (im Dialog steht darunter noch ein Hinweis).
    expect(box!.y).toBeLessThan(label!.y + label!.height);
    expect(box!.y + box!.height).toBeGreaterThan(label!.y);
    // Alle Knöpfe der Leiste auf einer Höhe und innerhalb der Leiste (nichts abgeschnitten).
    const knoepfe = await leiste.getByRole('button').evaluateAll(elemente => elemente.map(e => e.getBoundingClientRect().toJSON()));
    for (const knopf of knoepfe) {
        expect(Math.abs(knopf.top - knoepfe[0].top)).toBeLessThan(2);
        expect(knopf.right).toBeLessThanOrEqual(box!.x + box!.width + 1);
    }
    expect(knoepfe.at(-1)!.right).toBeLessThanOrEqual(ki!.x);
}
