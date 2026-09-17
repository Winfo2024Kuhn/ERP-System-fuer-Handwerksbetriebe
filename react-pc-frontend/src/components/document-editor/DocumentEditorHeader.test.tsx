/**
 * Vitest-Suite fuer DocumentEditorHeader -- Fokus auf die neue, optionale
 * `verlauf`-Prop (Einbindung der VerlaufKnoepfe). Die uebrigen Knoepfe der
 * Kopfleiste sind unveraendert und werden hier nicht einzeln nachgeprueft.
 *
 * DSGVO: nur Dummy-Daten (`Max Mustermann`).
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { ComponentProps } from 'react';
import { DocumentEditorHeader } from './DocumentEditorHeader';
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
