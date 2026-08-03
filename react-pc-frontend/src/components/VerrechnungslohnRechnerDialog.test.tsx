import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { VerrechnungslohnRechnerDialog } from './VerrechnungslohnRechnerDialog';

// Toast und Confirm haengen an Providern, die der Dialog im Test nicht hat.
const toastError = vi.fn();
vi.mock('./ui/toast', () => ({
    useToast: () => ({
        error: toastError,
        success: vi.fn(),
        warning: vi.fn(),
        info: vi.fn(),
    }),
}));
vi.mock('./ui/confirm-dialog', () => ({
    useConfirm: () => vi.fn().mockResolvedValue(true),
}));

const mockFetch = vi.fn();
global.fetch = mockFetch as unknown as typeof fetch;

const jahr = new Date().getFullYear();

/** Antwort mit Dummy-Daten (DSGVO): ein Mitarbeiter, eine Abteilung. */
const antwort = (interneQuoteProzent = 5) => ({
    jahr,
    modus: 'HOCHRECHNUNG',
    interneQuoteProzent,
    lohnzeilen: [
        {
            mitarbeiterId: 1,
            name: 'Max Mustermann',
            istGeschaeftsfuehrer: false,
            beschaeftigungsart: 'REGULAER',
            bruttoJahr: 50000,
            agAnteilSv: 10000,
            bgBeitrag: 1000,
            geldwerterVorteilJahr: 0,
            gesamtkosten: 61000,
            quelle: 'STUNDENLOHN_HOCHRECHNUNG',
            bruttoIstDefault: false,
        },
    ],
    stundenzeilen: [
        {
            mitarbeiterId: 1,
            name: 'Max Mustermann',
            istGeschaeftsfuehrer: false,
            sollstunden: 2080,
            urlaubsstunden: 200,
            krankheitsstunden: 64,
            interneStunden: 104,
            feiertagsstunden: 96,
            verkaeuflicheStunden: 1712,
            urlaubIstDefault: false,
            krankheitIstDefault: false,
            interneIstDefault: true,
            sollIstDefault: false,
        },
    ],
    kostenstellen: [],
    abteilungen: [{ abteilungId: 10, name: 'Schweisserei', aufschlagEuro: 0 }],
    datenLuecken: [],
    lohnsummeGesamt: 61000,
    verkaeuflicheStundenGesamt: 1712,
    gemeinkostenGesamt: 0,
    selbstkostenProStunde: 35.63,
});

const antwortOk = (body: unknown) => ({ ok: true, status: 200, json: async () => body });

const aufschlagFeld = () =>
    screen.getByLabelText('Aufschlag oder Abschlag für Schweisserei in Euro') as HTMLInputElement;

describe('VerrechnungslohnRechnerDialog', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        mockFetch.mockResolvedValue(antwortOk(antwort()));
    });

    it('macht aus einem Betrag nicht das Hundertfache, nur weil man reinklickt', async () => {
        // Der eigentliche Bug: Feld zeigte "1234.56", der Parser entfernte den
        // Punkt als Tausendertrenner -> 123456. Ohne jede Nutzereingabe.
        render(<VerrechnungslohnRechnerDialog open onClose={vi.fn()} />);
        await waitFor(() => expect(aufschlagFeld()).toBeTruthy());

        const feld = aufschlagFeld();
        fireEvent.change(feld, { target: { value: '1.234,56' } });
        fireEvent.blur(feld);
        await waitFor(() => expect(aufschlagFeld().value).toBe('1.234,56'));

        // Jetzt nur noch fokussieren und wieder verlassen - nichts tippen.
        fireEvent.focus(aufschlagFeld());
        fireEvent.blur(aufschlagFeld());

        await waitFor(() => expect(aufschlagFeld().value).toBe('1.234,56'));
        expect(aufschlagFeld().value).not.toBe('123.456,00');
    });

    it('laesst einen Abschlag mit Minuszeichen zu', async () => {
        render(<VerrechnungslohnRechnerDialog open onClose={vi.fn()} />);
        await waitFor(() => expect(aufschlagFeld()).toBeTruthy());

        const feld = aufschlagFeld();
        fireEvent.change(feld, { target: { value: '-5,00' } });
        fireEvent.blur(feld);

        await waitFor(() => expect(aufschlagFeld().value).toBe('-5,00'));
    });

    it('sperrt das Uebernehmen, wenn der Abschlag den Stundensatz aufzehrt', async () => {
        render(<VerrechnungslohnRechnerDialog open onClose={vi.fn()} />);
        await waitFor(() => expect(aufschlagFeld()).toBeTruthy());

        const feld = aufschlagFeld();
        fireEvent.change(feld, { target: { value: '-9.999,00' } });
        fireEvent.blur(feld);

        await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy());
        expect(screen.getByRole('alert').textContent).toContain('Schweisserei');

        const uebernehmen = screen
            .getAllByRole('button')
            .find((b) => b.textContent?.includes('übernehmen')) as HTMLButtonElement;
        expect(uebernehmen.disabled).toBe(true);
    });

    it('behaelt eine noch nicht uebernommene Prozent-Eingabe', async () => {
        // Regression: die Server-Antwort setzte den Regler stumm zurueck,
        // waehrend der Nutzer schon eine neue Zahl drin stehen hatte.
        render(<VerrechnungslohnRechnerDialog open onClose={vi.fn()} />);
        const feld = (await screen.findByLabelText('Interne Stunden?')) as HTMLInputElement;
        await waitFor(() => expect(feld.value).toBe('5'));

        fireEvent.change(feld, { target: { value: '30' } });
        expect(feld.value).toBe('30');

        // Erneute Antwort des Servers (weiterhin 5 %) darf die 30 nicht ueberschreiben.
        mockFetch.mockResolvedValue(antwortOk(antwort(5)));
        await waitFor(() => {
            expect(
                (screen.getByLabelText('Interne Stunden?') as HTMLInputElement).value
            ).toBe('30');
        });
    });

    it('fragt den Server nur auf Knopfdruck neu, nicht bei jedem Tastendruck', async () => {
        render(<VerrechnungslohnRechnerDialog open onClose={vi.fn()} />);
        const feld = (await screen.findByLabelText('Interne Stunden?')) as HTMLInputElement;
        await waitFor(() => expect(mockFetch).toHaveBeenCalledTimes(1));

        fireEvent.change(feld, { target: { value: '1' } });
        fireEvent.change(feld, { target: { value: '15' } });
        fireEvent.change(feld, { target: { value: '25' } });

        expect(mockFetch).toHaveBeenCalledTimes(1);

        const neuRechnen = screen
            .getAllByRole('button')
            .find((b) => b.textContent?.includes('Neu rechnen')) as HTMLButtonElement;
        fireEvent.click(neuRechnen);

        await waitFor(() => expect(mockFetch).toHaveBeenCalledTimes(2));
        expect(String(mockFetch.mock.calls[1][0])).toContain('internProzent=25');
    });

    it('zeigt die Klartext-Meldung des Servers statt einer Statusnummer', async () => {
        render(<VerrechnungslohnRechnerDialog open onClose={vi.fn()} />);
        await waitFor(() => expect(aufschlagFeld()).toBeTruthy());

        mockFetch.mockResolvedValue({
            ok: false,
            status: 400,
            json: async () => ({
                message: 'Der Abschlag fuer die Abteilung "Schweisserei" ist zu gross.',
            }),
        });

        const uebernehmen = screen
            .getAllByRole('button')
            .find((b) => b.textContent?.includes('übernehmen')) as HTMLButtonElement;
        fireEvent.click(uebernehmen);

        await waitFor(() => expect(toastError).toHaveBeenCalled());
        expect(toastError.mock.calls[0][0]).toContain('Schweisserei');
    });
});
