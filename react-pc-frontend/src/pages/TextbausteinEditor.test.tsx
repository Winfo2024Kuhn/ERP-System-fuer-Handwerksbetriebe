/**
 * Vitest-Suite fuer den TextbausteinEditor – Schwerpunkt: Vorschau (XSS).
 *
 * Das Vorlagen-HTML kommt ungeprueft aus der Datenbank. Die Vorschau darf es
 * nur bereinigt anzeigen; Platzhalter-Chips und eingesetzte Kundendaten
 * muessen dabei erhalten bleiben.
 *
 * DSGVO: ausschliesslich Dummy-Daten.
 */
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import TextbausteinEditor from './TextbausteinEditor';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';

vi.mock('../components/TiptapEditor', () => ({ TiptapEditor: () => null }));

const VORLAGE_HTML =
    '<p>Guten Tag {{Kundenname}},</p>' +
    '<p>Bauvorhaben: {{BAUVORHABEN}}</p>' +
    '<img src="x" onerror="alert(1)"><script>alert(2)</script>';

function antwort(daten: unknown) {
    return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(daten) });
}

function standardAntwort(url: string) {
    if (url === '/api/textbausteine') {
        return antwort([
            { id: 1, name: 'Anschreiben', html: VORLAGE_HTML },
            { id: 2, name: 'Mahnung', html: '<p>Zweite Vorlage für {{Kundenname}}</p>' },
        ]);
    }
    if (url === '/api/kunden') return antwort({ kunden: [{ id: 7, name: 'Max Mustermann' }] });
    return antwort([]);
}

const fetchMock = vi.fn(standardAntwort);

function renderEditor() {
    return render(
        <ToastProvider><ConfirmProvider><TextbausteinEditor /></ConfirmProvider></ToastProvider>,
    );
}

/**
 * Der Vorschau-Container bleibt bestehen, sein Inhalt wird je nach Ladestand neu gesetzt.
 * Deshalb den Container frisch abfragen und warten, bis Vorlage und Kunde eingesetzt sind –
 * nicht einen frueher gefundenen Kindknoten weiterverwenden.
 */
function findeVorschau(container: HTMLElement) {
    return waitFor(() => {
        const vorschau = container.querySelector<HTMLElement>('.rich-text-einheitlich');
        expect(vorschau).toHaveTextContent('Guten Tag Max Mustermann');
        return vorschau!;
    });
}

beforeEach(() => {
    // Aufrufzähler leert clearMocks (vitest.config.ts) vor jedem Test.
    vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe('TextbausteinEditor Vorschau', () => {
    it('zeigt das Vorlagen-HTML nur bereinigt, mit Kundendaten und Platzhalter-Chip', async () => {
        const { container } = renderEditor();

        const vorschau = await findeVorschau(container);

        const chips = vorschau.querySelectorAll('[data-preview-placeholder="true"]');
        expect(chips).toHaveLength(1);
        expect(chips[0]).toHaveTextContent('{{BAUVORHABEN}}');
        expect(vorschau.querySelector('script')).toBeNull();
        expect(vorschau.querySelector('[onerror]')).toBeNull();
        expect(container.querySelector('[onerror]')).toBeNull();
    });

    it('laedt Vorlagen, Kunden und Dokumenttypen nur einmal, auch nachdem die erste Vorlage gewaehlt ist', async () => {
        const { container } = renderEditor();

        await findeVorschau(container);

        const aufrufe = fetchMock.mock.calls.map(([url]) => url);
        expect(aufrufe.filter((url) => url === '/api/textbausteine')).toHaveLength(1);
        expect(aufrufe.filter((url) => url === '/api/kunden')).toHaveLength(1);
        expect(aufrufe.filter((url) => url === '/api/textbausteine/dokumenttypen')).toHaveLength(1);
    });

    it('behaelt den Vorschau-Inhalt, wenn die Ansicht ohne neue Vorschau neu zeichnet', async () => {
        const { container } = renderEditor();
        const vorschau = await findeVorschau(container);
        const absatz = within(vorschau).getByText('Guten Tag Max Mustermann,');

        // Kundendaten-Bereich zuklappen: zeichnet die Ansicht neu, die Vorschau bleibt gleich.
        fireEvent.click(screen.getByRole('button', { name: /Kundendaten für Vorschau/ }));

        expect(screen.queryAllByText('Kunde auswählen')).toHaveLength(0);
        expect(absatz.isConnected).toBe(true);
        expect(vorschau).toContainElement(absatz);
    });

    it('zeigt nach „Neue Vorlage“ und „Abbrechen“ wieder die zuvor gewaehlte Vorlage', async () => {
        const { container } = renderEditor();
        await findeVorschau(container);
        // Nicht die erste Vorlage waehlen, sonst faellt ein Zuruecksetzen auf die erste nicht auf.
        fireEvent.click(screen.getByText('Mahnung'));
        expect(container.querySelector('.rich-text-einheitlich')).toHaveTextContent('Zweite Vorlage für Max Mustermann');

        fireEvent.click(screen.getByRole('button', { name: /Neue Vorlage/ }));
        expect(container.querySelector('.rich-text-einheitlich')).toBeNull();

        fireEvent.click(screen.getByRole('button', { name: /Abbrechen/ }));
        expect(container.querySelector('.rich-text-einheitlich')).toHaveTextContent('Zweite Vorlage für Max Mustermann');
    });
});

describe('TextbausteinEditor Loeschen', () => {
    function loeschAufrufe() {
        return fetchMock.mock.calls.filter(([, init]) => (init as RequestInit | undefined)?.method === 'DELETE');
    }

    it('fragt vor dem Loeschen nach und loescht bei „Abbrechen“ nichts', async () => {
        const { container } = renderEditor();
        await findeVorschau(container);

        fireEvent.click(screen.getByRole('button', { name: 'Vorlage löschen' }));

        const dialog = await screen.findByRole('dialog', { name: 'Textvorlage löschen?' });
        expect(dialog).toHaveTextContent('„Anschreiben“ wird endgültig gelöscht.');
        expect(dialog).toHaveTextContent('Bereits erstellte Dokumente behalten ihren Text.');
        fireEvent.click(within(dialog).getByRole('button', { name: 'Abbrechen' }));

        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        expect(loeschAufrufe()).toHaveLength(0);
        expect(container.querySelector('.rich-text-einheitlich')).toHaveTextContent('Guten Tag Max Mustermann');
    });

    it('loescht nach Bestaetigung genau die Vorlage aus der Seitenleiste', async () => {
        const { container } = renderEditor();
        await findeVorschau(container);

        fireEvent.click(screen.getByRole('button', { name: 'Vorlage „Mahnung“ löschen' }));
        const dialog = await screen.findByRole('dialog', { name: 'Textvorlage löschen?' });
        expect(dialog).toHaveTextContent('„Mahnung“ wird endgültig gelöscht.');
        fireEvent.click(within(dialog).getByRole('button', { name: 'Löschen' }));

        await waitFor(() => expect(loeschAufrufe()).toHaveLength(1));
        expect(loeschAufrufe()[0][0]).toBe('/api/textbausteine/2');
    });

    it('meldet einen Netzwerkfehler beim Loeschen per Toast', async () => {
        // Eigener Mock nur für diesen Test, der gemeinsame fetchMock bleibt unverändert.
        vi.stubGlobal('fetch', vi.fn((url: string, init?: RequestInit) => (
            init?.method === 'DELETE' ? Promise.reject(new TypeError('Failed to fetch')) : standardAntwort(url)
        )));
        vi.spyOn(console, 'error').mockImplementation(() => {});
        const { container } = renderEditor();
        await findeVorschau(container);

        fireEvent.click(screen.getByRole('button', { name: 'Vorlage löschen' }));
        const dialog = await screen.findByRole('dialog', { name: 'Textvorlage löschen?' });
        fireEvent.click(within(dialog).getByRole('button', { name: 'Löschen' }));

        expect(await screen.findByText('Die Vorlage konnte nicht gelöscht werden.')).toBeInTheDocument();
    });
});
