import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { ImageViewer } from './ui/image-viewer';

describe('ImageViewer (Zeiterfassung)', () => {
    it('rendert nichts wenn src null ist', () => {
        const { container } = render(<ImageViewer src={null} onClose={() => {}} />);
        expect(container.firstChild).toBeNull();
    });

    it('zeigt Bild wenn src gesetzt ist', () => {
        render(<ImageViewer src="/foto.jpg" alt="Baustellenfoto" onClose={() => {}} />);
        const img = screen.getByRole('img');
        expect(img).toBeInTheDocument();
        expect(img).toHaveAttribute('src', '/foto.jpg');
    });

    it('zeigt Galerie-Zähler bei mehreren Bildern', () => {
        const images = [
            { url: '/bild1.jpg', name: 'Bild 1' },
            { url: '/bild2.jpg', name: 'Bild 2' },
            { url: '/bild3.jpg', name: 'Bild 3' },
        ];
        render(<ImageViewer src="/bild1.jpg" images={images} startIndex={0} onClose={() => {}} />);
        expect(screen.getByText('1 / 3')).toBeInTheDocument();
    });

    it('zeigt Zoom-Kontrollen', () => {
        render(<ImageViewer src="/test.jpg" onClose={() => {}} />);
        expect(screen.getByText('100%')).toBeInTheDocument();
    });
});
