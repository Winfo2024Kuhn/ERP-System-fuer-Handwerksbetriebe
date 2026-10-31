/**
 * Übersichtsseite der Textbausteine: Laden, Karten mit Beispieltext,
 * Filter in der URL, leere Zustände. DSGVO: nur Dummy-Daten.
 */
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Textbausteine from './Textbausteine';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { TEST_GRUPPEN, TEST_KATALOG, testBaustein } from '../components/textbaustein/__tests__/testdaten';
import type { TextbausteinDto } from '../components/textbaustein/typen';

vi.mock('../components/TiptapEditor', () => ({ TiptapEditor: () => null }));

const BAUSTEINE: TextbausteinDto[] = [
    testBaustein({ id: 1, name: 'Anschreiben', html: '<p>Guten Tag {{Kundenname}}</p><img src="x" onerror="alert(1)"><script>alert(2)</script>', dokumenttypen: ['Angebot'] }),
    testBaustein({ id: 2, name: 'Mahnung freundlich', html: '<p>Bitte zahlen Sie bis {{ZAHLUNGSZIEL}}.</p>', dokumenttypen: ['1. Mahnung'] }),
    testBaustein({ id: 3, name: 'Gruß', html: '<p>Mit freundlichen Grüßen</p>', dokumenttypen: [] }),
];

function antwort(daten: unknown, status = 200) {
    return Promise.resolve({ ok: status < 400, status, json: () => Promise.resolve(daten) });
}

let bausteine: TextbausteinDto[] = BAUSTEINE;
const fetchMock = vi.fn((url: string) => {
    if (url === '/api/textbausteine') return antwort(bausteine);
    if (url === '/api/textbausteine/placeholders') return antwort(TEST_KATALOG);
    if (url === '/api/textbausteine/dokumenttyp-gruppen') return antwort(TEST_GRUPPEN);
    return antwort({ message: 'unbekannt' }, 404);
});

function UrlMitschrift() {
    return <output data-testid="url-suche">{useLocation().search}</output>;
}
const aktuelleSuche = () => screen.getByTestId('url-suche').textContent;

function zeigeSeite(start = '/textbausteine') {
    return render(
        <MemoryRouter initialEntries={[start]}>
            <ToastProvider><ConfirmProvider><Textbausteine /><UrlMitschrift /></ConfirmProvider></ToastProvider>
        </MemoryRouter>,
    );
}

const karte = (name: string) => screen.getByRole('button', { name: `Textbaustein „${name}“ bearbeiten` }).closest('article')!;

beforeEach(() => {
    bausteine = BAUSTEINE;
    vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('Textbausteine', () => {
    it('lädt jeden Endpunkt genau einmal und zeigt Karten mit Beispielwerten statt Kürzeln', async () => {
        const { container } = zeigeSeite();
        await screen.findByRole('heading', { name: 'Anschreiben' });

        const aufrufe = fetchMock.mock.calls.map(([url]) => url);
        expect(aufrufe.filter((u) => u === '/api/textbausteine')).toHaveLength(1);
        expect(aufrufe.filter((u) => u === '/api/textbausteine/placeholders')).toHaveLength(1);
        expect(aufrufe.filter((u) => u === '/api/textbausteine/dokumenttyp-gruppen')).toHaveLength(1);
        expect(aufrufe).not.toContain('/api/kunden');
        expect(aufrufe).not.toContain('/api/textbausteine/dokumenttypen');

        const anschreiben = karte('Anschreiben');
        expect(anschreiben).toHaveTextContent('Max Mustermann');
        expect(anschreiben.querySelector('[data-platzhalter-chip="{{Kundenname}}"]')).not.toBeNull();
        expect(within(anschreiben).getByText('Angebot')).toBeInTheDocument();
        expect(within(karte('Gruß')).getByText('Ohne Zuordnung')).toBeInTheDocument();
        expect(container.querySelector('[onerror]')).toBeNull();
        expect(container.querySelector('script')).toBeNull();
        expect(screen.getByText('3 Textbausteine')).toBeInTheDocument();
    });

    it('sucht in Name und Text und schreibt die Suche in die URL', async () => {
        zeigeSeite();
        await screen.findByRole('heading', { name: 'Anschreiben' });
        fireEvent.change(screen.getByLabelText('Suchen'), { target: { value: 'zahlen' } });
        expect(screen.queryByRole('heading', { name: 'Anschreiben' })).toBeNull();
        expect(screen.getByRole('heading', { name: 'Mahnung freundlich' })).toBeInTheDocument();
        expect(screen.getByText('1 von 3 Textbausteinen')).toBeInTheDocument();
        expect(aktuelleSuche()).toBe('?q=zahlen');
    });

    it('übernimmt den Dokumentart-Filter aus der URL und zeigt bei keinem Treffer „Filter zurücksetzen“', async () => {
        zeigeSeite('/textbausteine?dokumentart=Rechnung');
        expect(await screen.findByText('Keine Textbausteine für „Rechnung“')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Filter zurücksetzen' }));
        expect(await screen.findByRole('heading', { name: 'Anschreiben' })).toBeInTheDocument();
        expect(aktuelleSuche()).toBe('');
    });

    it('filtert „Ohne Zuordnung“', async () => {
        zeigeSeite('/textbausteine?dokumentart=ohne-zuordnung');
        expect(await screen.findByRole('heading', { name: 'Gruß' })).toBeInTheDocument();
        expect(screen.queryByRole('heading', { name: 'Anschreiben' })).toBeNull();
    });

    it('erklärt, was ein Textbaustein ist, wenn es noch keine gibt', async () => {
        bausteine = [];
        zeigeSeite();
        expect(await screen.findByRole('heading', { name: 'Noch keine Textbausteine' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Ersten Textbaustein anlegen' })).toBeInTheDocument();
    });

    it('öffnet den Dialog per Karte und für einen neuen Textbaustein mit der gefilterten Dokumentart', async () => {
        zeigeSeite('/textbausteine?dokumentart=Angebot');
        fireEvent.click(await screen.findByRole('button', { name: 'Textbaustein „Anschreiben“ bearbeiten' }));
        const bearbeiten = await screen.findByRole('dialog', { name: 'Textbaustein bearbeiten' });
        expect(within(bearbeiten).getByLabelText(/Name/)).toHaveValue('Anschreiben');
        fireEvent.click(within(bearbeiten).getByRole('button', { name: 'Abbrechen' }));
        await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());

        fireEvent.click(screen.getByRole('button', { name: 'Neuer Textbaustein' }));
        const neu = await screen.findByRole('dialog', { name: 'Neuer Textbaustein' });
        expect(within(neu).getByLabelText(/Name/)).toHaveValue('');
        expect(within(neu).getByRole('switch', { name: 'Angebot' })).toHaveAttribute('aria-checked', 'true');
        expect(within(neu).getByRole('switch', { name: 'Rechnung' })).toHaveAttribute('aria-checked', 'false');
    });

    it('legt über „Kopie anlegen“ einen neuen Textbaustein mit gleichem Inhalt an', async () => {
        zeigeSeite();
        fireEvent.click(await screen.findByRole('button', { name: 'Kopie von „Anschreiben“ anlegen' }));
        const neu = await screen.findByRole('dialog', { name: 'Neuer Textbaustein' });
        expect(within(neu).getByLabelText(/Name/)).toHaveValue('Kopie von Anschreiben');
    });

    it('zeigt einen Fehlerzustand mit „Erneut versuchen“, wenn das Laden scheitert', async () => {
        vi.stubGlobal('fetch', vi.fn((url: string) => (url === '/api/textbausteine'
            ? antwort({ message: 'Server nicht erreichbar.' }, 500)
            : fetchMock(url))));
        zeigeSeite();
        expect(await screen.findByRole('heading', { name: 'Die Textbausteine konnten nicht geladen werden.' })).toBeInTheDocument();
        expect(screen.getByText('Server nicht erreichbar.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Erneut versuchen' })).toBeInTheDocument();
    });

    it('zeigt den Fehlerzustand, wenn die Platzhalter fehlen, und lädt mit „Erneut versuchen“ alles neu', async () => {
        let platzhalterKaputt = true;
        const mitFehler = vi.fn((url: string) => (url === '/api/textbausteine/placeholders' && platzhalterKaputt
            ? antwort({ message: 'Die Platzhalter sind gerade nicht erreichbar.' }, 500)
            : fetchMock(url)));
        vi.stubGlobal('fetch', mitFehler);
        zeigeSeite();
        expect(await screen.findByRole('heading', { name: 'Die Textbausteine konnten nicht geladen werden.' })).toBeInTheDocument();
        expect(screen.getByText('Die Platzhalter sind gerade nicht erreichbar.')).toBeInTheDocument();
        // Keine Karten mit lauter „unbekannten“ Platzhaltern.
        expect(screen.queryByRole('heading', { name: 'Anschreiben' })).toBeNull();

        platzhalterKaputt = false;
        fireEvent.click(screen.getByRole('button', { name: 'Erneut versuchen' }));
        expect(await screen.findByRole('heading', { name: 'Anschreiben' })).toBeInTheDocument();
        const aufrufe = mitFehler.mock.calls.map(([url]) => url);
        expect(aufrufe.filter((u) => u === '/api/textbausteine')).toHaveLength(2);
        expect(aufrufe.filter((u) => u === '/api/textbausteine/placeholders')).toHaveLength(2);
        expect(aufrufe.filter((u) => u === '/api/textbausteine/dokumenttyp-gruppen')).toHaveLength(2);
        expect(karte('Anschreiben')).toHaveTextContent('Max Mustermann');
    });

    it('fällt bei unbekannter Dokumentart in der URL auf „Alle“ zurück und wählt beim Anlegen nichts vor', async () => {
        zeigeSeite('/textbausteine?dokumentart=Lieferschein');
        expect(await screen.findByRole('heading', { name: 'Anschreiben' })).toBeInTheDocument();
        expect(screen.getByText('3 Textbausteine')).toBeInTheDocument();
        expect(screen.getByRole('combobox', { name: 'Verwendet in' })).toHaveTextContent('Alle Dokumentarten (3)');
        fireEvent.click(screen.getByRole('button', { name: 'Neuer Textbaustein' }));
        const neu = await screen.findByRole('dialog', { name: 'Neuer Textbaustein' });
        within(neu).getAllByRole('switch').forEach((schalter) => expect(schalter).toHaveAttribute('aria-checked', 'false'));
    });
});
