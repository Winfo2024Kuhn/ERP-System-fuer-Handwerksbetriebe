import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { SpamHinweise, SpamListenSchilder } from './SpamHinweise';
import type { FolderType } from './emailCenterModel';

type Felder = { spamVerdacht?: boolean; spamGruende?: string[]; warnhinweis?: string | null };

function renderHinweise(email: Felder, folder: FolderType = 'inbox', busy = false) {
    const onSpam = vi.fn();
    const onNotSpam = vi.fn();
    const ergebnis = render(<SpamHinweise email={email} folder={folder} busy={busy} onSpam={onSpam} onNotSpam={onNotSpam} />);
    return { ...ergebnis, onSpam, onNotSpam };
}

describe('SpamHinweise – Vielleicht Spam', () => {
    it('zeigt Hinweiszeile mit Text und beiden Knöpfen', async () => {
        const { onSpam, onNotSpam } = renderHinweise({ spamVerdacht: true, spamGruende: ['ABSENDER_GEFAELSCHT'] });
        const zeile = screen.getByTestId('spam-verdacht-hinweis');
        expect(zeile).toHaveTextContent('Vielleicht Spam');
        expect(zeile).toHaveTextContent('Diese E-Mail sieht verdächtig aus. Sie bleibt im Posteingang, bis du entscheidest.');

        await userEvent.click(within(zeile).getByRole('button', { name: 'Ist Spam' }));
        await userEvent.click(within(zeile).getByRole('button', { name: 'Kein Spam' }));
        expect(onSpam).toHaveBeenCalledTimes(1);
        expect(onNotSpam).toHaveBeenCalledTimes(1);
    });

    it('erklärt im Info-Knopf die Gründe in Klartext und was der Klick bewirkt', async () => {
        renderHinweise({ spamVerdacht: true, spamGruende: ['ABSENDER_GEFAELSCHT', 'ANTWORT_UMGELEITET', 'UNBEKANNT'] });
        await userEvent.click(screen.getByRole('button', { name: 'Mehr zu: Warum?' }));
        const popover = screen.getByRole('dialog', { name: 'Warum?' });
        expect(within(popover).getAllByRole('listitem').map(li => li.textContent)).toEqual([
            'Der Absendername passt nicht zur Adresse.',
            'Antworten gingen an eine andere Adresse.',
        ]);
        expect(popover).not.toHaveTextContent('UNBEKANNT');
        expect(popover).toHaveTextContent('Die Mail wandert in den Spam-Ordner – auch auf dem Mail-Server – und das Programm lernt dazu.');
        expect(popover).toHaveTextContent('Der Hinweis verschwindet, das Programm merkt es sich.');
    });

    it('nennt ohne bekannte Gründe den Lernfilter', async () => {
        renderHinweise({ spamVerdacht: true, spamGruende: [] });
        await userEvent.click(screen.getByRole('button', { name: 'Mehr zu: Warum?' }));
        expect(screen.getByRole('dialog', { name: 'Warum?' })).toHaveTextContent('Der Lernfilter kennt ähnliche Mails');
    });

    it('zeigt HTML aus den Gründen nie an und führt nichts aus', async () => {
        const alarm = vi.spyOn(window, 'alert').mockImplementation(() => undefined);
        const { container } = renderHinweise({
            spamVerdacht: true,
            spamGruende: ['<script>alert(1)</script>', '<img src=x onerror=alert(1)>'],
        });
        await userEvent.click(screen.getByRole('button', { name: 'Mehr zu: Warum?' }));
        const popover = screen.getByRole('dialog', { name: 'Warum?' });
        expect(popover.querySelector('script, img')).toBeNull();
        expect(container.querySelector('script, img')).toBeNull();
        expect(popover).not.toHaveTextContent('alert');
        expect(alarm).not.toHaveBeenCalled();
        alarm.mockRestore();
    });

    it('sperrt die Knöpfe, solange eine Aktion läuft', () => {
        renderHinweise({ spamVerdacht: true }, 'inbox', true);
        expect(screen.getByRole('button', { name: 'Ist Spam' })).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Kein Spam' })).toBeDisabled();
    });

    it('zeigt im Papierkorb keinen Verdacht', () => {
        const { container } = renderHinweise({ spamVerdacht: true }, 'trash');
        expect(container).toBeEmptyDOMElement();
    });

    it('zeichnet ohne Verdacht und Warnung nichts', () => {
        const { container } = renderHinweise({ spamVerdacht: false, warnhinweis: null });
        expect(container).toBeEmptyDOMElement();
    });
});

describe('SpamHinweise – Warnhinweis', () => {
    it('zeigt Titel und Satz ohne Knopf zum Wegklicken', async () => {
        renderHinweise({ warnhinweis: 'BANKVERBINDUNG_GEAENDERT' });
        const warnung = screen.getByRole('note', { name: 'Achtung: neue Bankverbindung' });
        expect(warnung).toHaveTextContent('Betrüger fälschen so Rechnungen bekannter Lieferanten.');
        // Nur das (i) – kein Schließen, keine Spam-Knöpfe.
        expect(within(warnung).getAllByRole('button').map(b => b.getAttribute('aria-label')))
            .toEqual(['Mehr zu: Woran erkennt das Programm das?']);
        expect(screen.queryByTestId('spam-verdacht-hinweis')).not.toBeInTheDocument();

        await userEvent.click(within(warnung).getByRole('button', { name: 'Mehr zu: Woran erkennt das Programm das?' }));
        const popover = screen.getByRole('dialog', { name: 'Woran erkennt das Programm das?' });
        expect(popover).toHaveTextContent('enthält eine IBAN');
        expect(popover).toHaveTextContent('Der Hinweis verschiebt nichts');
    });

    it('zeigt den Warnhinweis auch im Papierkorb und zusammen mit dem Verdacht', () => {
        renderHinweise({ warnhinweis: 'GOOGLE_PROFIL', spamVerdacht: true });
        expect(screen.getByRole('note', { name: 'Vorsicht: angebliche Google-Profil-Mail' })).toBeInTheDocument();
        expect(screen.getByTestId('spam-verdacht-hinweis')).toBeInTheDocument();
    });

    it('ignoriert unbekannte Warnhinweise', () => {
        const { container } = renderHinweise({ warnhinweis: '<b>UNBEKANNT</b>' });
        expect(container).toBeEmptyDOMElement();
    });
});

describe('SpamListenSchilder', () => {
    it('zeigt Schild „Spam-Verdacht“ und Warnzeichen mit Titel', () => {
        render(<SpamListenSchilder email={{ spamVerdacht: true, warnhinweis: 'BRANCHENBUCH' }} />);
        expect(screen.getByTestId('spam-verdacht-schild')).toHaveTextContent('Spam-Verdacht');
        expect(screen.getByRole('img', { name: 'Vorsicht: mögliche Branchenbuch-Abzocke' })).toBeInTheDocument();
    });

    it('zeichnet ohne Verdacht und mit unbekannter Warnung nichts', () => {
        const { container } = render(<SpamListenSchilder email={{ spamVerdacht: false, warnhinweis: 'FALSCH' }} />);
        expect(container).toBeEmptyDOMElement();
    });
});
