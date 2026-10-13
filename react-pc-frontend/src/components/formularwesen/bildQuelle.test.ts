import { describe, expect, it } from 'vitest';
import { sichereBildQuelle } from './bildQuelle';

describe('sichereBildQuelle', () => {
    it('lässt Rasterbilder als Data-URI durch', () => {
        expect(sichereBildQuelle('data:image/png;base64,iVBORw0KGgo=')).toBe('data:image/png;base64,iVBORw0KGgo=');
        expect(sichereBildQuelle('data:image/jpeg;base64,/9j/4AAQ')).toBe('data:image/jpeg;base64,/9j/4AAQ');
    });

    it('blockt SVG, HTML und kaputte Data-URIs', () => {
        expect(sichereBildQuelle('data:image/svg+xml;base64,PHN2Zz4=')).toBe('');
        expect(sichereBildQuelle('data:text/html;base64,PHNjcmlwdD4=')).toBe('');
        expect(sichereBildQuelle('DATA:image/png;base64,<script>')).toBe('');
    });

    it('prüft alles andere wie toSafeResourceUrl', () => {
        expect(sichereBildQuelle('/image001.png')).toBe('/image001.png');
        expect(sichereBildQuelle('https://example.invalid/logo.png')).toBe('https://example.invalid/logo.png');
        expect(sichereBildQuelle('javascript:alert(1)')).toBe('');
        expect(sichereBildQuelle('  ')).toBe('');
        expect(sichereBildQuelle(null)).toBe('');
    });
});
