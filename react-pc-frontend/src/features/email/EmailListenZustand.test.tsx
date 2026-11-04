import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { EmailListeLaedt, EmailListeLeer, EmailListenEnde, EmailSuchFehler } from './EmailListenZustand';

describe('EmailListeLeer', () => {
    it('nennt bei der Suche den Begriff und bietet „In allen Ordnern suchen“ an', async () => {
        const user = userEvent.setup();
        const onAlleOrdner = vi.fn();
        render(<EmailListeLeer art="suche" begriff=" müller geländer " modus="ordner" onAlleOrdner={onAlleOrdner} />);
        expect(screen.getByText('Keine E-Mails zu „müller geländer“ gefunden.')).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'In allen Ordnern suchen' }));
        expect(onAlleOrdner).toHaveBeenCalled();
    });

    it('bietet in „Alle Ordner“ keinen weiteren Modus an', () => {
        render(<EmailListeLeer art="suche" begriff="xyz" modus="alle" onAlleOrdner={vi.fn()} />);
        expect(screen.getByText('Keine E-Mails zu „xyz“ gefunden.')).toBeInTheDocument();
        expect(screen.queryByRole('button')).not.toBeInTheDocument();
    });

    it.each([
        ['entwuerfe', 'Keine Entwürfe'],
        ['ungelesen', 'Keine ungelesenen E-Mails in diesem Ordner'],
        ['ordner', 'Keine E-Mails'],
    ] as const)('zeigt für „%s“ „%s“', (art, titel) => {
        render(<EmailListeLeer art={art} begriff="" modus="ordner" onAlleOrdner={vi.fn()} />);
        expect(screen.getByText(titel)).toBeInTheDocument();
    });

    it('setzt keine Gedankenstriche als Satzzeichen', () => {
        render(<EmailListeLeer art="ungelesen" begriff="" modus="ordner" onAlleOrdner={vi.fn()} />);
        expect(screen.getByText('Alles gelesen. Neue Nachrichten erscheinen hier automatisch.')).toBeInTheDocument();
    });
});

describe('EmailSuchFehler', () => {
    it('sagt, dass die Suche nicht geklappt hat, und bietet einen neuen Versuch', async () => {
        const user = userEvent.setup();
        const onErneut = vi.fn();
        render(<EmailSuchFehler onErneut={onErneut} />);
        expect(screen.getByText('Die Suche hat nicht geklappt.')).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Erneut suchen' }));
        expect(onErneut).toHaveBeenCalled();
    });
});

describe('EmailListeLaedt', () => {
    it('meldet das Laden auch für Screenreader', () => {
        render(<EmailListeLaedt />);
        expect(screen.getByRole('status')).toHaveTextContent('E-Mails werden geladen');
    });
});

describe('EmailListenEnde', () => {
    it('zeigt beim Nachladen einen Ladehinweis', () => {
        render(<EmailListenEnde laedtMehr hatMehr onMehr={vi.fn()} />);
        expect(screen.getByText('Weitere E-Mails werden geladen …')).toBeInTheDocument();
    });

    it('bietet „Weitere laden“ als Knopf, wenn es mehr gibt', async () => {
        const user = userEvent.setup();
        const onMehr = vi.fn();
        render(<EmailListenEnde laedtMehr={false} hatMehr onMehr={onMehr} />);
        await user.click(screen.getByRole('button', { name: 'Weitere laden' }));
        expect(onMehr).toHaveBeenCalled();
    });

    it('zeigt das Ende der Liste', () => {
        render(<EmailListenEnde laedtMehr={false} hatMehr={false} onMehr={vi.fn()} />);
        expect(screen.getByText('Ende der Liste')).toBeInTheDocument();
    });
});
