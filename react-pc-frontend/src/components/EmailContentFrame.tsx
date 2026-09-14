import React, { useEffect, useRef, useState } from 'react';
import { escapeHtml, isLikelyPlainText } from './emailContentFrameUtils';
import { collapseThreadQuotes } from '../features/email/threadQuotes';

interface EmailContentFrameProps {
    html: string;
    className?: string;
    /** Im Thread-Modus: zitierte Inhalte (blockquote, Outlook-Divs etc.) einklappen */
    hideQuotes?: boolean;
}

export const EmailContentFrame: React.FC<EmailContentFrameProps> = ({ html, className, hideQuotes = false }) => {
    const iframeRef = useRef<HTMLIFrameElement>(null);
    const cleanupRef = useRef<(() => void) | null>(null);
    const [height, setHeight] = useState<number>(0);

    const updateHeight = () => {
        const iframe = iframeRef.current;
        if (iframe && iframe.contentWindow?.document?.body) {
            iframe.style.height = '0px';
            const scrollHeight =
                iframe.contentWindow.document.documentElement.scrollHeight ||
                iframe.contentWindow.document.body.scrollHeight;
            setHeight(scrollHeight + 2);
            iframe.style.height = `${scrollHeight + 2}px`;
        }
    };

    useEffect(() => () => cleanupRef.current?.(), []);

    const handleLoad = () => {
        const iframe = iframeRef.current;
        if (!iframe || !iframe.contentWindow?.document?.body) return;
        cleanupRef.current?.();

        // Zitate einklappen (vor Höhenberechnung, damit korrekte Höhe)
        if (hideQuotes) {
            collapseThreadQuotes(iframe.contentWindow.document, updateHeight);
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
        cleanupRef.current = () => {
            resizeObserver.disconnect();
            for (let i = 0; i < imgs.length; i++) imgs[i].removeEventListener('load', updateHeight);
        };
    };

    // Plain-Text-Mails (z. B. t-online, Hetzner-Tickets) liefern den Body als
    // Fließtext mit \n-Umbrüchen und ohne HTML-Struktur. Ohne pre-wrap kollabieren
    // alle Zeilen zu einem unleserlichen Bandwurm.
    const plainText = isLikelyPlainText(html);
    // Bei als Plain-Text klassifiziertem Inhalt vor dem Einsetzen in <body>
    // HTML-escapen, sonst würden eingebettete `<script>` / `<img onerror=…>`
    // im iframe ausgeführt (das Sandboxing ohne `allow-scripts` mildert
    // das, aber Defense-in-Depth ist hier billig).
    const bodyHtml = plainText
        ? escapeHtml(html)
        : (html || '<p style="color:#94a3b8;font-style:italic">Kein Inhalt</p>');

    const baseStyles = `
        <style>
            body {
                margin: 0;
                padding: ${hideQuotes ? '0.75rem' : '1rem'};
                font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
                font-size: 0.875rem;
                line-height: 1.5;
                color: #334155;
                overflow-wrap: anywhere;
                word-wrap: break-word;
                ${plainText ? 'white-space: pre-wrap;' : ''}
            }
            p { margin-top: 0; margin-bottom: 0.65em; }
            body > :last-child { margin-bottom: 0; }
            [data-quote-btn] { display: block; margin: 6px 0; padding: 4px 8px;
                font: inherit; font-size: 12px; line-height: 1.4; color: #64748b;
                background: #f8fafc; border: 1px solid #cbd5e1; border-radius: 6px; cursor: pointer; }
            [data-quote-btn]:hover { color: #be123c; background: #fff1f2; border-color: #fda4af; }
            [data-quote-btn]:focus-visible { outline: 2px solid #e11d48; outline-offset: 2px; }
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
${bodyHtml}
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
