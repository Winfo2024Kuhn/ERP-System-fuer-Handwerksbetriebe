import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useAdresseSpeichern } from './useAdresseSpeichern';
import { useEmailAnhaenge } from './useEmailAnhaenge';
import { neuerAnhang } from './anhangGrenze';

const { toastFehler } = vi.hoisted(() => ({ toastFehler: vi.fn() }));
vi.mock('../../../components/ui/toast', () => ({
    useToast: () => ({ error: toastFehler, success: vi.fn(), warning: vi.fn(), info: vi.fn() }),
}));
vi.mock('../../../lib/bildKomprimierung', () => ({
    komprimiereBilderFuerEmail: (dateien: File[]) => Promise.resolve(dateien),
    komprimiereBildFuerEmail: (datei: File) => Promise.resolve(datei),
}));

const ADRESSE_FEHLT = 'Adresse konnte nicht gespeichert werden. Bitte beim Kunden von Hand eintragen.';

afterEach(() => { vi.unstubAllGlobals(); toastFehler.mockReset(); vi.restoreAllMocks(); });

describe('Fehler im E-Mail-Formular erscheinen als Toast', () => {
    it('Adresse speichern scheitert: Toast, Formular schließt trotzdem', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => undefined);
        vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new Error('offline'))));
        const fertig = vi.fn();
        const { result } = renderHook(() => useAdresseSpeichern({ kundeId: 3, zuordnung: null, fertig }));
        act(() => result.current.anbieten('max.mustermann@example.com'));
        await act(async () => { await result.current.speichern('kunde'); });
        expect(toastFehler).toHaveBeenCalledWith(ADRESSE_FEHLT);
        expect(fertig).toHaveBeenCalled();
    });

    it('Adresse speichern mit Serverfehler: derselbe Toast', async () => {
        vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response('', { status: 500 }))));
        const fertig = vi.fn();
        const { result } = renderHook(() => useAdresseSpeichern({ kundeId: 3, zuordnung: null, fertig }));
        act(() => result.current.anbieten('max.mustermann@example.com'));
        await act(async () => { await result.current.speichern('kunde'); });
        expect(toastFehler).toHaveBeenCalledWith(ADRESSE_FEHLT);
        expect(fertig).toHaveBeenCalled();
    });

    it('Datei aus dem Projekt lässt sich nicht laden: Toast und Hinweis', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => undefined);
        vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response('', { status: 500 }))));
        const setError = vi.fn();
        const { result } = renderHook(() => useEmailAnhaenge({
            uploadedFiles: [], setUploadedFiles: vi.fn(), weiterleitungsBytes: 0, markDirty: vi.fn(), setError,
        }));
        await act(async () => {
            await result.current.dokumentUmschalten({ id: 5, originalDateiname: 'plan.pdf', url: '/api/dateien/5' } as never);
        });
        expect(setError).toHaveBeenCalledWith('"plan.pdf" konnte nicht angehängt werden.');
        expect(toastFehler).toHaveBeenCalledWith('"plan.pdf" konnte nicht angehängt werden.');
    });

    it('zu große Anhänge: Toast und Hinweis', async () => {
        const riesig = new File([new Uint8Array(15_000_000)], 'riesig.pdf', { type: 'application/pdf' });
        const setError = vi.fn();
        let liste = [neuerAnhang(new File(['x'], 'a.txt'))];
        const setUploadedFiles = vi.fn((neu: unknown) => { liste = typeof neu === 'function' ? neu(liste) : neu; });
        const { result } = renderHook(() => useEmailAnhaenge({
            uploadedFiles: liste, setUploadedFiles, weiterleitungsBytes: 0, markDirty: vi.fn(), setError,
        }));
        await act(async () => { await result.current.hochladen([riesig] as unknown as FileList); });
        expect(toastFehler).toHaveBeenCalledWith(expect.stringMatching(/^Anhänge dürfen zusammen höchstens/));
    });
});
