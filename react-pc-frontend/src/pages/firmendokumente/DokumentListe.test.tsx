import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';
import { ToastProvider } from '../../components/ui/toast';
import { baueBaum } from '../../lib/ordnerBaum';
import { DokumentListe, type DokumentListeProps } from './DokumentListe';
import { useDokumentAktionen } from './useDokumentAktionen';
import type { FirmenDokument, FirmenOrdner } from './typen';

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

    it('zeigt rechts neben dem Namen den Dateityp – „–“ ohne erkennbare Endung', () => {
        zeige({
            dokumente: [dok(1, 'Prüfbericht'), dok(2, 'Foto Halle', null, { dateiart: 'BILD', originalDateiname: 'IMG_0042.JPG' }),
                dok(3, 'ZAUNFELD_GZ75', null, { dateiart: 'SONSTIGE', originalDateiname: 'ZAUNFELD_GZ75' }),
                dok(4, 'Altes Prüfprotokoll', null, { originalDateiname: 'pruefprotokoll' })],
            gesamt: 4,
        });
        const kopf = screen.getAllByRole('columnheader').map((th) => th.textContent);
        expect(kopf.slice(0, 3)).toEqual(['Name', 'Dateityp', 'Fällig am']);
        const typ = (name: string) => within(screen.getByRole('row', { name: new RegExp(name) })).getAllByRole('cell')[1];
        expect(typ('Prüfbericht')).toHaveTextContent(/^PDF$/);
        expect(typ('Foto Halle')).toHaveTextContent(/^JPG$/);
        expect(typ('ZAUNFELD_GZ75')).toHaveTextContent(/^–$/);
        expect(typ('Altes Prüfprotokoll')).toHaveTextContent(/^PDF$/);
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
        expect(screen.getAllByRole('columnheader').map((th) => th.textContent).slice(0, 4)).toEqual(['Name', 'Dateityp', 'Ordner', 'Fällig am']);
        const zellen = within(screen.getByRole('row', { name: /Torprüfung/ })).getAllByRole('cell');
        expect(zellen[1]).toHaveTextContent(/^PDF$/);
        expect(zellen[2]).toHaveTextContent('Prüfungen › Tore');
        await user.click(screen.getByRole('button', { name: 'Weitere Aktionen für „Torprüfung“' }));
        expect(await screen.findByRole('menuitem', { name: 'Im Ordner zeigen' })).toBeInTheDocument();
    });
});

describe('DokumentListe – Unterordner und Vorschau', () => {
    const ordner = (id: number, name: string, anzahlDokumente: number): FirmenOrdner => ({
        id, name, parentId: 1, anzahlDokumente, anzahlUeberfaellig: 0, anzahlBaldFaellig: 0, darfAendern: true, eingeschraenkt: false,
    });
    const MONTAG = ordner(2, 'Montag', 5);
    const DIENSTAG = ordner(3, 'Dienstag', 1);
    const baum = baueBaum([MONTAG, DIENSTAG]);
    const ordnerAktionen = (onOeffnen = vi.fn()) => ({
        onOeffnen, ablage: { baum, istAdmin: false, onDateien: vi.fn(), onDokument: vi.fn(), onOrdner: vi.fn(), onAbgelehnt: vi.fn() },
    });

    it('zeigt Unterordner als Zeilen vor den Dokumenten – Klick auf den Namen öffnet', async () => {
        const user = userEvent.setup();
        const onOeffnen = vi.fn();
        zeige({ dokumente: [dok(7, 'Torprüfung')], gesamt: 1, unterordner: [MONTAG, DIENSTAG], ordnerAktionen: ordnerAktionen(onOeffnen) });
        const zeilen = screen.getAllByRole('row').slice(1);
        expect(zeilen.map((z) => within(z).getAllByRole('button')[0].textContent)).toEqual(['Montag', 'Dienstag', 'Torprüfung']);
        const zellen = within(zeilen[0]).getAllByRole('cell');
        expect(zellen[1]).toHaveTextContent(/^Ordner$/);
        // Die Anzahl steht klein unter dem Namen, nicht in der Spalte „Größe“ (dort „–“ wie bei Ordnern im Explorer).
        expect(zellen[0].textContent).toBe('Montag5\u00a0Dokumente');
        expect(zellen.at(-2)?.textContent).toBe('–');
        expect(within(zeilen[1]).getAllByRole('cell')[0].textContent).toBe('Dienstag1\u00a0Dokument');
        expect(zeilen[0]).toHaveAttribute('draggable', 'true');
        await user.click(screen.getByRole('button', { name: 'Montag' }));
        expect(onOeffnen).toHaveBeenCalledWith(2);
        expect(screen.getByText('1 von 1 angezeigt')).toBeInTheDocument();
    });

    it('wirkt bei einem Ordner nur mit Unterordnern nicht leer', () => {
        zeige({ unterordner: [MONTAG], ordnerAktionen: ordnerAktionen() });
        expect(screen.getByRole('row', { name: /Montag/ })).toBeInTheDocument();
        expect(screen.queryByText('In diesem Ordner liegt noch kein Dokument.')).toBeNull();
        expect(screen.queryByText(/angezeigt/)).toBeNull();
    });

    it('zeigt die Unterordner schon, während die Dokumente noch laden', () => {
        zeige({ laedt: true, unterordner: [MONTAG], ordnerAktionen: ordnerAktionen() });
        expect(screen.getByRole('row', { name: /Montag/ })).toBeInTheDocument();
        expect(screen.getByRole('status')).toHaveTextContent('Dokumente werden geladen …');
    });

    it('zeigt in der Vorschau Kacheln statt der Tabelle, die Größe als CSS-Variable', () => {
        const { container } = zeige({ dokumente: [dok(7, 'Torprüfung')], gesamt: 1, darstellung: { art: 'vorschau', groesse: 240 }, unterordner: [MONTAG], ordnerAktionen: ordnerAktionen() });
        expect(screen.queryByRole('table')).toBeNull();
        const liste = screen.getByRole('list', { name: 'Dokumente in Tore' });
        expect(within(liste).getAllByRole('listitem')).toHaveLength(2);
        expect((container.querySelector('[data-kacheln]') as HTMLElement).style.getPropertyValue('--kachel-groesse')).toBe('240px');
        expect(screen.getByText('1 von 1 angezeigt')).toBeInTheDocument();
    });
});
