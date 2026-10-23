import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { OrdnerLoeschenDialog } from './OrdnerLoeschenDialog';
import { baueBaum, leistungsZaehler, zieleFuerUmzug } from './ordnerLogik';
import type { Leistung, Loeschpruefung, Ordner } from './typen';

const o = (id: number, name: string, parentId: number | null, einheit: Ordner['einheit'] = 'LAUFENDE_METER'): Ordner => ({
    id, name, parentId, bildUrl: null, beschreibung: '', einheit,
    einheitAnzeige: einheit === 'QUADRATMETER' ? 'Quadratmeter' : 'Laufende Meter', projektAnzahl: 0,
});
const ORDNER = [o(1, 'Metallbau', null), o(2, 'Geländer', 1), o(3, 'Treppen', 1), o(4, 'Holzbau', null, 'QUADRATMETER')];
const LEISTUNGEN: Leistung[] = [
    { id: 10, name: 'Geländer verzinkt', beschreibung: '', preis: 1, einheit: 'STUECK', ordnerId: 2 },
    { id: 14, name: 'Wangentreppe', beschreibung: '', preis: 1, einheit: 'STUECK', ordnerId: 3 },
];
const baum = baueBaum(ORDNER);

const PRUEFUNG: Loeschpruefung = {
    kategorieId: 3, bezeichnung: 'Treppen', pfad: 'Metallbau > Treppen', hatBezuege: true,
    unterordnerAnzahl: 0, leistungAnzahl: 1, projektAnzahl: 23, zeitbuchungAnzahl: 41,
    unterordner: [],
    leistungen: [{ id: 14, name: 'Wangentreppe', ordnerId: 3 }],
    projekte: Array.from({ length: 20 }, (_, i) => ({ id: i + 1, bauvorhaben: `Neubau Mustermann ${i + 1}`, auftragsnummer: i === 0 ? null : `2026-${i}`, kunde: 'Max Mustermann' })),
};

function zeige(onLoeschen = vi.fn().mockResolvedValue(undefined), pruefung = PRUEFUNG) {
    render(
        <ToastProvider>
            <OrdnerLoeschenDialog
                pruefung={pruefung}
                ordner={baum.byId.get(pruefung.kategorieId)}
                ziele={zieleFuerUmzug(baum, leistungsZaehler(baum, LEISTUNGEN), pruefung.kategorieId)}
                ordnerVon={(id) => baum.byId.get(id)}
                onAbbrechen={vi.fn()}
                onLoeschen={onLoeschen}
            />
        </ToastProvider>,
    );
    return onLoeschen;
}

describe('OrdnerLoeschenDialog', () => {
    it('erklärt, was drinsteckt – mit „und N weitere“ und den Arbeitszeiten', () => {
        zeige();
        expect(screen.getByRole('dialog', { name: '„Treppen“ wird noch verwendet' })).toBeInTheDocument();
        expect(screen.getByText('23 Projekte')).toBeInTheDocument();
        expect(screen.getByText('41 Arbeitszeiten')).toBeInTheDocument();
        expect(screen.getByText('und 3 weitere')).toBeInTheDocument();
        expect(screen.getByText('Wangentreppe')).toBeInTheDocument();
        expect(screen.queryByText(/Unterordner \(/)).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Mehr zu: In anderen Ordner umziehen' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Mehr zu: Ordner und Inhalt löschen' })).toBeInTheDocument();
    });

    it('Umziehen ist vorausgewählt; ungültige Ziele sind gesperrt und nennen den Grund', () => {
        zeige();
        expect(screen.getByRole('radio', { name: /In anderen Ordner umziehen/ })).toBeChecked();
        expect(screen.getByRole('radio', { name: /^Metallbau/ })).toBeDisabled();
        expect(screen.getByRole('radio', { name: /^Treppen/ })).toBeDisabled();
        expect(screen.getByText('Das ist der Ordner selbst')).toBeInTheDocument();
        expect(screen.getByRole('radio', { name: /^Geländer/ })).toBeEnabled();
        expect(screen.getByRole('button', { name: 'Umziehen und löschen' })).toBeDisabled();
        expect(screen.getByText('Bitte zuerst einen Ziel-Ordner wählen.')).toBeInTheDocument();
    });

    it('warnt bei anderer Einheit und schickt UMZIEHEN mit Ziel', async () => {
        const nutzer = userEvent.setup();
        const onLoeschen = zeige();
        await nutzer.click(screen.getByRole('radio', { name: /^Holzbau/ }));
        expect(screen.getByText(/„Holzbau“ zählt in Quadratmeter, „Treppen“ in Laufende Meter/)).toBeInTheDocument();
        await nutzer.click(screen.getByRole('radio', { name: /^Geländer/ }));
        expect(screen.queryByText(/zählt in Quadratmeter/)).not.toBeInTheDocument();
        await nutzer.click(screen.getByRole('button', { name: 'Umziehen und löschen' }));
        expect(onLoeschen).toHaveBeenCalledWith('UMZIEHEN', 2);
    });

    it('„Ordner und Inhalt löschen“ nennt die Folgen und schickt ALLES', async () => {
        const nutzer = userEvent.setup();
        const onLoeschen = zeige();
        await nutzer.click(screen.getByRole('radio', { name: /Ordner und Inhalt löschen/ }));
        expect(screen.getByText(/Löscht auch 1 Leistung\. 23 Projekte verlieren diese Kategorie/)).toBeInTheDocument();
        await nutzer.click(screen.getByRole('button', { name: 'Endgültig löschen' }));
        expect(onLoeschen).toHaveBeenCalledWith('ALLES', null);
    });

    it('zeigt die Meldung des Servers im Dialog', async () => {
        const nutzer = userEvent.setup();
        zeige(vi.fn().mockRejectedValue(new Error('„Geländer“ hat Unterordner. Leistungen können nur in einem Ordner ohne Unterordner liegen.')));
        await nutzer.click(screen.getByRole('radio', { name: /^Geländer/ }));
        await nutzer.click(screen.getByRole('button', { name: 'Umziehen und löschen' }));
        await waitFor(() => expect(screen.getAllByText(/hat Unterordner\. Leistungen können nur/).length).toBeGreaterThan(0));
        expect(screen.getByRole('button', { name: 'Umziehen und löschen' })).toBeEnabled();
    });

    it('sagt, wenn es keinen passenden Ziel-Ordner gibt', () => {
        render(
            <ToastProvider>
                <OrdnerLoeschenDialog pruefung={{ ...PRUEFUNG, projektAnzahl: 0, projekte: [], zeitbuchungAnzahl: 0, leistungAnzahl: 0, leistungen: [] }}
                    ordner={undefined} ziele={[{ id: 3, name: 'Treppen', ebene: 0, gesperrt: 'Das ist der Ordner selbst' }]}
                    ordnerVon={() => undefined} onAbbrechen={vi.fn()} onLoeschen={vi.fn()} />
            </ToastProvider>,
        );
        expect(screen.getByText(/Es gibt keinen passenden Ziel-Ordner/)).toBeInTheDocument();
    });
});
