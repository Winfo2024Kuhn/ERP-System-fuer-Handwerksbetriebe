import { act, renderHook, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { ladeLoeschpruefung, ladeVerwendung, loescheOrdnerMitModus, loescheSofort } from './api';
import { testBaum, testLeistung } from './testDaten';
import { useLeistungenLoeschen } from './useLeistungenLoeschen';
import type { Loeschpruefung } from './typen';

vi.mock('./api', async (original) => ({
    ...(await original<typeof import('./api')>()),
    ladeLoeschpruefung: vi.fn(),
    ladeVerwendung: vi.fn(),
    loescheOrdnerMitModus: vi.fn(),
    loescheSofort: vi.fn(),
}));

const mitToasts = ({ children }: { children: ReactNode }) => <ToastProvider>{children}</ToastProvider>;

const pruefung = (extra: Partial<Loeschpruefung> = {}): Loeschpruefung => ({
    kategorieId: 2, bezeichnung: 'Geländer', pfad: 'Metallbau › Geländer', hatBezuege: false, unterordnerAnzahl: 0,
    leistungAnzahl: 0, projektAnzahl: 0, zeitbuchungAnzahl: 0, unterordner: [], leistungen: [], projekte: [], ...extra,
});

function zeige(gewaehlt: number | null = null) {
    const fn = {
        waehle: vi.fn(), neuLaden: vi.fn(async () => {}), planen: vi.fn(),
        entferneOrdner: vi.fn(), entferneLeistung: vi.fn(), setDialog: vi.fn(),
    };
    const { baum } = testBaum();
    const hook = renderHook(() => useLeistungenLoeschen({ baum, gewaehlt, ...fn }), { wrapper: mitToasts });
    return { ...hook, ...fn };
}

describe('useLeistungenLoeschen', () => {
    beforeEach(() => {
        vi.mocked(ladeLoeschpruefung).mockReset().mockResolvedValue(pruefung());
        vi.mocked(ladeVerwendung).mockReset().mockResolvedValue({ dokumentAnzahl: 0, dokumente: [] });
        vi.mocked(loescheOrdnerMitModus).mockReset().mockResolvedValue(undefined);
        vi.mocked(loescheSofort).mockReset().mockResolvedValue(undefined);
    });

    it('Ordner ohne Bezüge: plant das Löschen mit „Rückgängig“ und geht zum Eltern-Ordner', async () => {
        const { result, planen, waehle, entferneOrdner, setDialog } = zeige(2);
        await act(() => result.current.ordnerLoeschenStarten(2));
        expect(waehle).toHaveBeenCalledWith(1);
        expect(setDialog).not.toHaveBeenCalled();
        const auftrag = planen.mock.calls[0][0];
        expect(auftrag).toMatchObject({ schluessel: 'ordner:2', url: '/api/produktkategorien/2', name: 'Geländer' });
        auftrag.onGeloescht();
        expect(entferneOrdner).toHaveBeenCalledWith(2);
    });

    it('Ordner mit Bezügen: öffnet den Dialog statt zu löschen', async () => {
        const mitBezug = pruefung({ hatBezuege: true, projektAnzahl: 3 });
        vi.mocked(ladeLoeschpruefung).mockResolvedValue(mitBezug);
        const { result, planen, setDialog } = zeige();
        await act(() => result.current.ordnerLoeschenStarten(2));
        expect(setDialog).toHaveBeenCalledWith({ art: 'ordnerLoeschen', pruefung: mitBezug });
        expect(planen).not.toHaveBeenCalled();
    });

    it('Prüfung scheitert: Fehler als Toast', async () => {
        vi.mocked(ladeLoeschpruefung).mockRejectedValue(new Error('Prüfung nicht möglich.'));
        const { result } = zeige();
        await act(() => result.current.ordnerLoeschenStarten(2));
        expect(await screen.findByText('Prüfung nicht möglich.')).toBeInTheDocument();
    });

    it('Löschen mit Umzug wählt das Ziel und meldet, wohin der Inhalt kam', async () => {
        const { result, waehle, neuLaden, setDialog } = zeige(2);
        await act(() => result.current.ordnerLoeschenMitModus(pruefung({ hatBezuege: true }), 'UMZIEHEN', 4));
        expect(loescheOrdnerMitModus).toHaveBeenCalledWith(2, 'UMZIEHEN', 4);
        expect(setDialog).toHaveBeenCalledWith(null);
        expect(waehle).toHaveBeenCalledWith(4);
        expect(neuLaden).toHaveBeenCalled();
        expect(await screen.findByText('„Geländer“ gelöscht – der Inhalt liegt jetzt in „Holzbau“.')).toBeInTheDocument();
    });

    it('Löschen samt Inhalt: gewählter Ordner darin → zurück zum Eltern-Ordner', async () => {
        const { result, waehle } = zeige(2);
        await act(() => result.current.ordnerLoeschenMitModus(pruefung({ hatBezuege: true }), 'ALLES', null));
        expect(waehle).toHaveBeenCalledWith(1);
        expect(await screen.findByText('„Geländer“ und sein Inhalt wurden gelöscht.')).toBeInTheDocument();
    });

    it('Leistung ohne Dokumente: plant das Löschen', async () => {
        const { result, planen, entferneLeistung } = zeige();
        await act(() => result.current.leistungLoeschenStarten(testLeistung(11, 'Glasbrüstung', 2)));
        const auftrag = planen.mock.calls[0][0];
        expect(auftrag).toMatchObject({ schluessel: 'leistung:11', url: '/api/leistungen/11' });
        auftrag.onGeloescht();
        expect(entferneLeistung).toHaveBeenCalledWith(11);
    });

    it('Leistung in Dokumenten: Dialog; „Trotzdem löschen“ löscht sofort', async () => {
        const verwendung = { dokumentAnzahl: 1, dokumente: [] };
        vi.mocked(ladeVerwendung).mockResolvedValue(verwendung);
        const leistung = testLeistung(11, 'Glasbrüstung', 2);
        const { result, setDialog, entferneLeistung } = zeige();
        await act(() => result.current.leistungLoeschenStarten(leistung));
        expect(setDialog).toHaveBeenCalledWith({ art: 'leistungLoeschen', leistung, verwendung });

        await act(() => result.current.leistungTrotzdemLoeschen(leistung));
        expect(loescheSofort).toHaveBeenCalledWith('/api/leistungen/11', '„Glasbrüstung“ konnte nicht gelöscht werden.');
        expect(entferneLeistung).toHaveBeenCalledWith(11);
        expect(setDialog).toHaveBeenLastCalledWith(null);
        expect(await screen.findByText('„Glasbrüstung“ gelöscht.')).toBeInTheDocument();
    });
});
