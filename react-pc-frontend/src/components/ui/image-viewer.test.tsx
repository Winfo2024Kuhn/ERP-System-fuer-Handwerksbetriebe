import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { ImageViewer } from './image-viewer';

describe('ImageViewer', () => {
    it('rendert nichts wenn src null ist', () => {
        const { container } = render(<ImageViewer src={null} onClose={() => {}} />);
        expect(container.firstChild).toBeNull();
    });

    it('zeigt Bild wenn src gesetzt ist', () => {
        render(<ImageViewer src="/test-bild.jpg" alt="Testbild" onClose={() => {}} />);
        const img = screen.getByAltText('Testbild');
        expect(img).toBeInTheDocument();
        expect(img).toHaveAttribute('src', '/test-bild.jpg');
    });

    it('ruft onClose beim Klick auf den Schließen-Button auf', async () => {
        const handleClose = vi.fn();
        const user = userEvent.setup();
        render(<ImageViewer src="/test.jpg" onClose={handleClose} />);
        await user.click(screen.getByTitle('Schließen (ESC)'));
        // Button click propagates to overlay, so onClose is called at least once
        expect(handleClose).toHaveBeenCalled();
    });

    it('zeigt Gallery-Navigation bei mehreren Bildern', () => {
        const images = [
            { url: '/bild1.jpg', name: 'Bild 1' },
            { url: '/bild2.jpg', name: 'Bild 2' },
            { url: '/bild3.jpg', name: 'Bild 3' },
        ];
        render(<ImageViewer src="/bild1.jpg" images={images} startIndex={0} onClose={() => {}} />);
        expect(screen.getByText('1 / 3')).toBeInTheDocument();
    });

    it('navigiert zum nächsten Bild', async () => {
        const user = userEvent.setup();
        const images = [
            { url: '/bild1.jpg', name: 'Bild 1' },
            { url: '/bild2.jpg', name: 'Bild 2' },
        ];
        render(<ImageViewer src="/bild1.jpg" images={images} startIndex={0} onClose={() => {}} />);
        expect(screen.getByText('1 / 2')).toBeInTheDocument();
        await user.click(screen.getByTitle('Nächstes Bild (→)'));
        expect(screen.getByText('2 / 2')).toBeInTheDocument();
    });

    it('navigiert zum vorherigen Bild', async () => {
        const user = userEvent.setup();
        const images = [
            { url: '/bild1.jpg', name: 'Bild 1' },
            { url: '/bild2.jpg', name: 'Bild 2' },
        ];
        render(<ImageViewer src="/bild1.jpg" images={images} startIndex={1} onClose={() => {}} />);
        expect(screen.getByText('2 / 2')).toBeInTheDocument();
        await user.click(screen.getByTitle('Vorheriges Bild (←)'));
        expect(screen.getByText('1 / 2')).toBeInTheDocument();
    });

    it('zeigt keine Navigation bei einem einzelnen Bild', () => {
        render(<ImageViewer src="/einzelbild.jpg" onClose={() => {}} />);
        expect(screen.queryByTitle('Nächstes Bild (→)')).not.toBeInTheDocument();
        expect(screen.queryByTitle('Vorheriges Bild (←)')).not.toBeInTheDocument();
    });

    it('verwendet Fallback-Alt-Text', () => {
        render(<ImageViewer src="/test.jpg" onClose={() => {}} />);
        expect(screen.getByAltText('Vollbild')).toBeInTheDocument();
    });

    it('startet bei 100% Zoom', () => {
        render(<ImageViewer src="/test.jpg" onClose={() => {}} />);
        expect(screen.getByText('100%')).toBeInTheDocument();
    });

    it('vergrößert das Bild bei Klick auf Vergrößern', async () => {
        const user = userEvent.setup();
        render(<ImageViewer src="/test.jpg" onClose={() => {}} />);
        await user.click(screen.getByTitle('Vergrößern (+)'));
        expect(screen.getByText('150%')).toBeInTheDocument();
    });

    it('verkleinert nicht unter 100%', async () => {
        const user = userEvent.setup();
        render(<ImageViewer src="/test.jpg" onClose={() => {}} />);
        const out = screen.getByTitle('Verkleinern (-)');
        expect(out).toBeDisabled();
        await user.click(screen.getByTitle('Vergrößern (+)'));
        expect(screen.getByText('150%')).toBeInTheDocument();
        await user.click(screen.getByTitle('Verkleinern (-)'));
        expect(screen.getByText('100%')).toBeInTheDocument();
    });

    it('setzt den Zoom mit dem Prozent-Button zurück', async () => {
        const user = userEvent.setup();
        render(<ImageViewer src="/test.jpg" onClose={() => {}} />);
        await user.click(screen.getByTitle('Vergrößern (+)'));
        await user.click(screen.getByTitle('Vergrößern (+)'));
        expect(screen.getByText('200%')).toBeInTheDocument();
        await user.click(screen.getByTitle('Zoom zurücksetzen (0)'));
        expect(screen.getByText('100%')).toBeInTheDocument();
    });

    it('blendet Pfeil-Navigation aus, wenn gezoomt ist', async () => {
        const user = userEvent.setup();
        const images = [
            { url: '/bild1.jpg', name: 'Bild 1' },
            { url: '/bild2.jpg', name: 'Bild 2' },
        ];
        render(<ImageViewer src="/bild1.jpg" images={images} startIndex={0} onClose={() => {}} />);
        expect(screen.getByTitle('Nächstes Bild (→)')).toBeInTheDocument();
        await user.click(screen.getByTitle('Vergrößern (+)'));
        expect(screen.queryByTitle('Nächstes Bild (→)')).not.toBeInTheDocument();
    });

    it('toggelt Zoom per Doppelklick auf das Bild', async () => {
        const user = userEvent.setup();
        render(<ImageViewer src="/test.jpg" alt="Foto" onClose={() => {}} />);
        const img = screen.getByAltText('Foto');
        await user.dblClick(img);
        expect(screen.getByText('250%')).toBeInTheDocument();
        await user.dblClick(img);
        expect(screen.getByText('100%')).toBeInTheDocument();
    });
});
