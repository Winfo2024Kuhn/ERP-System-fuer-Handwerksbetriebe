import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import EmailsTab, { type GenericEmail } from './EmailsTab';

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

function baueMail(overrides: Partial<GenericEmail> = {}): GenericEmail {
    return {
        id: 1,
        subject: 'Angebot AG-2026/07/00005',
        fromAddress: 'firma@example.com',
        to: 'max.mustermann@example.com',
        direction: 'OUT',
        sentAt: '2026-07-17T08:02:15',
        bodyPreview: 'im Anhang finden Sie das Angebot',
        ...overrides,
    };
}

function rendere(email: GenericEmail) {
    return render(
        <MemoryRouter>
            <EmailsTab emails={[email]} kundeId={1} showComposeButton={false} showReplyButton={false} />
        </MemoryRouter>
    );
}

describe('EmailsTab – Zustell-Warnung', () => {
    it('warnt sichtbar, wenn eine Ausgangsmail nicht zugestellt werden konnte', () => {
        rendere(baueMail({
            zustellStatus: 'UNZUSTELLBAR',
            zustellFehler: 'unknown user / Teilnehmer existiert nicht',
        }));

        expect(screen.getByText('Nicht angekommen')).toBeInTheDocument();
        expect(screen.getByText(/Bitte Adresse prüfen und erneut senden/)).toBeInTheDocument();
    });

    it('nennt den Grund in Handwerker-Sprache statt im Server-Kauderwelsch', () => {
        rendere(baueMail({
            zustellStatus: 'UNZUSTELLBAR',
            zustellFehler: '550 5.1.1 <max.mustermann@example.com> User unknown',
        }));

        expect(screen.getByText('Diese E-Mail-Adresse gibt es nicht. Bitte Adresse prüfen und erneut senden.'))
            .toBeInTheDocument();
        // Der Rohtext bleibt zum Nachschlagen erhalten, steht aber nicht im Weg.
        expect(screen.getByTitle('550 5.1.1 <max.mustermann@example.com> User unknown'))
            .toBeInTheDocument();
    });

    it('zeigt keine Warnung bei einer normal versendeten Mail (Happy Path)', () => {
        rendere(baueMail({ zustellStatus: 'OFFEN' }));

        expect(screen.queryByText('Nicht angekommen')).not.toBeInTheDocument();
        expect(screen.getByText('Angebot AG-2026/07/00005')).toBeInTheDocument();
    });

    it('zeigt keine Warnung, wenn das Feld fehlt (Alt-Daten vor der Migration)', () => {
        rendere(baueMail());

        expect(screen.queryByText('Nicht angekommen')).not.toBeInTheDocument();
        expect(screen.getByText('Angebot AG-2026/07/00005')).toBeInTheDocument();
    });
});
