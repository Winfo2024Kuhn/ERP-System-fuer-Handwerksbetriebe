import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { ToastProvider } from '../../../components/ui/toast';
import { DirektbestellungDialog } from './DirektbestellungDialog';

it('legt eine Direktbestellung mit geprüftem Preisnachweis an, ohne Angebots- oder PA-Nummer', async () => {
  let order: Record<string, unknown> | null = null;
  global.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.includes('/api/einkauf/bedarf?')) return { ok: true, json: async () => ({ content: [{ id: 6, version: 2, position: { art: 'ARTIKEL', artikelId: 9, interneReferenz: 'MAT-9', bezeichnung: 'Profil', basis: { menge: 2, einheit: 'METER' } }, mengen: { disponierbar: 2 }, liefergruppe: {} }] }) } as Response;
    if (url.includes('/api/lieferanten?')) return { ok: true, json: async () => ({ lieferanten: [{ id: 8, lieferantenname: 'Musterstahl' }] }) } as Response;
    if (url === '/api/lieferanten/8') return { ok: true, json: async () => ({ id: 8, eigeneKundennummer: 'K-8' }) } as Response;
    if (url === '/api/lieferanten/8/einkauf-kontakte') return { ok: true, json: async () => [{ id: 4, version: 1, name: 'Einkauf', email: 'einkauf@example.test', standardBestellung: true, aktiv: true }] } as Response;
    order = JSON.parse(String(init?.body)); return { ok: true, status: 201, json: async () => ({ id: 22 }) } as Response;
  });
  render(<ToastProvider><DirektbestellungDialog onClose={vi.fn()} onCreated={vi.fn()} /></ToastProvider>);
  fireEvent.click(await screen.findByLabelText('Bedarf MAT-9 auswählen'));
  fireEvent.click(screen.getByRole('button', { name: 'Lieferant wählen' }));
  fireEvent.click(await screen.findByText('Musterstahl'));
  fireEvent.change(await screen.findByLabelText('Preis MAT-9'), { target: { value: '5,50' } });
  fireEvent.click(screen.getByLabelText('Preis bestätigt am MAT-9'));
  fireEvent.click(within(screen.getByRole('dialog', { name: 'Preis bestätigt am MAT-9 auswählen' })).getByRole('button', { name: 'Heute' }));
  fireEvent.change(screen.getByLabelText('Preisquelle MAT-9'), { target: { value: 'Angebot ANG-8, PDF' } });
  fireEvent.click(screen.getByRole('button', { name: 'Direktbestellung als Entwurf anlegen' }));
  await waitFor(() => expect(order).not.toBeNull());
  expect(order).toMatchObject({ lieferantId: 8, paket: [{ bedarfId: 6, version: 2, menge: 2 }], preise: [{ bedarfId: 6, preis: 5.5, einheit: 'METER', basisMenge: 1, preisHistorieId: null, bestaetigtAm: new Date().toLocaleDateString('sv-SE'), bestaetigungsbeleg: 'Angebot ANG-8, PDF' }] });
});

it('übernimmt ausgewählte Bedarfsanteile und deren Teilmengen in den Direktbestelldialog', async () => {
  global.fetch = vi.fn(async (eingabe: RequestInfo | URL) => String(eingabe) === '/api/einkauf/bedarf/6'
    ? ({ ok: true, json: async () => ({ id: 6, version: 2, position: { art: 'ARTIKEL', artikelId: 9, interneReferenz: 'MAT-9', bezeichnung: 'Profil', basis: { menge: 8, einheit: 'METER' } }, mengen: { disponierbar: 8 }, liefergruppe: {} }) } as Response)
    : ({ ok: true, json: async () => [] } as Response));
  render(<ToastProvider><DirektbestellungDialog initialeTeilmengen={[{ bedarfId: 6, menge: 1.25 }]} onClose={vi.fn()} onCreated={vi.fn()} /></ToastProvider>);
  expect(await screen.findByLabelText('Bedarf MAT-9 auswählen')).toBeChecked();
  expect(screen.getByLabelText('Menge MAT-9')).toHaveValue('1,25');
});

function freieBedarfeMitLieferant(failFirst = false, einheit = 'METER') {
  const requests: Record<string, unknown>[] = [];
  global.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url === '/api/einkauf/bedarf/206') return { ok: true, json: async () => ({ id: 206, version: 3, position: { art: 'FREITEXT', artikelId: null, interneReferenz: 'FREI-206', bezeichnung: 'Sondermaterial', basis: { menge: 8, einheit } }, mengen: { disponierbar: 8 } }) } as Response;
    if (url.includes('/api/lieferanten?')) return { ok: true, json: async () => ({ lieferanten: [{ id: 8, lieferantenname: 'Musterstahl' }] }) } as Response;
    if (url === '/api/lieferanten/8') return { ok: true, json: async () => ({ id: 8 }) } as Response;
    if (url === '/api/lieferanten/8/einkauf-kontakte') return { ok: true, json: async () => [{ id: 4, name: 'Einkauf', email: 'einkauf@example.test', standardBestellung: true, aktiv: true }] } as Response;
    if (url.endsWith('/direkt')) {
      requests.push(JSON.parse(String(init?.body)));
      if (failFirst && requests.length === 1) throw new TypeError('Verbindung unterbrochen');
      return { ok: true, json: async () => ({ id: 22 }) } as Response;
    }
    throw new Error(`Unerwartete Anfrage ${url}`);
  });
  return requests;
}

async function vorbereiten() {
  render(<ToastProvider><DirektbestellungDialog initialeTeilmengen={[{ bedarfId: 206, menge: 1.25 }]} onClose={vi.fn()} onCreated={vi.fn()} /></ToastProvider>);
  expect(await screen.findByLabelText('Bedarf FREI-206 auswählen')).toBeChecked();
  fireEvent.click(screen.getByRole('button', { name: 'Lieferant wählen' }));
  fireEvent.click(await screen.findByText('Musterstahl'));
  await screen.findByText('Kontakt: Einkauf');
}

it('lädt vorausgewählte IDs einzeln und bestellt freie Positionen mit offenem Preis', async () => {
  const requests = freieBedarfeMitLieferant();
  await vorbereiten();
  expect(screen.getByLabelText('Menge FREI-206')).toHaveValue('1,25');
  expect(screen.getByText('Preis offen')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Direktbestellung als Entwurf anlegen' }));
  await waitFor(() => expect(requests).toHaveLength(1));
  expect(requests[0]).toMatchObject({ paket: [{ bedarfId: 206, version: 3, menge: 1.25 }], preise: [] });
});

it.each(['0', '', '1,', '9', '1.2', '1,1234567'])('weist ungültige oder überhöhte Mengen %s zurück', async menge => {
  const requests = freieBedarfeMitLieferant();
  await vorbereiten();
  fireEvent.change(screen.getByLabelText('Menge FREI-206'), { target: { value: menge } });
  fireEvent.click(screen.getByRole('button', { name: 'Direktbestellung als Entwurf anlegen' }));
  await screen.findByRole('alert');
  expect(requests).toHaveLength(0);
});

it('verwendet nach unklarem Verbindungsfehler denselben Idempotenzschlüssel', async () => {
  const requests = freieBedarfeMitLieferant(true);
  await vorbereiten();
  fireEvent.click(screen.getByRole('button', { name: 'Direktbestellung als Entwurf anlegen' }));
  await screen.findByText('Verbindung unterbrochen');
  fireEvent.click(screen.getByRole('button', { name: 'Direktbestellung als Entwurf anlegen' }));
  await waitFor(() => expect(requests).toHaveLength(2));
  expect(requests[0].idempotenzKey).toBe(requests[1].idempotenzKey);
});

it('lädt ohne Vorauswahl auch spätere Bedarfsseiten und Zeichnungsteile', async () => {
  global.fetch = vi.fn(async input => {
    const page = new URL(String(input), 'http://localhost').searchParams.get('page');
    return { ok: true, json: async () => ({ totalPages: 2, content: page === '0' ? [] : [{ id: 300, version: 1, position: { art: 'ZEICHNUNGSTEIL', artikelId: null, interneReferenz: 'ZEICH-300', bezeichnung: 'Lasche', basis: { menge: 2, einheit: 'STUECK' } }, mengen: { disponierbar: 2 } }] }) } as Response;
  });
  render(<ToastProvider><DirektbestellungDialog onClose={vi.fn()} onCreated={vi.fn()} /></ToastProvider>);
  expect(await screen.findByLabelText('Bedarf ZEICH-300 auswählen')).toBeInTheDocument();
  expect(global.fetch).toHaveBeenCalledTimes(2);
});

it('überspringt einen fehlgeschlagenen vorausgewählten Bedarf nicht stillschweigend', async () => {
  global.fetch = vi.fn(async () => ({ ok: false, status: 404, json: async () => ({ message: 'Bedarf nicht mehr verfügbar.' }) }) as Response);
  render(<ToastProvider><DirektbestellungDialog initialeTeilmengen={[{ bedarfId: 206, menge: 1 }]} onClose={vi.fn()} onCreated={vi.fn()} /></ToastProvider>);
  await screen.findAllByText('Bedarf nicht mehr verfügbar.');
  expect(screen.getByRole('button', { name: 'Direktbestellung als Entwurf anlegen' })).toBeDisabled();
});

it('verlangt bei eingetragenem Preis weiterhin einen echten Bestätigungsnachweis', async () => {
  const requests = freieBedarfeMitLieferant();
  await vorbereiten();
  fireEvent.change(screen.getByLabelText('Preis FREI-206'), { target: { value: '5,50' } });
  fireEvent.click(screen.getByRole('button', { name: 'Direktbestellung als Entwurf anlegen' }));
  await screen.findByText('Bitte ein gültiges, nicht zukünftiges Bestätigungsdatum angeben.');
  expect(requests).toHaveLength(0);
});

it('weist gebrochene Stückzahlen vor der Bestellung zurück', async () => {
  const requests = freieBedarfeMitLieferant(false, 'STUECK');
  await vorbereiten();
  fireEvent.click(screen.getByRole('button', { name: 'Direktbestellung als Entwurf anlegen' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('ganze Zahl');
  expect(requests).toHaveLength(0);
});
