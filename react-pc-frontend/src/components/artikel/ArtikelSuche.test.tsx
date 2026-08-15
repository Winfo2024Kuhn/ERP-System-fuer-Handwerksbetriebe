import { useEffect } from 'react';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ArtikelSuche } from './ArtikelSuche';
// Eigene Datei, weil ESLint (react-refresh/only-export-components) neben dem
// Komponenten-Export keinen Funktions-Export in ArtikelSuche.tsx duldet.
import { formatCurrency } from './formatCurrency';

// DSGVO: ausschliesslich Dummy-Daten in Tests.
const TREFFER = [
    { id: 7, produktname: 'T-Stahl', abmessung: '40 x 40 x 5', positionsEinheit: 'lfm' },
];

const antwort = (artikel: unknown[]) => ({
    ok: true,
    json: async () => ({ artikel, gesamt: artikel.length, seite: 0, seitenGroesse: 12 }),
});

const filteroptionen = () => ({
    ok: true,
    json: async () => ({ herstellverfahren: [], fertigungszustand: [] }),
});

/** Query-Parameter des zuletzt abgesetzten Listen-Requests. */
function letzterListenRequest(): URLSearchParams {
    const aufrufe = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls
        .map((call: unknown[]) => call[0])
        .filter((url: unknown): url is string => typeof url === 'string' && url.startsWith('/api/artikel?'));
    const letzte = aufrufe[aufrufe.length - 1];
    return new URLSearchParams(letzte.substring(letzte.indexOf('?') + 1));
}

/** Adresse des Routers, damit sich die URL-Spiegelung pruefen laesst. */
let aktuelleAdresse = '';

function AdressSpion() {
    const location = useLocation();
    // Im Effekt statt beim Rendern: Schreiben nach aussen ist ein Seiteneffekt.
    useEffect(() => {
        aktuelleAdresse = location.pathname + location.search;
    });
    return null;
}

function zeigeSuche(element: React.ReactNode) {
    return render(
        <MemoryRouter>
            <AdressSpion />
            {element}
        </MemoryRouter>,
    );
}

describe('ArtikelSuche', () => {
    beforeEach(() => {
        aktuelleAdresse = '';
        vi.stubGlobal('fetch', vi.fn(async (url: string) => {
            if (url.includes('/filteroptionen') || url.includes('/werkstoffe')) return filteroptionen();
            return antwort(TREFFER);
        }));
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        vi.restoreAllMocks();
    });

    it('zeigt die Treffer der Suche', async () => {
        zeigeSuche(<ArtikelSuche />);

        expect(await screen.findByText('T-Stahl')).toBeInTheDocument();
    });

    it('schickt den Suchbegriff als Parameter mit', async () => {
        zeigeSuche(<ArtikelSuche />);

        await userEvent.type(await screen.findByLabelText('Freitext'), 'Rundrohr');

        await waitFor(() => {
            expect(fetch).toHaveBeenCalledWith(expect.stringContaining('q=Rundrohr'));
        });
    });

    // Ohne den Parameter stehen auch Artikel ohne Preis in der Liste. Fuer die
    // Materialkosten im Projekt waeren die unbrauchbar: Ohne Lieferant und
    // Preis laesst sich die Position nicht buchen.
    it('fragt ohne nurMitLieferantenpreis alle Artikel ab', async () => {
        zeigeSuche(<ArtikelSuche />);

        await waitFor(() => expect(fetch).toHaveBeenCalled());
        expect(letzterListenRequest().get('nurMitLieferantenpreis')).toBeNull();
    });

    it('grenzt auf Artikel mit Lieferantenpreis ein, wenn gefordert', async () => {
        zeigeSuche(<ArtikelSuche nurMitLieferantenpreis />);

        await waitFor(() => expect(fetch).toHaveBeenCalled());
        expect(letzterListenRequest().get('nurMitLieferantenpreis')).toBe('true');
    });

    // Die Zeile meldet sich als Schalter ("an- oder abwaehlen"). Ein Schalter,
    // der seinen Zustand verschweigt, laesst Screenreader-Nutzer nach dem
    // Klick im Unklaren, ob die Position nun drin ist oder nicht.
    it('sagt an, ob die Zeile ausgewaehlt ist', async () => {
        zeigeSuche(
            <ArtikelSuche
                onZeilenKlick={() => {}}
                zeilenGedrueckt={(a) => a.id === 7}
            />,
        );

        const zeile = await screen.findByRole('button', { name: /T-Stahl an- oder abwählen/ });
        expect(zeile).toHaveAttribute('aria-pressed', 'true');
    });

    it('meldet eine nicht gewaehlte Zeile als nicht gedrueckt', async () => {
        zeigeSuche(
            <ArtikelSuche
                onZeilenKlick={() => {}}
                zeilenGedrueckt={() => false}
            />,
        );

        const zeile = await screen.findByRole('button', { name: /T-Stahl an- oder abwählen/ });
        expect(zeile).toHaveAttribute('aria-pressed', 'false');
    });

    // Ohne Auswahl-Betrieb ist die Zeile ein Link auf die Detailseite - ein
    // aria-pressed waere dort schlicht falsch.
    it('setzt ohne Auswahl-Betrieb kein aria-pressed', async () => {
        zeigeSuche(<ArtikelSuche />);

        const zeile = await screen.findByRole('link', { name: /Details zu T-Stahl öffnen/ });
        expect(zeile).not.toHaveAttribute('aria-pressed');
    });

    it('rendert die uebergebene Zeilenaktion statt der Standardzeile', async () => {
        zeigeSuche(<ArtikelSuche zeilenAktion={(a) => <button>Übernehmen {a.produktname}</button>} />);

        expect(await screen.findByRole('button', { name: /Übernehmen T-Stahl/ })).toBeInTheDocument();
    });

    // Die Zeilenaktion sitzt in einer Zelle der anklickbaren Zeile. Ohne Stopp
    // liefe jeder Klick auf sie zusaetzlich in den Zeilen-Handler: Das
    // Auswahlfenster wuerde die Position doppelt uebernehmen bzw. aus dem
    // Fenster heraus auf die Detailseite navigieren.
    it('loest beim Klick auf die Zeilenaktion nicht zusaetzlich den Zeilenklick aus', async () => {
        const onZeilenKlick = vi.fn();
        const uebernehmen = vi.fn();
        zeigeSuche(
            <ArtikelSuche
                onZeilenKlick={onZeilenKlick}
                zeilenAktion={(a) => (
                    <button onClick={() => uebernehmen(a.id)}>Übernehmen {a.produktname}</button>
                )}
            />,
        );

        await userEvent.click(await screen.findByRole('button', { name: /Übernehmen T-Stahl/ }));

        expect(uebernehmen).toHaveBeenCalledWith(7);
        expect(onZeilenKlick).not.toHaveBeenCalled();
    });

    // Die Zeile faengt Enter/Leertaste ab - das darf die Zeilenaktion nicht mitreissen.
    it('loest auch per Tastatur keinen zusaetzlichen Zeilenklick aus', async () => {
        const onZeilenKlick = vi.fn();
        const uebernehmen = vi.fn();
        zeigeSuche(
            <ArtikelSuche
                onZeilenKlick={onZeilenKlick}
                zeilenAktion={(a) => (
                    <button onClick={() => uebernehmen(a.id)}>Übernehmen {a.produktname}</button>
                )}
            />,
        );

        (await screen.findByRole('button', { name: /Übernehmen T-Stahl/ })).focus();
        await userEvent.keyboard('{Enter}');

        expect(uebernehmen).toHaveBeenCalledWith(7);
        expect(onZeilenKlick).not.toHaveBeenCalled();
    });

    it('navigiert nicht, wenn die Zeilenaktion ohne onZeilenKlick geklickt wird', async () => {
        zeigeSuche(<ArtikelSuche zeilenAktion={(a) => <button>Übernehmen {a.produktname}</button>} />);

        await userEvent.click(await screen.findByRole('button', { name: /Übernehmen T-Stahl/ }));

        // Ohne Stopp landete man hier auf /artikel/7 - im Auswahlfenster fatal.
        expect(aktuelleAdresse).toBe('/');
    });

    // Ohne eigene Zeilenaktion bleibt die Pfeil-Zelle bewusst durchlaessig:
    // Dort ist der Klick Teil des Zeilen-Klicks (Artikelverwaltung).
    it('behaelt den Zeilenklick auf der Pfeil-Zelle ohne eigene Zeilenaktion', async () => {
        const onZeilenKlick = vi.fn();
        zeigeSuche(<ArtikelSuche onZeilenKlick={onZeilenKlick} />);

        // Mit onZeilenKlick ist die Zeile ein Button ("an- oder abwaehlen"),
        // kein Link mehr - sie navigiert ja nirgendwohin.
        const zeile = await screen.findByRole('button', { name: /T-Stahl an- oder abwählen/i });
        await userEvent.click(zeile.querySelector('td:last-child') as HTMLElement);

        expect(onZeilenKlick).toHaveBeenCalledWith(expect.objectContaining({ id: 7 }));
    });

    it('laesst die URL unangetastet, wenn urlSync aus ist', async () => {
        zeigeSuche(<ArtikelSuche urlSync={false} />);

        await userEvent.type(await screen.findByLabelText('Freitext'), 'Rundrohr');

        await waitFor(() => expect(fetch).toHaveBeenCalledWith(expect.stringContaining('q=Rundrohr')));
        // Weder die echte Adresszeile noch die des Routers duerfen sich bewegen:
        // Das Auswahlfenster darf die Seite dahinter nicht umschreiben.
        expect(window.location.search).toBe('');
        expect(aktuelleAdresse).toBe('/');
    });

    it('spiegelt die Suche in die Adresszeile, wenn urlSync an ist', async () => {
        zeigeSuche(<ArtikelSuche urlSync />);

        await userEvent.type(await screen.findByLabelText('Freitext'), 'Rundrohr');

        await waitFor(() => expect(aktuelleAdresse).toBe('/?q=Rundrohr'));
    });

    it('meldet den Zeilenklick nach aussen, statt zur Detailseite zu springen', async () => {
        const onZeilenKlick = vi.fn();
        zeigeSuche(<ArtikelSuche onZeilenKlick={onZeilenKlick} />);

        // Die Zeile kuendigt jetzt auch an, was der Klick tut - nicht mehr die
        // Detailseite, die er gerade nicht oeffnet.
        await userEvent.click(await screen.findByRole('button', { name: /T-Stahl an- oder abwählen/i }));

        expect(onZeilenKlick).toHaveBeenCalledWith(expect.objectContaining({ id: 7 }));
        expect(aktuelleAdresse).toBe('/');
    });

    it('laedt mit der uebergebenen Seitengroesse', async () => {
        zeigeSuche(<ArtikelSuche seitenGroesse={20} />);

        await waitFor(() => expect(letzterListenRequest().get('size')).toBe('20'));
    });

    it('verwirft die verspaetete Antwort auf einen veralteten Suchbegriff', async () => {
        // Die Antwort auf "a" wird kuenstlich zurueckgehalten, damit sie erst nach
        // der Antwort auf "ab" eintrifft - der Fall, der beim schnellen Tippen auftritt.
        const zurueckgehalten: Array<() => void> = [];
        vi.stubGlobal('fetch', vi.fn((url: string) => {
            if (url.includes('/filteroptionen') || url.includes('/werkstoffe')) return Promise.resolve(filteroptionen());

            const suchbegriff = new URLSearchParams(url.substring(url.indexOf('?') + 1)).get('q');
            if (suchbegriff === 'a') {
                return new Promise((aufloesen) => {
                    zurueckgehalten.push(() => aufloesen(antwort([{ id: 101, produktname: 'Alter Treffer' }])));
                });
            }
            if (suchbegriff === 'ab') {
                return Promise.resolve(antwort([{ id: 102, produktname: 'Neuer Treffer' }]));
            }
            return Promise.resolve(antwort(TREFFER));
        }));

        zeigeSuche(<ArtikelSuche />);
        expect(await screen.findByText('T-Stahl')).toBeInTheDocument();

        await userEvent.type(await screen.findByLabelText('Freitext'), 'ab');
        expect(await screen.findByText('Neuer Treffer')).toBeInTheDocument();

        await act(async () => {
            zurueckgehalten.forEach((aufloesen) => aufloesen());
            await Promise.resolve();
        });

        expect(screen.queryByText('Alter Treffer')).not.toBeInTheDocument();
        expect(screen.getByText('Neuer Treffer')).toBeInTheDocument();
    });

    it('stellt formatCurrency als benannten Export bereit', () => {
        expect(formatCurrency(8.4)).toBe(new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(8.4));
        expect(formatCurrency()).toBe(new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(0));
    });
});
