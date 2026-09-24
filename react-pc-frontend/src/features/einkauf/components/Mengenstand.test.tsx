import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Mengenstand } from './Mengenstand';

describe('Mengenstand', () => {
  it('formats quantities with German decimals and distinguishes missing values from zero', () => {
    render(<Mengenstand stand={{ bedarf: 12.5, lagergedeckt: 0, angefragt: 1.25, reserviert: 2, bestellt: 3, geliefert: 1, storniert: 0, ungedeckt: 9.5, disponierbar: null }} />);
    expect(screen.getByText('12,5')).toBeInTheDocument();
    expect(screen.getByText('1,25')).toBeInTheDocument();
    expect(screen.getAllByText('0')).toHaveLength(2);
    expect(screen.getByText('Nicht angegeben')).toBeInTheDocument();
  });
});
