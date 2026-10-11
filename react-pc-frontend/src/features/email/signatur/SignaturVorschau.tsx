import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { cn } from '../../../lib/utils';

/**
 * Zeigt Signatur-HTML sicher an: in einem abgeschotteten iframe ohne
 * Skript-Rechte (`sandbox` ohne `allow-scripts`) und zusätzlich mit einer
 * Content-Security-Policy, die nur Bilder und Inline-Styles erlaubt.
 * Selbst ein eingefügtes `<script>` oder `onerror=` läuft dort nie.
 *
 * `allow-same-origin` ist gesetzt, damit (1) Bilder alter Signaturen, die
 * über `/api/email/signatures/{id}/images/…` mit der Anmeldung geladen
 * werden, sichtbar sind und (2) wir die Höhe des Inhalts messen können.
 * Ohne `allow-scripts` ist das unbedenklich: im iframe kann nichts ausgeführt
 * werden, das die Herkunft ausnutzen könnte.
 */

const CSP = "default-src 'none'; img-src https: http: data: 'self'; style-src 'unsafe-inline'";

function vorschauDokument(html: string, mitRand = true): string {
    return '<!doctype html><html><head><meta charset="utf-8">'
        + `<meta http-equiv="Content-Security-Policy" content="${CSP}">`
        + '<base target="_blank">'
        + '<style>html,body{margin:0;padding:0;background:#ffffff;}'
        + `body{font-family:Arial,Helvetica,sans-serif;font-size:14px;color:#292524;${mitRand ? 'padding:2px;' : ''}overflow-x:auto;overflow-y:hidden;}</style>`
        + `</head><body>${html}</body></html>`;
}

interface SignaturVorschauProps {
    html: string;
    /** Name für Screenreader („Vorschau der Signatur …“). */
    titel: string;
    className?: string;
    /** Mindesthöhe in Pixeln, bevor der Inhalt gemessen ist. */
    minHoehe?: number;
    /** Höher wird die Vorschau nicht – der Rest wird abgeschnitten (Kartenansicht). */
    maxHoehe?: number;
}

export function SignaturVorschau({ html, titel, className, minHoehe = 60, maxHoehe }: SignaturVorschauProps) {
    const ref = useRef<HTMLIFrameElement>(null);
    const [hoehe, setHoehe] = useState(minHoehe);
    const dokument = useMemo(() => vorschauDokument(html), [html]);

    const messen = useCallback(() => {
        const body = ref.current?.contentDocument?.body;
        if (!body) return;
        // Höhe des Inhalts selbst – documentElement.scrollHeight wäre nie kleiner als das iframe und
        // würde nach einer hohen Vorlage nicht mehr schrumpfen.
        const gemessen = Math.ceil(body.getBoundingClientRect().height);
        if (gemessen > 0) setHoehe(Math.max(minHoehe, gemessen));
    }, [minHoehe]);

    // Bilder können nach dem load-Ereignis noch die Größe ändern (z. B. aus dem Cache) – nachmessen.
    useEffect(() => {
        const iframe = ref.current;
        if (!iframe || typeof ResizeObserver === 'undefined') return;
        let beobachter: ResizeObserver | null = null;
        const anhaengen = () => {
            beobachter?.disconnect();
            const body = iframe.contentDocument?.body;
            if (!body) return;
            beobachter = new ResizeObserver(() => messen());
            beobachter.observe(body);
        };
        iframe.addEventListener('load', anhaengen);
        return () => {
            iframe.removeEventListener('load', anhaengen);
            beobachter?.disconnect();
        };
    }, [messen]);

    const abgeschnitten = !!maxHoehe && hoehe > maxHoehe;
    const angezeigt = maxHoehe ? Math.min(hoehe, maxHoehe) : hoehe;

    return (
        <div className="relative">
            <iframe
                ref={ref}
                title={titel}
                sandbox="allow-same-origin"
                srcDoc={dokument}
                onLoad={messen}
                tabIndex={-1}
                className={cn('block w-full border-0 bg-white', className)}
                style={{ height: angezeigt }}
            />
            {abgeschnitten && (
                <div className="pointer-events-none absolute inset-x-0 bottom-0 h-12 bg-gradient-to-t from-white to-transparent" aria-hidden="true" />
            )}
        </div>
    );
}
