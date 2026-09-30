import { fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { TelefonAuswahlDialog } from './TelefonAuswahlDialog';
import { antwort, stubbeFetch } from './telefonTestdaten';

const TELEFONE = [{ name: 'LAN: PC Büro' }, { name: 'DECT: Mobilteil Büro' }];

function zeige(teil: Partial<Parameters<typeof TelefonAuswahlDialog>[0]> = {}) {
    const onWaehlen = vi.fn();
    const onSchliessen = vi.fn();
    render(
        <ToastProvider>
            <TelefonAuswahlDialog
                offen
                aktuell={null}
                bestaetigenText="Anrufen"
                onWaehlen={onWaehlen}
                onSchliessen={onSchliessen}
                {...teil}
            />
        </ToastProvider>,
    );
    return { onWaehlen, onSchliessen };
}

describe('TelefonAuswahlDialog', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('zeigt die Telefone der FRITZ!Box als Auswahl und übernimmt erst nach Bestätigung', async () => {
        stubbeFetch((url) => (url.pathname === '/api/telefon/telefone' ? antwort(TELEFONE) : undefined));
        const { onWaehlen } = zeige();

        const dialog = screen.getByRole('dialog', { name: 'Welches Telefon steht an diesem Rechner?' });
        expect(within(dialog).getByText(/Die Auswahl gilt nur für diesen Rechner\./)).toBeInTheDocument();
        expect(within(dialog).getByText(/^Wählen Sie das Telefon, das an diesem Rechner klingeln soll/)).toBeInTheDocument();
        expect(within(dialog).getByRole('status', { name: 'Telefone werden geladen' })).toBeInTheDocument();

        const liste = await within(dialog).findByRole('radiogroup', { name: 'Telefon wählen' });
        expect(within(liste).getAllByRole('radio')).toHaveLength(2);
        const anrufen = within(dialog).getByRole('button', { name: 'Anrufen' });
        expect(anrufen).toBeDisabled();

        fireEvent.click(within(liste).getByRole('radio', { name: 'DECT: Mobilteil Büro' }));
        expect(within(liste).getByRole('radio', { name: 'DECT: Mobilteil Büro' })).toHaveAttribute('aria-checked', 'true');
        fireEvent.click(anrufen);
        expect(onWaehlen).toHaveBeenCalledWith('DECT: Mobilteil Büro');
    });

    it('markiert das aktuelle Telefon vorab', async () => {
        stubbeFetch((url) => (url.pathname === '/api/telefon/telefone' ? antwort(TELEFONE) : undefined));
        zeige({ aktuell: 'LAN: PC Büro', bestaetigenText: 'Übernehmen' });
        expect(await screen.findByRole('radio', { name: 'LAN: PC Büro' })).toHaveAttribute('aria-checked', 'true');
        expect(screen.getByRole('button', { name: 'Übernehmen' })).toBeEnabled();
    });

    it('zeigt Telefonnamen nur als Text, nie als HTML', async () => {
        stubbeFetch((url) => (url.pathname === '/api/telefon/telefone'
            ? antwort([{ name: '<img src=x onerror=alert(1)>' }])
            : undefined));
        zeige();
        expect(await screen.findByRole('radio', { name: '<img src=x onerror=alert(1)>' })).toBeInTheDocument();
        expect(document.querySelector('img')).toBeNull();
    });

    it('erklärt eine leere Liste verständlich', async () => {
        stubbeFetch((url) => (url.pathname === '/api/telefon/telefone' ? antwort([]) : undefined));
        zeige();
        expect(await screen.findByText(/In der FRITZ!Box ist noch kein Telefon eingerichtet/)).toBeInTheDocument();
        expect(screen.getByText(/So telefonieren Sie am PC mit Headset/)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Anrufen' })).toBeDisabled();
    });

    it('zeigt einen Ladefehler im Dialog und als Meldung und lädt auf Wunsch neu', async () => {
        const meldung = 'Die Telefon-Anbindung ist noch nicht eingerichtet.';
        let versuche = 0;
        stubbeFetch((url) => {
            if (url.pathname !== '/api/telefon/telefone') return undefined;
            versuche += 1;
            return versuche === 1 ? antwort({ message: meldung }, 409) : antwort(TELEFONE);
        });
        zeige();
        expect(await screen.findAllByText(meldung)).toHaveLength(2);
        fireEvent.click(screen.getByRole('button', { name: /Erneut laden/ }));
        expect(await screen.findByRole('radio', { name: 'LAN: PC Büro' })).toBeInTheDocument();
    });

    it('Abbrechen schließt ohne Auswahl', async () => {
        stubbeFetch((url) => (url.pathname === '/api/telefon/telefone' ? antwort(TELEFONE) : undefined));
        const { onWaehlen, onSchliessen } = zeige();
        await screen.findByRole('radiogroup', { name: 'Telefon wählen' });
        fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }));
        expect(onSchliessen).toHaveBeenCalled();
        expect(onWaehlen).not.toHaveBeenCalled();
    });
});
