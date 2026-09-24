import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { ToastProvider } from '../../../components/ui/toast';
import { BedarfDialog } from './BedarfDialog';

it('prüft Menge und Stammdaten, bevor ein Bedarf gespeichert wird', async () => {
  const saved = vi.fn();
  global.fetch = vi.fn(async (input: RequestInfo | URL) => String(input).includes('/api/projekte/simple')
    ? ({ ok: true, json: async () => [] } as Response)
    : ({ ok: true, status: 201, json: async () => ({ id: 44 }) } as Response));
  render(<ToastProvider><BedarfDialog open onClose={vi.fn()} onSaved={saved} /></ToastProvider>);

  fireEvent.change(screen.getByLabelText('Menge'), { target: { value: '0' } });
  fireEvent.click(screen.getByRole('button', { name: 'Bedarf speichern' }));
  expect(global.fetch).not.toHaveBeenCalled();
  expect(await screen.findByRole('alert')).toHaveTextContent('Bitte eine Menge größer als 0 eingeben.');

  fireEvent.change(screen.getByLabelText('Menge'), { target: { value: '10' } });
  fireEvent.change(screen.getByLabelText('Bezeichnung'), { target: { value: 'Stahlprofil' } });
  fireEvent.click(screen.getByRole('button', { name: 'Projekt auswählen' }));
  await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument());
});

it('lässt ein Zeichnungsteil ohne Zeichnungsnummer und freigegebene Anlage nicht speichern', async () => {
  global.fetch = vi.fn(async () => ({ ok: true, json: async () => [] }) as Response);
  render(<ToastProvider><BedarfDialog open onClose={vi.fn()} onSaved={vi.fn()} /></ToastProvider>);
  fireEvent.click(screen.getByRole('combobox', { name: 'Positionsart' }));
  fireEvent.click(screen.getByRole('option', { name: 'Zeichnungsteil' }));
  fireEvent.change(screen.getByLabelText('Bezeichnung'), { target: { value: 'Träger' } });
  fireEvent.change(screen.getByLabelText('Menge'), { target: { value: '2' } });
  fireEvent.change(screen.getByLabelText('Lagerzweck'), { target: { value: 'Werkstatt' } });
  fireEvent.click(screen.getByRole('button', { name: 'Bedarf speichern' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Zeichnungsnummer und mindestens eine ausgewählte, freigegebene Zeichnungsanlage');
  expect(global.fetch).not.toHaveBeenCalled();
});
