import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';
import { ABFRAGE_ABSTAND_MS, TelefonbuchAbgleichEinstellung } from './TelefonbuchAbgleichEinstellung';
import { antwort, aufrufe, stubbeFetch } from './telefonTestdaten';
import type { TelefonbuchAbgleichErgebnis, TelefonbuchStatus } from './types';

const PFAD = '/api/telefon/einstellungen/telefonbuch';

const STAND: TelefonbuchStatus = {
    aktiv: true,
    telefonAnbindungAktiv: true,
    telefonbuchName: 'ERP-Kontakte',
    zuletztAbgeglichen: '2026-10-21T09:30:00',
    anzahlEintraege: 42,
    letzterFehler: null,
    loeschungOffen: false,
    laeuftGerade: false,
    letztesErgebnis: null,
};

const OK: TelefonbuchAbgleichErgebnis = {
    erfolgreich: true, laeuftSchon: false, meldung: 'Telefonbuch abgeglichen.', neu: 2, geaendert: 1, geloescht: 0, abgelehnt: 0, eintraege: 44,
};

/**
 * Simulierter Server: POST /abgleichen und PUT starten einen Lauf (laeuftGerade), der nach
 * {@code abfragenBisFertig} Status-Abfragen mit {@code ergebnis} endet.
 */
function stubbe(stand: Partial<TelefonbuchStatus> = {}, optionen: { ergebnis?: TelefonbuchAbgleichErgebnis; abfragenBisFertig?: number; putFehler?: boolean; postFehler?: string } = {}) {
    let aktuell: TelefonbuchStatus = { ...STAND, ...stand };
    let offeneAbfragen = aktuell.laeuftGerade ? (optionen.abfragenBisFertig ?? 1) + 1 : 0;
    const ergebnis = optionen.ergebnis ?? OK;
    const starte = () => {
        aktuell = { ...aktuell, laeuftGerade: true };
        offeneAbfragen = optionen.abfragenBisFertig ?? 1;
    };
    return stubbeFetch(
        (url, init) => {
            if (url.pathname !== PFAD) return undefined;
            if (init?.method === 'PUT') {
                if (optionen.putFehler) return antwort({ message: 'Die Einstellung konnte nicht gespeichert werden.' }, 500);
                const aktiv = JSON.parse(String(init.body)).aktiv as boolean;
                aktuell = { ...aktuell, aktiv, loeschungOffen: !aktiv };
                starte();
                return antwort(aktuell);
            }
            if (aktuell.laeuftGerade && --offeneAbfragen <= 0) {
                aktuell = aktuell.aktiv
                    ? { ...aktuell, laeuftGerade: false, letztesErgebnis: ergebnis, anzahlEintraege: ergebnis.eintraege,
                        letzterFehler: ergebnis.erfolgreich ? null : ergebnis.meldung }
                    : { ...aktuell, laeuftGerade: false, loeschungOffen: false, anzahlEintraege: 0,
                        letztesErgebnis: { ...ergebnis, meldung: 'Das Telefonbuch „ERP-Kontakte“ ist aus der FRITZ!Box gelöscht.' } };
            }
            return antwort(aktuell);
        },
        (url, init) => {
            if (url.pathname !== `${PFAD}/abgleichen` || init?.method !== 'POST') return undefined;
            if (optionen.postFehler) return antwort({ message: optionen.postFehler }, 409);
            starte();
            return antwort(aktuell, 202);
        },
    );
}

function zeige(telefonAnbindungAktiv?: boolean) {
    return render(
        <ToastProvider><ConfirmProvider><TelefonbuchAbgleichEinstellung telefonAnbindungAktiv={telefonAnbindungAktiv} /></ConfirmProvider></ToastProvider>,
    );
}

/** Eine Abfrage-Runde abwarten. */
async function naechsteAbfrage() {
    await act(async () => {
        await vi.advanceTimersByTimeAsync(ABFRAGE_ABSTAND_MS);
    });
}

describe('TelefonbuchAbgleichEinstellung', () => {
    beforeEach(() => {
        vi.useFakeTimers({ shouldAdvanceTime: true, toFake: ['setTimeout', 'clearTimeout'] });
    });
    afterEach(() => {
        vi.useRealTimers();
        vi.unstubAllGlobals();
    });

    it('zeigt Stand, Hinweise und den Schalter', async () => {
        stubbe();
        zeige();
        expect(await screen.findByRole('checkbox', { name: /Kontakte ins FRITZ!Box-Telefonbuch übertragen/ })).toBeChecked();
        expect(screen.getByText('42')).toBeInTheDocument();
        expect(screen.getByText('keiner')).toBeInTheDocument();
        expect(screen.getByText(/In der FRITZ!App Fon und an den Telefonen das Telefonbuch/)).toBeInTheDocument();
        expect(screen.getByText(/Nummern der Mitarbeiter sind dort für alle sichtbar/)).toBeInTheDocument();
        expect(screen.getByText(/wird aus der FRITZ!Box gelöscht/)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Mehr zu: Kontakte ins FRITZ!Box-Telefonbuch übertragen/ })).toBeInTheDocument();
    });

    it('Einschalten speichert sofort und zeigt nach dem Lauf das Ergebnis', async () => {
        const fetchMock = stubbe({ aktiv: false, zuletztAbgeglichen: null, anzahlEintraege: 0 });
        zeige();
        const schalter = await screen.findByRole('checkbox', { name: /Kontakte ins FRITZ!Box-Telefonbuch übertragen/ });
        expect(screen.getByText('noch nie')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Jetzt abgleichen/ })).toBeDisabled();

        fireEvent.click(schalter);

        await waitFor(() => expect(aufrufe(fetchMock, PFAD, 'PUT')).toHaveLength(1));
        expect(JSON.parse(String(aufrufe(fetchMock, PFAD, 'PUT')[0][1]?.body))).toEqual({ aktiv: true });
        expect(await screen.findByText('Die Kontakte werden jetzt ins FRITZ!Box-Telefonbuch übertragen.')).toBeInTheDocument();
        expect(schalter).toBeChecked();
        expect(screen.getByRole('button', { name: /Wird abgeglichen/ })).toBeDisabled();

        await naechsteAbfrage();
        expect(await screen.findByText('Abgeglichen: 2 neu, 1 geändert. Jetzt 44 Einträge.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Jetzt abgleichen/ })).toBeEnabled();
    });

    it('Ausschalten fragt nach, löscht im Hintergrund und meldet das Ergebnis', async () => {
        const fetchMock = stubbe();
        zeige();
        fireEvent.click(await screen.findByRole('checkbox', { name: /Kontakte ins FRITZ!Box-Telefonbuch übertragen/ }));

        const dialog = await screen.findByRole('dialog');
        expect(dialog).toHaveTextContent('Das Telefonbuch „ERP-Kontakte“ wird dabei aus der FRITZ!Box gelöscht.');
        fireEvent.click(screen.getByRole('button', { name: 'Ausschalten und löschen' }));

        await waitFor(() => expect(aufrufe(fetchMock, PFAD, 'PUT')).toHaveLength(1));
        expect(JSON.parse(String(aufrufe(fetchMock, PFAD, 'PUT')[0][1]?.body))).toEqual({ aktiv: false });
        expect(await screen.findByRole('button', { name: /Wird gelöscht/ })).toBeDisabled();

        await naechsteAbfrage();
        expect(await screen.findByText('Das Telefonbuch „ERP-Kontakte“ ist aus der FRITZ!Box gelöscht.')).toBeInTheDocument();
        expect(screen.getByRole('checkbox', { name: /Kontakte ins FRITZ!Box-Telefonbuch übertragen/ })).not.toBeChecked();
    });

    it('Ausschalten abgebrochen: nichts wird gespeichert', async () => {
        const fetchMock = stubbe();
        zeige();
        const schalter = await screen.findByRole('checkbox', { name: /Kontakte ins FRITZ!Box-Telefonbuch übertragen/ });
        fireEvent.click(schalter);
        fireEvent.click(await screen.findByRole('button', { name: 'Abbrechen' }));

        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        expect(aufrufe(fetchMock, PFAD, 'PUT')).toHaveLength(0);
        expect(schalter).toBeChecked();
    });

    it('offene Löschung: Hinweis und „Löschen jetzt nachholen“', async () => {
        const fetchMock = stubbe({ aktiv: false, loeschungOffen: true, letzterFehler: 'FRITZ!Box nicht erreichbar' });
        zeige();
        expect(await screen.findByText(/ist noch nicht aus der FRITZ!Box gelöscht/)).toBeInTheDocument();
        expect(screen.getByText('FRITZ!Box nicht erreichbar')).toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: /Löschen jetzt nachholen/ }));
        await waitFor(() => expect(aufrufe(fetchMock, `${PFAD}/abgleichen`, 'POST')).toHaveLength(1));
        await screen.findByRole('button', { name: /Wird gelöscht/ });
        await naechsteAbfrage();
        expect(await screen.findByText('Das Telefonbuch „ERP-Kontakte“ ist aus der FRITZ!Box gelöscht.')).toBeInTheDocument();
        expect(screen.queryByText(/ist noch nicht aus der FRITZ!Box gelöscht/)).not.toBeInTheDocument();
    });

    it('„Jetzt abgleichen“ fragt den Stand ab, bis der Lauf fertig ist', async () => {
        const fetchMock = stubbe({}, { abfragenBisFertig: 2 });
        zeige();
        fireEvent.click(await screen.findByRole('button', { name: /Jetzt abgleichen/ }));

        expect(await screen.findByText(/Läuft im Hintergrund/)).toBeInTheDocument();
        const getsVorher = aufrufe(fetchMock, PFAD, 'GET').length;
        await naechsteAbfrage();
        expect(screen.getByRole('button', { name: /Wird abgeglichen/ })).toBeInTheDocument();
        await naechsteAbfrage();
        expect(await screen.findByText('Abgeglichen: 2 neu, 1 geändert. Jetzt 44 Einträge.')).toBeInTheDocument();
        expect(aufrufe(fetchMock, PFAD, 'GET').length - getsVorher).toBe(2);

        // fertig: keine weiteren Abfragen
        await naechsteAbfrage();
        expect(aufrufe(fetchMock, PFAD, 'GET').length - getsVorher).toBe(2);
    });

    it('zeigt einen Fehler der FRITZ!Box als Toast und im Stand', async () => {
        const meldung = 'Der FRITZ!Box-Benutzer des ERP darf das Telefonbuch nicht ändern.';
        stubbe({}, { ergebnis: { ...OK, erfolgreich: false, meldung, neu: 0, geaendert: 0, eintraege: 0 } });
        zeige();
        fireEvent.click(await screen.findByRole('button', { name: /Jetzt abgleichen/ }));
        await screen.findByText(/Läuft im Hintergrund/);
        await naechsteAbfrage();

        await waitFor(() => expect(screen.getAllByText(meldung).length).toBeGreaterThanOrEqual(3));
    });

    it('meldet, wenn der Abgleich nicht starten kann', async () => {
        stubbe({}, { postFehler: 'Die Telefon-Anbindung ist ausgeschaltet.' });
        zeige();
        fireEvent.click(await screen.findByRole('button', { name: /Jetzt abgleichen/ }));
        expect(await screen.findByText('Die Telefon-Anbindung ist ausgeschaltet.')).toBeInTheDocument();
    });

    it('beim Verlassen der Seite wird nicht mehr abgefragt', async () => {
        const fetchMock = stubbe({ laeuftGerade: true }, { abfragenBisFertig: 99 });
        const { unmount } = zeige();
        await screen.findByRole('button', { name: /Wird abgeglichen/ });
        const gets = aufrufe(fetchMock, PFAD, 'GET').length;
        unmount();
        await naechsteAbfrage();
        await naechsteAbfrage();
        expect(aufrufe(fetchMock, PFAD, 'GET').length).toBe(gets);
    });

    it('sperrt den Abgleich bei ausgeschalteter Telefon-Anbindung und sagt warum', async () => {
        stubbe();
        zeige(false);
        const knopf = await screen.findByRole('button', { name: /Jetzt abgleichen/ });
        expect(knopf).toBeDisabled();
        expect(knopf).toHaveAttribute('title', 'Erst die Telefon-Anbindung aktivieren und speichern');
        expect(screen.getByText(/Die Telefon-Anbindung ist ausgeschaltet/)).toBeInTheDocument();
    });

    it('legt den Schalter zurück, wenn das Speichern scheitert', async () => {
        stubbe({ aktiv: false }, { putFehler: true });
        zeige();
        const schalter = await screen.findByRole('checkbox', { name: /Kontakte ins FRITZ!Box-Telefonbuch übertragen/ });
        fireEvent.click(schalter);
        expect(await screen.findByText('Die Einstellung konnte nicht gespeichert werden.')).toBeInTheDocument();
        await waitFor(() => expect(schalter).not.toBeChecked());
    });

    it('zeigt einen Ladefehler mit „Erneut laden“', async () => {
        stubbeFetch(() => antwort({ message: 'Keine Verbindung zum Server.' }, 500));
        zeige();
        expect(await screen.findByRole('button', { name: 'Erneut laden' })).toBeInTheDocument();
        // einmal im Bereich, einmal als Toast
        expect(screen.getAllByText('Keine Verbindung zum Server.')).toHaveLength(2);
    });
});
