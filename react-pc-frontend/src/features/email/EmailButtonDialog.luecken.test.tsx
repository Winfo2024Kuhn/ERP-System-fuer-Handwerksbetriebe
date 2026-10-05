import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { EmailButtonDialog } from './EmailButtonDialog';
import { BEWERTUNGS_URL_PLATZHALTER, MAX_BUTTON_TEXT_LAENGE, STANDARD_BUTTON_TEXT } from './emailButton';

function renderDialog(props: Partial<React.ComponentProps<typeof EmailButtonDialog>> = {}) {
    const onSchliessen = vi.fn();
    const onUebernehmen = vi.fn();
    render(<ToastProvider>
        <EmailButtonDialog offen vorhanden={null} onSchliessen={onSchliessen} onUebernehmen={onUebernehmen} {...props} />
    </ToastProvider>);
    return { onSchliessen, onUebernehmen };
}

describe('EmailButtonDialog', () => {
    it('Neu-Einfuegen startet mit Standardtext und Bewertungs-Ziel', async () => {
        const { onUebernehmen } = renderDialog();
        expect(screen.getByText('Button einfügen')).toBeInTheDocument();
        expect(screen.getByLabelText('Beschriftung')).toHaveValue(STANDARD_BUTTON_TEXT);
        expect(screen.getByRole('radio', { name: /Google-Bewertung/ })).toHaveAttribute('aria-checked', 'true');
        expect(screen.queryByLabelText('Internet-Adresse')).toBeNull();
        await userEvent.click(screen.getByRole('button', { name: 'Einfügen' }));
        expect(onUebernehmen).toHaveBeenCalledWith({ text: STANDARD_BUTTON_TEXT, href: BEWERTUNGS_URL_PLATZHALTER });
    });

    it('Vorhandener Button mit eigener Adresse wird vorbelegt und zeigt "Aendern"', async () => {
        const { onUebernehmen } = renderDialog({ vorhanden: { text: 'Zur Webseite', href: 'https://example.com/a' } });
        expect(screen.getByText('Button ändern')).toBeInTheDocument();
        expect(screen.getByLabelText('Beschriftung')).toHaveValue('Zur Webseite');
        expect(screen.getByRole('radio', { name: /Eigene Adresse/ })).toHaveAttribute('aria-checked', 'true');
        expect(screen.getByLabelText('Internet-Adresse')).toHaveValue('https://example.com/a');
        await userEvent.click(screen.getByRole('button', { name: 'Übernehmen' }));
        expect(onUebernehmen).toHaveBeenCalledWith({ text: 'Zur Webseite', href: 'https://example.com/a' });
    });

    it('Vorhandener Bewertungs-Button bleibt beim Bewertungs-Ziel', () => {
        renderDialog({ vorhanden: { text: 'Bewerten', href: BEWERTUNGS_URL_PLATZHALTER } });
        expect(screen.getByRole('radio', { name: /Google-Bewertung/ })).toHaveAttribute('aria-checked', 'true');
    });

    it('Eigene Adresse ohne Schema wird zu https:// ergaenzt', async () => {
        const { onUebernehmen } = renderDialog();
        await userEvent.click(screen.getByRole('radio', { name: /Eigene Adresse/ }));
        await userEvent.type(screen.getByLabelText('Internet-Adresse'), 'www.muster-firma.example.com');
        await userEvent.click(screen.getByRole('button', { name: 'Einfügen' }));
        expect(onUebernehmen).toHaveBeenCalledWith({ text: STANDARD_BUTTON_TEXT, href: 'https://www.muster-firma.example.com' });
    });

    it('Leere Beschriftung: Warnung, nichts wird uebernommen', async () => {
        const { onUebernehmen } = renderDialog();
        await userEvent.clear(screen.getByLabelText('Beschriftung'));
        await userEvent.type(screen.getByLabelText('Beschriftung'), '   ');
        await userEvent.click(screen.getByRole('button', { name: 'Einfügen' }));
        expect(onUebernehmen).not.toHaveBeenCalled();
        expect(await screen.findByText('Bitte eine Beschriftung für den Button eingeben.')).toBeInTheDocument();
    });

    it.each(['', 'javascript:alert(1)', 'data:text/html,<script>alert(1)</script>', 'ohne-punkt', 'ftp://example.com'])(
        'Ungueltige eigene Adresse "%s": Warnung, nichts wird uebernommen', async adresse => {
            const { onUebernehmen } = renderDialog();
            await userEvent.click(screen.getByRole('radio', { name: /Eigene Adresse/ }));
            if (adresse) await userEvent.type(screen.getByLabelText('Internet-Adresse'), adresse);
            await userEvent.click(screen.getByRole('button', { name: 'Einfügen' }));
            expect(onUebernehmen).not.toHaveBeenCalled();
            expect(await screen.findByText(/Bitte eine gültige Internet-Adresse eingeben/)).toBeInTheDocument();
        });

    it('Beschriftungsfeld ist auf die Maximallaenge begrenzt', () => {
        renderDialog();
        expect(screen.getByLabelText('Beschriftung')).toHaveAttribute('maxlength', String(MAX_BUTTON_TEXT_LAENGE));
    });

    it('Abbrechen ruft onSchliessen auf', async () => {
        const { onSchliessen, onUebernehmen } = renderDialog();
        await userEvent.click(screen.getByRole('button', { name: 'Abbrechen' }));
        expect(onSchliessen).toHaveBeenCalledTimes(1);
        expect(onUebernehmen).not.toHaveBeenCalled();
    });

    it('Geschlossen: nichts wird gerendert', () => {
        renderDialog({ offen: false });
        expect(screen.queryByText('Button einfügen')).toBeNull();
    });
});
