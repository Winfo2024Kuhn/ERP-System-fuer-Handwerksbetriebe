// page.jsx — PageHeader, FilterBar, EmptyState, Pagination

const PageHeader = ({ eyebrow, title, subtitle, actions }) => (
  <div style={{ display: "flex", alignItems: "flex-end", justifyContent: "space-between", gap: 20, marginBottom: 20 }}>
    <div>
      <div style={{ fontSize: 12, fontWeight: 600, color: "var(--brand)", textTransform: "uppercase", letterSpacing: "0.06em" }}>{eyebrow}</div>
      <h1 style={{ fontSize: 30, fontWeight: 700, color: "var(--slate-900)", letterSpacing: "-0.01em", margin: "2px 0 4px" }}>{title}</h1>
      {subtitle && <div style={{ fontSize: 14, color: "var(--fg-muted)" }}>{subtitle}</div>}
    </div>
    <div style={{ display: "flex", gap: 8, flexShrink: 0 }}>{actions}</div>
  </div>
);

const Field = ({ label, children }) => (
  <div style={{ display: "flex", flexDirection: "column", flex: 1 }}>
    <label style={{ fontSize: 14, fontWeight: 600, color: "var(--slate-700)", marginBottom: 6 }}>{label}</label>
    {children}
  </div>
);

const inputStyle = {
  width: "100%", border: "1px solid var(--border)", borderRadius: 4,
  padding: "8px 12px", fontSize: 14, color: "var(--slate-900)",
  background: "#fff", fontFamily: "inherit", outline: "none",
};

const Input = (props) => {
  const [focus, setFocus] = React.useState(false);
  return <input {...props}
    onFocus={() => setFocus(true)} onBlur={() => setFocus(false)}
    style={{ ...inputStyle, boxShadow: focus ? "0 0 0 2px var(--brand-ring)" : "none", ...(props.style || {}) }} />;
};

const Select = ({ children, ...props }) => {
  const [focus, setFocus] = React.useState(false);
  return (
    <div style={{ position: "relative" }}>
      <select {...props}
        onFocus={() => setFocus(true)} onBlur={() => setFocus(false)}
        style={{ ...inputStyle, appearance: "none", paddingRight: 32, boxShadow: focus ? "0 0 0 2px var(--brand-ring)" : "none" }}>
        {children}
      </select>
      <span style={{ position: "absolute", right: 10, top: "50%", transform: "translateY(-50%)", pointerEvents: "none", color: "var(--slate-400)" }}>
        <Icon name="chevron-down" size={16} />
      </span>
    </div>
  );
};

const FilterBar = () => (
  <div style={{ background: "#fff", border: "1px solid var(--border)", borderRadius: 8, padding: 20, boxShadow: "var(--shadow-sm)", marginBottom: 16 }}>
    <div style={{ display: "flex", gap: 16, alignItems: "flex-end" }}>
      <Field label="Freitext"><Input placeholder="Bauvorhaben, Kunde…" /></Field>
      <Field label="Kunde"><Input placeholder="Kundenname" /></Field>
      <Field label="Status">
        <Select defaultValue="Alle">
          <option>Alle</option><option>Offen</option><option>Gebucht</option><option>Abgeschlossen</option>
        </Select>
      </Field>
      <div style={{ display: "flex", gap: 8, paddingBottom: 0 }}>
        <Button variant="default">Filtern</Button>
        <Button variant="outline">Reset</Button>
      </div>
    </div>
    <div style={{ fontSize: 12, color: "var(--fg-muted)", marginTop: 10 }}>
      Für Performance werden immer nur 12 Einträge auf einmal geladen.
    </div>
  </div>
);

const EmptyState = () => (
  <div style={{
    background: "#fff", border: "1px dashed var(--border-strong)", borderRadius: 8,
    padding: "60px 20px", textAlign: "center",
  }}>
    <div style={{
      width: 48, height: 48, borderRadius: 12, background: "var(--rose-50)",
      color: "var(--brand)", display: "inline-flex", alignItems: "center", justifyContent: "center",
      marginBottom: 12,
    }}>
      <Icon name="briefcase" size={22} />
    </div>
    <div style={{ fontSize: 14, color: "var(--fg-muted)" }}>Keine Projekte gefunden.</div>
  </div>
);

const Pagination = () => (
  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 16, padding: "0 4px" }}>
    <span style={{ fontSize: 13, color: "var(--fg-muted)" }}>Keine Projekte gefunden.</span>
    <div style={{ display: "flex", gap: 8 }}>
      <Button variant="outline" size="sm" icon="chevron-left">zurück</Button>
      <Button variant="outline" size="sm">Weiter <Icon name="chevron-right" size={14} style={{ marginLeft: 4 }}/></Button>
    </div>
  </div>
);

Object.assign(window, { PageHeader, FilterBar, EmptyState, Pagination });
