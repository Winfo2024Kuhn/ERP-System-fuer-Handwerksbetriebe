import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import ArtikelDetail from './ArtikelDetail';
import { ToastProvider } from '../components/ui/toast';

/**
 * Testdaten mit Dummy-Lieferanten (DSGVO: keine echten Firmennamen).
 * Der Artikel entspricht einem Gelaenderrohr 42,4 x 2 aus Baustahl.
 */
const detailAntwort = {
    artikel: {
        id: 1,
        artikelnummer: 'ST-RR-42.4-2',
        produktname: '42.4x2',
        werkstoffName: 'Baustahl S235JR',
        kategoriePfad: 'Werkstoffe > Hohlprofile > Rundrohr',
        massnorm: 'EN 10219-2',
        werkstoffnorm: 'EN 10025-2',
        abmessung: '42,4 x 2',
        durchmesser: 42.4,
        wandstaerke: 2,
        kgProMeter: 1.9926,
        mantelflaeche: 0.1332,
        standardlaenge: 6000,
        verzinkungsgeeignet: true,
        pulverbeschichtungsgeeignet: true,
        beschichtungshinweis: 'Feuerverzinken und Pulverbeschichten moeglich.',
        profilform: { name: 'RUNDROHR', anzeigename: 'Rundrohr' },
        herstellverfahren: { name: 'GESCHWEISST', anzeigename: 'geschweisst', erklaerung: 'Aus Band gerollt.' },
        fertigungszustand: { name: 'KALTGEFERTIGT', anzeigename: 'kaltgefertigt', erklaerung: 'Bei Raumtemperatur geformt.' },
    },
    lieferanten: [
        {
            preisId: 10, lieferantId: 1, lieferantName: 'Muster Stahlhandel',
            externeArtikelnummer: 'M-1000', preis: 10, preisDatum: '2026-07-01T00:00:00Z',
            quelle: { name: 'MANUELL', anzeigename: 'Von Hand eingetragen' },
            guenstigster: true,
        },
        {
            preisId: 11, lieferantId: 2, lieferantName: 'Beispiel Metall GmbH',
            externeArtikelnummer: 'B-2000', preis: 12.5, preisDatum: '2026-07-15T00:00:00Z',
            quelle: { name: 'ANGEBOT_EMAIL', anzeigename: 'Aus Angebots-Mail uebernommen' },
            guenstigster: false, aufschlagProzent: 25,
        },
    ],
    preisverlauf: [
        { preisId: 11, lieferantId: 2, lieferantName: 'Beispiel Metall GmbH', preis: 12.5, preisDatum: '2026-07-15T00:00:00Z', aktuell: true },
        { preisId: 10, lieferantId: 1, lieferantName: 'Muster Stahlhandel', preis: 10, preisDatum: '2026-07-01T00:00:00Z', aktuell: true },
        { preisId: 9, lieferantId: 2, lieferantName: 'Beispiel Metall GmbH', preis: 11, preisDatum: '2026-05-01T00:00:00Z', aktuell: false },
    ],
};

function renderSeite() {
    return render(
        <ToastProvider>
            <MemoryRouter initialEntries={['/artikel/1']}>
                <Routes>
                    <Route path="/artikel/:id" element={<ArtikelDetail />} />
                </Routes>
            </MemoryRouter>
        </ToastProvider>,
    );
}

/** Platzhalter fuer die Artikelliste, der die aufgerufene Suche sichtbar macht. */
function ListenPlatzhalter() {
    const location = useLocation();
    return <div>Liste{location.search}</div>;
}

/**
 * Rendert die Detailseite so, wie man sie aus der Liste heraus erreicht:
 * Die gefilterte Liste liegt als vorheriger Eintrag in der History.
 */
function renderAusListeHeraus(listenAdresse: string, vonListe = true) {
    return render(
        <ToastProvider>
            <MemoryRouter
                initialEntries={[listenAdresse, { pathname: '/artikel/1', state: vonListe ? { vonListe: true } : null }]}
                initialIndex={1}
            >
                <Routes>
                    <Route path="/artikel" element={<ListenPlatzhalter />} />
                    <Route path="/artikel/:id" element={<ArtikelDetail />} />
                </Routes>
            </MemoryRouter>
        </ToastProvider>,
    );
}

describe('ArtikelDetail', () => {
    beforeEach(() => {
        vi.stubGlobal('fetch', vi.fn(() =>
            Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(detailAntwort) }),
        ));
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('zeigt technische Daten und Normen', async () => {
        renderSeite();
        expect(await screen.findByText('42.4x2')).toBeInTheDocument();
        expect(screen.getByText('EN 10219-2')).toBeInTheDocument();
        expect(screen.getByText('42,4 x 2')).toBeInTheDocument();
        expect(screen.getByText('1,993 kg/m')).toBeInTheDocument();
    });

    it('zeigt das Herstellverfahren im Klartext, nicht nur die Norm', async () => {
        renderSeite();
        // Das ist der Kern: "EN 10219-2" allein sagt einem Handwerker nichts.
        expect(await screen.findByText('geschweisst')).toBeInTheDocument();
        expect(screen.getByText('kaltgefertigt')).toBeInTheDocument();
        expect(screen.getByText('Rundrohr')).toBeInTheDocument();
    });

    it('kennzeichnet die Eignungen mit Text statt nur mit Farbe', async () => {
        renderSeite();
        expect(await screen.findByText('Feuerverzinken')).toBeInTheDocument();
        expect(screen.getByText('Pulverbeschichten')).toBeInTheDocument();
        // Barrierefreiheit: die Aussage darf nicht allein an der Farbe haengen.
        expect(screen.getAllByText('möglich').length).toBe(2);
    });

    it('listet alle Lieferanten mit deren eigener Artikelnummer', async () => {
        renderSeite();
        // Auf die Lieferantentabelle eingrenzen: die Namen stehen zusaetzlich
        // in der Verlaufstabelle, was hier nicht gemeint ist.
        const tabelle = await screen.findByRole('table', {
            name: /Lieferanten mit Artikelnummer und aktuellem Preis/i,
        });
        expect(within(tabelle).getByText('Muster Stahlhandel')).toBeInTheDocument();
        expect(within(tabelle).getByText('M-1000')).toBeInTheDocument();
        expect(within(tabelle).getByText('Beispiel Metall GmbH')).toBeInTheDocument();
        expect(within(tabelle).getByText('B-2000')).toBeInTheDocument();
    });

    it('markiert den guenstigsten Anbieter und zeigt den Aufschlag der anderen', async () => {
        renderSeite();
        expect(await screen.findByText('günstigster')).toBeInTheDocument();
        expect(screen.getByText('+25 %')).toBeInTheDocument();
    });

    it('stellt den Preisverlauf auch als Tabelle bereit', async () => {
        renderSeite();
        // Das Diagramm allein ist nicht vorlesbar - die Tabelle ist die
        // barrierefreie Entsprechung und enthaelt alle Preisstaende.
        const tabelle = await screen.findByRole('table', {
            name: /Alle Preisstände mit Datum, Lieferant und Herkunft/i,
        });
        const zeilen = within(tabelle).getAllByRole('row');
        expect(zeilen.length).toBe(4); // Kopfzeile plus drei Preisstaende
        expect(within(tabelle).getByText('abgelöst')).toBeInTheDocument();
    });

    it('zeigt eine verstaendliche Meldung, wenn der Artikel fehlt', async () => {
        vi.stubGlobal('fetch', vi.fn(() =>
            Promise.resolve({ ok: false, status: 404, json: () => Promise.resolve({}) }),
        ));
        renderSeite();
        expect(await screen.findByText('Dieser Artikel wurde nicht gefunden.')).toBeInTheDocument();
    });

    it('meldet einen Ladefehler, statt eine leere Seite zu zeigen', async () => {
        vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new Error('Netzwerk'))));
        renderSeite();
        await waitFor(() => {
            expect(screen.getByText('Die Artikeldaten konnten nicht geladen werden.')).toBeInTheDocument();
        });
    });

    it('fuehrt "Zurück" auf die Liste mit der urspruenglichen Suche', async () => {
        const user = userEvent.setup();
        renderAusListeHeraus('/artikel?q=rohr&werkstoff=S235JR&page=2');

        await user.click(await screen.findByRole('button', { name: 'Zurück' }));

        // Der Kern: Die Suche darf nicht verloren gehen, sonst muss der Anwender
        // alle Filter erneut setzen, nur weil er einen Artikel angesehen hat.
        expect(await screen.findByText('Liste?q=rohr&werkstoff=S235JR&page=2')).toBeInTheDocument();
    });

    it('fuehrt "Zurück" auch aus der Fehleranzeige auf die urspruengliche Suche', async () => {
        vi.stubGlobal('fetch', vi.fn(() =>
            Promise.resolve({ ok: false, status: 404, json: () => Promise.resolve({}) }),
        ));
        const user = userEvent.setup();
        renderAusListeHeraus('/artikel?q=rohr');

        await user.click(await screen.findByRole('button', { name: /Zurück zur Artikelliste/i }));

        expect(await screen.findByText('Liste?q=rohr')).toBeInTheDocument();
    });

    it('landet ohne Vorgeschichte auf der vollstaendigen Liste', async () => {
        const user = userEvent.setup();
        // Direkter Aufruf per Link oder Lesezeichen: Es gibt keine Suche, zu der
        // man zurueckkehren koennte.
        renderAusListeHeraus('/irgendwo', false);

        await user.click(await screen.findByRole('button', { name: 'Zurück' }));

        expect(await screen.findByText('Liste')).toBeInTheDocument();
    });
});
