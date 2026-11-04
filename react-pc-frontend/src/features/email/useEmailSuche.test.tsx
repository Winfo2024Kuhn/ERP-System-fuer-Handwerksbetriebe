import { act, renderHook, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import type { EmailItem, FolderType } from './emailCenterModel';
import { SUCHE_ENTPRELLEN_MS } from './emailSuche';
import { useEmailSuche } from './useEmailSuche';

const mail = (id: number): EmailItem => ({ id, type: 'EMAIL', direction: 'IN', subject: `Geländer ${id}`, fromAddress: 'max@example.com' });
const seite = (ids: number[], hatMehr: boolean) => new Response(JSON.stringify({ eintraege: ids.map(mail), hatMehr, seite: 0, seitenGroesse: 25 }));

/** Antworten von Hand freigeben, um späte Antworten nachzustellen. */
function offeneAntworten() {
    const offen: { url: string; signal?: AbortSignal | null; antworten: (res: Response) => void }[] = [];
    const fetchMock = vi.fn((url: string, init?: RequestInit) => new Promise<Response>((resolve, reject) => {
        init?.signal?.addEventListener('abort', () => reject(new DOMException('abgebrochen', 'AbortError')));
        offen.push({ url, signal: init?.signal, antworten: resolve });
    }));
    vi.stubGlobal('fetch', fetchMock);
    return { offen, fetchMock };
}

const wrapper = ({ children }: { children: ReactNode }) => <ToastProvider>{children}</ToastProvider>;
const starte = (ordner: FolderType = 'inbox') => renderHook(({ o }) => useEmailSuche(o), { wrapper, initialProps: { o: ordner } });
const warteEntprellen = () => act(async () => { await vi.advanceTimersByTimeAsync(SUCHE_ENTPRELLEN_MS); });
const params = (url: string) => new URL(url, 'http://localhost').searchParams;

describe('useEmailSuche', () => {
    beforeEach(() => { vi.useFakeTimers({ shouldAdvanceTime: true }); });
    afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); });

    it('fragt unter 2 Zeichen nicht und zeigt dann die normale Ordnerliste', async () => {
        const { fetchMock } = offeneAntworten();
        const { result } = starte();
        act(() => result.current.setBegriff('m'));
        await warteEntprellen();
        expect(result.current.aktiv).toBe(false);
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it('wartet 400 ms nach dem letzten Tastendruck und fragt nur einmal, im aktiven Ordner', async () => {
        const { offen } = offeneAntworten();
        const { result } = starte();
        act(() => result.current.setBegriff('mü'));
        await act(async () => { await vi.advanceTimersByTimeAsync(200); });
        act(() => result.current.setBegriff('müller geländer'));
        expect(result.current.laedt).toBe(true);
        await warteEntprellen();
        expect(offen).toHaveLength(1);
        expect(params(offen[0].url).get('q')).toBe('müller geländer');
        expect(params(offen[0].url).get('ordner')).toBe('inbox');
        expect(params(offen[0].url).get('page')).toBe('0');

        await act(async () => { offen[0].antworten(seite([1, 2], false)); });
        expect(result.current.laedt).toBe(false);
        expect(result.current.treffer.map(e => e.id)).toEqual([1, 2]);
    });

    it('sucht im Modus „Alle Ordner“ mit ordner=alle und leert dabei die alte Liste', async () => {
        const { offen } = offeneAntworten();
        const { result } = starte();
        act(() => result.current.setBegriff('geländer'));
        await warteEntprellen();
        await act(async () => { offen[0].antworten(seite([1], false)); });

        act(() => result.current.setModus('alle'));
        expect(result.current.treffer).toEqual([]);
        await warteEntprellen();
        expect(params(offen[1].url).get('ordner')).toBe('alle');
    });

    it('verwirft die späte Antwort einer älteren Suche', async () => {
        const { offen } = offeneAntworten();
        const { result } = starte();
        act(() => result.current.setBegriff('müller'));
        await warteEntprellen();
        act(() => result.current.setBegriff('mustermann'));
        expect(offen[0].signal?.aborted).toBe(true);
        await warteEntprellen();
        await act(async () => { offen[1].antworten(seite([7], false)); });
        await act(async () => { offen[0].antworten(seite([1], false)); });
        expect(result.current.treffer.map(e => e.id)).toEqual([7]);
    });

    it('lädt die nächste Seite nach und hängt sie an', async () => {
        const { offen } = offeneAntworten();
        const { result } = starte();
        act(() => result.current.setBegriff('geländer'));
        await warteEntprellen();
        await act(async () => { offen[0].antworten(seite([1, 2], true)); });
        expect(result.current.hatMehr).toBe(true);

        act(() => result.current.mehrLaden());
        act(() => result.current.mehrLaden()); // Doppelklick löst keine zweite Anfrage aus
        expect(result.current.laedtMehr).toBe(true);
        expect(offen).toHaveLength(2);
        expect(params(offen[1].url).get('page')).toBe('1');
        await act(async () => { offen[1].antworten(seite([3], false)); });
        expect(result.current.treffer.map(e => e.id)).toEqual([1, 2, 3]);
        expect(result.current.hatMehr).toBe(false);
    });

    it('zeigt die Meldung des Servers und einen Fehlerzustand', async () => {
        const { offen } = offeneAntworten();
        const { result } = starte();
        act(() => result.current.setBegriff('xy'));
        await warteEntprellen();
        await act(async () => {
            offen[0].antworten(new Response(JSON.stringify({ message: 'Der Suchbegriff ist zu lang (höchstens 200 Zeichen).' }), { status: 400 }));
        });
        expect(result.current.fehler).toBe(true);
        expect(screen.getByText('Der Suchbegriff ist zu lang (höchstens 200 Zeichen).')).toBeInTheDocument();
    });

    it('meldet ein gescheitertes Nachladen und lässt die Liste stehen', async () => {
        const { offen } = offeneAntworten();
        const { result } = starte();
        act(() => result.current.setBegriff('geländer'));
        await warteEntprellen();
        await act(async () => { offen[0].antworten(seite([1], true)); });
        act(() => result.current.mehrLaden());
        await act(async () => { offen[1].antworten(new Response('', { status: 500 })); });
        expect(screen.getByText('Weitere Treffer konnten nicht geladen werden.')).toBeInTheDocument();
        expect(result.current.treffer.map(e => e.id)).toEqual([1]);
        expect(result.current.nachladeFehler).toBe(true);
    });

    it('fragt im Entwürfe-Ordner im Ordner-Modus den Server nicht', async () => {
        const { fetchMock } = offeneAntworten();
        const { result } = starte('drafts');
        act(() => result.current.setBegriff('geländer'));
        await warteEntprellen();
        expect(result.current.aktiv).toBe(false);
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it('lädt die Treffer auf Wunsch neu, z. B. nach „Rückgängig“', async () => {
        const { offen } = offeneAntworten();
        const { result } = starte();
        act(() => result.current.setBegriff('geländer'));
        await warteEntprellen();
        await act(async () => { offen[0].antworten(seite([2], false)); });
        act(() => result.current.neuLaden());
        await warteEntprellen();
        await act(async () => { offen[1].antworten(seite([1, 2], false)); });
        expect(result.current.treffer.map(e => e.id)).toEqual([1, 2]);
    });
});
