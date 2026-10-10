import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DatevEinstellungenKarte } from './DatevEinstellungenKarte';
import type { Konfiguration } from '../../../../features/monatsabschluss/types';

const { toast } = vi.hoisted(() => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('../../../ui/toast', () => ({ useToast: () => toast }));

/** Nur Dummy-Daten (DSGVO). */
const MITARBEITER = [
    { id: 1, vorname: 'Max', nachname: 'Mustermann', aktiv: true },
    { id: 3, vorname: 'Moritz', nachname: 'Muster', aktiv: false },
    { id: 2, vorname: 'Erika', nachname: 'Mustermann', aktiv: true },
];
const KATEGORIEN = ['ARBEIT', 'FEIERTAG', 'URLAUB', 'KRANKHEIT', 'FORTBILDUNG', 'ZEITAUSGLEICH', 'KRANKENGELD', 'WIEDEREINGLIEDERUNG'];

let config: Konfiguration;
let puts: Konfiguration[];
let gets: string[];
let putAntwort: { status: number; body: unknown } | null;
let mitarbeiterFehler: boolean;

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

beforeEach(() => {
    vi.clearAllMocks();
    puts = []; gets = []; putAntwort = null; mitarbeiterFehler = false;
    config = {
        version: 2, ziel: 'LODAS', beraterNr: '001234', mandantenNr: '0012', stundenart: 'SOLL',
        zuordnungen: KATEGORIEN.map(kategorie => ({ kategorie, ausgeschlossen: kategorie !== 'ARBEIT', lohnart: kategorie === 'ARBEIT' ? '0200' : '' })),
        personalnummern: [{ mitarbeiterId: 1, personalnummer: '00014', stundenart: null }],
    };
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        if (url === '/api/mitarbeiter') return mitarbeiterFehler ? json({ message: 'Mitarbeiter gerade nicht abrufbar.' }, 500) : json(MITARBEITER);
        expect(url).toBe('/api/settings/datev');
        if (init?.method === 'PUT') {
            const body = JSON.parse(String(init.body)) as Konfiguration;
            puts.push(body);
            if (putAntwort) return json(putAntwort.body, putAntwort.status);
            config = { ...body, version: (config.version ?? 0) + 1 };
            return json(config);
        }
        gets.push(url);
        return json(config);
    }));
});

afterEach(() => vi.unstubAllGlobals());

async function zeige() {
    render(<DatevEinstellungenKarte />);
    return screen.findByRole('textbox', { name: 'Beraternummer' });
}

async function waehle(combobox: string, option: string) {
    fireEvent.click(screen.getByRole('combobox', { name: combobox }));
    fireEvent.click(await screen.findByRole('option', { name: option }));
}

const speichern = () => fireEvent.click(screen.getByRole('button', { name: /DATEV-Einstellungen speichern/ }));

describe('DatevEinstellungenKarte', () => {
    it('lädt Nummern mit führenden Nullen, Soll als Betriebsstandard und alle Mitarbeiter', async () => {
        const berater = await zeige();
        expect(berater).toHaveValue('001234');
        expect(screen.getByRole('textbox', { name: 'Mandantennummer' })).toHaveValue('0012');
        expect(screen.getByRole('radio', { name: /Soll-Stunden \(laut Arbeitszeitkonto\)/ })).toBeChecked();
        expect(screen.getByRole('note')).toHaveTextContent(/Monats-Soll minus Urlaub, Krankheit, Feiertage usw\..*nichts wird doppelt gezählt/);
        expect(await screen.findByRole('textbox', { name: 'Personalnummer für Max Mustermann' })).toHaveValue('00014');
        const zeilen = within(screen.getByRole('region', { name: 'Mitarbeiter' })).getAllByRole('row').slice(1);
        expect(zeilen.map(z => within(z).getByRole('rowheader').textContent)).toEqual(['Max Mustermann', 'Erika Mustermann', 'Moritz MusterDeaktiviert']);
        expect(screen.getByRole('button', { name: /DATEV-Einstellungen speichern/ })).toBeDisabled();
        expect(screen.getByText('Alle Änderungen sind gespeichert.')).toBeVisible();
    });

    it('speichert Betrieb auf Ist und einen Mitarbeiter abweichend auf Soll', async () => {
        const onSaved = vi.fn();
        render(<DatevEinstellungenKarte onSaved={onSaved} />);
        await screen.findByRole('textbox', { name: 'Personalnummer für Erika Mustermann' });

        fireEvent.click(screen.getByRole('radio', { name: /Ist-Stunden \(tatsächlich gearbeitet\)/ }));
        expect(screen.queryByRole('note')).not.toBeInTheDocument();
        expect(screen.getByRole('combobox', { name: 'Stunden ans Steuerbüro für Max Mustermann' })).toHaveTextContent('Wie Betrieb (Ist-Stunden)');
        fireEvent.change(screen.getByRole('textbox', { name: 'Personalnummer für Erika Mustermann' }), { target: { value: '00016' } });
        await waehle('Stunden ans Steuerbüro für Erika Mustermann', 'Soll-Stunden');
        // Ein Mitarbeiter auf Soll: der Hinweis zu Urlaub/Krankheit/Feiertagen gilt wieder.
        expect(screen.getByRole('note')).toBeVisible();
        expect(screen.getByText('Es gibt ungespeicherte Änderungen.')).toBeVisible();

        speichern();
        await waitFor(() => expect(toast.success).toHaveBeenCalledWith('DATEV-Einstellungen gespeichert.'));
        expect(puts).toHaveLength(1);
        expect(puts[0].stundenart).toBe('IST');
        expect(puts[0].beraterNr).toBe('001234');
        expect(puts[0].personalnummern).toEqual([
            { mitarbeiterId: 1, personalnummer: '00014', stundenart: null },
            { mitarbeiterId: 2, personalnummer: '00016', stundenart: 'SOLL' },
        ]);
        expect(onSaved).toHaveBeenCalled();
        expect(screen.getByText('Alle Änderungen sind gespeichert.')).toBeVisible();
    });

    it('sperrt die Stunden-Auswahl ohne Personalnummer und setzt sie beim Leeren zurück', async () => {
        await zeige();
        const erika = await screen.findByRole('combobox', { name: 'Stunden ans Steuerbüro für Erika Mustermann' });
        expect(erika).toBeDisabled();
        expect(erika.parentElement?.parentElement).toHaveAttribute('title', 'Bitte zuerst eine Personalnummer eintragen.');

        await waehle('Stunden ans Steuerbüro für Max Mustermann', 'Ist-Stunden');
        fireEvent.change(screen.getByRole('textbox', { name: 'Personalnummer für Max Mustermann' }), { target: { value: '' } });
        const max = screen.getByRole('combobox', { name: 'Stunden ans Steuerbüro für Max Mustermann' });
        expect(max).toBeDisabled();
        expect(max).toHaveTextContent('Wie Betrieb (Soll-Stunden)');

        speichern();
        await waitFor(() => expect(puts).toHaveLength(1));
        // Leere Personalnummern gehen nicht mit – der Server lehnte sie ab.
        expect(puts[0].personalnummern).toEqual([]);
    });

    it('übernimmt Lohnarten je Art und lässt „Nicht exportieren“ ohne Lohnart', async () => {
        await zeige();
        await waehle('Export für Urlaub', 'Lohnart zuordnen');
        fireEvent.change(screen.getByRole('textbox', { name: 'Lohnart für Urlaub' }), { target: { value: '0300' } });
        await waehle('Export für Arbeit', 'Nicht exportieren');
        expect(screen.queryByRole('textbox', { name: 'Lohnart für Arbeit' })).not.toBeInTheDocument();

        speichern();
        await waitFor(() => expect(puts).toHaveLength(1));
        expect(puts[0].zuordnungen.find(z => z.kategorie === 'URLAUB')).toEqual({ kategorie: 'URLAUB', ausgeschlossen: false, lohnart: '0300' });
        expect(puts[0].zuordnungen.find(z => z.kategorie === 'ARBEIT')).toEqual({ kategorie: 'ARBEIT', ausgeschlossen: true, lohnart: '' });
    });

    it('lehnt ungültige oder doppelte Nummern vor dem Speichern ab', async () => {
        const berater = await zeige();
        fireEvent.change(berater, { target: { value: '12,5' } });
        speichern();
        expect(puts).toHaveLength(0);
        expect(toast.error).toHaveBeenCalledWith('Beraternummer: Bitte 4 bis 7 Ziffern und eine Nummer größer als 0 eingeben.');
        expect(screen.getByRole('alert')).toHaveTextContent('Beraternummer');

        fireEvent.change(berater, { target: { value: '001234' } });
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
        fireEvent.change(await screen.findByRole('textbox', { name: 'Personalnummer für Erika Mustermann' }), { target: { value: '14' } });
        speichern();
        expect(puts).toHaveLength(0);
        expect(await screen.findByText(/mehrfach vergeben/)).toBeVisible();
    });

    it('kann unvollständige Angaben speichern', async () => {
        config = { version: null, ziel: 'LODAS', beraterNr: null, mandantenNr: null, stundenart: 'SOLL', zuordnungen: [], personalnummern: [] };
        const berater = await zeige();
        fireEvent.change(berater, { target: { value: '1234' } });
        speichern();
        await waitFor(() => expect(puts).toHaveLength(1));
        expect(puts[0]).toMatchObject({ beraterNr: '1234', mandantenNr: '', stundenart: 'SOLL' });
        expect(puts[0].zuordnungen).toHaveLength(8);
    });

    it('behält beim Versionskonflikt den Entwurf und lädt nur auf ausdrücklichen Wunsch neu', async () => {
        putAntwort = { status: 409, body: { message: 'Die DATEV-Einstellungen wurden geändert. Bitte neu laden.' } };
        await zeige();
        const mandant = screen.getByRole('textbox', { name: 'Mandantennummer' });
        fireEvent.change(mandant, { target: { value: '00021' } });
        speichern();

        expect(await screen.findByRole('alert')).toHaveTextContent('Die DATEV-Einstellungen wurden geändert. Bitte neu laden.');
        expect(toast.error).toHaveBeenCalledWith('Die DATEV-Einstellungen wurden geändert. Bitte neu laden.');
        expect(mandant).toHaveValue('00021');
        expect(gets).toHaveLength(1);

        config = { ...config, version: 7, mandantenNr: '99' };
        fireEvent.click(screen.getByRole('button', { name: /Neu laden/ }));
        await waitFor(() => expect(screen.getByRole('textbox', { name: 'Mandantennummer' })).toHaveValue('99'));
        expect(gets).toHaveLength(2);
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });

    it('zeigt die Meldung des Servers, wenn er die Angaben ablehnt', async () => {
        putAntwort = { status: 400, body: { message: 'Die Lohnart ist unbekannt.' } };
        const berater = await zeige();
        fireEvent.change(berater, { target: { value: '7654' } });
        speichern();
        expect(await screen.findByRole('alert')).toHaveTextContent('Die Lohnart ist unbekannt.');
        expect(screen.queryByRole('button', { name: /Neu laden/ })).not.toBeInTheDocument();
    });

    it('meldet einen Fehler bei den Mitarbeitern, ohne den Entwurf zu verwerfen', async () => {
        mitarbeiterFehler = true;
        const berater = await zeige();
        fireEvent.change(berater, { target: { value: '7654' } });
        const region = screen.getByRole('region', { name: 'Mitarbeiter' });
        expect(await within(region).findByRole('alert')).toHaveTextContent('Mitarbeiter gerade nicht abrufbar.');

        mitarbeiterFehler = false;
        fireEvent.click(within(region).getByRole('button', { name: 'Erneut laden' }));
        expect(await screen.findByRole('textbox', { name: 'Personalnummer für Max Mustermann' })).toBeVisible();
        expect(screen.getByRole('textbox', { name: 'Beraternummer' })).toHaveValue('7654');
        expect(gets).toHaveLength(1);
    });

    it('erklärt DATEV, Soll/Ist, „Nicht exportieren“ und Personalnummer per Info-Knopf an Ort und Stelle', async () => {
        await zeige();
        await screen.findByRole('textbox', { name: 'Personalnummer für Max Mustermann' });
        const oeffne = (name: string, index = 0) => {
            fireEvent.click(screen.getAllByRole('button', { name: `Mehr zu: ${name}` })[index]);
            return screen.getByRole('dialog', { name });
        };

        expect(oeffne('DATEV einrichten')).toHaveTextContent('Es wird nichts automatisch an DATEV geschickt.');
        // Neben der Betriebs-Auswahl und in der Spalte je Mitarbeiter – derselbe Text.
        expect(screen.getAllByRole('button', { name: 'Mehr zu: Stunden ans Steuerbüro' })).toHaveLength(2);
        const stunden = oeffne('Stunden ans Steuerbüro', 1);
        expect(stunden).toHaveTextContent('nichts wird doppelt gezählt');
        expect(stunden).toHaveTextContent('Mitarbeiter ohne Soll (z. B. Aushilfen) auf „Ist“ stellen');
        fireEvent.keyDown(stunden, { key: 'Escape' });
        expect(oeffne('Nicht exportieren')).toHaveTextContent('müssen dort bewusst bestätigt werden');
        fireEvent.keyDown(screen.getByRole('dialog', { name: 'Nicht exportieren' }), { key: 'Escape' });
        expect(oeffne('Personalnummer')).toHaveTextContent('keine eigene Soll/Ist-Auswahl');
        expect(puts).toHaveLength(0);
    });

    it('zeigt einen Ladefehler der Einstellungen mit „Erneut laden“', async () => {
        vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => String(input) === '/api/mitarbeiter'
            ? json(MITARBEITER)
            : json({ message: 'Diese Einstellungen dürfen nur Admins ändern.' }, 403)));
        render(<DatevEinstellungenKarte />);
        expect(await screen.findByRole('alert')).toHaveTextContent('Diese Einstellungen dürfen nur Admins ändern.');
        expect(screen.getByRole('button', { name: 'Erneut laden' })).toBeVisible();
    });
});
