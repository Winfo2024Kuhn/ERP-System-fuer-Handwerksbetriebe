import { act, fireEvent, render, renderHook, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';
import { ToastProvider } from '../../components/ui/toast';
import { DokumentListe, type DokumentListeProps } from './DokumentListe';
import { useDokumentAktionen } from './useDokumentAktionen';
import { useHochladen } from './useHochladen';
import type { FirmenDokument } from './typen';

vi.mock('./api', async (original) => ({ ...(await original<typeof import('./api')>()), loescheDokument: vi.fn() }));
vi.mock('../../lib/pdfDrucken', () => ({ druckePdfAusUrl: vi.fn() }));
vi.mock('../../lib/herunterladen', () => ({ starteDownload: vi.fn() }));

const { loescheDokument } = await import('./api');
const { druckePdfAusUrl } = await import('../../lib/pdfDrucken');
const { starteDownload } = await import('../../lib/herunterladen');

const dok = (id: number, name: string, faelligAm: string | null = null, extra: Partial<FirmenDokument> = {}): FirmenDokument => ({
    id, ordnerId: 2, name, originalDateiname: 'scan.pdf', groesseBytes: 482_113, hochgeladenAm: '2026-11-02T09:14:00',
    hochgeladenVon: 'Max Mustermann', faelligAm, url: `/api/firmendokumente/${id}/datei`, dateiart: 'PDF', darfAendern: true, ...extra,
});

const LEER: Omit<DokumentListeProps, 'aktionen'> = {
    beschriftung: 'Dokumente in Tore', dokumente: [], gesamt: 0, laedt: false, laedtMehr: false, fehler: null,
    leer: 'In diesem Ordner liegt noch kein Dokument.', onMehrLaden: vi.fn(), onErneut: vi.fn(),
};

function Mit({ props, onGeloescht = vi.fn() }: { props: Partial<DokumentListeProps>; onGeloescht?: (d: FirmenDokument) => void }) {
    const { aktionen, vorschau } = useDokumentAktionen({ onGeloescht, imOrdnerZeigen: vi.fn() });
    return (
        <>
            <DokumentListe {...LEER} {...props} aktionen={aktionen} />
            {vorschau && <p>Vorschau: {vorschau.name}</p>}
        </>
    );
}

function zeige(props: Partial<DokumentListeProps>, onGeloescht?: (d: FirmenDokument) => void) {
    return render(<ToastProvider><ConfirmProvider><Mit props={props} onGeloescht={onGeloescht} /></ConfirmProvider></ToastProvider>);
}

afterEach(() => vi.clearAllMocks());

describe('DokumentListe', () => {
    it('unterscheidet Laden, Fehler und leer', async () => {
        const user = userEvent.setup();
        const onErneut = vi.fn();
        const { rerender } = zeige({ laedt: true });
        expect(screen.getByRole('status')).toHaveTextContent('Dokumente werden geladen …');
        rerender(<ToastProvider><ConfirmProvider><Mit props={{ fehler: 'Den Ordner gibt es nicht mehr.', onErneut }} /></ConfirmProvider></ToastProvider>);
        expect(screen.getByText('Den Ordner gibt es nicht mehr.')).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Erneut versuchen' }));
        expect(onErneut).toHaveBeenCalled();
        rerender(<ToastProvider><ConfirmProvider><Mit props={{}} /></ConfirmProvider></ToastProvider>);
        expect(screen.getByText('In diesem Ordner liegt noch kein Dokument.')).toBeInTheDocument();
    });

    it('zeigt Name, Fällig am farbig, Datum und Größe – Namen mit HTML als reinen Text', () => {
        const { container } = zeige({ dokumente: [dok(1, '<img src=x onerror=alert(1)>', '2000-01-01'), dok(2, 'Torprüfung')], gesamt: 2 });
        const tabelle = screen.getByRole('table', { name: 'Dokumente in Tore' });
        expect(within(tabelle).getByText('<img src=x onerror=alert(1)>')).toBeInTheDocument();
        expect(container.querySelector('img')).toBeNull();
        const ueberfaellig = within(tabelle).getByText('01.01.2000').closest('span') as HTMLElement;
        expect(ueberfaellig.className).toContain('text-red-700');
        expect(ueberfaellig).toHaveTextContent('überfällig');
        expect(within(tabelle).getAllByText('02.11.2026')).toHaveLength(2);
        expect(within(tabelle).getAllByText('471 KB')).toHaveLength(2);
        expect(screen.getByText('2 von 2 angezeigt')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Mehr laden' })).toBeNull();
    });

    it('bietet „Mehr laden“, solange der Server mehr hat, und sperrt es beim Laden', async () => {
        const user = userEvent.setup();
        const onMehrLaden = vi.fn();
        const { rerender } = zeige({ dokumente: [dok(1, 'A')], gesamt: 60, onMehrLaden });
        expect(screen.getByText('1 von 60 angezeigt')).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Mehr laden' }));
        expect(onMehrLaden).toHaveBeenCalled();
        rerender(<ToastProvider><ConfirmProvider><Mit props={{ dokumente: [dok(1, 'A')], gesamt: 60, laedtMehr: true }} /></ConfirmProvider></ToastProvider>);
        expect(screen.getByRole('button', { name: 'Mehr laden' })).toBeDisabled();
    });

    it('öffnet per Klick die Vorschau und druckt bzw. lädt herunter', async () => {
        const user = userEvent.setup();
        vi.mocked(druckePdfAusUrl).mockRejectedValue(new Error('Das Dokument konnte nicht geladen werden.'));
        zeige({ dokumente: [dok(7, 'Torprüfung')], gesamt: 1 });
        await user.click(screen.getByRole('button', { name: 'Torprüfung' }));
        expect(screen.getByText('Vorschau: Torprüfung')).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: '„Torprüfung“ herunterladen' }));
        expect(starteDownload).toHaveBeenCalledWith('/api/firmendokumente/7/datei?download=true', 'Torprüfung.pdf');
        await user.click(screen.getByRole('button', { name: '„Torprüfung“ drucken' }));
        expect(druckePdfAusUrl).toHaveBeenCalledWith('/api/firmendokumente/7/datei');
        expect(await screen.findByText('Das Dokument konnte nicht geladen werden.')).toBeInTheDocument();
    });

    it('zeigt je Dateiart ein eigenes Symbol', () => {
        zeige({
            dokumente: [dok(1, 'Prüfbericht'), dok(2, 'Foto Halle', null, { dateiart: 'BILD', originalDateiname: 'foto.jpg' }),
                dok(3, 'Angebot', null, { dateiart: 'SONSTIGE', originalDateiname: 'angebot.docx' })],
            gesamt: 3,
        });
        const symbol = (name: string) => screen.getByRole('button', { name }).querySelector('svg')?.getAttribute('class') ?? '';
        expect(symbol('Prüfbericht')).toContain('lucide-file-text');
        expect(symbol('Foto Halle')).toContain('lucide-file-image');
        expect(symbol('Angebot')).toMatch(/lucide-file(\s|$)/);
    });

    it('öffnet Bilder in der Vorschau, lädt andere Dateien beim Klick herunter und druckt nur PDFs', async () => {
        const user = userEvent.setup();
        zeige({
            dokumente: [dok(2, 'Foto Halle', null, { dateiart: 'BILD', originalDateiname: 'IMG_0042.JPG' }),
                dok(3, 'Haftpflicht 2026', null, { dateiart: 'SONSTIGE', originalDateiname: 'police.docx' })],
            gesamt: 2,
        });
        expect(screen.queryByRole('button', { name: /drucken/ })).toBeNull();
        await user.click(screen.getByRole('button', { name: 'Foto Halle' }));
        expect(screen.getByText('Vorschau: Foto Halle')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Haftpflicht 2026' })).toHaveAttribute('title', '„Haftpflicht 2026“ herunterladen');
        await user.click(screen.getByRole('button', { name: 'Haftpflicht 2026' }));
        expect(starteDownload).toHaveBeenCalledWith('/api/firmendokumente/3/datei?download=true', 'Haftpflicht 2026.docx');
        expect(screen.queryByText('Vorschau: Haftpflicht 2026')).toBeNull();
        await user.click(screen.getByRole('button', { name: '„Foto Halle“ herunterladen' }));
        expect(starteDownload).toHaveBeenLastCalledWith('/api/firmendokumente/2/datei?download=true', 'Foto Halle.JPG');
    });

    it('blendet Bearbeiten und Löschen ohne Recht „Ändern“ aus – Ansehen, Drucken, Herunterladen bleiben', async () => {
        const user = userEvent.setup();
        zeige({ dokumente: [dok(7, 'Torprüfung', null, { darfAendern: false })], gesamt: 1 });
        expect(screen.getByRole('button', { name: '„Torprüfung“ drucken' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: '„Torprüfung“ herunterladen' })).toBeInTheDocument();
        expect(screen.getByRole('row', { name: /Torprüfung/ })).not.toHaveAttribute('draggable', 'true');
        await user.click(screen.getByRole('button', { name: 'Weitere Aktionen für „Torprüfung“' }));
        expect(await screen.findByRole('menuitem', { name: 'Ansehen' })).toBeInTheDocument();
        expect(screen.queryByRole('menuitem', { name: 'Bearbeiten …' })).toBeNull();
        expect(screen.queryByRole('menuitem', { name: 'Löschen …' })).toBeNull();
    });

    it('lässt Dokumente mit Recht „Ändern“ ziehen und gibt sie mit eigenem Datentyp mit', () => {
        zeige({ dokumente: [dok(7, 'Torprüfung', '2027-01-31')], gesamt: 1 });
        const zeile = screen.getByRole('row', { name: /Torprüfung/ });
        expect(zeile).toHaveAttribute('draggable', 'true');
        const daten = new Map<string, string>();
        const dataTransfer = { setData: (typ: string, wert: string) => daten.set(typ, wert), effectAllowed: '' };
        fireEvent.dragStart(zeile, { dataTransfer });
        expect(JSON.parse(daten.get('application/x-firmendokument') ?? '')).toEqual({ id: 7, ordnerId: 2, name: 'Torprüfung', faelligAm: '2027-01-31' });
        expect(dataTransfer.effectAllowed).toBe('move');
    });

    it('fragt vor dem Löschen nach und löscht erst nach Bestätigung', async () => {
        const user = userEvent.setup();
        vi.mocked(loescheDokument).mockResolvedValue(undefined);
        const onGeloescht = vi.fn();
        zeige({ dokumente: [dok(7, 'Torprüfung')], gesamt: 1 }, onGeloescht);

        const menue = async () => {
            await user.click(screen.getByRole('button', { name: 'Weitere Aktionen für „Torprüfung“' }));
            await user.click(await screen.findByRole('menuitem', { name: 'Löschen …' }));
        };
        await menue();
        const dialog = await screen.findByRole('dialog');
        expect(dialog).toHaveTextContent('„Torprüfung“ wird endgültig gelöscht. Das lässt sich nicht rückgängig machen.');
        await user.click(within(dialog).getByRole('button', { name: 'Abbrechen' }));
        expect(loescheDokument).not.toHaveBeenCalled();

        await menue();
        await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Dokument löschen' }));
        expect(loescheDokument).toHaveBeenCalledWith(7);
        expect(await screen.findByText('„Torprüfung“ gelöscht.')).toBeInTheDocument();
        expect(onGeloescht).toHaveBeenCalledWith(expect.objectContaining({ id: 7 }));
    });

    it('zeigt die Meldung, wenn das Löschen scheitert', async () => {
        const user = userEvent.setup();
        vi.mocked(loescheDokument).mockRejectedValue(new Error('Das Dokument gibt es nicht mehr.'));
        zeige({ dokumente: [dok(7, 'Torprüfung')], gesamt: 1 });
        await user.click(screen.getByRole('button', { name: 'Weitere Aktionen für „Torprüfung“' }));
        await user.click(await screen.findByRole('menuitem', { name: 'Löschen …' }));
        await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Dokument löschen' }));
        expect(await screen.findByText('Das Dokument gibt es nicht mehr.')).toBeInTheDocument();
    });

    it('zeigt in Listen über mehrere Ordner den Pfad und „Im Ordner zeigen“', async () => {
        const user = userEvent.setup();
        zeige({ dokumente: [dok(7, 'Torprüfung')], gesamt: 1, ordnerPfad: () => 'Prüfungen › Tore' });
        expect(screen.getByRole('columnheader', { name: 'Ordner' })).toBeInTheDocument();
        expect(screen.getByText('Prüfungen › Tore')).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Weitere Aktionen für „Torprüfung“' }));
        expect(await screen.findByRole('menuitem', { name: 'Im Ordner zeigen' })).toBeInTheDocument();
    });
});

describe('useHochladen', () => {
    const datei = (name: string, type = '') => new File(['inhalt'], name, { type });

    it('nimmt jede erlaubte Datei, lehnt Programmdateien mit Meldung ab und reiht die übrigen nacheinander', () => {
        const melde = vi.fn();
        const { result } = renderHook(() => useHochladen(melde));
        act(() => result.current.annehmen([datei('setup.EXE')], 2));
        expect(melde).toHaveBeenCalledWith('„setup.EXE“: Programmdateien (z. B. .exe oder .bat) können aus Sicherheitsgründen nicht abgelegt werden.');
        expect(result.current.aktuelle).toBeNull();

        act(() => result.current.annehmen([datei('a.pdf', 'application/pdf'), datei('virus.bat'), datei('foto.jpg', 'image/jpeg')], 2));
        expect(melde).toHaveBeenCalledTimes(2);
        expect(result.current.aktuelle).toMatchObject({ ordnerId: 2, nummer: 1, anzahl: 2 });
        expect(result.current.aktuelle?.datei.name).toBe('a.pdf');
        act(() => result.current.annehmen([datei('angebot.docx')], 9));
        expect(result.current.aktuelle).toMatchObject({ ordnerId: 2, nummer: 1, anzahl: 3 });

        act(() => result.current.weiter(4));
        expect(result.current.aktuelle).toMatchObject({ ordnerId: 4, nummer: 2, anzahl: 3 });
        expect(result.current.aktuelle?.datei.name).toBe('foto.jpg');
        act(() => result.current.abbrechen());
        expect(result.current.aktuelle).toBeNull();
        act(() => result.current.weiter(4));
        expect(result.current.aktuelle).toBeNull();
    });

    it('ist nach der letzten Datei fertig', () => {
        const { result } = renderHook(() => useHochladen(vi.fn()));
        act(() => result.current.annehmen([datei('a.pdf')], 2));
        act(() => result.current.weiter(2));
        expect(result.current.aktuelle).toBeNull();
    });
});
