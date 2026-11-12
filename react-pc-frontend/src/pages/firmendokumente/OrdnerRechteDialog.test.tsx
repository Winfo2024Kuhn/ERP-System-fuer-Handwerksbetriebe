import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { OrdnerRechteDialog } from './OrdnerRechteDialog';
import type { OrdnerRechte } from './typen';

vi.mock('./api', async (original) => ({ ...(await original<typeof import('./api')>()), ladeOrdnerRechte: vi.fn(), speichereOrdnerRechte: vi.fn() }));
const { ladeOrdnerRechte, speichereOrdnerRechte } = await import('./api');

const RECHTE: OrdnerRechte = {
    ordnerId: 1, ordnerName: 'Prüfungen', eingeschraenkt: false,
    abteilungen: [
        { abteilungId: 3, name: 'Büro', stufe: 'KEINE' },
        { abteilungId: 4, name: 'Werkstatt', stufe: 'ANSEHEN' },
    ],
};

function zeige(onGespeichert = vi.fn(), onAbbrechen = vi.fn()) {
    render(
        <ToastProvider>
            <OrdnerRechteDialog ordner={{ id: 1, name: 'Prüfungen' }} onAbbrechen={onAbbrechen} onGespeichert={onGespeichert} />
        </ToastProvider>,
    );
    return { onGespeichert, onAbbrechen };
}

const dialog = () => screen.getByRole('dialog', { name: 'Wer darf „Prüfungen“ sehen?' });
const reihe = (name: string) => within(dialog()).getByRole('radiogroup', { name: `Recht für „${name}“` });

afterEach(() => vi.clearAllMocks());

describe('OrdnerRechteDialog', () => {
    it('lädt die Rechte und zeigt „Alle Mitarbeiter“ mit beiden Info-Knöpfen', async () => {
        vi.mocked(ladeOrdnerRechte).mockResolvedValue(RECHTE);
        zeige();
        expect(within(dialog()).getByRole('status')).toHaveTextContent('Rechte werden geladen …');
        const auswahl = await within(dialog()).findByRole('radiogroup', { name: 'Wer darf den Ordner sehen?' });
        expect(ladeOrdnerRechte).toHaveBeenCalledWith(1);
        expect(within(auswahl).getByRole('radio', { name: 'Alle Mitarbeiter (sehen und ändern)' })).toHaveAttribute('aria-checked', 'true');
        // Bei „Alle“ ist die Abteilungstabelle weg.
        expect(within(dialog()).queryByRole('table')).toBeNull();
        expect(within(dialog()).getByRole('button', { name: 'Mehr zu: Wer darf das sehen?' })).toBeInTheDocument();
        expect(within(dialog()).getByRole('button', { name: 'Mehr zu: Alle oder nur bestimmte Abteilungen' })).toBeInTheDocument();
    });

    it('stellt auf „Nur bestimmte Abteilungen“ um, ändert Stufen und speichert den richtigen Body', async () => {
        const user = userEvent.setup();
        vi.mocked(ladeOrdnerRechte).mockResolvedValue(RECHTE);
        const gespeichert = { ...RECHTE, eingeschraenkt: true };
        vi.mocked(speichereOrdnerRechte).mockResolvedValue(gespeichert);
        const { onGespeichert } = zeige();
        await user.click(await within(dialog()).findByRole('radio', { name: 'Nur bestimmte Abteilungen' }));

        const tabelle = within(dialog()).getByRole('table', { name: 'Rechte je Abteilung' });
        expect(within(tabelle).getAllByRole('row')).toHaveLength(3);
        expect(within(reihe('Werkstatt')).getByRole('radio', { name: 'Ansehen' })).toHaveAttribute('aria-checked', 'true');
        expect(within(dialog()).queryByText('Nur Admins sehen diesen Ordner.')).toBeNull();
        await user.click(within(reihe('Büro')).getByRole('radio', { name: 'Ändern' }));
        await user.click(within(dialog()).getByRole('button', { name: 'Speichern' }));

        expect(speichereOrdnerRechte).toHaveBeenCalledWith(1, {
            eingeschraenkt: true,
            abteilungen: [{ abteilungId: 3, stufe: 'AENDERN' }, { abteilungId: 4, stufe: 'ANSEHEN' }],
        });
        expect(onGespeichert).toHaveBeenCalledWith(gespeichert);
    });

    it('weist darauf hin, wenn keine Abteilung Zugriff hat', async () => {
        const user = userEvent.setup();
        vi.mocked(ladeOrdnerRechte).mockResolvedValue({ ...RECHTE, eingeschraenkt: true });
        zeige();
        await user.click(within(await screen.findByRole('radiogroup', { name: 'Recht für „Werkstatt“' })).getByRole('radio', { name: 'Kein Zugriff' }));
        expect(within(dialog()).getByText('Nur Admins sehen diesen Ordner.')).toBeInTheDocument();
    });

    it('behält die Abteilungs-Einträge beim Zurückstellen auf „Alle“', async () => {
        const user = userEvent.setup();
        vi.mocked(ladeOrdnerRechte).mockResolvedValue({ ...RECHTE, eingeschraenkt: true });
        vi.mocked(speichereOrdnerRechte).mockResolvedValue(RECHTE);
        zeige();
        await user.click(await within(dialog()).findByRole('radio', { name: 'Alle Mitarbeiter (sehen und ändern)' }));
        expect(within(dialog()).queryByText('Nur Admins sehen diesen Ordner.')).toBeNull();
        await user.click(within(dialog()).getByRole('button', { name: 'Speichern' }));
        expect(speichereOrdnerRechte).toHaveBeenCalledWith(1, {
            eingeschraenkt: false,
            abteilungen: [{ abteilungId: 3, stufe: 'KEINE' }, { abteilungId: 4, stufe: 'ANSEHEN' }],
        });
    });

    it('zeigt Fehler beim Laden und Speichern im Dialog und als Toast', async () => {
        const user = userEvent.setup();
        vi.mocked(ladeOrdnerRechte).mockRejectedValueOnce(new Error('Rechte werden am obersten Ordner eingestellt. Sie gelten auch für alle Unterordner.'));
        const { onAbbrechen } = zeige();
        expect(await screen.findAllByText('Rechte werden am obersten Ordner eingestellt. Sie gelten auch für alle Unterordner.')).toHaveLength(2);
        expect(within(dialog()).getByRole('button', { name: 'Speichern' })).toBeDisabled();
        await user.click(within(dialog()).getByRole('button', { name: 'Abbrechen' }));
        expect(onAbbrechen).toHaveBeenCalled();
    });

    it('meldet einen gescheiterten Speicherversuch und bleibt offen', async () => {
        const user = userEvent.setup();
        vi.mocked(ladeOrdnerRechte).mockResolvedValue(RECHTE);
        vi.mocked(speichereOrdnerRechte).mockRejectedValue(new Error('Die Abteilung gibt es nicht mehr.'));
        const { onGespeichert } = zeige();
        await user.click(await within(dialog()).findByRole('button', { name: 'Speichern' }));
        expect(await screen.findAllByText('Die Abteilung gibt es nicht mehr.')).toHaveLength(2);
        expect(onGespeichert).not.toHaveBeenCalled();
        expect(within(dialog()).getByRole('button', { name: 'Speichern' })).toBeEnabled();
    });

    it('erklärt, wo man Abteilungen anlegt, wenn es keine gibt', async () => {
        const user = userEvent.setup();
        vi.mocked(ladeOrdnerRechte).mockResolvedValue({ ...RECHTE, abteilungen: [] });
        zeige();
        await user.click(await within(dialog()).findByRole('radio', { name: 'Nur bestimmte Abteilungen' }));
        expect(within(dialog()).getByText('Es gibt noch keine Abteilungen. Angelegt werden sie unter Einstellungen → Abteilungen & Rechte.')).toBeInTheDocument();
        expect(within(dialog()).getByText('Nur Admins sehen diesen Ordner.')).toBeInTheDocument();
    });

    it('zeigt Abteilungsnamen mit HTML als reinen Text', async () => {
        const user = userEvent.setup();
        vi.mocked(ladeOrdnerRechte).mockResolvedValue({ ...RECHTE, abteilungen: [{ abteilungId: 9, name: '<img src=x onerror=alert(1)>', stufe: 'KEINE' }] });
        zeige();
        await user.click(await within(dialog()).findByRole('radio', { name: 'Nur bestimmte Abteilungen' }));
        expect(within(dialog()).getByText('<img src=x onerror=alert(1)>')).toBeInTheDocument();
        expect(dialog().querySelector('img')).toBeNull();
    });
});
