---
name: IDS-Connect Punchout — Issue-Zuordnung
description: Kein eigenes Issue für IDS-Punchout; Commits werden an #54 (Bedarf Lieferantenauswahl) kommentiert
type: project
---

**IDS-Connect Punchout-Flow: Kein Sammel-Issue, aber Würth-Rückfrage-Issue #57 (Stand 2026-05-08).**

Commits mit IDS/Punchout-Bezug werden an Issue **#54** (`[Bedarf] Phase 2.2: Lieferantenauswahl-Modal + Preisanfrage`) kommentiert, weil der IDS-Flow den Lieferantenauswahl-Button auf der Bedarfsseite umsetzt.

Sprint-Struktur:
- Sprint 1 (Commit 7f0454b): IDS-Connect-Konfiguration pro Lieferant (Konfig-Pflege im Backend/Frontend)
- Sprint 2+3 (Commit 0ad580c): Punchout-Flow + `IdsLieferantenAuswahlModal` + `IdsPunchoutController/Service` + HMAC-Token + Cart-Return als ENTWURF-Bestellung
- Hotfix (Commit 53b207e): Würth `WUERTH_LEGACY`-Profil — `multipart/form-data` + lowercase-Feldnamen (`kndnr`, `name_kunde`, `pw_kunde`, `hookurl`)

**Issue #57** (OFFEN, blocked-external): Würth verlangt zusätzlich ein `action`-Feld — Wert noch unbekannt, Rückfrage bei Christian Heier (Würth-Außendienst) läuft.

**Labels neu angelegt:** `ids-connect` (IDS/Punchout-Features), `blocked-external` (wartet auf externe Auskunft/Daten).

**Why:** `gh issue list --search "IDS"` liefert sonst keine Treffer — kein Sammel-Issue angelegt. Nächster thematischer Dachissue ist #54.

**How to apply:** Bei weiteren IDS/Punchout-Commits → Kommentar an #54. Wenn Würth antwortet → Issue #57 kommentieren und schließen. Keywords für eigenes Issue: "IDS-Connect", "Punchout", "Lieferantenportal".
