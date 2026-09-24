import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { EinkaufNavigation } from './EinkaufNavigation';

describe('EinkaufNavigation', () => {
  it('markiert auf einer Projektbedarfsliste ausschließlich den Bedarf als aktiv', () => {
    render(<MemoryRouter initialEntries={['/bestellungen/bedarf/projekt/19']}><EinkaufNavigation active="bedarf" /></MemoryRouter>);
    expect(screen.getByRole('link', { name: 'Bedarf' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Bestellungen' })).not.toHaveAttribute('aria-current');
    expect(screen.getByRole('link', { name: 'Bestellungen' })).not.toHaveClass('bg-rose-50');
  });
  it('exposes the main purchasing steps and marks the current step', () => {
    render(<MemoryRouter initialEntries={['/einkauf/anfragen']}><EinkaufNavigation active="anfragen" /></MemoryRouter>);
    expect(screen.getByRole('navigation', { name: 'Einkauf' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Bedarf' })).toHaveAttribute('href', '/bestellungen/bedarf');
    expect(screen.getByRole('link', { name: 'Anfragen' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Bestellungen' })).toHaveAttribute('href', '/bestellungen');
    expect(screen.getByRole('link', { name: 'Lieferungen und Unterlagen' })).toHaveAttribute('href', '/einkauf/lieferungen');
  });
});
