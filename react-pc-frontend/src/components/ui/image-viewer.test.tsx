import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { Dialog } from './dialog';
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
        await user.click(screen.getByRole('button', { name: 'Vorschau schließen' }));
        expect(handleClose).toHaveBeenCalledTimes(1);
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

describe('ImageViewer – Herunterladen', () => {
    it('bietet das aktuelle Bild mit seinem Dateinamen zum Herunterladen an', async () => {
        const user = userEvent.setup();
        const images = [
            { url: '/api/bilder/1', name: 'Gelaender-vorher.jpg' },
            { url: '/api/bilder/2', name: 'Gelaender-nachher.jpg' },
        ];
        render(<ImageViewer src="/api/bilder/1" images={images} startIndex={0} onClose={() => {}} />);

        const link = screen.getByRole('link', { name: 'Bild herunterladen' });
        expect(link).toHaveAttribute('href', '/api/bilder/1');
        expect(link).toHaveAttribute('download', 'Gelaender-vorher.jpg');

        await user.click(screen.getByTitle('Nächstes Bild (→)'));
        expect(screen.getByRole('link', { name: 'Bild herunterladen' })).toHaveAttribute('download', 'Gelaender-nachher.jpg');
    });

    it('nimmt ohne Namen den letzten Teil der Adresse als Dateinamen', () => {
        render(<ImageViewer src="/uploads/tor%20vorne.png?v=2" images={[{ url: '/uploads/tor%20vorne.png?v=2' }]} onClose={() => {}} />);
        expect(screen.getByRole('link', { name: 'Bild herunterladen' })).toHaveAttribute('download', 'tor vorne.png');
    });

    it('bietet keinen Download für unsichere Adressen an', () => {
        render(<ImageViewer src="javascript:alert(1)" alt="Bild" onClose={() => {}} />);
        expect(screen.queryByRole('link', { name: 'Bild herunterladen' })).toBeNull();
    });

    it('schließt den Betrachter nicht beim Klick auf Herunterladen', async () => {
        const handleClose = vi.fn();
        const user = userEvent.setup();
        render(<ImageViewer src="/bild.jpg" alt="bild.jpg" onClose={handleClose} />);
        const link = screen.getByRole('link', { name: 'Bild herunterladen' });
        link.addEventListener('click', e => e.preventDefault());
        await user.click(link);
        expect(handleClose).not.toHaveBeenCalled();
    });
});

describe('ImageViewer – eigenes Fenster für Tastatur und Screenreader', () => {
    const fotos = [
        { url: '/api/emails/7/attachments/3', name: 'Max-Mustermann-Baustelle.jpg' },
        { url: '/api/emails/7/attachments/4', name: 'Max-Mustermann-Tor.jpg' },
    ];

    it('ist ein modales Fenster mit dem Dateinamen als Namen, Fokus auf „Vorschau schließen“', () => {
        render(<ImageViewer src={fotos[0].url} images={fotos} startIndex={0} onClose={() => {}} />);
        const fenster = screen.getByRole('dialog', { name: 'Max-Mustermann-Baustelle.jpg' });
        expect(fenster).toHaveAttribute('aria-modal', 'true');
        expect(fenster.parentElement).toBe(document.body);
        expect(screen.getByRole('button', { name: 'Vorschau schließen' })).toHaveFocus();
    });

    it('nennt nach dem Blättern den Namen des gezeigten Bildes', async () => {
        const user = userEvent.setup();
        render(<ImageViewer src={fotos[0].url} images={fotos} startIndex={0} onClose={() => {}} />);
        await user.click(screen.getByRole('button', { name: 'Nächstes Bild' }));
        expect(screen.getByRole('dialog', { name: 'Max-Mustermann-Tor.jpg' })).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Vorheriges Bild' }));
        expect(screen.getByRole('dialog', { name: 'Max-Mustermann-Baustelle.jpg' })).toBeInTheDocument();
    });

    it('beschriftet alle Icon-Knöpfe und zeigt einen Fokus-Ring', () => {
        render(<ImageViewer src={fotos[0].url} images={fotos} startIndex={1} onClose={() => {}} />);
        for (const name of ['Vorschau schließen', 'Verkleinern', 'Vergrößern', /^Zoom zurücksetzen/, 'Vorheriges Bild']) {
            const knopf = screen.getByRole('button', { name });
            expect(knopf.className).toContain('focus-visible:ring-2');
            expect(knopf.className).not.toContain('transition-all');
        }
    });

    it('blendet nur bei erlaubter Bewegung ein und zoomt nur dann weich', () => {
        render(<ImageViewer src="/bild.jpg" alt="bild.jpg" onClose={() => {}} />);
        const fenster = screen.getByRole('dialog');
        expect(fenster.className).not.toMatch(/(^|\s)animate-in/);
        expect(fenster.className).toContain('motion-safe:animate-in');
        const bild = screen.getByAltText('bild.jpg');
        expect(bild.className).not.toMatch(/(^|\s)animate-in/);
        expect(bild.className).toContain('motion-safe:transition-transform');
        expect(bild.style.transition).toBe('');
    });

    it('Escape über einem Dialog schließt nur den Betrachter', async () => {
        const user = userEvent.setup();
        const dialogZu = vi.fn();
        const betrachterZu = vi.fn();
        render(
            <Dialog open onOpenChange={dialogZu} aria-label="Verlauf">
                <ImageViewer src={fotos[0].url} alt={fotos[0].name} onClose={betrachterZu} />
            </Dialog>,
        );
        await user.keyboard('{Escape}');
        expect(betrachterZu).toHaveBeenCalledTimes(1);
        expect(dialogZu).not.toHaveBeenCalled();
    });

    it('zoomt mit + und setzt mit 0 zurück', async () => {
        const user = userEvent.setup();
        render(<ImageViewer src="/bild.jpg" onClose={() => {}} />);
        await user.keyboard('+');
        expect(screen.getByText('150%')).toBeInTheDocument();
        await user.keyboard('0');
        expect(screen.getByText('100%')).toBeInTheDocument();
    });

    it('lässt Strg/Cmd + und Alt+Pfeil beim Browser', async () => {
        const user = userEvent.setup();
        const images = [{ url: '/a.jpg', name: 'a.jpg' }, { url: '/b.jpg', name: 'b.jpg' }];
        render(<ImageViewer src="/a.jpg" images={images} startIndex={0} onClose={() => {}} />);
        await user.keyboard('{Control>}+{/Control}{Meta>}+{/Meta}{Alt>}{ArrowRight}{/Alt}');
        expect(screen.getByText('100%')).toBeInTheDocument();
        expect(screen.getByText('1 / 2')).toBeInTheDocument();
    });

    it('liegt über einem Dialog und unter dem Anruf-Fenster (z-index 70)', () => {
        render(
            <Dialog open onOpenChange={() => {}} aria-label="Verlauf">
                <ImageViewer src="/bild.jpg" alt="bild.jpg" onClose={() => {}} />
            </Dialog>,
        );
        const zIndex = Number(screen.getByRole('dialog', { name: 'bild.jpg' }).style.zIndex);
        expect(zIndex).toBeGreaterThan(50);
        expect(zIndex).toBeLessThan(70);
    });

    it('zoomt mit dem Mausrad auch, wenn der Betrachter erst nach dem Einbinden geöffnet wird', () => {
        const { rerender } = render(<ImageViewer src={null} onClose={() => {}} />);
        rerender(<ImageViewer src="/bild.jpg" onClose={() => {}} />);
        fireEvent.wheel(screen.getByRole('dialog'), { deltaY: -100 });
        expect(screen.getByText('120%')).toBeInTheDocument();
    });
});

describe('ImageViewer – Bild außerhalb der Galerie', () => {
    it('zeigt das angeklickte Bild, wenn es nicht in der Galerie steht (startIndex -1)', () => {
        // E-Mail-Center: Die Galerie kommt aus der gewählten Mail, das Foto aus einer anderen Nachricht des Verlaufs.
        const galerie = [{ url: '/api/emails/1/attachments/1', name: 'Plan.jpg' }];
        render(<ImageViewer src="/api/emails/2/attachments/5" alt="Max-Mustermann-Tor.jpg" images={galerie} startIndex={-1} onClose={() => {}} />);
        expect(screen.getByRole('dialog', { name: 'Max-Mustermann-Tor.jpg' })).toBeInTheDocument();
        expect(screen.getByAltText('Max-Mustermann-Tor.jpg')).toHaveAttribute('src', '/api/emails/2/attachments/5');
        expect(screen.queryByText('1 / 1')).not.toBeInTheDocument();
    });
});
