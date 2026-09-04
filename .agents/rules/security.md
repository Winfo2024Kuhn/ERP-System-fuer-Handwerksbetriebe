# Sicherheits- & Datenschutz-Richtlinien

## 🛑 Absolute Sicherheitsregeln
1. **API-Keys & Secrets:** NIEMALS in Code oder Commits schreiben. Nur in `application-local.properties` (gitignored). Vor jedem Commit `git diff --staged` prüfen.
2. **Datenschutz (DSGVO):** Nutzer-, Mitarbeiter- und Zeitdaten sind personenbezogen. In Tests NUR Dummy-Daten (`Max Mustermann`, `test@example.com`) verwenden.
3. **Sperrzone für Commits:** `application-local.properties`, `*.env`, `uploads/`, `*.key/pem/p12`.

## Sicherheits-Checkliste für neue Endpoints & Komponenten
1. **SQL Injection:** Immer parametrisierte Queries (`:param`).
2. **XSS:** Eingaben escapen / kein unsanitized HTML im UI.
3. **Ungültige IDs:** Negative IDs, 0, `Long.MAX_VALUE` abfangen.
4. **Validierung:** Leere Pflichtfelder und überlange Eingaben abfangen.
5. **Path-Traversal:** Bei Datei-Handling keine relativen Pfade (`../`) zulassen.
