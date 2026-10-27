import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../ui/toast';
import { DateiOrdnerSection } from './DateiOrdnerSection';

const FREIGABE = '\\\\erp-dateien.example.ts.net\\CADdrawings';
const FREIGABE_PFAD = '/app/uploads/CADdrawings';
const ALTE_ADRESSE = '\\\\BUERO-PC\\Zeichnungen';
const FELD = 'Netzwerk-Adresse für Kollegen (optional)';
const HINWEIS = 'Ordner-Freigabe des ERP ist eingeschaltet';
const ANLEITUNG = 'So geben Sie den Ordner im Netzwerk frei';
const EINTRAGEN = 'Diese Adresse eintragen';
const ORDNER_WARNUNG = 'So würden die Links ins Leere zeigen.';
const LAUNCHER_SATZ = 'Benutzer und Passwort für den Ordner stecken schon in der Einrichtung';
const LAUNCHER_ERST_SPEICHERN = 'Erst oben die Adresse der Ordner-Freigabe eintragen und speichern';

describe('DateiOrdnerSection – Ordner-Freigabe aus dem ERP-Paket', () => {
    let antwort: Record<string, unknown>;
    let gesendet: { pfad: string; networkUrl: string } | null;

    beforeEach(() => {
        antwort = {
            pfad: FREIGABE_PFAD,
            networkUrl: ALTE_ADRESSE,
            konfiguriert: true,
            containerFreigabe: FREIGABE,
            containerFreigabePfad: FREIGABE_PFAD,
        };
        gesendet = null;
        vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
            if (url === '/api/settings/datei-ordner' && init?.method === 'PUT') {
                gesendet = JSON.parse(String(init.body));
                return new Response('{}', { status: 200 });
            }
            if (url === '/api/settings/datei-ordner') {
                return new Response(JSON.stringify(antwort), { status: 200 });
            }
            return new Response('{}', { status: 404 });
        }));
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    function zeige() {
        return render(<ToastProvider><DateiOrdnerSection /></ToastProvider>);
    }

    it('verhält sich ohne Freigabe wie bisher: kein Hinweis, Windows-Anleitung da', async () => {
        antwort = { pfad: 'C:\\Zeichnungen', networkUrl: ALTE_ADRESSE, konfiguriert: true, containerFreigabe: null, containerFreigabePfad: null };
        zeige();

        await screen.findByLabelText(FELD);
        expect(screen.queryByText(HINWEIS)).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: EINTRAGEN })).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: ANLEITUNG })).toBeInTheDocument();
        expect(screen.queryByText(LAUNCHER_SATZ, { exact: false })).not.toBeInTheDocument();
        expect(screen.queryByText(LAUNCHER_ERST_SPEICHERN, { exact: false })).not.toBeInTheDocument();
    });

    it('behandelt ein fehlendes Feld (älterer Server) wie keine Freigabe', async () => {
        antwort = { pfad: 'C:\\Zeichnungen', networkUrl: ALTE_ADRESSE, konfiguriert: true };
        zeige();

        await screen.findByLabelText(FELD);
        expect(screen.queryByText(HINWEIS)).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: ANLEITUNG })).toBeInTheDocument();
    });

    it('zeigt die Adresse der Freigabe und blendet die Windows-Anleitung aus', async () => {
        zeige();

        const hinweis = await screen.findByRole('region', { name: HINWEIS });
        expect(within(hinweis).getByText(FREIGABE)).toBeInTheDocument();
        expect(within(hinweis).getByRole('button', { name: 'Mehr zu: Ordner-Freigabe des ERP' })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: ANLEITUNG })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Ordner automatisch freigeben/ })).not.toBeInTheDocument();
        // Der Ordner passt schon: keine Warnung, nur die Adresse fehlt noch.
        expect(screen.queryByText(ORDNER_WARNUNG, { exact: false })).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: EINTRAGEN })).toBeInTheDocument();
        // Gespeichert ist noch die alte Adresse: die Karte sagt, dass erst gespeichert werden muss.
        expect(screen.getByText(LAUNCHER_ERST_SPEICHERN, { exact: false })).toBeInTheDocument();
        expect(screen.queryByText(LAUNCHER_SATZ, { exact: false })).not.toBeInTheDocument();
    });

    it('erklärt am Info-Knopf Hintergrund und Wirkung', async () => {
        zeige();

        fireEvent.click(await screen.findByRole('button', { name: 'Mehr zu: Ordner-Freigabe des ERP' }));
        const erklaerung = screen.getByRole('dialog', { name: 'Ordner-Freigabe des ERP' });
        expect(erklaerung).toHaveTextContent('Das ERP-Paket bringt eine eigene Ordner-Freigabe mit.');
        expect(erklaerung).toHaveTextContent('nur im eigenen Tailscale-Netz erreichbar');
        expect(erklaerung).toHaveTextContent('Die tägliche Sicherung nimmt den Ordner automatisch mit.');
        expect(erklaerung).toHaveTextContent('Bereits hochgeladene Zeichnungen und Tabellen bleiben, wo sie sind');
    });

    it('trägt die Adresse per Klick ins Feld ein und zeigt danach „Eingetragen“', async () => {
        zeige();

        const feld = await screen.findByLabelText(FELD);
        expect(feld).toHaveValue(ALTE_ADRESSE);
        expect(screen.getByText('Danach speichern', { exact: false })).toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: EINTRAGEN }));

        expect(feld).toHaveValue(FREIGABE);
        expect(feld).toHaveFocus();
        expect(screen.queryByRole('button', { name: EINTRAGEN })).not.toBeInTheDocument();
        expect(screen.getByText('Eingetragen')).toBeInTheDocument();
        // Eingetragen heißt noch nicht gespeichert: die Hilfsprogramm-Karte erinnert ans Speichern.
        expect(screen.getByText('Bitte erst speichern – das Hilfsprogramm bekommt die gespeicherte Adresse.')).toBeInTheDocument();
        expect(gesendet).toBeNull();
    });

    it('erkennt Adresse und Ordner auch mit anderer Schreibweise, Leerzeichen und Schrägstrich am Ende als eingetragen', async () => {
        antwort = { ...antwort, networkUrl: `  ${FREIGABE.toUpperCase()} `, pfad: ` ${FREIGABE_PFAD}/ ` };
        zeige();

        expect(await screen.findByText('Eingetragen')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: EINTRAGEN })).not.toBeInTheDocument();
    });

    it('schickt die übernommene Adresse beim Speichern mit', async () => {
        zeige();

        fireEvent.click(await screen.findByRole('button', { name: EINTRAGEN }));
        fireEvent.click(screen.getByRole('button', { name: /Datei-Ordner speichern/ }));

        await waitFor(() => expect(gesendet).toEqual({ pfad: FREIGABE_PFAD, networkUrl: FREIGABE }));
        await waitFor(() => expect(screen.queryByText(/Bitte erst speichern/)).not.toBeInTheDocument());
        // Jetzt ist genau die Adresse der Freigabe gespeichert: die Zugangsdaten kommen mit der Einrichtung.
        expect(screen.getByText(LAUNCHER_SATZ, { exact: false })).toBeInTheDocument();
        expect(screen.queryByText(LAUNCHER_ERST_SPEICHERN, { exact: false })).not.toBeInTheDocument();
    });

    it('bietet bei abweichendem Ordner keinen Knopf an, sondern verweist an den Administrator', async () => {
        antwort = { ...antwort, pfad: 'C:\\Zeichnungen' };
        zeige();

        const hinweis = await screen.findByRole('region', { name: HINWEIS });
        expect(within(hinweis).getByText(ORDNER_WARNUNG, { exact: false })).toHaveTextContent(
            `Die Freigabe gibt den Ordner ${FREIGABE_PFAD} frei, das Programm legt Zeichnungen aber in C:\\Zeichnungen ab. `
            + 'So würden die Links ins Leere zeigen. Bitte den Administrator fragen: Er stellt die Freigabe auf den '
            + 'bisherigen Ordner um oder zieht die Dateien um.',
        );
        expect(within(hinweis).queryByRole('button', { name: /eintragen/ })).not.toBeInTheDocument();
        expect(within(hinweis).queryByText('Eingetragen')).not.toBeInTheDocument();

        // Der Ordner bleibt, wie er ist – auch beim Speichern.
        expect(screen.getByLabelText('Ordner auf diesem Rechner')).toHaveValue('C:\\Zeichnungen');
        fireEvent.click(screen.getByRole('button', { name: /Datei-Ordner speichern/ }));
        await waitFor(() => expect(gesendet).toEqual({ pfad: 'C:\\Zeichnungen', networkUrl: ALTE_ADRESSE }));
    });

    it('zeigt auch bei schon eingetragener Adresse kein „Eingetragen“, solange der Ordner abweicht', async () => {
        antwort = { ...antwort, pfad: 'C:\\Zeichnungen', networkUrl: FREIGABE };
        zeige();

        const hinweis = await screen.findByRole('region', { name: HINWEIS });
        expect(within(hinweis).queryByText('Eingetragen')).not.toBeInTheDocument();
        expect(within(hinweis).getByText(ORDNER_WARNUNG, { exact: false })).toBeInTheDocument();
    });

    it('vergleicht nur die Adresse, wenn der Server keinen Ordner zur Freigabe meldet', async () => {
        antwort = { ...antwort, pfad: 'C:\\Zeichnungen', networkUrl: FREIGABE, containerFreigabePfad: null };
        zeige();

        const hinweis = await screen.findByRole('region', { name: HINWEIS });
        expect(within(hinweis).getByText('Eingetragen')).toBeInTheDocument();
        expect(within(hinweis).queryByText(ORDNER_WARNUNG, { exact: false })).not.toBeInTheDocument();
    });
});
