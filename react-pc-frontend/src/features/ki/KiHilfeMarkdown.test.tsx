import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { KiHilfeMarkdown } from './KiHilfeMarkdown';

function zeige(text: string) {
    const onNavigate = vi.fn();
    const ergebnis = render(<KiHilfeMarkdown text={text} onNavigate={onNavigate} />);
    return { ...ergebnis, onNavigate };
}

describe('KiHilfeMarkdown', () => {
    it('zeigt Überschriften, Listen, Nummern und Leerzeilen', () => {
        const { container } = zeige('# Titel\n## Teil\n### Unterteil\n- Punkt A\n* Punkt B\n1. Erstens\n\nNormaler Text');
        expect(screen.getByRole('heading', { level: 2, name: 'Titel' })).toBeInTheDocument();
        expect(screen.getByRole('heading', { level: 3, name: 'Teil' })).toBeInTheDocument();
        expect(screen.getByRole('heading', { level: 4, name: 'Unterteil' })).toBeInTheDocument();
        expect(screen.getByText('Punkt A')).toBeInTheDocument();
        expect(screen.getByText('Punkt B')).toBeInTheDocument();
        expect(screen.getByText('1.')).toBeInTheDocument();
        expect(screen.getByText('Erstens')).toBeInTheDocument();
        expect(container.querySelector('.h-2')).not.toBeNull();
        expect(screen.getByText('Normaler Text').tagName).toBe('P');
    });

    it('formatiert fett, Code und kursiv', () => {
        zeige('Vorher **fett** und `Code` und *schräg* danach');
        expect(screen.getByText('fett').tagName).toBe('STRONG');
        expect(screen.getByText('Code').tagName).toBe('CODE');
        expect(screen.getByText('schräg').tagName).toBe('EM');
        expect(screen.getByText(/danach/)).toBeInTheDocument();
    });

    it('verlinkt nur erlaubte Seiten, alles andere bleibt Text', async () => {
        const user = userEvent.setup();
        const { onNavigate } = zeige('Siehe [Projekte](/projekte), [Firmendokumente](firmendokumente) und [Fremd](/gibt-es-nicht).');
        await user.click(screen.getByRole('button', { name: 'Projekte' }));
        expect(onNavigate).toHaveBeenCalledWith('/projekte');
        await user.click(screen.getByRole('button', { name: 'Firmendokumente' }));
        expect(onNavigate).toHaveBeenLastCalledWith('/firmendokumente');
        expect(screen.queryByRole('button', { name: 'Fremd' })).toBeNull();
        expect(screen.getByText('Fremd').tagName).toBe('STRONG');
    });

    it('baut Tabellen mit Kopf und Zeilen', () => {
        zeige('| Seite | Link |\n|---|---|\n| Kunden | **ja** |\n| Artikel | nein |');
        const tabelle = screen.getByRole('table');
        expect(within(tabelle).getAllByRole('columnheader').map((th) => th.textContent)).toEqual(['Seite', 'Link']);
        expect(within(tabelle).getAllByRole('row')).toHaveLength(3);
        expect(within(tabelle).getByText('ja').tagName).toBe('STRONG');
    });

    it('macht aus einer Zeile mit „|“ und Strich-Zeile ohne „|“ keine Tabelle', () => {
        zeige('a | b\n---');
        expect(screen.queryByRole('table')).toBeNull();
        expect(screen.getByText('a | b')).toBeInTheDocument();
    });

    it('zeigt HTML aus der Antwort als reinen Text', () => {
        const { container } = zeige('<img src=x onerror=alert(1)> und <script>alert(1)</script>');
        expect(screen.getByText('<img src=x onerror=alert(1)> und <script>alert(1)</script>')).toBeInTheDocument();
        expect(container.querySelector('img, script')).toBeNull();
    });
});
