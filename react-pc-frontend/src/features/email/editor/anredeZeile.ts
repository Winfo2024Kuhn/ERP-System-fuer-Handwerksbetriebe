/**
 * Tauscht im Mail-Textfeld nur die Anredezeile aus, der übrige Text bleibt.
 *
 * Gesucht wird der erste Absatz (ohne weitere Absätze darin), dessen Text genau
 * der zuletzt gesetzten Anrede `alt` entspricht. Hat der Nutzer die Zeile
 * gelöscht oder selbst umgeschrieben, passt nichts und es wird nichts erzwungen.
 * Der neue Text geht über `textContent` hinein (nie als HTML). Danach löst das
 * Feld wie bei den übrigen Editor-Hilfen ein `input`-Ereignis aus.
 *
 * @returns `true`, wenn die Zeile getauscht wurde.
 */
export function ersetzeAnredeZeile(editor: HTMLElement | null | undefined, alt: string, neu: string): boolean {
    if (!editor || alt === neu) return false;
    const gesucht = alt.trim();
    const zeile = Array.from(editor.querySelectorAll<HTMLElement>('p, div'))
        .find(el => !el.querySelector('p, div') && (el.textContent ?? '').trim() === gesucht);
    if (!zeile) return false;
    zeile.textContent = neu;
    editor.dispatchEvent(new Event('input', { bubbles: true }));
    return true;
}
