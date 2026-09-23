import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { QuellenLink } from './QuellenLink';

describe('QuellenLink', () => {
  it('opens a safe document URL and strips unsafe markup from source text', () => {
    render(<QuellenLink quelle={{ typ: 'DOKUMENT', id: 48, bezeichnung: '<img src=x onerror=alert(1)>Lieferschein', betrag: null, url: '/api/einkauf/pdf-vorschau/48', html: '<p>Geprüft</p><script>alert(1)</script>' }} />);
    const link = screen.getByRole('link', { name: 'Lieferschein' });
    expect(link).toHaveAttribute('href', '/api/einkauf/pdf-vorschau/48');
    expect(screen.getByText('Geprüft')).toBeInTheDocument();
    expect(document.querySelector('script')).toBeNull();
    expect(document.querySelector('img')).toBeNull();
  });

  it('renders unsafe resource URLs as inert text', () => {
    render(<QuellenLink quelle={{ typ: 'DOKUMENT', id: 49, bezeichnung: 'Unsichere Quelle', betrag: null, url: 'javascript:alert(1)', html: null }} />);
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
    expect(screen.getByText('Unsichere Quelle')).toBeInTheDocument();
  });
});
