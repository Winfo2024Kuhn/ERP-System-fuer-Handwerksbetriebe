import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { SchrittText } from './SchrittText';

vi.mock('../BeitragRichtextEditor', () => ({
    BeitragRichtextEditor: ({ html, onChange }: { html: string; onChange: (h: string) => void }) => (
        <textarea aria-label="Text" value={html} onChange={e => onChange(e.target.value)} />
    ),
}));

const entwurf = {
    titel: 'Balkonanlage erweitert',
    kurzbeschreibung: 'Kurz gefasst.',
    text: 'Erster Absatz.\n\nZweiter Absatz.',
    antwort: 'Vorschlag erstellt.',
};

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
    fetchMock = vi.fn(() => Promise.resolve({
        ok: true, status: 200, json: () => Promise.resolve(entwurf),
    }));
    vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

const leererStand = { titel: '', kurzbeschreibung: '', textHtml: '' };

function zeige(mitKi: boolean, stand = leererStand, onStandAendern = vi.fn()) {
    render(<SchrittText
        projektId={1}
        kiBilder={[]}
        vorschauBilder={[]}
        stand={stand}
        onStandAendern={onStandAendern}
        mitKi={mitKi}
    />);
    return onStandAendern;
}

describe('SchrittText', () => {
    it('startet ohne KI keinen Lauf', async () => {
        zeige(false);

        await waitFor(() => expect(fetchMock).not.toHaveBeenCalled());
    });

    it('holt beim KI-Weg sofort einen Vorschlag', async () => {
        const onStandAendern = zeige(true);

        await waitFor(() => {
            expect(onStandAendern).toHaveBeenCalledWith(expect.objectContaining({
                titel: 'Balkonanlage erweitert',
            }));
        });
    });

    it('macht aus dem Klartext der KI Absätze', async () => {
        const onStandAendern = zeige(true);

        await waitFor(() => {
            const letzter = onStandAendern.mock.calls.at(-1)?.[0];
            expect(letzter.textHtml).toBe('<p>Erster Absatz.</p><p>Zweiter Absatz.</p>');
        });
    });

    it('zeigt die Antwort der KI im Chat', async () => {
        zeige(true);

        expect(await screen.findByText('Vorschlag erstellt.')).toBeInTheDocument();
    });

    it('schickt beim Nachprompten den aktuellen Editor-Stand mit', async () => {
        const user = userEvent.setup();
        zeige(false, { titel: 'Von Hand', kurzbeschreibung: 'x', textHtml: '<p>Selbst geschrieben.</p>' });

        await user.type(screen.getByPlaceholderText(/Was soll geändert werden/), 'kuerzer bitte');
        await user.click(screen.getByRole('button', { name: 'Senden' }));

        await waitFor(async () => {
            const daten = fetchMock.mock.calls[0][1].body as FormData;
            // Der JSON-Teil liegt als Blob im FormData. Blob.text() liefert
            // immer ein Promise, deshalb hier awaiten statt synchron lesen.
            const teil = daten.get('anfrage') as unknown as { text: () => Promise<string> };
            const anfrage = JSON.parse(await teil.text());
            expect(anfrage.aktuellerTitel).toBe('Von Hand');
            expect(anfrage.aktuellerText).toContain('Selbst geschrieben');
        });
    });

    it('macht die letzte KI-Änderung rückgängig', async () => {
        const user = userEvent.setup();
        const vorher = { titel: 'Alt', kurzbeschreibung: 'Alt', textHtml: '<p>Alt</p>' };
        const onStandAendern = zeige(false, vorher);

        await user.type(screen.getByPlaceholderText(/Was soll geändert werden/), 'neu');
        await user.click(screen.getByRole('button', { name: 'Senden' }));
        await screen.findByRole('button', { name: 'Rückgängig' });
        onStandAendern.mockClear();

        await user.click(screen.getByRole('button', { name: 'Rückgängig' }));

        expect(onStandAendern).toHaveBeenCalledWith(vorher);
    });

    it('meldet einen Fehlschlag im Chat und lässt den Text stehen', async () => {
        const user = userEvent.setup();
        vi.stubGlobal('fetch', vi.fn(() => Promise.resolve({
            ok: false, status: 502, json: () => Promise.resolve({ message: 'kaputt' }),
        })));
        const stand = { titel: 'Bleibt', kurzbeschreibung: 'x', textHtml: '<p>Bleibt</p>' };
        const onStandAendern = zeige(false, stand);

        await user.type(screen.getByPlaceholderText(/Was soll geändert werden/), 'test');
        await user.click(screen.getByRole('button', { name: 'Senden' }));

        expect(await screen.findByText(/konnte gerade keinen Vorschlag/i)).toBeInTheDocument();
        expect(onStandAendern).not.toHaveBeenCalled();
    });

    it('sperrt Titel, Kurzbeschreibung, Text und Chat, während ein Lauf läuft', async () => {
        const user = userEvent.setup();
        let freigeben: (() => void) | undefined;
        fetchMock.mockImplementation(() => new Promise(resolve => {
            freigeben = () => resolve({ ok: true, status: 200, json: () => Promise.resolve(entwurf) });
        }));
        const stand = { titel: 'Stand', kurzbeschreibung: 'x', textHtml: '<p>Stand</p>' };
        zeige(false, stand);

        await user.type(screen.getByPlaceholderText(/Was soll geändert werden/), 'x');
        await user.click(screen.getByRole('button', { name: 'Senden' }));

        await waitFor(() => {
            expect(screen.getByDisplayValue('Stand')).toBeDisabled();
            expect(screen.getByLabelText('Kurzbeschreibung')).toBeDisabled();
            expect(screen.getByLabelText('Text').parentElement).toHaveAttribute('aria-disabled', 'true');
            expect(screen.getByPlaceholderText(/Was soll geändert werden/)).toBeDisabled();
            expect(screen.getByRole('button', { name: 'Senden' })).toBeDisabled();
        });

        freigeben?.();

        await waitFor(() => {
            expect(screen.getByDisplayValue('Stand')).not.toBeDisabled();
        });
    });
});
