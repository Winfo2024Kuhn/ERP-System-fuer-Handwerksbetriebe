/**
 * Vitest-Suite fuer den Dialog „Als neue Leistung speichern“.
 *
 * Ordnerbaum (gemockter fetch):
 *   Geländer (1)  ─┬─ Edelstahl (12, unterste Ebene)  ← Ordner der Ursprungs-Leistung
 *                  └─ Stahl (13, unterste Ebene)
 *   Treppen (2)    ── Podeste (21, unterste Ebene)
 *
 * DSGVO: ausschliesslich Dummy-Daten.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { ToastProvider } from '../ui/toast';
import { LeistungNeuSpeichernDialog } from './LeistungNeuSpeichernDialog';
import type { DocBlock, LeistungApiDto } from './types';

const ursprung: LeistungApiDto = {
    id: 7,
    name: 'Geländer montieren',
    description: '<p>Montage inkl. Befestigung</p>',
    price: 120,
    unit: { name: 'LAUFENDE_METER', anzeigename: 'Laufende Meter' },
    folderId: 12,
    kategoriePfad: 'Geländer > Edelstahl',
};

const block: DocBlock = {
    id: 'b1',
    type: 'SERVICE',
    title: 'Geländer montieren, Edelstahl V4A',
    description: '<p>Montage inkl. Befestigung und <strong>Abdichtung</strong></p>',
    quantity: 3,
    unit: 'lfm',
    price: 135.5,
    leistungId: 7,
    kategorieId: 12,
};

const KATEGORIEN: Record<number, { id: number; bezeichnung: string; parentId: number | null; leaf: boolean }> = {
    1: { id: 1, bezeichnung: 'Geländer', parentId: null, leaf: false },
    2: { id: 2, bezeichnung: 'Treppen', parentId: null, leaf: false },
    12: { id: 12, bezeichnung: 'Edelstahl', parentId: 1, leaf: true },
    13: { id: 13, bezeichnung: 'Stahl', parentId: 1, leaf: true },
    21: { id: 21, bezeichnung: 'Podeste', parentId: 2, leaf: true },
};

function antwort(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

type PostAntwort = () => Response | Promise<Response>;

function stubFetch(postAntwort: PostAntwort, kategorien = KATEGORIEN) {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        if (url === '/api/leistungen' && init?.method === 'POST') return postAntwort();
        if (url.startsWith('/api/produktkategorien/haupt')) {
            return antwort(Object.values(kategorien).filter(k => k.parentId == null));
        }
        const kinder = url.match(/^\/api\/produktkategorien\/(\d+)\/unterkategorien/);
        if (kinder) return antwort(Object.values(kategorien).filter(k => k.parentId === Number(kinder[1])));
        const einzeln = url.match(/^\/api\/produktkategorien\/(\d+)\?light=true$/);
        if (einzeln) {
            const k = kategorien[Number(einzeln[1])];
            // light=true liefert `leaf` nicht verlaesslich – bewusst immer true, der Baum muss gewinnen.
            return k ? antwort({ ...k, leaf: true }) : antwort({}, 404);
        }
        return antwort([], 404);
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

function zeige(props: Partial<React.ComponentProps<typeof LeistungNeuSpeichernDialog>> = {}) {
    const onGespeichert = vi.fn();
    const onClose = vi.fn();
    render(
        <ToastProvider>
            <LeistungNeuSpeichernDialog
                block={block}
                ursprungsLeistung={ursprung}
                onGespeichert={onGespeichert}
                onClose={onClose}
                {...props}
            />
        </ToastProvider>,
    );
    return { onGespeichert, onClose };
}

const speichernKnopf = () => screen.getByRole('button', { name: /^Speichern$/ });

/** Wartet, bis der Ursprungsordner aufgeklappt und vorausgewaehlt ist. */
async function warteAufVorauswahl() {
    await waitFor(() => expect(screen.getByTestId('speicherziel')).toHaveTextContent('Wird gespeichert in: Geländer › Edelstahl'));
    // Die Unterordner kommen ueber eine eigene Anfrage – erst dann ist der Baum bedienbar.
    await screen.findByRole('button', { name: /Edelstahl/ });
}

/** Fehlerzeile im Dialog (der Toast traegt ebenfalls role="alert"). */
const dialogFehler = () => within(screen.getByRole('dialog', { name: 'Als neue Leistung speichern' })).findByRole('alert');

beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn();
});

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('LeistungNeuSpeichernDialog', () => {
    it('klappt den Ordner der Ursprungs-Leistung auf, waehlt ihn vor und holt ihn ins Bild', async () => {
        stubFetch(() => antwort({}));
        zeige();

        await warteAufVorauswahl();
        const edelstahl = screen.getByRole('button', { name: /Edelstahl/ });
        expect(edelstahl).toHaveAttribute('aria-pressed', 'true');
        expect(screen.getByRole('button', { name: /Stahl$/ })).toHaveAttribute('aria-pressed', 'false');
        // Fremder Zweig bleibt zu
        expect(screen.queryByText('Podeste')).not.toBeInTheDocument();
        expect(Element.prototype.scrollIntoView).toHaveBeenCalledWith({ block: 'nearest' });
        expect(screen.getByRole('textbox', { name: /Name der Leistung/ })).toHaveValue('Geländer montieren, Edelstahl V4A');
        expect(speichernKnopf()).toBeEnabled();
    });

    it('sendet Name, Text, Preis, Einheit als Enum und Ordner und meldet die neue Leistung', async () => {
        const neu: LeistungApiDto = { ...ursprung, id: 99, name: 'Geländer montieren, Edelstahl V4A', price: 135.5 };
        const fetchMock = stubFetch(() => antwort(neu));
        const { onGespeichert } = zeige();
        await warteAufVorauswahl();

        fireEvent.click(speichernKnopf());

        await waitFor(() => expect(onGespeichert).toHaveBeenCalledWith(neu, { id: 12, pfad: 'Geländer › Edelstahl' }));
        const post = fetchMock.mock.calls.find(([url, init]) => url === '/api/leistungen' && init?.method === 'POST');
        expect(JSON.parse(String(post![1]!.body))).toEqual({
            name: 'Geländer montieren, Edelstahl V4A',
            description: block.description,
            price: 135.5,
            unit: 'LAUFENDE_METER',
            folderId: 12,
        });
    });

    it('schickt unit null bei frei getippter Einheit und nimmt einen anders gewaehlten Ordner', async () => {
        const fetchMock = stubFetch(() => antwort({ ...ursprung, id: 100 }));
        zeige({ block: { ...block, unit: 'Psch' } });
        await warteAufVorauswahl();

        fireEvent.click(screen.getByRole('button', { name: /Stahl$/ }));
        expect(screen.getByTestId('speicherziel')).toHaveTextContent('Geländer › Stahl');
        fireEvent.click(speichernKnopf());

        await waitFor(() => expect(fetchMock.mock.calls.some(([url, init]) => url === '/api/leistungen' && init?.method === 'POST')).toBe(true));
        const post = fetchMock.mock.calls.find(([url, init]) => url === '/api/leistungen' && init?.method === 'POST');
        const body = JSON.parse(String(post![1]!.body));
        expect(body.unit).toBeNull();
        expect(body.folderId).toBe(13);
    });

    it('zeigt die Meldung aus einer 400-Antwort im Dialog an und bleibt offen', async () => {
        stubFetch(() => antwort({ message: 'Der gewählte Ordner existiert nicht mehr.' }, 400));
        const { onGespeichert, onClose } = zeige();
        await warteAufVorauswahl();

        fireEvent.click(speichernKnopf());

        expect(await dialogFehler()).toHaveTextContent('Der gewählte Ordner existiert nicht mehr.');
        expect(onGespeichert).not.toHaveBeenCalled();
        expect(onClose).not.toHaveBeenCalled();
        expect(speichernKnopf()).toBeEnabled();
    });

    it('zeigt bei Serverfehler die allgemeine Meldung', async () => {
        stubFetch(() => new Response('kaputt', { status: 500 }));
        zeige();
        await warteAufVorauswahl();

        fireEvent.click(speichernKnopf());

        expect(await dialogFehler()).toHaveTextContent('Die Leistung konnte nicht gespeichert werden.');
    });

    it('sperrt Speichern ohne Namen und erklaert warum', async () => {
        const fetchMock = stubFetch(() => antwort({}));
        zeige();
        await warteAufVorauswahl();

        fireEvent.change(screen.getByRole('textbox', { name: /Name der Leistung/ }), { target: { value: '   ' } });

        expect(speichernKnopf()).toBeDisabled();
        expect(screen.getByText('Bitte geben Sie der Leistung einen Namen.')).toBeInTheDocument();
        fireEvent.click(speichernKnopf());
        expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false);
    });

    it('schickt beim Doppelklick nur eine Anfrage', async () => {
        let freigeben: (r: Response) => void = () => {};
        const fetchMock = stubFetch(() => new Promise<Response>(resolve => { freigeben = resolve; }));
        const { onGespeichert } = zeige();
        await warteAufVorauswahl();

        fireEvent.click(speichernKnopf());
        fireEvent.click(screen.getByRole('button', { name: /Wird gespeichert/ }));
        freigeben(antwort({ ...ursprung, id: 101 }));

        await waitFor(() => expect(onGespeichert).toHaveBeenCalledTimes(1));
        expect(fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST')).toHaveLength(1);
    });

    it('waehlt einen Ursprungsordner mit Unterordnern nicht vor, klappt ihn aber auf', async () => {
        // Altdaten: Leistung haengt direkt am Ordner "Geländer", der Unterordner hat.
        stubFetch(() => antwort({}));
        zeige({ ursprungsLeistung: { ...ursprung, folderId: 1 } });

        expect(await screen.findByText('Edelstahl')).toBeInTheDocument();
        expect(screen.getByTestId('speicherziel')).toHaveTextContent('Bitte wählen Sie einen Ordner der untersten Ebene.');
        expect(speichernKnopf()).toBeDisabled();

        // Ein Ordner mit Unterordnern klappt per Klick nur zu, wird aber nicht Ziel.
        fireEvent.click(screen.getByRole('button', { name: /^Geländer$/ }));
        expect(screen.queryByText('Edelstahl')).not.toBeInTheDocument();
        expect(speichernKnopf()).toBeDisabled();
    });

    it('schliesst mit Escape', async () => {
        stubFetch(() => antwort({}));
        const { onClose } = zeige();
        await warteAufVorauswahl();

        fireEvent.keyDown(document.activeElement ?? document.body, { key: 'Escape' });

        expect(onClose).toHaveBeenCalled();
    });
});
