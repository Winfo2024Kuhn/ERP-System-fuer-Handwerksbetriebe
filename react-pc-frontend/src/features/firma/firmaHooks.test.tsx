import type { ReactNode } from 'react';
import { act, renderHook, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';
import { useFirmaDaten } from './useFirmaDaten';
import { useFirmenLogo } from './useFirmenLogo';
import { useSteuerberaterKontakte } from './useSteuerberaterKontakte';
import { useSteuerberaterUnterlagen } from './useSteuerberaterUnterlagen';
import type { SteuerberaterKontakt, SteuerberaterUnterreiter } from './firmaTypen';

/** Dummy-Daten (DSGVO): Max Mustermann, Kanzlei Mustermann. */
const wrapper = ({ children }: { children: ReactNode }) => <ToastProvider><ConfirmProvider>{children}</ConfirmProvider></ToastProvider>;

function antwort(body: unknown, ok = true): Response {
    return { ok, json: () => Promise.resolve(body), text: () => Promise.resolve(typeof body === 'string' ? body : JSON.stringify(body)) } as Response;
}

const datei = (type: string, size = 10) => {
    const f = new File(['x'], 'logo', { type });
    Object.defineProperty(f, 'size', { value: size });
    return f;
};

const KANZLEI = { name: 'Kanzlei Mustermann', email: 'kanzlei@example.org' };

afterEach(() => vi.unstubAllGlobals());

describe('useFirmaDaten', () => {
    it('lädt die Firma, prüft vor dem Speichern und speichert die Zahlen als Zahlen', async () => {
        const fetchMock = vi.fn().mockImplementation(async (_url: string, init?: RequestInit) =>
            antwort(init?.body ? JSON.parse(String(init.body)) : { id: 1, firmenname: 'Mustermann GmbH' }));
        vi.stubGlobal('fetch', fetchMock);
        const { result } = renderHook(() => useFirmaDaten(), { wrapper });
        await act(() => result.current.ladeFirma());
        expect(result.current.firma?.tageBisZahlungserinnerung).toBe('7');

        act(() => result.current.aendern({ tageBisZahlungserinnerung: '' }));
        await act(() => result.current.speichern());
        expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(false);
        expect(await screen.findByText('Bitte Tage nach Fälligkeit eingeben.')).toBeVisible();

        act(() => result.current.aendern({ tageBisZahlungserinnerung: '10' }));
        await act(() => result.current.speichern());
        const put = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT');
        expect(put?.[0]).toBe('/api/firma');
        expect(JSON.parse(put?.[1].body)).toMatchObject({ firmenname: 'Mustermann GmbH', tageBisZahlungserinnerung: 10, bgSatzOverride: null });
        expect(await screen.findByText('Firmendaten gespeichert.')).toBeVisible();
    });
});

describe('useFirmenLogo', () => {
    it('lehnt falschen Dateityp und zu große Dateien ohne Anfrage ab', async () => {
        const fetchMock = vi.fn();
        vi.stubGlobal('fetch', fetchMock);
        const { result } = renderHook(() => useFirmenLogo('', vi.fn()), { wrapper });
        await act(() => result.current.hochladen(datei('application/pdf')));
        expect(await screen.findByText('Ungültiger Dateityp. Erlaubt sind PNG, JPEG und WebP.')).toBeVisible();
        await act(() => result.current.hochladen(datei('image/png', 2 * 1024 * 1024 + 1)));
        expect(await screen.findByText('Datei zu groß. Erlaubt sind höchstens 2 MB.')).toBeVisible();
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it('meldet den neuen Dateinamen und lädt das Bild neu', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(antwort({ logoDateiname: 'logo.png' })));
        const geaendert = vi.fn();
        const { result, rerender } = renderHook(({ name }) => useFirmenLogo(name, geaendert), { wrapper, initialProps: { name: 'alt.png' } });
        expect(result.current.logoSrc).toBe('/api/firma/logo?v=0');
        await act(() => result.current.hochladen(datei('image/png')));
        rerender({ name: 'logo.png' });
        expect(geaendert).toHaveBeenCalledWith('logo.png');
        expect(result.current.logoSrc).toBe('/api/firma/logo?v=1');
    });

    it('zeigt die Meldung des Servers bei abgelehntem Upload', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(antwort({ message: 'Bild ist beschädigt.' }, false)));
        const { result } = renderHook(() => useFirmenLogo('', vi.fn()), { wrapper });
        await act(() => result.current.hochladen(datei('image/webp')));
        expect(await screen.findByText('Bild ist beschädigt.')).toBeVisible();
    });

    it('löscht erst nach Bestätigung', async () => {
        const user = userEvent.setup();
        const fetchMock = vi.fn().mockResolvedValue(antwort({}));
        vi.stubGlobal('fetch', fetchMock);
        const geaendert = vi.fn();
        const { result } = renderHook(() => useFirmenLogo('logo.png', geaendert), { wrapper });
        let fertig: Promise<void> = Promise.resolve();
        act(() => { fertig = result.current.loeschen(); });
        expect(fetchMock).not.toHaveBeenCalled();
        await user.click(await screen.findByRole('button', { name: 'Löschen' }));
        await act(() => fertig);
        expect(fetchMock).toHaveBeenCalledWith('/api/firma/logo', { method: 'DELETE' });
        expect(geaendert).toHaveBeenCalledWith('');
    });
});

describe('useSteuerberaterKontakte', () => {
    it('legt neu per POST an und bearbeitet per PUT, danach ist der Dialog zu', async () => {
        const fetchMock = vi.fn().mockResolvedValue(antwort([]));
        vi.stubGlobal('fetch', fetchMock);
        const { result } = renderHook(() => useSteuerberaterKontakte(), { wrapper });
        act(() => result.current.neuAnlegen());
        expect(result.current.dialogOffen).toBe(true);
        expect(result.current.entwurf).toMatchObject({ aktiv: true, autoProcessEmails: true });
        act(() => result.current.setEntwurf(prev => ({ ...prev, ...KANZLEI })));
        await act(() => result.current.speichern());
        expect(fetchMock).toHaveBeenCalledWith('/api/firma/steuerberater', expect.objectContaining({ method: 'POST' }));
        expect(result.current.dialogOffen).toBe(false);

        act(() => result.current.setEntwurf({ id: 3, ...KANZLEI }));
        await act(() => result.current.speichern());
        expect(fetchMock).toHaveBeenCalledWith('/api/firma/steuerberater/3', expect.objectContaining({ method: 'PUT' }));
    });

    it('zeigt Klartext-Fehler des Servers und lässt den Dialog offen', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(antwort('Name fehlt', false)));
        const { result } = renderHook(() => useSteuerberaterKontakte(), { wrapper });
        act(() => result.current.neuAnlegen());
        act(() => result.current.setEntwurf(prev => ({ ...prev, ...KANZLEI })));
        await act(() => result.current.speichern());
        expect(await screen.findByText('Fehler beim Speichern: Name fehlt')).toBeVisible();
        expect(result.current.dialogOffen).toBe(true);
        expect(result.current.speichert).toBe(false);
    });

    it('meldet während des Speicherns „speichert“ und danach den Erfolg', async () => {
        let antworten: (r: Response) => void = () => {};
        vi.stubGlobal('fetch', vi.fn().mockImplementation((_url: string, init?: RequestInit) => init?.method
            ? new Promise<Response>(resolve => { antworten = resolve; })
            : Promise.resolve(antwort([]))));
        const { result } = renderHook(() => useSteuerberaterKontakte(), { wrapper });
        act(() => result.current.neuAnlegen());
        act(() => result.current.setEntwurf(prev => ({ ...prev, ...KANZLEI })));
        let fertig: Promise<void> = Promise.resolve();
        act(() => { fertig = result.current.speichern(); });
        expect(result.current.speichert).toBe(true);
        await act(async () => { antworten(antwort({})); await fertig; });
        expect(result.current.speichert).toBe(false);
        expect(await screen.findByText('Steuerberater gespeichert.')).toBeVisible();
    });

    it('setzt „speichert“ auch bei Netzwerkfehler zurück', async () => {
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')));
        const { result } = renderHook(() => useSteuerberaterKontakte(), { wrapper });
        act(() => result.current.neuAnlegen());
        act(() => result.current.setEntwurf(prev => ({ ...prev, ...KANZLEI })));
        await act(() => result.current.speichern());
        expect(result.current.speichert).toBe(false);
        expect(await screen.findByText('Steuerberater konnte nicht gespeichert werden. Bitte Verbindung prüfen.')).toBeVisible();
    });

    it('speichert ohne Haupt-E-Mail nicht und sagt, was fehlt', async () => {
        const fetchMock = vi.fn();
        vi.stubGlobal('fetch', fetchMock);
        const { result } = renderHook(() => useSteuerberaterKontakte(), { wrapper });
        act(() => result.current.neuAnlegen());
        act(() => result.current.setEntwurf(prev => ({ ...prev, name: 'Kanzlei Mustermann' })));
        await act(() => result.current.speichern());
        expect(fetchMock).not.toHaveBeenCalled();
        expect(await screen.findByText('Bitte Haupt-E-Mail eintragen.')).toBeVisible();
        expect(result.current.dialogOffen).toBe(true);
        expect(result.current.speichert).toBe(false);
    });

    it('nennt beim Löschen den Namen und meldet den Erfolg', async () => {
        const user = userEvent.setup();
        const fetchMock = vi.fn().mockResolvedValue(antwort([]));
        vi.stubGlobal('fetch', fetchMock);
        const { result } = renderHook(() => useSteuerberaterKontakte(), { wrapper });
        let fertig: Promise<void> = Promise.resolve();
        act(() => { fertig = result.current.loeschen({ id: 3, name: 'Kanzlei Mustermann' } as SteuerberaterKontakt); });
        expect(await screen.findByText('„Kanzlei Mustermann“ wirklich löschen? Bereits abgelegte Lohnabrechnungen und BWA bleiben erhalten.')).toBeVisible();
        expect(fetchMock).not.toHaveBeenCalled();
        await user.click(screen.getByRole('button', { name: 'Steuerberater löschen' }));
        await act(() => fertig);
        expect(fetchMock).toHaveBeenCalledWith('/api/firma/steuerberater/3', { method: 'DELETE' });
        expect(await screen.findByText('Steuerberater gelöscht.')).toBeVisible();
    });
});

describe('useSteuerberaterUnterlagen', () => {
    it('lädt Listen erst, wenn der Reiter sichtbar ist, und beachtet den Filter', async () => {
        const jahr = new Date().getFullYear();
        const fetchMock = vi.fn().mockResolvedValue(antwort([]));
        vi.stubGlobal('fetch', fetchMock);
        const { result, rerender } = renderHook(
            ({ sichtbar, unterreiter }: { sichtbar: boolean; unterreiter: SteuerberaterUnterreiter }) => useSteuerberaterUnterlagen(sichtbar, unterreiter),
            { wrapper, initialProps: { sichtbar: false, unterreiter: 'lohnabrechnungen' } },
        );
        expect(fetchMock).not.toHaveBeenCalled();

        rerender({ sichtbar: true, unterreiter: 'lohnabrechnungen' });
        await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(`/api/lohnabrechnungen/jahr/${jahr}`, expect.anything()));
        act(() => result.current.setSteuerberaterFilter('3'));
        await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(`/api/lohnabrechnungen/steuerberater/3/jahr/${jahr}`, expect.anything()));

        rerender({ sichtbar: true, unterreiter: 'bwa' });
        await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(`/api/bwa/jahr/${jahr}`, expect.anything()));
    });

    it('führt die Jahre zusammen und meldet Ladefehler', async () => {
        const jahr = new Date().getFullYear();
        vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(antwort([jahr - 2])).mockResolvedValueOnce(antwort([jahr - 1])));
        const { result } = renderHook(() => useSteuerberaterUnterlagen(false, 'kontakte'), { wrapper });
        await act(() => result.current.ladeJahre());
        expect(result.current.verfuegbareJahre).toEqual([jahr, jahr - 1, jahr - 2]);

        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(antwort([], false)));
        await act(() => result.current.ladeJahre());
        expect(await screen.findByText('Abrechnungsjahre konnten nicht geladen werden.')).toBeVisible();
    });

    it('verwirft die späte Antwort eines alten Jahres', async () => {
        const jahr = new Date().getFullYear();
        const offen = new Map<string, (r: Response) => void>();
        vi.stubGlobal('fetch', vi.fn().mockImplementation((url: string, init?: RequestInit) => new Promise<Response>((resolve, reject) => {
            offen.set(url, resolve);
            init?.signal?.addEventListener('abort', () => reject(new DOMException('abgebrochen', 'AbortError')));
        })));
        const { result } = renderHook(() => useSteuerberaterUnterlagen(true, 'bwa'), { wrapper });
        await waitFor(() => expect(offen.has(`/api/bwa/jahr/${jahr}`)).toBe(true));
        act(() => result.current.setJahr(jahr - 1));
        await waitFor(() => expect(offen.has(`/api/bwa/jahr/${jahr - 1}`)).toBe(true));

        await act(async () => { offen.get(`/api/bwa/jahr/${jahr - 1}`)?.(antwort([{ id: 2, jahr: jahr - 1 }])); });
        await act(async () => { offen.get(`/api/bwa/jahr/${jahr}`)?.(antwort([{ id: 1, jahr }])); });
        expect(result.current.bwaListe).toEqual([{ id: 2, jahr: jahr - 1 }]);
        expect(screen.queryByText('Auswertungen konnten nicht geladen werden.')).toBeNull();
    });

    it('verwirft auch bei Lohnabrechnungen die späte Antwort des alten Filters', async () => {
        const jahr = new Date().getFullYear();
        const offen = new Map<string, (r: Response) => void>();
        vi.stubGlobal('fetch', vi.fn().mockImplementation((url: string) => new Promise<Response>(resolve => { offen.set(url, resolve); })));
        const { result } = renderHook(() => useSteuerberaterUnterlagen(true, 'lohnabrechnungen'), { wrapper });
        await waitFor(() => expect(offen.has(`/api/lohnabrechnungen/jahr/${jahr}`)).toBe(true));
        act(() => result.current.setSteuerberaterFilter('3'));
        await waitFor(() => expect(offen.has(`/api/lohnabrechnungen/steuerberater/3/jahr/${jahr}`)).toBe(true));

        await act(async () => { offen.get(`/api/lohnabrechnungen/steuerberater/3/jahr/${jahr}`)?.(antwort([{ id: 7 }])); });
        await act(async () => { offen.get(`/api/lohnabrechnungen/jahr/${jahr}`)?.(antwort([{ id: 6 }, { id: 7 }])); });
        expect(result.current.lohnabrechnungen).toEqual([{ id: 7 }]);
    });
});
