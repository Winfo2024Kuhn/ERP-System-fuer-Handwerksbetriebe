/**
 * Vitest-Suite fuer den TextbausteinEditor – Schwerpunkt: Vorschau (XSS).
 *
 * Das Vorlagen-HTML kommt ungeprueft aus der Datenbank. Die Vorschau darf es
 * nur bereinigt anzeigen; Platzhalter-Chips und eingesetzte Kundendaten
 * muessen dabei erhalten bleiben.
 *
 * DSGVO: ausschliesslich Dummy-Daten.
 */
import { render, screen } from '@testing-library/react';
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

beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn((url: string) => {
        if (url === '/api/textbausteine') return antwort([{ id: 1, name: 'Anschreiben', html: VORLAGE_HTML }]);
        if (url === '/api/kunden') return antwort({ kunden: [{ id: 7, name: 'Max Mustermann' }] });
        return antwort([]);
    }));
});

afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe('TextbausteinEditor Vorschau', () => {
    it('zeigt das Vorlagen-HTML nur bereinigt, mit Kundendaten und Platzhalter-Chip', async () => {
        const { container } = render(
            <ToastProvider><ConfirmProvider><TextbausteinEditor /></ConfirmProvider></ToastProvider>,
        );

        const vorschauText = await screen.findByText(/Guten Tag Max Mustermann/);
        const vorschau = vorschauText.closest('.rich-text-einheitlich')!;

        expect(vorschau.querySelector('[data-preview-placeholder="true"]')).toHaveTextContent('{{BAUVORHABEN}}');
        expect(vorschau.querySelector('script')).toBeNull();
        expect(vorschau.querySelector('[onerror]')).toBeNull();
        expect(container.querySelector('[onerror]')).toBeNull();
    });
});
