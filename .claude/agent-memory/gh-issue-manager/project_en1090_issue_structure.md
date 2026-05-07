---
name: Issue-Struktur EN-1090
description: Übersicht der EN-1090-Issues nach Phasen und Konventionen für Issue-Kommentare
type: project
---

**EN-1090-Issues sind in Phasen gegliedert (Stand 2026-05-07):**

- Phase 1 (Compliance-Engine): #28, #29, #30, #31 — Zeugnis-Check, Wareneingang, WPK-Checkliste, ZfP
- Phase 2 (Protokolle): #32, #33, #34, #35, #36 — Korrosionsschutz, Montage, Schweissüberwachung, Nichtkonformität, CE
- Phase 3 (Mobile): #37, #38, #39, #40 — Mobile E-Check, Schweisser-Warnung, Montageprotokolle, Wareneingangskontrolle
- Phase 4 (Automatisierung): #41, #42, #43, #44, #45, #46 — Messmittel, Hilfsstoffe, Zeitbuchungs-Hook, KI-Extraktion, Archivierung, Dashboard
- Querschnitt: #50 (WPS Feedback-Loop), #51 (En1090AnforderungenService — blockiert #29, #30, #31, #40)
- Schnittbilder: #52 (Fixzuschnitt), #53 (Schnittbild-PDF)

**Infrastruktur-Commits ohne dediziertes Issue** (z.B. Flyway-Fixes, Branch-Hygiene):
Als Heimat eignet sich Issue #51 (Querschnitts-Issue), da es als technische Basis für alle EN-1090-Features dient.

**Why:** Kein separates "Infrastruktur"-Issue existiert. #51 ist das nächstliegend thematische Dach für technische Voraussetzungen.

**How to apply:** Bei technischen EN-1090-Branch-Commits ohne direkte Issue-Referenz → Kommentar an #51 setzen.
