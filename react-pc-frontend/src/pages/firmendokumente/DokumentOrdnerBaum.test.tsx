import { act, fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { baueBaum } from '../../lib/ordnerBaum';
import { DokumentOrdnerBaum } from './DokumentOrdnerBaum';
import { ordnerMitUeberfaelligem, schreibeGezogenesDokument } from './firmendokumenteLogik';
import { schreibeGezogenenOrdner } from './ordnerZiehen';
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
    onOrdnerAbgelegt?: () => void;
    onAblageAbgelehnt?: () => void;
    onNeuerOrdner?: () => void;
}

function Rahmen(props: RahmenProps) {
    const [gewaehlt, setGewaehlt] = useState<number | null>(props.start ?? null);
    const baum = props.baum ?? BAUM;
    const istAdmin = props.istAdmin ?? false;
    return (
        <>
            <DokumentOrdnerBaum
                baum={baum}
                istAdmin={istAdmin}
                gewaehlt={gewaehlt}
                ueberfaellig={ordnerMitUeberfaelligem(BAUM)}
                onWaehlen={setGewaehlt}
                onNeuerOrdner={props.onNeuerOrdner ?? vi.fn()}
                onAktion={props.onAktion ?? vi.fn()}
                ablage={{
                    baum,
                    istAdmin,
                    onDateien: props.onDateienAbgelegt ?? vi.fn(),
                    onDokument: props.onDokumentAbgelegt ?? vi.fn(),
                    onOrdner: props.onOrdnerAbgelegt ?? vi.fn(),
                    onAbgelehnt: props.onAblageAbgelehnt ?? vi.fn(),
                }}
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
        const daten = new Map<string, string>();
        schreibeGezogenesDokument({ setData: (typ, wert) => { daten.set(typ, wert); } }, { id: 10, ordnerId: 2, name: 'Torprüfung', faelligAm: null });
        const transfer = { types: [...daten.keys()], files: [], getData: (typ: string) => daten.get(typ) ?? '', dropEffect: 'none' };

        // Der eigene Ordner leuchtet gar nicht erst auf.
        const tore = zeile(/^Tore/);
        expect(fireEvent.dragOver(tore, { dataTransfer: transfer })).toBe(true);
        expect(tore.className).not.toContain('ring-rose-400');

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

describe('DokumentOrdnerBaum – Ordner per Ziehen verschieben', () => {
    // Fotos Baustelle Mustermann › Montag, Prüfungen, Geschäftsleitung (eingeschränkt) › Verträge
    const FOTOS = baueBaum([
        o(1, 'Fotos Baustelle Mustermann', null, 40),
        o(2, 'Montag', 1, 5),
        o(3, 'Vormittag', 2),
        o(4, 'Prüfungen', null, 2),
        o(6, 'Geschäftsleitung', null, 1, 0, { eingeschraenkt: true }),
        o(7, 'Verträge', 6, 0, 0, { eingeschraenkt: true }),
        o(8, 'Archiv', null, 0, 0, { darfAendern: false }),
    ]);

    /** dataTransfer wie beim Ziehen des Ordners `id` – getData liefert nur beim Ablegen etwas. */
    function ordnerTransfer(id: number) {
        const daten = new Map<string, string>();
        schreibeGezogenenOrdner({ setData: (typ, wert) => { daten.set(typ, wert); } }, { id, name: 'egal' });
        return { types: [...daten.keys()], files: [], getData: (typ: string) => daten.get(typ) ?? '', dropEffect: 'none' };
    }

    it('lässt Ordner mit Recht „Ändern“ ziehen – mit eigenem Datentyp', () => {
        render(<Rahmen baum={FOTOS} start={2} />);
        const montag = zeile(/^Montag/);
        expect(montag).toHaveAttribute('draggable', 'true');
        expect(zeile(/^Archiv/)).not.toHaveAttribute('draggable', 'true');
        const daten = new Map<string, string>();
        const dataTransfer = { setData: (typ: string, wert: string) => daten.set(typ, wert), effectAllowed: '' };
        fireEvent.dragStart(montag, { dataTransfer });
        expect(daten.get('application/x-firmendokument-ordner')).toBe('2');
        expect(dataTransfer.effectAllowed).toBe('move');
    });

    it('legt einen gezogenen Ordner auf einem anderen ab – ungültige Ziele nehmen ihn gar nicht an', () => {
        const onOrdnerAbgelegt = vi.fn();
        render(<Rahmen baum={FOTOS} start={3} onOrdnerAbgelegt={onOrdnerAbgelegt} />);
        const transfer = ordnerTransfer(2);
        // Der Ordner selbst, sein Unterordner, sein jetziger Elternordner und Ordner ohne „Ändern“.
        for (const name of [/^Montag/, /^Vormittag/, /^Fotos Baustelle/, /^Archiv/]) {
            const ziel = zeile(name);
            const drueber = fireEvent.dragOver(ziel, { dataTransfer: transfer });
            expect(drueber).toBe(true);
            expect(ziel.className).not.toContain('ring-rose-400');
            fireEvent.drop(ziel, { dataTransfer: transfer });
        }
        expect(onOrdnerAbgelegt).not.toHaveBeenCalled();

        const pruefungen = zeile(/^Prüfungen/);
        expect(fireEvent.dragOver(pruefungen, { dataTransfer: transfer })).toBe(false);
        expect(pruefungen.className).toContain('ring-rose-400');
        fireEvent.drop(pruefungen, { dataTransfer: transfer });
        expect(onOrdnerAbgelegt).toHaveBeenCalledWith(expect.objectContaining({ id: 2, name: 'Montag' }), expect.objectContaining({ id: 4 }));
    });

    it('macht einen Ordner zum Hauptordner, wenn er auf die Überschrift „Ordner“ fällt', async () => {
        const onOrdnerAbgelegt = vi.fn();
        render(<Rahmen baum={FOTOS} start={2} onOrdnerAbgelegt={onOrdnerAbgelegt} />);
        expect(screen.queryByText('Hier ablegen: als Hauptordner')).toBeNull();
        const transfer = ordnerTransfer(2);
        fireEvent.dragStart(zeile(/^Montag/), { dataTransfer: { setData: vi.fn(), effectAllowed: '' } });
        fireEvent(document, Object.assign(new Event('dragstart', { bubbles: true }), { dataTransfer: transfer }));
        const kopf = await screen.findByText('Hier ablegen: als Hauptordner');
        const flaeche = kopf.closest('[data-ablage="hauptordner"]') as HTMLElement;
        expect(fireEvent.dragOver(flaeche, { dataTransfer: transfer })).toBe(false);
        fireEvent.drop(flaeche, { dataTransfer: transfer });
        expect(onOrdnerAbgelegt).toHaveBeenCalledWith(expect.objectContaining({ id: 2 }), null);
        fireEvent(document, new Event('dragend', { bubbles: true }));
        await vi.waitFor(() => expect(screen.queryByText('Hier ablegen: als Hauptordner')).toBeNull());
        expect(screen.getByRole('heading', { name: 'Ordner' })).toBeInTheDocument();
    });

    it('nimmt einen Hauptordner nicht auf der Überschrift an – er ist schon einer', () => {
        const onOrdnerAbgelegt = vi.fn();
        const { container } = render(<Rahmen baum={FOTOS} onOrdnerAbgelegt={onOrdnerAbgelegt} />);
        const flaeche = container.querySelector('[data-ablage="hauptordner"]') as HTMLElement;
        const transfer = ordnerTransfer(4);
        expect(fireEvent.dragOver(flaeche, { dataTransfer: transfer })).toBe(true);
        fireEvent.drop(flaeche, { dataTransfer: transfer });
        expect(onOrdnerAbgelegt).not.toHaveBeenCalled();
    });

    it('lässt einen Ordner aus einem eingeschränkten Zweig nur als Admin in einen anderen Hauptordner', () => {
        const onOrdnerAbgelegt = vi.fn();
        const onAblageAbgelehnt = vi.fn();
        const { unmount } = render(<Rahmen baum={FOTOS} start={7} onOrdnerAbgelegt={onOrdnerAbgelegt} onAblageAbgelehnt={onAblageAbgelehnt} />);
        fireEvent.drop(zeile(/^Prüfungen/), { dataTransfer: ordnerTransfer(7) });
        expect(onOrdnerAbgelegt).not.toHaveBeenCalled();
        expect(onAblageAbgelehnt).toHaveBeenCalledWith('Aus einem eingeschränkten Ordner kann nur ein Admin in einen anderen Hauptordner verschieben.');
        unmount();

        render(<Rahmen baum={FOTOS} start={7} istAdmin onOrdnerAbgelegt={onOrdnerAbgelegt} />);
        fireEvent.drop(zeile(/^Prüfungen/), { dataTransfer: ordnerTransfer(7) });
        expect(onOrdnerAbgelegt).toHaveBeenCalledWith(expect.objectContaining({ id: 7 }), expect.objectContaining({ id: 4 }));
    });

    it('klappt einen zugeklappten Ordner auf, wenn man beim Ziehen kurz darüber verharrt', () => {
        vi.useFakeTimers();
        try {
            render(<Rahmen baum={FOTOS} />);
            const fotos = zeile(/^Fotos Baustelle/);
            const transfer = ordnerTransfer(4);
            fireEvent.dragOver(fotos, { dataTransfer: transfer });
            act(() => { vi.advanceTimersByTime(500); });
            fireEvent.dragOver(fotos, { dataTransfer: transfer });
            expect(fotos).toHaveAttribute('aria-expanded', 'false');
            act(() => { vi.advanceTimersByTime(300); });
            expect(fotos).toHaveAttribute('aria-expanded', 'true');

            // Kurz drüber und weiter: bleibt zu.
            const montag = zeile(/^Montag/);
            fireEvent.dragOver(montag, { dataTransfer: transfer });
            act(() => { vi.advanceTimersByTime(300); });
            fireEvent(montag, new MouseEvent('dragleave', { bubbles: true, relatedTarget: document.body }));
            act(() => { vi.advanceTimersByTime(1000); });
            expect(montag).toHaveAttribute('aria-expanded', 'false');
        } finally {
            vi.useRealTimers();
        }
    });
});
