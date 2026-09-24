export type PreviewConnection = { mode: 'mock' } | { mode: 'connected'; backendUrl: string; idsSupplierId?: number };

/** Never accept a mistyped connection mode as permission to display mock data. */
export function previewConnection(env: Record<string, string | undefined> = process.env): PreviewConnection {
    const mode = env.EN1090_API_MODE ?? 'mock';
    if (mode === 'mock' && !env.EN1090_BACKEND_URL) return { mode };
    if (mode !== 'connected' || !env.EN1090_BACKEND_URL) {
        throw new Error('Backend-Modus mit EN1090_API_MODE=connected und EN1090_BACKEND_URL ausdrücklich konfigurieren.');
    }
    let url: URL;
    try { url = new URL(env.EN1090_BACKEND_URL); }
    catch { throw new Error('Die lokale Backend-Adresse ist ungültig.'); }
    if (url.protocol !== 'http:' || url.hostname !== '127.0.0.1' || url.username || url.password || url.pathname !== '/' || url.search || url.hash) {
        throw new Error('Das lokale Backend benötigt eine HTTP-Adresse auf 127.0.0.1 ohne Zugangsdaten oder zusätzlichen Pfad.');
    }
    const supplierId = env.EN1090_IDS_SUPPLIER_ID;
    if (supplierId !== undefined && (!/^[1-9]\d*$/.test(supplierId) || !Number.isSafeInteger(Number(supplierId)))) {
        throw new Error('EN1090_IDS_SUPPLIER_ID muss die gültige Lieferanten-ID aus der befüllten Datenbank sein.');
    }
    return { mode, backendUrl: url.origin, ...(supplierId ? { idsSupplierId: Number(supplierId) } : {}) };
}
