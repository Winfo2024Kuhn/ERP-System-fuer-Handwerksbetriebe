import { describe, expect, it } from 'vitest';
import { verlinkeAdressen } from './linkErkennungDom';

function wurzelMit(html: string): HTMLElement {
    const doc = document.implementation.createHTMLDocument('');
    doc.body.innerHTML = html;
    return doc.body;
}

describe('verlinkeAdressen', () => {
    it('macht nackte Adressen zu Links, die im neuen Tab öffnen', () => {
        const body = wurzelMit('<p>Freigabe: https://onedrive.example.com/f/c/abc?e=x. Gruß</p>');
        verlinkeAdressen(body, { neuerTab: true });
        expect(body.innerHTML).toBe(
            '<p>Freigabe: <a href="https://onedrive.example.com/f/c/abc?e=x" target="_blank" rel="noopener noreferrer">https://onedrive.example.com/f/c/abc?e=x</a>. Gruß</p>',
        );
    });

    it('ohne neuen Tab nur href (für den Versand)', () => {
        const body = wurzelMit('<p>siehe www.example.com</p>');
        verlinkeAdressen(body);
        expect(body.innerHTML).toBe('<p>siehe <a href="https://www.example.com">www.example.com</a></p>');
    });

    it('lässt vorhandene Links unverändert und gibt ihnen rel', () => {
        const body = wurzelMit('<p><a href="https://example.com">https://example.com</a> und <a href="https://x.example.com">hier</a></p>');
        verlinkeAdressen(body, { neuerTab: true });
        expect(body.querySelectorAll('a')).toHaveLength(2);
        expect(body.querySelector('a')!.getAttribute('rel')).toBe('noopener noreferrer');
        expect(body.querySelector('a')!.textContent).toBe('https://example.com');
    });

    it('überspringt Skripte, Stile, Eingaben, Knöpfe und den Zitat-Knopf', () => {
        const body = wurzelMit(
            '<style>a{background:url(https://x.example.com/a.png)}</style><textarea>https://x.example.com</textarea>'
            + '<button>https://x.example.com</button><button data-quote-btn="1">https://x.example.com</button><p>max@example.com</p>',
        );
        verlinkeAdressen(body, { neuerTab: true });
        expect(body.querySelectorAll('a')).toHaveLength(1);
        expect(body.querySelector('a')!.getAttribute('href')).toBe('mailto:max@example.com');
    });

    it('überspringt auch svg, math, noscript und andere Sonderbereiche', () => {
        const body = wurzelMit(
            '<svg><text>https://example.com/a</text></svg><math><mi>www.example.com</mi></math>'
            + '<noscript>https://example.com/b</noscript><iframe>https://example.com/c</iframe>'
            + '<xmp>https://example.com/d</xmp><noembed>https://example.com/e</noembed><p>https://example.com/f</p>',
        );
        verlinkeAdressen(body, { neuerTab: true });
        expect(Array.from(body.querySelectorAll('a'), a => a.getAttribute('href'))).toEqual(['https://example.com/f']);
    });

    it('lässt HTML-Text als Text und baut nichts Ausführbares', () => {
        const body = wurzelMit('<p>&lt;img src=x onerror=alert(1)&gt; javascript:alert(1) https://example.com</p>');
        verlinkeAdressen(body, { neuerTab: true });
        expect(body.querySelector('img')).toBeNull();
        expect(body.querySelectorAll('a')).toHaveLength(1);
        expect(body.textContent).toBe('<img src=x onerror=alert(1)> javascript:alert(1) https://example.com');
    });

    it('verlinkt Klartext-Mails und versteckte Zitate', () => {
        const body = wurzelMit('Hallo,\nhier der Plan: https://example.com/plan\n<div style="display:none">&gt; alt: www.alt.example.com</div>');
        verlinkeAdressen(body, { neuerTab: true });
        expect(Array.from(body.querySelectorAll('a'), a => a.getAttribute('href'))).toEqual(['https://example.com/plan', 'https://www.alt.example.com']);
    });
});
