/**
 * Schematische Symbolbilder für Nahtarten (Stumpfnaht I/V/X, Kehlnaht, HV, DHV).
 * Nicht normgerecht nach DIN EN ISO 6947/9692 — für MVP ausreichend,
 * für Produktion ggf. durch fachgeprüfte Illustrationen ersetzen.
 *
 * Original-SVGs aus Claude-Design-Prototyp (illustrations.jsx) 1:1 übernommen.
 */

type GlyphProps = { className?: string };

const SvgFrame = ({ className, children }: { className?: string; children: React.ReactNode }) => (
    <svg viewBox="0 0 160 120" width="100%" height="100%" className={`block ${className ?? ''}`.trim()}>
        {children}
    </svg>
);

/** I-Naht — zwei stumpf aneinander stoßende Bleche ohne Fuge. */
export const NahtartStumpfI = ({ className }: GlyphProps) => (
    <SvgFrame className={className}>
        <rect x="18" y="52" width="58" height="22" fill="#cbd5e1" stroke="#334155" strokeWidth="1.5" />
        <rect x="84" y="52" width="58" height="22" fill="#cbd5e1" stroke="#334155" strokeWidth="1.5" />
        <path d="M76 52 L84 52 Q86 46 80 44 Q74 46 76 52 Z" fill="#e11d48" />
        <rect x="76" y="52" width="8" height="22" fill="#e11d48" />
        <path d="M76 74 Q80 78 84 74" fill="#be123c" />
        <line x1="77" y1="56" x2="83" y2="56" stroke="#9f1239" strokeWidth="0.5" />
        <line x1="77" y1="60" x2="83" y2="60" stroke="#9f1239" strokeWidth="0.5" />
        <line x1="77" y1="64" x2="83" y2="64" stroke="#9f1239" strokeWidth="0.5" />
        <line x1="77" y1="68" x2="83" y2="68" stroke="#9f1239" strokeWidth="0.5" />
    </SvgFrame>
);

/** V-Naht — 60°-V-Fuge mit Decklage. */
export const NahtartStumpfV = ({ className }: GlyphProps) => (
    <SvgFrame className={className}>
        <polygon points="18,52 76,52 66,74 18,74" fill="#cbd5e1" stroke="#334155" strokeWidth="1.5" />
        <polygon points="84,52 142,52 142,74 94,74" fill="#cbd5e1" stroke="#334155" strokeWidth="1.5" />
        <path d="M76 52 L84 52 Q88 46 80 42 Q72 46 76 52 Z" fill="#e11d48" />
        <polygon points="76,52 84,52 94,74 66,74" fill="#e11d48" />
        <path d="M66 74 Q80 80 94 74" fill="#be123c" />
        <line x1="72" y1="58" x2="88" y2="58" stroke="#9f1239" strokeWidth="0.5" />
        <line x1="70" y1="64" x2="90" y2="64" stroke="#9f1239" strokeWidth="0.5" />
        <line x1="68" y1="70" x2="92" y2="70" stroke="#9f1239" strokeWidth="0.5" />
    </SvgFrame>
);

/** X-Naht — Doppel-V-Fuge (beidseitig). */
export const NahtartStumpfX = ({ className }: GlyphProps) => (
    <SvgFrame className={className}>
        <polygon points="18,34 76,34 66,58 76,82 18,82" fill="#cbd5e1" stroke="#334155" strokeWidth="1.5" />
        <polygon points="84,34 142,34 142,82 84,82 94,58" fill="#cbd5e1" stroke="#334155" strokeWidth="1.5" />
        <polygon points="76,34 84,34 94,58 84,58 76,58 66,58" fill="#e11d48" />
        <polygon points="66,58 76,58 84,58 94,58 84,82 76,82" fill="#e11d48" />
        <path d="M76 34 Q80 28 84 34" fill="#be123c" />
        <path d="M76 82 Q80 88 84 82" fill="#be123c" />
    </SvgFrame>
);

/** Kehlnaht — T-Stoß mit beidseitigen Kehlnähten. */
export const NahtartKehl = ({ className }: GlyphProps) => (
    <SvgFrame className={className}>
        <rect x="20" y="72" width="122" height="18" fill="#cbd5e1" stroke="#334155" strokeWidth="1.5" />
        <rect x="72" y="20" width="18" height="52" fill="#cbd5e1" stroke="#334155" strokeWidth="1.5" />
        <polygon points="72,72 72,56 54,72" fill="#e11d48" stroke="#be123c" strokeWidth="1" />
        <polygon points="90,72 90,56 108,72" fill="#e11d48" stroke="#be123c" strokeWidth="1" />
        <line x1="60" y1="68" x2="68" y2="68" stroke="#9f1239" strokeWidth="0.5" />
        <line x1="63" y1="65" x2="70" y2="65" stroke="#9f1239" strokeWidth="0.5" />
        <line x1="92" y1="68" x2="100" y2="68" stroke="#9f1239" strokeWidth="0.5" />
        <line x1="92" y1="65" x2="97" y2="65" stroke="#9f1239" strokeWidth="0.5" />
    </SvgFrame>
);

/** HV-Naht — halbe V-Fuge (einseitig angeschrägt). */
export const NahtartHV = ({ className }: GlyphProps) => (
    <SvgFrame className={className}>
        <polygon points="18,52 76,52 66,74 18,74" fill="#cbd5e1" stroke="#334155" strokeWidth="1.5" />
        <rect x="76" y="52" width="66" height="22" fill="#cbd5e1" stroke="#334155" strokeWidth="1.5" />
        <path d="M76 52 L76 74 L66 74 Z" fill="#e11d48" />
        <path d="M66 74 Q72 78 76 74" fill="#be123c" />
        <path d="M66 52 Q72 48 76 52" fill="#be123c" opacity="0.8" />
        <line x1="68" y1="60" x2="74" y2="60" stroke="#9f1239" strokeWidth="0.5" />
        <line x1="67" y1="66" x2="74" y2="66" stroke="#9f1239" strokeWidth="0.5" />
        <line x1="66" y1="72" x2="75" y2="72" stroke="#9f1239" strokeWidth="0.5" />
    </SvgFrame>
);

/** DHV-Naht — doppelte HV-Fuge (K-Stoß). */
export const NahtartDHV = ({ className }: GlyphProps) => (
    <SvgFrame className={className}>
        <polygon points="18,34 76,34 66,58 76,82 18,82" fill="#cbd5e1" stroke="#334155" strokeWidth="1.5" />
        <rect x="84" y="34" width="58" height="48" fill="#cbd5e1" stroke="#334155" strokeWidth="1.5" />
        <path d="M76 34 L84 34 L84 58 L94 58 Z" fill="#e11d48" />
        <polygon points="66,58 84,58 84,82 76,82" fill="#e11d48" />
        <path d="M76 34 Q80 30 84 34" fill="#be123c" />
        <path d="M76 82 Q80 86 84 82" fill="#be123c" />
    </SvgFrame>
);

export type NahtartId = 'I' | 'V' | 'X' | 'Kehl' | 'HV' | 'DHV';

/** Dispatcher: wählt das passende Nahtart-Glyph anhand der ID. */
export const NahtGlyph = ({ kind, className }: { kind: string; className?: string }) => {
    switch (kind as NahtartId) {
        case 'I': return <NahtartStumpfI className={className} />;
        case 'V': return <NahtartStumpfV className={className} />;
        case 'X': return <NahtartStumpfX className={className} />;
        case 'Kehl': return <NahtartKehl className={className} />;
        case 'HV': return <NahtartHV className={className} />;
        case 'DHV': return <NahtartDHV className={className} />;
        default: return null;
    }
};
