import { render, screen } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../../../components/ui/toast';
import { Angebotsvergleich } from './Angebotsvergleich';

beforeEach(() => {
  global.fetch = vi.fn(async (input: RequestInfo | URL) => {
    const path = String(input);
    if (path.endsWith('/anfragen/51/angebote')) return { ok: true, json: async () => [
      { lieferantId: 1, lieferantenname: 'Stahl Nord', angebot: { id: 91, beteiligungId: 11, status: 'ANGEBOTEN', versionen: [{ id: 101, angebotId: 91, nummer: 1, version: 0, anfrageRevisionId: 10, status: 'GEPRUEFT', angebotsnummer: 'A', datum: '2026-09-20', gueltigBis: '2026-10-20', waehrung: 'EUR', positionen: [{ anfragePositionId: 31, angeboten: { menge: 2, einheit: 'STUECK' }, mindestmenge: null, verpackungseinheit: null, abweichungen: [], zeugnisse: [], kosten: [] }], kosten: [], zahlungsbedingungen: null, skontoProzent: null, skontoTage: null }] } },
      { lieferantId: 2, lieferantenname: 'Stahl Süd', angebot: { id: 92, beteiligungId: 12, status: 'ANGEBOTEN', versionen: [{ id: 102, angebotId: 92, nummer: 1, version: 0, anfrageRevisionId: 10, status: 'GEPRUEFT', angebotsnummer: 'B', datum: '2026-09-20', gueltigBis: '2026-10-20', waehrung: 'EUR', positionen: [{ anfragePositionId: 31, angeboten: { menge: 2, einheit: 'STUECK' }, mindestmenge: null, verpackungseinheit: null, abweichungen: ['Legierung abweichend'], zeugnisse: [], kosten: [] }], kosten: [], zahlungsbedingungen: null, skontoProzent: 2, skontoTage: 10 }] } },
      { lieferantId: 3, lieferantenname: 'Stahl West', angebot: { id: 93, beteiligungId: 13, status: 'ANGEBOTEN', versionen: [{ id: 103, angebotId: 93, nummer: 1, version: 0, anfrageRevisionId: 10, status: 'ERFASST', angebotsnummer: 'C', datum: '2026-09-20', gueltigBis: '2026-10-20', waehrung: 'EUR', positionen: [], kosten: [], zahlungsbedingungen: null, skontoProzent: null, skontoTage: null }] } },
    ] } as Response;
    if (path.endsWith('/anfragen/51/vergleich')) return { ok: true, json: async () => ({ anfrageId: 51, stichtag: '2026-09-24', bestesAngebotVersionId: 101, angebote: [
      { angebotVersionId: 101, nettoGesamt: 1060, vollstaendig: true, technischGeeignet: true, gueltig: true, hindernisse: [], rechnung: [{ key: 'MATERIAL', formel: '530 × 2', basis: 530, ergebnis: 1060, quellenbezug: 'A: netto pro Stück' }] },
      { angebotVersionId: 102, nettoGesamt: 1080, vollstaendig: true, technischGeeignet: true, gueltig: true, hindernisse: ['Legierung abweichend'], rechnung: [] },
      { angebotVersionId: 103, nettoGesamt: null, vollstaendig: false, technischGeeignet: false, gueltig: true, hindernisse: ['Preis fehlt', 'Zeugnis nicht lieferbar'], rechnung: [] },
    ] }) } as Response;
    if (path.endsWith('/anfragen/51')) return { ok: true, json: async () => ({ angezeigteRevisionId: 10, historisch: false, kopf: { id: 51 }, positionen: [{ id: 31, snapshot: { interneReferenz: 'MAT-31', bezeichnung: 'Stahlblech' }, herkuenfte: [] }], lieferanten: [] }) } as Response;
    return { ok: false, status: 404, json: async () => ({ message: 'Not found' }) } as Response;
  });
});

it('vergleicht Vollkosten, zeigt offene Gründe und empfiehlt keine unvollständige Position', async () => {
  render(<MemoryRouter><ToastProvider><Angebotsvergleich anfrageId={51} onBestellung={vi.fn()} /></ToastProvider></MemoryRouter>);
  expect((await screen.findAllByText('1.060,00 €')).length).toBeGreaterThan(0);
  expect(screen.getAllByText('1.080,00 €').length).toBeGreaterThan(0);
  expect(screen.getByText('Preis fehlt')).toBeInTheDocument();
  expect(screen.getAllByText('Legierung abweichend').length).toBeGreaterThan(0);
  expect(screen.getByText(/Skonto.*Hinweis/i)).toBeInTheDocument();
  expect(screen.getByText('MAT-31')).toBeInTheDocument();
  expect(screen.getAllByText('offen').length).toBeGreaterThan(0);
  expect(screen.getAllByRole('button', { name: /Als Bestellung vorbereiten/ }).filter(button => !button.hasAttribute('disabled'))).toHaveLength(1);
});
