import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { FormBlock } from '../../types';
import { ToastProvider } from '../ui/toast';
import PropertiesSidebar from './PropertiesSidebar';
import VorlagenEinstellungen from './VorlagenEinstellungen';
import EditorToolbar from './EditorToolbar';
import { PDF_TEXTFARBE } from './pdfStil';

const mitToast = (ui: React.ReactElement) => render(<ToastProvider>{ui}</ToastProvider>);

describe('PropertiesSidebar', () => {
    const text: FormBlock = { id: 't', type: 'text', page: 1, x: 100, y: 100, z: 1, width: 200, height: 60, content: 'Hallo Welt' };
    const zeige = (block: FormBlock = text) => {
        const props = { onAendern: vi.fn(), onLoeschen: vi.fn(), onDuplizieren: vi.fn(), onEbene: vi.fn(), onSchliessen: vi.fn() };
        mitToast(<PropertiesSidebar block={block} {...props} />);
        return props;
    };

    it('fügt Platzhalter an der Schreibmarke ein', async () => {
        const p = zeige();
        const feld = screen.getByLabelText('Text des Bausteins') as HTMLTextAreaElement;
        feld.setSelectionRange(5, 5);
        fireEvent.click(screen.getByRole('button', { name: 'kundenname' }));
        expect(p.onAendern).toHaveBeenCalledWith('t', { content: 'Hallo{{KUNDENNAME}} Welt' });
        fireEvent.change(feld, { target: { value: 'Neu' } });
        expect(p.onAendern).toHaveBeenLastCalledWith('t', { content: 'Neu' }, 'inhalt-t');
    });

    it('Schrift: fett und Größe schreiben den Druckstil aus', async () => {
        const user = userEvent.setup();
        const p = zeige();
        await user.click(screen.getByRole('button', { name: 'Fett' }));
        expect(p.onAendern).toHaveBeenLastCalledWith('t', { styles: { fontSize: 10, fontWeight: '700', color: PDF_TEXTFARBE } });
        const groesse = screen.getByLabelText('Größe in pt');
        await user.clear(groesse);
        await user.type(groesse, '12,5');
        await user.tab();
        expect(p.onAendern).toHaveBeenLastCalledWith('t', { styles: { fontSize: 12.5, fontWeight: '400', color: PDF_TEXTFARBE } });
    });

    it('richtet an den DIN-Rändern und der Seitenmitte aus, ordnet Ebenen, schließt', async () => {
        const user = userEvent.setup();
        const p = zeige();
        await user.click(screen.getByRole('button', { name: 'An den linken Rand (25 mm)' }));
        expect(p.onAendern).toHaveBeenLastCalledWith('t', { x: 70.87 });
        await user.click(screen.getByRole('button', { name: 'In die Seitenmitte' }));
        expect(p.onAendern).toHaveBeenLastCalledWith('t', { x: 197.5 });
        await user.click(screen.getByRole('button', { name: 'An den rechten Rand (20 mm)' }));
        expect(p.onAendern).toHaveBeenLastCalledWith('t', { x: 338.31 });
        await user.click(screen.getByRole('button', { name: /Nach vorn/ }));
        await user.click(screen.getByRole('button', { name: /Nach hinten/ }));
        expect(p.onEbene).toHaveBeenNthCalledWith(1, 't', 'vorne');
        expect(p.onEbene).toHaveBeenNthCalledWith(2, 't', 'hinten');
        await user.click(screen.getByRole('button', { name: 'Auswahl aufheben' }));
        await user.click(screen.getByRole('button', { name: /Duplizieren/ }));
        await user.click(screen.getByRole('button', { name: /Löschen/ }));
        expect(p.onSchliessen).toHaveBeenCalled();
        expect(p.onDuplizieren).toHaveBeenCalled();
        expect(p.onLoeschen).toHaveBeenCalledWith('t');
    });

    it('Felder, Positionen und Betreff erklären sich statt Eingaben zu zeigen', () => {
        const { unmount } = mitToast(<PropertiesSidebar block={{ ...text, type: 'datum', content: undefined }} onAendern={vi.fn()} onLoeschen={vi.fn()} onDuplizieren={vi.fn()} onEbene={vi.fn()} onSchliessen={vi.fn()} />);
        expect(screen.getByText(/füllt das Programm/)).toBeInTheDocument();
        unmount();
        const { unmount: weg } = mitToast(<PropertiesSidebar block={{ ...text, type: 'table' }} onAendern={vi.fn()} onLoeschen={vi.fn()} onDuplizieren={vi.fn()} onEbene={vi.fn()} onSchliessen={vi.fn()} />);
        expect(screen.getByText(/stehen im fertigen Dokument die Positionen/)).toBeInTheDocument();
        expect(screen.queryByText('Schrift')).not.toBeInTheDocument();
        weg();
        mitToast(<PropertiesSidebar block={{ ...text, content: '{{BETREFF}}' }} onAendern={vi.fn()} onLoeschen={vi.fn()} onDuplizieren={vi.fn()} onEbene={vi.fn()} onSchliessen={vi.fn()} />);
        expect(screen.getByText(/festen Betreff-Schrift/)).toBeInTheDocument();
    });
});

describe('VorlagenEinstellungen', () => {
    const zeige = (briefpapier = { seite1: null as string | null, folgeseiten: null as string | null }) => {
        const props = { onBriefpapier: vi.fn(), onDokumenttypen: vi.fn(), onLayoutZuruecksetzen: vi.fn() };
        mitToast(<VorlagenEinstellungen briefpapier={briefpapier} dokumenttypen={['Rechnung']} {...props} />);
        return props;
    };

    it('ordnet Dokumenttypen zu und ab', async () => {
        const user = userEvent.setup();
        const p = zeige();
        expect(screen.getByRole('checkbox', { name: 'Rechnung' })).toHaveAttribute('aria-checked', 'true');
        await user.click(screen.getByRole('checkbox', { name: 'Rechnung' }));
        expect(p.onDokumenttypen).toHaveBeenLastCalledWith([]);
        await user.click(screen.getByRole('checkbox', { name: 'Angebot' }));
        expect(p.onDokumenttypen).toHaveBeenLastCalledWith(['Rechnung', 'Angebot']);
        await user.click(screen.getByRole('button', { name: /Standard-Layout/ }));
        expect(p.onLayoutZuruecksetzen).toHaveBeenCalled();
    });

    it('übernimmt Rasterbilder als Briefpapier und lehnt anderes mit Meldung ab', async () => {
        const p = zeige();
        const eingabe = screen.getByLabelText('Briefpapier Seite 1 hochladen');
        const png = new File([new Uint8Array([137, 80, 78, 71])], 'briefkopf.png', { type: 'image/png' });
        fireEvent.change(eingabe, { target: { files: [png] } });
        await waitFor(() => expect(p.onBriefpapier).toHaveBeenCalledWith(1, expect.stringMatching(/^data:image\/png;base64,/)));

        const svg = new File(['<svg onload="alert(1)"/>'], 'boese.svg', { type: 'image/svg+xml' });
        fireEvent.change(screen.getByLabelText('Briefpapier Folgeseiten hochladen'), { target: { files: [svg] } });
        expect(await screen.findByText(/PNG-, JPG-, GIF- oder WebP-Bild/)).toBeInTheDocument();
        expect(p.onBriefpapier).toHaveBeenCalledTimes(1);
        fireEvent.change(eingabe, { target: { files: [] } });
        expect(p.onBriefpapier).toHaveBeenCalledTimes(1);
    });

    it('lehnt Bilder über 3,5 MB vorab ab – die Vorlage wäre sonst zu groß zum Speichern', async () => {
        const p = zeige();
        const gross = new File([new Uint8Array(1)], 'briefkopf.png', { type: 'image/png' });
        Object.defineProperty(gross, 'size', { value: 4.2 * 1024 * 1024 });
        fireEvent.change(screen.getByLabelText('Briefpapier Seite 1 hochladen'), { target: { files: [gross] } });
        expect(await screen.findByText('Das Bild ist zu groß (4,2 MB). Bitte ein Bild unter 3,5 MB wählen.')).toBeInTheDocument();
        expect(p.onBriefpapier).not.toHaveBeenCalled();
    });

    it('zeigt vorhandenes Briefpapier und entfernt es', async () => {
        const user = userEvent.setup();
        const p = zeige({ seite1: 'data:image/png;base64,iVBORw0KGgo=', folgeseiten: null });
        expect(screen.getByText('Bild hinterlegt')).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Briefpapier Seite 1 entfernen' }));
        expect(p.onBriefpapier).toHaveBeenCalledWith(1, null);
        expect(screen.getByRole('button', { name: 'Briefpapier Folgeseiten wählen' })).toBeInTheDocument();
    });
});

describe('EditorToolbar', () => {
    const zeige = (name: string | null = 'Briefpapier') => {
        const props = {
            onZurueck: vi.fn(), onUmbenennen: vi.fn(), onRueckgaengig: vi.fn(), onWiederholen: vi.fn(),
            onVorschau: vi.fn(), onSpeichern: vi.fn(), onStandardTexte: vi.fn(),
        };
        render(<EditorToolbar vorlagenName={name} ungespeichert={false} speichert={false} kannRueckgaengig={false} kannWiederholen {...props} />);
        return props;
    };

    it('benennt per Enter um, Escape bricht ab, gleicher Name tut nichts', async () => {
        const user = userEvent.setup();
        const p = zeige();
        await user.click(screen.getByRole('button', { name: /umbenennen/ }));
        await user.keyboard('{Escape}');
        await user.click(screen.getByRole('button', { name: /umbenennen/ }));
        await user.keyboard('{Enter}');
        expect(p.onUmbenennen).not.toHaveBeenCalled();
        await user.click(screen.getByRole('button', { name: /umbenennen/ }));
        await user.clear(screen.getByLabelText('Name der Vorlage'));
        await user.type(screen.getByLabelText('Name der Vorlage'), 'Briefpapier 2026{Enter}');
        expect(p.onUmbenennen).toHaveBeenCalledWith('Briefpapier 2026');
    });

    it('neue Vorlage: Standard-Texte erst nach dem Speichern, Rückgängig gesperrt', async () => {
        const user = userEvent.setup();
        const p = zeige(null);
        expect(screen.getByText('Neue Vorlage')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Standard-Texte/ })).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Rückgängig' })).toBeDisabled();
        await user.click(screen.getByRole('button', { name: 'Wiederholen' }));
        await user.click(screen.getByRole('button', { name: /Vorschau/ }));
        expect(p.onWiederholen).toHaveBeenCalled();
        expect(p.onVorschau).toHaveBeenCalled();
    });
});
