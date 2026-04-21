/**
 * Schematische Symbolbilder für Schweißverfahren (MAG/MIG/WIG/E-Hand).
 * Nicht normgerecht nach DIN EN ISO 6947/9692 — für MVP ausreichend,
 * für Produktion ggf. durch fachgeprüfte Illustrationen ersetzen.
 *
 * Original-SVGs aus Claude-Design-Prototyp (illustrations.jsx) 1:1 übernommen.
 */

type GlyphProps = { className?: string };

const GroundPlate = ({ y = 92 }: { y?: number }) => (
    <>
        <rect x="12" y={y} width="136" height="14" rx="2" fill="#e2e8f0" stroke="#cbd5e1" strokeWidth="1" />
        <line x1="12" y1={y + 14} x2="148" y2={y + 14} stroke="#94a3b8" strokeWidth="1" />
    </>
);

const SvgFrame = ({ className, children }: { className?: string; children: React.ReactNode }) => (
    <svg viewBox="0 0 160 120" width="100%" height="100%" className={`block ${className ?? ''}`.trim()}>
        {children}
    </svg>
);

/** MAG (135) — Metall-Aktivgas: Brennerkörper mit Gasdüse und Aktivgaswolke. */
export const VerfahrenMAG = ({ className }: GlyphProps) => (
    <SvgFrame className={className}>
        <rect x="62" y="14" width="36" height="48" rx="6" fill="#fff" stroke="#334155" strokeWidth="2" />
        <rect x="68" y="8" width="24" height="10" rx="2" fill="#334155" />
        <rect x="74" y="62" width="12" height="14" fill="#334155" />
        <polygon points="68,76 92,76 86,90 74,90" fill="#64748b" stroke="#334155" strokeWidth="1.5" />
        <line x1="80" y1="90" x2="80" y2="94" stroke="#e11d48" strokeWidth="2" />
        <path d="M72 95 Q80 99 88 95" stroke="#f43f5e" strokeWidth="2" fill="none" />
        <circle cx="80" cy="95" r="3" fill="#fbbf24" opacity="0.9" />
        <circle cx="60" cy="82" r="1.5" fill="#cbd5e1" />
        <circle cx="100" cy="82" r="1.5" fill="#cbd5e1" />
        <circle cx="54" cy="76" r="1" fill="#cbd5e1" />
        <circle cx="106" cy="76" r="1" fill="#cbd5e1" />
        <GroundPlate />
    </SvgFrame>
);

/** MIG (131) — Metall-Inertgas: wie MAG, aber mit Argon-Marker und symmetrischem Inertgas-Muster. */
export const VerfahrenMIG = ({ className }: GlyphProps) => (
    <SvgFrame className={className}>
        <rect x="62" y="14" width="36" height="48" rx="6" fill="#fff" stroke="#334155" strokeWidth="2" />
        <rect x="68" y="8" width="24" height="10" rx="2" fill="#334155" />
        <text x="80" y="44" textAnchor="middle" fontSize="12" fontWeight="700" fill="#334155" fontFamily="ui-sans-serif, system-ui, sans-serif">Ar</text>
        <rect x="74" y="62" width="12" height="14" fill="#334155" />
        <polygon points="68,76 92,76 86,90 74,90" fill="#64748b" stroke="#334155" strokeWidth="1.5" />
        <line x1="80" y1="90" x2="80" y2="94" stroke="#e11d48" strokeWidth="2" />
        <path d="M72 95 Q80 99 88 95" stroke="#f43f5e" strokeWidth="2" fill="none" />
        <circle cx="80" cy="95" r="3" fill="#fbbf24" opacity="0.9" />
        <circle cx="58" cy="80" r="1.3" fill="#94a3b8" />
        <circle cx="102" cy="80" r="1.3" fill="#94a3b8" />
        <circle cx="50" cy="72" r="1.3" fill="#94a3b8" />
        <circle cx="110" cy="72" r="1.3" fill="#94a3b8" />
        <GroundPlate />
    </SvgFrame>
);

/** WIG / TIG (141) — Wolfram-Inertgas: stiftförmiger Brenner mit Keramikdüse und Zusatzdraht. */
export const VerfahrenWIG = ({ className }: GlyphProps) => (
    <SvgFrame className={className}>
        <g transform="rotate(-18 80 60)">
            <rect x="68" y="6" width="24" height="44" rx="4" fill="#fff" stroke="#334155" strokeWidth="2" />
            <rect x="70" y="10" width="20" height="4" fill="#e11d48" />
            <polygon points="68,50 92,50 88,66 72,66" fill="#f1f5f9" stroke="#334155" strokeWidth="1.5" />
            <line x1="80" y1="66" x2="80" y2="74" stroke="#334155" strokeWidth="2" />
        </g>
        <path d="M88 93 Q94 99 100 93" stroke="#f43f5e" strokeWidth="2" fill="none" />
        <circle cx="94" cy="94" r="3" fill="#fbbf24" opacity="0.9" />
        <line x1="118" y1="72" x2="96" y2="92" stroke="#94a3b8" strokeWidth="2" />
        <circle cx="118" cy="72" r="2" fill="#64748b" />
        <GroundPlate />
    </SvgFrame>
);

/** E-Hand / MMA (111) — Elektrodenhalter mit Stabelektrode und Funkenspritzern. */
export const VerfahrenMMA = ({ className }: GlyphProps) => (
    <SvgFrame className={className}>
        <g transform="rotate(-10 80 50)">
            <rect x="60" y="10" width="40" height="18" rx="3" fill="#334155" />
            <rect x="62" y="13" width="4" height="12" fill="#e11d48" />
            <polygon points="96,10 108,18 96,28" fill="#475569" />
            <rect x="78" y="28" width="4" height="50" fill="#e2e8f0" stroke="#334155" strokeWidth="1" />
            <rect x="78" y="72" width="4" height="6" fill="#64748b" />
        </g>
        <path d="M72 93 Q80 99 88 93" stroke="#f43f5e" strokeWidth="2" fill="none" />
        <circle cx="80" cy="94" r="3.2" fill="#fbbf24" opacity="0.9" />
        <circle cx="96" cy="88" r="1" fill="#fbbf24" />
        <circle cx="66" cy="90" r="1" fill="#fbbf24" />
        <circle cx="102" cy="92" r="0.8" fill="#fbbf24" />
        <GroundPlate />
    </SvgFrame>
);

export type VerfahrenId = 'MAG' | 'MIG' | 'WIG' | 'MMA';

/** Dispatcher: wählt das passende Verfahrens-Glyph anhand der ID. */
export const VerfahrenGlyph = ({ letters, className }: { letters: string; className?: string }) => {
    switch (letters as VerfahrenId) {
        case 'MAG': return <VerfahrenMAG className={className} />;
        case 'MIG': return <VerfahrenMIG className={className} />;
        case 'WIG': return <VerfahrenWIG className={className} />;
        case 'MMA': return <VerfahrenMMA className={className} />;
        default: return null;
    }
};
