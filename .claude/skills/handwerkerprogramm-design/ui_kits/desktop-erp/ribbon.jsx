// ribbon.jsx — top tab ribbon + subgroup ribbon

const MAIN_TABS = [
  { id: "vorlagen",   label: "Vorlagen & Stammdaten" },
  { id: "projekt",    label: "Projektmanagement",     active: true },
  { id: "zeit",       label: "Zeiterfassung" },
  { id: "komm",       label: "Kommunikation" },
  { id: "finanzen",   label: "Finanzen & Controlling" },
];

// Subgroup entries for the active tab (Projektmanagement).
// "Dokumente" is the subgroup added in the latest revision.
const SUBGROUPS = [
  { group: "AUFTRÄGE", items: [
    { icon: "briefcase",     label: "Projekte",     active: true },
    { icon: "file-check",    label: "Anfragen" },
  ]},
  { group: "DOKUMENTE", items: [
    { icon: "file-text",     label: "Dokumente" },
  ]},
  { group: "PLANUNG", items: [
    { icon: "calendar",      label: "Kalender" },
  ]},
  { group: "EINKAUF", items: [
    { icon: "shopping-cart", label: "Bestellungen" },
    { icon: "list",          label: "Bedarf" },
  ]},
];

const BrandMark = () => (
  <div style={{ display: "flex", alignItems: "center", gap: 10, paddingRight: 20, borderRight: "1px solid var(--border)", height: "100%" }}>
    <img src="../../assets/app_logo.png" alt="Logo"
      style={{ width: 28, height: 28, borderRadius: 6, display: "block", objectFit: "cover" }} />
    <div style={{ display: "flex", flexDirection: "column", lineHeight: 1.1 }}>
      <span style={{ fontSize: 9, fontWeight: 700, color: "var(--slate-400)", letterSpacing: "0.08em", textTransform: "uppercase" }}>Firma</span>
      <span style={{ fontSize: 13, fontWeight: 700, color: "var(--slate-900)" }}>Musterfirma</span>
    </div>
  </div>
);

const MainTab = ({ label, active }) => {
  const [h, setH] = React.useState(false);
  return (
    <button
      onMouseEnter={() => setH(true)} onMouseLeave={() => setH(false)}
      style={{
        height: "100%", padding: "0 18px", border: 0, background: "transparent",
        fontSize: 14, fontWeight: active ? 600 : 500,
        color: active ? "var(--brand)" : (h ? "var(--slate-900)" : "var(--slate-600)"),
        borderBottom: active ? "2px solid var(--brand)" : "2px solid transparent",
        position: "relative", transition: "color var(--dur)",
      }}
    >{label}</button>
  );
};

const RibbonTile = ({ icon, label, active }) => {
  const [h, setH] = React.useState(false);
  return (
    <button
      onMouseEnter={() => setH(true)} onMouseLeave={() => setH(false)}
      style={{
        display: "flex", flexDirection: "column", alignItems: "center", gap: 6,
        padding: "8px 14px", border: 0, background: h && !active ? "var(--slate-100)" : "transparent",
        borderRadius: 8, minWidth: 68, transition: "all var(--dur)",
      }}
    >
      <span style={{
        width: 36, height: 36, borderRadius: 10,
        background: active ? "var(--rose-100)" : "var(--slate-100)",
        color: active ? "var(--brand)" : "var(--slate-500)",
        display: "flex", alignItems: "center", justifyContent: "center",
      }}>
        <Icon name={icon} size={20} />
      </span>
      <span style={{
        fontSize: 11, fontWeight: active ? 600 : 500,
        color: active ? "var(--slate-900)" : "var(--slate-700)", lineHeight: 1,
      }}>{label}</span>
    </button>
  );
};

const SubgroupColumn = ({ group, items }) => (
  <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-start", paddingRight: 16, borderRight: "1px solid var(--border)", marginRight: 16 }}>
    <span style={{ fontSize: 9, fontWeight: 700, color: "var(--slate-400)", letterSpacing: "0.08em", textTransform: "uppercase", padding: "0 10px", marginBottom: 2 }}>{group}</span>
    <div style={{ display: "flex", gap: 4 }}>
      {items.map((it, i) => <RibbonTile key={i} {...it} />)}
    </div>
  </div>
);

const UserPill = () => (
  <div style={{ display: "flex", alignItems: "center", gap: 8, padding: "4px 10px 4px 4px", border: "1px solid var(--border)", borderRadius: 999, background: "#fff" }}>
    <span style={{
      width: 28, height: 28, borderRadius: 999, background: "var(--slate-100)",
      color: "var(--slate-500)", display: "flex", alignItems: "center", justifyContent: "center",
    }}>
      <Icon name="user" size={16} />
    </span>
    <div style={{ display: "flex", flexDirection: "column", lineHeight: 1.1 }}>
      <span style={{ fontSize: 12, fontWeight: 600, color: "var(--slate-900)" }}>Lade…</span>
      <span style={{ fontSize: 10, color: "var(--fg-muted)" }}>Angemeldet</span>
    </div>
    <Icon name="chevron-down" size={14} color="var(--slate-400)" style={{ marginLeft: 2 }}/>
  </div>
);

const Ribbon = () => (
  <div style={{ background: "#fff", borderBottom: "1px solid var(--border)" }}>
    {/* Row 1: brand + main tabs + user */}
    <div style={{ height: 56, display: "flex", alignItems: "center", padding: "0 20px", gap: 12 }}>
      <BrandMark />
      <div style={{ display: "flex", alignItems: "center", height: "100%", flex: 1 }}>
        {MAIN_TABS.map(t => <MainTab key={t.id} {...t} />)}
      </div>
      <button style={{ border: 0, background: "transparent", padding: 8, borderRadius: 8, color: "var(--slate-500)" }} aria-label="Benachrichtigungen">
        <Icon name="bell" size={18} />
      </button>
      <UserPill />
      <button style={{ border: 0, background: "transparent", padding: 6, color: "var(--slate-400)" }} aria-label="Menüleiste einklappen" aria-expanded="true">
        <Icon name="chevron-up" size={16} />
      </button>
    </div>
    {/* Row 2: subgroup ribbon */}
    <div style={{ padding: "8px 20px 12px", display: "flex", alignItems: "flex-end", background: "#fff", borderTop: "1px solid var(--border-soft)" }}>
      {SUBGROUPS.map((sg, i) => <SubgroupColumn key={i} {...sg} />)}
    </div>
  </div>
);

Object.assign(window, { Ribbon });
