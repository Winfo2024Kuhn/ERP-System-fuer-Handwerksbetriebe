/* @ds-bundle: {"format":4,"namespace":"HandwerkerprogrammDesignSystem_1ed4e9","components":[],"sourceHashes":{"ui_kits/desktop-erp/app.jsx":"ef10dc570263","ui_kits/desktop-erp/chrome.jsx":"6297acbb5d93","ui_kits/desktop-erp/page.jsx":"6de184b31d8b","ui_kits/desktop-erp/ribbon.jsx":"cf8714c2e747","ui_kits/mobile-zeiterfassung/ios-frame.jsx":"d67eb3ffe562","ui_kits/mobile-zeiterfassung/mobile.jsx":"8b868df51f21"},"inlinedExternals":[],"unexposedExports":[]} */

(() => {

const __ds_ns = (window.HandwerkerprogrammDesignSystem_1ed4e9 = window.HandwerkerprogrammDesignSystem_1ed4e9 || {});

const __ds_scope = {};

(__ds_ns.__errors = __ds_ns.__errors || []);

// ui_kits/desktop-erp/app.jsx
try { (() => {
// app.jsx — compose the desktop ERP app

const DesktopApp = () => /*#__PURE__*/React.createElement("div", {
  style: {
    minHeight: "100vh",
    background: "var(--slate-50)"
  }
}, /*#__PURE__*/React.createElement(Ribbon, null), /*#__PURE__*/React.createElement("main", {
  style: {
    maxWidth: 1280,
    margin: "0 auto",
    padding: "32px 32px 80px"
  }
}, /*#__PURE__*/React.createElement(PageHeader, {
  eyebrow: "PROJEKTMANAGEMENT",
  title: "PROJEKT\xDCBERSICHT",
  subtitle: "\xDCbersicht und Verwaltung Ihrer Projekte.",
  actions: /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement(Button, {
    variant: "default",
    icon: "plus"
  }, "Neues Projekt"), /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    icon: "refresh-cw"
  }, "Aktualisieren"))
}), /*#__PURE__*/React.createElement(FilterBar, null), /*#__PURE__*/React.createElement(EmptyState, null), /*#__PURE__*/React.createElement(Pagination, null)), /*#__PURE__*/React.createElement(KIHilfeFab, null));
ReactDOM.createRoot(document.getElementById("root")).render(/*#__PURE__*/React.createElement(DesktopApp, null));
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/desktop-erp/app.jsx", error: String((e && e.message) || e) }); }

// ui_kits/desktop-erp/chrome.jsx
try { (() => {
// chrome.jsx — shared bits for the desktop ERP kit

const Icon = ({
  name,
  size = 16,
  color,
  strokeWidth = 2,
  style
}) => {
  const ref = React.useRef(null);
  React.useEffect(() => {
    if (!ref.current) return;
    ref.current.innerHTML = "";
    const el = document.createElement("i");
    el.setAttribute("data-lucide", name);
    ref.current.appendChild(el);
    window.lucide && window.lucide.createIcons({
      attrs: {
        width: size,
        height: size,
        "stroke-width": strokeWidth
      }
    });
  }, [name, size, strokeWidth]);
  return /*#__PURE__*/React.createElement("span", {
    ref: ref,
    style: {
      display: "inline-flex",
      color,
      lineHeight: 0,
      ...style
    }
  });
};

// Button — desktop variants used in the PC frontend
const Button = ({
  variant = "default",
  size = "md",
  icon,
  children,
  onClick,
  style
}) => {
  const base = {
    display: "inline-flex",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    fontFamily: "var(--font-sans)",
    fontWeight: 500,
    border: "1px solid transparent",
    transition: "all var(--dur)",
    cursor: "pointer",
    whiteSpace: "nowrap"
  };
  const sizes = {
    sm: {
      padding: "6px 12px",
      fontSize: 14,
      borderRadius: 4
    },
    md: {
      padding: "8px 16px",
      fontSize: 14,
      borderRadius: 8
    },
    lg: {
      padding: "10px 20px",
      fontSize: 15,
      borderRadius: 8
    }
  };
  const variants = {
    default: {
      background: "var(--brand)",
      color: "#fff",
      borderColor: "var(--brand)"
    },
    outline: {
      background: "#fff",
      color: "var(--rose-700)",
      borderColor: "var(--rose-200)"
    },
    ghost: {
      background: "transparent",
      color: "var(--rose-700)"
    },
    secondary: {
      background: "var(--slate-100)",
      color: "var(--slate-800)",
      borderColor: "var(--slate-200)"
    }
  };
  const hover = {
    default: {
      background: "var(--brand-hover)",
      borderColor: "var(--brand-hover)"
    },
    outline: {
      background: "var(--rose-50)"
    },
    ghost: {
      background: "var(--rose-100)"
    },
    secondary: {
      background: "var(--slate-200)"
    }
  };
  const [h, setH] = React.useState(false);
  return /*#__PURE__*/React.createElement("button", {
    onClick: onClick,
    onMouseEnter: () => setH(true),
    onMouseLeave: () => setH(false),
    style: {
      ...base,
      ...sizes[size],
      ...variants[variant],
      ...(h ? hover[variant] : {}),
      ...style
    }
  }, icon && /*#__PURE__*/React.createElement(Icon, {
    name: icon,
    size: 16
  }), children);
};

// Floating KI-Hilfe FAB — rose-600 pill bottom-right
const KIHilfeFab = () => {
  const [h, setH] = React.useState(false);
  return /*#__PURE__*/React.createElement("button", {
    onMouseEnter: () => setH(true),
    onMouseLeave: () => setH(false),
    style: {
      position: "fixed",
      right: 24,
      bottom: 24,
      background: "var(--brand)",
      color: "#fff",
      border: 0,
      padding: "12px 20px",
      borderRadius: 999,
      fontWeight: 600,
      fontSize: 14,
      display: "inline-flex",
      alignItems: "center",
      gap: 8,
      boxShadow: h ? "var(--shadow-rose-hover)" : "var(--shadow-rose)",
      transform: h ? "translateY(-2px)" : "none",
      transition: "all var(--dur)"
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "gem",
    size: 16
  }), "KI-Hilfe");
};
Object.assign(window, {
  Icon,
  Button,
  KIHilfeFab
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/desktop-erp/chrome.jsx", error: String((e && e.message) || e) }); }

// ui_kits/desktop-erp/page.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
// page.jsx — PageHeader, FilterBar, EmptyState, Pagination

const PageHeader = ({
  eyebrow,
  title,
  subtitle,
  actions
}) => /*#__PURE__*/React.createElement("div", {
  style: {
    display: "flex",
    alignItems: "flex-end",
    justifyContent: "space-between",
    gap: 20,
    marginBottom: 20
  }
}, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
  style: {
    fontSize: 12,
    fontWeight: 600,
    color: "var(--brand)",
    textTransform: "uppercase",
    letterSpacing: "0.06em"
  }
}, eyebrow), /*#__PURE__*/React.createElement("h1", {
  style: {
    fontSize: 30,
    fontWeight: 700,
    color: "var(--slate-900)",
    letterSpacing: "-0.01em",
    margin: "2px 0 4px"
  }
}, title), subtitle && /*#__PURE__*/React.createElement("div", {
  style: {
    fontSize: 14,
    color: "var(--fg-muted)"
  }
}, subtitle)), /*#__PURE__*/React.createElement("div", {
  style: {
    display: "flex",
    gap: 8,
    flexShrink: 0
  }
}, actions));
const Field = ({
  label,
  children
}) => /*#__PURE__*/React.createElement("div", {
  style: {
    display: "flex",
    flexDirection: "column",
    flex: 1
  }
}, /*#__PURE__*/React.createElement("label", {
  style: {
    fontSize: 14,
    fontWeight: 600,
    color: "var(--slate-700)",
    marginBottom: 6
  }
}, label), children);
const inputStyle = {
  width: "100%",
  border: "1px solid var(--border)",
  borderRadius: 4,
  padding: "8px 12px",
  fontSize: 14,
  color: "var(--slate-900)",
  background: "#fff",
  fontFamily: "inherit",
  outline: "none"
};
const Input = props => {
  const [focus, setFocus] = React.useState(false);
  return /*#__PURE__*/React.createElement("input", _extends({}, props, {
    onFocus: () => setFocus(true),
    onBlur: () => setFocus(false),
    style: {
      ...inputStyle,
      boxShadow: focus ? "0 0 0 2px var(--brand-ring)" : "none",
      ...(props.style || {})
    }
  }));
};
const Select = ({
  children,
  ...props
}) => {
  const [focus, setFocus] = React.useState(false);
  return /*#__PURE__*/React.createElement("div", {
    style: {
      position: "relative"
    }
  }, /*#__PURE__*/React.createElement("select", _extends({}, props, {
    onFocus: () => setFocus(true),
    onBlur: () => setFocus(false),
    style: {
      ...inputStyle,
      appearance: "none",
      paddingRight: 32,
      boxShadow: focus ? "0 0 0 2px var(--brand-ring)" : "none"
    }
  }), children), /*#__PURE__*/React.createElement("span", {
    style: {
      position: "absolute",
      right: 10,
      top: "50%",
      transform: "translateY(-50%)",
      pointerEvents: "none",
      color: "var(--slate-400)"
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "chevron-down",
    size: 16
  })));
};
const FilterBar = () => /*#__PURE__*/React.createElement("div", {
  style: {
    background: "#fff",
    border: "1px solid var(--border)",
    borderRadius: 8,
    padding: 20,
    boxShadow: "var(--shadow-sm)",
    marginBottom: 16
  }
}, /*#__PURE__*/React.createElement("div", {
  style: {
    display: "flex",
    gap: 16,
    alignItems: "flex-end"
  }
}, /*#__PURE__*/React.createElement(Field, {
  label: "Freitext"
}, /*#__PURE__*/React.createElement(Input, {
  placeholder: "Bauvorhaben, Kunde\u2026"
})), /*#__PURE__*/React.createElement(Field, {
  label: "Kunde"
}, /*#__PURE__*/React.createElement(Input, {
  placeholder: "Kundenname"
})), /*#__PURE__*/React.createElement(Field, {
  label: "Status"
}, /*#__PURE__*/React.createElement(Select, {
  defaultValue: "Alle"
}, /*#__PURE__*/React.createElement("option", null, "Alle"), /*#__PURE__*/React.createElement("option", null, "Offen"), /*#__PURE__*/React.createElement("option", null, "Gebucht"), /*#__PURE__*/React.createElement("option", null, "Abgeschlossen"))), /*#__PURE__*/React.createElement("div", {
  style: {
    display: "flex",
    gap: 8,
    paddingBottom: 0
  }
}, /*#__PURE__*/React.createElement(Button, {
  variant: "default"
}, "Filtern"), /*#__PURE__*/React.createElement(Button, {
  variant: "outline"
}, "Reset"))), /*#__PURE__*/React.createElement("div", {
  style: {
    fontSize: 12,
    color: "var(--fg-muted)",
    marginTop: 10
  }
}, "F\xFCr Performance werden immer nur 12 Eintr\xE4ge auf einmal geladen."));
const EmptyState = () => /*#__PURE__*/React.createElement("div", {
  style: {
    background: "#fff",
    border: "1px dashed var(--border-strong)",
    borderRadius: 8,
    padding: "60px 20px",
    textAlign: "center"
  }
}, /*#__PURE__*/React.createElement("div", {
  style: {
    width: 48,
    height: 48,
    borderRadius: 12,
    background: "var(--rose-50)",
    color: "var(--brand)",
    display: "inline-flex",
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 12
  }
}, /*#__PURE__*/React.createElement(Icon, {
  name: "briefcase",
  size: 22
})), /*#__PURE__*/React.createElement("div", {
  style: {
    fontSize: 14,
    color: "var(--fg-muted)"
  }
}, "Keine Projekte gefunden."));
const Pagination = () => /*#__PURE__*/React.createElement("div", {
  style: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    marginTop: 16,
    padding: "0 4px"
  }
}, /*#__PURE__*/React.createElement("span", {
  style: {
    fontSize: 13,
    color: "var(--fg-muted)"
  }
}, "Keine Projekte gefunden."), /*#__PURE__*/React.createElement("div", {
  style: {
    display: "flex",
    gap: 8
  }
}, /*#__PURE__*/React.createElement(Button, {
  variant: "outline",
  size: "sm",
  icon: "chevron-left"
}, "zur\xFCck"), /*#__PURE__*/React.createElement(Button, {
  variant: "outline",
  size: "sm"
}, "Weiter ", /*#__PURE__*/React.createElement(Icon, {
  name: "chevron-right",
  size: 14,
  style: {
    marginLeft: 4
  }
}))));
Object.assign(window, {
  PageHeader,
  FilterBar,
  EmptyState,
  Pagination
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/desktop-erp/page.jsx", error: String((e && e.message) || e) }); }

// ui_kits/desktop-erp/ribbon.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
// ribbon.jsx — top tab ribbon + subgroup ribbon

const MAIN_TABS = [{
  id: "vorlagen",
  label: "Vorlagen & Stammdaten"
}, {
  id: "projekt",
  label: "Projektmanagement",
  active: true
}, {
  id: "zeit",
  label: "Zeiterfassung"
}, {
  id: "komm",
  label: "Kommunikation"
}, {
  id: "finanzen",
  label: "Finanzen & Controlling"
}];

// Subgroup entries for the active tab (Projektmanagement).
// "Dokumente" is the subgroup added in the latest revision.
const SUBGROUPS = [{
  group: "AUFTRÄGE",
  items: [{
    icon: "briefcase",
    label: "Projekte",
    active: true
  }, {
    icon: "file-check",
    label: "Anfragen"
  }]
}, {
  group: "DOKUMENTE",
  items: [{
    icon: "file-text",
    label: "Dokumente"
  }]
}, {
  group: "PLANUNG",
  items: [{
    icon: "calendar",
    label: "Kalender"
  }]
}, {
  group: "EINKAUF",
  items: [{
    icon: "shopping-cart",
    label: "Bestellungen"
  }, {
    icon: "list",
    label: "Bedarf"
  }]
}];
const BrandMark = () => /*#__PURE__*/React.createElement("div", {
  style: {
    display: "flex",
    alignItems: "center",
    gap: 10,
    paddingRight: 20,
    borderRight: "1px solid var(--border)",
    height: "100%"
  }
}, /*#__PURE__*/React.createElement("img", {
  src: "../../assets/app_logo.png",
  alt: "Logo",
  style: {
    width: 28,
    height: 28,
    borderRadius: 6,
    display: "block",
    objectFit: "cover"
  }
}), /*#__PURE__*/React.createElement("div", {
  style: {
    display: "flex",
    flexDirection: "column",
    lineHeight: 1.1
  }
}, /*#__PURE__*/React.createElement("span", {
  style: {
    fontSize: 9,
    fontWeight: 700,
    color: "var(--slate-400)",
    letterSpacing: "0.08em",
    textTransform: "uppercase"
  }
}, "Firma"), /*#__PURE__*/React.createElement("span", {
  style: {
    fontSize: 13,
    fontWeight: 700,
    color: "var(--slate-900)"
  }
}, "Musterfirma")));
const MainTab = ({
  label,
  active
}) => {
  const [h, setH] = React.useState(false);
  return /*#__PURE__*/React.createElement("button", {
    onMouseEnter: () => setH(true),
    onMouseLeave: () => setH(false),
    style: {
      height: "100%",
      padding: "0 18px",
      border: 0,
      background: "transparent",
      fontSize: 14,
      fontWeight: active ? 600 : 500,
      color: active ? "var(--brand)" : h ? "var(--slate-900)" : "var(--slate-600)",
      borderBottom: active ? "2px solid var(--brand)" : "2px solid transparent",
      position: "relative",
      transition: "color var(--dur)"
    }
  }, label);
};
const RibbonTile = ({
  icon,
  label,
  active
}) => {
  const [h, setH] = React.useState(false);
  return /*#__PURE__*/React.createElement("button", {
    onMouseEnter: () => setH(true),
    onMouseLeave: () => setH(false),
    style: {
      display: "flex",
      flexDirection: "column",
      alignItems: "center",
      gap: 6,
      padding: "8px 14px",
      border: 0,
      background: h && !active ? "var(--slate-100)" : "transparent",
      borderRadius: 8,
      minWidth: 68,
      transition: "all var(--dur)"
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      width: 36,
      height: 36,
      borderRadius: 10,
      background: active ? "var(--rose-100)" : "var(--slate-100)",
      color: active ? "var(--brand)" : "var(--slate-500)",
      display: "flex",
      alignItems: "center",
      justifyContent: "center"
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: icon,
    size: 20
  })), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 11,
      fontWeight: active ? 600 : 500,
      color: active ? "var(--slate-900)" : "var(--slate-700)",
      lineHeight: 1
    }
  }, label));
};
const SubgroupColumn = ({
  group,
  items
}) => /*#__PURE__*/React.createElement("div", {
  style: {
    display: "flex",
    flexDirection: "column",
    alignItems: "flex-start",
    paddingRight: 16,
    borderRight: "1px solid var(--border)",
    marginRight: 16
  }
}, /*#__PURE__*/React.createElement("span", {
  style: {
    fontSize: 9,
    fontWeight: 700,
    color: "var(--slate-400)",
    letterSpacing: "0.08em",
    textTransform: "uppercase",
    padding: "0 10px",
    marginBottom: 2
  }
}, group), /*#__PURE__*/React.createElement("div", {
  style: {
    display: "flex",
    gap: 4
  }
}, items.map((it, i) => /*#__PURE__*/React.createElement(RibbonTile, _extends({
  key: i
}, it)))));
const UserPill = () => /*#__PURE__*/React.createElement("div", {
  style: {
    display: "flex",
    alignItems: "center",
    gap: 8,
    padding: "4px 10px 4px 4px",
    border: "1px solid var(--border)",
    borderRadius: 999,
    background: "#fff"
  }
}, /*#__PURE__*/React.createElement("span", {
  style: {
    width: 28,
    height: 28,
    borderRadius: 999,
    background: "var(--slate-100)",
    color: "var(--slate-500)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center"
  }
}, /*#__PURE__*/React.createElement(Icon, {
  name: "user",
  size: 16
})), /*#__PURE__*/React.createElement("div", {
  style: {
    display: "flex",
    flexDirection: "column",
    lineHeight: 1.1
  }
}, /*#__PURE__*/React.createElement("span", {
  style: {
    fontSize: 12,
    fontWeight: 600,
    color: "var(--slate-900)"
  }
}, "Lade\u2026"), /*#__PURE__*/React.createElement("span", {
  style: {
    fontSize: 10,
    color: "var(--fg-muted)"
  }
}, "Angemeldet")), /*#__PURE__*/React.createElement(Icon, {
  name: "chevron-down",
  size: 14,
  color: "var(--slate-400)",
  style: {
    marginLeft: 2
  }
}));
const Ribbon = () => /*#__PURE__*/React.createElement("div", {
  style: {
    background: "#fff",
    borderBottom: "1px solid var(--border)"
  }
}, /*#__PURE__*/React.createElement("div", {
  style: {
    height: 56,
    display: "flex",
    alignItems: "center",
    padding: "0 20px",
    gap: 12
  }
}, /*#__PURE__*/React.createElement(BrandMark, null), /*#__PURE__*/React.createElement("div", {
  style: {
    display: "flex",
    alignItems: "center",
    height: "100%",
    flex: 1
  }
}, MAIN_TABS.map(t => /*#__PURE__*/React.createElement(MainTab, _extends({
  key: t.id
}, t)))), /*#__PURE__*/React.createElement("button", {
  style: {
    border: 0,
    background: "transparent",
    padding: 8,
    borderRadius: 8,
    color: "var(--slate-500)"
  },
  "aria-label": "Benachrichtigungen"
}, /*#__PURE__*/React.createElement(Icon, {
  name: "bell",
  size: 18
})), /*#__PURE__*/React.createElement(UserPill, null), /*#__PURE__*/React.createElement("button", {
  style: {
    border: 0,
    background: "transparent",
    padding: 6,
    color: "var(--slate-400)"
  },
  "aria-label": "Men\xFCleiste einklappen",
  "aria-expanded": "true"
}, /*#__PURE__*/React.createElement(Icon, {
  name: "chevron-up",
  size: 16
}))), /*#__PURE__*/React.createElement("div", {
  style: {
    padding: "8px 20px 12px",
    display: "flex",
    alignItems: "flex-end",
    background: "#fff",
    borderTop: "1px solid var(--border-soft)"
  }
}, SUBGROUPS.map((sg, i) => /*#__PURE__*/React.createElement(SubgroupColumn, _extends({
  key: i
}, sg)))));
Object.assign(window, {
  Ribbon
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/desktop-erp/ribbon.jsx", error: String((e && e.message) || e) }); }

// ui_kits/mobile-zeiterfassung/ios-frame.jsx
try { (() => {
// iOS.jsx — Simplified iOS 26 (Liquid Glass) device frame
// Based on the iOS 26 UI Kit + Figma status bar spec. No assets, no deps.
// Exports: IOSDevice, IOSStatusBar, IOSNavBar, IOSGlassPill, IOSList, IOSListRow, IOSKeyboard

// ─────────────────────────────────────────────────────────────
// Status bar
// ─────────────────────────────────────────────────────────────
function IOSStatusBar({
  dark = false,
  time = '9:41'
}) {
  const c = dark ? '#fff' : '#000';
  return /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 154,
      alignItems: 'center',
      justifyContent: 'center',
      padding: '21px 24px 19px',
      boxSizing: 'border-box',
      position: 'relative',
      zIndex: 20,
      width: '100%'
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      height: 22,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      paddingTop: 1.5
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      fontFamily: '-apple-system, "SF Pro", system-ui',
      fontWeight: 590,
      fontSize: 17,
      lineHeight: '22px',
      color: c
    }
  }, time)), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      height: 22,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      gap: 7,
      paddingTop: 1,
      paddingRight: 1
    }
  }, /*#__PURE__*/React.createElement("svg", {
    width: "19",
    height: "12",
    viewBox: "0 0 19 12"
  }, /*#__PURE__*/React.createElement("rect", {
    x: "0",
    y: "7.5",
    width: "3.2",
    height: "4.5",
    rx: "0.7",
    fill: c
  }), /*#__PURE__*/React.createElement("rect", {
    x: "4.8",
    y: "5",
    width: "3.2",
    height: "7",
    rx: "0.7",
    fill: c
  }), /*#__PURE__*/React.createElement("rect", {
    x: "9.6",
    y: "2.5",
    width: "3.2",
    height: "9.5",
    rx: "0.7",
    fill: c
  }), /*#__PURE__*/React.createElement("rect", {
    x: "14.4",
    y: "0",
    width: "3.2",
    height: "12",
    rx: "0.7",
    fill: c
  })), /*#__PURE__*/React.createElement("svg", {
    width: "17",
    height: "12",
    viewBox: "0 0 17 12"
  }, /*#__PURE__*/React.createElement("path", {
    d: "M8.5 3.2C10.8 3.2 12.9 4.1 14.4 5.6L15.5 4.5C13.7 2.7 11.2 1.5 8.5 1.5C5.8 1.5 3.3 2.7 1.5 4.5L2.6 5.6C4.1 4.1 6.2 3.2 8.5 3.2Z",
    fill: c
  }), /*#__PURE__*/React.createElement("path", {
    d: "M8.5 6.8C9.9 6.8 11.1 7.3 12 8.2L13.1 7.1C11.8 5.9 10.2 5.1 8.5 5.1C6.8 5.1 5.2 5.9 3.9 7.1L5 8.2C5.9 7.3 7.1 6.8 8.5 6.8Z",
    fill: c
  }), /*#__PURE__*/React.createElement("circle", {
    cx: "8.5",
    cy: "10.5",
    r: "1.5",
    fill: c
  })), /*#__PURE__*/React.createElement("svg", {
    width: "27",
    height: "13",
    viewBox: "0 0 27 13"
  }, /*#__PURE__*/React.createElement("rect", {
    x: "0.5",
    y: "0.5",
    width: "23",
    height: "12",
    rx: "3.5",
    stroke: c,
    strokeOpacity: "0.35",
    fill: "none"
  }), /*#__PURE__*/React.createElement("rect", {
    x: "2",
    y: "2",
    width: "20",
    height: "9",
    rx: "2",
    fill: c
  }), /*#__PURE__*/React.createElement("path", {
    d: "M25 4.5V8.5C25.8 8.2 26.5 7.2 26.5 6.5C26.5 5.8 25.8 4.8 25 4.5Z",
    fill: c,
    fillOpacity: "0.4"
  }))));
}

// ─────────────────────────────────────────────────────────────
// Liquid glass pill — blur + tint + shine
// ─────────────────────────────────────────────────────────────
function IOSGlassPill({
  children,
  dark = false,
  style = {}
}) {
  return /*#__PURE__*/React.createElement("div", {
    style: {
      height: 44,
      minWidth: 44,
      borderRadius: 9999,
      position: 'relative',
      overflow: 'hidden',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      boxShadow: dark ? '0 2px 6px rgba(0,0,0,0.35), 0 6px 16px rgba(0,0,0,0.2)' : '0 1px 3px rgba(0,0,0,0.07), 0 3px 10px rgba(0,0,0,0.06)',
      ...style
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      position: 'absolute',
      inset: 0,
      borderRadius: 9999,
      backdropFilter: 'blur(12px) saturate(180%)',
      WebkitBackdropFilter: 'blur(12px) saturate(180%)',
      background: dark ? 'rgba(120,120,128,0.28)' : 'rgba(255,255,255,0.5)'
    }
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      position: 'absolute',
      inset: 0,
      borderRadius: 9999,
      boxShadow: dark ? 'inset 1.5px 1.5px 1px rgba(255,255,255,0.15), inset -1px -1px 1px rgba(255,255,255,0.08)' : 'inset 1.5px 1.5px 1px rgba(255,255,255,0.7), inset -1px -1px 1px rgba(255,255,255,0.4)',
      border: dark ? '0.5px solid rgba(255,255,255,0.15)' : '0.5px solid rgba(0,0,0,0.06)'
    }
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      position: 'relative',
      zIndex: 1,
      display: 'flex',
      alignItems: 'center',
      padding: '0 4px'
    }
  }, children));
}

// ─────────────────────────────────────────────────────────────
// Navigation bar — glass pills + large title
// ─────────────────────────────────────────────────────────────
function IOSNavBar({
  title = 'Title',
  dark = false,
  trailingIcon = true
}) {
  const muted = dark ? 'rgba(255,255,255,0.6)' : '#404040';
  const text = dark ? '#fff' : '#000';
  const pillIcon = content => /*#__PURE__*/React.createElement(IOSGlassPill, {
    dark: dark
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      width: 36,
      height: 36,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center'
    }
  }, content));
  return /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 10,
      paddingTop: 62,
      paddingBottom: 10,
      position: 'relative',
      zIndex: 5
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      padding: '0 16px'
    }
  }, pillIcon(/*#__PURE__*/React.createElement("svg", {
    width: "12",
    height: "20",
    viewBox: "0 0 12 20",
    fill: "none",
    style: {
      marginLeft: -1
    }
  }, /*#__PURE__*/React.createElement("path", {
    d: "M10 2L2 10l8 8",
    stroke: muted,
    strokeWidth: "2.5",
    strokeLinecap: "round",
    strokeLinejoin: "round"
  }))), trailingIcon && pillIcon(/*#__PURE__*/React.createElement("svg", {
    width: "22",
    height: "6",
    viewBox: "0 0 22 6"
  }, /*#__PURE__*/React.createElement("circle", {
    cx: "3",
    cy: "3",
    r: "2.5",
    fill: muted
  }), /*#__PURE__*/React.createElement("circle", {
    cx: "11",
    cy: "3",
    r: "2.5",
    fill: muted
  }), /*#__PURE__*/React.createElement("circle", {
    cx: "19",
    cy: "3",
    r: "2.5",
    fill: muted
  })))), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '0 16px',
      fontFamily: '-apple-system, system-ui',
      fontSize: 34,
      fontWeight: 700,
      lineHeight: '41px',
      color: text,
      letterSpacing: 0.4
    }
  }, title));
}

// ─────────────────────────────────────────────────────────────
// Grouped list (inset card, r:26) + row (52px)
// ─────────────────────────────────────────────────────────────
function IOSListRow({
  title,
  detail,
  icon,
  chevron = true,
  isLast = false,
  dark = false
}) {
  const text = dark ? '#fff' : '#000';
  const sec = dark ? 'rgba(235,235,245,0.6)' : 'rgba(60,60,67,0.6)';
  const ter = dark ? 'rgba(235,235,245,0.3)' : 'rgba(60,60,67,0.3)';
  const sep = dark ? 'rgba(84,84,88,0.65)' : 'rgba(60,60,67,0.12)';
  return /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      minHeight: 52,
      padding: '0 16px',
      position: 'relative',
      fontFamily: '-apple-system, system-ui',
      fontSize: 17,
      letterSpacing: -0.43
    }
  }, icon && /*#__PURE__*/React.createElement("div", {
    style: {
      width: 30,
      height: 30,
      borderRadius: 7,
      background: icon,
      marginRight: 12,
      flexShrink: 0
    }
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      color: text
    }
  }, title), detail && /*#__PURE__*/React.createElement("span", {
    style: {
      color: sec,
      marginRight: 6
    }
  }, detail), chevron && /*#__PURE__*/React.createElement("svg", {
    width: "8",
    height: "14",
    viewBox: "0 0 8 14",
    style: {
      flexShrink: 0
    }
  }, /*#__PURE__*/React.createElement("path", {
    d: "M1 1l6 6-6 6",
    stroke: ter,
    strokeWidth: "2",
    fill: "none",
    strokeLinecap: "round",
    strokeLinejoin: "round"
  })), !isLast && /*#__PURE__*/React.createElement("div", {
    style: {
      position: 'absolute',
      bottom: 0,
      right: 0,
      left: icon ? 58 : 16,
      height: 0.5,
      background: sep
    }
  }));
}
function IOSList({
  header,
  children,
  dark = false
}) {
  const hc = dark ? 'rgba(235,235,245,0.6)' : 'rgba(60,60,67,0.6)';
  const bg = dark ? '#1C1C1E' : '#fff';
  return /*#__PURE__*/React.createElement("div", null, header && /*#__PURE__*/React.createElement("div", {
    style: {
      fontFamily: '-apple-system, system-ui',
      fontSize: 13,
      color: hc,
      textTransform: 'uppercase',
      padding: '8px 36px 6px',
      letterSpacing: -0.08
    }
  }, header), /*#__PURE__*/React.createElement("div", {
    style: {
      background: bg,
      borderRadius: 26,
      margin: '0 16px',
      overflow: 'hidden'
    }
  }, children));
}

// ─────────────────────────────────────────────────────────────
// Device frame
// ─────────────────────────────────────────────────────────────
function IOSDevice({
  children,
  width = 402,
  height = 874,
  dark = false,
  title,
  keyboard = false
}) {
  return /*#__PURE__*/React.createElement("div", {
    style: {
      width,
      height,
      borderRadius: 48,
      overflow: 'hidden',
      position: 'relative',
      background: dark ? '#000' : '#F2F2F7',
      boxShadow: '0 40px 80px rgba(0,0,0,0.18), 0 0 0 1px rgba(0,0,0,0.12)',
      fontFamily: '-apple-system, system-ui, sans-serif',
      WebkitFontSmoothing: 'antialiased'
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      position: 'absolute',
      top: 11,
      left: '50%',
      transform: 'translateX(-50%)',
      width: 126,
      height: 37,
      borderRadius: 24,
      background: '#000',
      zIndex: 50
    }
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      position: 'absolute',
      top: 0,
      left: 0,
      right: 0,
      zIndex: 10
    }
  }, /*#__PURE__*/React.createElement(IOSStatusBar, {
    dark: dark
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      height: '100%',
      display: 'flex',
      flexDirection: 'column'
    }
  }, title !== undefined && /*#__PURE__*/React.createElement(IOSNavBar, {
    title: title,
    dark: dark
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      overflow: 'auto'
    }
  }, children), keyboard && /*#__PURE__*/React.createElement(IOSKeyboard, {
    dark: dark
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      position: 'absolute',
      bottom: 0,
      left: 0,
      right: 0,
      zIndex: 60,
      height: 34,
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'flex-end',
      paddingBottom: 8,
      pointerEvents: 'none'
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      width: 139,
      height: 5,
      borderRadius: 100,
      background: dark ? 'rgba(255,255,255,0.7)' : 'rgba(0,0,0,0.25)'
    }
  })));
}

// ─────────────────────────────────────────────────────────────
// Keyboard — iOS 26 liquid glass
// ─────────────────────────────────────────────────────────────
function IOSKeyboard({
  dark = false
}) {
  const glyph = dark ? 'rgba(255,255,255,0.7)' : '#595959';
  const sugg = dark ? 'rgba(255,255,255,0.6)' : '#333';
  const keyBg = dark ? 'rgba(255,255,255,0.22)' : 'rgba(255,255,255,0.85)';

  // special-key icons
  const icons = {
    shift: /*#__PURE__*/React.createElement("svg", {
      width: "19",
      height: "17",
      viewBox: "0 0 19 17"
    }, /*#__PURE__*/React.createElement("path", {
      d: "M9.5 1L1 9.5h4.5V16h8V9.5H18L9.5 1z",
      fill: glyph
    })),
    del: /*#__PURE__*/React.createElement("svg", {
      width: "23",
      height: "17",
      viewBox: "0 0 23 17"
    }, /*#__PURE__*/React.createElement("path", {
      d: "M7 1h13a2 2 0 012 2v11a2 2 0 01-2 2H7l-6-7.5L7 1z",
      fill: "none",
      stroke: glyph,
      strokeWidth: "1.6",
      strokeLinejoin: "round"
    }), /*#__PURE__*/React.createElement("path", {
      d: "M10 5l7 7M17 5l-7 7",
      stroke: glyph,
      strokeWidth: "1.6",
      strokeLinecap: "round"
    })),
    ret: /*#__PURE__*/React.createElement("svg", {
      width: "20",
      height: "14",
      viewBox: "0 0 20 14"
    }, /*#__PURE__*/React.createElement("path", {
      d: "M18 1v6H4m0 0l4-4M4 7l4 4",
      fill: "none",
      stroke: "#fff",
      strokeWidth: "1.8",
      strokeLinecap: "round",
      strokeLinejoin: "round"
    }))
  };
  const key = (content, {
    w,
    flex,
    ret,
    fs = 25,
    k
  } = {}) => /*#__PURE__*/React.createElement("div", {
    key: k,
    style: {
      height: 42,
      borderRadius: 8.5,
      flex: flex ? 1 : undefined,
      width: w,
      minWidth: 0,
      background: ret ? '#08f' : keyBg,
      boxShadow: '0 1px 0 rgba(0,0,0,0.075)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      fontFamily: '-apple-system, "SF Compact", system-ui',
      fontSize: fs,
      fontWeight: 458,
      color: ret ? '#fff' : glyph
    }
  }, content);
  const row = (keys, pad = 0) => /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 6.5,
      justifyContent: 'center',
      padding: `0 ${pad}px`
    }
  }, keys.map(l => key(l, {
    flex: true,
    k: l
  })));
  return /*#__PURE__*/React.createElement("div", {
    style: {
      position: 'relative',
      zIndex: 15,
      borderRadius: 27,
      overflow: 'hidden',
      padding: '11px 0 2px',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      boxShadow: dark ? '0 -2px 20px rgba(0,0,0,0.09)' : '0 -1px 6px rgba(0,0,0,0.018), 0 -3px 20px rgba(0,0,0,0.012)'
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      position: 'absolute',
      inset: 0,
      borderRadius: 27,
      backdropFilter: 'blur(12px) saturate(180%)',
      WebkitBackdropFilter: 'blur(12px) saturate(180%)',
      background: dark ? 'rgba(120,120,128,0.14)' : 'rgba(255,255,255,0.25)'
    }
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      position: 'absolute',
      inset: 0,
      borderRadius: 27,
      boxShadow: dark ? 'inset 1.5px 1.5px 1px rgba(255,255,255,0.15)' : 'inset 1.5px 1.5px 1px rgba(255,255,255,0.7), inset -1px -1px 1px rgba(255,255,255,0.4)',
      border: dark ? '0.5px solid rgba(255,255,255,0.15)' : '0.5px solid rgba(0,0,0,0.06)',
      pointerEvents: 'none'
    }
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 20,
      alignItems: 'center',
      padding: '8px 22px 13px',
      width: '100%',
      boxSizing: 'border-box',
      position: 'relative'
    }
  }, ['"The"', 'the', 'to'].map((w, i) => /*#__PURE__*/React.createElement(React.Fragment, {
    key: i
  }, i > 0 && /*#__PURE__*/React.createElement("div", {
    style: {
      width: 1,
      height: 25,
      background: '#ccc',
      opacity: 0.3
    }
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      textAlign: 'center',
      fontFamily: '-apple-system, system-ui',
      fontSize: 17,
      color: sugg,
      letterSpacing: -0.43,
      lineHeight: '22px'
    }
  }, w)))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 13,
      padding: '0 6.5px',
      width: '100%',
      boxSizing: 'border-box',
      position: 'relative'
    }
  }, row(['q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p']), row(['a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'], 20), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 14.25,
      alignItems: 'center'
    }
  }, key(icons.shift, {
    w: 45,
    k: 'shift'
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 6.5,
      flex: 1
    }
  }, ['z', 'x', 'c', 'v', 'b', 'n', 'm'].map(l => key(l, {
    flex: true,
    k: l
  }))), key(icons.del, {
    w: 45,
    k: 'del'
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 6,
      alignItems: 'center'
    }
  }, key('ABC', {
    w: 92.25,
    fs: 18,
    k: 'abc'
  }), key('', {
    flex: true,
    k: 'space'
  }), key(icons.ret, {
    w: 92.25,
    ret: true,
    k: 'ret'
  }))), /*#__PURE__*/React.createElement("div", {
    style: {
      height: 56,
      width: '100%',
      position: 'relative'
    }
  }));
}
Object.assign(window, {
  IOSDevice,
  IOSStatusBar,
  IOSNavBar,
  IOSGlassPill,
  IOSList,
  IOSListRow,
  IOSKeyboard
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/mobile-zeiterfassung/ios-frame.jsx", error: String((e && e.message) || e) }); }

// ui_kits/mobile-zeiterfassung/mobile.jsx
try { (() => {
// mobile.jsx — react-zeiterfassung dashboard

const Ic = ({
  name,
  size = 22,
  color,
  stroke = 2
}) => {
  const ref = React.useRef(null);
  React.useEffect(() => {
    if (!ref.current) return;
    ref.current.innerHTML = "";
    const el = document.createElement("i");
    el.setAttribute("data-lucide", name);
    ref.current.appendChild(el);
    window.lucide && window.lucide.createIcons({
      attrs: {
        width: size,
        height: size,
        "stroke-width": stroke
      }
    });
  }, [name, size, stroke]);
  return /*#__PURE__*/React.createElement("span", {
    ref: ref,
    style: {
      display: "inline-flex",
      color,
      lineHeight: 0
    }
  });
};
const Header = () => /*#__PURE__*/React.createElement("div", {
  style: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "flex-start",
    padding: "12px 20px 14px"
  }
}, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
  style: {
    fontSize: 13,
    color: "var(--slate-500)",
    fontWeight: 500
  }
}, "Dienstag, 10.3.2026"), /*#__PURE__*/React.createElement("div", {
  style: {
    fontSize: 22,
    fontWeight: 700,
    color: "var(--slate-900)",
    marginTop: 2,
    letterSpacing: "-0.01em"
  }
}, "Hallo, Max Mustermann!")), /*#__PURE__*/React.createElement("div", {
  style: {
    display: "inline-flex",
    alignItems: "center",
    gap: 6,
    padding: "6px 12px",
    background: "#dcfce7",
    color: "#166534",
    borderRadius: 999,
    fontSize: 13,
    fontWeight: 600,
    flexShrink: 0,
    marginTop: 4
  }
}, /*#__PURE__*/React.createElement(Ic, {
  name: "wifi",
  size: 14,
  stroke: 2.5
}), "Online"));
const Hero = () => {
  const [press, setPress] = React.useState(false);
  return /*#__PURE__*/React.createElement("div", {
    style: {
      padding: "0 20px"
    }
  }, /*#__PURE__*/React.createElement("button", {
    onMouseDown: () => setPress(true),
    onMouseUp: () => setPress(false),
    onMouseLeave: () => setPress(false),
    onTouchStart: () => setPress(true),
    onTouchEnd: () => setPress(false),
    style: {
      width: "100%",
      background: "var(--brand)",
      color: "#fff",
      border: 0,
      borderRadius: 16,
      padding: 16,
      display: "flex",
      alignItems: "center",
      gap: 14,
      boxShadow: "var(--shadow-rose)",
      transform: press ? "scale(0.97)" : "scale(1)",
      transition: "transform 120ms var(--ease-out)"
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      width: 48,
      height: 48,
      borderRadius: 12,
      background: "rgba(255,255,255,0.2)",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      flexShrink: 0
    }
  }, /*#__PURE__*/React.createElement("svg", {
    width: "22",
    height: "22",
    viewBox: "0 0 24 24",
    fill: "#fff"
  }, /*#__PURE__*/React.createElement("path", {
    d: "M8 5v14l11-7z"
  }))), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      textAlign: "left"
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontWeight: 700,
      fontSize: 17
    }
  }, "Zeit erfassen"), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 13,
      opacity: 0.9,
      marginTop: 1
    }
  }, "Neue Buchung starten")), /*#__PURE__*/React.createElement(Ic, {
    name: "chevron-right",
    size: 20
  })));
};
const TileCard = ({
  icon,
  label,
  tint
}) => {
  const tints = {
    rose: {
      bg: "var(--rose-100)",
      fg: "var(--brand)"
    },
    amber: {
      bg: "#fef3c7",
      fg: "#b45309"
    },
    indigo: {
      bg: "#e0e7ff",
      fg: "#4338ca"
    },
    green: {
      bg: "#dcfce7",
      fg: "#166534"
    }
  };
  const t = tints[tint] || tints.rose;
  return /*#__PURE__*/React.createElement("button", {
    style: {
      flex: 1,
      background: "#fff",
      border: "1px solid var(--border)",
      borderRadius: 14,
      padding: "16px 14px",
      display: "flex",
      flexDirection: "column",
      alignItems: "flex-start",
      gap: 10,
      boxShadow: "var(--shadow-sm)"
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      width: 40,
      height: 40,
      borderRadius: 10,
      background: t.bg,
      color: t.fg,
      display: "inline-flex",
      alignItems: "center",
      justifyContent: "center"
    }
  }, /*#__PURE__*/React.createElement(Ic, {
    name: icon,
    size: 20
  })), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 15,
      fontWeight: 600,
      color: "var(--slate-900)"
    }
  }, label));
};
const ActionRow = ({
  icon,
  title,
  subtitle,
  tint
}) => {
  const tints = {
    indigo: {
      bg: "#e0e7ff",
      fg: "#4338ca"
    },
    rose: {
      bg: "var(--rose-100)",
      fg: "var(--brand)"
    },
    green: {
      bg: "#dcfce7",
      fg: "#166534"
    }
  };
  const t = tints[tint] || tints.rose;
  return /*#__PURE__*/React.createElement("button", {
    style: {
      width: "100%",
      background: "#fff",
      border: "1px solid var(--border)",
      borderRadius: 14,
      padding: "14px 14px",
      display: "flex",
      alignItems: "center",
      gap: 14,
      boxShadow: "var(--shadow-sm)",
      textAlign: "left"
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      width: 40,
      height: 40,
      borderRadius: 10,
      background: t.bg,
      color: t.fg,
      display: "inline-flex",
      alignItems: "center",
      justifyContent: "center",
      flexShrink: 0
    }
  }, /*#__PURE__*/React.createElement(Ic, {
    name: icon,
    size: 20
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 15,
      fontWeight: 600,
      color: "var(--slate-900)"
    }
  }, title), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 12,
      color: "var(--fg-muted)",
      marginTop: 1
    }
  }, subtitle)));
};
const TodayTile = () => /*#__PURE__*/React.createElement("button", {
  style: {
    width: "100%",
    background: "#fff",
    border: "1px solid var(--border)",
    borderRadius: 14,
    padding: "14px 16px",
    display: "flex",
    alignItems: "center",
    boxShadow: "var(--shadow-sm)",
    textAlign: "left"
  }
}, /*#__PURE__*/React.createElement("div", {
  style: {
    flex: 1
  }
}, /*#__PURE__*/React.createElement("div", {
  style: {
    display: "flex",
    alignItems: "center",
    gap: 6,
    fontSize: 13,
    color: "var(--fg-muted)",
    fontWeight: 500
  }
}, /*#__PURE__*/React.createElement(Ic, {
  name: "clock",
  size: 14
}), " Heute gearbeitet"), /*#__PURE__*/React.createElement("div", {
  style: {
    fontSize: 28,
    fontWeight: 700,
    color: "var(--slate-900)",
    marginTop: 4,
    fontVariantNumeric: "tabular-nums",
    letterSpacing: "-0.01em"
  }
}, "0h 00min")), /*#__PURE__*/React.createElement(Ic, {
  name: "chevron-right",
  size: 18,
  color: "var(--slate-400)"
}));
const MobileApp = () => /*#__PURE__*/React.createElement("div", {
  style: {
    minHeight: "100%",
    background: "var(--slate-50)",
    paddingTop: 64,
    paddingBottom: 30
  }
}, /*#__PURE__*/React.createElement(Header, null), /*#__PURE__*/React.createElement(Hero, null), /*#__PURE__*/React.createElement("div", {
  style: {
    display: "grid",
    gridTemplateColumns: "1fr 1fr",
    gap: 12,
    padding: "14px 20px 0"
  }
}, /*#__PURE__*/React.createElement(TileCard, {
  icon: "folder-open",
  label: "Projekte",
  tint: "rose"
}), /*#__PURE__*/React.createElement(TileCard, {
  icon: "folder-open",
  label: "Angebote",
  tint: "amber"
}), /*#__PURE__*/React.createElement(TileCard, {
  icon: "users",
  label: "Kunden",
  tint: "indigo"
}), /*#__PURE__*/React.createElement(TileCard, {
  icon: "users",
  label: "Lieferanten",
  tint: "green"
})), /*#__PURE__*/React.createElement("div", {
  style: {
    display: "flex",
    flexDirection: "column",
    gap: 10,
    padding: "14px 20px 0"
  }
}, /*#__PURE__*/React.createElement(ActionRow, {
  icon: "calendar-days",
  title: "Kalender",
  subtitle: "Termine & Einladungen",
  tint: "indigo"
}), /*#__PURE__*/React.createElement(ActionRow, {
  icon: "plane",
  title: "Abwesenheit beantragen",
  subtitle: "Urlaub, Krankheit, Fortbildung",
  tint: "rose"
}), /*#__PURE__*/React.createElement(ActionRow, {
  icon: "clock",
  title: "Saldenauswertung",
  subtitle: "Urlaub & Stunden \xFCbersicht",
  tint: "green"
})), /*#__PURE__*/React.createElement("div", {
  style: {
    padding: "14px 20px 0"
  }
}, /*#__PURE__*/React.createElement(TodayTile, null)));
ReactDOM.createRoot(document.getElementById("root")).render(/*#__PURE__*/React.createElement(IOSDevice, {
  width: 402,
  height: 820
}, /*#__PURE__*/React.createElement(MobileApp, null)));
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/mobile-zeiterfassung/mobile.jsx", error: String((e && e.message) || e) }); }

})();
