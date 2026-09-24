import { defineConfig } from 'vite';
import originalConfig from '../vite.config';
import { previewApi } from './serverPlugin';
import { previewConnection } from './connectionConfig';

const connection = previewConnection();

// The same frontend can run against isolated mocks or the populated local backend.
export default defineConfig({
    ...originalConfig,
    define: { ...originalConfig.define, 'import.meta.env.VITE_EN1090_API_MODE': JSON.stringify(connection.mode) },
    plugins: [...(originalConfig.plugins ?? []), previewApi(connection)],
    server: {
        host: '127.0.0.1',
        port: Number(process.env.EN1090_PREVIEW_PORT ?? 8098),
        strictPort: true,
        proxy: connection.mode === 'connected' ? { '/api': { target: connection.backendUrl, changeOrigin: false } } : {},
        fs: { deny: ['.env', '.env.*', '*.{crt,pem}', '**/.git/**', '**/application-local.properties*', '**/ids-carts.local.json*', '**/preview/**'] },
    },
});
