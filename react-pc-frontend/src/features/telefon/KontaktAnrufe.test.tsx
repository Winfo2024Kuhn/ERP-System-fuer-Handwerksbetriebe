import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';
import { ToastProvider } from '../../components/ui/toast';
import { KontaktAnrufeTab } from './KontaktAnrufeTab';
import { anruf, antwort, aufrufe, KUNDE_MAX, nachricht, STATUS, stubbeFetch } from './telefonTestdaten';
import { WeitereRufnummern } from './WeitereRufnummern';

function zeige(element: React.ReactElement) {
    return render(
        <MemoryRouter>
            <ConfirmProvider><ToastProvider>{element}</ToastProvider></ConfirmProvider>
        </MemoryRouter>,
    );
}

describe('KontaktAnrufeTab', () => {
    beforeEach(() => {
        Object.defineProperty(HTMLMediaElement.prototype, 'play', { configurable: true, value: vi.fn().mockResolvedValue(undefined) });
        Object.defineProperty(HTMLMediaElement.prototype, 'pause', { configurable: true, value: vi.fn() });
    });
    afterEach(() => vi.unstubAllGlobals());

    it('listet Anrufe und Nachrichten eines Kunden chronologisch und spielt Nachrichten ab', async () => {
        const fetchMock = stubbeFetch(
            (url) => (url.pathname === '/api/telefon/anrufe'
                ? antwort({ content: [
                    anruf({ id: 1, zeitpunkt: '2026-09-28T09:00:00', kontakt: KUNDE_MAX }),
                    anruf({ id: 2, zeitpunkt: '2026-09-29T11:55:00', art: 'ANRUFBEANTWORTER', anrufbeantworter: 1, sprachnachrichtId: 11, kontakt: KUNDE_MAX }),
                ], totalElements: 2, totalPages: 1 })
                : undefined),
            (url) => (url.pathname === '/api/telefon/sprachnachrichten'
                ? antwort([nachricht({ id: 11, anrufbeantworter: 1 }), nachricht({ id: 12, zeitpunkt: '2026-09-29T15:00:00', neu: false })])
                : undefined),
            (url) => (url.pathname === '/api/telefon/status' ? antwort(STATUS) : undefined),
            (url, init) => (url.pathname === '/api/telefon/sprachnachrichten/11' && init?.method === 'PATCH'
                ? antwort(nachricht({ id: 11, anrufbeantworter: 1, neu: false, abgehoertAm: '2026-09-29T12:04:00', abgehoertVon: 'Max Mustermann' }))
                : undefined),
        );
        zeige(<KontaktAnrufeTab typ="KUNDE" kontaktId={7} />);
        const liste = await screen.findByRole('list', { name: 'Anrufe und Nachrichten' });
        expect(new URL(String(aufrufe(fetchMock, '/api/telefon/anrufe')[0][0]), 'http://localhost').searchParams.get('kundeId')).toBe('7');

        // Neueste zuerst: Einzelnachricht (15:00), dann Anruf mit Nachricht (11:55), dann Anruf vom Vortag.
        const eintraege = liste.querySelectorAll(':scope > li');
        expect(eintraege).toHaveLength(3);
        expect(eintraege[0].id).toBe('nachricht-12');
        expect(eintraege[1].querySelector('#nachricht-11')).not.toBeNull();
        expect(screen.getAllByText(/AB Nacht/).length).toBeGreaterThan(0);

        fireEvent.click(screen.getAllByRole('button', { name: /^Abspielen:/ })[1]);
        await waitFor(() => expect(aufrufe(fetchMock, '/api/telefon/sprachnachrichten/11', 'PATCH')).toHaveLength(1));
        expect(await screen.findByText(/^Abgehört von Max Mustermann/)).toBeInTheDocument();
    });

    it('fragt bei Lieferanten mit lieferantId und zeigt den leeren Zustand', async () => {
        const fetchMock = stubbeFetch(
            (url) => (url.pathname === '/api/telefon/anrufe' ? antwort({ content: [], totalElements: 0, totalPages: 0 }) : undefined),
            (url) => (url.pathname === '/api/telefon/sprachnachrichten' ? antwort([]) : undefined),
            (url) => (url.pathname === '/api/telefon/status' ? antwort(STATUS) : undefined),
        );
        zeige(<KontaktAnrufeTab typ="LIEFERANT" kontaktId={3} />);
        expect(await screen.findByText('Noch keine Anrufe von diesem Kontakt.')).toBeInTheDocument();
        expect(new URL(String(aufrufe(fetchMock, '/api/telefon/sprachnachrichten')[0][0]), 'http://localhost').searchParams.get('lieferantId')).toBe('3');
    });
});

describe('WeitereRufnummern', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('zeigt nichts, wenn keine Nummern gemerkt sind', async () => {
        const fetchMock = stubbeFetch(() => antwort([]));
        const { container } = zeige(<WeitereRufnummern typ="KUNDE" kontaktId={7} darfLoeschen />);
        await waitFor(() => expect(fetchMock).toHaveBeenCalled());
        expect(container.textContent).toBe('');
    });

    it('zeigt gemerkte Nummern für alle, aber Entfernen nur mit Recht', async () => {
        stubbeFetch(() => antwort([{ id: 5, nummer: '0931 1234567' }]));
        zeige(<WeitereRufnummern typ="KUNDE" kontaktId={7} darfLoeschen={false} />);
        expect(await screen.findByText('0931 1234567')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /entfernen/ })).toBeNull();
    });

    it('entfernt eine Nummer nach Rückfrage', async () => {
        const fetchMock = stubbeFetch(
            (url, init) => (url.pathname === '/api/telefon/kontakt-rufnummern/5' && init?.method === 'DELETE' ? new Response(null, { status: 204 }) : undefined),
            (url) => (url.pathname === '/api/telefon/kontakt-rufnummern' ? antwort([{ id: 5, nummer: '0931 1234567' }]) : undefined),
        );
        zeige(<WeitereRufnummern typ="LIEFERANT" kontaktId={3} darfLoeschen />);
        fireEvent.click(await screen.findByRole('button', { name: 'Rufnummer 0931 1234567 entfernen' }));
        fireEvent.click(await screen.findByRole('button', { name: 'Entfernen' }));
        await waitFor(() => expect(screen.queryByText('0931 1234567')).toBeNull());
        expect(aufrufe(fetchMock, '/api/telefon/kontakt-rufnummern/5', 'DELETE')).toHaveLength(1);
        expect(new URL(String(aufrufe(fetchMock, '/api/telefon/kontakt-rufnummern')[0][0]), 'http://localhost').searchParams.get('lieferantId')).toBe('3');
    });
});
