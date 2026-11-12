import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { baueBaum } from '../../lib/ordnerBaum';
import type { DokumentAktionen } from './DokumentZeilenAktionen';
import { RechterBereich, type Ansicht, type ListenStand, type RechterBereichProps } from './RechterBereich';
import type { FirmenDokument, FirmenOrdner } from './typen';
import type { AblageRegeln } from './useAblageZiel';

// Zählt, wie oft eine Kachel ihr Symbol zeichnet – so sieht man, ob Kacheln beim Regler neu rendern.
const symbolGezeichnet = vi.hoisted(() => vi.fn());
vi.mock('./DateiSymbol', () => ({ DateiSymbol: () => { symbolGezeichnet(); return null; } }));

const o = (id: number, name: string, darfAendern: boolean, parentId: number | null = null, anzahlDokumente = 0): FirmenOrdner => ({
    id, name, parentId, anzahlDokumente, anzahlUeberfaellig: 0, anzahlBaldFaellig: 0, darfAendern, eingeschraenkt: !darfAendern,
});
const BAUM = baueBaum([o(1, 'Prüfungen', true), o(6, 'Geschäftsleitung', false), o(2, 'Montag', true, 1, 5), o(3, 'Dienstag', true, 1, 2)]);
const LISTE: ListenStand = { eintraege: [], gesamt: 0, laedt: false, laedtMehr: false, fehler: null, mehrLaden: vi.fn(), neuLaden: vi.fn() };
const AKTIONEN: DokumentAktionen = { ansehen: vi.fn(), drucken: vi.fn(), herunterladen: vi.fn(), bearbeiten: vi.fn(), loeschen: vi.fn() };
const DOK: FirmenDokument = {
    id: 7, ordnerId: 1, name: 'Torprüfung', originalDateiname: 'scan.pdf', groesseBytes: 1000, hochgeladenAm: '2026-11-02T09:14:00',
    hochgeladenVon: 'Max Mustermann', faelligAm: null, url: '/api/firmendokumente/7/datei', dateiart: 'PDF', darfAendern: true,
};

function zeige(ansicht: Ansicht, extra: Partial<RechterBereichProps> = {}) {
    const onHochladen = vi.fn();
    const onOrdnerWaehlen = vi.fn();
    const baum = extra.baum ?? BAUM;
    const ablage: AblageRegeln = { baum, istAdmin: false, onDateien: vi.fn(), onDokument: vi.fn(), onOrdner: vi.fn(), onAbgelehnt: vi.fn() };
    const props: RechterBereichProps = { ansicht, baum, liste: LISTE, aktionen: AKTIONEN, onHochladen, onOrdnerWaehlen, ablage, stapel: null, onStapelAbbrechen: vi.fn(), ...extra };
    const ergebnis = render(<RechterBereich {...props} />);
    return { ...ergebnis, props, onHochladen, onOrdnerWaehlen, ablage };
}

describe('RechterBereich', () => {
    it('bietet Hochladen per Knopf und Ziehen für jede Datei an', async () => {
        const user = userEvent.setup();
        const { onHochladen, ablage } = zeige({ art: 'ordner', ordnerId: 1 }, { baum: baueBaum([o(1, 'Prüfungen', true)]) });
        expect(screen.getByText('Dateien hierher ziehen oder über den Knopf auswählen.')).toBeInTheDocument();
        expect(screen.getByText('In diesem Ordner liegt noch kein Dokument. Über „Datei hochladen“ oder per Ziehen ablegen.')).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Datei hochladen' }));
        expect(onHochladen).toHaveBeenCalled();
        const dateien = [new File(['x'], 'foto.jpg', { type: 'image/jpeg' }), new File(['x'], 'liste.xlsx')];
        fireEvent.drop(screen.getByRole('region', { name: 'Prüfungen' }), { dataTransfer: { types: ['Files'], files: dateien } });
        expect(ablage.onDateien).toHaveBeenCalledWith(1, dateien);
    });

    it('zeigt ohne Recht „Ändern“ nur einen ruhigen Hinweis – kein Hochladen, kein Ablegen', () => {
        const { ablage } = zeige({ art: 'ordner', ordnerId: 6 });
        expect(screen.getByText('Dieser Ordner lässt sich nur ansehen.')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Datei hochladen' })).toBeNull();
        expect(screen.queryByRole('button', { name: 'Mehr zu: Mehrere Dateien hochladen' })).toBeNull();
        expect(screen.getByText('In diesem Ordner liegt noch kein Dokument.')).toBeInTheDocument();
        const flaeche = screen.getByRole('region', { name: 'Geschäftsleitung' });
        const drueber = fireEvent.dragOver(flaeche, { dataTransfer: { types: ['Files'], files: [], dropEffect: 'none' } });
        expect(drueber).toBe(true);
        fireEvent.drop(flaeche, { dataTransfer: { types: ['Files'], files: [new File(['x'], 'foto.jpg')] } });
        expect(ablage.onDateien).not.toHaveBeenCalled();
    });

    it('zeigt die Unterordner des gewählten Ordners vor den Dateien – Ablegen darauf geht in den Unterordner', () => {
        const { ablage } = zeige({ art: 'ordner', ordnerId: 1 });
        const zeilen = screen.getAllByRole('row').slice(1);
        // Reihenfolge wie im Baum (nach Name).
        expect(zeilen.map((z) => within(z).getAllByRole('button')[0].textContent)).toEqual(['Dienstag', 'Montag']);
        expect(screen.queryByText(/noch kein Dokument/)).toBeNull();
        const dateien = [new File(['x'], 'foto.jpg', { type: 'image/jpeg' })];
        fireEvent.dragOver(zeilen[1], { dataTransfer: { types: ['Files'], files: dateien, dropEffect: 'none' } });
        fireEvent.drop(zeilen[1], { dataTransfer: { types: ['Files'], files: dateien } });
        expect(ablage.onDateien).toHaveBeenCalledTimes(1);
        expect(ablage.onDateien).toHaveBeenCalledWith(2, dateien);
    });

    it('zeigt in Suche und Übersicht keine Ordner, in den fälligen Dokumenten die Ordner-Spalte', () => {
        const liste = { ...LISTE, eintraege: [DOK], gesamt: 1 };
        const spalten = (tabelle: string) => within(screen.getByRole('table', { name: tabelle })).getAllByRole('columnheader').map((th) => th.textContent);
        const { rerender, props } = zeige({ art: 'suche', suchbegriff: 'tor' }, { liste });
        expect(spalten('Suchtreffer')).toEqual(['Name', 'Dateityp', 'Fällig am', 'Hochgeladen am', 'Größe', 'Aktionen']);
        expect(screen.getAllByRole('row')).toHaveLength(2);
        expect(screen.getByRole('row', { name: /Torprüfung/ })).toHaveAttribute('draggable', 'true');

        rerender(<RechterBereich {...props} ansicht={{ art: 'uebersicht' }} />);
        expect(spalten('Fällige Dokumente')).toEqual(['Name', 'Dateityp', 'Ordner', 'Fällig am', 'Hochgeladen am', 'Größe', 'Aktionen']);
        expect(screen.getAllByRole('row')).toHaveLength(2);
    });

    it('schaltet zwischen Liste und Vorschau um, mit Regler für die Größe – gilt für alle Ansichten und bleibt gemerkt', async () => {
        const user = userEvent.setup();
        const liste = { ...LISTE, eintraege: [DOK], gesamt: 1 };
        const { rerender, props, unmount } = zeige({ art: 'ordner', ordnerId: 1 }, { liste });
        const umschalter = screen.getByRole('radiogroup', { name: 'Ansicht' });
        expect(within(umschalter).getByRole('radio', { name: 'Liste' })).toHaveAttribute('aria-checked', 'true');
        expect(screen.queryByRole('slider')).toBeNull();
        await user.click(within(umschalter).getByRole('radio', { name: 'Vorschau' }));

        expect(screen.queryByRole('table')).toBeNull();
        expect(within(screen.getByRole('list', { name: 'Dokumente in Prüfungen' })).getAllByRole('listitem')).toHaveLength(3);
        const regler = screen.getByRole('slider', { name: 'Größe der Vorschaubilder' });
        fireEvent.keyDown(regler, { key: 'End' });
        expect(regler).toHaveAttribute('aria-valuenow', '320');
        expect(JSON.parse(localStorage.getItem('erp.gemerkt.firmendokumente.ansicht') ?? '')).toEqual({ art: 'vorschau', groesse: 320 });

        rerender(<RechterBereich {...props} ansicht={{ art: 'suche', suchbegriff: 'tor' }} />);
        expect(screen.getByRole('list', { name: 'Suchtreffer' })).toBeInTheDocument();
        unmount();
        zeige({ art: 'uebersicht' }, { liste });
        expect(screen.getByRole('list', { name: 'Fällige Dokumente' })).toHaveTextContent('Prüfungen');
        expect(screen.getByRole('button', { name: 'Mehr zu: Liste oder Vorschau' })).toBeInTheDocument();
    });

    it('zeigt den Fortschritt beim Hochladen mehrerer Dateien und sperrt solange „Datei hochladen“ mit Grund', () => {
        zeige({ art: 'ordner', ordnerId: 1 }, { stapel: { gesamt: 40, fertig: 12 } });
        const fortschritt = screen.getByRole('progressbar', { name: 'Dateien werden hochgeladen' });
        expect(fortschritt).toHaveAttribute('aria-valuenow', '12');
        expect(fortschritt).toHaveAttribute('aria-valuemax', '40');
        expect(screen.getByText('Lade hoch … 12 von 40')).toBeInTheDocument();
        const knopf = screen.getByRole('button', { name: 'Datei hochladen' });
        expect(knopf).toBeDisabled();
        expect(knopf.closest('[title]')).toHaveAttribute('title', 'Bitte warten, bis die laufenden Dateien hochgeladen sind.');
        expect(screen.getByRole('button', { name: 'Mehr zu: Mehrere Dateien hochladen' })).toBeInTheDocument();
    });

    it('rendert die Kacheln eines Blattordners beim Ziehen am Regler nicht neu', async () => {
        const user = userEvent.setup();
        const angebot: FirmenDokument = { ...DOK, id: 8, ordnerId: 2, name: 'Angebot', originalDateiname: 'angebot.docx', dateiart: 'SONSTIGE' };
        zeige({ art: 'ordner', ordnerId: 2 }, { liste: { ...LISTE, eintraege: [angebot], gesamt: 1 } });
        await user.click(screen.getByRole('radio', { name: 'Vorschau' }));
        const vorher = symbolGezeichnet.mock.calls.length;
        expect(vorher).toBeGreaterThan(0);
        const regler = screen.getByRole('slider', { name: 'Größe der Vorschaubilder' });
        fireEvent.keyDown(regler, { key: 'ArrowRight' });
        fireEvent.keyDown(regler, { key: 'ArrowRight' });
        expect(regler).toHaveAttribute('aria-valuenow', '176');
        expect(symbolGezeichnet.mock.calls.length).toBe(vorher);
    });

    it('bietet beim Hochladen mehrerer Dateien „Abbrechen“ an', async () => {
        const user = userEvent.setup();
        const onStapelAbbrechen = vi.fn();
        const { rerender, props } = zeige({ art: 'ordner', ordnerId: 1 }, { stapel: { gesamt: 40, fertig: 12 }, onStapelAbbrechen });
        await user.click(screen.getByRole('button', { name: 'Hochladen abbrechen' }));
        expect(onStapelAbbrechen).toHaveBeenCalledTimes(1);
        rerender(<RechterBereich {...props} stapel={{ gesamt: 40, fertig: 13, abgebrochen: true }} />);
        expect(screen.getByRole('button', { name: 'Wird abgebrochen …' })).toBeDisabled();
    });
});
