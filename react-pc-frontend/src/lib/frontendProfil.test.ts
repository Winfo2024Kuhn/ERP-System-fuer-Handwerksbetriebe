import { afterEach, describe, expect, it, vi } from 'vitest';
import { aktuellesFrontendProfil } from './frontendProfil';

afterEach(() => { vi.restoreAllMocks(); });

describe('aktuellesFrontendProfil', () => {
    it('liest das Frontend-Profil nur, wenn es gültig ist', () => {
        expect(aktuellesFrontendProfil()).toBeNull();
        localStorage.setItem('frontendUserSelection', JSON.stringify({ id: 3, displayName: 'Max Mustermann' }));
        expect(aktuellesFrontendProfil()).toEqual({ id: 3, displayName: 'Max Mustermann' });
        localStorage.setItem('frontendUserSelection', '{kaputt');
        vi.spyOn(console, 'warn').mockImplementation(() => {});
        expect(aktuellesFrontendProfil()).toBeNull();
    });
});
