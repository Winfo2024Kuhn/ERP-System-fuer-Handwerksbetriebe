import { afterEach, describe, expect, it } from 'vitest';
import { createServer as createHttpServer, type Server } from 'node:http';
import { createServer as createViteServer, type ViteDevServer } from 'vite';
import { previewApi } from './serverPlugin';

const servers: Server[] = [];
const viteServers: ViteDevServer[] = [];
afterEach(async () => {
    await Promise.all(viteServers.splice(0).map(server => server.close()));
    await Promise.all(servers.splice(0).map(server => new Promise<void>(resolve => server.close(() => resolve()))));
});
async function listen(server: Server) {
    servers.push(server);
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
    return `http://127.0.0.1:${(server.address() as { port: number }).port}`;
}
async function setup() {
    const calls: { path: string; method: string; cookie?: string; csrf?: string; body: string }[] = [];
    const backendUrl = await listen(createHttpServer(async (req, res) => {
        const chunks: Buffer[] = [];
        for await (const chunk of req) chunks.push(Buffer.from(chunk));
        calls.push({ path: req.url!, method: req.method!, cookie: req.headers.cookie, csrf: req.headers['x-xsrf-token'] as string, body: Buffer.concat(chunks).toString() });
        res.setHeader('Content-Type', 'application/json');
        if (req.url === '/api/auth/me') { res.statusCode = 401; res.end('{"message":"Backend login required"}'); }
        else if (req.url === '/api/unknown') { res.statusCode = 404; res.end('{"message":"Backend missing"}'); }
        else if (req.url === '/api/projekte') res.end('[{"id":9876,"name":"Dummy DB project"}]');
        else { res.statusCode = 201; res.setHeader('Set-Cookie', 'JSESSIONID=dummy-session; HttpOnly; Path=/'); res.end('{"source":"backend"}'); }
    }));
    const vite = await createViteServer({ configFile: false, logLevel: 'silent', plugins: [previewApi({ mode: 'connected', backendUrl, idsSupplierId: 1 })], server: { middlewareMode: true, proxy: { '/api': { target: backendUrl, changeOrigin: false } } }, appType: 'custom' });
    viteServers.push(vite);
    const origin = await listen(createHttpServer(vite.middlewares));
    return { origin, calls };
}

describe('connected application API proxy', () => {
    it('loads backend records and preserves missing/unauthenticated responses instead of fake data', async () => {
        const { origin } = await setup();
        expect(await (await fetch(origin + '/api/projekte')).json()).toEqual([{ id: 9876, name: 'Dummy DB project' }]);
        const auth = await fetch(origin + '/api/auth/me');
        expect(auth.status).toBe(401);
        expect(await auth.json()).toEqual({ message: 'Backend login required' });
        const missing = await fetch(origin + '/api/unknown');
        expect(missing.status).toBe(404);
        expect(await missing.json()).toEqual({ message: 'Backend missing' });
        expect(await (await fetch(origin + '/api/preview/mode')).json()).toEqual({ mode: 'connected', idsTestMode: false });
    });
    it('preserves writes, session cookies and CSRF and disables the preview reset', async () => {
        const { origin, calls } = await setup();
        const response = await fetch(origin + '/api/auth/login', { method: 'POST', headers: { Cookie: 'JSESSIONID=dummy-old', 'X-XSRF-TOKEN': 'dummy-csrf', 'Content-Type': 'application/x-www-form-urlencoded' }, body: 'username=dummy&password=dummy-only' });
        expect(response.status).toBe(201);
        expect(response.headers.get('set-cookie')).toContain('JSESSIONID=dummy-session');
        expect(calls[0]).toEqual({ path: '/api/auth/login', method: 'POST', cookie: 'JSESSIONID=dummy-old', csrf: 'dummy-csrf', body: 'username=dummy&password=dummy-only' });
        expect((await fetch(origin + '/api/preview/reset', { method: 'POST' })).status).toBe(404);
        expect(calls).toHaveLength(1);
    });
});
