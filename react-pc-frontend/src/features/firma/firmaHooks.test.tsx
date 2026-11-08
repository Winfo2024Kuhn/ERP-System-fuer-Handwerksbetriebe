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
import type { SteuerberaterUnterreiter } from './firmaTypen';

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
        expect(await screen.findByText('Ungültiger Dateityp – erlaubt sind PNG, JPEG und WebP.')).toBeVisible();
        await act(() => result.current.hochladen(datei('image/png', 2 * 1024 * 1024 + 1)));
        expect(await screen.findByText('Datei zu groß – maximal 2 MB.')).toBeVisible();
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
        await act(() => result.current.speichern());
        expect(fetchMock).toHaveBeenCalledWith('/api/firma/steuerberater', expect.objectContaining({ method: 'POST' }));
        expect(result.current.dialogOffen).toBe(false);

        act(() => result.current.setEntwurf({ id: 3, name: 'Kanzlei Mustermann' }));
        await act(() => result.current.speichern());
        expect(fetchMock).toHaveBeenCalledWith('/api/firma/steuerberater/3', expect.objectContaining({ method: 'PUT' }));
    });

    it('zeigt Klartext-Fehler des Servers und lässt den Dialog offen', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(antwort('Name fehlt', false)));
        const { result } = renderHook(() => useSteuerberaterKontakte(), { wrapper });
        act(() => result.current.neuAnlegen());
        await act(() => result.current.speichern());
        expect(await screen.findByText('Fehler beim Speichern: Name fehlt')).toBeVisible();
        expect(result.current.dialogOffen).toBe(true);
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
        await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(`/api/lohnabrechnungen/jahr/${jahr}`));
        act(() => result.current.setSteuerberaterFilter('3'));
        await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(`/api/lohnabrechnungen/steuerberater/3/jahr/${jahr}`));

        rerender({ sichtbar: true, unterreiter: 'bwa' });
        await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(`/api/bwa/jahr/${jahr}`));
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
});
