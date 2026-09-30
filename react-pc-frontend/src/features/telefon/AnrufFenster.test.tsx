import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ComponentProps } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { AnrufFenster } from './AnrufFenster';
import { antwort, aufrufe, KANZLEI_BEISPIEL, KUNDE_ERIKA, KUNDE_MAX, LIEFERANT_GMBH, stubbeFetch, UEBERBLICK_MAX } from './telefonTestdaten';
import { setzeTelefonBerechtigungZurueck } from './useTelefonBerechtigung';
import { WAEHL_TELEFON_SCHLUESSEL } from './useWaehlTelefon';
import type { UeberblickZustand } from './useKontaktUeberblick';
import type { LiveAnrufAnzeige } from './useTelefonLive';

function live(teil: Partial<LiveAnrufAnzeige> = {}): LiveAnrufAnzeige {
    return {
        verbindungsId: '1',
        status: 'KLINGELT',
        nummer: '0931 1234567',
        kontakt: KUNDE_MAX,
        kandidaten: [],
        angenommen: false,
        gespraechSeit: null,
        verpasst: false,
        ...teil,
    };
}

const LAEDT: UeberblickZustand = { status: 'laedt' };

/** Das Fenster braucht die Meldungsfläche (Zurückrufen meldet Erfolg und Fehler per Toast). */
function Fenster(props: ComponentProps<typeof AnrufFenster>) {
    return <ToastProvider><AnrufFenster {...props} /></ToastProvider>;
}

function zeige(anruf: LiveAnrufAnzeige, weitere = 0, ueberblick: UeberblickZustand | null = LAEDT) {
    const onSchliessen = vi.fn();
    const onKontaktOeffnen = vi.fn();
    const onOeffnen = vi.fn();
    const onFesthalten = vi.fn();
    const ergebnis = render(
        <Fenster
            anruf={anruf}
            weitere={weitere}
            onSchliessen={onSchliessen}
            onKontaktOeffnen={onKontaktOeffnen}
            ueberblick={anruf.kontakt ? ueberblick : null}
            onOeffnen={onOeffnen}
            onFesthalten={onFesthalten}
        />,
    );
    return { ...ergebnis, onSchliessen, onKontaktOeffnen, onOeffnen, onFesthalten };
}

describe('AnrufFenster', () => {
    beforeEach(() => setzeTelefonBerechtigungZurueck());
    afterEach(() => {
        vi.useRealTimers();
        vi.unstubAllGlobals();
        window.localStorage.clear();
    });

    it('zeigt einen bekannten Kunden groß mit allen Angaben und öffnet die Akte', () => {
        const { onKontaktOeffnen } = zeige(live());
        expect(screen.getByRole('dialog', { name: 'Max Mustermann' })).toHaveAttribute('aria-modal', 'false');
        expect(screen.getByRole('status')).toHaveTextContent('ruft an');
        expect(screen.getByText('Kunde')).toBeInTheDocument();
        expect(screen.getByText('Kunden-Nr. K-1007')).toBeInTheDocument();
        expect(screen.getByText('Musterstadt')).toBeInTheDocument();
        expect(screen.getByText('0931 1234567')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Akte öffnen' }));
        expect(onKontaktOeffnen).toHaveBeenCalledWith(KUNDE_MAX);
    });

    it('kennzeichnet Lieferanten', () => {
        zeige(live({ kontakt: LIEFERANT_GMBH }));
        expect(screen.getByRole('heading', { name: 'Mustermann GmbH' })).toBeInTheDocument();
        expect(screen.getByText('Lieferant')).toBeInTheDocument();
    });

    it('stiehlt keinen Fokus: das Eingabefeld im Hintergrund behält ihn', () => {
        render(<input aria-label="Dokumenttext" />);
        const feld = screen.getByLabelText('Dokumenttext');
        feld.focus();
        zeige(live());
        expect(document.activeElement).toBe(feld);
    });

    it('zeigt unbekannte Nummern nur mit Schließen', () => {
        zeige(live({ kontakt: null }));
        expect(screen.getByRole('heading', { name: 'Unbekannte Nummer' })).toBeInTheDocument();
        expect(screen.getByText('0931 1234567')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Akte öffnen' })).toBeNull();
        expect(screen.getByRole('button', { name: 'Schließen' })).toBeInTheDocument();
    });

    it('zeigt unterdrückte Nummern', () => {
        zeige(live({ kontakt: null, nummer: '' }));
        expect(screen.getByRole('heading', { name: 'Nummer unterdrückt' })).toBeInTheDocument();
    });

    it('bietet bei mehrdeutiger Nummer die Kandidaten zum Öffnen an', () => {
        const { onKontaktOeffnen } = zeige(live({ kontakt: null, kandidaten: [KUNDE_MAX, KUNDE_ERIKA] }));
        expect(screen.getByRole('heading', { name: 'Einer dieser Kontakte' })).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: /Erika Mustermann/ }));
        expect(onKontaktOeffnen).toHaveBeenCalledWith(KUNDE_ERIKA);
    });

    it('zeigt im Gespräch die laufende Dauer', () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date(2026, 8, 29, 12, 0, 0));
        zeige(live({ status: 'IM_GESPRAECH', angenommen: true, gespraechSeit: Date.now() - 83_000 }));
        expect(screen.getByRole('status')).toHaveTextContent('Im Gespräch · 01:23');
        act(() => { vi.advanceTimersByTime(2000); });
        expect(screen.getByRole('status')).toHaveTextContent('Im Gespräch · 01:25');
    });

    it('meldet den Anrufbeantworter und verpasste Anrufe', () => {
        const { rerender } = zeige(live({ status: 'ANRUFBEANTWORTER' }));
        expect(screen.getByRole('status')).toHaveTextContent('Anrufbeantworter nimmt auf');
        rerender(<Fenster anruf={live({ status: 'BEENDET', verpasst: true })} weitere={0} onSchliessen={vi.fn()} onKontaktOeffnen={vi.fn()} ueberblick={LAEDT} onOeffnen={vi.fn()} />);
        expect(screen.getByRole('status')).toHaveTextContent('Verpasst');
    });

    it('schließt per Escape und Klick auf den Hintergrund', () => {
        const { onSchliessen } = zeige(live());
        fireEvent.keyDown(document, { key: 'Escape' });
        expect(onSchliessen).toHaveBeenCalledTimes(1);
        fireEvent.click(screen.getByTestId('anruf-fenster-hintergrund'));
        expect(onSchliessen).toHaveBeenCalledTimes(2);
    });

    describe('Zurückrufen', () => {
        function stubbeTelefon() {
            return stubbeFetch(
                (url) => (url.pathname === '/api/telefon/berechtigung' ? antwort({ darfTelefonSehen: true }) : undefined),
                (url) => (url.pathname === '/api/telefon/telefone' ? antwort([{ name: 'LAN: PC Büro' }]) : undefined),
                (url, init) => (url.pathname === '/api/telefon/anrufen' && init?.method === 'POST' ? antwort(undefined, 204) : undefined),
            );
        }

        it('sagt beim Klingeln, wo man annimmt, und bietet noch kein Zurückrufen an', () => {
            stubbeTelefon();
            zeige(live());
            expect(screen.getByText('Am Headset oder im Telefon-Programm annehmen')).toBeInTheDocument();
            expect(screen.queryByRole('button', { name: /zurückrufen/ })).toBeNull();
        });

        it('zeigt den Hinweis im Gespräch nicht mehr', () => {
            zeige(live({ status: 'IM_GESPRAECH', angenommen: true, gespraechSeit: Date.now() }));
            expect(screen.queryByText('Am Headset oder im Telefon-Programm annehmen')).toBeNull();
        });

        it('bietet nach einem verpassten Anruf „Zurückrufen“ als Hauptaktion an und schließt nach dem Start', async () => {
            window.localStorage.setItem(WAEHL_TELEFON_SCHLUESSEL, 'LAN: PC Büro');
            const fetchMock = stubbeTelefon();
            const { onSchliessen, onFesthalten } = zeige(live({ status: 'BEENDET', verpasst: true }));
            const knopf = await screen.findByRole('button', { name: 'Max Mustermann zurückrufen' });
            expect(knopf).toHaveTextContent('Zurückrufen');
            expect(screen.getByRole('button', { name: 'Akte öffnen' })).toBeInTheDocument();

            fireEvent.click(knopf);
            expect(onFesthalten).toHaveBeenCalled();
            await waitFor(() => expect(aufrufe(fetchMock, '/api/telefon/anrufen', 'POST')).toHaveLength(1));
            expect(JSON.parse(String(aufrufe(fetchMock, '/api/telefon/anrufen', 'POST')[0][1]?.body)))
                .toEqual({ telefon: 'LAN: PC Büro', nummer: '0931 1234567' });
            await waitFor(() => expect(onSchliessen).toHaveBeenCalledTimes(1));
        });

        it('hält das verpasste Fenster offen, sobald man mit der Maus darauf zeigt', () => {
            const { onFesthalten } = zeige(live({ status: 'BEENDET', verpasst: true }));
            fireEvent.pointerEnter(screen.getByRole('dialog', { name: 'Max Mustermann' }));
            expect(onFesthalten).toHaveBeenCalled();
        });

        it('bietet bei unterdrückter Nummer kein Zurückrufen an', () => {
            stubbeTelefon();
            zeige(live({ kontakt: null, nummer: '', status: 'BEENDET', verpasst: true }));
            expect(screen.getByRole('heading', { name: 'Nummer unterdrückt' })).toBeInTheDocument();
            expect(screen.queryByRole('button', { name: /zurückrufen/ })).toBeNull();
        });

        it('Escape im Auswahl-Dialog schließt nur den Dialog, nicht das Anruf-Fenster', async () => {
            stubbeTelefon();
            const { onSchliessen } = zeige(live({ status: 'BEENDET', verpasst: true }));
            fireEvent.click(await screen.findByRole('button', { name: 'Max Mustermann zurückrufen' }));
            const dialog = await screen.findByRole('dialog', { name: 'Welches Telefon steht an diesem Rechner?' });
            // Der Dialog liegt über dem Anruf-Fenster (z-index 70, nur eine Quelle: style).
            const fensterEbene = Number(screen.getByTestId('anruf-fenster').style.zIndex);
            expect(fensterEbene).toBe(70);
            expect(Number(dialog.parentElement?.style.zIndex)).toBeGreaterThan(fensterEbene);
            fireEvent.keyDown(dialog, { key: 'Escape' });
            await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Welches Telefon steht an diesem Rechner?' })).toBeNull());
            expect(onSchliessen).not.toHaveBeenCalled();
        });
    });

    it('nennt weitere gleichzeitige Anrufe', () => {
        const { rerender } = zeige(live(), 1);
        expect(screen.getByText('+1 weiterer Anruf')).toBeInTheDocument();
        rerender(<Fenster anruf={live()} weitere={2} onSchliessen={vi.fn()} onKontaktOeffnen={vi.fn()} ueberblick={LAEDT} onOeffnen={vi.fn()} />);
        expect(screen.getByText('+2 weitere Anrufe')).toBeInTheDocument();
    });

    describe('Überblick zum Anrufer', () => {
        it('zeigt Ansprechpartner, Adresse, Projekte und Anfragen und springt hinein', () => {
            const { onOeffnen } = zeige(live(), 0, { status: 'fertig', daten: UEBERBLICK_MAX });
            expect(screen.getByText('Ansprechpartner')).toBeInTheDocument();
            expect(screen.getByText('Erika Mustermann')).toBeInTheDocument();
            expect(screen.getByText('Musterweg 1')).toBeInTheDocument();
            expect(screen.getByText('12345 Musterstadt')).toBeInTheDocument();

            const projekte = screen.getByRole('region', { name: 'Projekte' });
            expect(projekte).toHaveTextContent('(2)');
            expect(projekte).toHaveTextContent('Auftrags-Nr. 2026-001 · Musterstadt');
            expect(screen.getByRole('button', { name: /Carport/ })).toHaveTextContent('Abgeschlossen');
            fireEvent.click(screen.getByRole('button', { name: /Wintergarten Musterweg/ }));
            expect(onOeffnen).toHaveBeenCalledWith('/projekte?projektId=21');

            expect(screen.getByRole('button', { name: /Balkongeländer/ })).toHaveTextContent('Angebots-Nr. AN-2026-044 · Beispielhausen');
            const ohneBauvorhaben = screen.getByRole('button', { name: /Anfrage ohne Bauvorhaben/ });
            expect(ohneBauvorhaben).toHaveTextContent('Noch kein Angebot');
            fireEvent.click(ohneBauvorhaben);
            expect(onOeffnen).toHaveBeenCalledWith('/anfragen?anfrageId=32');
        });

        it('zeigt beim Laden Platzhalter statt leerer Listen, der Ort steht schon da', () => {
            zeige(live());
            expect(screen.getByText('Wird geladen …')).toBeInTheDocument();
            expect(screen.getByText('Musterstadt')).toBeInTheDocument();
            expect(screen.queryByText('Noch keine Projekte.')).toBeNull();
            expect(screen.queryByRole('button', { name: /Wintergarten/ })).toBeNull();
        });

        it('meldet leere Listen', () => {
            zeige(live(), 0, { status: 'fertig', daten: { ...UEBERBLICK_MAX, ansprechpartner: null, projekte: [], anfragen: [] } });
            expect(screen.getByText('Noch keine Projekte.')).toBeInTheDocument();
            expect(screen.getByText('Keine Anfragen.')).toBeInTheDocument();
            expect(screen.queryByText('Ansprechpartner')).toBeNull();
        });

        it('meldet einen Ladefehler sichtbar, Akte öffnen geht weiter', () => {
            zeige(live(), 0, { status: 'fehler', meldung: 'Projekte und Anfragen konnten nicht geladen werden.' });
            expect(screen.getByRole('alert')).toHaveTextContent('Projekte und Anfragen konnten nicht geladen werden.');
            expect(screen.queryByRole('region', { name: 'Projekte' })).toBeNull();
            expect(screen.getByRole('button', { name: 'Akte öffnen' })).toBeInTheDocument();
        });

        it('zeigt beim Lieferanten den Vertreter, aber keine Projekte', () => {
            zeige(live({ kontakt: LIEFERANT_GMBH }), 0, {
                status: 'fertig',
                daten: { ...UEBERBLICK_MAX, typ: 'LIEFERANT', id: 3, nummer: null, ansprechpartner: 'Hans Beispiel', projekte: [], anfragen: [] },
            });
            expect(screen.getByText('Vertreter')).toBeInTheDocument();
            expect(screen.getByText('Hans Beispiel')).toBeInTheDocument();
            expect(screen.queryByRole('region', { name: 'Projekte' })).toBeNull();
        });

        it('nennt bei mehr als 50 Einträgen die Gesamtzahl und verweist auf die Akte', () => {
            zeige(live(), 0, { status: 'fertig', daten: { ...UEBERBLICK_MAX, projekteGesamt: 57 } });
            const projekte = screen.getByRole('region', { name: 'Projekte' });
            expect(projekte).toHaveTextContent('(57)');
            expect(projekte).toHaveTextContent('… und 55 weitere – alle stehen in der Akte.');
            expect(screen.getByRole('region', { name: 'Anfragen' })).not.toHaveTextContent('weitere –');
        });

        it('zeigt ohne zugeordneten Kontakt keinen Überblick', () => {
            zeige(live({ kontakt: null }));
            expect(screen.queryByTestId('anruf-kontakt-details')).toBeNull();
        });
    });

    describe('Steuerberater', () => {
        it('zeigt die Kanzlei mit Ansprechpartnern, aber ohne „Akte öffnen“ und ohne Projekte', () => {
            zeige(live({ kontakt: KANZLEI_BEISPIEL }), 0, {
                status: 'fertig',
                daten: { ...UEBERBLICK_MAX, typ: 'STEUERBERATER', id: 30, name: 'Kanzlei Beispiel', nummer: null,
                    ansprechpartner: 'Christine Beispiel', strasse: null, plz: null, ort: null,
                    projekte: [], projekteGesamt: 0, anfragen: [], anfragenGesamt: 0 },
            });
            expect(screen.getByRole('heading', { name: 'Kanzlei Beispiel' })).toBeInTheDocument();
            expect(screen.getByText('Steuerberater')).toBeInTheDocument();
            expect(screen.getByText('Ansprechpartner')).toBeInTheDocument();
            expect(screen.getByText('Christine Beispiel')).toBeInTheDocument();
            expect(screen.queryByText('Adresse')).toBeNull();
            expect(screen.queryByRole('region', { name: 'Projekte' })).toBeNull();
            expect(screen.queryByRole('button', { name: 'Akte öffnen' })).toBeNull();
            expect(screen.getByRole('button', { name: 'Schließen' })).toBeInTheDocument();
        });

        it('zeigt nur den Ansprechpartner, von dessen Nummer angerufen wird', () => {
            zeige(live({ kontakt: { ...KANZLEI_BEISPIEL, ansprechpartner: 'Erika Beispiel' } }), 0, {
                status: 'fertig',
                daten: { ...UEBERBLICK_MAX, typ: 'STEUERBERATER', id: 30, name: 'Kanzlei Beispiel', nummer: null,
                    ansprechpartner: 'Christine Beispiel, Erika Beispiel', strasse: null, plz: null, ort: null,
                    projekte: [], projekteGesamt: 0, anfragen: [], anfragenGesamt: 0 },
            });
            expect(screen.getByText('Erika Beispiel')).toBeInTheDocument();
            expect(screen.queryByText(/Christine Beispiel/)).toBeNull();
        });

        it('bietet eine Kanzlei unter mehreren Kandidaten nur als Hinweis, nicht als Link an', () => {
            const { onKontaktOeffnen } = zeige(live({ kontakt: null, kandidaten: [KUNDE_MAX, KANZLEI_BEISPIEL] }));
            expect(screen.getByText('Kanzlei Beispiel')).toBeInTheDocument();
            expect(screen.queryByRole('button', { name: /Kanzlei Beispiel/ })).toBeNull();
            fireEvent.click(screen.getByRole('button', { name: /Max Mustermann/ }));
            expect(onKontaktOeffnen).toHaveBeenCalledWith(KUNDE_MAX);
        });
    });
});
