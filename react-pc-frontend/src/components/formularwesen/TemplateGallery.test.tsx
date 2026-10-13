import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ConfirmProvider } from '../ui/confirm-dialog';
import TemplateGallery from './TemplateGallery';

const VORLAGEN = [
    { name: 'Briefpapier Musterbetrieb', modified: '2026-10-01T09:00:00Z', assignedDokumenttypen: ['Rechnung'] },
    { name: 'Mahnung schlicht', assignedDokumenttypen: [] },
];

function zeige() {
    const props = { onCreateNew: vi.fn(), onEdit: vi.fn(), onDuplicate: vi.fn(), onDelete: vi.fn(), onRefresh: vi.fn() };
    render(<ConfirmProvider><TemplateGallery templates={VORLAGEN} {...props} /></ConfirmProvider>);
    return props;
}

describe('TemplateGallery', () => {
    it('zeigt Aktionen ohne Hover und öffnet, dupliziert, löscht nach Rückfrage', async () => {
        const user = userEvent.setup();
        const p = zeige();
        expect(screen.getByText('Geändert am 01.10.2026')).toBeInTheDocument();
        expect(screen.getByText('Noch keinem Dokumenttyp zugeordnet')).toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: 'Vorlage „Briefpapier Musterbetrieb“ bearbeiten' }));
        await user.click(screen.getAllByRole('button', { name: /Bearbeiten/ })[1]);
        await user.click(screen.getByRole('button', { name: 'Vorlage „Mahnung schlicht“ duplizieren' }));
        expect(p.onEdit).toHaveBeenNthCalledWith(1, 'Briefpapier Musterbetrieb');
        expect(p.onEdit).toHaveBeenNthCalledWith(2, 'Mahnung schlicht');
        expect(p.onDuplicate).toHaveBeenCalledWith('Mahnung schlicht');

        await user.click(screen.getByRole('button', { name: 'Vorlage „Mahnung schlicht“ löschen' }));
        await user.click(await screen.findByRole('button', { name: 'Löschen' }));
        expect(p.onDelete).toHaveBeenCalledWith('Mahnung schlicht');
    });

    it('filtert nach Name und Dokumenttyp', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(screen.getByRole('button', { name: 'Rechnung' }));
        expect(screen.queryByText('Mahnung schlicht')).not.toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: /Alle/ }));
        await user.type(screen.getByPlaceholderText('Vorlagen durchsuchen...'), 'xyz');
        expect(screen.getByText('Keine Vorlagen gefunden')).toBeInTheDocument();
    });
});
