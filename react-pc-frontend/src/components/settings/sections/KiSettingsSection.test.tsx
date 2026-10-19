import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../../../auth/AuthContext';
import { ToastProvider } from '../../ui/toast';
import { ConfirmProvider } from '../../ui/confirm-dialog';
import { KiSettingsSection } from './KiSettingsSection';
import { antwort, aufrufe, stubbeFetch } from '../../../features/telefon/telefonTestdaten';
import { admin } from '../../../features/ki/kiTestdaten';

/** Server-Stand: Ist ein Schlüssel hinterlegt? PUT trägt ihn ein. Nur Dummy-Schlüssel. */
function stubbeServer(schluesselDa: boolean) {
    const stand = { schluesselDa };
    const mock = stubbeFetch(
        (url) => (url.pathname === '/api/auth/me' ? antwort(admin({ kiAktiv: stand.schluesselDa })) : undefined),
        (url, init) => {
            if (url.pathname !== '/api/settings/gemini') return undefined;
            if (init?.method === 'PUT') {
                stand.schluesselDa = true;
                return antwort({});
            }
            if (init?.method === 'DELETE') {
                stand.schluesselDa = false;
                return antwort({ message: 'KI-Schlüssel entfernt.' });
            }
            return antwort({ apiKeySet: stand.schluesselDa });
        },
        (url) => (url.pathname === '/api/settings/anfrage-funnel-spamfilter' ? antwort({ aktiv: true }) : undefined),
    );
    return mock;
}

function zeige() {
    return render(<ToastProvider><ConfirmProvider><AuthProvider><KiSettingsSection /></AuthProvider></ConfirmProvider></ToastProvider>);
}

afterEach(() => vi.unstubAllGlobals());

describe('KiSettingsSection – Statuskasten', () => {
    it('sagt ohne Schlüssel ruhig „KI-Funktionen sind aus“ und was wegfällt', async () => {
        stubbeServer(false);
        zeige();

        const titel = await screen.findByRole('heading', { name: 'KI-Funktionen sind aus' });
        const kasten = titel.closest('section')!;
        expect(kasten).toHaveClass('bg-slate-50');
        expect(within(kasten).getByText(/Das Programm funktioniert trotzdem vollständig/)).toBeInTheDocument();
        expect(within(kasten).getByText('KI-Hilfe in der Menüleiste')).toBeInTheDocument();
        expect(within(kasten).getByText('Spracheingabe in der Handy-App')).toBeInTheDocument();
        expect(within(kasten).getByText(/von Hand eingetragen/)).toHaveTextContent('E-Rechnungen (ZUGFeRD, XRechnung) liest das Programm weiter selbst.');
        // Der Spam-Filter läuft über ein eigenes Sprachmodell, nicht über Gemini – er fällt also nicht weg.
        expect(within(kasten).queryByText(/Spaß-Anfragen/)).not.toBeInTheDocument();
        // Der Schlüssel ist ausdrücklich freiwillig.
        expect(screen.getByText('Freiwillig.')).toBeInTheDocument();
    });

    it('zeigt mit Schlüssel einen grünen Kasten „KI-Funktionen sind an“', async () => {
        stubbeServer(true);
        zeige();

        const titel = await screen.findByRole('heading', { name: 'KI-Funktionen sind an' });
        expect(titel.closest('section')).toHaveClass('bg-emerald-50');
        expect(screen.queryByText('Spracheingabe in der Handy-App')).not.toBeInTheDocument();
    });

    it('erklärt per Info-Knopf, was im Hintergrund passiert', async () => {
        stubbeServer(false);
        const user = userEvent.setup();
        zeige();

        await user.click(await screen.findByRole('button', { name: 'Mehr zu: KI-Funktionen' }));

        const info = screen.getByRole('dialog', { name: 'KI-Funktionen' });
        expect(info).toHaveTextContent('Was passiert im Hintergrund?');
        expect(info).toHaveTextContent('Google Gemini');
        expect(info).toHaveTextContent('Ohne Schlüssel verlässt nichts davon den Betrieb.');
        expect(info).toHaveTextContent('Was bewirkt das?');
    });
});

describe('KiSettingsSection – Speichern', () => {
    it('lädt nach dem Speichern die Anmeldung neu, damit „KI an“ sofort überall gilt', async () => {
        const mock = stubbeServer(false);
        const user = userEvent.setup();
        zeige();

        await screen.findByRole('heading', { name: 'KI-Funktionen sind aus' });
        await waitFor(() => expect(aufrufe(mock, '/api/auth/me')).toHaveLength(1));

        await user.type(screen.getByLabelText(/KI-Schlüssel/), 'AIza-dummy-schluessel');
        await user.click(screen.getByRole('button', { name: /Schlüssel speichern/ }));

        expect(await screen.findByRole('heading', { name: 'KI-Funktionen sind an' })).toBeInTheDocument();
        expect(aufrufe(mock, '/api/settings/gemini', 'PUT')).toHaveLength(1);
        expect(aufrufe(mock, '/api/auth/me')).toHaveLength(2);
        expect(await screen.findByText('KI-Schlüssel gespeichert.')).toBeInTheDocument();
    });

    it('funktioniert auch ohne Anmelde-Rahmen (z. B. in der Einstellungs-Übersicht im Test)', async () => {
        const mock = stubbeServer(false);
        const user = userEvent.setup();
        render(<ToastProvider><ConfirmProvider><KiSettingsSection /></ConfirmProvider></ToastProvider>);

        await screen.findByRole('heading', { name: 'KI-Funktionen sind aus' });
        await user.type(screen.getByLabelText(/KI-Schlüssel/), 'AIza-dummy-schluessel');
        await user.click(screen.getByRole('button', { name: /Schlüssel speichern/ }));

        expect(await screen.findByRole('heading', { name: 'KI-Funktionen sind an' })).toBeInTheDocument();
        expect(aufrufe(mock, '/api/auth/me')).toHaveLength(0);
    });
});

describe('KiSettingsSection – Schlüssel entfernen', () => {
    it('zeigt „Schlüssel entfernen“ nur mit gespeichertem Schlüssel', async () => {
        stubbeServer(false);
        zeige();
        await screen.findByRole('heading', { name: 'KI-Funktionen sind aus' });
        expect(screen.queryByRole('button', { name: /Schlüssel entfernen/ })).not.toBeInTheDocument();
    });

    it('fragt nach, löscht den Schlüssel und lädt Status und Anmeldung neu', async () => {
        const mock = stubbeServer(true);
        const user = userEvent.setup();
        zeige();

        await screen.findByRole('heading', { name: 'KI-Funktionen sind an' });
        await waitFor(() => expect(aufrufe(mock, '/api/auth/me')).toHaveLength(1));

        // Info-Knopf erklärt, was wegfällt und was bleibt.
        await user.click(screen.getByRole('button', { name: 'Mehr zu: Schlüssel entfernen' }));
        const info = screen.getByRole('dialog', { name: 'Schlüssel entfernen' });
        expect(info).toHaveTextContent('Das Programm fragt danach keine KI mehr an.');
        expect(info).toHaveTextContent('Schon ausgelesene Daten bleiben erhalten.');
        await user.keyboard('{Escape}');

        // Abbrechen löscht nichts.
        await user.click(screen.getByRole('button', { name: /^Schlüssel entfernen$/ }));
        await user.click(within(await screen.findByRole('dialog', { name: 'KI-Schlüssel entfernen?' })).getByRole('button', { name: 'Abbrechen' }));
        expect(aufrufe(mock, '/api/settings/gemini', 'DELETE')).toHaveLength(0);

        await user.click(screen.getByRole('button', { name: /^Schlüssel entfernen$/ }));
        const rueckfrage = await screen.findByRole('dialog', { name: 'KI-Schlüssel entfernen?' });
        await user.click(within(rueckfrage).getByRole('button', { name: 'Schlüssel entfernen' }));

        expect(await screen.findByRole('heading', { name: 'KI-Funktionen sind aus' })).toBeInTheDocument();
        expect(aufrufe(mock, '/api/settings/gemini', 'DELETE')).toHaveLength(1);
        expect(aufrufe(mock, '/api/auth/me')).toHaveLength(2);
        expect(await screen.findByText('KI-Schlüssel entfernt.')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /^Schlüssel entfernen$/ })).not.toBeInTheDocument();
    });

    it('zeigt die Meldung vom Server, wenn das Entfernen nicht klappt', async () => {
        stubbeFetch(
            (url) => (url.pathname === '/api/auth/me' ? antwort(admin({ kiAktiv: true })) : undefined),
            (url, init) => {
                if (url.pathname !== '/api/settings/gemini') return undefined;
                if (init?.method === 'DELETE') return antwort({ status: 403, message: 'Nur Administratoren dürfen das.' }, 403);
                return antwort({ apiKeySet: true });
            },
            (url) => (url.pathname === '/api/settings/anfrage-funnel-spamfilter' ? antwort({ aktiv: true }) : undefined),
        );
        const user = userEvent.setup();
        zeige();

        await user.click(await screen.findByRole('button', { name: /^Schlüssel entfernen$/ }));
        await user.click(within(await screen.findByRole('dialog', { name: 'KI-Schlüssel entfernen?' })).getByRole('button', { name: 'Schlüssel entfernen' }));
        expect(await screen.findByText('Nur Administratoren dürfen das.')).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'KI-Funktionen sind an' })).toBeInTheDocument();
    });
});

describe('KiSettingsSection – Wortwahl und Spam-Filter', () => {
    it('spricht von „KI-Schlüssel“ statt „API Key“', async () => {
        stubbeServer(false);
        zeige();

        expect(await screen.findByRole('heading', { name: /KI-Schlüssel \(Google Gemini\)/ })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Schlüssel testen/ })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Schlüssel speichern/ })).toBeInTheDocument();
        expect(screen.queryByText(/API Key|API testen|Gemini Key/)).not.toBeInTheDocument();
    });

    it('erklärt beim Spam-Filter, dass er ohne Gemini-Schlüssel läuft und keine Anfrage abweist', async () => {
        stubbeServer(false);
        zeige();

        const beschreibung = await screen.findByText(/prüft ein Sprachmodell jede neue Anfrage/);
        expect(beschreibung).toHaveTextContent('Dafür braucht es keinen Gemini-Schlüssel');
        // Ehrlich zum tatsächlichen Ablauf: gespeichert und bestätigt wird immer, geprüft erst danach.
        expect(beschreibung).toHaveTextContent('Die Anfrage wird trotzdem immer gespeichert');
        expect(beschreibung).toHaveTextContent('löschen Sie von Hand unter „Anfragen“');
        expect(screen.queryByText(/nicht gesendet werden konnte|blockiert/)).not.toBeInTheDocument();
    });
});
