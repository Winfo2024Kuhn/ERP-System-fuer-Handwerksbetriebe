import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { EmailSuchHinweis, EmailSuchleiste, type EmailSuchleisteProps } from './EmailSuchleiste';

function zeige(props: Partial<EmailSuchleisteProps> = {}) {
    const rueckrufe = { onBegriff: vi.fn(), onModus: vi.fn(), onSortierung: vi.fn(), onLeseFilter: vi.fn() };
    const ansicht = render(<EmailSuchleiste begriff="" modus="ordner" sucheAktiv={false} sortierung="desc"
        leseFilter="all" zeigeLeseFilter {...rueckrufe} {...props} />);
    return { ...rueckrufe, ...ansicht };
}

describe('EmailSuchleiste', () => {
    it('gibt Eingaben weiter und begrenzt sie auf 200 Zeichen', async () => {
        const user = userEvent.setup();
        const { onBegriff } = zeige();
        const feld = screen.getByRole('searchbox', { name: 'E-Mails durchsuchen' });
        expect(feld).toHaveAttribute('maxLength', '200');
        expect(feld).toHaveAttribute('placeholder', 'In diesem Ordner suchen …');
        await user.type(feld, 'm');
        expect(onBegriff).toHaveBeenCalledWith('m');
    });

    it('schaltet zwischen „Ordner“ und „Alle Ordner“ um und zeigt den gewählten Modus', async () => {
        const user = userEvent.setup();
        const { onModus, rerender } = zeige();
        expect(screen.getByRole('button', { name: 'Ordner' })).toHaveAttribute('aria-pressed', 'true');
        await user.click(screen.getByRole('button', { name: 'Alle Ordner' }));
        expect(onModus).toHaveBeenCalledWith('alle');
        rerender(<EmailSuchleiste begriff="" modus="alle" sucheAktiv={false} sortierung="desc" leseFilter="all" zeigeLeseFilter
            onBegriff={vi.fn()} onModus={vi.fn()} onSortierung={vi.fn()} onLeseFilter={vi.fn()} />);
        expect(screen.getByRole('button', { name: 'Alle Ordner' })).toHaveAttribute('aria-pressed', 'true');
        expect(screen.getByRole('searchbox')).toHaveAttribute('placeholder', 'In allen Ordnern suchen …');
    });

    it('leert die Suche per Knopf', async () => {
        const user = userEvent.setup();
        const { onBegriff } = zeige({ begriff: 'geländer' });
        await user.click(screen.getByRole('button', { name: 'Suche leeren' }));
        expect(onBegriff).toHaveBeenCalledWith('');
    });

    it('erklärt die Suche im Info-Knopf neben dem Umschalter', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(screen.getByRole('button', { name: 'Mehr zu: So sucht das E-Mail-Center' }));
        const info = screen.getByRole('dialog', { name: 'So sucht das E-Mail-Center' });
        expect(info).toHaveTextContent('Was passiert im Hintergrund?');
        expect(info).toHaveTextContent('Durchsucht werden Absender und Empfänger samt Namen, CC, Betreff');
        expect(info).toHaveTextContent('„wurth“, „wuerth“ und „Würth“ finden dasselbe.');
        expect(info).toHaveTextContent('Auch Wortteile zählen: „gel“ findet „Geländer“.');
        expect(info).toHaveTextContent('Was bewirkt das?');
        expect(info).toHaveTextContent('Entwürfe werden nicht durchsucht.');
    });

    it('sortiert ohne Suche und zeigt den Lese-Filter', async () => {
        const user = userEvent.setup();
        const { onSortierung, onLeseFilter } = zeige();
        await user.click(screen.getByRole('button', { name: 'Neueste zuerst, klicken für Älteste zuerst' }));
        expect(onSortierung).toHaveBeenCalledWith('asc');
        await user.click(screen.getByRole('button', { name: 'Ungelesen' }));
        expect(onLeseFilter).toHaveBeenCalledWith('unread');
    });

    it('sperrt während der Suche die Sortierung mit Begründung und blendet den Lese-Filter aus', async () => {
        const user = userEvent.setup();
        const { onSortierung } = zeige({ begriff: 'geländer', sucheAktiv: true });
        const sortieren = screen.getByRole('button', { name: 'Suchtreffer sind immer nach Datum sortiert, neueste zuerst.' });
        expect(sortieren).toHaveAttribute('aria-disabled', 'true');
        expect(sortieren).toHaveAttribute('title', 'Suchtreffer sind immer nach Datum sortiert, neueste zuerst.');
        await user.click(sortieren);
        expect(onSortierung).not.toHaveBeenCalled();
        expect(screen.queryByRole('button', { name: 'Ungelesen' })).not.toBeInTheDocument();
    });
});

describe('EmailSuchHinweis', () => {
    const basis = { modus: 'ordner' as const, begriff: '', aktiv: false, laedt: false, fehler: false, anzahl: 0, hatMehr: false, serverSuche: true };

    it('bittet bei einem Zeichen um ein zweites', () => {
        render(<EmailSuchHinweis {...basis} begriff="m" />);
        expect(screen.getByRole('status')).toHaveTextContent('Mindestens 2 Zeichen eingeben.');
    });

    it('zeigt ohne Begriff im Modus „Ordner“ nichts', () => {
        const { container } = render(<EmailSuchHinweis {...basis} />);
        expect(container).toBeEmptyDOMElement();
    });

    it('erklärt den Modus „Alle Ordner“, bevor gesucht wird', () => {
        render(<EmailSuchHinweis {...basis} modus="alle" />);
        expect(screen.getByRole('status')).toHaveTextContent('Sucht in allen Ordnern, auch im Papierkorb, im Spam und in Gesendet.');
    });

    it('zeigt Laden und Trefferzahl samt „es gibt mehr“', () => {
        const { rerender } = render(<EmailSuchHinweis {...basis} begriff="geländer" aktiv laedt />);
        expect(screen.getByRole('status')).toHaveTextContent('Suche läuft …');
        rerender(<EmailSuchHinweis {...basis} begriff="geländer" modus="alle" aktiv anzahl={25} hatMehr />);
        expect(screen.getByRole('status')).toHaveTextContent('25 Treffer in allen Ordnern, weitere beim Scrollen');
        rerender(<EmailSuchHinweis {...basis} begriff="geländer" aktiv anzahl={1} />);
        expect(screen.getByRole('status')).toHaveTextContent('1 Treffer in diesem Ordner');
    });

    it('schweigt im Fehlerfall – das zeigt die Liste', () => {
        const { container } = render(<EmailSuchHinweis {...basis} begriff="geländer" aktiv fehler />);
        expect(container).toBeEmptyDOMElement();
    });

    it('bittet in den Entwürfen nicht um ein zweites Zeichen – dort filtert die Seite selbst', () => {
        const { container } = render(<EmailSuchHinweis {...basis} begriff="m" serverSuche={false} />);
        expect(container).toBeEmptyDOMElement();
    });
});
