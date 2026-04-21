/**
 * Schematische Symbolbilder für Schweißpositionen nach EN ISO 6947
 * (PA, PB, PC, PD, PE, PF, PG).
 *
 * Jede Position existiert in zwei Nahtart-Varianten:
 *   - "butt"   → Stumpfstoß (zwei Bleche stoßen aneinander)
 *   - "fillet" → Kehlnaht / T-Stoß
 *
 * Der Schwerkraftpfeil (rechts oben im PosFrame) zeigt immer nach unten,
 * unabhängig von der Orientierung des Werkstücks — damit der Betrachter
 * die Lage einordnen kann.
 *
 * Nicht normgerecht nach DIN EN ISO 6947/9692 — für MVP ausreichend,
 * für Produktion ggf. durch fachgeprüfte Illustrationen ersetzen.
 */

type Joint = 'butt' | 'fillet';
type GlyphProps = { className?: string; joint?: Joint };

const PLATE_FILL = '#cbd5e1';
const PLATE_STROKE = '#334155';
const PLATE_SW = 1.5;
const WELD = '#e11d48';
const WELD_DARK = '#be123c';

const PosFrame = ({ className, children }: { className?: string; children: React.ReactNode }) => (
    <svg viewBox="0 0 160 120" width="100%" height="100%" className={`block ${className ?? ''}`.trim()}>
        <g opacity="0.55">
            <line x1="142" y1="14" x2="142" y2="30" stroke="#94a3b8" strokeWidth="1.2" />
            <polygon points="138,28 142,34 146,28" fill="#94a3b8" />
            <text x="150" y="26" fontSize="8" fill="#64748b">g</text>
        </g>
        {children}
    </svg>
);

// ---------------------------------------------------------------------------
// Stumpfnaht-Varianten (butt) — zwei Bleche stoßen aneinander
// ---------------------------------------------------------------------------

const PA_butt = () => (
    <>
        <rect x="20" y="70" width="58" height="18" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
        <rect x="82" y="70" width="58" height="18" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
        <rect x="48" y="63" width="64" height="8" rx="3" fill={WELD} />
        <ellipse cx="80" cy="62" rx="28" ry="2" fill={WELD_DARK} />
    </>
);

// PB stumpf — horizontal leicht schräg, Naht horizontal obenauf
const PB_butt = () => (
    <>
        <g transform="rotate(-10 80 78)">
            <rect x="20" y="70" width="58" height="18" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
            <rect x="82" y="70" width="58" height="18" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
            <rect x="48" y="63" width="64" height="8" rx="3" fill={WELD} />
        </g>
    </>
);

// PC stumpf — Bleche vertikal gestapelt, horizontale Naht dazwischen (Querposition)
const PC_butt = () => (
    <>
        <rect x="30" y="22" width="100" height="38" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
        <rect x="30" y="64" width="100" height="38" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
        <rect x="30" y="56" width="100" height="8" rx="3" fill={WELD} />
        <line x1="36" y1="60" x2="124" y2="60" stroke={WELD_DARK} strokeWidth="0.6" />
    </>
);

// PD stumpf — horizontal überkopf leicht schräg
const PD_butt = () => (
    <>
        <g transform="rotate(10 80 38)">
            <rect x="20" y="32" width="58" height="18" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
            <rect x="82" y="32" width="58" height="18" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
            <rect x="48" y="48" width="64" height="8" rx="3" fill={WELD} />
        </g>
    </>
);

// PE stumpf — horizontal überkopf, Naht an Unterseite
const PE_butt = () => (
    <>
        <rect x="20" y="28" width="58" height="18" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
        <rect x="82" y="28" width="58" height="18" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
        <rect x="48" y="46" width="64" height="8" rx="3" fill={WELD} />
        <ellipse cx="80" cy="56" rx="28" ry="2" fill={WELD_DARK} />
    </>
);

// PF stumpf — vertikal, Naht vertikal, Pfeil nach oben (steigend)
const PF_butt = () => (
    <>
        <rect x="34" y="20" width="42" height="84" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
        <rect x="84" y="20" width="42" height="84" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
        <rect x="76" y="32" width="8" height="72" rx="3" fill={WELD} />
        <polygon points="74,36 80,24 86,36" fill={WELD_DARK} />
    </>
);

// PG stumpf — vertikal, Naht vertikal, Pfeil nach unten (fallend)
const PG_butt = () => (
    <>
        <rect x="34" y="20" width="42" height="84" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
        <rect x="84" y="20" width="42" height="84" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
        <rect x="76" y="16" width="8" height="72" rx="3" fill={WELD} />
        <polygon points="74,84 80,96 86,84" fill={WELD_DARK} />
    </>
);

// ---------------------------------------------------------------------------
// Kehlnaht-Varianten (fillet) — T-Stoß, beidseitige Kehlnähte
// ---------------------------------------------------------------------------

// PA Kehl — T-Stoß um -45° gekippt (Wanne, Winkel offen nach oben)
const PA_fillet = () => (
    <>
        <g transform="rotate(-45 80 72)">
            <rect x="18" y="72" width="124" height="16" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
            <rect x="72" y="34" width="16" height="38" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
            <polygon points="72,72 72,60 60,72" fill={WELD} />
            <polygon points="88,72 88,60 100,72" fill={WELD} />
        </g>
    </>
);

// PB Kehl — T-Stoß aufrecht, Kehlnaht beidseitig an der Kante
const PB_fillet = () => (
    <>
        <rect x="18" y="80" width="124" height="16" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
        <rect x="72" y="28" width="16" height="52" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
        <polygon points="72,80 72,64 56,80" fill={WELD} stroke={WELD_DARK} strokeWidth={1} />
        <polygon points="88,80 88,64 104,80" fill={WELD} stroke={WELD_DARK} strokeWidth={1} />
    </>
);

// PC Kehl — T-Stoß mit vertikalem Steg, horizontal verlaufende Kehlnaht
const PC_fillet = () => (
    <>
        <rect x="30" y="18" width="16" height="84" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
        <rect x="46" y="52" width="86" height="16" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
        <polygon points="46,52 62,52 46,40" fill={WELD} stroke={WELD_DARK} strokeWidth={1} />
        <polygon points="46,68 62,68 46,80" fill={WELD} stroke={WELD_DARK} strokeWidth={1} />
    </>
);

// PD Kehl — T-Stoß kopfüber (Deckplatte oben, Steg hängt nach unten)
const PD_fillet = () => (
    <>
        <rect x="18" y="24" width="124" height="16" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
        <rect x="72" y="40" width="16" height="52" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
        <polygon points="72,40 72,56 56,40" fill={WELD} stroke={WELD_DARK} strokeWidth={1} />
        <polygon points="88,40 88,56 104,40" fill={WELD} stroke={WELD_DARK} strokeWidth={1} />
    </>
);

// PE Kehl — T-Stoß kopfüber, leicht andere Anordnung als PD (überkopf flach)
const PE_fillet = () => (
    <>
        <rect x="18" y="28" width="124" height="16" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
        <rect x="72" y="44" width="16" height="50" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
        <polygon points="72,44 72,58 60,44" fill={WELD} stroke={WELD_DARK} strokeWidth={1} />
        <polygon points="88,44 88,58 100,44" fill={WELD} stroke={WELD_DARK} strokeWidth={1} />
        <ellipse cx="80" cy="52" rx="12" ry="1.5" fill={WELD_DARK} opacity="0.55" />
    </>
);

// PF Kehl — T-Stoß vertikal mit Pfeil nach oben
const PF_fillet = () => (
    <>
        <rect x="30" y="14" width="16" height="90" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
        <rect x="46" y="50" width="84" height="16" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
        <polygon points="46,50 62,50 46,34" fill={WELD} />
        <polygon points="46,66 62,66 46,82" fill={WELD} />
        <rect x="56" y="28" width="6" height="64" rx="3" fill={WELD} opacity="0.35" />
        <polygon points="54,32 59,20 64,32" fill={WELD_DARK} />
    </>
);

// PG Kehl — T-Stoß vertikal mit Pfeil nach unten
const PG_fillet = () => (
    <>
        <rect x="30" y="14" width="16" height="90" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
        <rect x="46" y="50" width="84" height="16" fill={PLATE_FILL} stroke={PLATE_STROKE} strokeWidth={PLATE_SW} />
        <polygon points="46,50 62,50 46,34" fill={WELD} />
        <polygon points="46,66 62,66 46,82" fill={WELD} />
        <rect x="56" y="26" width="6" height="64" rx="3" fill={WELD} opacity="0.35" />
        <polygon points="54,86 59,98 64,86" fill={WELD_DARK} />
    </>
);

// ---------------------------------------------------------------------------
// Öffentliche Komponenten
// ---------------------------------------------------------------------------

const makePos = (Butt: () => React.ReactElement, Fillet: () => React.ReactElement) =>
    ({ joint = 'butt', className }: GlyphProps) => (
        <PosFrame className={className}>
            {joint === 'fillet' ? <Fillet /> : <Butt />}
        </PosFrame>
    );

/** PA — Wannenlage (flach, Schweißen von oben). */
export const PosPA = makePos(PA_butt, PA_fillet);
/** PB — Horizontal-Vertikal (Kehlnaht-typisch). */
export const PosPB = makePos(PB_butt, PB_fillet);
/** PC — Querposition (horizontale Naht auf vertikaler Fläche). */
export const PosPC = makePos(PC_butt, PC_fillet);
/** PD — Horizontal-Überkopf (Kehlnaht-typisch). */
export const PosPD = makePos(PD_butt, PD_fillet);
/** PE — Überkopf (flach, von unten schweißen). */
export const PosPE = makePos(PE_butt, PE_fillet);
/** PF — Steigend (vertikal aufwärts). */
export const PosPF = makePos(PF_butt, PF_fillet);
/** PG — Fallend (vertikal abwärts). */
export const PosPG = makePos(PG_butt, PG_fillet);

export type PositionId = 'PA' | 'PB' | 'PC' | 'PD' | 'PE' | 'PF' | 'PG';

/**
 * Dispatcher: wählt Positions-Glyph anhand der Positions-ID.
 * Mit `nahtartKind='Kehl'` werden die Kehlnaht-Varianten (T-Stoß) gezeichnet,
 * sonst die Stumpfnaht-Varianten.
 */
export const PositionGlyph = ({
    id,
    nahtartKind,
    className,
}: {
    id: string;
    nahtartKind?: string;
    className?: string;
}) => {
    const joint: Joint = nahtartKind === 'Kehl' ? 'fillet' : 'butt';
    switch (id as PositionId) {
        case 'PA': return <PosPA joint={joint} className={className} />;
        case 'PB': return <PosPB joint={joint} className={className} />;
        case 'PC': return <PosPC joint={joint} className={className} />;
        case 'PD': return <PosPD joint={joint} className={className} />;
        case 'PE': return <PosPE joint={joint} className={className} />;
        case 'PF': return <PosPF joint={joint} className={className} />;
        case 'PG': return <PosPG joint={joint} className={className} />;
        default: return null;
    }
};
