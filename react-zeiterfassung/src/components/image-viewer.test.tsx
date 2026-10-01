import { fireEvent, render, screen } from '@testing-library/react';
import { describe, it, expect, vi, afterEach } from 'vitest';
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

    // Regression: Der Spinner drehte sich für immer, wenn ein Bild schneller lud als
    // der zeitversetzte Reset des Ladezustands. Jetzt hängt der Zustand an der Adresse.
    it('blendet den Ladehinweis aus, sobald das Bild geladen ist', () => {
        render(<ImageViewer src="/api/dokumente/foto.jpg" alt="Baustellenfoto" onClose={() => {}} />);
        expect(screen.getByRole('status', { name: 'Bild wird geladen' })).toBeInTheDocument();

        fireEvent.load(screen.getByAltText('Baustellenfoto'));

        expect(screen.queryByRole('status')).not.toBeInTheDocument();
    });

    it('lädt gespeicherte Bilder in Anzeigegröße und zeigt solange die Vorschau', () => {
        render(<ImageViewer src="/api/images/notiz.jpg" alt="Notizbild" onClose={() => {}} />);

        expect(screen.getByAltText('Notizbild')).toHaveAttribute('src', '/api/dokumente/notiz.jpg/anzeige');
        const vorschau = document.body.querySelector('img[aria-hidden="true"]');
        expect(vorschau).toHaveAttribute('src', '/api/dokumente/notiz.jpg/thumbnail');
    });

    it('fällt auf das Original zurück, wenn die Anzeigegröße fehlt', () => {
        render(<ImageViewer src="/api/dokumente/alt.jpg" alt="Altbild" onClose={() => {}} />);

        fireEvent.error(screen.getByAltText('Altbild'));

        expect(screen.getByAltText('Altbild')).toHaveAttribute('src', '/api/dokumente/alt.jpg');
    });

    it('zeigt einen Fehlerhinweis mit erneutem Versuch, wenn auch das Original fehlt', () => {
        render(<ImageViewer src="/api/dokumente/weg.jpg" alt="Weg" onClose={() => {}} />);

        fireEvent.error(screen.getByAltText('Weg'));
        fireEvent.error(screen.getByAltText('Weg'));

        expect(screen.getByText('Bild konnte nicht geladen werden.')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Erneut versuchen' }));
        expect(screen.getByAltText('Weg')).toHaveAttribute('src', '/api/dokumente/weg.jpg/anzeige');
    });

    it('öffnet beim erneuten Öffnen wieder das angetippte Bild', () => {
        const images = [
            { url: '/api/dokumente/a.jpg', name: 'Bild A' },
            { url: '/api/dokumente/b.jpg', name: 'Bild B' },
        ];
        const { rerender } = render(<ImageViewer src={null} images={[]} startIndex={0} onClose={() => {}} />);

        rerender(<ImageViewer src="/api/dokumente/b.jpg" images={images} startIndex={1} onClose={() => {}} />);
        expect(screen.getByText('2 / 2')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Bild 1 anzeigen' }));
        expect(screen.getByText('1 / 2')).toBeInTheDocument();

        rerender(<ImageViewer src={null} images={[]} startIndex={0} onClose={() => {}} />);
        rerender(<ImageViewer src="/api/dokumente/b.jpg" images={images} startIndex={1} onClose={() => {}} />);
        expect(screen.getByText('2 / 2')).toBeInTheDocument();
    });

    it('schließt über die beschriftete Schaltfläche', () => {
        let geschlossen = false;
        render(<ImageViewer src="/foto.jpg" onClose={() => { geschlossen = true; }} />);

        fireEvent.click(screen.getByRole('button', { name: 'Schließen' }));

        expect(geschlossen).toBe(true);
    });

    describe('Hineinzoomen', () => {
        afterEach(() => vi.unstubAllGlobals());

        /** Image-Ersatz: meldet sofort "geladen", damit das Nachladen des Originals prüfbar wird. */
        function stubImage(geladen: string[]) {
            vi.stubGlobal('Image', class {
                onload: (() => void) | null = null;
                set src(wert: string) {
                    geladen.push(wert);
                    queueMicrotask(() => this.onload?.());
                }
            });
        }

        it('lädt beim Vergrößern das Original nach und tauscht es ein', async () => {
            const geladen: string[] = [];
            stubImage(geladen);
            render(<ImageViewer src="/api/dokumente/typenschild.jpg" alt="Typenschild" onClose={() => {}} />);
            const bild = screen.getByAltText('Typenschild');
            fireEvent.load(bild);
            expect(bild).toHaveAttribute('src', '/api/dokumente/typenschild.jpg/anzeige');
            expect(geladen).not.toContain('/api/dokumente/typenschild.jpg');

            fireEvent.click(screen.getByRole('button', { name: 'Vergrößern' }));

            expect(await screen.findByText('150%')).toBeInTheDocument();
            await vi.waitFor(() => expect(screen.getByAltText('Typenschild')).toHaveAttribute('src', '/api/dokumente/typenschild.jpg'));
            expect(geladen).toContain('/api/dokumente/typenschild.jpg');
            expect(screen.queryByRole('status')).not.toBeInTheDocument();
        });

        it('lädt bei Bildern ohne Varianten nichts doppelt', () => {
            const geladen: string[] = [];
            stubImage(geladen);
            render(<ImageViewer src="blob:http://localhost/foto" alt="Neues Foto" onClose={() => {}} />);

            fireEvent.click(screen.getByRole('button', { name: 'Vergrößern' }));

            expect(geladen).toEqual([]);
            expect(screen.getByAltText('Neues Foto')).toHaveAttribute('src', 'blob:http://localhost/foto');
        });

        it('zeigt im Zoom den Hinweis zum Zurücksetzen statt die Zeile zu entfernen', () => {
            stubImage([]);
            render(<ImageViewer src="/foto.jpg" onClose={() => {}} />);
            expect(screen.getByText('Doppeltippen zum Zoomen')).toBeInTheDocument();

            fireEvent.click(screen.getByRole('button', { name: 'Vergrößern' }));

            expect(screen.getByText('Doppeltippen zum Zurücksetzen')).toBeInTheDocument();
            fireEvent.click(screen.getByRole('button', { name: 'Zoom zurücksetzen' }));
            expect(screen.getByText('Doppeltippen zum Zoomen')).toBeInTheDocument();
        });
    });

    describe('Doppeltippen', () => {
        const bilder = [
            { url: '/api/dokumente/a.jpg', name: 'Bild A' },
            { url: '/api/dokumente/b.jpg', name: 'Bild B' },
            { url: '/api/dokumente/c.jpg', name: 'Bild C' },
        ];
        const flaeche = () => screen.getByAltText('Bild A').parentElement!;
        const finger = (x: number) => ({ clientX: x, clientY: 300 });

        function tippe(el: HTMLElement) {
            fireEvent.touchStart(el, { touches: [finger(200)], changedTouches: [finger(200)] });
            fireEvent.touchEnd(el, { touches: [], changedTouches: [finger(200)] });
        }

        it('zoomt bei zwei schnellen Tipps hinein', () => {
            render(<ImageViewer src="/api/dokumente/a.jpg" images={bilder} startIndex={0} onClose={() => {}} />);

            tippe(flaeche());
            tippe(flaeche());

            expect(screen.getByText('250%')).toBeInTheDocument();
        });

        // Regression: Jeder Fingeraufsatz zählte als Tipp – schnelles Weiterwischen zoomte hinein
        it('zoomt nicht, wenn direkt nach einer Wischgeste wieder getippt wird', () => {
            render(<ImageViewer src="/api/dokumente/a.jpg" images={bilder} startIndex={0} onClose={() => {}} />);
            const el = flaeche();

            fireEvent.touchStart(el, { touches: [finger(320)], changedTouches: [finger(320)] });
            fireEvent.touchMove(el, { touches: [finger(80)], changedTouches: [finger(80)] });
            fireEvent.touchEnd(el, { touches: [], changedTouches: [finger(80)] });
            fireEvent.touchStart(el, { touches: [finger(320)], changedTouches: [finger(320)] });
            fireEvent.touchEnd(el, { touches: [], changedTouches: [finger(320)] });

            expect(screen.getByText('100%')).toBeInTheDocument();
        });
    });
});
