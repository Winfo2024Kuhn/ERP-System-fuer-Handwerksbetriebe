import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { EmailContentFrame } from './EmailContentFrame';

/** jsdom lädt `srcdoc` nicht selbst – der Test setzt den Inhalt und löst `load` aus. */
function ladeRahmen(inhalt: string, hideQuotes = false) {
    render(<EmailContentFrame html={inhalt} hideQuotes={hideQuotes} />);
    const rahmen = screen.getByTitle('Email Content') as HTMLIFrameElement;
    const dokument = rahmen.contentDocument!;
    dokument.body.innerHTML = inhalt;
    fireEvent.load(rahmen);
    return dokument;
}

describe('EmailContentFrame', () => {
    it('macht nackte Adressen nach dem Laden zu Links in einem neuen Tab', () => {
        const dokument = ladeRahmen('<p>Hier die Freigabe: https://onedrive.example.com/f/c/abc123?e=XyZ.</p>');
        const link = dokument.querySelector('a')!;
        expect(link.getAttribute('href')).toBe('https://onedrive.example.com/f/c/abc123?e=XyZ');
        expect(link.getAttribute('target')).toBe('_blank');
        expect(link.getAttribute('rel')).toBe('noopener noreferrer');
        expect(dokument.body.textContent).toBe('Hier die Freigabe: https://onedrive.example.com/f/c/abc123?e=XyZ.');
    });

    it('lässt vorhandene Links stehen und verlinkt auch eingeklappte Zitate', () => {
        const dokument = ladeRahmen(
            '<p><a href="https://example.com/angebot">Angebot</a></p>'
            + '<blockquote>Am Montag schrieb Max Mustermann: siehe www.example.com</blockquote>',
            true,
        );
        const links = Array.from(dokument.querySelectorAll('a'), a => a.getAttribute('href'));
        expect(links).toEqual(['https://example.com/angebot', 'https://www.example.com']);
        expect(dokument.querySelector('a')!.textContent).toBe('Angebot');
        expect(dokument.querySelector('[data-quote-btn]')?.textContent ?? '').not.toContain('http');
    });
});
