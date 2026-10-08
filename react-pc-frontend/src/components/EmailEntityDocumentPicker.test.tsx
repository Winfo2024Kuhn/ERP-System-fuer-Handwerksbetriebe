import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { EmailEntityDocumentPicker } from './EmailEntityDocumentPicker';
import type { ProjektDokument } from '../types';

const bild: ProjektDokument = {
    id: -5,
    originalDateiname: 'baustelle.pdf',
    url: '/api/dokumente/abc_baustelle.pdf',
    dokumentGruppe: 'DIVERSE_DOKUMENTE',
};

describe('EmailEntityDocumentPicker', () => {
    it('zeigt den Ordner Bautagebuch nur, wenn es Dateien daraus gibt', () => {
        const { rerender } = render(
            <EmailEntityDocumentPicker documents={[]} loading={false} selectedIds={new Set()} loadingIds={new Set()} onToggle={vi.fn()} />
        );
        expect(screen.queryByText('Bautagebuch')).toBeNull();

        rerender(
            <EmailEntityDocumentPicker documents={[]} loading={false} selectedIds={new Set()} loadingIds={new Set()} onToggle={vi.fn()} bautagebuchDocuments={[bild]} />
        );
        expect(screen.getByText('Bautagebuch')).toBeTruthy();
    });

    it('meldet die Auswahl einer Bautagebuch-Datei', () => {
        const onToggle = vi.fn();
        render(
            <EmailEntityDocumentPicker documents={[]} loading={false} selectedIds={new Set()} loadingIds={new Set()} onToggle={onToggle} bautagebuchDocuments={[bild]} />
        );
        fireEvent.click(screen.getByText('Bautagebuch'));
        fireEvent.click(screen.getByRole('checkbox'));
        expect(onToggle).toHaveBeenCalledWith(bild);
    });
});
