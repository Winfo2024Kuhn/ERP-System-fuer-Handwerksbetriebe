import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { OrdnerBaum, type OrdnerBaumProps } from './OrdnerBaum';
import { baueBaum, leistungsZaehler } from './ordnerLogik';
import type { Leistung, Ordner, ZiehObjekt } from './typen';

const o = (id: number, name: string, parentId: number | null, projektAnzahl = 0): Ordner => ({
    id, name, parentId, bildUrl: null, beschreibung: '', einheit: 'STUECK', einheitAnzeige: 'Stück', projektAnzahl,
});
const ORDNER = [o(1, 'Metallbau', null, 5), o(2, 'Geländer', 1, 3), o(3, 'Treppen', 1), o(4, 'Holzbau', null)];
const LEISTUNGEN: Leistung[] = [
    { id: 10, name: 'Geländer V2A', beschreibung: '<p>Handlauf rund</p>', preis: 1, einheit: 'STUECK', ordnerId: 2 },
    { id: 11, name: 'Geländer verzinkt', beschreibung: '', preis: 1, einheit: 'STUECK', ordnerId: 2 },
    { id: 12, name: 'Glasbrüstung', beschreibung: '', preis: 1, einheit: 'STUECK', ordnerId: 2 },
    { id: 13, name: 'Brüstung Holz', beschreibung: '', preis: 1, einheit: 'STUECK', ordnerId: 2 },
];

/** Baum mit eigenem Zustand wie auf der Seite; Rückrufe sind beobachtbar. */
function Testbaum(ueberschreiben: Partial<OrdnerBaumProps> & { start?: number | null }) {
    const baum = baueBaum(ORDNER);
    const [gewaehlt, setGewaehlt] = useState<number | null>(ueberschreiben.start ?? null);
    const [aufgeklappt, setAufgeklappt] = useState(new Set<number>([1]));
    const [suchtext, setSuchtext] = useState('');
    const [neuerOrdner, setNeuerOrdner] = useState<{ parentId: number | null } | null>(null);
    const [umbenennenId, setUmbenennenId] = useState<number | null>(null);
    const [ziehen, setZiehen] = useState<ZiehObjekt | null>(null);
    return (
        <OrdnerBaum
            baum={baum}
            zaehler={leistungsZaehler(baum, LEISTUNGEN)}
            leistungen={LEISTUNGEN}
            gewaehlt={gewaehlt}
            aufgeklappt={aufgeklappt}
            onAufklappen={(id, offen) => setAufgeklappt((alt) => { const n = new Set(alt); if (offen) n.add(id); else n.delete(id); return n; })}
            onWaehlen={(id) => { setGewaehlt(id); ueberschreiben.onWaehlen?.(id); }}
            suchtext={suchtext}
            onSuchtext={setSuchtext}
            neuerOrdner={neuerOrdner}
            onNeuerOrdner={(parentId) => setNeuerOrdner({ parentId })}
            onNeuerOrdnerAbbrechen={() => setNeuerOrdner(null)}
            onNeuerOrdnerSpeichern={ueberschreiben.onNeuerOrdnerSpeichern ?? vi.fn().mockResolvedValue(undefined)}
            umbenennenId={umbenennenId}
            onUmbenennen={setUmbenennenId}
            onUmbenennenAbbrechen={() => setUmbenennenId(null)}
            onUmbenennenSpeichern={ueberschreiben.onUmbenennenSpeichern ?? (async () => setUmbenennenId(null))}
            onVerschieben={ueberschreiben.onVerschieben ?? vi.fn()}
            onLoeschen={ueberschreiben.onLoeschen ?? vi.fn()}
            ziehen={ziehen}
            onZiehenStart={setZiehen}
            onZiehenEnde={() => setZiehen(null)}
            ablageErlaubt={ueberschreiben.ablageErlaubt ?? ((ziel) => ziel !== 2)}
            onAblegen={ueberschreiben.onAblegen ?? vi.fn()}
            onBildAblegen={ueberschreiben.onBildAblegen ?? vi.fn()}
            onLeistungOeffnen={ueberschreiben.onLeistungOeffnen ?? vi.fn()}
        />
    );
}

const zeile = (name: RegExp) => screen.getByRole('treeitem', { name });

describe('OrdnerBaum', () => {
    it('zeigt Zähler für Leistungen und Projekte vorlesbar an', () => {
        render(<Testbaum />);
        expect(zeile(/^Metallbau, 4 Leistungen, 5 Projekte$/)).toHaveAttribute('aria-expanded', 'true');
        expect(zeile(/^Geländer, 4 Leistungen, 3 Projekte$/)).toHaveAttribute('aria-level', '2');
        expect(zeile(/^Holzbau, 0 Leistungen, 0 Projekte$/)).not.toHaveAttribute('aria-expanded');
    });

    it('legt einen Unterordner direkt in der Zeile an: Hinweis auf wandernde Leistungen, Enter speichert', async () => {
        const speichern = vi.fn().mockResolvedValue(undefined);
        render(<Testbaum start={2} onNeuerOrdnerSpeichern={speichern} />);
        await userEvent.click(screen.getByRole('button', { name: 'Unterordner in „Geländer“ anlegen' }));
        const eingabe = screen.getByRole('textbox', { name: 'Name des neuen Unterordners in „Geländer“' });
        expect(eingabe).toHaveFocus();
        expect(screen.getByText('Die 4 Leistungen aus „Geländer“ wandern in den neuen Unterordner.')).toBeInTheDocument();

        await userEvent.keyboard('{Enter}');
        expect(screen.getByText('Bitte geben Sie dem Ordner einen Namen.')).toBeInTheDocument();
        expect(speichern).not.toHaveBeenCalled();

        await userEvent.type(eingabe, 'Edelstahl{Enter}');
        expect(speichern).toHaveBeenCalledWith(2, 'Edelstahl');
    });

    it('Esc bricht das Anlegen ab, ohne zu speichern', async () => {
        const speichern = vi.fn();
        render(<Testbaum onNeuerOrdnerSpeichern={speichern} />);
        await userEvent.click(screen.getByRole('button', { name: /Ordner$/ }));
        const eingabe = screen.getByRole('textbox', { name: 'Name des neuen Ordners' });
        await userEvent.type(eingabe, 'Tore{Escape}');
        expect(screen.queryByRole('textbox', { name: 'Name des neuen Ordners' })).not.toBeInTheDocument();
        expect(speichern).not.toHaveBeenCalled();
    });

    it('zeigt die Fehlermeldung des Servers an der Eingabezeile', async () => {
        render(<Testbaum onNeuerOrdnerSpeichern={vi.fn().mockRejectedValue(new Error('Der Name ist zu lang.'))} />);
        await userEvent.click(screen.getByRole('button', { name: /Ordner$/ }));
        await userEvent.type(screen.getByRole('textbox', { name: 'Name des neuen Ordners' }), 'Tore{Enter}');
        expect(await screen.findByText('Der Name ist zu lang.')).toBeInTheDocument();
        expect(screen.getByRole('textbox', { name: 'Name des neuen Ordners' })).toHaveValue('Tore');
    });

    it('lässt sich per Tastatur bedienen: Pfeile wandern und klappen, Enter wählt, F2 benennt um, Entf löscht', async () => {
        const onWaehlen = vi.fn();
        const onLoeschen = vi.fn();
        render(<Testbaum onWaehlen={onWaehlen} onLoeschen={onLoeschen} />);
        const uebersicht = screen.getByRole('treeitem', { name: 'Alle Ordner (Übersicht)' });
        uebersicht.focus();
        await userEvent.keyboard('{ArrowDown}');
        expect(zeile(/^Holzbau/)).toHaveFocus();
        await userEvent.keyboard('{ArrowDown}');
        expect(zeile(/^Metallbau/)).toHaveFocus();
        await userEvent.keyboard('{ArrowLeft}');
        expect(zeile(/^Metallbau/)).toHaveAttribute('aria-expanded', 'false');
        await userEvent.keyboard('{ArrowRight}');
        expect(zeile(/^Metallbau/)).toHaveAttribute('aria-expanded', 'true');
        await userEvent.keyboard('{ArrowRight}');
        expect(zeile(/^Geländer/)).toHaveFocus();
        await userEvent.keyboard('{ArrowLeft}');
        expect(zeile(/^Metallbau/)).toHaveFocus();
        await userEvent.keyboard('{End}');
        expect(zeile(/^Treppen/)).toHaveFocus();
        await userEvent.keyboard('{Enter}');
        expect(onWaehlen).toHaveBeenLastCalledWith(3);
        await userEvent.keyboard('{Delete}');
        expect(onLoeschen).toHaveBeenCalledWith(3);
        await userEvent.keyboard('{Home}');
        expect(screen.getByRole('treeitem', { name: 'Alle Ordner (Übersicht)' })).toHaveFocus();
        await userEvent.keyboard(' ');
        expect(onWaehlen).toHaveBeenLastCalledWith(null);
        await userEvent.keyboard('{ArrowDown}{ArrowLeft}');
        expect(screen.getByRole('treeitem', { name: 'Alle Ordner (Übersicht)' })).toHaveFocus();
        await userEvent.keyboard('{ArrowDown}{F2}');
        expect(screen.getByRole('textbox', { name: 'Neuer Name für „Holzbau“' })).toHaveFocus();
    });

    it('benennt um: Enter speichert, unveränderter Name bricht ab', async () => {
        const speichern = vi.fn().mockResolvedValue(undefined);
        render(<Testbaum onUmbenennenSpeichern={speichern} />);
        zeile(/^Holzbau/).focus();
        await userEvent.keyboard('{F2}{Enter}');
        expect(speichern).not.toHaveBeenCalled();
        zeile(/^Holzbau/).focus();
        await userEvent.keyboard('{F2}');
        await userEvent.keyboard('Holzbau & Carports{Enter}');
        expect(speichern).toHaveBeenCalledWith(4, 'Holzbau & Carports');
    });

    it('sucht in Ordnern und Leistungen und klappt Treffer auf', async () => {
        const onLeistungOeffnen = vi.fn();
        render(<Testbaum onLeistungOeffnen={onLeistungOeffnen} />);
        await userEvent.type(screen.getByRole('textbox', { name: 'Ordner und Leistungen suchen' }), 'handlauf');
        expect(zeile(/^Geländer/)).toBeInTheDocument();
        expect(screen.queryByRole('treeitem', { name: /^Holzbau/ })).not.toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: /Geländer V2A/ }));
        expect(onLeistungOeffnen).toHaveBeenCalledWith(LEISTUNGEN[0]);
        await userEvent.clear(screen.getByRole('textbox', { name: 'Ordner und Leistungen suchen' }));
        await userEvent.type(screen.getByRole('textbox', { name: 'Ordner und Leistungen suchen' }), 'gibtsnicht');
        expect(screen.getByText('Keine Ordner oder Leistungen zu „gibtsnicht“.')).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Suche leeren' }));
        expect(zeile(/^Holzbau/)).toBeInTheDocument();
    });

    it('Ziehen: gültige Ziele nehmen an, ungültige lehnen ab; Bilddateien werden Ordnerbild', () => {
        const onAblegen = vi.fn();
        const onBildAblegen = vi.fn();
        render(<Testbaum onAblegen={onAblegen} onBildAblegen={onBildAblegen} />);
        const transfer = { types: ['text/plain'], setData: vi.fn(), effectAllowed: '', dropEffect: '' };
        fireEvent.dragStart(zeile(/^Treppen/), { dataTransfer: transfer });

        // Ungültig (Geländer): kein Ablege-Hinweis, kein Ablegen
        const ungueltig = new Event('dragover', { bubbles: true, cancelable: true });
        Object.assign(ungueltig, { dataTransfer: transfer });
        zeile(/^Geländer/).dispatchEvent(ungueltig);
        expect(ungueltig.defaultPrevented).toBe(false);
        expect(zeile(/^Geländer/).className).toContain('opacity-50');

        fireEvent.dragOver(zeile(/^Holzbau/), { dataTransfer: transfer });
        expect(screen.getByText('Hier ablegen')).toBeInTheDocument();
        fireEvent.drop(zeile(/^Holzbau/), { dataTransfer: transfer });
        expect(onAblegen).toHaveBeenCalledWith(4);
        fireEvent.dragEnd(zeile(/^Treppen/));

        // Bild aus dem Finder
        const bild = new File(['x'], 'foto.png', { type: 'image/png' });
        fireEvent.dragOver(zeile(/^Holzbau/), { dataTransfer: { types: ['Files'] } });
        expect(screen.getByText('Als Ordnerbild')).toBeInTheDocument();
        fireEvent.dragLeave(zeile(/^Holzbau/), { relatedTarget: document.body });
        expect(screen.queryByText('Als Ordnerbild')).not.toBeInTheDocument();
        fireEvent.drop(zeile(/^Holzbau/), { dataTransfer: { types: ['Files'], files: [bild] } });
        expect(onBildAblegen).toHaveBeenCalledWith(4, bild);
    });

    it('Ordner auf „Alle Ordner“ ziehen legt ihn in die oberste Ebene', () => {
        const onAblegen = vi.fn();
        render(<Testbaum onAblegen={onAblegen} ablageErlaubt={() => true} />);
        const transfer = { types: ['text/plain'], setData: vi.fn(), effectAllowed: '', dropEffect: '' };
        fireEvent.dragStart(zeile(/^Treppen/), { dataTransfer: transfer });
        const uebersicht = screen.getByRole('treeitem', { name: 'Alle Ordner (Übersicht)' });
        fireEvent.dragOver(uebersicht, { dataTransfer: transfer });
        expect(screen.getByText('In oberste Ebene')).toBeInTheDocument();
        fireEvent.drop(uebersicht, { dataTransfer: transfer });
        expect(onAblegen).toHaveBeenCalledWith(null);
    });

    it('Menü „…“ bietet Umbenennen, Verschieben und Löschen', async () => {
        const onVerschieben = vi.fn();
        const onLoeschen = vi.fn();
        render(<Testbaum start={2} onVerschieben={onVerschieben} onLoeschen={onLoeschen} />);
        await userEvent.click(screen.getByRole('button', { name: 'Weitere Aktionen für „Geländer“' }));
        await userEvent.click(await screen.findByRole('menuitem', { name: 'Verschieben nach …' }));
        expect(onVerschieben).toHaveBeenCalledWith(2);
        await userEvent.click(screen.getByRole('button', { name: 'Weitere Aktionen für „Geländer“' }));
        await userEvent.click(await screen.findByRole('menuitem', { name: 'Löschen …' }));
        expect(onLoeschen).toHaveBeenCalledWith(2);
        await userEvent.click(screen.getByRole('button', { name: 'Weitere Aktionen für „Geländer“' }));
        await userEvent.click(await screen.findByRole('menuitem', { name: 'Umbenennen' }));
        await waitFor(() => expect(screen.getByRole('textbox', { name: 'Neuer Name für „Geländer“' })).toBeInTheDocument());
    });

    it('zeigt einen leeren Zustand ohne Ordner', () => {
        render(
            <OrdnerBaum baum={baueBaum([])} zaehler={new Map()} leistungen={[]} gewaehlt={null} aufgeklappt={new Set()}
                onAufklappen={vi.fn()} onWaehlen={vi.fn()} suchtext="" onSuchtext={vi.fn()} neuerOrdner={null}
                onNeuerOrdner={vi.fn()} onNeuerOrdnerAbbrechen={vi.fn()} onNeuerOrdnerSpeichern={vi.fn()} umbenennenId={null}
                onUmbenennen={vi.fn()} onUmbenennenAbbrechen={vi.fn()} onUmbenennenSpeichern={vi.fn()} onVerschieben={vi.fn()}
                onLoeschen={vi.fn()} ziehen={null} onZiehenStart={vi.fn()} onZiehenEnde={vi.fn()} ablageErlaubt={() => false}
                onAblegen={vi.fn()} onBildAblegen={vi.fn()} onLeistungOeffnen={vi.fn()} />,
        );
        expect(screen.getByText(/Noch keine Ordner/)).toBeInTheDocument();
    });
});
