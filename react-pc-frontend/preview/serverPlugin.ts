import { homedir } from 'node:os';
import { createHash } from 'node:crypto';
import { resolve, dirname } from 'node:path';
import { createIdsDraftStore } from './idsDraftStore';
import { previewConnection, type PreviewConnection } from './connectionConfig';
import { connectedIds } from './connectedIds';
import type { Plugin } from 'vite';
import { handleProcurementMock, resetProcurementMock, receiveIdsCart } from './procurementMock';
import { createIdsBridge, loadIdsCredentials } from './idsBridge';

export function previewApi(connection: PreviewConnection = previewConnection()): Plugin {
    return {
        name: `en1090-local-${connection.mode}-api`,
        configureServer(server) {
            const drafts = createIdsDraftStore(process.env.IDS_PREVIEW_CONFIG_PATH ? resolve(dirname(process.env.IDS_PREVIEW_CONFIG_PATH), 'ids-carts.local.json') : resolve(homedir(), 'Library/Application Support/Codex/ids-preview', createHash('sha256').update(process.cwd()).digest('hex').slice(0, 16), 'ids-carts.local.json'));
            if (connection.mode === 'mock') for (const draft of drafts.all()) receiveIdsCart(draft, draft.id);
            const ids = createIdsBridge(`http://127.0.0.1:${server.config.server.port}`, loadIdsCredentials, connection.mode === 'mock' ? receiveIdsCart : () => {}, undefined, drafts, connection.mode === 'connected' ? connection.idsSupplierId ?? null : undefined);
            const authenticatedIds = connection.mode === 'connected' ? connectedIds(connection.backendUrl, ids, connection.idsSupplierId ?? null) : null;
            server.middlewares.use((req, res, next) => {
                const url = new URL(req.url ?? '/', 'http://127.0.0.1');
                const aliases: Record<string, string> = {
                    ...(connection.mode === 'mock' ? { '/': '/bestellungen/bedarf' } : {}),
                    '/bestellungen/bedarf/vorrat': '/bestellungen/bedarf/legacy',
                    '/einkaufsanfragen': '/einkauf/preisanfragen',
                };
                if (aliases[url.pathname]) {
                    res.statusCode = 302;
                    res.setHeader('Location', aliases[url.pathname]);
                    return res.end();
                }
                if (!url.pathname.startsWith('/api/')) return next();
                const json = (body: unknown, status = 200) => {
                    res.statusCode = status;
                    res.setHeader('Content-Type', 'application/json; charset=utf-8');
                    res.setHeader('Cache-Control', 'no-store');
                    res.end(JSON.stringify(body));
                };
                if (url.pathname === '/api/preview/mode') return json({ mode: connection.mode, idsTestMode: connection.mode === 'mock' && process.env.IDS_PREVIEW_CONFIG_PATH === '/tmp/ids-e2e-isolated/application-local.properties' });
                if (authenticatedIds) {
                    if (url.pathname.startsWith('/api/preview/')) return json({ message: 'Vorschau-Aktionen sind im Backend-Modus nicht verfügbar.' }, 404);
                    void authenticatedIds(req, res, url).then(handled => { if (!handled) next(); }).catch(() => {
                        if (!res.writableEnded) json({ message: 'Die IDS-Verbindung konnte nicht ausgeführt werden.' }, 502);
                    });
                    return;
                }
                if (url.pathname === '/api/auth/me' || url.pathname === '/api/auth/login') {
                    return json({ id: 1, username: 'max.mustermann', displayName: 'Max Mustermann', active: true, admin: true, roles: ['ADMIN'], requiresInitialSetup: false });
                }
                if (url.pathname === '/api/auth/logout') return json({});
                if (url.pathname === '/api/preview/reset' && req.method === 'POST') {
                    if (process.env.IDS_PREVIEW_CONFIG_PATH !== "/tmp/ids-e2e-isolated/application-local.properties") return json({ message: "Nur im isolierten Test verfügbar." }, 403);
                    drafts.clear();
                    resetProcurementMock();
                    return json({ reset: true });
                }
                if (['/api/settings/smtp', '/api/settings/gemini', '/api/settings/datei-ordner'].includes(url.pathname) && req.method === 'GET') return json({});
                if (url.pathname === '/api/einkauf/berechtigungen') return json(['LESEN', 'SCHREIBEN', 'VERWALTEN']);
                if (url.pathname === '/api/features') return json({ en1090: true, echeck: true, email: true, rag: false });
                if (url.pathname === '/api/notifications/summary') return json({ totalCount: 0, categories: [], recentItems: [] });
                void ids(req, res, url).then(handled => handled || handleProcurementMock(req, res, url)).then(handled => {
                    if (!handled && !res.writableEnded) {
                        server.config.logger.warn(`[EN1090-Mock fehlt] ${req.method} ${url.pathname}`);
                        json({ message: `Für ${url.pathname} fehlt noch ein Vorschau-Mock.` }, 404);
                    }
                }).catch(error => {
                    server.config.logger.error(`[EN1090-Mock] ${error instanceof Error ? error.message : 'Unbekannter Fehler'}`);
                    if (!res.writableEnded) json({ message: 'Die Vorschau konnte diese Aktion nicht ausführen.' }, 500);
                });
            });
        },
    };
}
