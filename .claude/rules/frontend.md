---
paths:
  - "react-pc-frontend/**"
  - "react-zeiterfassung/**"
---

### 🎨 Design-Skill-Pflicht bei Frontend-Arbeit

Frontend-Edits brauchen **zwei** Freigaben: das gelesene `FRONTEND_UI.md` **und** einen aufgerufenen Design-Skill. Der Hook blockt sonst mit `DESIGN-SKILL-GUARD`:

- `handwerkerprogramm-design` — **Pflicht-Standard.** Unser eigenes, projektspezifisches Design-System: Farben, Typografie, Icon-Vokabular, Wording/Terminologie, fertige UI-Kits (Desktop + Mobile) und echte Screenshots. Immer zuerst dieser.
- `ui-ux-pro-max` — generisch (Styles, Farben, Typografie, UX-Regeln), nur ergänzend, wenn `handwerkerprogramm-design` eine Frage offenlässt.
- Nur falls auf dem Rechner als Plugin installiert (nicht überall vorhanden): `frontend-design:frontend-design` (visuelle Ausrichtung neuer UI), `ui-ux-pro-max:design-system` (Design-Tokens, Komponenten-Specs), `ui-ux-pro-max:design` (Logos, Banner, Icons).

**MCP-Pflicht:** Für Komponenten den MCP-Server `shadcn` (fertige shadcn-Bausteine) nutzen, statt von Hand nachzubauen; ergänzend `magic` (21st.dev, animierte Tailwind-Layouts), sofern er verbunden ist – er braucht einen eigenen API-Key. Details in `FRONTEND_UI.md`.
