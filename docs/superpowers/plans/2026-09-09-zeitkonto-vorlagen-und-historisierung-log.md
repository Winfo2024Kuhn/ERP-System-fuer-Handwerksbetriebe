# Kontext-Log: Zeitkonto-Vorlagen und Historisierung

## Fortsetzung am 09.09.2026 — Orchestrator

Branch: feature/zeitkonto-vorlagen-und-historisierung
Basis: 48d25186
Status: Implementierung vom Nutzer ausdrücklich freigegeben; Planung und Umsetzung laufen.

Bestand: Spec und Issue #98 vorhanden. Keine ursprünglichen Produktänderungen. Fremde Änderungen an static/index.html und lokalen Skill-/Codex-Dateien bleiben unberührt.

Baseline vor ersten Codeänderungen:
- Backend: 2611 Tests, 0 Failures, 4 Errors. AuditChainRepairIntegrationTest und AuditHashRoundtripDiagnoseTest: MySQL-Treiber akzeptiert H2-Test-URL nicht.
- PC: 1133 bestanden, 18 fehlgeschlagen in LieferantDokumentModal.test.tsx (Blob.stream fehlt im Testkontext); 91/92 Dateien bestanden.
- Mobile: 146/146 Tests, 11/11 Dateien bestanden.

Umsetzung: isolierte Worktrees, höchstens drei disjunkte Tasks pro Runde, anschließender Review. Modellnamen der früheren Claude-Rollen sind in dieser Codex-Sitzung nicht verfügbar; Subagenten verwenden das Sitzungsmodell bei gleicher Aufgabenverteilung.

Fachliche Präzisierungen für die Umsetzung:
- Historisierung schützt auch offene Altmonate. Der Monatsabschluss ist eine zusätzliche Sperre für den gespeicherten Monatswert, keine zugesicherte rechtliche GoBD-Zertifizierung.
- Abwesenheitsstunden bleiben wie entschieden unverändert. Bei rückdatiertem Wechsel innerhalb offener Monate muss die Vorschau vorhandene Abwesenheitsgutschriften sichtbar nennen; sie werden nicht still neu berechnet. Eine Änderung der Stundengutschrift ist nicht automatisch eine Änderung des Urlaubsanspruchs in Tagen.
- Akteur des Monatsabschlusses wird aus authentifiziertem FrontendUserPrincipal und zugeordnetem Mitarbeiter bestimmt. Frei übermittelte Mitarbeiter-IDs sind keine Berechtigungsnachweise. Das neue Recht bleibt standardmäßig aus; kein impliziter Admin-Bypass.
- Schema wird in nachvollziehbare Migrationen geteilt. Alte Tabelle bleibt nur während des internen Umbaus bestehen und entfällt vor Abschluss; keine produktive Datenbank wird für Entwicklungsprüfungen geändert.
