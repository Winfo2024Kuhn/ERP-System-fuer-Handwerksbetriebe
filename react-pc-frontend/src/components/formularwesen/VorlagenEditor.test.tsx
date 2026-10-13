import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ToastProvider } from '../ui/toast';
import { ConfirmProvider } from '../ui/confirm-dialog';
import VorlagenEditor, { type OffeneVorlage } from './VorlagenEditor';
import { serialisiereVorlage, leseVorlage } from './vorlagenFormat';

/** Alte Vorlage (ohne Briefform-Meta), Anschrift oberhalb des Fensters. */
const ALTE_VORLAGE = '<meta name="page-format" content="A4" /><style>body{font-family:Inter,Arial,sans-serif;}</style>'
    + '<div class="page">'
    + `<div data-block-type="text" data-page="1" data-x="57" data-y="120" data-z="1" data-width="240" data-height="77" data-content="${encodeURIComponent('{{KUNDENADRESSE}}')}"></div>`
    + '<div data-block-type="table" data-page="1" data-x="57" data-y="300" data-z="2" data-width="481" data-height="450"></div>'
    + '</div><div class="page"></div>';

type Gespeichert = { name: string; html: string; assignedDokumenttypen: string[] };

function starte(vorlage: OffeneVorlage = { name: 'Briefpapier Muster', html: ALTE_VORLAGE, dokumenttypen: ['Rechnung'] }) {
    const onZurueck = vi.fn();
    render(
        <ToastProvider>
            <ConfirmProvider>
                <VorlagenEditor vorlage={vorlage} onZurueck={onZurueck} />
            </ConfirmProvider>
        </ToastProvider>,
    );
    return { onZurueck };
}

const hinweis = () => screen.getByTestId('anschrift-hinweis');
const speicherstand = () => screen.getByTestId('speicherstand');

describe('VorlagenEditor', () => {
    let gespeichert: Gespeichert[];

    beforeEach(() => {
        gespeichert = [];
        window.localStorage.clear();
        vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
            if (url === '/api/formulare/templates' && init?.method === 'POST') {
                const daten = JSON.parse(String(init.body)) as Gespeichert;
                gespeichert.push(daten);
                return { ok: true, json: async () => ({ name: daten.name }) };
            }
            return { ok: true, json: async () => [] };
        }));
    });
    afterEach(() => vi.unstubAllGlobals());

    it('setzt die Anschrift ins Fenster, mit Rückgängig und Wiederholen', async () => {
        const user = userEvent.setup();
        starte();
        expect(speicherstand()).toHaveTextContent('Gespeichert');
        expect(hinweis()).toHaveAttribute('data-status', 'ragt-heraus');
        expect(hinweis()).toHaveTextContent('Die Anschrift ragt aus dem Fenster');

        await user.click(within(hinweis()).getByRole('button', { name: 'Anschrift ins Fenster setzen' }));
        expect(hinweis()).toHaveAttribute('data-status', 'passt');
        expect(hinweis()).toHaveTextContent('Anschrift sitzt im Fenster');
        expect(speicherstand()).toHaveTextContent('Ungespeicherte Änderungen');

        await user.click(screen.getByRole('button', { name: 'Rückgängig' }));
        expect(hinweis()).toHaveAttribute('data-status', 'ragt-heraus');
        expect(speicherstand()).toHaveTextContent('Gespeichert');

        await user.click(screen.getByRole('button', { name: 'Wiederholen' }));
        expect(hinweis()).toHaveAttribute('data-status', 'passt');
    });

    it('speichert im unveränderten Format – nur die verschobene Anschrift ändert sich', async () => {
        const user = userEvent.setup();
        starte();
        await user.click(within(hinweis()).getByRole('button', { name: 'Anschrift ins Fenster setzen' }));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(gespeichert).toHaveLength(1));
        const { name, html, assignedDokumenttypen } = gespeichert[0];
        expect(name).toBe('Briefpapier Muster');
        expect(assignedDokumenttypen).toEqual(['Rechnung']);
        expect(html).toBe(ALTE_VORLAGE.replace('data-x="57" data-y="120"', 'data-x="57" data-y="178"'));
        expect(html).not.toContain('briefform');
        await waitFor(() => expect(speicherstand()).toHaveTextContent('Gespeichert'));
    });

    it('schreibt die Briefform nur, wenn sie gewählt wurde', async () => {
        const user = userEvent.setup();
        starte();
        await user.click(screen.getByRole('radio', { name: 'Form A' }));
        expect(screen.queryByText('Gilt für diese Vorlage – zum Übernehmen speichern.')).not.toBeInTheDocument();
        // Bei Form A sitzt das Fenster höher – die Anschrift (y = 120 pt) liegt jetzt im Fenster, aber in der Vermerkzone.
        expect(hinweis()).toHaveAttribute('data-status', 'zusatzzone');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        await waitFor(() => expect(gespeichert).toHaveLength(1));
        expect(leseVorlage(gespeichert[0].html).briefform).toBe('A');
        expect(gespeichert[0].html.replace('<meta name="briefform" content="A" />', '')).toBe(ALTE_VORLAGE);
    });

    it('bedient sich per Tastatur und mm-Feldern', async () => {
        const user = userEvent.setup();
        starte();
        await user.click(within(screen.getByRole('list', { name: 'Ebenen' })).getByRole('button', { name: /^Anschrift/ }));
        const vonLinks = screen.getByLabelText('Von links in mm');
        expect(vonLinks).toHaveValue('20,1');

        fireEvent.keyDown(window, { key: 'ArrowRight', shiftKey: true });
        expect(vonLinks).toHaveValue('23,6');
        fireEvent.keyDown(window, { key: 'ArrowRight' });
        fireEvent.keyDown(window, { key: 'ArrowRight' });
        // Mehrere Pfeiltasten = ein Rückgängig-Schritt
        fireEvent.keyDown(window, { key: 'z', ctrlKey: true });
        expect(screen.getByLabelText('Von links in mm')).toHaveValue('20,1');

        await user.clear(vonLinks);
        await user.type(vonLinks, '25{Enter}');
        await user.tab();
        expect(vonLinks).toHaveValue('25,0');
        // Ungültige Eingabe: Fehlermeldung, Wert bleibt
        await user.clear(vonLinks);
        await user.type(vonLinks, '25,{Enter}');
        expect(screen.getByRole('alert')).toHaveTextContent('Dezimalkomma');
        await user.keyboard('{Escape}');
        expect(vonLinks).toHaveValue('25,0');

        fireEvent.keyDown(window, { key: 'd', ctrlKey: true });
        const ebenen = () => within(screen.getByRole('list', { name: 'Ebenen' })).getAllByRole('button', { name: /^Anschrift/ });
        expect(ebenen()).toHaveLength(2);
        fireEvent.keyDown(window, { key: 'Delete' });
        expect(ebenen()).toHaveLength(1);
        await user.click(ebenen()[0]);
        fireEvent.keyDown(window, { key: 'Escape' });
        expect(screen.getByRole('complementary', { name: 'Einstellungen der Vorlage' })).toBeInTheDocument();
    });

    it('fügt Bausteine per Klick hinzu – die Anschrift gleich ins Fenster', async () => {
        const user = userEvent.setup();
        starte({ name: null, html: '<div class="page"></div><div class="page"></div>', dokumenttypen: [] });
        expect(hinweis()).toHaveAttribute('data-status', 'keine-anschrift');
        await user.click(screen.getByRole('button', { name: 'Anschrift hinzufügen' }));
        expect(hinweis()).toHaveAttribute('data-status', 'passt');
        expect(screen.getByRole('complementary', { name: 'Eigenschaften des Bausteins' })).toHaveTextContent('Anschrift');
    });

    it('fragt bei einer neuen Vorlage nach dem Namen', async () => {
        const user = userEvent.setup();
        starte({ name: null, html: null, dokumenttypen: [] });
        expect(speicherstand()).toHaveTextContent('Ungespeicherte Änderungen');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        const dialog = await screen.findByRole('dialog', { name: 'Vorlage speichern' });
        await user.type(within(dialog).getByLabelText('Name der Vorlage'), 'Briefpapier neu{Enter}');
        await waitFor(() => expect(gespeichert[0]?.name).toBe('Briefpapier neu'));
        expect(gespeichert[0].html).toBe(serialisiereVorlage(leseVorlage(gespeichert[0].html)));
        await waitFor(() => expect(speicherstand()).toHaveTextContent('Gespeichert'));
    });

    it('fragt vor dem Verlassen mit ungespeicherten Änderungen nach', async () => {
        const user = userEvent.setup();
        const { onZurueck } = starte();
        await user.click(within(hinweis()).getByRole('button', { name: 'Anschrift ins Fenster setzen' }));
        await user.click(screen.getByRole('button', { name: 'Alle Vorlagen' }));
        await user.click(await screen.findByRole('button', { name: 'Weiter bearbeiten' }));
        expect(onZurueck).not.toHaveBeenCalled();
        await user.click(screen.getByRole('button', { name: 'Alle Vorlagen' }));
        await user.click(await screen.findByRole('button', { name: 'Verwerfen' }));
        expect(onZurueck).toHaveBeenCalledTimes(1);
    });

    it('Vorschau: Norm-Hinweis mit Korrektur, Umschlag-Ansicht, Escape schließt', async () => {
        const user = userEvent.setup();
        starte();
        // Im Editor die Hilfslinien ausschalten – die Vorschau zeigt sie trotzdem.
        await user.click(screen.getByRole('switch', { name: 'Norm-Hilfslinien' }));
        expect(screen.queryByTestId('anschrift-hinweis')).not.toBeInTheDocument();
        expect(window.localStorage.getItem('formularwesen.normHilfslinien')).toBe('aus');

        await user.click(screen.getByRole('button', { name: 'Vorschau' }));
        const vorschau = await screen.findByRole('dialog', { name: 'Vorschau' });
        expect(within(vorschau).getByTestId('norm-overlay')).toBeInTheDocument();
        expect(within(vorschau).getByTestId('anschrift-hinweis')).toHaveAttribute('data-status', 'ragt-heraus');

        await user.click(within(vorschau).getByRole('button', { name: 'Anschrift ins Fenster setzen' }));
        expect(within(vorschau).getByTestId('anschrift-hinweis')).toHaveTextContent('Anschrift sitzt im Fenster');

        await user.click(within(vorschau).getByRole('radio', { name: 'Form A' }));
        expect(within(vorschau).getByText('Gilt für diese Vorlage – zum Übernehmen speichern.')).toBeInTheDocument();
        await user.click(within(vorschau).getByRole('radio', { name: 'Form B' }));

        await user.click(within(vorschau).getByRole('switch', { name: 'Norm-Hilfslinien' }));
        expect(within(vorschau).queryByTestId('norm-overlay')).not.toBeInTheDocument();
        await user.click(within(vorschau).getByRole('switch', { name: 'Umschlag-Ansicht' }));
        expect(within(vorschau).getByTestId('norm-overlay')).toBeInTheDocument();

        await user.keyboard('{Escape}');
        await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Vorschau' })).not.toBeInTheDocument());
    });
});
