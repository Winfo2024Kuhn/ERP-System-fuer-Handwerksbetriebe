import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import DocumentPreviewModal from './DocumentPreviewModal';
import { Dialog } from './ui/dialog';

vi.mock('./ui/PdfCanvasViewer', () => ({ PdfCanvasViewer: () => <div data-testid="pdf" /> }));

describe('DocumentPreviewModal', () => {
    it('hat einen beschrifteten Schließen-Knopf mit Tooltip', () => {
        const onClose = vi.fn();
        render(<DocumentPreviewModal doc={{ url: '/api/dokumente/1', title: 'Zeichnung.pdf' }} isPdf onClose={onClose} />);
        const schliessen = screen.getByRole('button', { name: 'Vorschau schließen' });
        expect(schliessen).toHaveAttribute('title', 'Vorschau schließen');
        fireEvent.click(schliessen);
        expect(onClose).toHaveBeenCalled();
    });

    it('ist ein modales Fenster mit dem Titel als Namen und Fokus auf „Vorschau schließen“', () => {
        render(<DocumentPreviewModal doc={{ url: '/api/dokumente/1', title: 'Max-Mustermann-Angebot.pdf' }} isPdf onClose={() => {}} />);
        const fenster = screen.getByRole('dialog', { name: 'Max-Mustermann-Angebot.pdf' });
        expect(fenster).toHaveAttribute('aria-modal', 'true');
        expect(fenster.parentElement).toBe(document.body);
        expect(screen.getByRole('button', { name: 'Vorschau schließen' })).toHaveFocus();
    });

    it('Escape über einem Dialog schließt nur die Vorschau, Fokus zurück an den Opener', async () => {
        const user = userEvent.setup();
        const dialogZu = vi.fn();
        function ImDialog() {
            const [offen, setOffen] = useState(false);
            return (
                <Dialog open onOpenChange={dialogZu} aria-label="Rechtstext bearbeiten">
                    <button onClick={() => setOffen(true)}>Vorschau anzeigen</button>
                    {offen && <DocumentPreviewModal doc={{ url: '/api/x.pdf', title: 'AGB' }} isPdf onClose={() => setOffen(false)} />}
                </Dialog>
            );
        }
        render(<ImDialog />);
        const opener = screen.getByRole('button', { name: 'Vorschau anzeigen' });
        await user.click(opener);
        expect(screen.getByRole('dialog', { name: 'AGB' })).toBeInTheDocument();
        await user.tab();
        expect(screen.getByRole('dialog', { name: 'AGB' })).toContainElement(document.activeElement as HTMLElement);

        await user.keyboard('{Escape}');
        expect(screen.queryByRole('dialog', { name: 'AGB' })).not.toBeInTheDocument();
        expect(dialogZu).not.toHaveBeenCalled();
        expect(opener).toHaveFocus();
    });

    it('liegt über einem Dialog und unter dem Anruf-Fenster (z-index 70)', () => {
        render(
            <Dialog open onOpenChange={() => {}} aria-label="Rechtstext bearbeiten">
                <DocumentPreviewModal doc={{ url: '/api/x.pdf', title: 'AGB' }} isPdf onClose={() => {}} />
            </Dialog>,
        );
        const zIndex = Number(screen.getByRole('dialog', { name: 'AGB' }).style.zIndex);
        expect(zIndex).toBeGreaterThan(50);
        expect(zIndex).toBeLessThan(70);
    });

    it('bietet bei unsicherer Adresse weder Vorschau noch Herunterladen oder neuen Tab an', () => {
        render(<DocumentPreviewModal doc={{ url: 'javascript:alert(1)', title: 'Max-Mustermann.pdf' }} isPdf onClose={() => {}} />);
        expect(screen.queryByTestId('pdf')).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Herunterladen/ })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Neuer Tab/ })).not.toBeInTheDocument();
        expect(screen.getByText('Vorschau nicht verfügbar')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Vorschau schließen' })).toHaveFocus();
    });

    it('nutzt die geprüfte Adresse für Vorschau und neuen Tab', () => {
        const oeffnen = vi.spyOn(window, 'open').mockReturnValue(null);
        render(<DocumentPreviewModal doc={{ url: '/api/dokumente/1', title: 'Plan.pdf' }} isPdf onClose={() => {}} />);
        expect(screen.getByTestId('pdf')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: /Neuer Tab/ }));
        expect(oeffnen).toHaveBeenCalledWith('/api/dokumente/1', '_blank', 'noopener,noreferrer');
        oeffnen.mockRestore();
    });
});
