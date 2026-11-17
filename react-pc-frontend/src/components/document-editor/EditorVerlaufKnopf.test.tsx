import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../ui/toast';
import { ConfirmProvider } from '../ui/confirm-dialog';
import { EditorVerlaufKnopf } from './EditorVerlaufKnopf';
import type { AusgangsGeschaeftsDokument } from './types';

const DOKUMENT = { id: 1, dokumentNummer: 'AG-2026/11/00001', typ: 'ANGEBOT', datum: '2026-11-17', gebucht: false, storniert: false, bearbeitbar: true } as AusgangsGeschaeftsDokument;

beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
        staende: [], gesamt: 0, seite: 0, seitenGroesse: 25, hatMehr: false, offeneAenderungen: null, dokumentBearbeitbar: true,
    }), { status: 200 })));
});
afterEach(() => vi.unstubAllGlobals());

function zeige(vorOeffnen: () => Promise<void>) {
    render(<ToastProvider><ConfirmProvider><EditorVerlaufKnopf dokument={DOKUMENT} vorOeffnen={vorOeffnen} /></ConfirmProvider></ToastProvider>);
}

describe('EditorVerlaufKnopf', () => {
    it('speichert vorher und öffnet dann den Verlauf', async () => {
        const vorOeffnen = vi.fn().mockResolvedValue(undefined);
        zeige(vorOeffnen);
        await userEvent.click(screen.getByRole('button', { name: 'Verlauf' }));
        expect(vorOeffnen).toHaveBeenCalled();
        expect(await screen.findByRole('dialog', { name: /Verlauf/ })).toBeInTheDocument();
    });

    it('meldet, wenn das Speichern vorher scheitert, und öffnet den Verlauf trotzdem', async () => {
        zeige(vi.fn().mockRejectedValue(new Error('weg')));
        await userEvent.click(screen.getByRole('button', { name: 'Verlauf' }));
        expect(await screen.findByText('Ihre letzten Änderungen konnten nicht gespeichert werden. Der Verlauf zeigt den zuletzt gespeicherten Inhalt.')).toBeInTheDocument();
        expect(await screen.findByRole('dialog', { name: /Verlauf/ })).toBeInTheDocument();
    });
});
