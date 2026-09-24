import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { expect, it, vi } from 'vitest';
import { ToastProvider } from '../components/ui/toast';
import EinkaufFaelligkeiten from './EinkaufFaelligkeiten';

it('zeigt nur offene Fälligkeiten und bereitet eine Nachfrage vor ohne sie zu senden', async () => {
  const paths: string[] = [];
  global.fetch = vi.fn(async (eingabe: RequestInfo | URL, optionen?: RequestInit) => {
    const pfad = String(eingabe); paths.push(`${optionen?.method ?? 'GET'} ${pfad}`);
    if (pfad === '/api/auth/me') return { ok: true, json: async () => ({ id: 9, roles: ['ADMIN'] }) } as Response;
    if (pfad.includes('/api/einkauf/faelligkeiten?')) return { ok: true, json: async () => ({ content: [
      { typ: 'ANFRAGE_ANTWORTFRIST', vorgangId: 81, nummer: 'PA-81', beteiligungId: 83, frist: null, zustaendigId: 9, hinweis: 'Termin klären' },
      { typ: 'ZEUGNIS', vorgangId: 22, nummer: 'B-22 / Zeugnis 3.1', beteiligungId: null, frist: '2026-09-23', zustaendigId: 9, hinweis: 'Charge fehlt' },
    ], totalPages: 1, totalElements: 2, number: 0 }) } as Response;
    if (pfad.endsWith('/nachfrage')) return { ok: true, json: async () => ({ typ: 'ANFRAGE_ANTWORTFRIST', vorgangId: 81, beteiligungId: 83, vorlageId: 3, vorlageVersion: 2, empfaenger: 'lieferant@example.test', subject: 'Rückmeldung zu PA-81', htmlBody: '<p>Bitte antworten.</p>', fehlendeNachweise: [] }) } as Response;
    return { ok: true, json: async () => [] } as Response;
  });
  render(<ToastProvider><BrowserRouter><EinkaufFaelligkeiten /></BrowserRouter></ToastProvider>);
  await screen.findByText('Termin klären');
  expect(screen.getAllByText(/B-22 \/ Zeugnis 3\.1/)).toHaveLength(2);
  expect(screen.queryByText('Erledigt')).not.toBeInTheDocument();
  fireEvent.click(screen.getAllByRole('button', { name: 'Nachfrage vorbereiten' })[0]);
  await screen.findByRole('dialog');
  await waitFor(() => expect(paths.some(pfad => pfad.includes('/nachfrage'))).toBe(true));
  expect(paths.some(pfad => pfad.includes('/senden') || pfad.endsWith('/antworten'))).toBe(false);
});
