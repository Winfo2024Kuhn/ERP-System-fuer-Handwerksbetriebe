import { fireEvent, render, screen } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { AnlagenEditor } from './AnlagenEditor';

it('verlangt für ein Zeichnungsteil mindestens eine freigegebene Zeichnungsanlage', () => {
  const geändert = vi.fn();
  render(<AnlagenEditor anlagen={[{ id: 8, dateiId: 18, bedarfId: 6, revision: 'A', dateiname: 'Plan A.pdf', mimeTyp: 'application/pdf', byteAnzahl: 4096, sha256: 'hash', freigegeben: false, versendet: false, hochgeladenAm: '2026-09-24T08:00:00Z' }]} ausgewählt={[]} geändert={geändert} zeichnungsteil />);
  expect(screen.getByText('Plan A.pdf · Revision A · Nicht freigegeben')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('checkbox', { name: /Plan A\.pdf/ }));
  expect(geändert).toHaveBeenCalledWith([8]);
  expect(screen.getByText('Für ein Zeichnungsteil wird eine freigegebene Zeichnungsanlage benötigt.')).toBeInTheDocument();
});
