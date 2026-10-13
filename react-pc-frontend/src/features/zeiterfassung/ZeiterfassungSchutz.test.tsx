import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { antwort, stubbeFetch } from '../telefon/telefonTestdaten';
import { ZeiterfassungSchutz } from './ZeiterfassungSchutz';
import { setzeZeiterfassungSichtbarkeitZurueck } from './useZeiterfassungSichtbarkeit';
import { ZEITERFASSUNG_PFADE } from '../../components/layout/navigation';

function stubbe(darf: boolean | 'haengt') {
    stubbeFetch((url) => {
        if (url.pathname !== '/api/zeitverwaltung/sichtbarkeit') return undefined;
        return darf === 'haengt' ? new Promise<Response>(() => undefined) as unknown as Response : antwort({ darfZeiterfassungSehen: darf });
    });
}

function zeige(pfad: string) {
    return render(
        <MemoryRouter initialEntries={[pfad]}>
            <ZeiterfassungSchutz><p>Seiteninhalt</p></ZeiterfassungSchutz>
        </MemoryRouter>,
    );
}

describe('ZeiterfassungSchutz', () => {
    beforeEach(() => setzeZeiterfassungSichtbarkeitZurueck());
    afterEach(() => vi.unstubAllGlobals());

    it('zeigt mit Recht die Seite', async () => {
        stubbe(true);
        zeige('/zeitbuchungen');
        expect(await screen.findByText('Seiteninhalt')).toBeInTheDocument();
        expect(screen.queryByText(/ausgeblendet/)).toBeNull();
    });

    it('zeigt ohne Recht einen freundlichen Hinweis statt der Seite', async () => {
        stubbe(false);
        zeige('/urlaubsantraege');
        expect(await screen.findByText('Die Zeiterfassung ist für deinen Zugang ausgeblendet.')).toBeInTheDocument();
        expect(screen.getByText('Frag im Büro nach, wenn du sie brauchst.')).toBeInTheDocument();
        expect(screen.queryByText('Seiteninhalt')).toBeNull();
        expect(screen.queryByRole('alert')).toBeNull();
    });

    it('zeigt beim Prüfen weder Seite noch Hinweis', async () => {
        stubbe('haengt');
        zeige('/monatsabschluss');
        expect(await screen.findByRole('status')).toHaveTextContent('Zugang wird geprüft');
        expect(screen.queryByText('Seiteninhalt')).toBeNull();
        expect(screen.queryByText(/ausgeblendet/)).toBeNull();
    });

    it('schützt jede Seite des Menü-Bereichs', async () => {
        stubbe(false);
        const { unmount } = zeige('/zeitkonten');
        await screen.findByText(/ausgeblendet/);
        unmount();
        for (const pfad of ZEITERFASSUNG_PFADE) {
            const { unmount: weg } = zeige(pfad);
            expect(await screen.findByText(/ausgeblendet/)).toBeInTheDocument();
            expect(screen.queryByText('Seiteninhalt')).toBeNull();
            weg();
        }
    });

    it('lässt andere Seiten ohne Recht und ohne Wartezeit durch', async () => {
        stubbe(false);
        zeige('/projekte');
        expect(screen.getByText('Seiteninhalt')).toBeInTheDocument();
        await waitFor(() => expect(screen.queryByRole('status')).toBeNull());
    });
});
