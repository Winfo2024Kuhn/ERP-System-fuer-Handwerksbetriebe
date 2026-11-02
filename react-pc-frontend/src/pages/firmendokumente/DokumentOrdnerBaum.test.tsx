import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { baueBaum } from '../../lib/ordnerBaum';
import { DokumentOrdnerBaum } from './DokumentOrdnerBaum';
import { ordnerMitUeberfaelligem } from './firmendokumenteLogik';
import type { FirmenOrdner } from './typen';

const o = (id: number, name: string, parentId: number | null, anzahlDokumente = 0, anzahlUeberfaellig = 0): FirmenOrdner => ({
    id, name, parentId, anzahlDokumente, anzahlUeberfaellig, anzahlBaldFaellig: 0,
});

const BAUM = baueBaum([
    o(1, 'Prüfungen', null, 0),
    o(2, 'Tore', 1, 3, 1),
    o(3, 'Leitern', 1, 1),
    o(4, 'Arbeitsschutz', null, 2),
    o(5, 'Zeugnisse <img src=x onerror=alert(1)>', null, 0),
]);

function Rahmen(props: { start?: number | null; onAktion?: () => void; onDateienAbgelegt?: () => void; onNeuerOrdner?: () => void }) {
    const [gewaehlt, setGewaehlt] = useState<number | null>(props.start ?? null);
    return (
        <>
            <DokumentOrdnerBaum
                baum={BAUM}
                gewaehlt={gewaehlt}
                ueberfaellig={ordnerMitUeberfaelligem(BAUM)}
                onWaehlen={setGewaehlt}
                onNeuerOrdner={props.onNeuerOrdner ?? vi.fn()}
                onAktion={props.onAktion ?? vi.fn()}
                onDateienAbgelegt={props.onDateienAbgelegt ?? vi.fn()}
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
});
