---
name: security-audit
description: Security-Review eines Endpoints, einer Komponente oder eines Commits auf OWASP Top 10, Secrets und DSGVO.
---

# Security-Audit

Führe ein vollständiges Security-Review des angegebenen Codes durch.

---

## 0. Secrets & API-Keys (höchste Priorität)

Suche im aktuellen Diff/Code nach:
- `api_key`, `apikey`, `api-key`
- `password`, `passwort`, `passwd`, `pwd`
- `token`, `secret`, `credentials`
- `private_key`, `-----BEGIN`
- Hardcodierte IP-Adressen oder URLs mit Zugangsdaten

**Befund:** Sofort stoppen, Key rotieren (Git-History reicht NICHT), dann erst beheben.

---

## 1. Personenbezogene Daten (DSGVO)

- [ ] Werden Kundendaten, Mitarbeiterdaten oder Zeitbuchungen verarbeitet?
- [ ] Sind Logs frei von Namen, E-Mails, Adressen?
- [ ] Werden Test-Dummy-Daten verwendet (keine echten Nutzer)?
- [ ] Werden Dateien mit Personendaten korrekt in `uploads/` gespeichert (gitignored)?
- [ ] Gibt es eine Rechtsgrundlage für die Datenverarbeitung?

---

## 2. SQL Injection (A03:2021)

```java
// SICHER:
@Query("SELECT a FROM Artikel a WHERE a.name LIKE %:name%")
List<Artikel> findByName(@Param("name") String name);

// UNSICHER (Befund melden):
"SELECT ... WHERE name LIKE '%" + userInput + "%'"
```

- [ ] Ausschließlich parametrisierte Queries (`@Query` mit `:param`)
- [ ] Kein String-Concat in JPQL/SQL
- [ ] Spring Data Derived Queries wo möglich

---

## 3. Cross-Site Scripting / XSS (A03:2021)

Backend:
- [ ] HTML-Ausgaben über `EmailHtmlSanitizer` sanitisiert?
- [ ] Nutzereingaben in E-Mails/Dokumenten bereinigt?

Frontend:
- [ ] Kein `dangerouslySetInnerHTML` ohne serverseitig sanitisierte Daten?
- [ ] URL-Parameter mit `encodeURIComponent()` kodiert?
- [ ] React-eigenes Escaping genutzt (normale JSX-Ausgabe `{variable}`)?

---

## 4. Path Traversal (A01:2021)

Bei Datei-Upload/Download-Endpoints:
- [ ] Dateinamen normalisiert: `Paths.get(name).getFileName().toString()`
- [ ] Pfad bleibt innerhalb `uploads/`-Verzeichnis (`.startsWith(uploadDir)`)
- [ ] UUID-basierte Dateinamen bevorzugt

---

## 5. Mass Assignment (A08:2021)

- [ ] DTOs schützen vor ungewollten Feldänderungen
- [ ] Jackson `FAIL_ON_UNKNOWN_PROPERTIES` konfiguriert?
- [ ] Keine sensiblen Felder (Rollen, Admin-Flags) in Request-DTOs?

---

## 6. Input-Validierung (A03:2021)

- [ ] Pflichtfelder mit `@NotNull`, `@NotBlank`, `@Size` annotiert?
- [ ] Maximallängen definiert (z.B. `@Size(max = 500)`)
- [ ] Numerische Grenzen geprüft (`@Min`, `@Max`)
- [ ] Datei-Typen bei Uploads validiert (kein `.exe`, `.bat`, `.sh`)
- [ ] Maximale Dateigröße konfiguriert (< 15 GB)

---

## 7. Datei-Upload-Sicherheit

- [ ] Nur erlaubte MIME-Types (PDF, JPG, PNG, DOCX...)
- [ ] Gefährliche Typen blockiert (`.exe`, `.bat`, `.sh`, `.ps1`)
- [ ] Dateigröße begrenzt
- [ ] Dateiname sanitisiert (kein Path-Traversal)

---

## 8. Logging (A09:2021)

- [ ] Sicherheitsrelevante Aktionen geloggt (Upload, Löschung, Zugriffsversuche)
- [ ] Logs enthalten KEINE Passwörter, Tokens oder vollständige personenbezogene Daten
- [ ] Keine Stack-Traces mit sensiblen Daten in Produktions-Logs

---

## Bewertung

Nach dem Review ausgeben:
- **Kritische Befunde** (sofortiger Handlungsbedarf)
- **Empfehlungen** (sollte behoben werden)
- **Bestanden** (kein Handlungsbedarf)
