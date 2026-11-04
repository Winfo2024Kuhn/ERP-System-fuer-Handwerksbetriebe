import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import DocumentPreviewModal from './DocumentPreviewModal';

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
});
