// chrome.jsx — shared bits for the desktop ERP kit

const Icon = ({ name, size = 16, color, strokeWidth = 2, style }) => {
  const ref = React.useRef(null);
  React.useEffect(() => {
    if (!ref.current) return;
    ref.current.innerHTML = "";
    const el = document.createElement("i");
    el.setAttribute("data-lucide", name);
    ref.current.appendChild(el);
    window.lucide && window.lucide.createIcons({
      attrs: { width: size, height: size, "stroke-width": strokeWidth }
    });
  }, [name, size, strokeWidth]);
  return <span ref={ref} style={{ display: "inline-flex", color, lineHeight: 0, ...style }} />;
};

// Button — desktop variants used in the PC frontend
const Button = ({ variant = "default", size = "md", icon, children, onClick, style }) => {
  const base = {
    display: "inline-flex", alignItems: "center", justifyContent: "center", gap: 8,
    fontFamily: "var(--font-sans)", fontWeight: 500, border: "1px solid transparent",
    transition: "all var(--dur)", cursor: "pointer", whiteSpace: "nowrap",
  };
  const sizes = {
    sm: { padding: "6px 12px", fontSize: 14, borderRadius: 4 },
    md: { padding: "8px 16px", fontSize: 14, borderRadius: 8 },
    lg: { padding: "10px 20px", fontSize: 15, borderRadius: 8 },
  };
  const variants = {
    default: { background: "var(--brand)", color: "#fff", borderColor: "var(--brand)" },
    outline: { background: "#fff", color: "var(--rose-700)", borderColor: "var(--rose-200)" },
    ghost:   { background: "transparent", color: "var(--rose-700)" },
    secondary: { background: "var(--slate-100)", color: "var(--slate-800)", borderColor: "var(--slate-200)" },
  };
  const hover = {
    default: { background: "var(--brand-hover)", borderColor: "var(--brand-hover)" },
    outline: { background: "var(--rose-50)" },
    ghost:   { background: "var(--rose-100)" },
    secondary: { background: "var(--slate-200)" },
  };
  const [h, setH] = React.useState(false);
  return (
    <button
      onClick={onClick}
      onMouseEnter={() => setH(true)}
      onMouseLeave={() => setH(false)}
      style={{ ...base, ...sizes[size], ...variants[variant], ...(h ? hover[variant] : {}), ...style }}
    >
      {icon && <Icon name={icon} size={16} />}
      {children}
    </button>
  );
};

// Floating KI-Hilfe FAB — rose-600 pill bottom-right
const KIHilfeFab = () => {
  const [h, setH] = React.useState(false);
  return (
    <button
      onMouseEnter={() => setH(true)} onMouseLeave={() => setH(false)}
      style={{
        position: "fixed", right: 24, bottom: 24,
        background: "var(--brand)", color: "#fff", border: 0,
        padding: "12px 20px", borderRadius: 999, fontWeight: 600, fontSize: 14,
        display: "inline-flex", alignItems: "center", gap: 8,
        boxShadow: h ? "var(--shadow-rose-hover)" : "var(--shadow-rose)",
        transform: h ? "translateY(-2px)" : "none",
        transition: "all var(--dur)",
      }}
    >
      <Icon name="gem" size={16} />
      KI-Hilfe
    </button>
  );
};

Object.assign(window, { Icon, Button, KIHilfeFab });
