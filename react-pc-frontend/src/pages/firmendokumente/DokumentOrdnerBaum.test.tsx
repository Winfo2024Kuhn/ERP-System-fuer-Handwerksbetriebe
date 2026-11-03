import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { baueBaum } from '../../lib/ordnerBaum';
import { DokumentOrdnerBaum } from './DokumentOrdnerBaum';
import { ordnerMitUeberfaelligem } from './firmendokumenteLogik';
import type { FirmenOrdner } from './typen';

const o = (id: number, name: string, parentId: number | null, anzahlDokumente = 0, anzahlUeberfaellig = 0, extra: Partial<FirmenOrdner> = {}): FirmenOrdner => ({
    id, name, parentId, anzahlDokumente, anzahlUeberfaellig, anzahlBaldFaellig: 0, darfAendern: true, eingeschraenkt: false, ...extra,
});

const BAUM = baueBaum([
    o(1, 'Prüfungen', null, 0),
    o(2, 'Tore', 1, 3, 1),
    o(3, 'Leitern', 1, 1),
    o(4, 'Arbeitsschutz', null, 2),
    o(5, 'Zeugnisse <img src=x onerror=alert(1)>', null, 0),
]);

// Geschäftsleitung ist eingeschränkt und für den Angemeldeten nur zum Ansehen.
const MIT_RECHTEN = baueBaum([
    o(1, 'Prüfungen', null, 0),
    o(2, 'Tore', 1, 3),
    o(6, 'Geschäftsleitung', null, 1, 0, { darfAendern: false, eingeschraenkt: true }),
    o(7, 'Verträge', 6, 0, 0, { darfAendern: false, eingeschraenkt: true }),
]);

interface RahmenProps {
    start?: number | null;
    baum?: typeof BAUM;
    istAdmin?: boolean;
    onAktion?: () => void;
    onDateienAbgelegt?: () => void;
    onDokumentAbgelegt?: () => void;
    onAblageAbgelehnt?: () => void;
    onNeuerOrdner?: () => void;
}

function Rahmen(props: RahmenProps) {
    const [gewaehlt, setGewaehlt] = useState<number | null>(props.start ?? null);
    return (
        <>
            <DokumentOrdnerBaum
                baum={props.baum ?? BAUM}
                istAdmin={props.istAdmin ?? false}
                gewaehlt={gewaehlt}
                ueberfaellig={ordnerMitUeberfaelligem(BAUM)}
                onWaehlen={setGewaehlt}
                onNeuerOrdner={props.onNeuerOrdner ?? vi.fn()}
                onAktion={props.onAktion ?? vi.fn()}
                onDateienAbgelegt={props.onDateienAbgelegt ?? vi.fn()}
                onDokumentAbgelegt={props.onDokumentAbgelegt ?? vi.fn()}
                onAblageAbgelehnt={props.onAblageAbgelehnt ?? vi.fn()}
            />
            <output>{gewaehlt === null ? 'Übersicht' : `Ordner ${gewaehlt}`}</output>
        </>
    );
}

const baum = () => screen.getByRole('tree', { name: 'Ordner' });
const zeile = (name: RegExp) => within(baum()).getByRole('treeitem', { name });

describe('DokumentOrdnerBaum', () => {
    it('zeigt Übersicht und Hauptordner mit Anzahl, Ebene und rotem Punkt', () => {
        render(<Rahmen />);
        const uebersicht = zeile(/^Übersicht$/);
        expect(uebersicht).toHaveAttribute('aria-selected', 'true');
        expect(uebersicht).toHaveAttribute('tabindex', '0');
        const pruefungen = zeile(/^Prüfungen, 0 Dokumente, enthält überfällige Dokumente$/);
        expect(pruefungen).toHaveAttribute('aria-expanded', 'false');
        expect(pruefungen).toHaveAttribute('aria-level', '1');
        expect(pruefungen).toHaveAttribute('tabindex', '-1');
        expect(zeile(/^Arbeitsschutz, 2 Dokumente$/)).not.toHaveAttribute('aria-expanded');
        expect(within(baum()).queryByRole('treeitem', { name: /^Tore/ })).toBeNull();
    });

    it('zeigt einen Ordnernamen mit HTML als reinen Text', () => {
        const { container } = render(<Rahmen />);
        expect(screen.getByText('Zeugnisse <img src=x onerror=alert(1)>')).toBeInTheDocument();
        expect(container.querySelector('img')).toBeNull();
    });

    it('bedient sich mit der Tastatur wie ein Baum', async () => {
        const user = userEvent.setup();
        render(<Rahmen />);
        zeile(/^Übersicht$/).focus();
        await user.keyboard('{ArrowDown}{ArrowDown}');
        const pruefungen = zeile(/^Prüfungen/);
        expect(pruefungen).toHaveFocus();
        await user.keyboard('{ArrowRight}');
        expect(pruefungen).toHaveAttribute('aria-expanded', 'true');
        await user.keyboard('{ArrowRight}');
        const leitern = zeile(/^Leitern/);
        expect(leitern).toHaveFocus();
        expect(leitern).toHaveAttribute('aria-level', '2');
        await user.keyboard('{Enter}');
        expect(screen.getByRole('status')).toHaveTextContent('Ordner 3');
        expect(leitern).toHaveAttribute('aria-selected', 'true');
        await user.keyboard('{ArrowLeft}');
        expect(pruefungen).toHaveFocus();
        await user.keyboard('{ArrowLeft}');
        expect(pruefungen).toHaveAttribute('aria-expanded', 'false');
        await user.keyboard('{ArrowLeft}');
        expect(zeile(/^Übersicht$/)).toHaveFocus();
        await user.keyboard('{End}');
        expect(zeile(/^Zeugnisse <img/)).toHaveFocus();
        await user.keyboard('{Home}{ArrowUp} ');
        expect(zeile(/^Übersicht$/)).toHaveFocus();
        expect(screen.getByRole('status')).toHaveTextContent('Übersicht');
    });

    it('klappt die übergeordneten Ordner des gewählten Ordners auf', () => {
        render(<Rahmen start={2} />);
        expect(zeile(/^Prüfungen/)).toHaveAttribute('aria-expanded', 'true');
        const tore = zeile(/^Tore, 3 Dokumente, enthält überfällige Dokumente$/);
        expect(tore).toHaveAttribute('aria-selected', 'true');
        expect(tore).toHaveAttribute('tabindex', '0');
    });

    it('klappt per Pfeil-Knopf auf und zu, ohne den Ordner zu wählen', async () => {
        const user = userEvent.setup();
        const { container } = render(<Rahmen />);
        const pfeil = zeile(/^Prüfungen/).querySelector('button[aria-hidden="true"]') as HTMLButtonElement;
        await user.click(pfeil);
        expect(zeile(/^Prüfungen/)).toHaveAttribute('aria-expanded', 'true');
        expect(screen.getByRole('status')).toHaveTextContent('Übersicht');
        await user.click(pfeil);
        expect(container.querySelectorAll('[role="treeitem"]')).toHaveLength(4);
    });

    it('bietet je Ordner Unterordner, Umbenennen, Verschieben und Löschen an', async () => {
        const user = userEvent.setup();
        const onAktion = vi.fn();
        const onNeuerOrdner = vi.fn();
        render(<Rahmen onAktion={onAktion} onNeuerOrdner={onNeuerOrdner} />);
        await user.click(screen.getByRole('button', { name: 'Neuer Ordner' }));
        expect(onNeuerOrdner).toHaveBeenCalled();
        for (const [punkt, aktion] of [['Unterordner anlegen', 'unterordner'], ['Umbenennen', 'umbenennen'], ['Verschieben …', 'verschieben'], ['Löschen', 'loeschen']] as const) {
            await user.click(screen.getByRole('button', { name: 'Weitere Aktionen für „Arbeitsschutz“' }));
            await user.click(await screen.findByRole('menuitem', { name: punkt }));
            expect(onAktion).toHaveBeenLastCalledWith(aktion, expect.objectContaining({ id: 4 }));
        }
        expect(screen.getByRole('status')).toHaveTextContent('Übersicht');
    });

    it('nimmt abgelegte Dateien auf einem Ordner an, aber keinen gezogenen Text', () => {
        const onDateienAbgelegt = vi.fn();
        render(<Rahmen onDateienAbgelegt={onDateienAbgelegt} />);
        const ziel = zeile(/^Arbeitsschutz/);
        const dateien = [new File(['%PDF-1.7'], 'bericht.pdf', { type: 'application/pdf' })];
        fireEvent.dragOver(ziel, { dataTransfer: { types: ['Files'], files: dateien, dropEffect: 'none' } });
        expect(ziel.className).toContain('ring-rose-400');
        // Über ein Kind-Element (Name, Zähler) zu fahren, löscht die Markierung nicht.
        // jsdom kennt kein DragEvent mit relatedTarget – ein MouseEvent gleichen Namens trägt es.
        const verlassen = (wohin: Element) => fireEvent(ziel, new MouseEvent('dragleave', { bubbles: true, relatedTarget: wohin }));
        verlassen(within(ziel).getByText('Arbeitsschutz'));
        expect(ziel.className).toContain('ring-rose-400');
        verlassen(document.body);
        expect(ziel.className).not.toContain('ring-rose-400');
        fireEvent.drop(ziel, { dataTransfer: { types: ['text/plain'], files: [] } });
        expect(onDateienAbgelegt).not.toHaveBeenCalled();
        fireEvent.drop(ziel, { dataTransfer: { types: ['Files'], files: dateien } });
        expect(onDateienAbgelegt).toHaveBeenCalledWith(4, dateien);
    });

    it('nimmt Dateien nur auf Ordnern mit Recht „Ändern“ an – ohne Hervorheben sonst', () => {
        const onDateienAbgelegt = vi.fn();
        render(<Rahmen baum={MIT_RECHTEN} onDateienAbgelegt={onDateienAbgelegt} />);
        const ziel = zeile(/^Geschäftsleitung/);
        const dateien = [new File(['x'], 'foto.jpg', { type: 'image/jpeg' }), new File(['x'], 'liste.xlsx')];
        const drueber = fireEvent.dragOver(ziel, { dataTransfer: { types: ['Files'], files: dateien, dropEffect: 'none' } });
        expect(drueber).toBe(true); // nicht verhindert = Browser zeigt „Ablegen nicht möglich“
        expect(ziel.className).not.toContain('ring-rose-400');
        fireEvent.drop(ziel, { dataTransfer: { types: ['Files'], files: dateien } });
        expect(onDateienAbgelegt).not.toHaveBeenCalled();
        fireEvent.drop(zeile(/^Prüfungen/), { dataTransfer: { types: ['Files'], files: dateien } });
        expect(onDateienAbgelegt).toHaveBeenCalledWith(1, dateien);
    });

    it('verschiebt ein gezogenes Dokument auf einen anderen Ordner mit Recht „Ändern“', () => {
        const onDokumentAbgelegt = vi.fn();
        const onDateienAbgelegt = vi.fn();
        render(<Rahmen baum={MIT_RECHTEN} start={2} onDokumentAbgelegt={onDokumentAbgelegt} onDateienAbgelegt={onDateienAbgelegt} />);
        const daten = JSON.stringify({ id: 10, ordnerId: 2, name: 'Torprüfung', faelligAm: null });
        const transfer = { types: ['application/x-firmendokument', 'text/plain'], files: [], getData: (typ: string) => (typ === 'application/x-firmendokument' ? daten : ''), dropEffect: 'none' };

        const pruefungen = zeile(/^Prüfungen/);
        fireEvent.dragOver(pruefungen, { dataTransfer: transfer });
        expect(pruefungen.className).toContain('ring-rose-400');
        fireEvent.drop(pruefungen, { dataTransfer: transfer });
        expect(onDokumentAbgelegt).toHaveBeenCalledWith({ id: 10, ordnerId: 2, name: 'Torprüfung', faelligAm: null }, expect.objectContaining({ id: 1 }));
        expect(onDateienAbgelegt).not.toHaveBeenCalled();

        // In den eigenen Ordner und in Ordner ohne Recht „Ändern“: nichts.
        onDokumentAbgelegt.mockClear();
        fireEvent.drop(zeile(/^Tore/), { dataTransfer: transfer });
        const geschaeft = zeile(/^Geschäftsleitung/);
        fireEvent.dragOver(geschaeft, { dataTransfer: transfer });
        expect(geschaeft.className).not.toContain('ring-rose-400');
        fireEvent.drop(geschaeft, { dataTransfer: transfer });
        expect(onDokumentAbgelegt).not.toHaveBeenCalled();
    });

    it('zeigt ein Schloss hinter eingeschränkten Ordnern', () => {
        render(<Rahmen baum={MIT_RECHTEN} />);
        const geschaeft = zeile(/^Geschäftsleitung/);
        expect(geschaeft).toHaveAccessibleName('Geschäftsleitung, 1 Dokument, nur für bestimmte Abteilungen sichtbar');
        expect(within(geschaeft).getByTitle('Nur für bestimmte Abteilungen sichtbar')).toBeInTheDocument();
        expect(within(zeile(/^Prüfungen/)).queryByTitle('Nur für bestimmte Abteilungen sichtbar')).toBeNull();
    });

    it('blendet Ordner-Aktionen ohne Recht „Ändern“ aus – ganz ohne Menü, wenn nichts bleibt', async () => {
        render(<Rahmen baum={MIT_RECHTEN} />);
        expect(screen.queryByRole('button', { name: 'Weitere Aktionen für „Geschäftsleitung“' })).toBeNull();
        expect(screen.getByRole('button', { name: 'Weitere Aktionen für „Prüfungen“' })).toBeInTheDocument();
        // Neuer oberster Ordner bleibt für alle.
        expect(screen.getByRole('button', { name: 'Neuer Ordner' })).toBeInTheDocument();
    });

    it('bietet „Wer darf das sehen?“ nur Admins und nur an obersten Ordnern an', async () => {
        const user = userEvent.setup();
        const onAktion = vi.fn();
        const { unmount } = render(<Rahmen baum={MIT_RECHTEN} start={2} onAktion={onAktion} />);
        await user.click(screen.getByRole('button', { name: 'Weitere Aktionen für „Prüfungen“' }));
        expect(await screen.findByRole('menuitem', { name: 'Umbenennen' })).toBeInTheDocument();
        expect(screen.queryByRole('menuitem', { name: 'Wer darf das sehen?' })).toBeNull();
        unmount();

        render(<Rahmen baum={MIT_RECHTEN} start={2} istAdmin onAktion={onAktion} />);
        await user.click(screen.getByRole('button', { name: 'Weitere Aktionen für „Prüfungen“' }));
        await user.click(await screen.findByRole('menuitem', { name: 'Wer darf das sehen?' }));
        expect(onAktion).toHaveBeenCalledWith('rechte', expect.objectContaining({ id: 1 }));
        await user.click(screen.getByRole('button', { name: 'Weitere Aktionen für „Tore“' }));
        expect(await screen.findByRole('menuitem', { name: 'Umbenennen' })).toBeInTheDocument();
        expect(screen.queryByRole('menuitem', { name: 'Wer darf das sehen?' })).toBeNull();
    });

    it('lässt ein Dokument aus einem eingeschränkten Zweig nur als Admin in einen anderen Hauptordner fallen', () => {
        // Geschäftsleitung ist eingeschränkt, hier aber mit Recht „Ändern“.
        const baum = baueBaum([
            o(1, 'Prüfungen', null, 0),
            o(6, 'Geschäftsleitung', null, 1, 0, { eingeschraenkt: true }),
            o(7, 'Verträge', 6, 0, 0, { eingeschraenkt: true }),
        ]);
        const daten = JSON.stringify({ id: 13, ordnerId: 7, name: 'Gesellschaftervertrag', faelligAm: null });
        const transfer = { types: ['application/x-firmendokument'], files: [], getData: () => daten, dropEffect: 'none' };
        const onDokumentAbgelegt = vi.fn();
        const onAblageAbgelehnt = vi.fn();
        const { unmount } = render(<Rahmen baum={baum} start={7} onDokumentAbgelegt={onDokumentAbgelegt} onAblageAbgelehnt={onAblageAbgelehnt} />);
        fireEvent.drop(zeile(/^Prüfungen/), { dataTransfer: transfer });
        expect(onDokumentAbgelegt).not.toHaveBeenCalled();
        expect(onAblageAbgelehnt).toHaveBeenCalledWith('Aus einem eingeschränkten Ordner kann nur ein Admin in einen anderen Hauptordner verschieben.');
        // Im selben Zweig geht es.
        fireEvent.drop(zeile(/^Geschäftsleitung/), { dataTransfer: transfer });
        expect(onDokumentAbgelegt).toHaveBeenCalledWith(expect.objectContaining({ id: 13 }), expect.objectContaining({ id: 6 }));
        unmount();

        onDokumentAbgelegt.mockClear();
        render(<Rahmen baum={baum} start={7} istAdmin onDokumentAbgelegt={onDokumentAbgelegt} />);
        fireEvent.drop(zeile(/^Prüfungen/), { dataTransfer: transfer });
        expect(onDokumentAbgelegt).toHaveBeenCalledWith(expect.objectContaining({ id: 13 }), expect.objectContaining({ id: 1 }));
    });
});
