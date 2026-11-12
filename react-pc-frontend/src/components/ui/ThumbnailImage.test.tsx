import { render, screen, fireEvent } from '@testing-library/react';
import { act } from 'react';
import { describe, it, expect, vi } from 'vitest';
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

    it('zeigt bei Fehler das mitgegebene Ersatz-Symbol – ohne auf das Original zurückzufallen', () => {
        const { container } = render(
            <ThumbnailImage src="/api/firmendokumente/7/vorschaubild" alt="" ersatz={<span data-testid="ersatz">PDF</span>} />,
        );
        const img = container.querySelector('img') as HTMLImageElement;
        fireEvent.error(img);
        expect(screen.getByTestId('ersatz')).toBeInTheDocument();
        expect(container.querySelector('img')).toBeNull();
        expect(container.querySelector('[role="img"]')).toBeNull();
    });

    it('versucht es nach einem Fehler einmal später erneut, erst danach kommt der Ersatz', () => {
        vi.useFakeTimers();
        try {
            const { container } = render(
                <ThumbnailImage src="/api/firmendokumente/7/vorschaubild" alt="" ersatz={<span data-testid="ersatz">PDF</span>}
                    wiederholen={{ anzahl: 1, nachMs: 5000 }} />,
            );
            fireEvent.error(container.querySelector('img') as HTMLImageElement);
            expect(screen.queryByTestId('ersatz')).toBeNull();
            expect(container.querySelector('[class*="animate-pulse"]')).not.toBeNull();
            act(() => { vi.advanceTimersByTime(4999); });
            expect(container.querySelector('img')).toHaveAttribute('src', '/api/firmendokumente/7/vorschaubild');
            act(() => { vi.advanceTimersByTime(1); });
            const neu = container.querySelector('img') as HTMLImageElement;
            expect(neu).toHaveAttribute('src', '/api/firmendokumente/7/vorschaubild?v=1');
            fireEvent.error(neu);
            expect(screen.getByTestId('ersatz')).toBeInTheDocument();
        } finally {
            vi.useRealTimers();
        }
    });
});
