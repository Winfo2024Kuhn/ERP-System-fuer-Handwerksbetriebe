/**
 * Vitest-Suite fuer ServiceBlock – Schwerpunkt: Einklappen und Wahlpositions-Menue.
 *
 * Hintergrund: Eine Leistungs-Karte belegt aufgeklappt rund 250 px. Bei 20
 * Positionen ist die Uebersicht weg, deshalb startet jede Karte zugeklappt.
 *
 * DSGVO: ausschliesslich Dummy-Daten.
 */
import { describe, it, expect, vi } from 'vitest';
import { useState } from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ServiceBlock } from './ServiceBlock';
import type { DocBlock } from './types';

vi.mock('../TiptapEditor', () => ({
    TiptapEditor: ({ onChange, verlaufsModus }: { onChange: (v: string, a: string) => void; verlaufsModus?: boolean }) => (
        <button data-testid="tiptap" data-verlaufsmodus={String(!!verlaufsModus)}
                onClick={() => onChange('<p>neu</p>', 'sonstiges')} />
    ),
}));

const block: DocBlock = {
    id: 'b1', type: 'SERVICE', title: 'Geländer Edelstahl',
    quantity: 12, unit: 'm', price: 103.33,
};

const props = {
    block, positionNumber: '3', isLocked: false, isActive: false,
    editorRefs: { current: {} } as never,
    onEditorReady: vi.fn(), onUpdate: vi.fn(), onRemove: vi.fn(),
    onModusWechsel: vi.fn(), onAlternativOeffnen: vi.fn(),
    onFocus: vi.fn(), onEditorFocus: vi.fn(),
};

describe('ServiceBlock Einklappen', () => {
    it('ist standardmaessig zu und zeigt nur Nummer, Kurztext und Summe', () => {
        render(<ServiceBlock {...props} />);

        expect(screen.getByText('3')).toBeInTheDocument();
        expect(screen.getByDisplayValue('Geländer Edelstahl')).toBeInTheDocument();
        expect(screen.getByText(/1.239,96/)).toBeInTheDocument();
        expect(screen.queryByTestId('tiptap')).not.toBeInTheDocument();
        expect(screen.queryByLabelText('Menge')).not.toBeInTheDocument();
    });

    it('zeigt nach Klick auf den Pfeil Beschreibung und Kalkulation', () => {
        render(<ServiceBlock {...props} />);
        fireEvent.click(screen.getByRole('button', { name: /aufklappen/i }));

        expect(screen.getByTestId('tiptap')).toBeInTheDocument();
        expect(screen.getByLabelText('Menge')).toBeInTheDocument();
    });
});

describe('ServiceBlock WYSIWYG-Metriken', () => {
    it('rendert die Beschreibung im Mass der PDF-Bezeichnungsspalte', () => {
        // Die Klasse traegt Schriftgroesse, Zeilen-/Absatzabstand und Spaltenbreite des PDF.
        // Ohne sie bricht der Text im Editor an anderer Stelle um als im Ausdruck.
        const { container } = render(<ServiceBlock {...props} />);
        fireEvent.click(screen.getByRole('button', { name: /aufklappen/i }));

        expect(container.querySelector('.doc-pdf-metrics--spalte')).not.toBeNull();
    });
});

describe('ServiceBlock Wahlpositions-Menue', () => {
    it('laedt eine feste Position mit "Kunde waehlt" zum Umschalten ein', () => {
        render(<ServiceBlock {...props} />);
        expect(screen.getByRole('button', { name: /Kunde wählt/ })).toBeInTheDocument();
    });

    it('nennt eine gruppenlose Wahlposition "Optional"', () => {
        render(<ServiceBlock {...props} block={{ ...block, optional: true }} />);
        expect(screen.getByRole('button', { name: /Optional/ })).toBeInTheDocument();
    });

    it('nennt ein Gruppenmitglied "Alternative"', () => {
        render(<ServiceBlock {...props}
            block={{ ...block, optional: true, alternativGruppe: 'Geländer' }} />);
        expect(screen.getByRole('button', { name: /Alternative/ })).toBeInTheDocument();
    });

    it('schaltet ueber den Menuepunkt "Optional" auf optional um', () => {
        const onModusWechsel = vi.fn();
        render(<ServiceBlock {...props} onModusWechsel={onModusWechsel} />);

        fireEvent.click(screen.getByRole('button', { name: /Kunde wählt/ }));
        fireEvent.click(screen.getByRole('menuitemradio', { name: /Optional/ }));

        expect(onModusWechsel).toHaveBeenCalledWith('b1', 'optional');
    });

    it('oeffnet ueber "Alternativ" den Gruppen-Dialog statt direkt umzuschalten', () => {
        const onModusWechsel = vi.fn();
        const onAlternativOeffnen = vi.fn();
        render(<ServiceBlock {...props}
            onModusWechsel={onModusWechsel} onAlternativOeffnen={onAlternativOeffnen} />);

        fireEvent.click(screen.getByRole('button', { name: /Kunde wählt/ }));
        fireEvent.click(screen.getByRole('menuitemradio', { name: /Alternativ/ }));

        expect(onAlternativOeffnen).toHaveBeenCalledWith('b1');
        expect(onModusWechsel).not.toHaveBeenCalled();
    });

    it('bietet einer Wahlposition den Weg zurueck auf "Fest beauftragt"', () => {
        const onModusWechsel = vi.fn();
        render(<ServiceBlock {...props}
            block={{ ...block, optional: true }} onModusWechsel={onModusWechsel} />);

        fireEvent.click(screen.getByRole('button', { name: /Optional/ }));
        fireEvent.click(screen.getByRole('menuitemradio', { name: /Fest beauftragt/ }));

        expect(onModusWechsel).toHaveBeenCalledWith('b1', 'fest');
    });

    it('oeffnet im gesperrten Dokument kein Menue', () => {
        render(<ServiceBlock {...props} isLocked />);

        fireEvent.click(screen.getByRole('button', { name: /Kunde wählt/ }));

        expect(screen.queryByRole('menu')).not.toBeInTheDocument();
    });
});

describe('ServiceBlock Hinweis auf fehlenden Kundentext', () => {
    // Der PDF-Druck faellt ohne Beschreibung auf den Titel zurueck
    // (RechnungPdfService:881-889). Bei Material ist der Titel die
    // Kurzbeschreibung aus den Stammdaten - reine Innensicht, die nie auf ein
    // Kundendokument darf. Deshalb muss der Bediener es im Editor sehen.
    const material: DocBlock = { ...block, artikelId: 7, description: '' };

    it('weist eine Materialposition ohne Kundentext aus', () => {
        render(<ServiceBlock {...props} block={material} />);

        expect(screen.getByText(/Kein Text für den Kunden/)).toBeInTheDocument();
    });

    it('wertet einen leeren Rich-Text-Rest wie gar keinen Text', () => {
        render(<ServiceBlock {...props} block={{ ...material, description: '<p></p>' }} />);

        expect(screen.getByText(/Kein Text für den Kunden/)).toBeInTheDocument();
    });

    it('schweigt, sobald ein Kundentext da ist', () => {
        render(<ServiceBlock {...props}
            block={{ ...material, description: '<p>T-Stahl 40 x 40 x 5 mm, verzinkt</p>' }} />);

        expect(screen.queryByText(/Kein Text für den Kunden/)).not.toBeInTheDocument();
    });

    it('laesst eine Leistung ohne Beschreibung in Ruhe', () => {
        // Dort ist der Titel ein vom Bediener geschriebener Satz - der Notnagel
        // im PDF ist fuer Leistungen ausdruecklich richtig.
        render(<ServiceBlock {...props} block={{ ...block, description: '' }} />);

        expect(screen.queryByText(/Kein Text für den Kunden/)).not.toBeInTheDocument();
    });
});

describe('ServiceBlock Verlaufsmodus (DOM-Vertraege fuer "Stelle zeigen")', () => {
    it('traegt data-verlauf-feld="title" am Titelfeld (auch zugeklappt sichtbar)', () => {
        render(<ServiceBlock {...props} />);

        expect(screen.getByDisplayValue('Geländer Edelstahl')).toHaveAttribute('data-verlauf-feld', 'title');
    });

    it('traegt data-verlauf-feld an Mengen-, Einheit- und EP-Eingabe sowie am Beschreibungs-Wrapper', () => {
        const { container } = render(<ServiceBlock {...props} />);
        fireEvent.click(screen.getByRole('button', { name: /aufklappen/i }));

        expect(screen.getByLabelText('Menge')).toHaveAttribute('data-verlauf-feld', 'quantity');
        expect(screen.getByLabelText('Einzelpreis')).toHaveAttribute('data-verlauf-feld', 'price');
        expect(screen.getByDisplayValue('m')).toHaveAttribute('data-verlauf-feld', 'unit');
        expect(container.querySelector('[data-verlauf-feld="description"]')).not.toBeNull();
    });

    it('reicht die von TiptapEditor gemeldete Aenderungsart an onUpdate durch', () => {
        const onUpdate = vi.fn();
        render(<ServiceBlock {...props} onUpdate={onUpdate} />);
        fireEvent.click(screen.getByRole('button', { name: /aufklappen/i }));

        fireEvent.click(screen.getByTestId('tiptap'));

        expect(onUpdate).toHaveBeenCalledWith('b1', { description: '<p>neu</p>' }, 'sonstiges');
    });

    it('reicht verlaufsModus an TiptapEditor durch', () => {
        render(<ServiceBlock {...props} verlaufsModus />);
        fireEvent.click(screen.getByRole('button', { name: /aufklappen/i }));

        expect(screen.getByTestId('tiptap')).toHaveAttribute('data-verlaufsmodus', 'true');
    });
});

describe('ServiceBlock Knopf "Als neue Leistung speichern"', () => {
    const knopf = () => screen.queryByRole('button', { name: 'Als neue Leistung speichern' });

    it('zeigt den Knopf schon zugeklappt, wenn die Position abweicht', () => {
        const onAlsNeueLeistungSpeichern = vi.fn();
        render(<ServiceBlock {...props} kannAlsNeueLeistungSpeichern onAlsNeueLeistungSpeichern={onAlsNeueLeistungSpeichern} />);

        expect(screen.queryByLabelText('Menge')).not.toBeInTheDocument();
        fireEvent.click(knopf()!);
        expect(onAlsNeueLeistungSpeichern).toHaveBeenCalledWith('b1');
        // Klick faellt nicht auf die Karte durch.
        expect(props.onFocus).not.toHaveBeenCalledWith('b1');
    });

    it('zeigt keinen Knopf, wenn nichts abweicht', () => {
        render(<ServiceBlock {...props} kannAlsNeueLeistungSpeichern={false} onAlsNeueLeistungSpeichern={vi.fn()} />);
        expect(knopf()).not.toBeInTheDocument();
    });

    it('zeigt keinen Knopf, wenn das Dokument gesperrt ist', () => {
        render(<ServiceBlock {...props} isLocked kannAlsNeueLeistungSpeichern onAlsNeueLeistungSpeichern={vi.fn()} />);
        expect(knopf()).not.toBeInTheDocument();
    });
});

describe('ServiceBlock Menge und Einzelpreis mit Dezimalkomma', () => {
    /** Haelt den Block wie der Editor im State, damit jede Eingabe neu gerendert wird. */
    function MitZustand({ start, onUpdate }: { start: DocBlock; onUpdate: (u: Partial<DocBlock>) => void }) {
        const [b, setB] = useState(start);
        return (
            <ServiceBlock {...props} block={b} defaultCollapsed={false}
                onUpdate={(_id, updates) => { onUpdate(updates); setB(alt => ({ ...alt, ...updates })); }} />
        );
    }

    it('laesst eine fuehrende 0 stehen, damit 0,5 eingegeben werden kann', async () => {
        const onUpdate = vi.fn();
        const start = { ...block, quantity: 1 };
        const { rerender } = render(<MitZustand start={start} onUpdate={onUpdate} />);
        const feld = screen.getByLabelText('Menge');

        // Die vorhandene 1 markieren und mit 0 ueberschreiben.
        await userEvent.type(feld, '0', { initialSelectionStart: 0, initialSelectionEnd: 1 });
        expect(feld).toHaveValue('0');
        expect(onUpdate).toHaveBeenLastCalledWith({ quantity: 0 });

        // Der Editor rendert nach jeder Eingabe noch einmal nach (Verlauf,
        // Autosave). Auch danach muss die 0 stehen bleiben.
        rerender(<MitZustand start={start} onUpdate={onUpdate} />);
        expect(feld).toHaveValue('0');

        await userEvent.type(feld, ',5');
        expect(feld).toHaveValue('0,5');
        expect(onUpdate).toHaveBeenLastCalledWith({ quantity: 0.5 });
    });

    it('nimmt beim Einzelpreis 0,50 an', async () => {
        const onUpdate = vi.fn();
        render(<MitZustand start={{ ...block, price: 0 }} onUpdate={onUpdate} />);
        const feld = screen.getByLabelText('Einzelpreis');

        await userEvent.click(feld);
        await userEvent.type(feld, '0,50');
        expect(feld).toHaveValue('0,50');
        expect(onUpdate).toHaveBeenLastCalledWith({ price: 0.5 });
    });

    it('zeigt vorhandene Werte mit Komma und uebernimmt neue Mengen', async () => {
        const onUpdate = vi.fn();
        render(<MitZustand start={{ ...block, quantity: 2.5 }} onUpdate={onUpdate} />);
        const feld = screen.getByLabelText('Menge');
        expect(feld).toHaveValue('2,5');

        await userEvent.clear(feld);
        await userEvent.type(feld, '12');
        expect(onUpdate).toHaveBeenLastCalledWith({ quantity: 12 });
    });

    it('setzt eine unvollstaendige Eingabe beim Verlassen auf den geltenden Wert zurueck', async () => {
        const onUpdate = vi.fn();
        render(<MitZustand start={{ ...block, quantity: 3 }} onUpdate={onUpdate} />);
        const feld = screen.getByLabelText('Menge');

        await userEvent.type(feld, ',');
        expect(feld).toHaveValue('3,');
        await userEvent.tab();
        expect(feld).toHaveValue('3');
        expect(onUpdate).not.toHaveBeenCalled();
    });

    it('uebernimmt einen von aussen geaenderten Wert (z. B. Rueckgaengig)', () => {
        const { rerender } = render(<ServiceBlock {...props} block={{ ...block, quantity: 4 }} defaultCollapsed={false} />);
        expect(screen.getByLabelText('Menge')).toHaveValue('4');

        rerender(<ServiceBlock {...props} block={{ ...block, quantity: 7.25 }} defaultCollapsed={false} />);
        expect(screen.getByLabelText('Menge')).toHaveValue('7,25');
    });
});
