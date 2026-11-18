/**
 * Anhänge des E-Mail-Formulars: Dateien aus dem Nachrichtenfeld (Einfügen,
 * Ablegen) laufen durch dieselbe Grenze wie hochgeladene.
 * DSGVO: nur Dummy-Daten.
 */
import { useState } from 'react';
import { act, renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ANHANG_ZU_GROSS, MAX_ATTACHMENT_BYTES, type UploadedFile } from './anhangGrenze';
import { useEmailAnhaenge } from './useEmailAnhaenge';

const toast = vi.hoisted(() => ({ error: vi.fn(), success: vi.fn(), warning: vi.fn(), info: vi.fn() }));
vi.mock('../../../components/ui/toast', () => ({ useToast: () => toast }));
vi.mock('../../../lib/bildKomprimierung', () => ({
    komprimiereBilderFuerEmail: (dateien: File[]) => Promise.resolve(dateien),
    komprimiereBildFuerEmail: (datei: File) => Promise.resolve(datei),
}));

function mitAnhaengen() {
    const markDirty = vi.fn();
    const setError = vi.fn();
    const { result } = renderHook(() => {
        const [dateien, setDateien] = useState<UploadedFile[]>([]);
        return { dateien, anhaenge: useEmailAnhaenge({ uploadedFiles: dateien, setUploadedFiles: setDateien, weiterleitungsBytes: 0, markDirty, setError }) };
    });
    return { result, markDirty, setError };
}

describe('useEmailAnhaenge.hochladen', () => {
    it('nimmt Dateien als Liste an und meldet, wie viele angehängt wurden', async () => {
        const { result, markDirty } = mitAnhaengen();
        let angenommen: number | undefined;
        await act(async () => {
            angenommen = await result.current.anhaenge.hochladen([
                new File(['a'], 'Bild-1.png', { type: 'image/png' }),
                new File(['b'], 'Aufmass.pdf', { type: 'application/pdf' }),
            ]);
        });
        expect(angenommen).toBe(2);
        expect(result.current.dateien.map(eintrag => eintrag.file.name)).toEqual(['Bild-1.png', 'Aufmass.pdf']);
        expect(markDirty).toHaveBeenCalled();
    });

    it('lässt zu große Dateien weg, meldet die Grenze und zählt nur den Rest', async () => {
        const { result } = mitAnhaengen();
        const riesig = new File(['x'], 'Riesig.pdf', { type: 'application/pdf' });
        Object.defineProperty(riesig, 'size', { value: MAX_ATTACHMENT_BYTES + 1 });
        let angenommen: number | undefined;
        await act(async () => {
            angenommen = await result.current.anhaenge.hochladen([riesig, new File(['b'], 'Klein.pdf', { type: 'application/pdf' })]);
        });
        expect(angenommen).toBe(1);
        expect(toast.error).toHaveBeenCalledWith(ANHANG_ZU_GROSS);
        expect(result.current.dateien.map(eintrag => eintrag.file.name)).toEqual(['Klein.pdf']);
    });

    it('liefert 0 ohne Dateien', async () => {
        const { result } = mitAnhaengen();
        await expect(result.current.anhaenge.hochladen(null)).resolves.toBe(0);
        await expect(result.current.anhaenge.hochladen([])).resolves.toBe(0);
    });
});
