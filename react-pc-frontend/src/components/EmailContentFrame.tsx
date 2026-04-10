import React, { useEffect, useRef, useState } from 'react';

interface EmailContentFrameProps {
    html: string;
    className?: string;
    /** Im Thread-Modus: zitierte Inhalte (blockquote, Outlook-Divs etc.) einklappen */
    hideQuotes?: boolean;
}

// ─────────────────────────────────────────────────────────────────────────────
// Quote-Collapsing: läuft im Parent-Context, manipuliert iframe-DOM direkt.
// Kein inline-Script im iframe nötig → keine CSP-Konflikte.
// ─────────────────────────────────────────────────────────────────────────────
function collapseQuotes(doc: Document, onHeightChange: () => void): void {
    // Gibt es schon Toggle-Buttons? Dann nicht nochmal anwenden.
    if (doc.querySelector('[data-quote-btn]')) return;

    // ── Hilfsfunktion: Gruppe von Elementen verstecken + Toggle-Button einfügen ──
    function hideGroup(elements: Element[]): void {
        if (!elements.length) return;
        const firstEl = elements[0];
        const parent = firstEl.parentNode;
        if (!parent) return;

        // Alle ausblenden
        elements.forEach(el => ((el as HTMLElement).style.display = 'none'));

        // Toggle-Button VOR dem ersten Element einfügen
        const btn = doc.createElement('button');
        btn.setAttribute('data-quote-btn', '1');
        btn.textContent = '···';
        btn.title = 'Zitierten Inhalt anzeigen';
        Object.assign(btn.style, {
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '6px 0',
            padding: '2px 10px',
            fontSize: '13px',
            fontWeight: '600',
            letterSpacing: '2px',
            color: '#64748b',
            background: '#f1f5f9',
            border: '1px solid #cbd5e1',
            borderRadius: '6px',
            cursor: 'pointer',
            lineHeight: '1.4',
            fontFamily: 'inherit',
        });

        btn.addEventListener('mouseenter', () => {
            btn.style.background = '#e2e8f0';
            btn.style.borderColor = '#94a3b8';
        });
        btn.addEventListener('mouseleave', () => {
            btn.style.background = '#f1f5f9';
            btn.style.borderColor = '#cbd5e1';
        });

        let expanded = false;
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            expanded = !expanded;
            elements.forEach(el => ((el as HTMLElement).style.display = expanded ? '' : 'none'));
            btn.textContent = expanded ? '▲ Zitat ausblenden' : '···';
            btn.style.letterSpacing = expanded ? '0' : '2px';
            setTimeout(onHeightChange, 50);
        });

        parent.insertBefore(btn, firstEl);
    }

    // ── Outlook-Muster 1: #divRplyFwdMsg (Outlook Web App / OWA) ────────────
    // Struktur: [hr] [appendonsend] #divRplyFwdMsg [quoted-siblings...]
    const rplyEl = doc.querySelector('#divRplyFwdMsg');
    if (rplyEl) {
        const group: Element[] = [];
        // Vorherige leere Geschwister (hr, leere divs) aufsammeln
        let prevEl = rplyEl.previousElementSibling;
        const prevCollect: Element[] = [];
        while (prevEl) {
            const tag = prevEl.tagName.toUpperCase();
            const isEmpty = (prevEl.textContent || '').trim().length < 5;
            if (tag === 'HR' || (tag === 'DIV' && isEmpty)) {
                prevCollect.unshift(prevEl);
                prevEl = prevEl.previousElementSibling;
            } else { break; }
        }
        group.push(...prevCollect);
        // divRplyFwdMsg + alle folgenden Geschwister
        let cur: Element | null = rplyEl;
        while (cur) { group.push(cur); cur = cur.nextElementSibling; }
        hideGroup(group);
        return; // Nicht auch noch blockquote suchen
    }

    // ── Outlook-Muster 2: border-top Trennlinie (Outlook Desktop/Exchange) ──
    // Variante A: <div style="border-top">Von:...</div>  [direct separator]
    // Variante B: <div><div style="border-top">Von:...</div></div>  [wrapper]
    const borderDivs = doc.querySelectorAll<HTMLElement>('div[style*="border-top"]');
    for (const bd of Array.from(borderDivs)) {
        if (/Von:|From:|Gesendet:|Sent:|De:|Envoy/i.test(bd.textContent || '')) {
            let startEl: Element = bd;
            const parent2 = bd.parentElement;
            if (parent2 && parent2.tagName.toUpperCase() === 'DIV') {
                const pLen = (parent2.textContent || '').trim().length;
                const bLen = (bd.textContent || '').trim().length;
                if (pLen > 0 && Math.abs(pLen - bLen) < 30) {
                    startEl = parent2; // Variante B: Parent ist Wrapper
                }
            }
            const group: Element[] = [startEl];
            let nxt = startEl.nextElementSibling;
            while (nxt) { group.push(nxt); nxt = nxt.nextElementSibling; }
            hideGroup(group);
            return;
        }
    }

    // ── Standard-Selektoren (Gmail, Yahoo, blockquote, Apple Mail) ───────────
    const quoteSelectors = [
        'blockquote',
        '.gmail_quote',
        '.yahoo_quoted',
        '.moz-cite-prefix',
        '[class*="ygmail_extra"]',
    ];

    // Nur Top-Level-Quotes (keine verschachtelten)
    const found: Element[] = [];
    for (const sel of quoteSelectors) {
        try {
            doc.querySelectorAll(sel).forEach(el => {
                const isNested = found.some(f => f.contains(el));
                if (!isNested && found.indexOf(el) === -1) found.push(el);
            });
        } catch { /* ignore invalid selectors */ }
    }

    for (const quote of found) {
        // Optionale Zeile VOR dem Quote verstecken ("Am ... schrieb:", "On ... wrote:")
        let prev: Node | null = quote.previousSibling;
        while (prev && prev.nodeType === 3 && (prev.textContent || '').trim() === '') {
            prev = prev.previousSibling;
        }
        if (prev && prev.nodeType === 1) {
            const text = ((prev as Element).textContent || '').trim();
            if (/^(Am |On |Von:|From:|De:|Le )/i.test(text) || text.length < 120) {
                (prev as HTMLElement).style.display = 'none';
            }
        }
        hideGroup([quote]);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EmailContentFrame
// ─────────────────────────────────────────────────────────────────────────────
export const EmailContentFrame: React.FC<EmailContentFrameProps> = ({ html, className, hideQuotes = false }) => {
    const iframeRef = useRef<HTMLIFrameElement>(null);
    const [height, setHeight] = useState<number>(0);

    const updateHeight = () => {
        const iframe = iframeRef.current;
        if (iframe && iframe.contentWindow?.document?.body) {
            iframe.style.height = '0px';
            const scrollHeight =
                iframe.contentWindow.document.documentElement.scrollHeight ||
                iframe.contentWindow.document.body.scrollHeight;
            setHeight(scrollHeight + 20);
            iframe.style.height = `${scrollHeight + 20}px`;
        }
    };

    // srcDoc aktualisieren wenn html sich ändert
    useEffect(() => {
        // Wird durch onLoad abgehandelt – kein zusätzliches Handling nötig
    }, [html]);

    const handleLoad = () => {
        const iframe = iframeRef.current;
        if (!iframe || !iframe.contentWindow?.document?.body) return;

        // Zitate einklappen (vor Höhenberechnung, damit korrekte Höhe)
        if (hideQuotes) {
            collapseQuotes(iframe.contentWindow.document, updateHeight);
        }

        updateHeight();

        // Höhe bei Größenänderung anpassen
        const body = iframe.contentWindow.document.body;
        const resizeObserver = new ResizeObserver(() => updateHeight());
        resizeObserver.observe(body);

        // Bilder: Höhe nach dem Laden anpassen
        const imgs = body.getElementsByTagName('img');
        for (let i = 0; i < imgs.length; i++) {
            imgs[i].addEventListener('load', updateHeight);
        }
    };

    const baseStyles = `
        <style>
            body {
                margin: 0;
                padding: 1rem;
                font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
                font-size: 0.875rem;
                line-height: 1.6;
                color: #334155;
                overflow-wrap: break-word;
                word-wrap: break-word;
            }
            a { color: #e11d48; text-decoration: underline; }
            img { max-width: 100%; height: auto; display: block; }
            blockquote { border-left: 4px solid #e2e8f0; margin-left: 0; padding-left: 1rem; color: #64748b; }
            pre { background: #f1f5f9; padding: 1rem; overflow-x: auto; border-radius: 0.375rem; }
            table { border-collapse: collapse; }
            ::-webkit-scrollbar { display: none; }
        </style>
        <base target="_blank">
    `;

    const fullHtml = `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
${baseStyles}
</head>
<body>
${html || '<p style="color:#94a3b8;font-style:italic">Kein Inhalt</p>'}
</body>
</html>`;

    return (
        <iframe
            ref={iframeRef}
            srcDoc={fullHtml}
            className={`w-full block border-none overflow-hidden ${className || ''}`}
            onLoad={handleLoad}
            title="Email Content"
            sandbox="allow-same-origin allow-popups allow-popups-to-escape-sandbox"
            style={{ minHeight: '60px', height: height ? `${height}px` : '100%' }}
        />
    );
};
