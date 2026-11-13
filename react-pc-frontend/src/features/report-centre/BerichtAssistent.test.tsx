import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { BerichtAssistent } from './BerichtAssistent';

const starteDownload = vi.hoisted(() => vi.fn());
vi.mock('../../lib/herunterladen', () => ({ starteDownload }));

const ARBEITSZEIT = { montagStunden: 8 };
const ZEITKONTEN = [
    { mitarbeiterId: 1, mitarbeiterName: 'Max Mustermann', fuehrtZeitkonto: true, istGeschaeftsfuehrer: false, aktuell: { arbeitszeit: ARBEITSZEIT } },
    { mitarbeiterId: 2, mitarbeiterName: 'Erika Musterfrau', fuehrtZeitkonto: true, istGeschaeftsfuehrer: false, aktuell: { arbeitszeit: ARBEITSZEIT } },
    { mitarbeiterId: 3, mitarbeiterName: 'Chef Mustermann', fuehrtZeitkonto: true, istGeschaeftsfuehrer: true, aktuell: { arbeitszeit: ARBEITSZEIT } },
];

const zeile = (id: number, name: string, teil: Record<string, unknown> = {}) => ({
    mitarbeiterId: id, name, tagessoll: 8, arbeitsstunden: 152, urlaubStunden: 16, feiertagStunden: 8,
    krankheitStunden: 0, fortbildungStunden: 0, stundenkontoStand: 12.5, resturlaubTage: 7.5, festgeschrieben: true, ...teil,
});

let stundenart: 'SOLL' | 'IST' = 'SOLL';
const vorschau = () => ({
    jahr: 2026, monat: 10, stundenart,
    zeilen: [zeile(1, 'Max Mustermann'), zeile(2, 'Erika Musterfrau', { stundenkontoStand: -3.5, festgeschrieben: false })],
    alleFestgeschrieben: false,
});

function antwort(status: number, inhalt: unknown, kopf: Record<string, string> = {}) {
    return Promise.resolve({
        ok: status < 300, status, headers: new Headers(kopf),
        json: () => Promise.resolve(inhalt),
        blob: () => Promise.resolve(new Blob(['%PDF-1.7'], { type: 'application/pdf' })),
    });
}

let vorschauStatus = 200;
let zeitkonten: unknown[] = ZEITKONTEN;
const fetchMock = vi.fn((url: string) => {
    if (url === '/api/zeitverwaltung/zeitkonten') return antwort(200, zeitkonten);
    if (url.startsWith('/api/zeitverwaltung/berichte/steuerberater?')) {
        return vorschauStatus === 200 ? antwort(200, vorschau()) : antwort(vorschauStatus, { status: vorschauStatus, message: 'nein' });
    }
    if (url.startsWith('/api/zeitverwaltung/berichte/steuerberater/pdf')) {
        return antwort(200, null, { 'Content-Disposition': 'attachment; filename="Stundenaufstellung_2026-10.pdf"' });
    }
    if (url.startsWith('/api/zeitverwaltung/berichte/mitarbeiter/pdf')) {
        return antwort(200, null, { 'Content-Disposition': 'attachment; filename="Zeitbericht_2026-10-01_bis_2026-10-31.pdf"' });
    }
    return antwort(404, null);
});

beforeEach(() => {
    vorschauStatus = 200;
    stundenart = 'SOLL';
    zeitkonten = ZEITKONTEN;
    fetchMock.mockClear();
    starteDownload.mockClear();
    global.fetch = fetchMock as unknown as typeof fetch;
});
afterEach(() => vi.clearAllMocks());

function starte() {
    const onClose = vi.fn();
    const onPdfErstellt = vi.fn(() => 'blob:bericht');
    render(
        <ToastProvider>
            <BerichtAssistent offen onClose={onClose} onPdfErstellt={onPdfErstellt} heute={new Date(2026, 10, 5)} />
        </ToastProvider>,
    );
    return { onClose, onPdfErstellt, dialog: screen.getByRole('dialog', { name: 'Neuer Bericht' }) };
}

const weiter = () => fireEvent.click(screen.getByRole('button', { name: 'Weiter' }));

describe('BerichtAssistent', () => {
    it('führt vom Steuerberater-Bericht bis zur Vorschau-Tabelle', async () => {
        starte();
        expect(screen.getByText('Was möchtest du machen?')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Weiter' })).toBeDisabled();
        fireEvent.click(screen.getByRole('button', { name: /An Steuerberater schicken/ }));
        weiter();

        expect(screen.getByRole('combobox', { name: 'Monat' })).toHaveTextContent('Oktober');
        expect(await screen.findByRole('checkbox', { name: 'Max Mustermann' })).toBeChecked();
        expect(screen.queryByRole('checkbox', { name: 'Chef Mustermann' })).not.toBeInTheDocument();
        weiter();

        const tabelle = await screen.findByRole('table');
        // Alle gewählt: keine IDs (der Server nimmt alle relevanten), keine Stundenart (Admin-Einstellung).
        expect(fetchMock).toHaveBeenCalledWith('/api/zeitverwaltung/berichte/steuerberater?jahr=2026&monat=10', expect.anything());
        for (const spalte of ['Tagessoll', 'Urlaub', 'Feiertage', 'Krankheit', 'Fortbildung', 'Monat']) {
            expect(within(tabelle).getByRole('columnheader', { name: spalte })).toBeInTheDocument();
        }
        // Keine Gesamtstunden: weder Sollstunden je Mitarbeiter noch eine Summenzeile.
        expect(within(tabelle).queryByRole('columnheader', { name: 'Soll' })).not.toBeInTheDocument();
        expect(within(tabelle).queryByRole('row', { name: /Summe/ })).not.toBeInTheDocument();
        expect(tabelle.querySelector('tfoot')).toBeNull();
        const max = within(tabelle).getByRole('row', { name: /Max Mustermann/ });
        expect(max).toHaveTextContent('152,0');
        expect(max).not.toHaveTextContent('176,0');
        const erika = within(tabelle).getByRole('row', { name: /Erika Musterfrau/ });
        expect(erika).toHaveTextContent('−3,5');
        expect(erika).toHaveTextContent('noch offen');
        expect(screen.getByText('Für 1 Mitarbeiter ist der Monat noch nicht abgeschlossen – Werte können sich noch ändern.')).toBeInTheDocument();
        expect(screen.getByText('Sollstunden')).toBeInTheDocument();
        expect(screen.getByText(/Übermittelt werden/)).toBeInTheDocument();
        expect(screen.getByText('Ein Admin kann das unter Einstellungen → Zeiterfassung ändern.')).toBeInTheDocument();
        expect(within(tabelle).getByRole('columnheader', { name: /Arbeitsstunden \(Soll\)/ })).toBeInTheDocument();
        for (const titel of ['Arbeitsstunden (Soll)', 'Stundenkonto', 'Resturlaub', 'Monat noch nicht abgeschlossen']) {
            expect(screen.getByRole('button', { name: `Mehr zu: ${titel}` })).toBeInTheDocument();
        }
        expect(screen.getByRole('button', { name: 'An Steuerberater senden' })).toBeEnabled();
        expect(screen.getByRole('button', { name: 'PDF herunterladen' })).toBeEnabled();
    });

    it('zeigt bei Iststunden die passende Spalte und Erklärung', async () => {
        stundenart = 'IST';
        starte();
        fireEvent.click(screen.getByRole('button', { name: /An Steuerberater schicken/ }));
        weiter();
        await screen.findByRole('checkbox', { name: 'Max Mustermann' });
        weiter();
        const tabelle = await screen.findByRole('table');
        expect(screen.getByText('Iststunden')).toBeInTheDocument();
        expect(within(tabelle).getByRole('columnheader', { name: /Arbeitsstunden \(Ist\)/ })).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Mehr zu: Arbeitsstunden (Ist)' }));
        expect(screen.getByRole('dialog', { name: 'Arbeitsstunden (Ist)' })).toHaveTextContent('wirklich gebuchte Arbeitszeit');

        // Das PDF nimmt genau die Stundenart der Vorschau.
        fireEvent.click(screen.getByRole('button', { name: 'PDF herunterladen' }));
        await waitFor(() => expect(starteDownload).toHaveBeenCalledWith('blob:bericht', 'Stundenaufstellung_2026-10.pdf'));
        const pdfUrl = new URL(fetchMock.mock.calls.map(([url]) => url).find(url => url.includes('/steuerberater/pdf'))!, 'http://x');
        expect(pdfUrl.searchParams.get('stundenart')).toBe('IST');
        expect(pdfUrl.searchParams.has('mitarbeiterIds')).toBe(false);
    });

    it('erlaubt beim Steuerberater „alle“ über 50, aber keine Teilauswahl über 50', async () => {
        zeitkonten = Array.from({ length: 52 }, (_, i) => ({
            ...ZEITKONTEN[0], mitarbeiterId: 100 + i, mitarbeiterName: i === 0 ? 'Max Mustermann' : `Erika Musterfrau ${i}`,
        }));
        starte();
        fireEvent.click(screen.getByRole('button', { name: /An Steuerberater schicken/ }));
        weiter();
        fireEvent.click(await screen.findByRole('checkbox', { name: 'Max Mustermann' }));
        weiter();
        expect(within(screen.getByRole('dialog', { name: 'Neuer Bericht' })).getByRole('alert'))
            .toHaveTextContent('Bitte höchstens 50 Mitarbeiter auswählen oder alle.');
        fireEvent.click(screen.getByRole('button', { name: 'Alle' }));
        weiter();
        await screen.findByRole('table');
        expect(fetchMock).toHaveBeenCalledWith('/api/zeitverwaltung/berichte/steuerberater?jahr=2026&monat=10', expect.anything());
    });

    it('schickt beim Steuerberater nur bei Teilauswahl die IDs mit', async () => {
        starte();
        fireEvent.click(screen.getByRole('button', { name: /An Steuerberater schicken/ }));
        weiter();
        fireEvent.click(await screen.findByRole('checkbox', { name: 'Erika Musterfrau' }));
        weiter();
        await screen.findByRole('table');
        expect(fetchMock).toHaveBeenCalledWith('/api/zeitverwaltung/berichte/steuerberater?jahr=2026&monat=10&mitarbeiterIds=1', expect.anything());
    });

    it('blendet Stundenkonto und Resturlaub aus, wenn die Schalter aus sind', async () => {
        starte();
        fireEvent.click(screen.getByRole('button', { name: /An Steuerberater schicken/ }));
        weiter();
        await screen.findByRole('checkbox', { name: 'Max Mustermann' });
        fireEvent.click(screen.getByRole('switch', { name: 'Stundenkonto zeigen' }));
        fireEvent.click(screen.getByRole('switch', { name: 'Resturlaub zeigen' }));
        weiter();
        const tabelle = await screen.findByRole('table');
        expect(within(tabelle).queryByRole('columnheader', { name: /Stundenkonto/ })).not.toBeInTheDocument();
        expect(within(tabelle).queryByRole('columnheader', { name: /Resturlaub/ })).not.toBeInTheDocument();
    });

    it('sagt, wenn das Recht für die Zeiterfassung fehlt', async () => {
        vorschauStatus = 403;
        starte();
        fireEvent.click(screen.getByRole('button', { name: /An Steuerberater schicken/ }));
        weiter();
        await screen.findByRole('checkbox', { name: 'Max Mustermann' });
        weiter();
        const meldungen = await screen.findAllByText('Dafür fehlt dir das Recht, die Zeiterfassung zu sehen.');
        expect(meldungen.length).toBeGreaterThan(0);
        expect(screen.getByRole('button', { name: 'An Steuerberater senden' })).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Nochmal versuchen' })).toBeInTheDocument();
    });

    it('prüft beim Mitarbeiter-Bericht die Auswahl und erstellt dann das PDF', async () => {
        const { onPdfErstellt } = starte();
        fireEvent.click(screen.getByRole('button', { name: /Bericht für Mitarbeiter/ }));
        weiter();
        await screen.findByRole('checkbox', { name: 'Max Mustermann' });
        weiter();
        expect(within(screen.getByRole('dialog', { name: 'Neuer Bericht' })).getByRole('alert'))
            .toHaveTextContent('Bitte mindestens einen Mitarbeiter wählen.');
        expect(screen.queryByRole('button', { name: 'PDF erstellen' })).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole('checkbox', { name: 'Max Mustermann' }));
        weiter();
        expect(screen.getByText('Oktober 2026')).toBeInTheDocument();
        expect(screen.getByText('Max Mustermann')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'PDF erstellen' }));

        await waitFor(() => expect(onPdfErstellt).toHaveBeenCalled());
        const url = new URL(fetchMock.mock.calls.at(-1)![0], 'http://x');
        expect(Object.fromEntries(url.searchParams)).toEqual({
            mitarbeiterIds: '1', von: '2026-10-01', bis: '2026-10-31', mitTagesliste: 'true', mitUnterschrift: 'true',
        });
        expect(onPdfErstellt).toHaveBeenCalledWith(expect.objectContaining({ dateiname: 'Zeitbericht_2026-10-01_bis_2026-10-31.pdf' }), 'Bericht für Mitarbeiter');
        expect(starteDownload).toHaveBeenCalledWith('blob:bericht', 'Zeitbericht_2026-10-01_bis_2026-10-31.pdf');
    });

    it('geht mit „Zurück“ einen Schritt zurück und behält die Wahl', async () => {
        starte();
        fireEvent.click(screen.getByRole('button', { name: /Bericht für Mitarbeiter/ }));
        weiter();
        await screen.findByRole('checkbox', { name: 'Max Mustermann' });
        fireEvent.click(screen.getByRole('button', { name: 'Zurück' }));
        expect(screen.getByRole('button', { name: /Bericht für Mitarbeiter/ })).toHaveAttribute('aria-pressed', 'true');
        expect(screen.getByRole('listitem', { current: 'step' })).toHaveTextContent('Bericht wählen');
    });

    it('schließt mit Escape', () => {
        const { onClose, dialog } = starte();
        fireEvent.keyDown(dialog, { key: 'Escape' });
        expect(onClose).toHaveBeenCalled();
    });
});
