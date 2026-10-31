/**
 * Dialog „Aus Lieferanten-Shop“: Schritte, Aufschlag, Einfügen, Artikelstamm.
 * DSGVO: nur Dummy-Daten.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { ToastProvider } from '../ui/toast';
import { ConfirmProvider } from '../ui/confirm-dialog';
import type { IdsLieferant, IdsPosition, IdsVorgang } from './api';
import type { ShopBesuchZustand } from './useIdsShopBesuch';

const besuch: ShopBesuchZustand & { starte: ReturnType<typeof vi.fn>; abbrechen: ReturnType<typeof vi.fn> } = {
    phase: 'bereit', vorgang: null, fehlertext: null, starte: vi.fn(), abbrechen: vi.fn(),
};
vi.mock('./useIdsShopBesuch', () => ({ useIdsShopBesuch: () => besuch }));

const uebernimmIdsArtikelstamm = vi.fn();
vi.mock('./api', async (original) => ({
    ...(await original<typeof import('./api')>()),
    uebernimmIdsArtikelstamm: (...a: unknown[]) => uebernimmIdsArtikelstamm(...a),
}));

/** Das Anlegen-Fenster hat eigene Tests – hier zählt nur, was der Dialog hineingibt und was er mit dem Ergebnis macht. */
const anlegenFenster = vi.fn();
vi.mock('../CreateArticleModal', () => ({
    CreateArticleModal: (props: { vorbelegung?: unknown; onClose: () => void; onSave: (a: { id: number; artikelnummer: string }) => void }) => {
        anlegenFenster(props.vorbelegung);
        return (
            <div role="dialog" aria-label="Neuen Artikel anlegen">
                <button type="button" onClick={props.onClose}>Fenster abbrechen</button>
                <button type="button" onClick={() => props.onSave({ id: 99, artikelnummer: 'A-000123' })}>Fenster speichern</button>
            </div>
        );
    },
}));

const auth = { isAdmin: false };
vi.mock('../../auth/AuthContext', () => ({ useOptionalAuth: () => auth }));

import { ShopWarenkorbDialog } from './ShopWarenkorbDialog';
import { SHOP_ANLEGEN_HINWEIS } from './warenkorbPositionen';

const MUSTER: IdsLieferant = { lieferantId: 7, lieferantName: 'Muster Großhandel', passwortGesetzt: true };

const pos = (teil: Partial<IdsPosition>): IdsPosition => ({
    index: 0, artikelnummer: '0892 100', bezeichnung: 'Silikon-Kartusche', langtext: null, menge: 5, einheit: 'Stk',
    einkaufspreis: 4, hinweis: null, aufschlagProzent: 25, aufschlagQuelle: 'ARTIKEL', stamm: null, ...teil,
});

const VORGANG: IdsVorgang = {
    vorgangId: 42, lieferantId: 7, lieferantName: 'Muster Großhandel', status: 'EMPFANGEN', fehlertext: null,
    mitBestellung: false, empfangenAm: '2026-10-31T10:00:00Z', standardAufschlagProzent: 20,
    positionen: [
        pos({ index: 0, stamm: { artikelId: 11, bezeichnung: 'Silikon', aktuellerPreis: 3.5 } }),
        pos({ index: 1, artikelnummer: '0903 200', bezeichnung: 'Injektionsmörtel', menge: 2, einkaufspreis: 10, aufschlagProzent: 10, aufschlagQuelle: 'KATEGORIE', hinweis: 'Nur solange Vorrat reicht' }),
        pos({ index: 2, artikelnummer: '0157 610', bezeichnung: 'Schraube 6x100', menge: 100, einkaufspreis: 0.2, aufschlagProzent: null, aufschlagQuelle: 'KEINER', stamm: { artikelId: 12, bezeichnung: 'Schraube', aktuellerPreis: 0.2 } }),
    ],
};

function zeige(lieferanten: IdsLieferant[] = [MUSTER]) {
    const onEinfuegen = vi.fn();
    const onSchliessen = vi.fn();
    const ui = (offen = true) => (
        <ToastProvider>
            <ConfirmProvider>
                <ShopWarenkorbDialog offen={offen} lieferanten={lieferanten} onEinfuegen={onEinfuegen} onSchliessen={onSchliessen} />
            </ConfirmProvider>
        </ToastProvider>
    );
    const r = render(ui());
    return { ...r, onEinfuegen, onSchliessen, neu: () => r.rerender(ui()), zu: () => r.rerender(ui(false)) };
}

const VORGANG_2: IdsVorgang = {
    ...VORGANG, vorgangId: 43, empfangenAm: '2026-10-31T10:20:00Z',
    positionen: [
        // Derselbe Artikel wie Zeile 0 des ersten Besuchs – kommt als eigene Zeile dazu.
        pos({ index: 0, aufschlagProzent: 30, stamm: null }),
        pos({ index: 1, artikelnummer: '0681 300', bezeichnung: 'Dübel 8 mm', menge: 50, einkaufspreis: 0.1, aufschlagProzent: 40,
            stamm: { artikelId: 13, bezeichnung: 'Dübel', aktuellerPreis: 0.08 } }),
    ],
};

const bezeichnungen = () => screen.getAllByRole('textbox', { name: /^Bezeichnung von Artikel/ }).map((e) => (e as HTMLInputElement).value);

/** Bis „Preise prüfen“: Shop öffnen, dann kommt der Warenkorb an. */
function bisPreise(vorgang: IdsVorgang = VORGANG) {
    const t = zeige();
    fireEvent.click(screen.getByRole('button', { name: /Shop öffnen/ }));
    besuch.phase = 'empfangen';
    besuch.vorgang = vorgang;
    t.neu();
    return t;
}

beforeEach(() => {
    Object.assign(besuch, { phase: 'bereit', vorgang: null, fehlertext: null });
    besuch.starte.mockReset();
    besuch.abbrechen.mockReset();
    uebernimmIdsArtikelstamm.mockReset();
    anlegenFenster.mockReset();
    auth.isAdmin = false;
});
afterEach(() => vi.clearAllMocks());

describe('ShopWarenkorbDialog – Schritt 1 und 2', () => {
    it('wählt einen einzelnen Shop vor und öffnet ihn', () => {
        zeige();
        expect(screen.getByRole('radio', { name: /Muster Großhandel/ })).toHaveAttribute('aria-checked', 'true');
        expect(screen.getByRole('listitem', { current: 'step' })).toHaveTextContent('Shop wählen');
        fireEvent.click(screen.getByRole('button', { name: /Shop öffnen/ }));
        expect(besuch.starte).toHaveBeenCalledWith(7);
    });

    it('verlangt bei mehreren Shops erst eine Wahl', () => {
        zeige([MUSTER, { lieferantId: 8, lieferantName: 'Beispiel AG', passwortGesetzt: true }]);
        const oeffnen = screen.getByRole('button', { name: /Shop öffnen/ });
        expect(oeffnen).toBeDisabled();
        expect(oeffnen).toHaveAttribute('title', 'Bitte zuerst einen Lieferanten wählen');
        fireEvent.click(screen.getByRole('radio', { name: /Beispiel AG/ }));
        fireEvent.click(oeffnen);
        expect(besuch.starte).toHaveBeenCalledWith(8);
    });

    it('zeigt beim Warten, was zu tun ist, und bei Fehlern „Nochmal versuchen“', () => {
        const t = zeige();
        fireEvent.click(screen.getByRole('button', { name: /Shop öffnen/ }));
        besuch.phase = 'wartet';
        t.neu();
        expect(screen.getByText(/ist in einem neuen Tab geöffnet/)).toBeInTheDocument();
        expect(screen.getByText(/Warenkorb übernehmen/)).toBeInTheDocument();
        expect(screen.getByRole('listitem', { current: 'step' })).toHaveTextContent('Im Shop auswählen');
        fireEvent.click(screen.getByRole('button', { name: /Shop erneut öffnen/ }));
        expect(besuch.starte).toHaveBeenCalledTimes(2);

        besuch.phase = 'fehler';
        besuch.fehlertext = 'Der Browser hat das neue Fenster blockiert – bitte Pop-ups für dieses Programm erlauben.';
        t.neu();
        expect(screen.getByRole('alert')).toHaveTextContent(/Pop-ups/);
        fireEvent.click(screen.getByRole('button', { name: /Nochmal versuchen/ }));
        expect(besuch.starte).toHaveBeenCalledTimes(3);
        fireEvent.click(screen.getByRole('button', { name: 'Zurück' }));
        expect(besuch.abbrechen).toHaveBeenCalled();
        expect(screen.getByRole('button', { name: /Shop öffnen/ })).toBeInTheDocument();
    });
});

describe('ShopWarenkorbDialog – Shop ohne Passwort', () => {
    const OHNE_PW: IdsLieferant = { lieferantId: 8, lieferantName: 'Beispiel AG', passwortGesetzt: false };

    it('warnt beim gewählten Lieferanten ohne Passwort, öffnen bleibt möglich', () => {
        zeige([MUSTER, OHNE_PW]);
        expect(screen.queryByRole('note')).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole('radio', { name: /Beispiel AG/ }));
        expect(screen.getByRole('note')).toHaveTextContent(
            'Für Beispiel AG ist kein Passwort gespeichert – der Shop wird die Anmeldung vermutlich ablehnen („Login failed“).');
        expect(screen.queryByRole('link', { name: /Zugang in den Einstellungen ergänzen/ })).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: /Shop öffnen/ }));
        expect(besuch.starte).toHaveBeenCalledWith(8);
    });

    it('verschwindet beim Lieferanten mit Passwort', () => {
        zeige([MUSTER, OHNE_PW]);
        fireEvent.click(screen.getByRole('radio', { name: /Beispiel AG/ }));
        fireEvent.click(screen.getByRole('radio', { name: /Muster Großhandel/ }));
        expect(screen.queryByRole('note')).not.toBeInTheDocument();
    });

    it('zeigt Admins den Weg zur Einrichtung (neuer Tab, Dokument bleibt offen)', () => {
        auth.isAdmin = true;
        zeige([OHNE_PW]);
        const link = screen.getByRole('link', { name: /Zugang in den Einstellungen ergänzen/ });
        expect(link).toHaveAttribute('href', '/einstellungen#ids');
        expect(link).toHaveAttribute('target', '_blank');
        expect(link).toHaveAttribute('rel', expect.stringContaining('noopener'));
    });
});

describe('ShopWarenkorbDialog – von außen geschlossen', () => {
    it('hört auf zu warten, wenn der Dialog zugemacht wird (z. B. Dokument gesperrt)', () => {
        const t = zeige();
        fireEvent.click(screen.getByRole('button', { name: /Shop öffnen/ }));
        besuch.phase = 'wartet';
        t.neu();
        besuch.abbrechen.mockClear();
        t.zu();
        expect(besuch.abbrechen).toHaveBeenCalled();
    });
});

describe('ShopWarenkorbDialog – Preise prüfen', () => {
    it('zeigt Zeilen, Shop-Hinweise, Aufschlag-Herkunft und Summen', () => {
        bisPreise();
        expect(screen.getByRole('listitem', { current: 'step' })).toHaveTextContent('Preise prüfen');
        expect(screen.getByLabelText('Bezeichnung von Artikel 0892 100')).toHaveValue('Silikon-Kartusche');
        expect(screen.getByText('Nur solange Vorrat reicht')).toBeInTheDocument();
        expect(screen.getByText('aus Artikelstamm')).toBeInTheDocument();
        expect(screen.getByText('aus Kategorie')).toBeInTheDocument();
        expect(screen.getByText('kein Aufschlag hinterlegt')).toBeInTheDocument();
        expect(screen.getByLabelText('Aufschlag für alle (%)')).toHaveValue('20');
        // EK 20+20+20 = 60; VK 25+22+20 = 67
        const summen = screen.getByLabelText('Summen');
        expect(within(summen).getByText('60,00 €')).toBeInTheDocument();
        expect(within(summen).getByText('67,00 €')).toBeInTheDocument();
        expect(within(summen).getByText('7,00 €')).toBeInTheDocument();
        expect(screen.queryByText(/bereits bestellt/)).not.toBeInTheDocument();
    });

    it('warnt deutlich, wenn der Warenkorb schon bestellt wurde, und markiert fehlende Preise', () => {
        bisPreise({ ...VORGANG, mitBestellung: true, positionen: [pos({ einkaufspreis: null })] });
        expect(screen.getByText(/Dieser Warenkorb wurde im Shop bereits bestellt/)).toBeInTheDocument();
        expect(screen.getByText(/Nicht noch einmal bestellen/)).toBeInTheDocument();
        expect(screen.getByText('Kein Preis vom Shop – bitte Einkaufspreis eintragen')).toBeInTheDocument();
    });

    it('verweigert das Einfügen ohne Einkaufspreis mit verständlicher Meldung', async () => {
        const { onEinfuegen } = bisPreise({ ...VORGANG, positionen: [pos({ einkaufspreis: null })] });
        fireEvent.click(screen.getByRole('button', { name: /In Dokument einfügen/ }));
        expect(await screen.findByText(/Bitte den Einkaufspreis bei „Silikon-Kartusche“ eingeben/)).toBeInTheDocument();
        expect(onEinfuegen).not.toHaveBeenCalled();
    });

    it('setzt den Aufschlag für alle und fügt jeden Artikel als eigene Position ein', async () => {
        const { onEinfuegen } = bisPreise();
        fireEvent.change(screen.getByLabelText('Aufschlag für alle (%)'), { target: { value: '50' } });
        fireEvent.click(screen.getByRole('button', { name: 'Auf alle anwenden' }));
        expect(screen.getAllByText('für alle gesetzt')).toHaveLength(3);
        expect(screen.getByLabelText('Aufschlag von „Injektionsmörtel“')).toHaveValue('50');
        // Schraube aus der Liste nehmen
        fireEvent.click(screen.getAllByRole('button', { name: 'Artikel aus der Liste entfernen' })[2]);
        fireEvent.click(screen.getByRole('button', { name: /In Dokument einfügen/ }));

        expect(onEinfuegen).toHaveBeenCalledTimes(1);
        const bloecke = onEinfuegen.mock.calls[0][0];
        expect(bloecke).toHaveLength(2);
        expect(bloecke[0]).toMatchObject({ type: 'SERVICE', title: 'Silikon-Kartusche', quantity: 5, unit: 'Stk', price: 6, artikelId: 11,
            description: '<p>Silikon-Kartusche</p>' });
        expect(bloecke[1]).toMatchObject({ title: 'Injektionsmörtel', quantity: 2, price: 15 });
        expect(bloecke[1].artikelId).toBeUndefined();
        expect(await screen.findByText('2 Positionen eingefügt.')).toBeInTheDocument();
    });

    it('fasst bei „Alles in einer Position“ zusammen und zeigt die Vorschau', () => {
        const { onEinfuegen } = bisPreise();
        fireEvent.click(screen.getByRole('radio', { name: 'Alles in einer Position' }));
        fireEvent.change(screen.getByLabelText('Überschrift'), { target: { value: 'Material Bad' } });
        const vorschau = screen.getByLabelText('Vorschau der Position');
        expect(vorschau).toHaveTextContent('Material Bad · 1 pauschal · 67,00 €');
        expect(vorschau).toHaveTextContent('5× Silikon-Kartusche, 2× Injektionsmörtel, 100× Schraube 6x100');
        fireEvent.click(screen.getByRole('button', { name: /In Dokument einfügen/ }));
        expect(onEinfuegen.mock.calls[0][0]).toEqual([expect.objectContaining({
            title: 'Material Bad', quantity: 1, unit: 'pauschal', price: 67,
            description: '<p>5× Silikon-Kartusche, 2× Injektionsmörtel, 100× Schraube 6x100</p>',
        })]);
    });

    it('fragt vor dem Verwerfen des Warenkorbs nach', async () => {
        const { onSchliessen } = bisPreise();
        fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }));
        fireEvent.click(await screen.findByRole('button', { name: 'Weiter prüfen' }));
        await waitFor(() => expect(screen.queryByRole('button', { name: 'Weiter prüfen' })).not.toBeInTheDocument());
        expect(onSchliessen).not.toHaveBeenCalled();
        fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }));
        fireEvent.click(await screen.findByRole('button', { name: 'Verwerfen' }));
        await waitFor(() => expect(onSchliessen).toHaveBeenCalled());
        expect(besuch.abbrechen).toHaveBeenCalled();
    });
});

describe('ShopWarenkorbDialog – Artikelstamm', () => {
    function bisStamm() {
        const t = bisPreise();
        fireEvent.click(screen.getByRole('button', { name: /In Dokument einfügen/ }));
        return t;
    }

    it('wählt „Preis aktualisieren“ vor und bietet für unbekannte Artikel „Als Artikel anlegen…“', () => {
        bisStamm();
        expect(screen.getByRole('listitem', { current: 'step' })).toHaveTextContent('Artikelstamm');
        expect(screen.getByText(/3,50 €/)).toBeInTheDocument();
        expect(screen.getByRole('checkbox', { name: 'Preis aktualisieren' })).toBeChecked();
        expect(screen.getAllByRole('checkbox')).toHaveLength(1);
        expect(screen.getByText('Noch nicht im Artikelstamm')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Als Artikel anlegen… („Injektionsmörtel“)' })).toBeInTheDocument();
        expect(screen.getByText(/Preis unverändert/)).toBeInTheDocument();
    });

    it('erklärt per Info-Button, was „Preis aktualisieren“ und „Als Artikel anlegen…“ tun', () => {
        bisStamm();
        fireEvent.click(screen.getByRole('button', { name: 'Mehr zu: Artikelstamm aktualisieren' }));
        const info = screen.getByRole('dialog', { name: 'Artikelstamm aktualisieren' });
        expect(info).toHaveTextContent('der alte bleibt im Preisverlauf');
        expect(info).toHaveTextContent('öffnet das gewohnte Anlegen-Fenster, vorausgefüllt mit Bezeichnung, Lieferanten-Nummer, Preis und Einheit');
        expect(info).toHaveTextContent('Das Dokument ändert sich dadurch nicht mehr.');
    });

    it('„Als Artikel anlegen…“ öffnet das Anlegen-Fenster vorausgefüllt und zeigt danach die neue Nummer', async () => {
        const t = bisPreise();
        // Bezeichnung umformuliert, Preis vom Shop unverändert
        fireEvent.change(screen.getByLabelText('Bezeichnung von Artikel 0903 200'), { target: { value: 'Injektionsmörtel 300 ml' } });
        fireEvent.click(screen.getByRole('button', { name: /In Dokument einfügen/ }));
        fireEvent.click(screen.getByRole('button', { name: /^Als Artikel anlegen…/ }));

        expect(screen.getByRole('dialog', { name: 'Neuen Artikel anlegen' })).toBeInTheDocument();
        expect(anlegenFenster).toHaveBeenLastCalledWith({
            produktname: 'Injektionsmörtel 300 ml',
            externeArtikelnummer: '0903 200',
            lieferant: { id: 7, name: 'Muster Großhandel' },
            preis: 10,
            verrechnungseinheit: 'STUECK',
            preiseinheit: 'Stk',
            preisQuelle: 'IDS_SHOP',
            hinweis: SHOP_ANLEGEN_HINWEIS,
        });

        fireEvent.click(screen.getByRole('button', { name: 'Fenster speichern' }));
        expect(screen.queryByRole('dialog', { name: 'Neuen Artikel anlegen' })).not.toBeInTheDocument();
        expect(screen.getByText('Angelegt als A-000123')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /^Als Artikel anlegen…/ })).not.toBeInTheDocument();
        expect(uebernimmIdsArtikelstamm).not.toHaveBeenCalled();
        expect(t.onSchliessen).not.toHaveBeenCalled();
    });

    it('meldet einen geänderten Einkaufspreis als eigene Angabe (MANUELL)', () => {
        bisPreise();
        fireEvent.change(screen.getByLabelText('Einkaufspreis von „Injektionsmörtel“'), { target: { value: '12,5' } });
        fireEvent.click(screen.getByRole('button', { name: /In Dokument einfügen/ }));
        fireEvent.click(screen.getByRole('button', { name: /^Als Artikel anlegen…/ }));
        expect(anlegenFenster).toHaveBeenLastCalledWith(expect.objectContaining({ preis: 12.5, preisQuelle: 'MANUELL' }));
    });

    it('Abbrechen im Anlegen-Fenster ändert nichts', () => {
        bisStamm();
        fireEvent.click(screen.getByRole('button', { name: /^Als Artikel anlegen…/ }));
        fireEvent.click(screen.getByRole('button', { name: 'Fenster abbrechen' }));
        expect(screen.queryByRole('dialog', { name: 'Neuen Artikel anlegen' })).not.toBeInTheDocument();
        expect(screen.getByText('Noch nicht im Artikelstamm')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /^Als Artikel anlegen…/ })).toBeInTheDocument();
    });

    it('„Übernehmen“ sendet nur die Preis-Aktualisierungen und schließt', async () => {
        uebernimmIdsArtikelstamm.mockResolvedValue({ aktualisiert: 1, angelegt: 0, unveraendert: 1, artikelIds: {} });
        const { onSchliessen } = bisStamm();
        fireEvent.click(screen.getByRole('button', { name: /^Übernehmen/ }));
        await waitFor(() => expect(onSchliessen).toHaveBeenCalled());
        expect(uebernimmIdsArtikelstamm).toHaveBeenCalledWith(42, {
            aktualisieren: [{ index: 0, einkaufspreis: 4 }],
            neuAnlegen: [],
        });
        expect(await screen.findByText('1 Preis aktualisiert.')).toBeInTheDocument();
    });

    it('zeigt „Fertig“, wenn es nichts zu aktualisieren gibt', () => {
        const { onSchliessen } = bisPreise({ ...VORGANG, positionen: [VORGANG.positionen[1]] });
        fireEvent.click(screen.getByRole('button', { name: /In Dokument einfügen/ }));
        expect(screen.queryByRole('button', { name: /^Übernehmen/ })).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Fertig' }));
        expect(onSchliessen).toHaveBeenCalled();
    });

    it('zeigt Fehler und bleibt offen; „Überspringen“ schließt ohne Aufruf', async () => {
        uebernimmIdsArtikelstamm.mockRejectedValue(new Error('Der Artikelstamm ist gerade gesperrt.'));
        const { onSchliessen } = bisStamm();
        fireEvent.click(screen.getByRole('button', { name: /^Übernehmen/ }));
        expect(await screen.findByText('Der Artikelstamm ist gerade gesperrt.')).toBeInTheDocument();
        expect(onSchliessen).not.toHaveBeenCalled();
        fireEvent.click(screen.getByRole('button', { name: 'Überspringen' }));
        expect(onSchliessen).toHaveBeenCalled();
    });

    it('sperrt „Übernehmen“ ohne Haken mit Erklärung', () => {
        bisStamm();
        fireEvent.click(screen.getByRole('checkbox', { name: 'Preis aktualisieren' }));
        const knopf = screen.getByRole('button', { name: /^Übernehmen/ });
        expect(knopf).toBeDisabled();
        expect(knopf).toHaveAttribute('title', expect.stringMatching(/mindestens einen Haken bei „Preis aktualisieren“/));
    });
});

describe('ShopWarenkorbDialog – Artikel aus der Liste nehmen', () => {
    it('entfernt eine Zeile sofort, rechnet die Summen neu und holt sie mit „Rückgängig“ samt Änderungen zurück', async () => {
        bisPreise();
        fireEvent.change(screen.getByLabelText('Menge von „Injektionsmörtel“'), { target: { value: '3' } });
        fireEvent.click(screen.getAllByRole('button', { name: 'Artikel aus der Liste entfernen' })[1]);

        expect(bezeichnungen()).toEqual(['Silikon-Kartusche', 'Schraube 6x100']);
        const summen = screen.getByLabelText('Summen');
        // EK 20 + 20 = 40; VK 25 + 20 = 45
        expect(within(summen).getByText('40,00 €')).toBeInTheDocument();
        expect(within(summen).getByText('45,00 €')).toBeInTheDocument();
        expect(within(summen).getByText('5,00 €')).toBeInTheDocument();

        expect(await screen.findByText('„Injektionsmörtel“ aus der Liste entfernt.')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Rückgängig' }));
        expect(bezeichnungen()).toEqual(['Silikon-Kartusche', 'Injektionsmörtel', 'Schraube 6x100']);
        expect(screen.getByLabelText('Menge von „Injektionsmörtel“')).toHaveValue('3');
        // EK 20 + 30 + 20 = 70
        expect(within(screen.getByLabelText('Summen')).getByText('70,00 €')).toBeInTheDocument();
    });

    it('zeigt einen klaren Leerzustand und sperrt das Einfügen mit Begründung', () => {
        const { onEinfuegen } = bisPreise();
        for (let i = 0; i < 3; i++) fireEvent.click(screen.getAllByRole('button', { name: 'Artikel aus der Liste entfernen' })[0]);

        expect(screen.getByText('Keine Artikel mehr in der Liste')).toBeInTheDocument();
        expect(screen.queryByRole('table')).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Weitere Artikel aus dem Shop holen' })).toBeEnabled();
        const einfuegen = screen.getByRole('button', { name: /In Dokument einfügen/ });
        expect(einfuegen).toBeDisabled();
        expect(einfuegen).toHaveAttribute('title', expect.stringMatching(/Keine Artikel in der Liste/));
        expect(within(screen.getByLabelText('Summen')).getAllByText('0,00 €')).toHaveLength(3);
        fireEvent.click(einfuegen);
        expect(onEinfuegen).not.toHaveBeenCalled();
    });

    it('schließt ohne Rückfrage, wenn die Liste leer ist', () => {
        const { onSchliessen } = bisPreise();
        for (let i = 0; i < 3; i++) fireEvent.click(screen.getAllByRole('button', { name: 'Artikel aus der Liste entfernen' })[0]);
        fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }));
        expect(onSchliessen).toHaveBeenCalled();
    });
});

describe('ShopWarenkorbDialog – weitere Artikel aus dem Shop', () => {
    /** Startet den zweiten Besuch und lässt ihn bis „wartet“ laufen. */
    function zweiterBesuchWartet(t: ReturnType<typeof bisPreise>) {
        fireEvent.click(screen.getByRole('button', { name: 'Weitere Artikel aus dem Shop holen' }));
        besuch.phase = 'wartet';
        besuch.vorgang = null;
        t.neu();
    }

    function zweiterBesuchKommtAn(t: ReturnType<typeof bisPreise>, vorgang: IdsVorgang = VORGANG_2) {
        besuch.phase = 'empfangen';
        besuch.vorgang = vorgang;
        t.neu();
    }

    it('erklärt per Info-Button, was passiert', () => {
        bisPreise();
        fireEvent.click(screen.getByRole('button', { name: 'Mehr zu: Weitere Artikel holen' }));
        const info = screen.getByRole('dialog', { name: 'Weitere Artikel holen' });
        expect(info).toHaveTextContent('neuer Besuch, wieder 60 Minuten gültig');
        expect(info).toHaveTextContent('Was du oben schon geändert hast, bleibt erhalten. Im Shop wird nichts bestellt.');
    });

    it('öffnet den Shop beim selben Lieferanten, zeigt den Warte-Hinweis und hängt die neuen Artikel an', async () => {
        const t = bisPreise();
        fireEvent.change(screen.getByLabelText('Bezeichnung von Artikel 0903 200'), { target: { value: 'Injektionsmörtel 300 ml' } });
        fireEvent.change(screen.getByLabelText('Aufschlag von „Silikon-Kartusche“'), { target: { value: '50' } });
        zweiterBesuchWartet(t);

        expect(besuch.starte).toHaveBeenLastCalledWith(7);
        const hinweis = screen.getByRole('group', { name: 'Weiterer Shop-Besuch' });
        expect(hinweis).toHaveTextContent('Der Shop von Muster Großhandel ist offen – weitere Artikel dort zusammenstellen und „Warenkorb übernehmen“ klicken.');
        expect(screen.getByRole('button', { name: 'Weitere Artikel aus dem Shop holen' })).toBeDisabled();
        expect(screen.getByRole('button', { name: /In Dokument einfügen/ })).toBeDisabled();
        // Die Liste bleibt beim Warten sichtbar und bearbeitbar.
        expect(bezeichnungen()).toHaveLength(3);

        zweiterBesuchKommtAn(t);
        expect(bezeichnungen()).toEqual(['Silikon-Kartusche', 'Injektionsmörtel 300 ml', 'Schraube 6x100', 'Silikon-Kartusche', 'Dübel 8 mm']);
        const aufschlaege = screen.getAllByLabelText('Aufschlag von „Silikon-Kartusche“');
        expect(aufschlaege.map((e) => (e as HTMLInputElement).value)).toEqual(['50', '30']);
        expect(screen.queryByRole('group', { name: 'Weiterer Shop-Besuch' })).not.toBeInTheDocument();
        expect(await screen.findByText('2 weitere Artikel aus dem Shop übernommen.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /In Dokument einfügen/ })).toBeEnabled();
    });

    it('holt aus dem Leerzustand weitere Artikel', async () => {
        const t = bisPreise();
        for (let i = 0; i < 3; i++) fireEvent.click(screen.getAllByRole('button', { name: 'Artikel aus der Liste entfernen' })[0]);
        zweiterBesuchWartet(t);
        expect(screen.getByRole('group', { name: 'Weiterer Shop-Besuch' })).toBeInTheDocument();
        zweiterBesuchKommtAn(t);
        expect(bezeichnungen()).toEqual(['Silikon-Kartusche', 'Dübel 8 mm']);
        expect(screen.queryByText('Keine Artikel mehr in der Liste')).not.toBeInTheDocument();
    });

    it('lässt die Liste bei Fehler unverändert und erklärt es', async () => {
        const t = bisPreise();
        zweiterBesuchWartet(t);
        besuch.phase = 'abgelaufen';
        besuch.fehlertext = 'Der Shop-Besuch war länger als 60 Minuten offen. Bitte den Shop erneut öffnen.';
        t.neu();
        expect(await screen.findByText(/Keine weiteren Artikel geholt: Der Shop-Besuch war länger als 60 Minuten offen\..*Deine Liste bleibt, wie sie ist\./))
            .toBeInTheDocument();
        expect(bezeichnungen()).toEqual(['Silikon-Kartusche', 'Injektionsmörtel', 'Schraube 6x100']);
        expect(screen.queryByRole('group', { name: 'Weiterer Shop-Besuch' })).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Weitere Artikel aus dem Shop holen' })).toBeEnabled();
    });

    it('nimmt bei Fehler ohne Text eine verständliche Ersatzmeldung', async () => {
        const t = bisPreise();
        zweiterBesuchWartet(t);
        besuch.phase = 'fehler';
        besuch.fehlertext = null;
        t.neu();
        expect(await screen.findByText(/Der Shop konnte nicht geöffnet werden\./)).toBeInTheDocument();
    });

    it('„Abbrechen“ im Warte-Hinweis hört auf zu warten, die Liste bleibt', () => {
        const t = bisPreise();
        zweiterBesuchWartet(t);
        besuch.abbrechen.mockClear();
        fireEvent.click(within(screen.getByRole('group', { name: 'Weiterer Shop-Besuch' })).getByRole('button', { name: 'Abbrechen' }));
        expect(besuch.abbrechen).toHaveBeenCalled();
        expect(screen.queryByRole('group', { name: 'Weiterer Shop-Besuch' })).not.toBeInTheDocument();
        expect(bezeichnungen()).toHaveLength(3);
    });

    it('zeigt beim Öffnen „wird geöffnet“', () => {
        const t = bisPreise();
        fireEvent.click(screen.getByRole('button', { name: 'Weitere Artikel aus dem Shop holen' }));
        besuch.phase = 'startet';
        besuch.vorgang = null;
        t.neu();
        expect(screen.getByRole('group', { name: 'Weiterer Shop-Besuch' })).toHaveTextContent('Der Shop von Muster Großhandel wird geöffnet …');
    });

    it('übernimmt in den Artikelstamm je Shop-Besuch mit dessen Indizes und zählt zusammen', async () => {
        uebernimmIdsArtikelstamm
            .mockResolvedValueOnce({ aktualisiert: 1, angelegt: 0, unveraendert: 0, artikelIds: {} })
            .mockResolvedValueOnce({ aktualisiert: 1, angelegt: 0, unveraendert: 0, artikelIds: {} });
        const t = bisPreise();
        // Injektionsmörtel (42/1) raus – es bleiben 42/0, 42/2, 43/0, 43/1
        fireEvent.click(screen.getAllByRole('button', { name: 'Artikel aus der Liste entfernen' })[1]);
        zweiterBesuchWartet(t);
        zweiterBesuchKommtAn(t);
        fireEvent.click(screen.getByRole('button', { name: /In Dokument einfügen/ }));

        expect(screen.getAllByRole('checkbox', { name: 'Preis aktualisieren' })).toHaveLength(2);
        expect(screen.getAllByRole('button', { name: /^Als Artikel anlegen…/ })).toHaveLength(1);
        fireEvent.click(screen.getByRole('button', { name: /^Übernehmen/ }));

        await waitFor(() => expect(t.onSchliessen).toHaveBeenCalled());
        expect(uebernimmIdsArtikelstamm.mock.calls).toEqual([
            [42, { aktualisieren: [{ index: 0, einkaufspreis: 4 }], neuAnlegen: [] }],
            [43, { aktualisieren: [{ index: 1, einkaufspreis: 0.1 }], neuAnlegen: [] }],
        ]);
        expect(await screen.findByText('2 Preise aktualisiert.')).toBeInTheDocument();
    });

    it('schickt nach einem Teil-Fehler nur den fehlenden Teil erneut', async () => {
        uebernimmIdsArtikelstamm
            .mockResolvedValueOnce({ aktualisiert: 1, angelegt: 0, unveraendert: 0, artikelIds: {} })
            .mockRejectedValueOnce(new Error('Der Artikelstamm ist gerade gesperrt.'))
            .mockResolvedValueOnce({ aktualisiert: 1, angelegt: 0, unveraendert: 0, artikelIds: {} });
        const t = bisPreise();
        zweiterBesuchWartet(t);
        zweiterBesuchKommtAn(t);
        fireEvent.click(screen.getByRole('button', { name: /In Dokument einfügen/ }));
        fireEvent.click(screen.getByRole('button', { name: /^Übernehmen/ }));

        expect(await screen.findByText('Der Artikelstamm ist gerade gesperrt. Schon erledigt: 1 Preis aktualisiert.')).toBeInTheDocument();
        expect(t.onSchliessen).not.toHaveBeenCalled();
        await waitFor(() => expect(screen.getByRole('button', { name: /^Übernehmen/ })).toBeEnabled());

        fireEvent.click(screen.getByRole('button', { name: /^Übernehmen/ }));
        await waitFor(() => expect(t.onSchliessen).toHaveBeenCalled());
        expect(uebernimmIdsArtikelstamm.mock.calls.map((c) => c[0])).toEqual([42, 43, 43]);
        expect(await screen.findByText('2 Preise aktualisiert.')).toBeInTheDocument();
    });
});
