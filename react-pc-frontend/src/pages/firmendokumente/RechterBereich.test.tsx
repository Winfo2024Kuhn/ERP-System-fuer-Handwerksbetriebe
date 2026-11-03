import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { baueBaum } from '../../lib/ordnerBaum';
import type { DokumentAktionen } from './DokumentZeilenAktionen';
import { RechterBereich, type ListenStand } from './RechterBereich';
import type { FirmenDokument, FirmenOrdner } from './typen';

const o = (id: number, name: string, darfAendern: boolean): FirmenOrdner => ({
    id, name, parentId: null, anzahlDokumente: 0, anzahlUeberfaellig: 0, anzahlBaldFaellig: 0, darfAendern, eingeschraenkt: !darfAendern,
});
const BAUM = baueBaum([o(1, 'Prüfungen', true), o(6, 'Geschäftsleitung', false)]);
const LISTE: ListenStand = { eintraege: [], gesamt: 0, laedt: false, laedtMehr: false, fehler: null, mehrLaden: vi.fn(), neuLaden: vi.fn() };
const AKTIONEN: DokumentAktionen = { ansehen: vi.fn(), drucken: vi.fn(), herunterladen: vi.fn(), bearbeiten: vi.fn(), loeschen: vi.fn() };

function zeige(ordnerId: number) {
    const onHochladen = vi.fn();
    const onDateienAbgelegt = vi.fn();
    render(<RechterBereich ansicht={{ art: 'ordner', ordnerId }} baum={BAUM} liste={LISTE} aktionen={AKTIONEN}
        onHochladen={onHochladen} onDateienAbgelegt={onDateienAbgelegt} onOrdnerWaehlen={vi.fn()} />);
    return { onHochladen, onDateienAbgelegt };
}

describe('RechterBereich', () => {
    it('bietet Hochladen per Knopf und Ziehen für jede Datei an', async () => {
        const user = userEvent.setup();
        const { onHochladen, onDateienAbgelegt } = zeige(1);
        expect(screen.getByText('Dateien hierher ziehen oder über den Knopf auswählen.')).toBeInTheDocument();
        expect(screen.getByText('In diesem Ordner liegt noch kein Dokument. Über „Datei hochladen“ oder per Ziehen ablegen.')).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Datei hochladen' }));
        expect(onHochladen).toHaveBeenCalled();
        const dateien = [new File(['x'], 'foto.jpg', { type: 'image/jpeg' }), new File(['x'], 'liste.xlsx')];
        fireEvent.drop(screen.getByRole('region', { name: 'Prüfungen' }), { dataTransfer: { types: ['Files'], files: dateien } });
        expect(onDateienAbgelegt).toHaveBeenCalledWith(1, dateien);
    });

    it('zeigt ohne Recht „Ändern“ nur einen ruhigen Hinweis – kein Hochladen, kein Ablegen', () => {
        const { onDateienAbgelegt } = zeige(6);
        expect(screen.getByText('Du kannst diesen Ordner nur ansehen.')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Datei hochladen' })).toBeNull();
        expect(screen.getByText('In diesem Ordner liegt noch kein Dokument.')).toBeInTheDocument();
        const ablage = screen.getByRole('region', { name: 'Geschäftsleitung' });
        const drueber = fireEvent.dragOver(ablage, { dataTransfer: { types: ['Files'], files: [], dropEffect: 'none' } });
        expect(drueber).toBe(true);
        fireEvent.drop(ablage, { dataTransfer: { types: ['Files'], files: [new File(['x'], 'foto.jpg')] } });
        expect(onDateienAbgelegt).not.toHaveBeenCalled();
    });

    it('zeigt in den Suchtreffern keine Ordner-Spalte, in den fälligen Dokumenten schon', () => {
        const dok: FirmenDokument = {
            id: 7, ordnerId: 1, name: 'Torprüfung', originalDateiname: 'scan.pdf', groesseBytes: 1000, hochgeladenAm: '2026-11-02T09:14:00',
            hochgeladenVon: 'Max Mustermann', faelligAm: null, url: '/api/firmendokumente/7/datei', dateiart: 'PDF', darfAendern: true,
        };
        const liste = { ...LISTE, eintraege: [dok], gesamt: 1 };
        const spalten = (tabelle: string) => within(screen.getByRole('table', { name: tabelle })).getAllByRole('columnheader').map((th) => th.textContent);
        const { rerender } = render(<RechterBereich ansicht={{ art: 'suche', suchbegriff: 'tor' }} baum={BAUM} liste={liste} aktionen={AKTIONEN}
            onHochladen={vi.fn()} onDateienAbgelegt={vi.fn()} onOrdnerWaehlen={vi.fn()} />);
        expect(spalten('Suchtreffer')).toEqual(['Name', 'Dateityp', 'Fällig am', 'Hochgeladen am', 'Größe', 'Aktionen']);
        expect(screen.getByRole('row', { name: /Torprüfung/ })).toHaveAttribute('draggable', 'true');

        rerender(<RechterBereich ansicht={{ art: 'uebersicht' }} baum={BAUM} liste={liste} aktionen={AKTIONEN}
            onHochladen={vi.fn()} onDateienAbgelegt={vi.fn()} onOrdnerWaehlen={vi.fn()} />);
        expect(spalten('Fällige Dokumente')).toEqual(['Name', 'Dateityp', 'Ordner', 'Fällig am', 'Hochgeladen am', 'Größe', 'Aktionen']);
        expect(screen.getByRole('row', { name: /Torprüfung/ })).toHaveAttribute('draggable', 'true');
    });
});
