import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { SteuerberaterEmailModal } from './SteuerberaterEmailModal';

/** Briefanrede in der Stundenaufstellung an den Steuerberater (Dummy-Daten). */

function steuerberaterMit(ansprechpartner: { anrede: string | null; nachname: string }) {
    return [{
        id: 1,
        name: 'Kanzlei Mustermann',
        email: 'kanzlei@example.com',
        telefon: '',
        weitereEmails: [],
        ansprechpartnerListe: [{
            id: 2, vorname: 'Max', email: 'max@example.com', telefon: '', istLohnAnsprechpartner: true, ...ansprechpartner,
        }],
    }];
}

function starte(ansprechpartner: { anrede: string | null; nachname: string }) {
    global.fetch = vi.fn((url: string) => {
        if (url === '/api/firma/steuerberater') {
            return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(steuerberaterMit(ansprechpartner)) });
        }
        return Promise.resolve({ ok: false, status: 404, json: () => Promise.resolve(null) });
    }) as unknown as typeof fetch;
    const { container } = render(
        <SteuerberaterEmailModal isOpen onClose={() => { }} mitarbeiterDaten={[]} monat={10} jahr={2026} />,
    );
    return () => container.ownerDocument.querySelector('[contenteditable]') as HTMLElement | null;
}

/** Erst wenn der Ansprechpartner gewählt ist (Empfänger gesetzt), steht seine Anrede im Text. */
async function ansprechpartnerGewaehlt() {
    await screen.findByDisplayValue('max@example.com');
}

afterEach(() => vi.restoreAllMocks());

describe('SteuerberaterEmailModal: Anrede', () => {
    it('nimmt bei Herr mit Nachname die persönliche Anrede', async () => {
        const editor = starte({ anrede: 'HERR', nachname: 'Mustermann' });
        await waitFor(() => expect(editor()?.innerHTML).toContain('Sehr geehrter Herr Mustermann,'));
    });

    it('nimmt bei Herr ohne Nachname „Sehr geehrte Damen und Herren,“', async () => {
        const editor = starte({ anrede: 'HERR', nachname: '' });
        await ansprechpartnerGewaehlt();
        await waitFor(() => expect(editor()?.innerHTML).toContain('Sehr geehrte Damen und Herren,'));
        expect(editor()?.innerHTML).not.toContain('Sehr geehrter Herr');
    });

    it('maskiert HTML im Nachnamen', async () => {
        const editor = starte({ anrede: 'FRAU', nachname: '<img src=x onerror=alert(1)>' });
        await waitFor(() => expect(editor()?.innerHTML).toContain('Sehr geehrte Frau &lt;img src=x onerror=alert(1)&gt;,'));
        expect(editor()?.querySelector('img')).toBeNull();
    });
});
