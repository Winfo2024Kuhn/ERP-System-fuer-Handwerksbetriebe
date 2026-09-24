import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { PositionsEditor } from './PositionsEditor';
import type { PositionDraft } from '../positionDrafts';
import type { EinkaufAnlage } from '../types';
const anlagen: EinkaufAnlage[] = [{ id: 41, dateiId: 4, bedarfId: 1, revision: 'B', dateiname: 'Traeger.pdf', mimeTyp: 'application/pdf', byteAnzahl: 100, sha256: 'dummy', freigegeben: true, versendet: false, hochgeladenAm: '2026-09-24T00:00:00Z' }];

const init: PositionDraft = { art: 'ZEICHNUNGSTEIL', artikelId: null, interneReferenz: 'ZT-1', zeichnungsnummer: 'W1', zeichnungsrevision: 'A', bezeichnung: 'Träger', werkstoff: 'S235JR', abmessung: 'IPE 120', menge: '0', einheit: 'METER', stueckzahl: '0', einzelLaengeMm: '0', kgJeMeter: '', faktorQuelle: '', schnittForm: 'GERADE', winkelLinks: '', winkelRechts: '', bearbeitung: '', oberflaeche: '', dokumente: [{ art: 'ZEUGNIS_3_1', grundlage: 'EN 10204', grundlageVersion: '2025', fachlichBestaetigt: true }], anlageVersionIds: [41] };

describe('PositionsEditor', () => {
  it('macht den Zeichnungsteil aus einer Artikelposition erreichbar', () => {
    const onChange = vi.fn();
    render(<PositionsEditor value={{ ...init, art: 'ARTIKEL', artikelId: 8 }} onChange={onChange} />);
    fireEvent.click(screen.getByRole('combobox', { name: 'Positionsart' }));
    fireEvent.click(screen.getByRole('option', { name: 'Zeichnungsteil' }));
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ art: 'ZEICHNUNGSTEIL', artikelId: null }));
  });

  it('clears a zero quantity on focus and keeps the two cut angles independently editable', () => {
    const onChange = vi.fn();
    render(<PositionsEditor value={init} onChange={onChange} />);
    fireEvent.focus(screen.getByLabelText('Menge *'));
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ menge: '' }));
    fireEvent.change(screen.getByLabelText('Linker Winkel'), { target: { value: '45' } });
    expect(onChange).toHaveBeenLastCalledWith(expect.objectContaining({ winkelLinks: '45', winkelRechts: '' }));
    expect(screen.getByLabelText('Rechter Winkel')).toBeInTheDocument();
  });

  it('shows multiple document kinds and the linked revisioned attachment', () => {
    render(<PositionsEditor value={{ ...init, dokumente: [
      ...init.dokumente, { art: 'CE_NACHWEIS', grundlage: 'EN 1090', grundlageVersion: '2', fachlichBestaetigt: false },
    ] }} onChange={vi.fn()} anlagen={anlagen} />);
    expect(screen.getAllByText('Zeugnis 3.1').length).toBeGreaterThan(0);
    expect(screen.getByText('CE-Nachweis')).toBeInTheDocument();
    expect(screen.getByText('Traeger.pdf · Revision B · Freigegeben')).toBeInTheDocument();
  });

  it('does not expose editing controls in read-only mode', () => {
    render(<PositionsEditor value={init} onChange={vi.fn()} readOnly />);
    expect(screen.getByLabelText('Menge *')).toHaveAttribute('readOnly');
    expect(screen.queryByRole('button', { name: 'Artikel auswählen' })).not.toBeInTheDocument();
  });

  it('shows why an incomplete document requirement was not added', () => {
    render(<PositionsEditor value={init} onChange={vi.fn()} />);
    fireEvent.click(screen.getByRole('button', { name: 'Hinzufügen' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Grundlage und Grundlagenversion sind Pflichtangaben.');
  });
});
