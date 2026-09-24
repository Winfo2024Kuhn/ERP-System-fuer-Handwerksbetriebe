import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../../components/ui/toast';
import { BelegAuswahl } from './BelegAuswahl';

const vorhandeneDatei = { dateiId: 501, lieferantDokumentId: 42, typ: 'SONSTIG', dateiname: 'Stahlzeugnis.pdf', verfuegbar: true };

beforeEach(() => {
  global.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input);
    if (path.endsWith('/73/belege') && init?.method === 'GET') return new Response(JSON.stringify([
      { lieferantDokumentId: 41, typ: 'SONSTIG', dateiname: 'Altzeugnis.pdf', verfuegbar: false },
      { lieferantDokumentId: 42, typ: 'SONSTIG', dateiname: 'Stahlzeugnis.pdf', verfuegbar: true },
    ]), { status: 200 });
    if (path.endsWith('/belege/42/datei')) return new Response(JSON.stringify(vorhandeneDatei), { status: 200 });
    if (path.includes('/belege?typ=SONSTIG')) return new Response(JSON.stringify({ ...vorhandeneDatei, dateiId: 502, lieferantDokumentId: 43, dateiname: 'Neu.pdf' }), { status: 201 });
    return new Response('{}', { status: 200 });
  }) as typeof fetch;
});

it('zeigt fehlende historische PDFs an, lässt sie aber nicht zuordnen', async () => {
  render(<ToastProvider><BelegAuswahl bestellungId={73} typ="SONSTIG" value={null} onChange={vi.fn()} /></ToastProvider>);

  expect(await screen.findByText('Altzeugnis.pdf')).toBeInTheDocument();
  expect(screen.getByText('· Datei fehlt')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /Altzeugnis\.pdf zuordnen/ })).toBeDisabled();
  expect(screen.getByRole('button', { name: /Stahlzeugnis\.pdf zuordnen/ })).toBeEnabled();
});

it('registriert eine ausgewählte vorhandene PDF und gibt beide IDs an den Aufrufer zurück', async () => {
  const onChange = vi.fn();
  render(<ToastProvider><BelegAuswahl bestellungId={73} typ="SONSTIG" value={null} onChange={onChange} /></ToastProvider>);

  await userEvent.click(await screen.findByRole('button', { name: /Stahlzeugnis\.pdf zuordnen/ }));

  await waitFor(() => expect(onChange).toHaveBeenCalledWith(vorhandeneDatei));
  expect(global.fetch).toHaveBeenCalledWith('/api/einkauf/bestellungen/73/belege/42/datei', expect.objectContaining({ method: 'POST' }));
});

it('weist neue PDFs als Lieferantenbeleg nach erfolgreichem Upload zu', async () => {
  const onChange = vi.fn();
  render(<ToastProvider><BelegAuswahl bestellungId={73} typ="SONSTIG" value={null} onChange={onChange} /></ToastProvider>);
  const pdf = new File(['%PDF-1.7'], 'Neu.pdf', { type: 'application/pdf' });

  await userEvent.upload(await screen.findByLabelText('PDF hochladen'), pdf);

  await waitFor(() => expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ dateiId: 502, lieferantDokumentId: 43 })));
  expect(global.fetch).toHaveBeenCalledWith('/api/einkauf/bestellungen/73/belege?typ=SONSTIG', expect.objectContaining({ method: 'POST' }));
});

it('weist nicht-PDF-Dateien vor dem Upload zurück', async () => {
  const onChange = vi.fn();
  render(<ToastProvider><BelegAuswahl bestellungId={73} typ="RECHNUNG" value={null} onChange={onChange} /></ToastProvider>);
  const invalid = new File(['not pdf'], 'programm.exe', { type: 'application/octet-stream' });

  fireEvent.change(await screen.findByLabelText('PDF hochladen'), { target: { files: [invalid] } });

  expect(await screen.findByRole('alert')).toHaveTextContent('Bitte wählen Sie eine PDF-Datei aus.');
  expect(global.fetch).not.toHaveBeenCalledWith(expect.stringContaining('/belege?typ='), expect.objectContaining({ method: 'POST' }));
});
