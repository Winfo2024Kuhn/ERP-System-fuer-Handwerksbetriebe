import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { ThumbnailImage } from './ThumbnailImage';

describe('ThumbnailImage', () => {
    it('lädt das Vorschaubild statt des Originals', () => {
        render(
            <ThumbnailImage
                src="/api/dokumente/foto.jpg/thumbnail"
                fallbackSrc="/api/dokumente/foto.jpg"
                alt="Baustellenfoto"
            />
        );
        expect(screen.getByAltText('Baustellenfoto')).toHaveAttribute(
            'src',
            '/api/dokumente/foto.jpg/thumbnail'
        );
    });

    it('lädt Bilder verzögert und asynchron', () => {
        render(<ThumbnailImage src="/thumb.jpg" alt="Foto" />);
        const img = screen.getByAltText('Foto');
        expect(img).toHaveAttribute('loading', 'lazy');
        expect(img).toHaveAttribute('decoding', 'async');
    });

    it('zeigt das Skeleton solange das Bild lädt und blendet es danach aus', () => {
        const { container } = render(<ThumbnailImage src="/thumb.jpg" alt="Foto" />);

        const skeleton = container.querySelector('[aria-hidden="true"]');
        expect(skeleton).toBeInTheDocument();
        expect(skeleton?.className).toContain('animate-pulse');

        const img = screen.getByAltText('Foto');
        expect(img.className).toContain('opacity-0');

        fireEvent.load(img);

        expect(container.querySelector('[aria-hidden="true"]')).not.toBeInTheDocument();
        expect(screen.getByAltText('Foto').className).toContain('opacity-100');
    });

    it('respektiert die Systemeinstellung für reduzierte Bewegung', () => {
        const { container } = render(<ThumbnailImage src="/thumb.jpg" alt="Foto" />);
        expect(container.querySelector('[aria-hidden="true"]')?.className).toContain(
            'motion-safe:animate-pulse'
        );
    });

    it('fällt auf das Originalbild zurück wenn das Vorschaubild fehlschlägt', () => {
        render(
            <ThumbnailImage
                src="/api/dokumente/foto.jpg/thumbnail"
                fallbackSrc="/api/dokumente/foto.jpg"
                alt="Foto"
            />
        );

        fireEvent.error(screen.getByAltText('Foto'));

        expect(screen.getByAltText('Foto')).toHaveAttribute('src', '/api/dokumente/foto.jpg');
    });

    it('zeigt einen Platzhalter wenn auch das Original fehlschlägt', () => {
        render(
            <ThumbnailImage
                src="/api/dokumente/foto.jpg/thumbnail"
                fallbackSrc="/api/dokumente/foto.jpg"
                alt="Foto"
            />
        );

        fireEvent.error(screen.getByAltText('Foto'));
        fireEvent.error(screen.getByAltText('Foto'));

        expect(screen.queryByAltText('Foto')).not.toBeInTheDocument();
        expect(screen.getByTitle('Foto')).toBeInTheDocument();
    });

    it('zeigt den Platzhalter ohne fallbackSrc direkt beim ersten Fehler', () => {
        render(<ThumbnailImage src="/kaputt.jpg" alt="Foto" />);

        fireEvent.error(screen.getByAltText('Foto'));

        expect(screen.queryByAltText('Foto')).not.toBeInTheDocument();
        expect(screen.getByTitle('Foto')).toBeInTheDocument();
    });

    it('startet neu wenn ein anderes Bild angezeigt wird', () => {
        const { rerender, container } = render(<ThumbnailImage src="/erstes.jpg" alt="Foto" />);
        fireEvent.load(screen.getByAltText('Foto'));
        expect(container.querySelector('[aria-hidden="true"]')).not.toBeInTheDocument();

        rerender(<ThumbnailImage src="/zweites.jpg" alt="Foto" />);

        expect(container.querySelector('[aria-hidden="true"]')).toBeInTheDocument();
        expect(screen.getByAltText('Foto')).toHaveAttribute('src', '/zweites.jpg');
    });

    it('übernimmt zusätzliche CSS-Klassen', () => {
        render(<ThumbnailImage src="/thumb.jpg" alt="Foto" className="object-cover" />);
        expect(screen.getByAltText('Foto').className).toContain('object-cover');
    });
});
