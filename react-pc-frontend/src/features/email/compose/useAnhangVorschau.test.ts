import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useAnhangVorschau } from './useAnhangVorschau';

const pdf = new File(['%PDF'], 'plan.pdf', { type: 'application/pdf' });
const bild = new File(['x'], 'foto.png', { type: 'image/png' });

const original = { erzeugen: URL.createObjectURL, freigeben: URL.revokeObjectURL };

function stubbeBlobAdressen() {
    let nummer = 0;
    const erzeugt = vi.fn(() => `blob:http://localhost/${++nummer}`);
    const freigegeben = vi.fn();
    URL.createObjectURL = erzeugt;
    URL.revokeObjectURL = freigegeben;
    return { erzeugt, freigegeben };
}

afterEach(() => {
    URL.createObjectURL = original.erzeugen;
    URL.revokeObjectURL = original.freigeben;
});

describe('useAnhangVorschau', () => {
    it('gibt die Blob-Adresse der PDF-Vorschau beim Schließen frei', () => {
        const { freigegeben } = stubbeBlobAdressen();
        const { result } = renderHook(() => useAnhangVorschau());
        act(() => result.current.zeigePdf(pdf));
        expect(result.current.pdfUrl).toBe('blob:http://localhost/1');
        act(() => result.current.schliessePdf());
        expect(result.current.pdfUrl).toBeNull();
        expect(freigegeben).toHaveBeenCalledWith('blob:http://localhost/1');
    });

    it('gibt offene Vorschauen beim Abbauen frei', () => {
        const { freigegeben } = stubbeBlobAdressen();
        const { result, unmount } = renderHook(() => useAnhangVorschau());
        act(() => result.current.zeigeDatei(pdf));
        act(() => result.current.zeigeDatei(bild));
        unmount();
        expect(freigegeben).toHaveBeenCalledWith('blob:http://localhost/1');
        expect(freigegeben).toHaveBeenCalledWith('blob:http://localhost/2');
    });
});
