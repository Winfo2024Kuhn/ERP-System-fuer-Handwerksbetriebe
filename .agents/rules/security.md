# Sicherheits- & Datenschutz-Richtlinien

## 🛑 Absolute Sicherheitsregeln
1. **API-Keys & Secrets:** NIEMALS in Code, Logs oder Commits schreiben. Vor jedem Commit `git diff --staged` prüfen. Bei Leak: Sofort rotieren! Wo sie liegen:
   - **Grundregel (alle übrigen und neuen Secrets):** nur in `application-local.properties` (gitignored) bzw. im Docker-Betrieb in `app.env` (Umgebungsvariablen). Muss ein Secret in die DB, dann nur verschlüsselt über `MailSecretService` – wie das FRITZ!Box-Passwort. Keine weiteren Klartext-Secrets in `system_setting`.
   - **Gemini-Key, SMTP/IMAP-Zugänge:** DB-Tabelle `system_setting` (im Klartext), gepflegt unter System-Einstellungen → KI-Funktionen bzw. → E-Mail; Postfach-Passwörter zusätzlich verschlüsselt in `email_absender`. Die Properties `ai.gemini.api-key`, `smtp.*`, `imap.*` sind nur Rückfall (`application-local.properties` bzw. `app.env`).
   - **FRITZ!Box-Passwort:** `system_setting`, verschlüsselt mit `mail.credentials.encryption-key`.
   - DB-Dumps enthalten deshalb Klartext-Zugänge: nie committen oder weitergeben.
2. **Datenschutz (DSGVO):** Nutzer-, Mitarbeiter- und Zeitdaten sind personenbezogen. In Tests NUR Dummy-Daten (`Max Mustermann`, `test@example.com`) verwenden.
3. **Sperrzone für Commits:** `application-local.properties`, `*.env`, `uploads/`, `*.key/pem/p12`, DB-Dumps.

## Sicherheits-Checkliste für neue Endpoints & Komponenten
1. **SQL Injection:** Immer parametrisierte Queries (`:param`).
2. **XSS:** Eingaben escapen / kein unsanitized HTML im UI.
3. **Ungültige IDs:** Negative IDs, 0, `Long.MAX_VALUE` abfangen.
4. **Validierung:** Leere Pflichtfelder und überlange Eingaben abfangen.
5. **Path-Traversal:** Bei Datei-Handling keine relativen Pfade (`../`) zulassen.
