import { act, renderHook, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { legeOrdnerAn, speichereOrdner } from './api';
import { testBaum, testOrdner } from './testDaten';
import { useOrdnerAktionen } from './useOrdnerAktionen';

vi.mock('./api', async (original) => ({
    ...(await original<typeof import('./api')>()),
    legeOrdnerAn: vi.fn(),
    speichereOrdner: vi.fn(),
}));

const mitToasts = ({ children }: { children: ReactNode }) => <ToastProvider>{children}</ToastProvider>;

function zeige() {
    const neuLaden = vi.fn(async () => {});
    const ersetzeOrdner = vi.fn();
    const waehle = vi.fn();
    const aufklappen = vi.fn();
    const { baum, zaehler } = testBaum();
    const hook = renderHook(() => useOrdnerAktionen({ baum, zaehler, neuLaden, ersetzeOrdner, waehle, aufklappen }), { wrapper: mitToasts });
    return { ...hook, neuLaden, ersetzeOrdner, waehle, aufklappen };
}

describe('useOrdnerAktionen', () => {
    beforeEach(() => {
        vi.mocked(legeOrdnerAn).mockReset().mockResolvedValue(testOrdner(7, 'Edelstahl', 2));
        vi.mocked(speichereOrdner).mockReset().mockImplementation(async (id, a) => testOrdner(id, a.name, null));
    });

    it('neuer Ordner und Umbenennen schließen sich gegenseitig aus', () => {
        const { result, aufklappen } = zeige();
        act(() => result.current.umbenennenStarten(4));
        act(() => result.current.neuenOrdnerStarten(2));
        expect(result.current.neuerOrdner).toEqual({ parentId: 2 });
        expect(result.current.umbenennenId).toBeNull();
        expect(aufklappen).toHaveBeenCalledWith(2, true);
        act(() => result.current.umbenennenStarten(4));
        expect(result.current.neuerOrdner).toBeNull();
        expect(result.current.umbenennenId).toBe(4);
    });

    it('legt einen Unterordner an, nimmt Leistungen mit, wählt ihn und meldet es', async () => {
        const { result, neuLaden, waehle } = zeige();
        act(() => result.current.neuenOrdnerStarten(2));
        await act(() => result.current.ordnerAnlegen(2, 'Edelstahl'));
        expect(legeOrdnerAn).toHaveBeenCalledWith({ name: 'Edelstahl', parentId: 2, leistungenMitnehmen: true });
        expect(result.current.neuerOrdner).toBeNull();
        expect(neuLaden).toHaveBeenCalled();
        expect(waehle).toHaveBeenCalledWith(7);
        expect(await screen.findByText('Ordner „Edelstahl“ angelegt – 2 Leistungen sind mitgewandert.')).toBeInTheDocument();
    });

    it('Hauptordner ohne Leistungen: einfache Meldung', async () => {
        vi.mocked(legeOrdnerAn).mockResolvedValue(testOrdner(8, 'Dach', null));
        const { result } = zeige();
        await act(() => result.current.ordnerAnlegen(null, 'Dach'));
        expect(legeOrdnerAn).toHaveBeenCalledWith({ name: 'Dach', parentId: null, leistungenMitnehmen: false });
        expect(await screen.findByText('Ordner „Dach“ angelegt.')).toBeInTheDocument();
    });

    it('benennt um und übernimmt den Ordner', async () => {
        const { result, ersetzeOrdner } = zeige();
        act(() => result.current.umbenennenStarten(4));
        await act(() => result.current.ordnerUmbenennen(4, 'Holz'));
        expect(ersetzeOrdner).toHaveBeenCalledWith(expect.objectContaining({ id: 4, name: 'Holz' }));
        expect(result.current.umbenennenId).toBeNull();
        expect(await screen.findByText('Umbenannt in „Holz“.')).toBeInTheDocument();
    });

    it('Bild ablegen: prüft die Datei vorher und meldet Fehler', async () => {
        const { result, ersetzeOrdner } = zeige();
        await act(() => result.current.bildAblegen(4, new File(['x'], 'liste.pdf', { type: 'application/pdf' })));
        expect(speichereOrdner).not.toHaveBeenCalled();
        expect(await screen.findByRole('alert')).toBeInTheDocument();

        await act(() => result.current.bildAblegen(4, new File(['x'], 'holz.png', { type: 'image/png' })));
        expect(speichereOrdner).toHaveBeenCalledWith(4, expect.objectContaining({ bild: expect.any(File) }));
        expect(ersetzeOrdner).toHaveBeenCalled();
        expect(await screen.findByText('Bild für „Holzbau“ übernommen.')).toBeInTheDocument();

        vi.mocked(speichereOrdner).mockRejectedValueOnce(new Error('Bild zu groß für den Server.'));
        await act(() => result.current.bildAblegen(4, new File(['x'], 'holz.png', { type: 'image/png' })));
        expect(await screen.findByText('Bild zu groß für den Server.')).toBeInTheDocument();
    });
});
