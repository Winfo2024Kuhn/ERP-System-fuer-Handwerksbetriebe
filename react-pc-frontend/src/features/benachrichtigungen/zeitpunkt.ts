/**
 * Zeitpunkt einer Benachrichtigung in deutscher Schreibweise:
 * „heute, 02:00“, „gestern, 14:12“, „morgen, 09:00“, sonst „11.10.2026, 02:00“.
 * Liefert der Server nur ein Datum (z. B. Termine: „2026-10-11“), steht
 * keine Uhrzeit dabei („heute“, „11.10.2026“). Unlesbares ergibt einen leeren Text.
 *
 * Der Zeitpunkt wird als Ortszeit gelesen – `new Date(iso)` würde je nach
 * Browser UTC annehmen und um ein, zwei Stunden verrutschen.
 */

const zweistellig = (n: number) => String(n).padStart(2, '0');

function tagDifferenz(datum: Date, jetzt: Date): number {
    const a = new Date(datum.getFullYear(), datum.getMonth(), datum.getDate());
    const b = new Date(jetzt.getFullYear(), jetzt.getMonth(), jetzt.getDate());
    return Math.round((a.getTime() - b.getTime()) / 86_400_000);
}

export function formatZeitpunkt(iso: string | null | undefined, jetzt: Date = new Date()): string {
    const treffer = /^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2}))?/.exec(iso ?? '');
    if (!treffer) return '';
    const [, j, mo, t, h, mi] = treffer;
    const datum = new Date(Number(j), Number(mo) - 1, Number(t), Number(h ?? 0), Number(mi ?? 0));
    // 31.02. o. ä. rollt in den nächsten Monat – das ist kein echtes Datum.
    if (datum.getDate() !== Number(t) || datum.getMonth() !== Number(mo) - 1) return '';

    const differenz = tagDifferenz(datum, jetzt);
    const tag = differenz === 0 ? 'heute'
        : differenz === -1 ? 'gestern'
            : differenz === 1 ? 'morgen'
                : `${zweistellig(datum.getDate())}.${zweistellig(datum.getMonth() + 1)}.${datum.getFullYear()}`;
    if (h === undefined) return tag;
    return `${tag}, ${zweistellig(datum.getHours())}:${zweistellig(datum.getMinutes())}`;
}
