// mobile.jsx — react-zeiterfassung dashboard

const Ic = ({ name, size = 22, color, stroke = 2 }) => {
  const ref = React.useRef(null);
  React.useEffect(() => {
    if (!ref.current) return;
    ref.current.innerHTML = "";
    const el = document.createElement("i");
    el.setAttribute("data-lucide", name);
    ref.current.appendChild(el);
    window.lucide && window.lucide.createIcons({ attrs: { width: size, height: size, "stroke-width": stroke } });
  }, [name, size, stroke]);
  return <span ref={ref} style={{ display: "inline-flex", color, lineHeight: 0 }} />;
};

const Header = () => (
  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", padding: "12px 20px 14px" }}>
    <div>
      <div style={{ fontSize: 13, color: "var(--slate-500)", fontWeight: 500 }}>Dienstag, 10.3.2026</div>
      <div style={{ fontSize: 22, fontWeight: 700, color: "var(--slate-900)", marginTop: 2, letterSpacing: "-0.01em" }}>
        Hallo, Max Mustermann!
      </div>
    </div>
    <div style={{
      display: "inline-flex", alignItems: "center", gap: 6, padding: "6px 12px",
      background: "#dcfce7", color: "#166534", borderRadius: 999,
      fontSize: 13, fontWeight: 600, flexShrink: 0, marginTop: 4,
    }}>
      <Ic name="wifi" size={14} stroke={2.5} />
      Online
    </div>
  </div>
);

const Hero = () => {
  const [press, setPress] = React.useState(false);
  return (
    <div style={{ padding: "0 20px" }}>
      <button
        onMouseDown={() => setPress(true)} onMouseUp={() => setPress(false)} onMouseLeave={() => setPress(false)}
        onTouchStart={() => setPress(true)} onTouchEnd={() => setPress(false)}
        style={{
          width: "100%", background: "var(--brand)", color: "#fff", border: 0,
          borderRadius: 16, padding: 16, display: "flex", alignItems: "center", gap: 14,
          boxShadow: "var(--shadow-rose)",
          transform: press ? "scale(0.97)" : "scale(1)",
          transition: "transform 120ms var(--ease-out)",
        }}
      >
        <div style={{
          width: 48, height: 48, borderRadius: 12, background: "rgba(255,255,255,0.2)",
          display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0,
        }}>
          <svg width="22" height="22" viewBox="0 0 24 24" fill="#fff"><path d="M8 5v14l11-7z"/></svg>
        </div>
        <div style={{ flex: 1, textAlign: "left" }}>
          <div style={{ fontWeight: 700, fontSize: 17 }}>Zeit erfassen</div>
          <div style={{ fontSize: 13, opacity: 0.9, marginTop: 1 }}>Neue Buchung starten</div>
        </div>
        <Ic name="chevron-right" size={20} />
      </button>
    </div>
  );
};

const TileCard = ({ icon, label, tint }) => {
  const tints = {
    rose:   { bg: "var(--rose-100)",  fg: "var(--brand)" },
    amber:  { bg: "#fef3c7",          fg: "#b45309" },
    indigo: { bg: "#e0e7ff",          fg: "#4338ca" },
    green:  { bg: "#dcfce7",          fg: "#166534" },
  };
  const t = tints[tint] || tints.rose;
  return (
    <button style={{
      flex: 1, background: "#fff", border: "1px solid var(--border)",
      borderRadius: 14, padding: "16px 14px", display: "flex", flexDirection: "column",
      alignItems: "flex-start", gap: 10, boxShadow: "var(--shadow-sm)",
    }}>
      <span style={{ width: 40, height: 40, borderRadius: 10, background: t.bg, color: t.fg, display: "inline-flex", alignItems: "center", justifyContent: "center" }}>
        <Ic name={icon} size={20} />
      </span>
      <span style={{ fontSize: 15, fontWeight: 600, color: "var(--slate-900)" }}>{label}</span>
    </button>
  );
};

const ActionRow = ({ icon, title, subtitle, tint }) => {
  const tints = {
    indigo: { bg: "#e0e7ff",          fg: "#4338ca" },
    rose:   { bg: "var(--rose-100)",  fg: "var(--brand)" },
    green:  { bg: "#dcfce7",          fg: "#166534" },
  };
  const t = tints[tint] || tints.rose;
  return (
    <button style={{
      width: "100%", background: "#fff", border: "1px solid var(--border)",
      borderRadius: 14, padding: "14px 14px", display: "flex", alignItems: "center",
      gap: 14, boxShadow: "var(--shadow-sm)", textAlign: "left",
    }}>
      <span style={{ width: 40, height: 40, borderRadius: 10, background: t.bg, color: t.fg, display: "inline-flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
        <Ic name={icon} size={20} />
      </span>
      <div style={{ flex: 1 }}>
        <div style={{ fontSize: 15, fontWeight: 600, color: "var(--slate-900)" }}>{title}</div>
        <div style={{ fontSize: 12, color: "var(--fg-muted)", marginTop: 1 }}>{subtitle}</div>
      </div>
    </button>
  );
};

const TodayTile = () => (
  <button style={{
    width: "100%", background: "#fff", border: "1px solid var(--border)",
    borderRadius: 14, padding: "14px 16px", display: "flex", alignItems: "center",
    boxShadow: "var(--shadow-sm)", textAlign: "left",
  }}>
    <div style={{ flex: 1 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 13, color: "var(--fg-muted)", fontWeight: 500 }}>
        <Ic name="clock" size={14} /> Heute gearbeitet
      </div>
      <div style={{ fontSize: 28, fontWeight: 700, color: "var(--slate-900)", marginTop: 4, fontVariantNumeric: "tabular-nums", letterSpacing: "-0.01em" }}>
        0h 00min
      </div>
    </div>
    <Ic name="chevron-right" size={18} color="var(--slate-400)" />
  </button>
);

const MobileApp = () => (
  <div style={{ minHeight: "100%", background: "var(--slate-50)", paddingTop: 64, paddingBottom: 30 }}>
    <Header />
    <Hero />
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, padding: "14px 20px 0" }}>
      <TileCard icon="folder-open" label="Projekte"    tint="rose" />
      <TileCard icon="folder-open" label="Angebote"    tint="amber" />
      <TileCard icon="users"       label="Kunden"      tint="indigo" />
      <TileCard icon="users"       label="Lieferanten" tint="green" />
    </div>
    <div style={{ display: "flex", flexDirection: "column", gap: 10, padding: "14px 20px 0" }}>
      <ActionRow icon="calendar-days" title="Kalender"               subtitle="Termine & Einladungen"       tint="indigo" />
      <ActionRow icon="plane"         title="Abwesenheit beantragen" subtitle="Urlaub, Krankheit, Fortbildung" tint="rose" />
      <ActionRow icon="clock"         title="Saldenauswertung"        subtitle="Urlaub & Stunden übersicht"  tint="green" />
    </div>
    <div style={{ padding: "14px 20px 0" }}>
      <TodayTile />
    </div>
  </div>
);

ReactDOM.createRoot(document.getElementById("root")).render(
  <IOSDevice width={402} height={820}>
    <MobileApp />
  </IOSDevice>
);
