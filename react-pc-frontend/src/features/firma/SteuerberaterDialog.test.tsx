import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';
import { SteuerberaterDialog } from './SteuerberaterDialog';
import { useSteuerberaterKontakte } from './useSteuerberaterKontakte';
import type { SteuerberaterKontakt } from './firmaTypen';

/** Dummy-Daten (DSGVO): Kanzlei Mustermann. */
function Harness() {
    const kontakte = useSteuerberaterKontakte();
    return (
        <>
            <button type="button" onClick={kontakte.neuAnlegen}>Öffnen</button>
            <button type="button" onClick={() => kontakte.bearbeiten({ id: 3, name: 'Kanzlei Mustermann', email: 'kanzlei@example.org', autoProcessEmails: true, aktiv: true } as SteuerberaterKontakt)}>Bearbeiten öffnen</button>
            <SteuerberaterDialog kontakte={kontakte} />
        </>
    );
}

const rendern = () => render(<ToastProvider><ConfirmProvider><Harness /></ConfirmProvider></ToastProvider>);

function antwort(body: unknown, ok = true): Response {
    return { ok, json: () => Promise.resolve(body), text: () => Promise.resolve(JSON.stringify(body)) } as Response;
}

afterEach(() => vi.unstubAllGlobals());

describe('SteuerberaterDialog', () => {
    it('hat einen zugänglichen Namen und alle Felder sind über ihr Label erreichbar', async () => {
        const user = userEvent.setup();
        rendern();
        await user.click(screen.getByRole('button', { name: 'Öffnen' }));
        const dialog = screen.getByRole('dialog', { name: 'Neuer Steuerberater' });
        const d = within(dialog);
        expect(d.getByLabelText('Name')).toBeInTheDocument();
        expect(d.getByLabelText('Telefon')).toBeInTheDocument();
        expect(d.getByLabelText('Haupt-E-Mail (für die Erkennung von BWA und Lohnabrechnungen) *')).toBeInTheDocument();
        expect(d.getByLabelText('Gültig ab')).toBeInTheDocument();
        expect(d.getByLabelText('Gültig bis')).toBeInTheDocument();
        const haken = d.getByRole('checkbox', { name: 'Auch andere E-Mail-Adressen der Kanzlei erkennen' });
        expect(haken).not.toHaveAttribute('title');
        expect(haken).not.toHaveAttribute('aria-label');
        expect(d.getByRole('button', { name: /Mehr zu/ })).toBeInTheDocument();

        await user.click(d.getByRole('button', { name: 'E-Mail hinzufügen' }));
        expect(d.getByLabelText('Weitere E-Mail-Adresse 1')).toBeInTheDocument();
        expect(d.getByRole('button', { name: 'Weitere E-Mail-Adresse 1 entfernen' })).toBeInTheDocument();
        await user.click(d.getByRole('button', { name: 'Ansprechpartner hinzufügen' }));
        for (const feld of ['Anrede', 'Vorname', 'Nachname *', 'E-Mail', 'Telefon']) {
            expect(d.getAllByLabelText(feld).length).toBeGreaterThan(0);
        }
        expect(d.getByLabelText('Vorname')).toBeInTheDocument();
        expect(d.getByLabelText('Nachname *')).toBeInTheDocument();
        expect(d.getByLabelText('Anrede')).toBeInTheDocument();
        expect(d.getByRole('button', { name: 'Ansprechpartner 1 entfernen' })).toBeInTheDocument();
    });

    it('speichert ohne Haupt-E-Mail nicht und nennt das fehlende Feld', async () => {
        const user = userEvent.setup();
        const fetchMock = vi.fn();
        vi.stubGlobal('fetch', fetchMock);
        rendern();
        await user.click(screen.getByRole('button', { name: 'Öffnen' }));
        const dialog = screen.getByRole('dialog', { name: 'Neuer Steuerberater' });
        await user.type(within(dialog).getByLabelText('Name'), 'Kanzlei Mustermann');
        await user.click(within(dialog).getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByText('Bitte Haupt-E-Mail eintragen.')).toBeVisible();
        expect(fetchMock).not.toHaveBeenCalled();
        expect(screen.getByRole('dialog', { name: 'Neuer Steuerberater' })).toBeInTheDocument();
    });

    it('heißt beim Bearbeiten „Steuerberater bearbeiten“', async () => {
        const user = userEvent.setup();
        rendern();
        await user.click(screen.getByRole('button', { name: 'Bearbeiten öffnen' }));
        expect(screen.getByRole('dialog', { name: 'Steuerberater bearbeiten' })).toBeInTheDocument();
    });

    it('sperrt Speichern und Abbrechen während des Speicherns, ein Doppelklick sendet nur einmal', async () => {
        const user = userEvent.setup();
        let antworten: (r: Response) => void = () => {};
        const fetchMock = vi.fn().mockImplementation((_url: string, init?: RequestInit) => init?.method === 'POST'
            ? new Promise<Response>(resolve => { antworten = resolve; })
            : Promise.resolve(antwort([])));
        vi.stubGlobal('fetch', fetchMock);
        rendern();
        await user.click(screen.getByRole('button', { name: 'Öffnen' }));
        const dialog = screen.getByRole('dialog', { name: 'Neuer Steuerberater' });
        await user.type(within(dialog).getByLabelText('Name'), 'Kanzlei Mustermann');
        await user.type(within(dialog).getByLabelText(/^Haupt-E-Mail/), 'kanzlei@example.org');
        const speichern = within(dialog).getByRole('button', { name: 'Speichern' });
        await user.dblClick(speichern);

        const gesperrt = within(dialog).getByRole('button', { name: 'Speichert …' });
        expect(gesperrt).toBeDisabled();
        expect(within(dialog).getByRole('button', { name: 'Abbrechen' })).toBeDisabled();
        expect(fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST')).toHaveLength(1);

        antworten(antwort({ id: 4 }));
        expect(await screen.findByText('Steuerberater gespeichert.')).toBeVisible();
        expect(screen.queryByRole('dialog', { name: 'Neuer Steuerberater' })).toBeNull();
    });
});
