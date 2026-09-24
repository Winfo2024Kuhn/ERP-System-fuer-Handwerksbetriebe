import { afterEach, describe, expect, it } from 'vitest';
import { createServer, type Server } from 'node:http';
import { connectedIds } from './connectedIds';

const servers: Server[] = [];
afterEach(async () => {
    await Promise.all(servers.splice(0).map(server => new Promise<void>(resolve => server.close(() => resolve()))));
});
async function listen(server: Server) {
    servers.push(server);
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
    return `http://127.0.0.1:${(server.address() as { port: number }).port}`;
}
async function setup() {
    let authStatus = 200;
    let user = { id: 7, active: true, admin: false };
    let permissions: string[] = ['LESEN'];
    let cookie = '';
    let idsCalls = 0;
    const backend = await listen(createServer((req, res) => {
        cookie = req.headers.cookie ?? '';
        res.setHeader('Content-Type', 'application/json');
        if (req.url === '/api/auth/me') {
            res.statusCode = authStatus;
            res.end(JSON.stringify(user));
        } else res.end(JSON.stringify(permissions));
    }));
    const handler = connectedIds(backend, async (_req, res) => { idsCalls++; res.end('local IDS'); return true; }, 203);
    const origin = await listen(createServer((req, res) => {
        void handler(req, res, new URL(req.url!, 'http://127.0.0.1')).then(handled => {
            if (!handled) { res.statusCode = 404; res.end('proxy next'); }
        });
    }));
    return { origin, getCookie: () => cookie, getCalls: () => idsCalls, setStatus: (value: number) => { authStatus = value; }, setUser: (value: typeof user) => { user = value; }, setPermissions: (value: string[]) => { permissions = value; } };
}

describe('local IDS with real backend authentication', () => {
    it('does not intercept ordinary APIs or supply authentication itself', async () => {
        const app = await setup();
        for (const path of ['/api/projekte', '/api/auth/me', '/api/settings/smtp', '/api/features']) {
            expect((await fetch(app.origin + path)).status).toBe(404);
        }
        expect(app.getCalls()).toBe(0);
    });
    it('requires a real active session and passes its cookie to the backend', async () => {
        const app = await setup();
        app.setStatus(401);
        expect((await fetch(app.origin + '/api/ids/warenkoerbe', { headers: { Cookie: 'JSESSIONID=dummy-session' } })).status).toBe(401);
        expect(app.getCookie()).toBe('JSESSIONID=dummy-session');
        app.setStatus(200);
        app.setUser({ id: 7, active: false, admin: false });
        expect((await fetch(app.origin + '/api/ids/warenkoerbe')).status).toBe(403);
        expect(app.getCalls()).toBe(0);
    });
    it('requires admin for IDS settings and procurement rights for cart actions', async () => {
        const app = await setup();
        expect((await fetch(app.origin + '/api/admin/ids/lieferanten')).status).toBe(403);
        expect((await fetch(app.origin + '/api/ids/warenkoerbe')).status).toBe(200);
        expect((await fetch(app.origin + '/api/ids/punchout/203/start', { method: 'POST' })).status).toBe(403);
        app.setPermissions(['LESEN', 'BEARBEITEN']);
        expect((await fetch(app.origin + '/api/ids/punchout/203/start', { method: 'POST' })).status).toBe(200);
        app.setUser({ id: 7, active: true, admin: true });
        expect((await fetch(app.origin + '/api/admin/lieferanten/203/ids-konfig')).status).toBe(200);
    });
    it('lets only the exact callback reach the single-use token validator without a session', async () => {
        const app = await setup();
        app.setStatus(401);
        expect((await fetch(app.origin + '/api/ids/punchout/203/return?token=dummy', { method: 'POST' })).status).toBe(200);
        expect((await fetch(app.origin + '/api/ids/punchout/203/return?token=dummy')).status).toBe(401);
        expect((await fetch(app.origin + '/api/ids/punchout/999/return?token=dummy', { method: 'POST' })).status).toBe(401);
        expect(app.getCalls()).toBe(1);
    });
    it('fails closed when the backend fails instead of providing mock identity', async () => {
        const app = await setup();
        app.setStatus(503);
        expect((await fetch(app.origin + '/api/ids/warenkoerbe')).status).toBe(502);
        expect(app.getCalls()).toBe(0);
    });
});
