import { act, renderHook, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import type { FirmenDokument } from './typen';
import { useHochladen } from './useHochladen';

vi.mock('./api', async (original) => ({ ...(await original<typeof import('./api')>()), ladeHoch: vi.fn() }));
const { ladeHoch } = await import('./api');

const datei = (name: string, type = '') => new File(['inhalt'], name, { type });
const huelle = ({ children }: { children: ReactNode }) => <ToastProvider>{children}</ToastProvider>;

/** Ein Upload, den der Test selbst beendet. */
function offen() {
    const laufend: { resolve: (d: FirmenDokument) => void; reject: (e: Error) => void }[] = [];
    vi.mocked(ladeHoch).mockImplementation(() => new Promise((resolve, reject) => { laufend.push({ resolve, reject }); }));
    return laufend;
}

function starte() {
    const melde = vi.fn();
    const onStapelFertig = vi.fn();
    const hook = renderHook(() => useHochladen({ meldeFehler: melde, onStapelFertig, ordnerName: (id) => (id === 2 ? 'Fotos Baustelle' : 'Prüfungen') }), { wrapper: huelle });
    return { ...hook, melde, onStapelFertig };
}

afterEach(() => vi.clearAllMocks());

describe('useHochladen', () => {
    it('fragt bei einer einzelnen Datei per Dialog nach und lehnt Programmdateien mit Meldung ab', () => {
        const { result, melde } = starte();
        act(() => result.current.annehmen([datei('setup.EXE')], 2));
        expect(melde).toHaveBeenCalledWith('„setup.EXE“: Programmdateien (z. B. .exe oder .bat) können aus Sicherheitsgründen nicht abgelegt werden.');
        expect(result.current.aktuelle).toBeNull();

        act(() => result.current.annehmen([datei('a.pdf', 'application/pdf'), datei('virus.bat')], 2));
        expect(melde).toHaveBeenCalledTimes(2);
        expect(result.current.aktuelle).toMatchObject({ ordnerId: 2, nummer: 1, anzahl: 1 });
        expect(result.current.aktuelle?.datei.name).toBe('a.pdf');
        expect(ladeHoch).not.toHaveBeenCalled();

        // Eine weitere einzelne Datei, während der Dialog offen ist, reiht sich an.
        act(() => result.current.annehmen([datei('angebot.docx')], 9));
        expect(result.current.aktuelle).toMatchObject({ ordnerId: 2, nummer: 1, anzahl: 2 });
        act(() => result.current.weiter());
        expect(result.current.aktuelle).toMatchObject({ ordnerId: 9, nummer: 2, anzahl: 2 });
        act(() => result.current.weiter());
        expect(result.current.aktuelle).toBeNull();
        act(() => result.current.abbrechen());
        expect(result.current.aktuelle).toBeNull();
    });

    it('lädt ab zwei Dateien ohne Dialog hoch – Name aus dem Dateinamen, kein Fällig am – und lädt am Ende einmal neu', async () => {
        const laufend = offen();
        const { result, onStapelFertig } = starte();
        act(() => result.current.annehmen([datei('IMG_0001.JPG'), datei('IMG_0002.JPG'), datei('Prüfbericht Tor.pdf')], 2));
        expect(result.current.aktuelle).toBeNull();
        expect(result.current.stapel).toEqual({ gesamt: 3, fertig: 0 });
        expect(vi.mocked(ladeHoch).mock.calls.map(([d, angaben]) => [d.name, angaben])).toEqual([
            ['IMG_0001.JPG', { name: 'IMG_0001', ordnerId: 2, faelligAm: null }],
            ['IMG_0002.JPG', { name: 'IMG_0002', ordnerId: 2, faelligAm: null }],
            ['Prüfbericht Tor.pdf', { name: 'Prüfbericht Tor', ordnerId: 2, faelligAm: null }],
        ]);
        await act(async () => { laufend[0].resolve({} as FirmenDokument); });
        expect(result.current.stapel).toEqual({ gesamt: 3, fertig: 1 });
        expect(onStapelFertig).not.toHaveBeenCalled();
        await act(async () => { laufend[1].resolve({} as FirmenDokument); laufend[2].resolve({} as FirmenDokument); });
        expect(result.current.stapel).toBeNull();
        expect(onStapelFertig).toHaveBeenCalledTimes(1);
        expect(await screen.findByText('3 Dateien in „Fotos Baustelle“ abgelegt.')).toBeInTheDocument();
    });

    it('schickt höchstens drei Dateien gleichzeitig und nimmt weitere in den laufenden Stapel', async () => {
        const laufend = offen();
        const { result } = starte();
        act(() => result.current.annehmen(['1', '2', '3', '4', '5'].map((n) => datei(`foto_${n}.jpg`)), 2));
        expect(ladeHoch).toHaveBeenCalledTimes(3);
        await act(async () => { laufend[0].resolve({} as FirmenDokument); });
        expect(ladeHoch).toHaveBeenCalledTimes(4);
        // Auch eine einzelne Datei geht während des Laufs ohne Dialog mit.
        act(() => result.current.annehmen([datei('nachzuegler.jpg')], 2));
        expect(result.current.aktuelle).toBeNull();
        expect(result.current.stapel).toEqual({ gesamt: 6, fertig: 1 });
    });

    it('meldet Teilfehler mit Dateinamen und Grund', async () => {
        const laufend = offen();
        const { result, onStapelFertig } = starte();
        act(() => result.current.annehmen([datei('a.jpg'), datei('b.jpg'), datei('c.jpg')], 4));
        await act(async () => {
            laufend[0].resolve({} as FirmenDokument);
            laufend[1].reject(new Error('Die Datei ist kaputt.'));
            laufend[2].reject(new Error('Die Datei ist kaputt.'));
        });
        expect(onStapelFertig).toHaveBeenCalledTimes(1);
        expect(await screen.findByText('1 von 3 abgelegt. Nicht geklappt: b.jpg, c.jpg (Die Datei ist kaputt.)')).toBeInTheDocument();
    });

    it('bricht ab: die Warteschlange leert sich, laufende Dateien gehen noch zu Ende', async () => {
        const laufend = offen();
        const { result, onStapelFertig } = starte();
        act(() => result.current.annehmen(['1', '2', '3', '4', '5', '6'].map((n) => datei(`foto_${n}.jpg`)), 2));
        await act(async () => { laufend[0].resolve({} as FirmenDokument); });
        expect(ladeHoch).toHaveBeenCalledTimes(4);
        act(() => result.current.stapelAbbrechen());
        expect(result.current.stapel).toEqual({ gesamt: 6, fertig: 1, abgebrochen: true });
        await act(async () => { laufend[1].resolve({} as FirmenDokument); laufend[2].resolve({} as FirmenDokument); laufend[3].resolve({} as FirmenDokument); });
        expect(ladeHoch).toHaveBeenCalledTimes(4);
        expect(result.current.stapel).toBeNull();
        expect(onStapelFertig).toHaveBeenCalledTimes(1);
        expect(await screen.findByText('4 von 6 abgelegt, Rest abgebrochen.')).toBeInTheDocument();
    });

    it('nimmt nach „Abbrechen“ neue Dateien wieder normal an', async () => {
        const laufend = offen();
        const { result } = starte();
        act(() => result.current.annehmen(['1', '2', '3', '4'].map((n) => datei(`foto_${n}.jpg`)), 2));
        act(() => result.current.stapelAbbrechen());
        expect(result.current.stapel?.abgebrochen).toBe(true);
        act(() => result.current.annehmen([datei('neu_1.jpg'), datei('neu_2.jpg')], 2));
        expect(result.current.stapel?.abgebrochen).toBeFalsy();
        expect(result.current.stapel?.gesamt).toBe(6);
        await act(async () => { laufend.forEach((l) => l.resolve({} as FirmenDokument)); });
        // Die neuen Dateien gehen wirklich hoch, nicht nur die drei, die beim Abbrechen liefen.
        expect(vi.mocked(ladeHoch).mock.calls.map(([d]) => d.name)).toEqual(expect.arrayContaining(['neu_1.jpg', 'neu_2.jpg']));
        await act(async () => { laufend.forEach((l) => l.resolve({} as FirmenDokument)); });
        expect(await screen.findByText('5 von 6 abgelegt, Rest abgebrochen.')).toBeInTheDocument();
    });
});
