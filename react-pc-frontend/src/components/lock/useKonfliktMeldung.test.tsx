import { useState } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ConfirmProvider } from '../ui/confirm-dialog';
import { useKonfliktMeldung } from './useKonfliktMeldung';

// Testkomponente: ruft pruefeAntwort mit der uebergebenen Response auf und
// schreibt das Ergebnis (true/false) sowie einen Bereitschafts-Status ins DOM,
// damit Tests darauf warten koennen.
function TestKomponente({
    response,
    bezeichnung,
}: {
    response: Response;
    bezeichnung?: string;
}) {
    const { pruefeAntwort } = useKonfliktMeldung(bezeichnung);
    const [ergebnis, setErgebnis] = useState<'offen' | 'true' | 'false'>('offen');

    const handlePruefen = async () => {
        const konflikt = await pruefeAntwort(response);
        setErgebnis(konflikt ? 'true' : 'false');
    };

    return (
        <div>
            <button onClick={handlePruefen}>Pruefen</button>
            <span data-testid="ergebnis">{ergebnis}</span>
        </div>
    );
}

function renderMitProvider(response: Response, bezeichnung?: string) {
    return render(
        <ConfirmProvider>
            <TestKomponente response={response} bezeichnung={bezeichnung} />
        </ConfirmProvider>
    );
}

describe('useKonfliktMeldung', () => {
    const reloadMock = vi.fn();

    beforeEach(() => {
        reloadMock.mockClear();
        Object.defineProperty(window, 'location', {
            configurable: true,
            value: { ...window.location, reload: reloadMock },
        });
    });

    it('liefert false und zeigt keine Meldung bei Status 200', async () => {
        const user = userEvent.setup();
        const response = new Response(JSON.stringify({ ok: true }), { status: 200 });
        renderMitProvider(response);

        await user.click(screen.getByText('Pruefen'));

        await waitFor(() => expect(screen.getByTestId('ergebnis')).toHaveTextContent('false'));
        expect(screen.queryByText('Nicht gespeichert')).not.toBeInTheDocument();
        expect(reloadMock).not.toHaveBeenCalled();
    });

    it.each([400, 401, 404, 500])(
        'behandelt Status %i nicht als Versionskonflikt',
        async status => {
            const user = userEvent.setup();
            const response = new Response(JSON.stringify({ message: 'Fehler' }), { status });
            renderMitProvider(response);

            await user.click(screen.getByText('Pruefen'));

            await waitFor(() => expect(screen.getByTestId('ergebnis')).toHaveTextContent('false'));
            expect(screen.queryByText('Nicht gespeichert')).not.toBeInTheDocument();
        }
    );

    it('zeigt bei Status 409 den exakten Wortlaut aus der Wording-Tabelle mit eingesetzter Bezeichnung und liefert true', async () => {
        const user = userEvent.setup();
        // Reale Form des Backend-409-Bodys (ApiError.message, siehe
        // RestExceptionHandler.handleOptimisticLockingFailure) -- constraint/
        // fields/detail fehlen wegen @JsonInclude(NON_EMPTY).
        const response = new Response(
            JSON.stringify({
                status: 409,
                message:
                    'Jemand anders hat diese Daten gerade gespeichert. Ihre Änderungen wurden nicht übernommen — bitte neu laden.',
            }),
            { status: 409 }
        );
        renderMitProvider(response, 'Dokument');

        await user.click(screen.getByText('Pruefen'));

        expect(await screen.findByText('Nicht gespeichert')).toBeInTheDocument();
        expect(
            screen.getByText(
                'Jemand anders hat dieses Dokument gerade gespeichert. Ihre Änderungen wurden nicht übernommen — bitte neu laden.'
            )
        ).toBeInTheDocument();
        expect(screen.getByText('Neu laden')).toBeInTheDocument();
        expect(screen.getByText('Abbrechen')).toBeInTheDocument();

        // pruefeAntwort() haengt am Promise von useConfirm(), bis der Nutzer
        // eine Wahl trifft -- ohne Klick bliebe "ergebnis" fuer immer "offen".
        await user.click(screen.getByText('Abbrechen'));

        await waitFor(() => expect(screen.getByTestId('ergebnis')).toHaveTextContent('true'));
    });

    it('setzt eine andere Bezeichnung korrekt in den Meldungstext ein', async () => {
        const user = userEvent.setup();
        const response = new Response(null, { status: 409 });
        renderMitProvider(response, 'Projekt');

        await user.click(screen.getByText('Pruefen'));

        expect(
            await screen.findByText(
                'Jemand anders hat dieses Projekt gerade gespeichert. Ihre Änderungen wurden nicht übernommen — bitte neu laden.'
            )
        ).toBeInTheDocument();
    });

    it('nutzt "Dokument" als Standard-Bezeichnung, wenn keine uebergeben wird', async () => {
        const user = userEvent.setup();
        const response = new Response(null, { status: 409 });
        renderMitProvider(response);

        await user.click(screen.getByText('Pruefen'));

        expect(
            await screen.findByText(
                'Jemand anders hat dieses Dokument gerade gespeichert. Ihre Änderungen wurden nicht übernommen — bitte neu laden.'
            )
        ).toBeInTheDocument();
    });

    it('Klick auf "Neu laden" loest window.location.reload genau einmal aus', async () => {
        const user = userEvent.setup();
        const response = new Response(null, { status: 409 });
        renderMitProvider(response, 'Dokument');

        await user.click(screen.getByText('Pruefen'));
        await screen.findByText('Nicht gespeichert');

        await user.click(screen.getByText('Neu laden'));

        await waitFor(() => expect(screen.getByTestId('ergebnis')).toHaveTextContent('true'));
        expect(reloadMock).toHaveBeenCalledTimes(1);
    });

    it('Klick auf "Abbrechen" loest keinen Reload aus, liefert aber weiterhin true', async () => {
        const user = userEvent.setup();
        const response = new Response(null, { status: 409 });
        renderMitProvider(response, 'Dokument');

        await user.click(screen.getByText('Pruefen'));
        await screen.findByText('Nicht gespeichert');

        await user.click(screen.getByText('Abbrechen'));

        await waitFor(() => expect(screen.getByTestId('ergebnis')).toHaveTextContent('true'));
        expect(reloadMock).not.toHaveBeenCalled();
    });

    it('zeigt die Meldung bei 409 ohne JSON-Body (Textkoerper) trotzdem an, ohne abzustuerzen', async () => {
        const user = userEvent.setup();
        const response = new Response('Internal Server Error (kein JSON)', { status: 409 });
        renderMitProvider(response, 'Dokument');

        await user.click(screen.getByText('Pruefen'));

        expect(await screen.findByText('Nicht gespeichert')).toBeInTheDocument();

        await user.click(screen.getByText('Abbrechen'));

        await waitFor(() => expect(screen.getByTestId('ergebnis')).toHaveTextContent('true'));
    });

    it('faerbt den "Neu laden"-Knopf rose statt amber -- gefuellte Primaeraktionen sind im Design-System rose, amber ist Warn-Icons vorbehalten (Task 8a)', async () => {
        const user = userEvent.setup();
        const response = new Response(null, { status: 409 });
        renderMitProvider(response, 'Dokument');

        await user.click(screen.getByText('Pruefen'));
        const neuLadenKnopf = await screen.findByText('Neu laden');

        expect(neuLadenKnopf.className).toContain('bg-rose-600');
        expect(neuLadenKnopf.className).not.toContain('bg-amber-500');

        await user.click(neuLadenKnopf);
        await waitFor(() => expect(screen.getByTestId('ergebnis')).toHaveTextContent('true'));
    });

    it('enthaelt keinen englischen Fachbegriff im gerenderten Dialog', async () => {
        const user = userEvent.setup();
        const response = new Response(null, { status: 409 });
        renderMitProvider(response, 'Dokument');

        await user.click(screen.getByText('Pruefen'));
        await screen.findByText('Nicht gespeichert');

        expect(document.body.textContent).not.toMatch(/lock|conflict|version/i);
    });
});
