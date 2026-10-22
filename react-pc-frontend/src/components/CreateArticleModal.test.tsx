import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CreateArticleModal } from './CreateArticleModal';
import { ToastProvider } from './ui/toast';

/** Dummy-Lieferant und -Werkstoff (DSGVO: keine echten Firmen). */
const lieferanten = { lieferanten: [{ id: 5, lieferantenname: 'Muster Stahlhandel' }] };
const werkstoffe = [{ id: 4, name: 'Baustahl S235JR' }];

let fetchMock: ReturnType<typeof vi.fn>;
let postAntwort: { ok: boolean; status: number; body: unknown };
let vorschauAntwort: { ok: boolean; body: unknown };
/** Dateinamen, deren Upload der Server ablehnt. */
let abgelehnteDateien: string[];

function renderModal() {
    const onSave = vi.fn();
    const onClose = vi.fn();
    render(
        <ToastProvider>
            <CreateArticleModal onSave={onSave} onClose={onClose} />
        </ToastProvider>,
    );
    return { onSave, onClose };
}

const artikelPosts = () => fetchMock.mock.calls.filter(([url, o]) => o?.method === 'POST' && url === '/api/artikel');
const postBody = () => {
    const aufruf = artikelPosts()[0];
    return aufruf ? JSON.parse(String(aufruf[1]?.body)) : null;
};
const uploads = () => fetchMock.mock.calls
    .filter(([url, o]) => o?.method === 'POST' && String(url).endsWith('/dokumente'))
    .map(([url, o]) => {
        const daten = o?.body as FormData;
        return { url: String(url), typ: daten.get('typ'), name: (daten.get('datei') as File).name };
    });

async function lieferantWaehlen() {
    await userEvent.click(screen.getByRole('tab', { name: /Preis & Lieferant/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Lieferant' }));
    await userEvent.click(await screen.findByText('Muster Stahlhandel'));
}

async function nameEintragen(name = 'Flachstahl 20 x 10') {
    await userEvent.type(screen.getByLabelText('Produktname *'), name);
}

describe('CreateArticleModal', () => {
    beforeEach(() => {
        postAntwort = { ok: true, status: 200, body: { id: 77, artikelnummer: 'A-004143', produktname: 'Flachstahl 20 x 10' } };
        vorschauAntwort = { ok: true, body: { artikelnummer: 'A-004143' } };
        abgelehnteDateien = [];
        fetchMock = vi.fn((url: string, options?: RequestInit) => {
            const antwort = (ok: boolean, body: unknown, status = ok ? 200 : 400) =>
                Promise.resolve({ ok, status, json: () => Promise.resolve(body) });
            if (options?.method === 'POST' && url === '/api/artikel') return antwort(postAntwort.ok, postAntwort.body, postAntwort.status);
            if (options?.method === 'POST' && url.endsWith('/dokumente')) {
                const datei = (options.body as FormData).get('datei') as File;
                return abgelehnteDateien.includes(datei.name)
                    ? antwort(false, { message: 'Dieser Dateityp wird nicht unterstützt.' })
                    : antwort(true, {});
            }
            if (url === '/api/artikel/naechste-artikelnummer') return antwort(vorschauAntwort.ok, vorschauAntwort.body, vorschauAntwort.ok ? 200 : 500);
            if (url === '/api/artikel/werkstoffe/details') return antwort(true, werkstoffe);
            if (url.startsWith('/api/lieferanten')) return antwort(true, lieferanten);
            return antwort(true, []);
        });
        vi.stubGlobal('fetch', fetchMock);
    });

    afterEach(() => vi.unstubAllGlobals());

    it('zeigt die vorgeschlagene Artikel-Nr. und schickt null, damit der Server sie vergibt', async () => {
        const { onSave } = renderModal();

        expect(await screen.findByText('A-004143 · wird automatisch vergeben')).toBeInTheDocument();
        expect(screen.getByLabelText('Produktname *')).toHaveFocus();
        await nameEintragen();
        await userEvent.click(screen.getByRole('button', { name: 'Artikel anlegen' }));

        await waitFor(() => expect(onSave).toHaveBeenCalledWith(expect.objectContaining({ id: 77, artikelnummer: 'A-004143' })));
        const body = postBody();
        expect(body.artikelnummer).toBeNull();
        expect(body.produktname).toBe('Flachstahl 20 x 10');
        expect(body.verrechnungseinheit).toBe('STUECK');
        expect(body.preis).toBeNull();
        expect(body.lieferantId).toBeNull();
        expect(body.externeArtikelnummer).toBeNull();
        expect(body.verpackungseinheit).toBe(1);
        expect(await screen.findByText('Artikel A-004143 ist angelegt.')).toBeInTheDocument();
    });

    it('übernimmt Einheit und Werkstoff aus „Das Wichtigste“', async () => {
        const user = userEvent.setup();
        renderModal();
        await nameEintragen();

        await user.click(screen.getByRole('combobox', { name: 'Einheit' }));
        await user.click(await screen.findByRole('option', { name: 'Laufende Meter' }));
        await user.click(screen.getByRole('combobox', { name: 'Werkstoff' }));
        await user.click(await screen.findByRole('option', { name: 'Baustahl S235JR' }));
        await user.click(screen.getByRole('button', { name: 'Artikel anlegen' }));

        await waitFor(() => expect(postBody()).not.toBeNull());
        expect(postBody()).toMatchObject({ verrechnungseinheit: 'LAUFENDE_METER', werkstoffId: 4 });
    });

    it('schickt die unveränderte Vorschau auch bei „Eigene Nummer“ als null, eine echte eigene Nummer aber mit', async () => {
        const user = userEvent.setup();
        renderModal();
        await nameEintragen();
        await screen.findByText('A-004143 · wird automatisch vergeben');

        await user.click(screen.getByRole('button', { name: 'Eigene Nummer' }));
        const feld = screen.getByLabelText('Artikel-Nr.');
        expect(feld).toHaveValue('A-004143');

        await user.clear(feld);
        await user.type(feld, 'FL-20-10');
        await user.click(screen.getByRole('button', { name: 'Artikel anlegen' }));

        await waitFor(() => expect(postBody()).not.toBeNull());
        expect(postBody().artikelnummer).toBe('FL-20-10');
    });

    it('schickt null, wenn im Feld „Eigene Nummer“ die Vorschau unverändert steht', async () => {
        const user = userEvent.setup();
        renderModal();
        await nameEintragen();
        await screen.findByText('A-004143 · wird automatisch vergeben');

        await user.click(screen.getByRole('button', { name: 'Eigene Nummer' }));
        await user.click(screen.getByRole('button', { name: 'Artikel anlegen' }));

        await waitFor(() => expect(postBody()).not.toBeNull());
        expect(postBody().artikelnummer).toBeNull();
    });

    it('wechselt mit „Automatisch“ zurück zur Vorschau', async () => {
        const user = userEvent.setup();
        renderModal();
        await screen.findByText('A-004143 · wird automatisch vergeben');
        await user.click(screen.getByRole('button', { name: 'Eigene Nummer' }));
        await user.click(screen.getByRole('button', { name: 'Automatisch' }));
        expect(screen.getByText('A-004143 · wird automatisch vergeben')).toBeInTheDocument();
        expect(screen.queryByLabelText('Artikel-Nr.')).toBeNull();
    });

    it('sagt ohne Vorschau ehrlich, dass die Nummer beim Anlegen vergeben wird', async () => {
        vorschauAntwort = { ok: false, body: {} };
        renderModal();
        expect(await screen.findByText('wird beim Anlegen automatisch vergeben')).toBeInTheDocument();
    });

    it('springt ohne Namen zurück auf „Das Wichtigste“ und setzt den Fokus ins Namensfeld', async () => {
        const user = userEvent.setup();
        renderModal();

        await user.click(screen.getByRole('tab', { name: /Mehr Angaben/ }));
        await user.click(screen.getByRole('button', { name: 'Artikel anlegen' }));

        expect(screen.getByRole('tab', { name: /Das Wichtigste/ })).toHaveAttribute('aria-selected', 'true');
        await waitFor(() => expect(screen.getByLabelText('Produktname *')).toHaveFocus());
        expect(screen.getByText('Bitte einen Produktnamen eingeben.')).toBeInTheDocument();
        expect(artikelPosts()).toHaveLength(0);
    });

    it('führt mit „Weiter“ durch die Reiter; auf dem letzten gibt es kein „Weiter“', async () => {
        const user = userEvent.setup();
        renderModal();

        await user.click(screen.getByRole('button', { name: 'Weiter' }));
        expect(screen.getByRole('tab', { name: /Preis & Lieferant/ })).toHaveAttribute('aria-selected', 'true');
        await user.click(screen.getByRole('button', { name: 'Weiter' }));
        expect(screen.getByRole('tab', { name: /Bilder & Unterlagen/ })).toHaveAttribute('aria-selected', 'true');
        await user.click(screen.getByRole('button', { name: 'Weiter' }));
        expect(screen.getByRole('tab', { name: /Mehr Angaben/ })).toHaveAttribute('aria-selected', 'true');
        expect(screen.queryByRole('button', { name: 'Weiter' })).toBeNull();
        // Die optionalen Reiter sind als solche erkennbar
        expect(screen.getByRole('tab', { name: /Mehr Angaben/ })).toHaveTextContent('optional');
    });

    it('sperrt Preis und Lieferanten-Nummer, solange kein Lieferant gewählt ist', async () => {
        renderModal();
        await userEvent.click(screen.getByRole('tab', { name: /Preis & Lieferant/ }));

        expect(screen.getByLabelText('Preis in Euro')).toBeDisabled();
        expect(screen.getByLabelText('Artikelnummer beim Lieferanten')).toBeDisabled();
        expect(screen.getByText(/Erst den Lieferanten wählen – Preis und Lieferanten-Nummer/)).toBeInTheDocument();
        // Kein Preis mit 0 vorbelegt
        expect(screen.getByLabelText('Preis in Euro')).toHaveValue('');

        await userEvent.click(screen.getByRole('button', { name: 'Lieferant' }));
        await userEvent.click(await screen.findByText('Muster Stahlhandel'));

        expect(screen.getByLabelText('Preis in Euro')).toBeEnabled();
        expect(screen.getByLabelText('Artikelnummer beim Lieferanten')).toBeEnabled();
        expect(screen.queryByText(/Erst den Lieferanten wählen/)).not.toBeInTheDocument();
        expect(screen.getByRole('tab', { name: /Preis & Lieferant/ })).toHaveTextContent('ausgefüllt');
    });

    it('liest den Preis im deutschen Format mit Lieferant', async () => {
        renderModal();

        await nameEintragen('Quadratrohr 40x40x2');
        await lieferantWaehlen();
        await userEvent.type(screen.getByLabelText('Artikelnummer beim Lieferanten'), 'MS-100');
        await userEvent.type(screen.getByLabelText('Preis in Euro'), '1.234,50');
        await userEvent.click(screen.getByRole('button', { name: 'Artikel anlegen' }));

        await waitFor(() => expect(postBody()).not.toBeNull());
        expect(postBody()).toMatchObject({ lieferantId: 5, preis: 1234.5, externeArtikelnummer: 'MS-100', artikelnummer: null });
    });

    it('zeigt die Meldung des Servers bei einer vergebenen Artikelnummer und legt nichts an', async () => {
        postAntwort = { ok: false, status: 400, body: { status: 400, message: 'Die Artikelnummer „4711“ ist schon vergeben.' } };
        const user = userEvent.setup();
        const { onSave } = renderModal();

        await nameEintragen('Quadratrohr');
        await user.click(screen.getByRole('button', { name: 'Eigene Nummer' }));
        await user.clear(screen.getByLabelText('Artikel-Nr.'));
        await user.type(screen.getByLabelText('Artikel-Nr.'), '4711');
        await user.click(screen.getByRole('button', { name: 'Artikel anlegen' }));

        expect(await screen.findByText('Die Artikelnummer „4711“ ist schon vergeben.')).toBeInTheDocument();
        expect(onSave).not.toHaveBeenCalled();
        // Nach dem Fehler darf man es erneut versuchen
        expect(screen.getByRole('button', { name: 'Artikel anlegen' })).toBeEnabled();
    });

    it('erklärt die Artikel-Nr. im Info-Button', async () => {
        renderModal();
        await userEvent.click(screen.getByRole('button', { name: /Mehr zu: Artikel-Nr\./ }));
        expect(await screen.findByText(/die nächste freie Nummer/)).toBeInTheDocument();
        expect(screen.getByText(/gibt es nur einmal/)).toBeInTheDocument();
    });

    it('lädt nach dem Anlegen erst das Vorschaubild, dann die Unterlagen mit ihrer Art hoch', async () => {
        const user = userEvent.setup();
        const { onSave } = renderModal();
        await nameEintragen();

        await user.click(screen.getByRole('tab', { name: /Bilder & Unterlagen/ }));
        await user.upload(screen.getByLabelText('Bild auswählen', { selector: 'input' }), new File(['bild'], 'flachstahl.jpg', { type: 'image/jpeg' }));
        // jsdom kann keine Bilder lesen: Statt der Zeichenflaeche steht der Platzhalter mit demselben Namen da.
        expect(screen.getByRole('img', { name: 'Gewähltes Bild: flachstahl.jpg' })).toBeInTheDocument();
        expect(screen.getByText('Keine Vorschau')).toBeInTheDocument();

        // Erst als Datenblatt, dann als Zulassung
        await user.upload(screen.getByLabelText('Unterlage auswählen'), new File(['pdf'], 'datenblatt.pdf', { type: 'application/pdf' }));
        await user.click(screen.getByRole('combobox', { name: 'Art der Unterlage' }));
        await user.click(await screen.findByRole('option', { name: 'Zulassung' }));
        await user.upload(screen.getByLabelText('Unterlage auswählen'), new File(['pdf'], 'zulassung.pdf', { type: 'application/pdf' }));

        const liste = screen.getByRole('list', { name: 'Gewählte Unterlagen' });
        expect(within(liste).getAllByRole('listitem')).toHaveLength(2);
        expect(screen.getByRole('tab', { name: /Bilder & Unterlagen/ })).toHaveTextContent('3 Dateien gewählt');

        await user.click(screen.getByRole('button', { name: 'Artikel anlegen' }));

        await waitFor(() => expect(onSave).toHaveBeenCalled());
        expect(uploads()).toEqual([
            { url: '/api/artikel/77/dokumente', typ: 'VORSCHAUBILD', name: 'flachstahl.jpg' },
            { url: '/api/artikel/77/dokumente', typ: 'DATENBLATT', name: 'datenblatt.pdf' },
            { url: '/api/artikel/77/dokumente', typ: 'ZULASSUNG', name: 'zulassung.pdf' },
        ]);
        // Hochgeladen wird erst, wenn der Artikel steht
        const reihenfolge = fetchMock.mock.calls.filter(([, o]) => o?.method === 'POST').map(([url]) => url);
        expect(reihenfolge[0]).toBe('/api/artikel');
    });

    it('nimmt eine gewählte Unterlage wieder heraus', async () => {
        const user = userEvent.setup();
        renderModal();
        await user.click(screen.getByRole('tab', { name: /Bilder & Unterlagen/ }));
        await user.upload(screen.getByLabelText('Unterlage auswählen'), new File(['pdf'], 'datenblatt.pdf', { type: 'application/pdf' }));
        await user.click(screen.getByRole('button', { name: 'datenblatt.pdf entfernen' }));
        expect(screen.getByText('Noch keine Unterlagen gewählt.')).toBeInTheDocument();
    });

    it('meldet einen fehlgeschlagenen Upload, behält den Artikel aber und öffnet ihn trotzdem', async () => {
        abgelehnteDateien = ['zeichnung.pdf'];
        const user = userEvent.setup();
        const { onSave } = renderModal();
        await nameEintragen();

        await user.click(screen.getByRole('tab', { name: /Bilder & Unterlagen/ }));
        await user.upload(screen.getByLabelText('Unterlage auswählen'), new File(['pdf'], 'zeichnung.pdf', { type: 'application/pdf' }));
        await user.click(screen.getByRole('button', { name: 'Artikel anlegen' }));

        expect(await screen.findByText('„zeichnung.pdf“ konnte nicht hochgeladen werden – du kannst sie auf der Artikelseite nachreichen.')).toBeInTheDocument();
        await waitFor(() => expect(onSave).toHaveBeenCalledWith(expect.objectContaining({ id: 77 })));
    });

    it('legt bei einem Doppelklick nur einen Artikel an', async () => {
        let freigeben: (wert: unknown) => void = () => {};
        const haengt = new Promise((resolve) => { freigeben = resolve; });
        const ursprung = fetchMock.getMockImplementation()!;
        fetchMock.mockImplementation((url: string, options?: RequestInit) => {
            if (options?.method === 'POST' && url === '/api/artikel') {
                return haengt.then(() => ({ ok: true, status: 200, json: () => Promise.resolve(postAntwort.body) }));
            }
            return ursprung(url, options);
        });
        const user = userEvent.setup();
        const { onSave } = renderModal();
        await nameEintragen();

        const knopf = screen.getByRole('button', { name: 'Artikel anlegen' });
        await user.dblClick(knopf);
        expect(artikelPosts()).toHaveLength(1);
        expect(screen.getByRole('button', { name: /Wird angelegt/ })).toBeDisabled();

        freigeben(null);
        await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
        expect(artikelPosts()).toHaveLength(1);
    });
});
