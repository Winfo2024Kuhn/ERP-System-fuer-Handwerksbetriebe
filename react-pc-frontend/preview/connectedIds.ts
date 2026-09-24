import type { IncomingMessage, ServerResponse } from 'node:http';

type IdsHandler = (req: IncomingMessage, res: ServerResponse, url: URL) => Promise<boolean>;

/** The local IDS adapter uses the real application session, never a preview identity. */
export function connectedIds(backendUrl: string, ids: IdsHandler, supplierId: number | null = null): IdsHandler {
    const reject = (res: ServerResponse, status: number, message: string) => {
        res.statusCode = status;
        res.setHeader('Content-Type', 'application/json; charset=utf-8');
        res.setHeader('Cache-Control', 'no-store');
        res.end(JSON.stringify({ message }));
        return true;
    };
    return async (req, res, url) => {
        const settings = url.pathname === '/api/admin/ids/lieferanten' || /^\/api\/admin\/lieferanten\/[1-9]\d*\/ids-konfig$/.test(url.pathname);
        if (!settings && !url.pathname.startsWith('/api/ids/')) return false;
        // Würth posts from another origin without the ERP session. The bridge validates
        // its random, expiring, single-use token before reading or importing anything.
        if (supplierId !== null && url.pathname === `/api/ids/punchout/${supplierId}/return` && req.method === 'POST') return ids(req, res, url);
        try {
            const request = { headers: { Cookie: req.headers.cookie ?? '', Accept: 'application/json' }, redirect: 'manual' as const, signal: AbortSignal.timeout(5000) };
            const response = await fetch(`${backendUrl}/api/auth/me`, request);
            if (response.status === 401 || response.status === 403) return reject(res, response.status, 'Bitte zuerst in der Anwendung anmelden.');
            if (!response.ok) return reject(res, 502, 'Die Anmeldung konnte am Backend nicht geprüft werden.');
            const user = await response.json() as { id?: number; active?: boolean; admin?: boolean };
            if (!user.id || user.active !== true) return reject(res, 403, 'Dieser Zugang ist nicht aktiv.');
            if (settings) {
                if (user.admin !== true) return reject(res, 403, 'Die IDS-Einstellungen benötigen Administratorrechte.');
            } else {
                const permissionsResponse = await fetch(`${backendUrl}/api/einkauf/berechtigungen`, request);
                if (!permissionsResponse.ok) return reject(res, 403, 'Die Einkaufsberechtigung konnte nicht bestätigt werden.');
                const permissions: unknown = await permissionsResponse.json();
                const required = req.method === 'GET' ? 'LESEN' : 'BEARBEITEN';
                if (!Array.isArray(permissions) || !permissions.includes(required)) return reject(res, 403, 'Für diese Aktion fehlt die Einkaufsberechtigung.');
            }
            return ids(req, res, url);
        } catch {
            return reject(res, 502, 'Das Backend ist derzeit nicht erreichbar. Bitte erneut versuchen.');
        }
    };
}
