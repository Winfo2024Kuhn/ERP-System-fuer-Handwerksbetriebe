/** Dateigröße für die Anzeige: „84 KB“ / „1,2 MB“ (mindestens 1 KB, Dezimalkomma). */
export function formatBytes(bytes: number): string {
    if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`;
}
