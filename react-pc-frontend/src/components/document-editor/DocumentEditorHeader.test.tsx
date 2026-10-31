/**
 * Vitest-Suite fuer DocumentEditorHeader -- Fokus auf die neue, optionale
 * `verlauf`-Prop (Einbindung der VerlaufKnoepfe). Die uebrigen Knoepfe der
 * Kopfleiste sind unveraendert und werden hier nicht einzeln nachgeprueft.
 *
 * DSGVO: nur Dummy-Daten (`Max Mustermann`).
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ComponentProps } from 'react';
import { DocumentEditorHeader } from './DocumentEditorHeader';
import { shopUnterzeile } from './shopUnterzeile';
import type { VerlaufKnoepfeProps } from './VerlaufKnoepfe';

function baueProps(
    overrides: Partial<ComponentProps<typeof DocumentEditorHeader>> = {}
): ComponentProps<typeof DocumentEditorHeader> {
    return {
        dokumentNummer: 'AN-2026-001',
        kontextInfo: 'Max Mustermann',
        isLocked: false,
        istGebucht: false,
        saving: false,
        saveSuccess: false,
        hasUnsavedChanges: false,
        previewLoading: false,
        dokument: null,
        emailLoading: false,
        onClose: vi.fn(),
        onSave: vi.fn(),
        onOpenTextbausteinPicker: vi.fn(),
        onOpenLeistungPicker: vi.fn(),
        onOpenStundensatzPicker: vi.fn(),
        onOpenMaterialPicker: vi.fn(),
        onAddSeparator: vi.fn(),
        onAddSectionHeader: vi.fn(),
        onOpenRabattDialog: vi.fn(),
        onExport: vi.fn(),
        onPrint: vi.fn(),
        onSendEmail: vi.fn(),
        onSendDraft: vi.fn(),
        onGaebImport: vi.fn(),
        fileInputRef: { current: null },
        onFileChange: vi.fn(),
        ...overrides,
    };
}

const verlaufProps: VerlaufKnoepfeProps = {
    kannRueckgaengig: true,
    kannWiederholen: false,
    naechstesRueckgaengig: 'Position gelöscht',
    naechstesWiederholen: null,
    schritte: [{ id: 1, bezeichnung: 'Position gelöscht' }],
    onRueckgaengig: vi.fn(),
    onWiederholen: vi.fn(),
};

describe('DocumentEditorHeader Verlauf-Integration', () => {
    it('rendert die Verlaufsknoepfe links von "Textbaustein", wenn die Prop gesetzt ist', () => {
        const { container } = render(<DocumentEditorHeader {...baueProps({ verlauf: verlaufProps })} />);

        const buttons = Array.from(container.querySelectorAll('button'));
        const rueckgaengigIndex = buttons.findIndex(b => b.getAttribute('aria-label') === 'Rückgängig');
        const textbausteinIndex = buttons.findIndex(b => b.textContent?.includes('Textbaustein'));

        expect(rueckgaengigIndex).toBeGreaterThanOrEqual(0);
        expect(textbausteinIndex).toBeGreaterThan(rueckgaengigIndex);
    });

    it('rendert ohne verlauf-Prop keinen Rueckgaengig-Knopf (abwaertskompatibel)', () => {
        render(<DocumentEditorHeader {...baueProps()} />);

        expect(screen.queryByRole('button', { name: 'Rückgängig' })).not.toBeInTheDocument();
    });

    it('rendert bei gesperrtem Dokument weder die mittlere Gruppe noch die Verlaufsknoepfe', () => {
        render(<DocumentEditorHeader {...baueProps({ verlauf: verlaufProps, isLocked: true })} />);

        expect(screen.queryByRole('button', { name: 'Rückgängig' })).not.toBeInTheDocument();
        expect(screen.queryByText('Textbaustein')).not.toBeInTheDocument();
    });
});

describe('DocumentEditorHeader – Material (Artikelstamm oder Lieferanten-Shop)', () => {
    it('ohne Shop-Anbindung öffnet „Material“ direkt den Artikelstamm – kein Menü', async () => {
        const onOpenMaterialPicker = vi.fn();
        render(<DocumentEditorHeader {...baueProps({ onOpenMaterialPicker })} />);
        const knopf = screen.getByRole('button', { name: 'Material' });
        expect(knopf).not.toHaveAttribute('aria-haspopup');
        await userEvent.click(knopf);
        expect(onOpenMaterialPicker).toHaveBeenCalledTimes(1);
        expect(screen.queryByRole('menu')).not.toBeInTheDocument();
        expect(screen.queryByText('Aus Lieferanten-Shop')).not.toBeInTheDocument();
    });

    it('mit Shop-Anbindung ist „Material“ ein Menü mit zwei beschriebenen Wegen', async () => {
        const onOpenMaterialPicker = vi.fn();
        const onOpenShopWarenkorb = vi.fn();
        render(<DocumentEditorHeader {...baueProps({ onOpenMaterialPicker, onOpenShopWarenkorb, shopLieferantenNamen: ['Muster Großhandel'] })} />);
        const knopf = screen.getByRole('button', { name: 'Material' });
        expect(knopf).toHaveAttribute('aria-haspopup', 'menu');
        expect(knopf).toHaveAttribute('aria-expanded', 'false');

        await userEvent.click(knopf);
        const menue = await screen.findByRole('menu');
        expect(knopf).toHaveAttribute('aria-expanded', 'true');
        const eintraege = within(menue).getAllByRole('menuitem');
        expect(eintraege).toHaveLength(2);
        expect(eintraege[0]).toHaveTextContent('Aus Artikelstamm');
        expect(eintraege[0]).toHaveTextContent('Artikel, die schon im Programm gepflegt sind');
        expect(eintraege[1]).toHaveTextContent('Aus Lieferanten-Shop');
        expect(eintraege[1]).toHaveTextContent('Muster Großhandel – aktuelle Einkaufspreise holen');

        await userEvent.click(eintraege[1]);
        expect(onOpenShopWarenkorb).toHaveBeenCalledTimes(1);
        expect(onOpenMaterialPicker).not.toHaveBeenCalled();
        await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument());

        await userEvent.click(knopf);
        await userEvent.click(await screen.findByRole('menuitem', { name: /Aus Artikelstamm/ }));
        expect(onOpenMaterialPicker).toHaveBeenCalledTimes(1);
    });

    it('lässt sich ganz mit der Tastatur bedienen', async () => {
        const onOpenShopWarenkorb = vi.fn();
        render(<DocumentEditorHeader {...baueProps({ onOpenShopWarenkorb })} />);
        const knopf = screen.getByRole('button', { name: 'Material' });
        knopf.focus();
        await userEvent.keyboard('{Enter}');
        const menue = await screen.findByRole('menu');
        await waitFor(() => expect(within(menue).getAllByRole('menuitem')[0]).toHaveFocus());
        await userEvent.keyboard('{ArrowDown}');
        expect(within(menue).getAllByRole('menuitem')[1]).toHaveFocus();
        await userEvent.keyboard('{Enter}');
        expect(onOpenShopWarenkorb).toHaveBeenCalledTimes(1);

        await userEvent.click(knopf);
        await screen.findByRole('menu');
        await userEvent.keyboard('{Escape}');
        await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument());
        expect(knopf).toHaveFocus();
    });

    it('zeigt bei gesperrtem Dokument kein Material-Menü', () => {
        render(<DocumentEditorHeader {...baueProps({ onOpenShopWarenkorb: vi.fn(), isLocked: true })} />);
        expect(screen.queryByRole('button', { name: 'Material' })).not.toBeInTheDocument();
    });
});

describe('shopUnterzeile', () => {
    it('nennt den oder die Lieferanten', () => {
        expect(shopUnterzeile()).toBe('Würth & Co. – aktuelle Einkaufspreise holen');
        expect(shopUnterzeile(['  '])).toBe('Würth & Co. – aktuelle Einkaufspreise holen');
        expect(shopUnterzeile(['Muster Großhandel'])).toBe('Muster Großhandel – aktuelle Einkaufspreise holen');
        expect(shopUnterzeile(['Muster AG', 'Beispiel KG'])).toBe('Muster AG & Beispiel KG – aktuelle Einkaufspreise holen');
        expect(shopUnterzeile(['Muster AG', 'Beispiel KG', 'Dritte GmbH'])).toBe('Muster AG, Beispiel KG & weitere – aktuelle Einkaufspreise holen');
    });
});

describe('DocumentEditorHeader – lange Dokumentnummer', () => {
    it('kürzt die Nummer statt sie zu überlappen und zeigt sie samt Kunde voll im Tooltip', () => {
        const { rerender } = render(<DocumentEditorHeader {...baueProps({ dokumentNummer: 'RE-2026/10/00031' })} />);
        const titel = screen.getByRole('heading', { name: 'RE-2026/10/00031' });
        expect(titel).toHaveAttribute('title', 'RE-2026/10/00031 – Max Mustermann');
        expect(titel.className).toContain('truncate');
        expect(titel.className).not.toContain('flex-shrink-0');
        expect(titel.parentElement?.className).toContain('flex-1');

        rerender(<DocumentEditorHeader {...baueProps({ dokumentNummer: '', kontextInfo: '' })} />);
        expect(screen.getByRole('heading', { name: 'Neues Dokument' })).toHaveAttribute('title', 'Neues Dokument');
    });
});
