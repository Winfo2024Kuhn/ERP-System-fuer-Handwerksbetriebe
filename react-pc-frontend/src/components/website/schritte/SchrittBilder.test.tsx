import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { SchrittBilder } from './SchrittBilder';

vi.mock('../BildEditorModal', () => ({
    BildEditorModal: ({ offen }: { offen: boolean }) =>
        offen ? <div data-testid="bild-editor" /> : null,
}));

const notizen = [{
    notiz: 'Tor am Montag montiert',
    erstelltAm: '2026-08-20T10:00:00',
    bilder: [{
        id: 1, originalDateiname: 'tor.jpg',
        url: '/api/dokumente/tor.jpg', thumbnailUrl: '/api/dokumente/tor.jpg/thumbnail',
        erstelltAm: '2026-08-20T10:00:00',
    }],
}];

const dokumente = [
    {
        id: 7, originalDateiname: 'plan.pdf', dateityp: 'application/pdf',
        url: '/api/dokumente/plan.pdf', thumbnailUrl: '',
        dokumentGruppe: 'PLANUNGSDOKUMENTE', uploadDatum: '2026-08-01',
    },
    {
        id: 8, originalDateiname: 'halle.jpg', dateityp: 'image/jpeg',
        url: '/api/dokumente/halle.jpg', thumbnailUrl: '/api/dokumente/halle.jpg/thumbnail',
        dokumentGruppe: 'BILDER', uploadDatum: '2026-08-02',
    },
];

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
    fetchMock = vi.fn((url: string) => Promise.resolve({
        ok: true, status: 200,
        json: () => Promise.resolve(url.includes('/notizen') ? notizen : dokumente),
    }));
    vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

function zeige(auswahl: never[] = [], onAuswahlAendern = vi.fn()) {
    render(<SchrittBilder projektId={1} auswahl={auswahl} onAuswahlAendern={onAuswahlAendern} />);
    return onAuswahlAendern;
}

describe('SchrittBilder', () => {
    it('zeigt beide Quellen getrennt an', async () => {
        zeige();

        expect(await screen.findByText('Aus dem Bautagebuch')).toBeInTheDocument();
        expect(screen.getByText('Aus den Projektdokumenten')).toBeInTheDocument();
    });

    it('zeigt den Notiztext als Hinweis am Bautagebuch-Bild', async () => {
        zeige();

        expect(await screen.findByText(/Tor am Montag montiert/)).toBeInTheDocument();
    });

    it('nimmt nur Dokumente aus der Gruppe BILDER auf', async () => {
        zeige();
        await screen.findByText('Aus dem Bautagebuch');

        expect(screen.getByAltText('halle.jpg')).toBeInTheDocument();
        expect(screen.queryByAltText('plan.pdf')).not.toBeInTheDocument();
    });

    it('meldet eine Auswahl nach oben', async () => {
        const user = userEvent.setup();
        const onAuswahlAendern = zeige();
        await screen.findByAltText('tor.jpg');

        await user.click(screen.getByAltText('tor.jpg'));

        expect(onAuswahlAendern).toHaveBeenCalledWith([
            expect.objectContaining({ bild: expect.objectContaining({ schluessel: 'notiz-1' }) }),
        ]);
    });

    it('zählt die Auswahl mit', async () => {
        zeige([{ bild: { schluessel: 'notiz-1' }, bearbeitung: {} }] as never);

        expect(await screen.findByText(/1 Bild ausgewählt/)).toBeInTheDocument();
    });

    it('öffnet den Bildeditor für ein ausgewähltes Bild', async () => {
        const user = userEvent.setup();
        zeige([{
            bild: {
                schluessel: 'notiz-1', quelle: 'bautagebuch',
                url: '/api/dokumente/tor.jpg', thumbnailUrl: '/api/dokumente/tor.jpg/thumbnail',
                originalDateiname: 'tor.jpg', datum: null, hinweis: null,
            },
            bearbeitung: {},
        }] as never);
        await screen.findByAltText('tor.jpg');

        await user.click(screen.getByRole('button', { name: 'tor.jpg bearbeiten' }));

        expect(screen.getByTestId('bild-editor')).toBeInTheDocument();
    });

    it('erklärt den Leerzustand', async () => {
        vi.stubGlobal('fetch', vi.fn(() => Promise.resolve({
            ok: true, status: 200, json: () => Promise.resolve([]),
        })));
        zeige();

        expect(await screen.findByText(/keine Bilder/i)).toBeInTheDocument();
    });
});
