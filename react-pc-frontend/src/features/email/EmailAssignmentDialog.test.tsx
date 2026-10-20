import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { AssignModal } from './EmailAssignmentDialog';

// DSGVO: nur Dummy-Firmen.
function antwort(body: unknown, status = 200) {
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function rendere(props: Partial<Parameters<typeof AssignModal>[0]> = {}) {
    const onAssign = vi.fn(() => Promise.resolve());
    const onClose = vi.fn();
    render(
        <ToastProvider>
            <AssignModal isOpen onClose={onClose} onAssign={onAssign} emailSubject="Lieferschein 4711" emailId={9} {...props} />
        </ToastProvider>
    );
    return { onAssign, onClose };
}

describe('AssignModal – Lieferant und Zuordnung aufheben', () => {
    let fetchMock: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        fetchMock = vi.fn((url: string) => {
            if (url.startsWith('/api/lieferanten?')) {
                return Promise.resolve(antwort({ lieferanten: [{ id: 33, lieferantenname: 'Musterstahl GmbH', ort: 'Musterstadt' }], gesamt: 1 }));
            }
            return Promise.resolve(antwort({ projekte: [], anfragen: [] }));
        });
        vi.stubGlobal('fetch', fetchMock);
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('sucht Lieferanten und ordnet die Mail dem gewählten zu', async () => {
        const user = userEvent.setup();
        const { onAssign, onClose } = rendere();

        await user.click(screen.getByRole('button', { name: 'Lieferant' }));
        await user.type(screen.getByPlaceholderText('Lieferant suchen...'), 'muster');

        await user.click(await screen.findByRole('button', { name: /Musterstahl GmbH/ }));

        expect(fetchMock.mock.calls.some(([url]) => String(url).startsWith('/api/lieferanten?q=muster'))).toBe(true);
        expect(onAssign).toHaveBeenCalledWith('lieferant', 33);
        await waitFor(() => expect(onClose).toHaveBeenCalled());
    });

    it('zeigt „Zuordnung aufheben“ nur bei zugeordneter Mail und ruft es auf', async () => {
        const user = userEvent.setup();
        const onUnassign = vi.fn(() => Promise.resolve());
        const { onClose } = rendere({ aktuelleZuordnung: 'Lieferant · Musterstahl GmbH', onUnassign });

        expect(screen.getByText('Lieferant · Musterstahl GmbH')).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Zuordnung aufheben' }));

        expect(onUnassign).toHaveBeenCalledTimes(1);
        expect(await screen.findByText('Zuordnung wurde aufgehoben.')).toBeInTheDocument();
        expect(onClose).toHaveBeenCalled();
    });

    it('bietet ohne Zuordnung kein Aufheben an', () => {
        rendere({ aktuelleZuordnung: null, onUnassign: vi.fn() });
        expect(screen.queryByRole('button', { name: 'Zuordnung aufheben' })).toBeNull();
    });

    it('meldet, wenn das Aufheben fehlschlägt, und lässt den Dialog offen', async () => {
        const user = userEvent.setup();
        const { onClose } = rendere({ aktuelleZuordnung: 'Projekt 2026-001', onUnassign: () => Promise.reject(new Error('404')) });

        await user.click(screen.getByRole('button', { name: 'Zuordnung aufheben' }));

        expect(await screen.findByText('Die Zuordnung konnte nicht aufgehoben werden.')).toBeInTheDocument();
        expect(onClose).not.toHaveBeenCalled();
    });
});
