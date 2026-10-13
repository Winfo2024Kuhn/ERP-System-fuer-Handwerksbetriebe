/**
 * Urlaubstage im deutschen Zahlenformat.
 *
 * Halbe Urlaubstage zählen 0,5 – der Server liefert Resturlaub, genommene und
 * geplante Tage deshalb als Dezimalzahl. Ganze Zahlen erscheinen ohne
 * Nachkomma („12"), halbe mit Komma („12,5"). Ungültige Werte werden als „0"
 * gezeigt, damit nie „NaN Tage" auf dem Bildschirm steht.
 */
export function formatUrlaubstage(tage: number | null | undefined): string {
    const wert = Number(tage);
    if (!Number.isFinite(wert)) return '0';
    return wert.toLocaleString('de-DE', { maximumFractionDigits: 2 });
}

/** „12,5 Tage" bzw. „1 Tag" – für Sätze, in denen die Einheit dazugehört. */
export function formatUrlaubstageMitEinheit(tage: number | null | undefined): string {
    const text = formatUrlaubstage(tage);
    return `${text} ${text === '1' ? 'Tag' : 'Tage'}`;
}
