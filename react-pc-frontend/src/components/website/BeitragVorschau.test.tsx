import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { BeitragVorschau } from './BeitragVorschau';

const standard = {
    titel: 'Neues Tor fuer die Halle',
    textHtml: '<p>Wir haben ein Schiebetor gesetzt.</p>',
    bildUrls: [],
    veroeffentlichtAm: null,
};

describe('BeitragVorschau', () => {
    it('zeigt Titel und Text', () => {
        render(<BeitragVorschau {...standard} />);

        expect(screen.getByRole('heading', { name: 'Neues Tor fuer die Halle' })).toBeInTheDocument();
        expect(screen.getByText('Wir haben ein Schiebetor gesetzt.')).toBeInTheDocument();
    });

    it('zeigt die Brotkrumen wie auf der Website', () => {
        render(<BeitragVorschau {...standard} />);

        expect(screen.getByText('Start')).toBeInTheDocument();
        expect(screen.getByText('Aktuelles')).toBeInTheDocument();
    });

    it('sagt deutlich, wenn der Beitrag noch nicht veroeffentlicht ist', () => {
        render(<BeitragVorschau {...standard} />);

        expect(screen.getByText('Noch nicht veröffentlicht')).toBeInTheDocument();
    });

    it('zeigt Monat und Jahr, sobald ein Datum da ist', () => {
        render(<BeitragVorschau {...standard} veroeffentlichtAm="2026-08-27 10:00:00" />);

        expect(screen.getByText(/August 2026/)).toBeInTheDocument();
    });

    it('zeigt alle Bilder mit Alt-Text', () => {
        render(<BeitragVorschau
            {...standard}
            bildUrls={[
                { url: '/api/dokumente/a.jpg', altText: 'Tor von aussen' },
                { url: '/api/dokumente/b.jpg', altText: '' },
            ]}
        />);

        expect(screen.getByAltText('Tor von aussen')).toBeInTheDocument();
        // Ohne Alt-Text faellt die Website auf den Titel zurueck, das bilden wir nach.
        expect(screen.getByAltText('Neues Tor fuer die Halle')).toBeInTheDocument();
    });

    it('entfernt verbotene Tags aus dem Text', () => {
        render(<BeitragVorschau
            {...standard}
            textHtml='<p>Sauber</p><script>alert(1)</script><h2>Ueberschrift</h2>'
        />);

        expect(screen.getByText('Sauber')).toBeInTheDocument();
        expect(document.querySelector('script')).toBeNull();
        expect(document.querySelector('h2')).toBeNull();
    });

    it('kommt mit leerem Text zurecht', () => {
        render(<BeitragVorschau {...standard} textHtml="" titel="" />);

        expect(screen.getByText('Hier erscheint der Text.')).toBeInTheDocument();
    });
});
