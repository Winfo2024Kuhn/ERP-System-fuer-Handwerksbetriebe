import React, { useEffect, useRef, useState } from 'react';

interface EmailContentFrameProps {
    html: string;
    className?: string;
}

export const EmailContentFrame: React.FC<EmailContentFrameProps> = ({ html, className }) => {
    const iframeRef = useRef<HTMLIFrameElement>(null);
    const [height, setHeight] = useState<number>(0);

    // Function to update height based on content
    const updateHeight = () => {
        const iframe = iframeRef.current;
        if (iframe && iframe.contentWindow?.document?.body) {
            // Reset height to allow shrinking
            iframe.style.height = '0px';
            
            // Calculate new height
            const scrollHeight = iframe.contentWindow.document.documentElement.scrollHeight || 
                                 iframe.contentWindow.document.body.scrollHeight;
            
            // Add a small buffer to prevent scrollbars from appearing due to rounding
            setHeight(scrollHeight + 20);
            iframe.style.height = `${scrollHeight + 20}px`;
        }
    };

    useEffect(() => {
        const iframe = iframeRef.current;
        if (!iframe) return;

        // Create a blob to handle charset and base styles better than srcDoc sometimes
        // But srcDoc is easier for React updates. Let's use srcDoc but inject some base CSS.
        



    }, [html]);

    // Update height when HTML changes or loads
    const handleLoad = () => {
        updateHeight();
        
        // Optional: Set up a resize observer on the iframe body from the parent side 
        // if access is allowed (srcDoc usually is).
        const iframe = iframeRef.current;
        if (iframe && iframe.contentWindow?.document?.body) {
            const body = iframe.contentWindow.document.body;
            const resizeObserver = new ResizeObserver(() => {
                updateHeight();
            });
            resizeObserver.observe(body);
            
            // Also monitor images loading
            const imgs = body.getElementsByTagName('img');
            for (let i = 0; i < imgs.length; i++) {
                imgs[i].addEventListener('load', updateHeight);
            }
        }
    };

    // Inject styles and html into srcDoc
    const baseStyles = `
        <style>
            body {
                margin: 0;
                padding: 1rem; /* Add padding to match the previous p-6 */
                font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
                font-size: 0.875rem;
                line-height: 1.6;
                color: #334155; /* slate-700 */
                overflow-wrap: break-word;
                word-wrap: break-word;
            }
            a { color: #e11d48; text-decoration: underline; } /* rose-600 */
            img { max-width: 100%; height: auto; display: block; }
            blockquote { border-left: 4px solid #e2e8f0; margin-left: 0; padding-left: 1rem; color: #64748b; }
            pre { background: #f1f5f9; padding: 1rem; overflow-x: auto; border-radius: 0.375rem; }
            /* Table resets */
            /* Table resets - minimal */
            table { border-collapse: collapse; }
            
            /* Hide scrollbar */
            ::-webkit-scrollbar { display: none; }
        </style>
        <!-- Base target blank to open links in new tab -->
        <base target="_blank">
    `;

    const fullHtml = `
        <!DOCTYPE html>
        <html>
            <head>
                <meta charset="utf-8">
                ${baseStyles}
            </head>
            <body>
                ${html || '<p class="text-slate-400 italic">Kein Inhalt</p>'}
            </body>
        </html>
    `;

    return (
        <iframe
            ref={iframeRef}
            srcDoc={fullHtml}
            className={`w-full block border-none overflow-hidden ${className || ''}`}
            onLoad={handleLoad}
            title="Email Content"
            sandbox="allow-same-origin allow-popups allow-popups-to-escape-sandbox"
            style={{ minHeight: '100px', height: height ? `${height}px` : '100%' }}
        />
    );
};
