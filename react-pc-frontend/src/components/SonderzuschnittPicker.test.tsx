import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { SonderzuschnittPicker } from './SonderzuschnittPicker';
import { ToastProvider } from './ui/toast';
const backend = vi.hoisted(() => ({ connected: false }));
vi.mock('../features/einkauf/originalBedarfApi', () => ({ get nutztEchtesBackend() { return backend.connected; } }));
beforeEach(() => { backend.connected = false; });
afterEach(() => vi.unstubAllGlobals());
function mock() {
  vi.stubGlobal('fetch', vi.fn(async url => new Response(JSON.stringify(String(url).startsWith('/api/schnitt-achsen') ? [{ id: 7, bildUrl: '/achse.png', kategorieId: 4 }] : [{ id: 8, schnittAchseId: 7, bildUrlSchnittbild: '/schnitt.png' }]), { status: 200 })));
}
it('übernimmt Achse, Schnittbild und vorhandene Winkel gemeinsam', async () => {
  mock(); const onSubmit = vi.fn();
  render(<ToastProvider><SonderzuschnittPicker isOpen onClose={vi.fn()} onSubmit={onSubmit} artikelId={17} initial={{ schnittAchseId: 7, schnittbildId: 8, anschnittWinkelLinks: 45 }} /></ToastProvider>);
  await waitFor(() => expect(screen.getByRole('button', { name: 'Übernehmen' })).toBeEnabled());
  fireEvent.click(screen.getByRole('button', { name: 'Übernehmen' }));
  expect(onSubmit).toHaveBeenCalledWith({ schnittAchseId: 7, schnittbildId: 8, schnittAchseBildUrl: '/achse.png', schnittbildBildUrl: '/schnitt.png', anschnittWinkelLinks: 45, anschnittWinkelRechts: 90 });
  expect(fetch).toHaveBeenCalledWith('/api/schnitt-achsen?artikelId=17');
});
it('lässt keine Übernahme ohne ausgewählte Achse und Schnittbild zu', async () => {
  mock(); render(<ToastProvider><SonderzuschnittPicker isOpen onClose={vi.fn()} onSubmit={vi.fn()} kategorieId={4} /></ToastProvider>);
  expect(await screen.findByAltText('Achse 7')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Übernehmen' })).toBeDisabled();
});

it('lädt im verbundenen Betrieb echte Schnittformen ohne Achsenroute und übernimmt Kommawinkel', async () => {
  backend.connected = true;
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify([{ id: 8, form: 'A', bildUrlSchnittbild: '/schnitt.png' }]), { status: 200 })));
  const onSubmit = vi.fn();
  render(<ToastProvider><SonderzuschnittPicker isOpen onClose={vi.fn()} onSubmit={onSubmit} artikelId={17} /></ToastProvider>);
  fireEvent.click(await screen.findByRole('button', { name: /Schnittbild 8/ }));
  fireEvent.change(screen.getByLabelText('Winkel links'), { target: { value: '45,5' } });
  fireEvent.click(screen.getByRole('button', { name: 'Übernehmen' }));
  expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ schnittbildId: 8, schnittAchseId: null, schnittForm: 'A', anschnittWinkelLinks: 45.5, anschnittWinkelRechts: 90 }));
  expect(fetch).toHaveBeenCalledOnce();
  expect(fetch).toHaveBeenCalledWith('/api/schnittbilder?artikelId=17');
  expect(screen.queryByRole('spinbutton')).not.toBeInTheDocument();
});
it('leert Nullwinkel bei Fokus und lehnt unvollständige Winkel ab', async () => {
  backend.connected = true;
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify([{ id: 8, form: 'A', bildUrlSchnittbild: '/schnitt.png' }]), { status: 200 })));
  const onSubmit = vi.fn();
  render(<ToastProvider><SonderzuschnittPicker isOpen onClose={vi.fn()} onSubmit={onSubmit} artikelId={17} initial={{ schnittbildId: 8, anschnittWinkelLinks: 0 }} /></ToastProvider>);
  await waitFor(() => expect(screen.getByRole('button', { name: 'Übernehmen' })).toBeEnabled());
  fireEvent.focus(screen.getByLabelText('Winkel links')); expect(screen.getByLabelText('Winkel links')).toHaveValue('');
  fireEvent.change(screen.getByLabelText('Winkel links'), { target: { value: '45,' } });
  fireEvent.click(screen.getByRole('button', { name: 'Übernehmen' }));
  expect(onSubmit).not.toHaveBeenCalled();
  expect(await screen.findByText(/vollständige Zahl mit Dezimalkomma/)).toBeInTheDocument();
});
