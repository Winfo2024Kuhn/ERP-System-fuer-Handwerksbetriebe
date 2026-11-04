import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import EmailsTab from './EmailsTab';
import { ToastProvider } from './ui/toast';
import { mitReiter, reiterEintrag, type ReiterEintragRoh } from '../features/email/emailsTab/emailsTabTestApi';

/**
 * Regressionstest fuer die Rueckläufer-Warnung im E-Mail-Reiter.
 *
 * Hintergrund: Ein Angebot ging an eine nicht existierende Adresse. Der
 * SMTP-Versand galt als erfolgreich, die Ablehnung kam erst danach zurueck —
 * in der Akte sah die nie angekommene Mail exakt aus wie eine erfolgreich
 * versendete.
 *
 * Der Test haengt bewusst an `EmailsTab`: das ist die Komponente, die
 * Kunden-, Projekt-, Anfrage- und Lieferanten-Editor tatsaechlich rendern.
 *
 * Alle Daten sind Dummy-Daten (DSGVO).
 */

afterEach(() => vi.unstubAllGlobals());

function baueMail(overrides: Partial<ReiterEintragRoh> = {}): ReiterEintragRoh {
    return reiterEintrag(1, {
        subject: 'Angebot AG-2026/07/00005',
        fromAddress: 'firma@example.com',
        recipient: 'max.mustermann@example.com',
        direction: 'OUT',
        sentAt: '2026-07-17T08:02:15',
        bodyHtml: '<p>im Anhang finden Sie das Angebot</p>',
        ...overrides,
    });
}

async function rendere(email: ReiterEintragRoh) {
    vi.stubGlobal('fetch', vi.fn(mitReiter([email])));
    render(
        <MemoryRouter>
            <ToastProvider>
                <EmailsTab bezug="kunde" bezugId={1} showComposeButton={false} showReplyButton={false} />
            </ToastProvider>
        </MemoryRouter>
    );
    await screen.findByText('Angebot AG-2026/07/00005');
}

describe('EmailsTab – Zustell-Warnung', () => {
    it('warnt sichtbar, wenn eine Ausgangsmail nicht zugestellt werden konnte', async () => {
        await rendere(baueMail({
            zustellStatus: 'UNZUSTELLBAR',
            zustellFehler: 'unknown user / Teilnehmer existiert nicht',
        }));

        expect(screen.getByText('Nicht angekommen')).toBeInTheDocument();
        expect(screen.getByText(/Bitte Adresse prüfen und erneut senden/)).toBeInTheDocument();
    });

    it('nennt den Grund in Handwerker-Sprache statt im Server-Kauderwelsch', async () => {
        await rendere(baueMail({
            zustellStatus: 'UNZUSTELLBAR',
            zustellFehler: '550 5.1.1 <max.mustermann@example.com> User unknown',
        }));

        expect(screen.getByText('Diese E-Mail-Adresse gibt es nicht. Bitte Adresse prüfen und erneut senden.'))
            .toBeInTheDocument();
        // Der Rohtext bleibt zum Nachschlagen erhalten, steht aber nicht im Weg.
        expect(screen.getByTitle('550 5.1.1 <max.mustermann@example.com> User unknown'))
            .toBeInTheDocument();
    });

    it('zeigt keine Warnung bei einer normal versendeten Mail (Happy Path)', async () => {
        await rendere(baueMail({ zustellStatus: 'OFFEN' }));

        expect(screen.queryByText('Nicht angekommen')).not.toBeInTheDocument();
        expect(screen.getByText('Angebot AG-2026/07/00005')).toBeInTheDocument();
    });

    it('zeigt keine Warnung, wenn der Server keinen Zustellstatus schickt', async () => {
        await rendere(baueMail({ zustellStatus: null }));

        expect(screen.queryByText('Nicht angekommen')).not.toBeInTheDocument();
        expect(screen.getByText('Angebot AG-2026/07/00005')).toBeInTheDocument();
    });
});
