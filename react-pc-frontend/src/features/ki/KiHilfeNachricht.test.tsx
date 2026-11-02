import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { KiHilfeLeer, KiHilfeNachricht } from './KiHilfeNachricht';

describe('KiHilfeNachricht', () => {
    it('zeigt eine Frage als reinen Text, auch mit HTML', () => {
        const { container } = render(<KiHilfeNachricht nachricht={{ role: 'user', text: '<img src=x onerror=alert(1)> **nicht fett**' }} onNavigate={vi.fn()} />);
        expect(screen.getByText('<img src=x onerror=alert(1)> **nicht fett**')).toBeInTheDocument();
        expect(container.querySelector('img, strong')).toBeNull();
    });

    it('zeigt eine Antwort mit Quellen; ohne Titel steht die Adresse da', () => {
        render(<KiHilfeNachricht onNavigate={vi.fn()} nachricht={{
            role: 'assistant', text: '**Antwort**',
            sources: [{ title: 'DIN 18065', url: 'https://example.org/din' }, { title: '', url: 'https://example.org/ohne' }],
        }} />);
        expect(screen.getByText('Antwort').tagName).toBe('STRONG');
        expect(screen.getByRole('link', { name: 'DIN 18065' })).toHaveAttribute('href', 'https://example.org/din');
        expect(screen.getByRole('link', { name: 'https://example.org/ohne' })).toHaveAttribute('rel', 'noopener noreferrer');
    });

    it('zeigt Antworten ohne Quellen ohne Quellenblock', () => {
        render(<KiHilfeNachricht nachricht={{ role: 'assistant', text: 'Kurz', sources: [] }} onNavigate={vi.fn()} />);
        expect(screen.queryByText('Quellen:')).toBeNull();
    });

    it('bietet im leeren Chat Vorschläge an', async () => {
        const user = userEvent.setup();
        const onVorschlag = vi.fn();
        render(<KiHilfeLeer onVorschlag={onVorschlag} />);
        await user.click(screen.getByRole('button', { name: 'Wie storniere ich eine Rechnung?' }));
        expect(onVorschlag).toHaveBeenCalledWith('Wie storniere ich eine Rechnung?');
    });
});
