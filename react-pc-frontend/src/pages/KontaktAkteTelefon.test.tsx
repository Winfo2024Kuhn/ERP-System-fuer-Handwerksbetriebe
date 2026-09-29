import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { ToastProvider } from '../components/ui/toast';
import { Kundeneditor } from './Kundeneditor';
import LieferantenEditor from './LieferantenEditor';
import { anruf, antwort, KUNDE_MAX, LIEFERANT_GMBH, STATUS, stubbeFetch } from '../features/telefon/telefonTestdaten';
import { setzeTelefonBerechtigungZurueck } from '../features/telefon/useTelefonBerechtigung';

/**
 * Reiter „Anrufe" und „Weitere Rufnummern" in Kunden- und Lieferantenakte.
 * Nur Dummy-Daten (DSGVO).
 */

const KUNDE = {
    id: 7, kundennummer: 'K-1007', name: 'Max Mustermann', strasse: 'Musterweg 1', plz: '12345', ort: 'Musterstadt',
    telefon: '0931 1234567', mobiltelefon: '', kundenEmails: [], kommunikation: [], projekte: [], anfragen: [], geschaeftsdokumente: [], notizen: [],
};
const LIEFERANT = {
    id: 3, lieferantenname: 'Mustermann GmbH', strasse: 'Musterweg 2', plz: '12345', ort: 'Würzburg', lieferantenTyp: 'Lieferant',
    rollen: [], emails: [], kommunikation: [], dokumente: [], notizen: [], kundenEmails: [],
};

function stubbe(darf: boolean) {
    return stubbeFetch(
        (url) => (url.pathname === '/api/auth/me'
            ? antwort({ id: 1, displayName: 'Max Mustermann', username: 'max', active: true, roles: [], admin: false, requiresInitialSetup: false })
            : undefined),
        (url) => (url.pathname === '/api/telefon/berechtigung' ? antwort({ darfTelefonSehen: darf }) : undefined),
        (url) => (url.pathname === '/api/kunden/7' ? antwort(KUNDE) : undefined),
        (url) => (url.pathname === '/api/kunden' ? antwort({ kunden: [KUNDE], gesamt: 1 }) : undefined),
        (url) => (url.pathname === '/api/lieferanten/3' ? antwort(LIEFERANT) : undefined),
        (url) => (url.pathname === '/api/lieferanten' ? antwort({ lieferanten: [LIEFERANT], gesamt: 1 }) : undefined),
        (url) => (url.pathname === '/api/telefon/kontakt-rufnummern' ? antwort([{ id: 5, nummer: '0931 5555555' }]) : undefined),
        (url) => {
            if (url.pathname !== '/api/telefon/anrufe') return undefined;
            const kontakt = url.searchParams.get('kundeId') ? KUNDE_MAX : LIEFERANT_GMBH;
            return antwort({ content: [anruf({ id: 1, kontakt }), anruf({ id: 2, kontakt })], totalElements: 2, totalPages: 1 });
        },
        (url) => (url.pathname === '/api/telefon/sprachnachrichten' ? antwort([]) : undefined),
        (url) => (url.pathname === '/api/telefon/status' ? antwort(STATUS) : undefined),
        () => antwort([]),
    );
}

function zeige(element: React.ReactElement, adresse: string) {
    return render(
        <MemoryRouter initialEntries={[adresse]}>
            <AuthProvider><ConfirmProvider><ToastProvider>{element}</ToastProvider></ConfirmProvider></AuthProvider>
        </MemoryRouter>,
    );
}

describe('Kundenakte – Telefon', () => {
    beforeEach(() => setzeTelefonBerechtigungZurueck());
    afterEach(() => vi.unstubAllGlobals());

    it('zeigt mit Recht den Reiter "Anrufe" mit Zähler und die Liste', async () => {
        stubbe(true);
        zeige(<Kundeneditor />, '/kunden?kundeId=7');
        const reiter = await screen.findByRole('button', { name: /^Anrufe\s*2$/ });
        fireEvent.click(reiter);
        expect(await screen.findByRole('list', { name: 'Anrufe und Nachrichten' })).toBeInTheDocument();
        expect(screen.getByText('0931 5555555')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Rufnummer 0931 5555555 entfernen' })).toBeInTheDocument();
    });

    it('zeigt ohne Recht keinen Reiter, aber die weiteren Rufnummern ohne Entfernen', async () => {
        stubbe(false);
        zeige(<Kundeneditor />, '/kunden?kundeId=7');
        expect(await screen.findByText('0931 5555555')).toBeInTheDocument();
        await waitFor(() => expect(screen.getByRole('button', { name: /E-Mails/ })).toBeInTheDocument());
        expect(screen.queryByRole('button', { name: /^Anrufe/ })).toBeNull();
        expect(screen.queryByRole('button', { name: /entfernen/ })).toBeNull();
    });
});

describe('Lieferantenakte – Telefon', () => {
    beforeEach(() => setzeTelefonBerechtigungZurueck());
    afterEach(() => vi.unstubAllGlobals());

    it('öffnet mit Recht über ?tab=anrufe direkt die Anrufe', async () => {
        stubbe(true);
        zeige(<LieferantenEditor />, '/lieferanten?lieferantId=3&tab=anrufe');
        await waitFor(() => expect(screen.getByRole('tab', { selected: true })).toHaveTextContent(/Anrufe/));
        expect(await screen.findByRole('list', { name: 'Anrufe und Nachrichten' })).toBeInTheDocument();
        expect(screen.getByText('0931 5555555')).toBeInTheDocument();
    });

    it('fällt ohne Recht von ?tab=anrufe auf den E-Mail-Verlauf zurück', async () => {
        stubbe(false);
        zeige(<LieferantenEditor />, '/lieferanten?lieferantId=3&tab=anrufe');
        const offen = await screen.findByRole('tab', { selected: true });
        await waitFor(() => expect(offen).toHaveTextContent(/E-Mail-Verlauf/));
        expect(screen.queryByRole('tab', { name: /Anrufe/ })).toBeNull();
    });
});
