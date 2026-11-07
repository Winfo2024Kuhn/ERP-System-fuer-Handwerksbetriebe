import { act, renderHook, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { ladeLeistungen, ladeOrdner, speichereLeistung } from './api';
import { testLeistung, testOrdner, TEST_LEISTUNGEN, TEST_ORDNER } from './testDaten';
import { useLeistungenDaten } from './useLeistungenDaten';

vi.mock('./api', () => ({ ladeOrdner: vi.fn(), ladeLeistungen: vi.fn(), speichereLeistung: vi.fn() }));

const mitToasts = ({ children }: { children: ReactNode }) => <ToastProvider>{children}</ToastProvider>;
const zeige = () => renderHook(() => useLeistungenDaten(), { wrapper: mitToasts });

describe('useLeistungenDaten', () => {
    beforeEach(() => {
        vi.mocked(ladeOrdner).mockReset().mockResolvedValue(TEST_ORDNER);
        vi.mocked(ladeLeistungen).mockReset().mockResolvedValue(TEST_LEISTUNGEN);
        vi.mocked(speichereLeistung).mockReset();
        vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 204 })));
    });
    afterEach(() => vi.unstubAllGlobals());

    it('lädt Ordner und Leistungen und baut Baum und Zähler', async () => {
        const { result } = zeige();
        expect(result.current.ladeStatus).toBe('laedt');
        await waitFor(() => expect(result.current.ladeStatus).toBe('fertig'));
        expect(result.current.ordner).toHaveLength(3);
        expect(result.current.baum.byId.get(2)?.name).toBe('Geländer');
        expect(result.current.zaehler.get(1)).toEqual({ direkt: 0, gesamt: 2 });
    });

    it('zeigt einen Ladefehler und lädt auf Wunsch erneut', async () => {
        vi.mocked(ladeOrdner).mockRejectedValueOnce(new Error('Server nicht erreichbar.'));
        const { result } = zeige();
        await waitFor(() => expect(result.current.ladeStatus).toBe('fehler'));
        expect(result.current.ladeFehler).toBe('Server nicht erreichbar.');
        act(() => result.current.erneutLaden());
        expect(result.current.ladeStatus).toBe('laedt');
        await waitFor(() => expect(result.current.ladeStatus).toBe('fertig'));
        expect(result.current.ladeFehler).toBeNull();
    });

    it('lädt im Hintergrund neu; ein Fehler kommt als Meldung, die Daten bleiben', async () => {
        const { result } = zeige();
        await waitFor(() => expect(result.current.ladeStatus).toBe('fertig'));
        vi.mocked(ladeOrdner).mockResolvedValueOnce([...TEST_ORDNER, testOrdner(7, 'Edelstahl', 2)]);
        await act(() => result.current.neuLaden());
        expect(result.current.baum.byId.has(7)).toBe(true);

        vi.mocked(ladeOrdner).mockRejectedValueOnce(new Error('Neu laden ging schief.'));
        await act(() => result.current.neuLaden());
        expect(await screen.findByText('Neu laden ging schief.')).toBeInTheDocument();
        expect(result.current.ladeStatus).toBe('fertig');
        expect(result.current.baum.byId.has(7)).toBe(true);
    });

    it('ersetzt Ordner und behält die Projektzahl, wenn die Antwort keine mitbringt', async () => {
        vi.mocked(ladeOrdner).mockResolvedValue([testOrdner(4, 'Holzbau', null, { projektAnzahl: 5 })]);
        const { result } = zeige();
        await waitFor(() => expect(result.current.ladeStatus).toBe('fertig'));
        act(() => result.current.ersetzeOrdner(testOrdner(4, 'Holz', null, { projektAnzahl: 0 })));
        expect(result.current.baum.byId.get(4)).toMatchObject({ name: 'Holz', projektAnzahl: 5 });
        act(() => result.current.entferneOrdner(4));
        expect(result.current.ordner).toHaveLength(0);
    });

    it('Leistung speichern: neue wird angehängt, bestehende ersetzt', async () => {
        const { result } = zeige();
        await waitFor(() => expect(result.current.ladeStatus).toBe('fertig'));
        vi.mocked(speichereLeistung).mockResolvedValueOnce(testLeistung(12, 'Handlauf', 2));
        await act(async () => { await result.current.leistungSpeichern({ name: 'Handlauf', beschreibung: '', preis: 10, einheit: 'STUECK', ordnerId: 2 }); });
        expect(result.current.leistungen.map((l) => l.id)).toEqual([10, 11, 12]);

        vi.mocked(speichereLeistung).mockResolvedValueOnce({ ...testLeistung(11, 'Glasbrüstung VSG', 2) });
        const neu = await act(() => result.current.leistungSpeichern(testLeistung(11, 'Glasbrüstung VSG', 2)));
        expect(neu.name).toBe('Glasbrüstung VSG');
        expect(result.current.leistungen.find((l) => l.id === 11)?.name).toBe('Glasbrüstung VSG');
        act(() => result.current.entferneLeistung(11));
        expect(result.current.leistungen.map((l) => l.id)).toEqual([10, 12]);
    });

    it('blendet zum Löschen geplante Einträge sofort aus', async () => {
        const { result } = zeige();
        await waitFor(() => expect(result.current.ladeStatus).toBe('fertig'));
        act(() => result.current.planen({ schluessel: 'ordner:4', url: '/api/produktkategorien/4', name: 'Holzbau', fehlerText: 'x', onGeloescht: () => {} }));
        expect(result.current.baum.byId.has(4)).toBe(false);
        expect(result.current.ordnerRoh.some((o) => o.id === 4)).toBe(true);
    });
});
