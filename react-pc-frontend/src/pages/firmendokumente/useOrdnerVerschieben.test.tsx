import { act, renderHook, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';
import { ToastProvider } from '../../components/ui/toast';
import { baueBaum } from '../../lib/ordnerBaum';
import type { FirmenOrdner } from './typen';
import { useOrdnerVerschieben } from './useOrdnerVerschieben';

vi.mock('./api', async (original) => ({ ...(await original<typeof import('./api')>()), verschiebeOrdner: vi.fn() }));
const { verschiebeOrdner } = await import('./api');

const o = (id: number, name: string, parentId: number | null, eingeschraenkt = false): FirmenOrdner => ({
    id, name, parentId, anzahlDokumente: 0, anzahlUeberfaellig: 0, anzahlBaldFaellig: 0, darfAendern: true, eingeschraenkt,
});
const BAUM = baueBaum([o(1, 'Fotos Baustelle', null), o(2, 'Montag', 1), o(4, 'Prüfungen', null), o(6, 'Geschäftsleitung', null, true), o(7, 'Verträge', 6, true)]);
const [FOTOS, MONTAG, PRUEFUNGEN, GESCHAEFT, VERTRAEGE] = [1, 2, 4, 6, 7].map((id) => BAUM.byId.get(id) as FirmenOrdner);

const huelle = ({ children }: { children: ReactNode }) => <ToastProvider><ConfirmProvider>{children}</ConfirmProvider></ToastProvider>;

function starte() {
    const onVerschoben = vi.fn();
    const hook = renderHook(() => useOrdnerVerschieben(BAUM, onVerschoben), { wrapper: huelle });
    return { ...hook, onVerschoben };
}

afterEach(() => vi.clearAllMocks());

describe('useOrdnerVerschieben', () => {
    it('verschiebt per Ziehen, meldet den Erfolg und bietet „Rückgängig“ an', async () => {
        const user = userEvent.setup();
        vi.mocked(verschiebeOrdner).mockResolvedValue({ ...MONTAG, parentId: 4 });
        const { result, onVerschoben } = starte();
        await act(() => result.current.perZiehen(MONTAG, PRUEFUNGEN));
        expect(verschiebeOrdner).toHaveBeenCalledWith(2, 4);
        expect(onVerschoben).toHaveBeenCalledTimes(1);
        expect(await screen.findByText('„Montag“ nach „Prüfungen“ verschoben.')).toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: 'Rückgängig' }));
        expect(verschiebeOrdner).toHaveBeenLastCalledWith(2, 1);
        expect(await screen.findByText('„Montag“ ist wieder in „Fotos Baustelle“.')).toBeInTheDocument();
        expect(onVerschoben).toHaveBeenCalledTimes(2);
    });

    it('macht einen Ordner zum Hauptordner – Rückgängig holt ihn zurück in seinen alten Ordner', async () => {
        const user = userEvent.setup();
        vi.mocked(verschiebeOrdner).mockResolvedValue(MONTAG);
        const { result } = starte();
        await act(() => result.current.perZiehen(MONTAG, null));
        expect(verschiebeOrdner).toHaveBeenCalledWith(2, null);
        await screen.findByText('„Montag“ ist jetzt ein Hauptordner.');
        await user.click(screen.getByRole('button', { name: 'Rückgängig' }));
        expect(verschiebeOrdner).toHaveBeenLastCalledWith(2, 1);
    });

    it('meldet, wenn „Rückgängig“ nicht klappt', async () => {
        const user = userEvent.setup();
        vi.mocked(verschiebeOrdner).mockResolvedValueOnce(FOTOS).mockRejectedValueOnce(new Error('Den Ordner gibt es nicht mehr.'));
        const { result } = starte();
        await act(() => result.current.perZiehen(FOTOS, PRUEFUNGEN));
        await user.click(await screen.findByRole('button', { name: 'Rückgängig' }));
        expect(verschiebeOrdner).toHaveBeenLastCalledWith(1, null);
        expect(await screen.findByText('Den Ordner gibt es nicht mehr.')).toBeInTheDocument();
    });

    it('fragt nach, bevor sich die Sichtbarkeit ändert – Abbrechen verschiebt nichts', async () => {
        const user = userEvent.setup();
        vi.mocked(verschiebeOrdner).mockResolvedValue({ ...MONTAG, parentId: 6 });
        const { result, onVerschoben } = starte();
        let ergebnis: Promise<boolean> = Promise.resolve(true);
        act(() => { ergebnis = result.current.perZiehen(MONTAG, GESCHAEFT); });
        const dialog = await screen.findByRole('dialog', { name: 'Ordner verschieben?' });
        expect(dialog).toHaveTextContent('„Montag“ nach „Geschäftsleitung“ verschieben? Danach sehen ihn nur noch die Abteilungen, die „Geschäftsleitung“ sehen dürfen.');
        await user.click(within(dialog).getByRole('button', { name: 'Abbrechen' }));
        await act(async () => { expect(await ergebnis).toBe(false); });
        expect(verschiebeOrdner).not.toHaveBeenCalled();
        expect(onVerschoben).not.toHaveBeenCalled();

        act(() => { ergebnis = result.current.perZiehen(MONTAG, GESCHAEFT); });
        await user.click(within(await screen.findByRole('dialog', { name: 'Ordner verschieben?' })).getByRole('button', { name: 'Verschieben' }));
        await act(async () => { expect(await ergebnis).toBe(true); });
        expect(verschiebeOrdner).toHaveBeenCalledWith(2, 6);
    });

    it('zeigt beim Ziehen die Meldung des Servers; im Dialog wirft der Fehler, damit er dort steht', async () => {
        vi.mocked(verschiebeOrdner).mockRejectedValue(new Error('Ein Ordner kann nicht in sich selbst oder einen seiner Unterordner verschoben werden.'));
        const { result, onVerschoben } = starte();
        await act(async () => { expect(await result.current.perZiehen(MONTAG, PRUEFUNGEN)).toBe(false); });
        expect(onVerschoben).not.toHaveBeenCalled();
        expect(await screen.findByText('Ein Ordner kann nicht in sich selbst oder einen seiner Unterordner verschoben werden.')).toBeInTheDocument();
        await expect(result.current.verschieben(MONTAG, PRUEFUNGEN)).rejects.toThrow('Ein Ordner kann nicht in sich selbst');
    });

    it('bietet kein „Rückgängig“, wenn ein eingeschränkter Hauptordner seine Einstellung verliert – und sagt das vorher', async () => {
        const user = userEvent.setup();
        vi.mocked(verschiebeOrdner).mockResolvedValue({ ...GESCHAEFT, parentId: 4, eingeschraenkt: false });
        const { result, onVerschoben } = starte();
        let ergebnis: Promise<boolean> = Promise.resolve(false);
        act(() => { ergebnis = result.current.perZiehen(GESCHAEFT, PRUEFUNGEN); });
        const dialog = await screen.findByRole('dialog', { name: 'Ordner verschieben?' });
        expect(dialog).toHaveTextContent('Die Einstellung „Wer darf das sehen?“ dieses Ordners geht dabei verloren.');
        await user.click(within(dialog).getByRole('button', { name: 'Verschieben' }));
        await act(async () => { expect(await ergebnis).toBe(true); });
        expect(verschiebeOrdner).toHaveBeenCalledWith(6, 4);
        expect(await screen.findByText('„Geschäftsleitung“ nach „Prüfungen“ verschoben.')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Rückgängig' })).toBeNull();
        expect(onVerschoben).toHaveBeenCalledTimes(1);
    });

    it('bietet kein „Rückgängig“, wenn ein offener Hauptordner in einen eingeschränkten Zweig kommt', async () => {
        const user = userEvent.setup();
        vi.mocked(verschiebeOrdner).mockResolvedValue({ ...FOTOS, parentId: 7, eingeschraenkt: true });
        const { result } = starte();
        let ergebnis: Promise<boolean> = Promise.resolve(false);
        act(() => { ergebnis = result.current.perZiehen(FOTOS, VERTRAEGE); });
        const dialog = await screen.findByRole('dialog', { name: 'Ordner verschieben?' });
        expect(dialog).toHaveTextContent('Zurückholen geht danach nur über einen Admin.');
        await user.click(within(dialog).getByRole('button', { name: 'Verschieben' }));
        await act(async () => { expect(await ergebnis).toBe(true); });
        expect(await screen.findByText('„Fotos Baustelle“ nach „Verträge“ verschoben.')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Rückgängig' })).toBeNull();
    });
});
