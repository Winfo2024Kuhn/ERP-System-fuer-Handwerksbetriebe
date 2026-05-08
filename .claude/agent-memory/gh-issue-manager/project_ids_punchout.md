---
name: IDS-Connect Punchout — Issue-Zuordnung
description: Kein eigenes Issue für IDS-Punchout; Commits werden an #54 (Bedarf Lieferantenauswahl) kommentiert
type: project
---

**IDS-Connect Punchout-Flow hat kein dediziertes GitHub-Issue (Stand 2026-05-08).**

Commits mit IDS/Punchout-Bezug werden an Issue **#54** (`[Bedarf] Phase 2.2: Lieferantenauswahl-Modal + Preisanfrage`) kommentiert, weil der IDS-Flow den Lieferantenauswahl-Button auf der Bedarfsseite umsetzt.

Sprint-Struktur:
- Sprint 1 (Commit 7f0454b): IDS-Connect-Konfiguration pro Lieferant (Konfig-Pflege im Backend/Frontend)
- Sprint 2+3 (Commit 0ad580c): Punchout-Flow + `IdsLieferantenAuswahlModal` + `IdsPunchoutController/Service` + HMAC-Token + Cart-Return als ENTWURF-Bestellung

**Why:** `gh issue list --search "IDS"` liefert keine Treffer — kein eigenes Issue angelegt. Nächster thematischer Dachissue ist #54.

**How to apply:** Bei weiteren IDS/Punchout-Commits → Kommentar an #54. Wenn User ein eigenes Issue anlegen will, Keywords: "IDS-Connect", "Punchout", "Lieferantenportal".
