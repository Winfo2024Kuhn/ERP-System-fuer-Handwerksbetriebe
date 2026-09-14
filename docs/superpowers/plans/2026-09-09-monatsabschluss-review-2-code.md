# Abschnitt 2 – Code-Review und Nachprüfung

Stand: 09.09.2026. Prüfbereich a1b1a44f..f602247d, Tasks 2/4/7. Merge-Commits im Featurestand geprüft. Eigener Review-Worktree codex/monatsabschluss-review-2-code. Nachbesserung bdceac5f als b71ea5da übernommen und selbst geprüft.

**Ampel: 🟡 – abgenommen nach Nachbesserung. Keine offenen neuen Blocker.**

## Behobener Rotbefund

DatePicker.tsx:118 beschriftet Tagbuttons jetzt sinnvoll mit vollständigem Datum. Bestehende Integrationsprüfungen suchten weiterhin den zugänglichen Namen `1`: MitarbeiterEditor.task8.test.tsx:118/123 und ZeiterfassungZeitkontenTask10.test.tsx:52. Dadurch brachen zwei Tests der vollständigen PC-Suite. bdceac5f passt ausschließlich diese drei Locator an; Schutz vor verspäteten Vorschauantworten und Payload-Prüfungen bleiben erhalten. Eigene Nachprüfung: beide Dateien, **5/5 Tests bestanden**. Keine Produktänderung durch den Reviewer.

## Eigene Verifikation

| Prüfung | Ergebnis | Log |
| --- | --- | --- |
| Vollständiges Backend `./mvnw -B test` | 2803 Tests, 0 Failures, 4 bekannte Audit-Errors, 13 Skips | `/tmp/monatsabschluss-review2-tests.log` |
| Audit-Nachlauf mit explizitem MySQL-Dialekt/URL und create | 4 Tests, 0 Failures/Errors, 1 bekannter Skip | `/tmp/monatsabschluss-review2-audit.log` |
| DatevExportMysqlTest | 6/6 bestanden | `/tmp/monatsabschluss-review2-exportmysql.log` |
| MonatsabschlussSammelMysqlTest | 3/3 bestanden | `/tmp/monatsabschluss-review2-sammelmysql.log` |
| PC lint | Exit 0 | `/tmp/monatsabschluss-review2-lint.log` |
| Vollständige PC-Suite vor Nachbesserung | 1202 Tests: 1182 bestanden, 20 fehlgeschlagen | `/tmp/monatsabschluss-review2-pc.log` |
| Baseline a1b1a44f, drei betroffene PC-Dateien | 18 identische Altfehler; beide Stichtagdateien 5/5 bestanden; insgesamt 6 bestanden/18 fehlgeschlagen | `/tmp/monatsabschluss-review2-baseline-pc.log` |
| Eigene Nachprüfung bdceac5f | beide Stichtagdateien 5/5 bestanden | `/tmp/monatsabschluss-review2-nachpruefung.log` |
| PC Produktionsbuild | Exit 0 | `/tmp/monatsabschluss-review2-build.log` |

Die 18 übrigen PC-Fehler stammen unverändert aus LieferantDokumentModal.test.tsx (`TypeError: object.stream is not a function`). Eigenständig im isolierten Baseline-Worktree reproduziert; kein Abschnittsregressionsbefund. Die vier Audit-Errors sind die bereits belegte H2-/MySQL-Testkonfigurationsmischung und bestehen mit korrekter Konfiguration. Deshalb keine Behauptung einer vollständig grünen Standardsuite.

MySQL ausschließlich auf eigenen Datenbanken `review2_export`, `review2_sammel`, `review2_audit` im bereitgestellten temporären Container (Port 33379); keine realen Daten. Volllauf verwendet standardmäßige isolierte H2-Tests. Das verfügbare Shellwerkzeug besitzt keinen timeout-Parameter; Logs wurden in Dateien geschrieben und zurückgegebene Sessions bis zum Exit explizit gepollt. Kein separater Maven-Package/Clean-Lauf. Eigene statische Buildartefakte zurückgenommen und nicht committet.

## Codebeurteilung und Hinweise

- Berechtigungsprüfung vor neuen Datenzugriffen; DTOs, parametrisierte Abfragen, feste Dateinamen, no-store am Download, Limits für Mitarbeiter/Monate/Seiten. Kein neuer Geheimnis- oder personenbezogener Loggingbefund.
- Übersichten laden Personen, Abteilungen und Salden in begrenzten Mengen. Geschlossene Stände gewinnen; warme geschlossene Listen benötigen keine Liveberechnung/Audits. Kalte offene Caches verwenden die vorhandene begrenzte Berechnung, wie im Plan erlaubt.
- Sammelabschluss sperrt Mitarbeiter und Saldo, besitzt eigenständige Transaktionen je Eintrag und ruft die vorhandene Abschlusslogik. MySQL prüft konkurrierenden Einzel-/Sammelabschluss, Rollbackisolation und Wiederholung ohne doppeltes Audit.
- DATEV prüft Auswahl, Version, Konfiguration und historische Kategorien erneut. Eigene Exporttransaktion verhindert veralteten Persistence-Context; optimistische Commitprüfungen verhindern Dateirückgabe bei konkurrierender Wiederöffnung/Konfigurations-/Personalnummeränderung. Keine Teil-Datei; Golden-Bytes und Aggregation geprüft.
- Der dokumentierte Task-3-Randfall GET und PUT innerhalb derselben Transaktion ist in den hier eingeführten HTTP-Pfaden nicht vorhanden. Keine Blockierung wegen hypothetischer späterer Zusammenlegung.
- Nicht blockierende Grenze: Die Dokumentation unterscheidet strukturelles NUM11.2-Limit von einem nicht belegten engeren BS01-Höchstwert. Kein tatsächlicher DATEV-Import oder Zertifizierungsnachweis erfolgt; diese Grenze bleibt ausdrücklich offengelegt.
- Bestehende Bundlegrößenwarnung und genannte Alt-Testfehler sind Hinweise. Design/E2E/Screenshots bleiben beim parallel zuständigen Designreview und sind nicht durch diesen Bericht abgenommen.
