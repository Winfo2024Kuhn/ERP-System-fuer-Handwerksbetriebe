import { act, fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { baueBaum } from '../../lib/ordnerBaum';
import { DokumentKacheln, type DokumentKachelnProps } from './DokumentKacheln';
import type { DokumentAktionen } from './DokumentZeilenAktionen';
import { schreibeGezogenesDokument } from './firmendokumenteLogik';
import type { FirmenDokument, FirmenOrdner } from './typen';
import type { AblageRegeln } from './useAblageZiel';

const dok = (id: number, name: string, extra: Partial<FirmenDokument> = {}): FirmenDokument => ({
    id, ordnerId: 1, name, originalDateiname: 'scan.pdf', groesseBytes: 1000, hochgeladenAm: '2026-11-02T09:14:00',
    hochgeladenVon: 'Max Mustermann', faelligAm: null, url: `/api/firmendokumente/${id}/datei`, dateiart: 'PDF', darfAendern: true, ...extra,
});
const o = (id: number, name: string, parentId: number | null, extra: Partial<FirmenOrdner> = {}): FirmenOrdner => ({
    id, name, parentId, anzahlDokumente: 0, anzahlUeberfaellig: 0, anzahlBaldFaellig: 0, darfAendern: true, eingeschraenkt: false, ...extra,
});

const BAUM = baueBaum([o(1, 'Fotos Baustelle Mustermann', null), o(2, 'Montag', 1, { anzahlDokumente: 5 }), o(3, 'Archiv', 1, { darfAendern: false })]);

function zeige(props: Partial<DokumentKachelnProps> = {}) {
    const aktionen: DokumentAktionen = { ansehen: vi.fn(), drucken: vi.fn(), herunterladen: vi.fn(), bearbeiten: vi.fn(), loeschen: vi.fn() };
    const ablage: AblageRegeln = { baum: BAUM, istAdmin: false, onDateien: vi.fn(), onDokument: vi.fn(), onOrdner: vi.fn(), onAbgelehnt: vi.fn() };
    const onOeffnen = vi.fn();
    render(<DokumentKacheln beschriftung="Dokumente in Montag" dokumente={[]} aktionen={aktionen} unterordner={[]} ordnerAktionen={{ onOeffnen, ablage }} {...props} />);
    return { aktionen, ablage, onOeffnen };
}

describe('DokumentKacheln', () => {
    it('fragt das Vorschaubild nur bei PDFs und Bildern an – andere Dateien zeigen ihr Symbol', () => {
        zeige({
            dokumente: [dok(7, 'Torprüfung'), dok(8, 'Foto Halle', { dateiart: 'BILD', originalDateiname: 'IMG_0042.JPG' }),
                dok(9, 'Angebot', { dateiart: 'SONSTIGE', originalDateiname: 'angebot.docx' })],
        });
        const liste = screen.getByRole('list', { name: 'Dokumente in Montag' });
        const bilder = Array.from(liste.querySelectorAll('img')).map((img) => img.getAttribute('src'));
        expect(bilder).toEqual(['/api/firmendokumente/7/vorschaubild', '/api/firmendokumente/8/vorschaubild']);
        expect(liste.querySelector('img')).toHaveAttribute('loading', 'lazy');
        const angebot = screen.getByRole('button', { name: 'Angebot' });
        expect(angebot.querySelector('svg')?.getAttribute('class')).toMatch(/lucide-file(\s|$)/);
        expect(within(screen.getAllByRole('listitem')[2]).getByText('DOCX')).toBeInTheDocument();
    });

    it('versucht ein fehlendes Vorschaubild einmal nach 5 s erneut und zeigt dann das Symbol der Dateiart', () => {
        vi.useFakeTimers();
        try {
            zeige({ dokumente: [dok(7, 'Torprüfung')] });
            const knopf = screen.getByRole('button', { name: 'Torprüfung' });
            fireEvent.error(knopf.querySelector('img') as HTMLImageElement);
            act(() => { vi.advanceTimersByTime(5000); });
            const zweiter = knopf.querySelector('img') as HTMLImageElement;
            expect(zweiter).toHaveAttribute('src', '/api/firmendokumente/7/vorschaubild?v=1');
            fireEvent.error(zweiter);
            act(() => { vi.advanceTimersByTime(60_000); });
            expect(knopf.querySelector('img')).toBeNull();
            expect(knopf.querySelector('svg')?.getAttribute('class')).toContain('lucide-file-text');
        } finally {
            vi.useRealTimers();
        }
    });

    it('öffnet per Klick auf die Kachel und bietet im „…“-Menü auch Drucken und Herunterladen', async () => {
        const user = userEvent.setup();
        const { aktionen } = zeige({ dokumente: [dok(7, 'Torprüfung')] });
        await user.click(screen.getByRole('button', { name: 'Torprüfung' }));
        expect(aktionen.ansehen).toHaveBeenCalledWith(expect.objectContaining({ id: 7 }));
        await user.click(screen.getByRole('button', { name: 'Weitere Aktionen für „Torprüfung“' }));
        await user.click(await screen.findByRole('menuitem', { name: 'Drucken' }));
        expect(aktionen.drucken).toHaveBeenCalledWith(expect.objectContaining({ id: 7 }));
        await user.click(screen.getByRole('button', { name: 'Weitere Aktionen für „Torprüfung“' }));
        await user.click(await screen.findByRole('menuitem', { name: 'Herunterladen' }));
        expect(aktionen.herunterladen).toHaveBeenCalled();
        expect(screen.queryByRole('button', { name: '„Torprüfung“ drucken' })).toBeNull();
    });

    it('zeigt Namen mit HTML als reinen Text, den vollen Namen als Tooltip und in der Übersicht den Ordnerpfad', () => {
        const name = 'Zaunfeld <img src=x onerror=alert(1)> Füllung Rolltor Halle 2 Nordseite mit Schlupftür';
        zeige({ dokumente: [dok(7, name, { faelligAm: '2000-01-01' })], ordnerPfad: () => 'Prüfungen › Tore' });
        expect(screen.getByTitle(name)).toHaveTextContent(name);
        expect(document.querySelector('img[src="x"]')).toBeNull();
        expect(screen.getByText('Prüfungen › Tore')).toBeInTheDocument();
        expect(screen.getByText('01.01.2000')).toBeInTheDocument();
    });

    it('lässt Kacheln mit Recht „Ändern“ ziehen wie Tabellenzeilen', () => {
        zeige({ dokumente: [dok(7, 'Torprüfung', { faelligAm: '2027-01-31' }), dok(8, 'Police', { darfAendern: false })] });
        const [erste, zweite] = screen.getAllByRole('listitem');
        expect(erste).toHaveAttribute('draggable', 'true');
        expect(zweite).not.toHaveAttribute('draggable');
        const daten = new Map<string, string>();
        const dataTransfer = { setData: (typ: string, wert: string) => daten.set(typ, wert), effectAllowed: '' };
        fireEvent.dragStart(erste, { dataTransfer });
        expect(JSON.parse(daten.get('application/x-firmendokument') ?? '')).toEqual({ id: 7, ordnerId: 1, name: 'Torprüfung', faelligAm: '2027-01-31' });
    });

    it('zeigt Unterordner als Kacheln vor den Dateien – Klick öffnet, sie nehmen Dateien, Dokumente und Ordner an', async () => {
        const user = userEvent.setup();
        const { ablage, onOeffnen } = zeige({ dokumente: [dok(7, 'Torprüfung')], unterordner: [BAUM.byId.get(2) as FirmenOrdner, BAUM.byId.get(3) as FirmenOrdner] });
        const kacheln = screen.getAllByRole('listitem');
        expect(kacheln[0]).toHaveTextContent('Montag');
        expect(kacheln[0]).toHaveTextContent('5 Dokumente');
        expect(kacheln[2]).toHaveTextContent('Torprüfung');
        await user.click(screen.getByRole('button', { name: 'Montag, Ordner, 5 Dokumente' }));
        expect(onOeffnen).toHaveBeenCalledWith(2);

        const dateien = [new File(['x'], 'foto.jpg', { type: 'image/jpeg' })];
        expect(fireEvent.dragOver(kacheln[0], { dataTransfer: { types: ['Files'], files: dateien, dropEffect: 'none' } })).toBe(false);
        fireEvent.drop(kacheln[0], { dataTransfer: { types: ['Files'], files: dateien } });
        expect(ablage.onDateien).toHaveBeenCalledWith(2, dateien);

        const daten = new Map<string, string>();
        schreibeGezogenesDokument({ setData: (typ, wert) => { daten.set(typ, wert); } }, { id: 7, ordnerId: 1, name: 'Torprüfung', faelligAm: null });
        fireEvent.drop(kacheln[0], { dataTransfer: { types: [...daten.keys()], files: [], getData: (typ: string) => daten.get(typ) ?? '' } });
        expect(ablage.onDokument).toHaveBeenCalledWith(expect.objectContaining({ id: 7 }), expect.objectContaining({ id: 2 }));

        // Ohne „Ändern“: nimmt nichts an und lässt sich nicht ziehen.
        expect(kacheln[1]).not.toHaveAttribute('draggable');
        expect(fireEvent.dragOver(kacheln[1], { dataTransfer: { types: ['Files'], files: dateien, dropEffect: 'none' } })).toBe(true);
        expect(kacheln[0]).toHaveAttribute('draggable', 'true');
    });
});
