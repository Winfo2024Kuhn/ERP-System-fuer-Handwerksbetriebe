import { fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { antwort, stubbeFetch } from './telefonTestdaten';
import { WaehlTelefonEinstellung } from './WaehlTelefonEinstellung';
import { WAEHL_TELEFON_SCHLUESSEL } from './useWaehlTelefon';

const TELEFONE = [{ name: 'LAN: PC Büro' }, { name: 'DECT: Mobilteil Büro' }];

function zeige() {
    stubbeFetch((url) => (url.pathname === '/api/telefon/telefone' ? antwort(TELEFONE) : undefined));
    return render(<ToastProvider><WaehlTelefonEinstellung /></ToastProvider>);
}

describe('WaehlTelefonEinstellung', () => {
    afterEach(() => {
        vi.unstubAllGlobals();
        window.localStorage.clear();
    });

    it('sagt, dass noch kein Telefon gewählt ist, und wählt eins aus', async () => {
        zeige();
        expect(screen.getByRole('heading', { name: 'Telefon an diesem Rechner' })).toBeInTheDocument();
        expect(screen.getByText(/Noch kein Telefon ausgewählt/)).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Zurücksetzen' })).toBeNull();
        expect(screen.getByText(/Nehmen Sie ab, wählt die FRITZ!Box die Nummer\./)).toBeInTheDocument();
        expect(document.body.textContent).not.toMatch(/\b(du|dein|dich|dir)\b/i);

        fireEvent.click(screen.getByRole('button', { name: 'Telefon auswählen' }));
        const dialog = await screen.findByRole('dialog', { name: 'Welches Telefon steht an diesem Rechner?' });
        fireEvent.click(await within(dialog).findByRole('radio', { name: 'DECT: Mobilteil Büro' }));
        fireEvent.click(within(dialog).getByRole('button', { name: 'Übernehmen' }));

        expect(screen.queryByRole('dialog')).toBeNull();
        expect(screen.getByText('DECT: Mobilteil Büro')).toBeInTheDocument();
        expect(window.localStorage.getItem(WAEHL_TELEFON_SCHLUESSEL)).toBe('DECT: Mobilteil Büro');
    });

    it('ändert das gespeicherte Telefon und setzt es zurück', async () => {
        window.localStorage.setItem(WAEHL_TELEFON_SCHLUESSEL, 'LAN: PC Büro');
        zeige();
        expect(screen.getByText('LAN: PC Büro')).toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: 'Ändern' }));
        const dialog = await screen.findByRole('dialog', { name: 'Welches Telefon steht an diesem Rechner?' });
        expect(await within(dialog).findByRole('radio', { name: 'LAN: PC Büro' })).toHaveAttribute('aria-checked', 'true');
        fireEvent.click(within(dialog).getByRole('button', { name: 'Abbrechen' }));
        expect(window.localStorage.getItem(WAEHL_TELEFON_SCHLUESSEL)).toBe('LAN: PC Büro');

        fireEvent.click(screen.getByRole('button', { name: 'Zurücksetzen' }));
        expect(screen.getByText(/Noch kein Telefon ausgewählt/)).toBeInTheDocument();
        expect(window.localStorage.getItem(WAEHL_TELEFON_SCHLUESSEL)).toBeNull();
    });

    it('klappt die Anleitung für Headset und Telefon-Programm auf', () => {
        zeige();
        const umschalter = screen.getByRole('button', { name: 'So telefonieren Sie am PC mit Headset' });
        expect(umschalter).toHaveAttribute('aria-expanded', 'false');
        expect(screen.queryByText(/MicroSIP/)).toBeNull();

        fireEvent.click(umschalter);
        expect(umschalter).toHaveAttribute('aria-expanded', 'true');
        const anleitung = screen.getByRole('list', { name: 'Anleitung: am PC mit Headset telefonieren' });
        expect(within(anleitung).getAllByRole('listitem')).toHaveLength(6);
        expect(anleitung).toHaveTextContent('MicroSIP');
        expect(anleitung).toHaveTextContent('Telefonie → Anrufe → Wählhilfe');
        expect(anleitung).toHaveTextContent('fritz.box');
        expect(anleitung).not.toHaveTextContent(/TR-064|Registrar/);
        const tipp = screen.getByText(/^Tipp:/);
        expect(tipp).toHaveTextContent('Tipp: In der FRITZ!Box unter Telefonie → Rufbehandlung → Rufsperren teure Sondernummern sperren (z. B. 0900, 0137, 118…).');
        expect(tipp).not.toHaveTextContent(/\b(du|dein|dich|dir)\b/i);
        expect(anleitung).not.toHaveTextContent(/\b(du|dein|dich|dir)\b/i);
    });
});
