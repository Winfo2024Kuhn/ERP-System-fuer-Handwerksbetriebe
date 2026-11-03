import { act, renderHook, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import type { FirmenOrdner } from './typen';
import { useDokumentVerschieben } from './useDokumentVerschieben';

vi.mock('./api', async (original) => ({ ...(await original<typeof import('./api')>()), speichereDokument: vi.fn() }));
const { speichereDokument } = await import('./api');

const ZIEL: FirmenOrdner = { id: 3, name: 'Leitern', parentId: 1, anzahlDokumente: 0, anzahlUeberfaellig: 0, anzahlBaldFaellig: 0, darfAendern: true, eingeschraenkt: false };
const DOK = { id: 10, ordnerId: 2, name: 'Torprüfung', faelligAm: '2027-01-31' };

const huelle = ({ children }: { children: ReactNode }) => <ToastProvider>{children}</ToastProvider>;

afterEach(() => vi.clearAllMocks());

describe('useDokumentVerschieben', () => {
    it('schickt alle drei Felder mit dem neuen Ordner und meldet den Erfolg', async () => {
        vi.mocked(speichereDokument).mockResolvedValue({} as never);
        const onVerschoben = vi.fn();
        const { result } = renderHook(() => useDokumentVerschieben(onVerschoben), { wrapper: huelle });
        await act(() => result.current(DOK, ZIEL));
        expect(speichereDokument).toHaveBeenCalledWith(10, { name: 'Torprüfung', ordnerId: 3, faelligAm: '2027-01-31' });
        expect(onVerschoben).toHaveBeenCalled();
        expect(await screen.findByText('„Torprüfung“ nach „Leitern“ verschoben.')).toBeInTheDocument();
    });

    it('zeigt die Meldung des Servers, wenn es nicht klappt', async () => {
        vi.mocked(speichereDokument).mockRejectedValue(new Error('Du darfst in diesem Ordner nichts ändern.'));
        const onVerschoben = vi.fn();
        const { result } = renderHook(() => useDokumentVerschieben(onVerschoben), { wrapper: huelle });
        await act(() => result.current(DOK, ZIEL));
        expect(onVerschoben).not.toHaveBeenCalled();
        expect(await screen.findByText('Du darfst in diesem Ordner nichts ändern.')).toBeInTheDocument();
    });

    it('nimmt Name und Fällig am aus der aktuellen Liste, falls sich das Dokument inzwischen geändert hat', async () => {
        vi.mocked(speichereDokument).mockResolvedValue({} as never);
        const aktuell = [{
            id: 10, ordnerId: 2, name: 'Torprüfung 2027', originalDateiname: 'scan.pdf', groesseBytes: 1, hochgeladenAm: '2026-11-02T09:14:00',
            hochgeladenVon: 'Max Mustermann', faelligAm: null, url: '/api/firmendokumente/10/datei', dateiart: 'PDF' as const, darfAendern: true,
        }];
        const { result } = renderHook(() => useDokumentVerschieben(vi.fn(), aktuell), { wrapper: huelle });
        await act(() => result.current(DOK, ZIEL));
        expect(speichereDokument).toHaveBeenCalledWith(10, { name: 'Torprüfung 2027', ordnerId: 3, faelligAm: null });
        expect(await screen.findByText('„Torprüfung 2027“ nach „Leitern“ verschoben.')).toBeInTheDocument();
    });
});
