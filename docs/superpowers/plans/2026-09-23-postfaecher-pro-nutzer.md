# Plan: E-Mail-Postfächer pro Nutzer

Issue: #168
Feature-Branch: `feature/postfaecher-pro-nutzer` (Release 1, Tasks 1–12)
Folge-Branch: `feature/postfaecher-aufraeumen` (Release 2, nur Task 13 — erst nach bestätigter Produktivmigration)
Spec: `docs/superpowers/specs/2026-09-23-postfaecher-pro-nutzer.md` (vollständig lesen)
Kontext-Log: `docs/superpowers/plans/2026-09-23-postfaecher-pro-nutzer-log.md` (legt der Orchestrator an)

**Status:** Grobplan mit 13 Tasks. Abschnitts-/Paket-Einteilung, Branches und
Worktrees fehlen bewusst — die trägt der nächste Agent nach. Maßgeblich dafür:
„Abhängigkeiten“, „Kopplungs-Hinweise“ und „Dateiübersicht“ am Ende.

**Code-Stand der Recherche:** `origin/codex/beschaffung-konzept` @ `486ee800`.
Alle Zeilenangaben beziehen sich darauf. Nach dem Merge auf `main` können sie
sich um wenige Zeilen verschieben — dann per Symbolname suchen, nicht raten.

---

## Voraussetzung (Blocker)

`origin/codex/beschaffung-konzept` muss auf `main` gemergt sein, **bevor**
Task 1 startet. Der Plan baut auf dessen Bausteinen auf:
`MailSecretService`, `KontoMailTransport`, `MailkontoService`, `SentMailArchiver.archiviere(...)`,
`email.konto_id` (V384), `email_import_identitaet` (V384), `einkauf_mailkonto` (V383),
`EinkaufOutboxService`/`EinkaufVersandWorker`, `LocalTestMailPolicy`.
Der Orchestrator prüft beim Start: `git merge-base --is-ancestor 486ee800 origin/main`.

---

## Entschiedene Punkte (verbindlich)

Vom Orchestrator entschieden:

1. **Zusätzliche Import-Ordner:** eigene Tabelle `postfach_import_ordner`, keine
   JSON-Spalte. **Ergänzung dieses Plans:** zusätzlich Spalte `richtung ENUM('IN','OUT')`
   — ohne sie lässt sich die heutige zweite Gesendet-Liste
   (`OUTGOING_FOLDERS = "INBOX.Sent", "INBOX.Sent Items"`, `EmailImportService.java:115-117`)
   nicht übernehmen.
2. **Migrationsnummern:** nicht fest vergeben. Im Plan steht `V{N}` (Task 1) und
   `V{M}` (Task 13) = **nächste freie Nummer nach dem Einkauf-Merge**. Stand der
   Recherche: `main` endet bei `V376`, der Einkaufs-Branch bringt `V377`–`V387` und
   `V396`. Also voraussichtlich `V397`. Der Coding-Agent prüft beim Start
   `ls src/main/resources/db/migration | sort -V | tail -3` auf `origin/main` **und**
   `git ls-tree -r --name-only origin/<offene-branches> src/main/resources/db/migration`
   (Lücke V388–V395 ist vermutlich von anderen Branches belegt).
   `spring.flyway.out-of-order=true` ist gesetzt, trotzdem immer > höchste Nummer.
3. **Konsument von `SystemSettingsController#getMailFromAddress`
   (`GET/PUT /api/settings/mail-from`, `SystemSettingsController.java:177-206`):**
   ermittelt = `react-pc-frontend/src/components/settings/sections/EmailSettingsSection.tsx`
   (Laden Zeile 121, Speichern Zeile 229, Block „Ihr Postfach“ ab Zeile 440).
   **Entscheidung: entfällt ersatzlos.** Die Absenderadresse für Website-Anfragen
   kommt künftig aus dem Versandzweck `WEBSITE_ANFRAGE`, der Anzeigename aus
   `postfach.absender_name`. Endpunkt und Frontend-Block werden in Task 5 bzw.
   Task 9 entfernt.

## Ergänzende Plan-Entscheidungen (von der Spec offen gelassen, begründet)

- **A. „Max. ein persönliches Postfach pro Nutzer“:** MySQL kennt keine partiellen
  Indizes. Da `besitzer_id` bei `ALLGEMEIN` per Regel `NULL` ist, reicht ein
  einfaches `UNIQUE (besitzer_id)` (MySQL/H2 erlauben mehrere `NULL`). Dazu
  `CHECK`-Constraint (Art ↔ Besitzer) **und** Service-Prüfung (ältere MySQL
  ignorieren `CHECK`).
- **B. Neue Spalte `postfach.absender_name`** (sichtbarer Absendername, z. B.
  „Bauschlosserei Muster“). `anzeigename` ist das Etikett in der Oberfläche
  (z. B. „Altes Postfach (t-online)“) und darf nicht beim Kunden als Absender
  erscheinen. Übernimmt `mail.absender-name`, `mail.dokumente.absender-name`,
  `einkauf_mailkonto.from_name`.
- **C. `email.postfach_id` etc. als einfache `Long`-Spalte** im Entity
  (`@Column(name="postfach_id") Long postfachId`), nicht als `@ManyToOne`. Grund:
  keine N+1-Ladevorgänge in den Listen, keine Kopplung der großen `Email`-Entity.
  FK liegt in der Datenbank.
- **D. Standard-Postfach eines Nutzers** (serverseitig, `PostfachSichtbarkeitService.standardPostfach`):
  persönliches Postfach, falls sendefähig; sonst das sendefähige sichtbare Postfach
  mit der kleinsten ID; sonst keins → 400 „Bitte ein Absender-Postfach auswählen.“
  Nötig, weil nach der Migration **niemand** ein persönliches Postfach hat, aber
  Steuerberater-, Antwort- und Bestell-Dialoge heute `sender: null` schicken.
- **E. Detail-Zugriff auf eine Mail** (`GET /api/emails/{id}`, Thread, Anhänge,
  Markieren …): erlaubt, wenn das Postfach der Mail sichtbar ist **oder** die Mail
  fachlich zugeordnet ist (Projekt/Anfrage/Lieferant ≠ null). Sonst **404**
  (nicht 403, keine Existenz preisgeben — gleiche Antwort wie `getEmailById`
  heute bei unbekannter ID, `UnifiedEmailController.java:782-790`). Setzt Spec
  Abschnitt 3 (fachliche Ansichten bleiben sichtbar) für Links aus Projekten um.
- **F. Zweitkopie bei Mehrfachzustellung:** Kommt dieselbe `message_id` als `IN`
  in einem zweiten Postfach an, übernimmt die Zweitkopie Zuordnung
  (Projekt/Anfrage/Lieferant/`zuordnungTyp`) sowie Spam-/Newsletter-Kennzeichen
  der Erstkopie und läuft **nicht** erneut durch Anhang-/Beleg-Verarbeitung und
  Anfrage-Erkennung. Sonst entstünden doppelte Lieferanten-Rechnungen
  (`EmailAttachmentProcessingService.processLieferantAttachments`).
- **G. Migration erhält heutiges Verhalten:** Der Migrator gibt **allen**
  bestehenden Nutzern Zugriff auf **alle** migrierten Postfächer (Altes Postfach,
  Dokumente-Postfach, Einkaufs-Postfach) — heute sieht jeder alle Mails. Versandzwecke
  nach Migration: `WEBSITE_ANFRAGE` → Altes Postfach; `GESCHAEFTSDOKUMENTE` →
  Dokumente-Postfach, falls `smtp.dokumente` aktiv war, sonst Altes Postfach;
  `EINKAUF` → Einkaufs-Postfach, falls `einkauf_mailkonto` aktiv war, sonst leer
  (wie heute: Einkauf aus). Ohne diese Belegung bräche am Tag des Deploys jeder
  Rechnungsversand.
- **H. Zweck-Signatur:** `postfach_versandzweck.signatur_id` optional. Ist sie leer,
  gilt wie heute die System-Signatur (`EmailSignatureService.appendSystemSignatureIfConfigured`,
  Zeile 96-100). Der Migrator setzt sie leer.
- **I. Einkaufs-Outbox bleibt beim String `"EINKAUF"`:** `einkauf_versandauftrag.konto_id`
  und `EinkaufVersandDto.KontoZugangReferenz("EINKAUF")` bleiben als
  Zweck-Referenz; aufgelöst wird über `Versandzweck.EINKAUF`. Kein Schemaumbau der Outbox.
- **J. Zwei Releases:** Flyway läuft beim Start **vor** dem Java-Migrator. Das
  Entfernen der Altlasten (NOT NULL, `konto_id`, `einkauf_mailkonto`, `email_absender`,
  `smtp.*`-Schlüssel) kann deshalb nicht im selben Deploy passieren → Task 13 ist ein
  eigener PR, der erst nach geprüfter Produktivmigration gemergt wird. Sein SQL
  bricht selbst ab, wenn noch Zeilen ohne `postfach_id` existieren.

---

## Global Constraints

Gilt für jeden Task. Maßgeblich ist der `### Task N`-Block, nicht die Kurzfassung im Auftrag.

### Pflichtlektüre vor dem ersten Edit (hook-erzwungen)

| Du änderst … | Vorher per `Read` laden |
| --- | --- |
| `*.java` (auch Tests/Config) | `docs/agent instructions/docs/BACKEND_ARCH.md` |
| `*.tsx`/`*.ts` unter `react-pc-frontend/` | `docs/agent instructions/docs/FRONTEND_UI.md` **und** Skill `handwerkerprogramm-design` aufrufen (ohne Präfix) |
| Testdateien | `docs/agent instructions/docs/TESTING_SECURITY.md` |
| alle | `.claude/skills/loese-problem/references/kriterien.md` |

Frontend zusätzlich: MCP `shadcn` für fertige Bausteine; Pflicht-Komponenten aus
`FRONTEND_UI.md` (`select-custom.tsx`, eigene Toasts/Dialoge, kein `window.confirm`).
Einstellungs-Bausteine wiederverwenden: `SettingsCard`, `PasswordField`,
`TestResultBanner`, `SaveButton`, `SectionLoading` (`components/settings/settingsUi.tsx:30-155`),
`parseErrorMessage` (`components/settings/settingsApi.ts:20`).
TypeScript: `erasableSyntaxOnly` — keine `enum`, keine Parameter-Properties, keine `namespace`.

### Projektregeln

- Constructor Injection (`@RequiredArgsConstructor`), keine Logik im Controller,
  Entities nie direkt als JSON (DTO-Records). `OutOfOfficeController` gibt heute
  Entities zurück — wird in Task 6 auf DTO umgestellt.
- Flyway: idempotent (Spalten/Indizes über `information_schema` prüfen, Muster
  `V384__email_kontobezug_importidentitaet.sql`), bestehende Migrationen **nie** ändern,
  Java-Enums als `ENUM('…')`-Spalte (`BACKEND_ARCH.md`, `ddl-auto=validate`).
- Nur parametrisierte Queries (`@Query` mit `:param`, `JdbcTemplate` mit `?`).
- Passwörter: nie loggen, nie zurückliefern, `toString()` aller Records mit
  Passwortfeldern maskieren (Muster `MailkontoDto.ServerZugang.toString`,
  `KontoZugang.toString`, `MailkontoService.java:249-257`).
- Keine stille Ausweichlösung auf ein anderes Postfach (Spec Abschnitt 9).
- Handwerker-Sprache in allen sichtbaren Texten: „Postfach“, „Gemeinsames Postfach“,
  „Abruf aktiv“, „Automatische Mails“, „Verbindung testen“ — keine Begriffe wie
  SMTP/IMAP ohne Erklärung in Überschriften (in Feldbeschriftungen erlaubt:
  „Postausgang-Server (SMTP)“, „Posteingang-Server (IMAP)“).
- Neue Auslagerungen/Refactorings, die **nicht** in diesem Plan stehen: anhalten
  und den Nutzer fragen (CLAUDE.md Workflow-Regel 1).

### Testdaten (DSGVO)

Nur Dummy-Daten: Personen „Max Mustermann“ (Profil-ID 7, `max.mustermann@muster-handwerk.de`)
und „Erika Musterfrau“ (Profil-ID 8, `erika.musterfrau@muster-handwerk.de`),
Gemeinschaftspostfach `info@muster-handwerk.de`, Kunde `kunde@muster-kunde.de`.
**Achtung Abwesenheit:** `OutOfOfficeResponder.isReservedTestAddress` (Zeile 298-310)
unterdrückt Antworten an `example.com/.test/...` — Absender in OOO-Tests daher
`kunde@muster-kunde.de`, nicht `example.com`.

### Test-Gates je Coding-Agent (nicht die volle Suite)

- Backend: nur eigene Klassen, `./mvnw -B test -Dtest=KlasseA,KlasseB`.
- Frontend: `npx vitest run <eigene Dateien>`, `npm run lint`, `npm run build`
  (nur als Prüfung — **Ausgabe unter `src/main/resources/static/` nicht committen**,
  `git checkout -- src/main/resources/static && git clean -fd src/main/resources/static`),
  eigene Playwright-Spec: `E2E_PORT=<port> npx playwright test e2e/<spec>` mit
  gestubbten `/api`-Routen (Vorbild `react-pc-frontend/e2e/kasse-einstellungen.spec.ts`,
  Helfer `e2e/hilfen/api.ts` nur **lesen**, nicht ändern; Stubs in der Spec definieren).
- MySQL-Migrationstests (Testcontainers) brauchen Docker. Ist Docker nicht da:
  im Kontext-Log melden, nicht überspringen.

### Build-Artefakte

Der eingecheckte Frontend-Build (`src/main/resources/static/`) wird **nur** in
Task 12 einmal gebaut und committet (parallele Hash-Assets kollidieren sonst).

---

## Gemeinsame Verträge

### Datenmodell (Task 1)

```java
package org.example.kalkulationsprogramm.domain.postfach;
public enum PostfachArt { PERSOENLICH, ALLGEMEIN }
public enum Versandzweck { WEBSITE_ANFRAGE, GESCHAEFTSDOKUMENTE, EINKAUF }
public final class PostfachMigrationsMarker {           // system_setting-Schlüssel
    public static final String SCHLUESSEL = "postfach.migration.version";
    public static final String VERSION = "1";
}
@Embeddable public class PostfachImportOrdner {         // equals/hashCode über beide Felder
    @Column(name = "ordner_name", length = 255, nullable = false) String ordnerName;
    @Enumerated(EnumType.STRING) @Column(name = "richtung", nullable = false) EmailDirection richtung;
}
@Entity @Table(name = "postfach") public class Postfach {
    Long id; @Version long version;
    String adresse; String anzeigename; String absenderName;          // absenderName nullable
    @Enumerated(EnumType.STRING) PostfachArt art; Long besitzerId;     // nullable
    String smtpHost; int smtpPort; String smtpUsername; String smtpPasswordCiphertext; String smtpTls; // "TLS"|"STARTTLS"
    String imapHost; Integer imapPort; String imapUsername; String imapPasswordCiphertext; String imapTls; // alle nullable
    String ordnerPosteingang; String ordnerGesendet;
    @ElementCollection(fetch = EAGER) @CollectionTable(name = "postfach_import_ordner", joinColumns = @JoinColumn(name = "postfach_id"))
    Set<PostfachImportOrdner> importOrdner;
    @ElementCollection(fetch = EAGER) @CollectionTable(name = "postfach_zugriff", joinColumns = @JoinColumn(name = "postfach_id"))
    @Column(name = "frontend_user_id") Set<Long> zugriffNutzerIds;
    boolean abrufAktiv; Long standardSignaturId; Instant letzterAbruf; String letzterFehler;
    public boolean imapEingerichtet() { /* host, port>0, username, ciphertext gesetzt */ }
}
@Entity @Table(name = "postfach_versandzweck") public class PostfachVersandzweck {
    Long id; @Enumerated(EnumType.STRING) @Column(unique = true) Versandzweck zweck; Long postfachId; Long signaturId;
}
```

Neue Felder bestehender Entities (alle `Long`, nullable bis Task 13):
`Email.postfachId`, `EmailImportIdentitaet.postfachId`, `OutOfOfficeSchedule.postfachId`,
`EmailDraft.postfachId`.

### Laufzeit-Zugang (Task 2)

```java
package org.example.kalkulationsprogramm.service.mail;
public record PostfachZugang(Long postfachId, String netzwerkSchluessel, boolean aktiv,
        String adresse, String absenderName,
        MailkontoDto.ServerZugang smtp,
        MailkontoDto.ServerZugang imap,          // null = kein IMAP eingerichtet
        String ordnerPosteingang, String ordnerGesendet,
        List<String> eingangsOrdner,             // Posteingang + Import-Ordner IN, ohne Duplikate
        List<String> ausgangsOrdner,             // Gesendet + Import-Ordner OUT, ohne Duplikate
        boolean einkaufsPostfach) {              // an Versandzweck EINKAUF gebunden
    @Override public String toString() { /* ohne Passwörter */ }
}
public record PostfachKennung(Long postfachId, String netzwerkSchluessel) {}
```
`netzwerkSchluessel` = `"EINKAUF"`, wenn das Postfach am Zweck `EINKAUF` hängt, sonst
`"POSTFACH-" + id` — damit bleibt `LocalTestMailPolicy.pruefeNetzwerkzugriff(String)`
(`config/LocalTestMailPolicy.java:20-27`) unverändert gültig.

```java
@Service public class PostfachService {                       // Nachfolger von MailkontoService
    public PostfachZugang zugang(Long postfachId);             // NoSuchElementException; IllegalStateException wenn Schlüssel fehlt
    public Optional<PostfachZugang> zugangFuerZweck(Versandzweck zweck);   // leer + log.warn, wenn nicht belegt/nicht sendefähig
    public PostfachZugang verlangeZugangFuerZweck(Versandzweck zweck);     // IllegalStateException mit Nutzertext
    public Optional<EmailSignature> signaturFuerZweck(Versandzweck zweck);
    public List<PostfachKennung> abrufAktivePostfaecher();     // abruf_aktiv && imapEingerichtet, sortiert nach id
    @Transactional(propagation = REQUIRES_NEW) public void abrufErgebnis(Long postfachId, Instant zeitpunkt, String fehlerCode);
    public boolean sendefaehig(Postfach p);                    // adresse, smtpHost, 1<=smtpPort<=65535, smtpUsername, smtpPasswordCiphertext
    public PostfachZugang zugangAus(Postfach p, String smtpKlartext, String imapKlartext); // für Verbindungstest ungespeicherter Formulare; null = gespeichertes Passwort
}
@Component public class PostfachMailFabrik {
    public EmailService emailService(PostfachZugang zugang);  // Versand + Sent-Kopie in genau dieses Postfach
}
```
Nutzertext bei fehlendem Zweck (einheitlich):
`„Für „<Zweckname>“ ist kein Absender-Postfach eingestellt. Bitte unter Einstellungen → Automatische Mails festlegen.“`
Zweckname: `WEBSITE_ANFRAGE` = „Bestätigung von Website-Anfragen“,
`GESCHAEFTSDOKUMENTE` = „Rechnungen, Mahnungen und Bestätigungen“, `EINKAUF` = „Einkauf“.

### Sichtbarkeit und Verwaltung (Task 5)

```java
package org.example.kalkulationsprogramm.service.postfach;
public class PostfachSichtbarkeitService {
    public FrontendUserProfile aktuellerNutzer(Authentication auth);   // AccessDeniedException analog EinkaufBerechtigungService.java:56-65
    public boolean istAdmin(Authentication auth);                      // DB-Rolle, nicht Session
    public void verlangeAdmin(Authentication auth);
    public List<Postfach> sichtbarePostfaecher(Authentication auth);   // kein Admin-Bypass!
    public Set<Long> sichtbarePostfachIds(Authentication auth);
    public Set<Long> gefiltertePostfachIds(Authentication auth, String filter); // null/"alle" | "mein" | "<id>"; unbekannt/fremd → leeres Set
    public boolean darfNutzen(Authentication auth, Long postfachId);
    public boolean darfEmailSehen(Authentication auth, Email email);    // Entscheidung E
    public Optional<Postfach> persoenlichesPostfach(Authentication auth);
    public Optional<Postfach> standardPostfach(Authentication auth);    // Entscheidung D
    public Optional<Postfach> antwortPostfach(Authentication auth, Email ursprung); // Ursprung wenn darfNutzen && sendefähig, sonst standardPostfach
}
```

REST (neu):

| Methode + Pfad | Wer | Antwort |
| --- | --- | --- |
| `GET /api/settings/postfaecher` | Admin | `List<PostfachDto.Response>` |
| `GET /api/settings/postfaecher/{id}` | Admin | `PostfachDto.Response` |
| `POST /api/settings/postfaecher` | Admin | `PostfachDto.Response` (201) |
| `PUT /api/settings/postfaecher/{id}` | Admin | `PostfachDto.Response` (409 bei Versionskonflikt) |
| `DELETE /api/settings/postfaecher/{id}` | Admin | 204 / 409 „Postfach enthält noch E-Mails – bitte „Abruf aktiv“ ausschalten statt löschen.“ |
| `POST /api/settings/postfaecher/verbindung-testen` | Admin | `MailTransportDto.Testverbindung`; Body `PostfachDto.Verbindungstest(Long postfachId, PostfachDto.Update formular)` |
| `GET /api/settings/postfaecher/status` | Admin | `PostfachDto.Status` |
| `GET /api/settings/versandzwecke` | Admin | `List<PostfachDto.VersandzweckEintrag>` (immer alle 3) |
| `PUT /api/settings/versandzwecke/{zweck}` | Admin | `PostfachDto.VersandzweckEintrag` |
| `GET /api/postfaecher/meine?antwortAufEmailId=` | angemeldet | `PostfachDto.MeinePostfaecher` (nur sendefähige sichtbare) |
| `GET /api/postfaecher/adressen` | angemeldet | `List<String>` aller Postfach-Adressen (ersetzt „eigene Adressen“ im E-Mail-Center) |

`/api/settings/**` ist bereits Admin-only (`SecurityConfig.java:208`), `/api/postfaecher/**`
fällt unter `anyRequest().authenticated()` (Zeile 228). Keine SecurityConfig-Änderung nötig;
Admin-Prüfung trotzdem zusätzlich im Service.

```java
package org.example.kalkulationsprogramm.dto.Postfach;
public final class PostfachDto {
    public record ImportOrdner(String ordnerName, EmailDirection richtung) {}
    public record Update(Long version, String adresse, String anzeigename, String absenderName,
            PostfachArt art, Long besitzerId, Set<Long> zugriffNutzerIds,
            String smtpHost, int smtpPort, String smtpUsername, Verschluesselung smtpTls, String smtpPassword,
            boolean imapEingerichtet, String imapHost, Integer imapPort, String imapUsername,
            Verschluesselung imapTls, String imapPassword,
            String ordnerPosteingang, String ordnerGesendet, List<ImportOrdner> importOrdner,
            Long standardSignaturId, boolean abrufAktiv) { /* toString maskiert Passwörter */ }
    public record Response(Long id, long version, String adresse, String anzeigename, String absenderName,
            PostfachArt art, Long besitzerId, String besitzerName, Set<Long> zugriffNutzerIds,
            String smtpHost, int smtpPort, String smtpUsername, Verschluesselung smtpTls, boolean smtpPasswortGesetzt,
            boolean imapEingerichtet, String imapHost, Integer imapPort, String imapUsername, Verschluesselung imapTls,
            boolean imapPasswortGesetzt, String ordnerPosteingang, String ordnerGesendet, List<ImportOrdner> importOrdner,
            Long standardSignaturId, boolean abrufAktiv, Instant letzterAbruf, String letzterFehler,
            Set<Versandzweck> zwecke) {}
    public record Verbindungstest(Long postfachId, Update formular) {}
    public record ZweckWarnung(Versandzweck zweck, String text) {}
    public record Status(boolean verschluesselungEingerichtet, boolean sendefaehigesPostfachVorhanden,
            boolean umstellungAbgeschlossen, List<ZweckWarnung> warnungen) {}
    public record VersandzweckEintrag(Versandzweck zweck, String bezeichnung, Long postfachId,
            String postfachAdresse, Long signaturId, boolean gueltig, String warnung) {}
    public record VersandzweckUpdate(Long postfachId, Long signaturId) {}
    public record MeinPostfach(Long id, String adresse, String anzeigename, PostfachArt art,
            Long standardSignaturId) {}
    public record MeinePostfaecher(Long standardPostfachId, List<MeinPostfach> postfaecher) {}
}
```
`Verschluesselung` = `org.example.kalkulationsprogramm.dto.Einkauf.MailkontoDto.Verschluesselung`
(bleibt im Einkauf-Paket, nicht verschieben).

### E-Mail-Center-Parameter (Task 7)

Alle Ordner-Endpunkte unter `/api/emails/...` und `/api/emails/stats`,
`/api/emails/search`, `/api/emails/mark-all-read` akzeptieren
`?postfach=alle|mein|<id>` (fehlend = `alle`). `UnifiedEmailDto` bekommt
`Long postfachId`, `String postfachAdresse`, `String postfachName`.
Senden/Antworten: `ProjektEmailDto` (`dto/ProjektEmail/ProjektEmailDto.java`) bekommt
`Long postfachId`; `sender` wird ignoriert.

---

## Tasks

Pfadpräfix Backend: `J = src/main/java/org/example/kalkulationsprogramm`,
`T = src/test/java/org/example/kalkulationsprogramm`, `R = src/main/resources`.

### Task 1 — Schema Phase 1 + Entities + Repositories

- **Abhängig von:** Voraussetzung (Einkauf gemergt).
- **Files:**
  - `R/db/migration/V{N}__postfach_grundlagen.sql` (neu)
  - `J/domain/postfach/{PostfachArt,Versandzweck,PostfachMigrationsMarker,PostfachImportOrdner,Postfach,PostfachVersandzweck}.java` (neu)
  - `J/repository/PostfachRepository.java`, `J/repository/PostfachVersandzweckRepository.java` (neu)
  - `J/domain/Email.java`, `J/domain/einkauf/EmailImportIdentitaet.java`, `J/domain/OutOfOfficeSchedule.java`, `J/domain/EmailDraft.java` (je ein neues Feld)
  - `T/db/PostfachGrundlagenMigrationMysqlTest.java` (neu), `T/repository/PostfachRepositoryTest.java` (neu)
- **Vorbild:**
  - SQL-Idempotenz über `information_schema`: `V384__email_kontobezug_importidentitaet.sql` (ganze Datei).
  - Element-Collection-Tabelle mit ENUM + FK: `V377__einkauf_berechtigungen.sql:1-7`;
    Entity-Seite: `FrontendUserProfile.java:61-72`.
  - Entity mit Ciphertext-/TLS-Spalten und `@Version`: `domain/einkauf/EinkaufMailkonto.java` (ganze Datei).
  - MySQL-Migrationstest: `T/db/EinkaufVorlagenMigrationMysqlTest.java:17-50` (Container `mysql:8.0.44`, Skript zweimal ausführen).
  - Repository-Test: `T/service/mail/MailkontoPersistenzTest.java:28-45` (`@DataJpaTest`).
- **Interfaces:**
  - **Produces:** Entities/Enums aus „Gemeinsame Verträge → Datenmodell“ und
    ```java
    public interface PostfachRepository extends JpaRepository<Postfach, Long> {
        List<Postfach> findByAbrufAktivTrueOrderByIdAsc();
        Optional<Postfach> findByAdresseIgnoreCase(String adresse);
        boolean existsByAdresseIgnoreCase(String adresse);
        Optional<Postfach> findFirstByArtAndBesitzerId(PostfachArt art, Long besitzerId);
        @Query("SELECT p FROM Postfach p WHERE (p.art = :persoenlich AND p.besitzerId = :nutzerId) "
             + "OR (p.art = :allgemein AND :nutzerId MEMBER OF p.zugriffNutzerIds) ORDER BY p.art DESC, p.anzeigename")
        List<Postfach> findSichtbarFuer(@Param("nutzerId") Long nutzerId,
                @Param("persoenlich") PostfachArt persoenlich, @Param("allgemein") PostfachArt allgemein);
        @Query("SELECT p.adresse FROM Postfach p ORDER BY p.adresse") List<String> findAlleAdressen();
    }
    public interface PostfachVersandzweckRepository extends JpaRepository<PostfachVersandzweck, Long> {
        Optional<PostfachVersandzweck> findByZweck(Versandzweck zweck);
        List<PostfachVersandzweck> findByPostfachId(Long postfachId);
    }
    ```
  - **Consumes:** nichts.
- **Steps:**
  - [ ] Migrationsnummer ermitteln (Entscheidung 2) und im Kontext-Log notieren.
  - [ ] SQL `V{N}__postfach_grundlagen.sql` schreiben (alle Tabellen `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`, alles mit `IF NOT EXISTS` bzw. `information_schema`-Wächter):
        - `postfach`: `id BIGINT AUTO_INCREMENT PK`, `version BIGINT NOT NULL DEFAULT 0`,
          `adresse VARCHAR(254) NOT NULL`, `anzeigename VARCHAR(120) NOT NULL`, `absender_name VARCHAR(120) NULL`,
          `art ENUM('PERSOENLICH','ALLGEMEIN') NOT NULL`, `besitzer_id BIGINT NULL`,
          `smtp_host VARCHAR(253) NOT NULL`, `smtp_port INT NOT NULL DEFAULT 465`, `smtp_username VARCHAR(254) NOT NULL`,
          `smtp_password_ciphertext TEXT NULL`, `smtp_tls VARCHAR(16) NOT NULL DEFAULT 'TLS'`,
          `imap_host VARCHAR(253) NULL`, `imap_port INT NULL`, `imap_username VARCHAR(254) NULL`,
          `imap_password_ciphertext TEXT NULL`, `imap_tls VARCHAR(16) NULL`,
          `ordner_posteingang VARCHAR(255) NOT NULL DEFAULT 'INBOX'`, `ordner_gesendet VARCHAR(255) NOT NULL DEFAULT 'Sent'`,
          `abruf_aktiv BOOLEAN NOT NULL DEFAULT FALSE`, `standard_signatur_id BIGINT NULL`,
          `letzter_abruf TIMESTAMP(6) NULL`, `letzter_fehler VARCHAR(80) NULL`;
          `UNIQUE uk_postfach_adresse (adresse)` (Kollation ist case-insensitiv), `UNIQUE uk_postfach_besitzer (besitzer_id)`,
          `CHECK ck_postfach_besitzer ((art='PERSOENLICH' AND besitzer_id IS NOT NULL) OR (art='ALLGEMEIN' AND besitzer_id IS NULL))`,
          `FK fk_postfach_besitzer → frontend_user_profile(id)` (ohne Cascade),
          `FK fk_postfach_signatur → email_signature(id) ON DELETE SET NULL`.
        - `postfach_zugriff (postfach_id BIGINT, frontend_user_id BIGINT, PK beide)`, FKs `ON DELETE CASCADE` auf `postfach` und `frontend_user_profile`.
        - `postfach_import_ordner (postfach_id BIGINT, ordner_name VARCHAR(255), richtung ENUM('IN','OUT'), PK alle drei)`, FK Cascade.
        - `postfach_versandzweck (id BIGINT AI PK, zweck ENUM('WEBSITE_ANFRAGE','GESCHAEFTSDOKUMENTE','EINKAUF') NOT NULL UNIQUE, postfach_id BIGINT NULL, signatur_id BIGINT NULL)`, FKs `ON DELETE SET NULL`.
        - `email`: `ADD COLUMN postfach_id BIGINT NULL`, FK `fk_email_postfach`, `UNIQUE INDEX idx_email_postfach_message_id (postfach_id, message_id)`,
          `MODIFY COLUMN konto_id VARCHAR(16) NULL` (nur wenn Spalte existiert; alter Index `idx_email_konto_message_id` **bleibt** bis Task 13).
        - `email_import_identitaet`: `ADD postfach_id BIGINT NULL`, `MODIFY konto_id VARCHAR(16) NULL`,
          `UNIQUE uk_email_import_identitaet_postfach_uid (postfach_id, folder, uidvalidity, uid)`, FK.
        - `out_of_office_schedule`: `ADD postfach_id BIGINT NULL`, FK, `INDEX ix_ooo_postfach (postfach_id, active)`.
          (Die Tabelle stammt aus der Zeit vor `V208`, existiert also in Produktion ohne Create-Skript.)
        - `email_draft`: `ADD postfach_id BIGINT NULL`, FK `ON DELETE SET NULL`.
  - [ ] Entities laut Vertrag anlegen. `Postfach.smtpTls/imapTls` als `String` (wie `EinkaufMailkonto`, Zeile 37 ff.), `art` als `@Enumerated(STRING)`.
        `@Table(name="postfach", uniqueConstraints = {@UniqueConstraint(name="uk_postfach_adresse", columnNames="adresse"), @UniqueConstraint(name="uk_postfach_besitzer", columnNames="besitzer_id")})`.
  - [ ] `Email.java`: Feld `@Column(name = "postfach_id") private Long postfachId;` neben `kontoId` (Zeile 66) und im `@Table(indexes=…)` (Zeile 21-31) `@Index(name = "idx_email_postfach_message_id", columnList = "postfach_id,message_id", unique = true)` ergänzen. `kontoId` **bleibt** hier (entfernt Task 3).
  - [ ] `EmailImportIdentitaet`, `OutOfOfficeSchedule`, `EmailDraft`: je `Long postfachId` (`@Column(name="postfach_id")`), Getter/Setter.
  - [ ] `PostfachRepositoryTest` (`@DataJpaTest`, H2): (a) zwei `ALLGEMEIN`-Postfächer ohne Besitzer speicherbar; (b) zweites persönliches Postfach für Max Mustermann (besitzerId 7) → `DataIntegrityViolationException`; (c) doppelte Adresse → Exception; (d) `findSichtbarFuer(7,…)` liefert sein persönliches + `info@` nur wenn 7 in `zugriffNutzerIds`, **nicht** das persönliche von Erika (8); (e) Import-Ordner und Zugriffe werden gespeichert und wieder geladen.
  - [ ] `PostfachGrundlagenMigrationMysqlTest`: legt Minimal-Tabellen `frontend_user_profile`, `email_signature`, `email` (mit `konto_id VARCHAR(16) NOT NULL`, `message_id VARCHAR(512)`, Index `idx_email_konto_message_id`), `email_import_identitaet`, `out_of_office_schedule`, `email_draft` an, führt Skript **zweimal** aus; prüft: Tabellen vorhanden, `email.konto_id` nullable, `INSERT` einer Mail ohne `konto_id` möglich, zwei Mails mit gleicher `message_id` in **verschiedenen** Postfächern erlaubt und im **selben** Postfach abgewiesen, `CHECK` weist `PERSOENLICH` ohne Besitzer ab.
  - [ ] `./mvnw -B test -Dtest=PostfachRepositoryTest,PostfachGrundlagenMigrationMysqlTest` grün; zusätzlich `./mvnw -B -q compile`.

### Task 2 — Postfach-Zugang, Versand-Fabrik, Transport/Archiv generalisieren, `MailkontoService` ablösen

- **Abhängig von:** Task 1. **Muss mit Task 3 im selben Coding-Paket laufen** (siehe Kopplungs-Hinweise: `EmailImportService` nutzt `MailkontoService`, der hier gelöscht wird).
- **Files:**
  - neu: `J/service/mail/PostfachZugang.java`, `J/service/mail/PostfachKennung.java`, `J/service/mail/PostfachService.java`, `J/service/mail/PostfachMailFabrik.java`
  - geändert: `J/service/mail/KontoMailTransport.java`, `J/service/mail/SentMailArchiver.java`, `src/main/java/org/example/email/EmailService.java`,
    `J/service/einkauf/EinkaufOutboxService.java`, `J/service/einkauf/EinkaufVersandWorker.java`, `J/exception/EinkaufExceptionHandler.java`, `J/dto/Einkauf/MailkontoDto.java`
  - gelöscht: `J/service/mail/MailkontoService.java`, `J/controller/EinkaufMailkontoController.java`, `J/domain/einkauf/EinkaufMailkonto.java`, `J/repository/EinkaufMailkontoRepository.java`
  - Tests: neu `T/service/mail/PostfachServiceTest.java`, `T/service/mail/PostfachMailFabrikTest.java`; angepasst `T/service/mail/KontoMailTransportTest.java`, `T/service/mail/SentMailArchiverTest.java`, `T/service/EinkaufOutboxServiceTest.java`, `T/service/EinkaufVersandWorkerTest.java`, `T/repository/EinkaufOutboxParallelTest.java`; gelöscht `T/service/mail/MailkontoServiceTest.java`, `T/service/mail/MailkontoPersistenzTest.java`, `T/controller/EinkaufMailkontoHttpTest.java`, `T/controller/EinkaufMailkontoSecurityTest.java`
- **Vorbild:**
  - Entschlüsseln/Vollständigkeit: `MailkontoService.ladeEinkaufZugang()` Zeile 210-221, `komplett()` 223-228.
  - Maskiertes `toString`: `MailkontoService.KontoZugang` Zeile 249-257.
  - IMAP-Properties TLS/STARTTLS: `KontoMailTransport.imapEigenschaften` Zeile 194-205.
- **Interfaces:**
  - **Produces:** `PostfachZugang`, `PostfachKennung`, `PostfachService`, `PostfachMailFabrik` (Verträge oben);
    `SentMailArchiver.fuerPostfach(PostfachZugang): EmailService.SentCopyHandler`;
    `SentMailArchiver.archiviere(PostfachZugang, byte[]): ArchivErgebnis`;
    `KontoMailTransport.vorbereiten/senden/sendenVorbereitet/pruefeVerbindung` mit `PostfachZugang` statt `MailkontoService.KontoZugang`;
    `EmailService.mitVerschluesselung(MailkontoDto.Verschluesselung): EmailService`.
  - **Consumes:** Task 1 (Entities, Repositories), `MailSecretService` (unverändert), `EmailSignatureRepository`.
- **Steps:**
  - [ ] `PostfachZugang`/`PostfachKennung` laut Vertrag. `eingangsOrdner` = `[ordnerPosteingang] + importOrdner(richtung IN)` ohne Duplikate, Reihenfolge stabil; `ausgangsOrdner` analog mit `ordnerGesendet` + `OUT`.
  - [ ] `PostfachService`:
        - `zugang(id)`: `postfachRepository.findById` sonst `NoSuchElementException("Postfach nicht gefunden.")`; ist ein Ciphertext gesetzt → `secrets.ensureConfigured()` und `secrets.decrypt(...)`; `imap` nur befüllen, wenn `imapEingerichtet()`, sonst `null`; `aktiv = sendefaehig(p)`; `einkaufsPostfach` = `versandzweckRepository.findByZweck(EINKAUF)` zeigt auf diese ID; `netzwerkSchluessel` wie im Vertrag. TLS-Strings über `Verschluesselung.valueOf`, Fallback `TLS` (Muster `MailkontoService.enumValue`, Zeile 242-244).
        - `zugangFuerZweck`: Eintrag fehlt oder `postfachId == null` oder Postfach nicht sendefähig → `log.warn("[Postfach] Kein gültiges Postfach für Versandzweck {}", zweck)` und `Optional.empty()`. **Kein** Rückfall auf andere Postfächer.
        - `verlangeZugangFuerZweck`: wirft `IllegalStateException` mit dem Nutzertext aus „Gemeinsame Verträge“.
        - `signaturFuerZweck`: `signaturId` → `emailSignatureRepository.findById`.
        - `abrufAktivePostfaecher`: `findByAbrufAktivTrueOrderByIdAsc()` gefiltert auf `imapEingerichtet()`, gemappt auf `PostfachKennung` (ohne zu entschlüsseln — die Policy muss vor dem Entschlüsseln prüfen können, vgl. Kommentar `EmailImportService.java:148`).
        - `abrufErgebnis`: `letzterAbruf`, `letzterFehler` (≤ 80 Zeichen) setzen, `REQUIRES_NEW`, Optimistic-Lock-Fehler schlucken (debug-Log), damit ein gleichzeitiges Admin-Speichern den Abruf nicht stört.
        - `zugangAus(p, smtpKlartext, imapKlartext)`: wie `zugang`, nimmt aber übergebene Klartexte statt Ciphertext, wenn nicht leer.
  - [ ] `EmailService` (`org/example/email/EmailService.java`): Feld `verschluesselung = Verschluesselung.TLS` + `mitVerschluesselung(...)`. Die vier dupliziert gebauten SMTP-Properties (`sendEmail` 374-382, `sendEmailAndReturnMessageId` 456-461, `…WithInline` 497-502, `sendEmailWithMultipleAttachments` 536-541) in **eine** private Methode `smtpEigenschaften()` ziehen: `TLS` = heutiges Verhalten (`ssl.enable` + SocketFactory); `STARTTLS` = `mail.smtp.starttls.enable/required=true`, ohne SSL-SocketFactory. `sendEmail(...)` mit hart codiertem `secureimap.t-online.de` (Zeile 399 ff.) hat keine Aufrufer — **nicht anfassen**.
  - [ ] `PostfachMailFabrik.emailService(z)`: `new EmailService(z.smtp().host(), z.smtp().port(), z.smtp().username(), z.smtp().password(), localTestMailPolicy).mitVerschluesselung(z.smtp().tls()).mitKontoId(z.netzwerkSchluessel()).mitAbsenderName(z.absenderName()).mitSentKopie(sentMailArchiver.fuerPostfach(z))`. Wirft `IllegalStateException`, wenn `!z.aktiv()`.
  - [ ] `SentMailArchiver`: neue Methode `fuerPostfach(PostfachZugang z)` → Handler; `z.imap() == null` → nur `log.debug`, sonst Kopie per IMAP in `z.ordnerGesendet()`; existiert der Ordner nicht, Fallback auf `SENT_ORDNER_KANDIDATEN` (Zeile 58-59, Logik von `findeSentOrdner`). TLS/STARTTLS-Properties wie in `archiviere` (Zeile 108-117). Duplikatprüfung `istBereitsVorhanden` wiederverwenden, Schalter `mail.sent-kopie.aktiv` respektieren. `archiviere(KontoZugang, byte[])` (Zeile 94) → Parametertyp `PostfachZugang`, `konto.sent()` → `ordnerGesendet()`, `konto.id()` → `netzwerkSchluessel()`, `imap == null` → `ArchivErgebnis(false, "KONTO_UNGUELTIG")`.
        `archiviereKopie(MimeMessage)` (Zeile 77-80) und `fuerDokumentKonto()` (88-91) mit `@Deprecated(forRemoval = true)` markieren — Aufrufer verschwinden in Task 7/8, Löschung in Task 13.
  - [ ] `KontoMailTransport`: Typ `MailkontoService.KontoZugang` → `PostfachZugang` (Zeilen 45, 75, 87, 124, 207); `konto.fromAddress()/fromName()` → `adresse()/absenderName()`, `konto.id()` → `netzwerkSchluessel()`, `konto.sent()` → `ordnerGesendet()`. `pruefeKonto` (207-212): IMAP nur prüfen, wenn `imap != null`. `pruefeVerbindung` (124-154): ist `imap == null`, IMAP-Teil überspringen, `imapErfolgreich=false`, `fehlerCode` = `"IMAP_NICHT_EINGERICHTET"`, falls SMTP ok war.
  - [ ] Einkauf: `EinkaufOutboxService` Zeile 49-50: Policy-Aufruf bleibt, danach `var konto = postfachService.verlangeZugangFuerZweck(Versandzweck.EINKAUF);` (die `"EINKAUF"`-Prüfung in `EinkaufVersandDto.VersandSnapshot`, Zeile 18, bleibt — Entscheidung I). `EinkaufVersandWorker` Zeile 33-35: Typ `PostfachZugang`, `konto = postfachService.verlangeZugangFuerZweck(Versandzweck.EINKAUF)`; `IllegalStateException` landet im bestehenden `catch (RuntimeException)` → `KONTO_NICHT_VERFUEGBAR`.
  - [ ] Löschen: `MailkontoService`, `EinkaufMailkontoController`, `EinkaufMailkonto`, `EinkaufMailkontoRepository` samt der vier Tests. In `EinkaufExceptionHandler` Zeile 17 `EinkaufMailkontoController.class` aus `assignableTypes` entfernen, `istMailkontoAnfrage` (Zeile 52-54) und dessen Verwendungen entfernen. In `MailkontoDto` `Update`/`Response` löschen (`ServerZugang`, `Verschluesselung` bleiben); `MailTransportDto.Testmail/TestmailErgebnis` löschen, falls danach unbenutzt (`git grep`).
  - [ ] `EmailImportService` wird in **Task 3** umgestellt (gleiches Paket) — bis dahin kompiliert das Projekt nicht; erst nach Task 3 bauen.
  - [ ] Tests: `PostfachServiceTest` (Mockito): Zugang mit/ohne IMAP; entschlüsselt über gemockten `MailSecretService`; Schlüssel fehlt + Ciphertext → `IllegalStateException`; `zugangFuerZweck` leer bei fehlendem Eintrag, bei nicht sendefähigem Postfach (kein Passwort) und bei `postfachId=null`, jeweils **ohne** anderes Postfach anzufragen; `netzwerkSchluessel` = `"EINKAUF"` bzw. `"POSTFACH-5"`; `toString` enthält kein Passwort; `abrufAktivePostfaecher` lässt Postfach ohne IMAP weg. `PostfachMailFabrikTest`: STARTTLS-Postfach erzeugt Properties mit `starttls.required` (über package-private `smtpEigenschaften()` prüfen), Sent-Handler-Aufruf ohne IMAP macht keine Netzwerkverbindung. Bestehende Transport-/Archiv-/Outbox-/Worker-Tests auf `PostfachZugang`-Fixtures umbauen (Adresse `einkauf@muster-handwerk.de`), neuer Fall: Einkaufszweck nicht belegt → Worker markiert `KONTO_NICHT_VERFUEGBAR`.
  - [ ] Nach Task 3: `./mvnw -B test -Dtest=PostfachServiceTest,PostfachMailFabrikTest,KontoMailTransportTest,SentMailArchiverTest,EinkaufOutboxServiceTest,EinkaufVersandWorkerTest,EinkaufOutboxParallelTest` grün.

### Task 3 — IMAP-Abruf über alle Postfächer, Konto-ID entfernen

- **Abhängig von:** Task 1, Task 2 (gleiches Coding-Paket, danach).
- **Files:**
  - `J/service/EmailImportService.java`, `J/repository/EmailRepository.java`, `J/repository/EmailImportIdentitaetRepository.java`,
    `J/domain/Email.java` (`kontoId` + alter Index raus), `J/domain/einkauf/EmailImportIdentitaet.java` (`kontoId` raus),
    `J/service/BounceErkennungService.java`, `J/service/EmailOutboundPersistenceService.java`,
    `J/config/EmailThreadBackfillRunner.java`, `J/service/OutOfOfficeResponder.java` (**nur** Record `IncomingMail`, Zeile 54-62)
  - Tests: `T/service/EmailImportServiceTest.java`, `T/service/EmailKontoImportTest.java` → umbenennen in `T/service/EmailPostfachImportTest.java`, `T/service/BounceErkennungServiceTest.java`, `T/service/OutOfOfficeResponderTest.java` (nur Konstruktoraufrufe des Records)
- **Vorbild:** der heutige Konto-Loop `importNewEmails` Zeile 140-162 (Fehlerisolation pro Konto) und `doImport(String)` 176-223.
- **Interfaces:**
  - **Produces:**
    ```java
    public int doImport();                         // alle abrufaktiven Postfächer, Summe
    public int doImport(Long postfachId);          // ein Postfach (für Tests/Trigger)
    public boolean persistImportierteNachricht(Message msg, EmailDirection direction, PostfachZugang zugang,
            String folderName, long uid, long uidValidity) throws MessagingException, IOException;
    public int backfillParentEmails(Long postfachId);
    static String fehlendeMessageId(Long postfachId, String folder, long uidValidity, long uid);
    // OutOfOfficeResponder:
    public record IncomingMail(Long postfachId, String fromAddress, String subject, LocalDateTime sentAt, boolean spam,
            boolean newsletter, String autoSubmittedHeader, String precedenceHeader, String listIdHeader) {}
    // EmailRepository (ersetzt alle kontoId-Methoden, Zeilen 58-70 und 348):
    List<Email> findByPostfachId(Long postfachId);
    Optional<Email> findByPostfachIdAndMessageId(Long postfachId, String messageId);
    boolean existsByPostfachIdAndMessageId(Long postfachId, String messageId);
    Optional<Email> findFirstByMessageIdAndPostfachIdNotAndDirectionOrderByIdAsc(String messageId, Long postfachId, EmailDirection direction);
    List<Email> findByPostfachIdAndMessageIdIn(Long postfachId, Collection<String> messageIds);
    // EmailImportIdentitaetRepository:
    boolean existsByPostfachIdAndFolderAndUidValidityAndUid(Long postfachId, String folder, long uidValidity, long uid);
    // EmailOutboundPersistenceService:
    public void speichereOutEmail(Email email, Long postfachId);
    public boolean existsByMessageId(Long postfachId, String messageId);
    // BounceErkennungService:
    public boolean verarbeiteRuecklaeufer(Message msg, Long postfachId);
    ```
  - **Consumes:** Task 2 (`PostfachService.abrufAktivePostfaecher/zugang/abrufErgebnis`, `PostfachZugang`).
- **Steps:**
  - [ ] `importNewEmails()` (Zeile 140-162): Schleife über `postfachService.abrufAktivePostfaecher()`; pro Kennung: `localTestMailPolicy.pruefeNetzwerkzugriff(k.netzwerkSchluessel())`, `zugang = postfachService.zugang(k.postfachId())`, `n = importierePostfach(zugang)`, `postfachService.abrufErgebnis(id, Instant.now(), null)`; `catch (Exception e)` → `log.error("[EmailImport] Fehler beim Import von Postfach {}: {}", id, e.getClass().getSimpleName())` + `abrufErgebnis(id, now, fehlerCode(e))` und **weiter** mit dem nächsten Postfach. `fehlerCode`: `AuthenticationFailedException` → `AUTHENTIFIZIERUNG_FEHLGESCHLAGEN`, `MessagingException` → `IMAP_VERBINDUNG`, `IllegalStateException` → `ZUGANG_UNLESBAR`, sonst `UNBEKANNT`.
  - [ ] `doImport(String)` → private `importierePostfach(PostfachZugang)`: gleicher Ablauf wie Zeile 177-222, aber Verbindungsfehler von `store.connect` **nicht** schlucken (damit `letzterFehler` stimmt), Ordner aus `zugang.eingangsOrdner()`/`ausgangsOrdner()`. `INCOMING_FOLDERS`/`OUTGOING_FOLDERS` (Zeile 106-117) löschen — Task 4 hat eine eigene Kopie für die Migration.
  - [ ] `doImport()` (Zeile 171-173) = Schleife wie oben mit Summe; `doImport(Long)`; `triggerImport()` (1067) nutzt `doImport()`.
  - [ ] Signaturen durchziehen: `importFromFolder(Store, String, EmailDirection, PostfachZugang)`, `importMessage(Message, IMAPFolder, EmailDirection, PostfachZugang)` (Überladung ohne Konto, Zeile 352-355, löschen), `persistImportierteNachricht(...)`, `pruefeRuecklaeufer(Message, EmailDirection, PostfachZugang)`.
  - [ ] In `persistImportierteNachricht` (Zeile 374-661): `kontoId`-Abfragen auf `postfachId`; `email.setPostfachId(zugang.postfachId())` statt `setKontoId` (Zeile 443); Identität mit `postfachId` (Konstruktoren von `EmailImportIdentitaet` Zeile 45-51 auf `Long postfachId` umstellen); Subject-Fallback (518) und Bounce (337) für **alle** Postfächer mit `!zugang.einkaufsPostfach()` statt nur `HAUPT`; Einkaufs-Event (642) bei `zugang.einkaufsPostfach()`; Legacy-Nachbearbeitung (609) bei `!zugang.einkaufsPostfach()`.
  - [ ] **Zweitkopie (Entscheidung F):** vor dem Speichern für `direction == IN` `erstkopie = emailRepository.findFirstByMessageIdAndPostfachIdNotAndDirectionOrderByIdAsc(messageId, postfachId, IN)`. Vorhanden → Zuordnung übernehmen (`assignToProjekt/assignToAnfrage/assignToLieferant` bzw. `zuordnungTyp`), Spam-/Newsletter-Kennzeichen kopieren (Setter der Felder hinter `isSpam()/isNewsletter()` in `Email.java` prüfen), `processAttachments` normal (Dateien gehören zur Mail), **aber** `postProcessEmail`/`processLieferantAttachments` (609-616) überspringen. `log.debug` ohne Adresse.
  - [ ] Abwesenheit (622-640): Bedingung `"HAUPT".equals(kontoId)` entfernen (gilt jetzt für jedes Postfach), `new OutOfOfficeResponder.IncomingMail(zugang.postfachId(), …)`. Record in `OutOfOfficeResponder` um die erste Komponente `Long postfachId` erweitern — **sonst nichts** in dieser Datei (Rest ist Task 6).
  - [ ] `findParentEmail(msg, Long postfachId)` (717-751) → `findByPostfachIdAndMessageIdIn`; `fehlendeMessageId(Long postfachId, …)` (770) → `"<no-msgid-p" + postfachId + "-" …`.
  - [ ] `backfillParentEmails()` (1168) iteriert `postfachRepository.findAll()`; `backfillParentEmails(Long)` nutzt `findByPostfachId`. `EmailThreadBackfillRunner` Zeile 50-52: Schleife über `postfachRepository.findAll()` statt der drei Strings.
  - [ ] `deleteEmailFromServer(Email)` (1340 ff.): `email.getPostfachId() == null` → debug + return; sonst `postfachService.zugang(id)`, `imap == null` → return; Verbindung mit TLS/STARTTLS aus `zugang.imap().tls()`, Policy mit `netzwerkSchluessel()`, Fallback-Ordnerliste = `eingangsOrdner + ausgangsOrdner` statt `INCOMING_FOLDERS + OUTGOING_FOLDERS`.
  - [ ] `Email.java`: Feld `kontoId` (Zeile 65-66) und Index `idx_email_konto_message_id` (Zeile 22) entfernen. `EmailImportIdentitaet`: `kontoId` entfernen, `@UniqueConstraint` auf `postfach_id, folder, uidvalidity, uid` umstellen (Name `uk_email_import_identitaet_postfach_uid`).
  - [ ] `EmailRepository`: Methoden Zeile 58-70 und 348 ersetzen (Vertrag). `findByMessageId`/`existsByMessageId`/`findByMessageIdIn` haben laut `git grep` keine Aufrufer — löschen; falls doch welche auftauchen, ohne Konto-Filter neu schreiben.
  - [ ] `BounceErkennungService`: Überladung Zeile 180-182 (HAUPT) löschen, `verarbeiteRuecklaeufer(Message, Long postfachId)`, `markiereBetroffeneAusgangsmail(String, Long)` → `findByPostfachIdAndMessageId`.
  - [ ] `EmailOutboundPersistenceService`: HAUPT-Überladungen (Zeile 33-37, 49-52) löschen, Rest auf `Long postfachId` (Vertrag). Aufrufer `AnfrageBestaetigungVersandService` stellt **Task 8** um — bis dahin kompiliert die Datei nicht; deshalb beide in derselben Welle integrieren oder Task 8 direkt danach (siehe Kopplungs-Hinweise).
  - [ ] Tests (Dummy-Adressen, H2 bzw. Mockito): `EmailPostfachImportTest` — (a) zwei abrufaktive Postfächer, das erste wirft `AuthenticationFailedException` → zweites wird trotzdem importiert, `abrufErgebnis(1, …, "AUTHENTIFIZIERUNG_FEHLGESCHLAGEN")`, `abrufErgebnis(2, …, null)`; (b) Import-Ordner IN/OUT werden mit richtiger Richtung abgefragt; (c) dieselbe Message-ID in `info@` und `max.mustermann@` ergibt zwei Zeilen; die Zweitkopie übernimmt Projekt-Zuordnung und löst `processLieferantAttachments` **nicht** aus; (d) Einkaufs-Postfach veröffentlicht `EinkaufEmailImportiert`, andere nicht; (e) `IncomingMail` trägt die `postfachId`. `BounceErkennungServiceTest` auf `postfachId`. `EmailImportServiceTest` an neue Signaturen anpassen.
  - [ ] `./mvnw -B test -Dtest=EmailImportServiceTest,EmailPostfachImportTest,BounceErkennungServiceTest,OutOfOfficeResponderTest` + die Tests aus Task 2 grün (Stand: Task 8 noch offen → falls `AnfrageBestaetigungVersandService` nicht kompiliert, dort **nur** die zwei Aufrufe mechanisch auf `(null, messageId)`/`(email, null)` umbiegen und im Log vermerken; Task 8 ersetzt sie fachlich).

### Task 4 — Java-Migrator: Altlast t-online, Dokumente- und Einkaufskonto in Postfächer überführen

- **Abhängig von:** Task 1. Unabhängig von Task 2/3 (liest Altdaten per `JdbcTemplate` und `SystemSettingsService`-Gettern, schreibt über Repositories) → parallel zu Task 2+3 möglich.
- **Files:**
  - neu: `J/service/postfach/PostfachMigrationService.java`, `J/config/PostfachMigrationRunner.java`
  - Tests neu: `T/service/postfach/PostfachMigrationServiceTest.java` (H2, `@DataJpaTest` + `@Import`), `T/service/postfach/PostfachMigrationMysqlTest.java` (Testcontainers, optional wenn Docker da)
- **Vorbild:**
  - Start-Runner, fehlertolerant, Schalter `app.startup-maintenance.enabled`: `J/config/EmailThreadBackfillRunner.java:31-62`.
  - Settings-Getter inkl. Property-Fallback: `SystemSettingsService.java:81-122` (SMTP, `getMailFromAddress`), `327-360` (IMAP), `138-180`/`232-285` (Dokumente-Konto, `nutztDokumentMailKonto`, `getDokumentMailKonto`, `getDokumentImapZugang`), `SystemSettingsService.save(key, value, beschreibung)` (Zeile ~492).
  - Einkaufs-Altzeile: Spalten aus `V383__einkauf_mailkonto.sql`.
- **Interfaces:**
  - **Produces:**
    ```java
    public enum MigrationsErgebnis { ABGESCHLOSSEN, BEREITS_ERLEDIGT, NICHTS_ZU_TUN, SCHLUESSEL_FEHLT }
    @Service public class PostfachMigrationService {
        @Transactional public MigrationsErgebnis migriere();
    }
    ```
    Nach Erfolg: `system_setting[PostfachMigrationsMarker.SCHLUESSEL] = "1"` (auch bei `NICHTS_ZU_TUN`).
  - **Consumes:** Task 1 (Entities, Repos, Marker), `MailSecretService.isConfigured/encrypt`, `SystemSettingsService`-Getter (bleiben bis Task 13), `FrontendUserProfileRepository.findAll()`.
- **Steps:**
  - [ ] Runner: `@EventListener(ApplicationReadyEvent.class) @Order(10)` (vor `EmailThreadBackfillRunner` mit `@Order(100)`), ruft `migriere()`, loggt das Ergebnis; `SCHLUESSEL_FEHLT` → `log.error("[PostfachMigration] Schlüssel mail.credentials.encryption-key fehlt – Umstellung NICHT durchgeführt. E-Mail-Abruf und -Versand ruhen bis zum Neustart mit Schlüssel.")`. Jede Exception fangen (Start darf nicht scheitern), dann ERROR-Log.
  - [ ] `migriere()` in **einer** Transaktion (JpaTransactionManager nimmt `JdbcTemplate` mit), damit der Import-Scheduler (Start nach 10 s, `EmailImportService.java:140`) die neuen Postfächer erst sieht, wenn auch alle Mails umgehängt sind:
        1. Marker = `"1"` → `BEREITS_ERLEDIGT`.
        2. Altdaten erkennen: `hauptVorhanden = hatWert(settings.getSmtpHost()) && hatWert(settings.getSmtpUsername())`; `dokumenteVorhanden = settings.nutztDokumentMailKonto()`; `einkaufZeile` = `SELECT … FROM einkauf_mailkonto WHERE id='EINKAUF'` nur wenn Tabelle existiert (`tabelleVorhanden`, s. u.). Nichts davon → Marker setzen, `NICHTS_ZU_TUN`.
        3. Klartext-Passwörter vorhanden, aber `!secrets.isConfigured()` → **ohne** Änderung `SCHLUESSEL_FEHLT`.
        4. **Altes Postfach:** `adresse = settings.getMailFromAddress()`, `anzeigename = "Altes Postfach (t-online)"`, `absenderName = settings.getMailAbsenderName()` (leer → `null`), `art = ALLGEMEIN`, SMTP `getSmtpHost/Port/Username`, `encrypt(getSmtpPassword())`, `smtpTls="TLS"` (heutiges `EmailService` sendet immer SSL); IMAP `getImapHost/Port/Username`, `encrypt(getImapPassword())`, `imapTls="TLS"`; `ordnerPosteingang="INBOX"`, `ordnerGesendet="INBOX.Sent"`; Import-Ordner IN = `"INBOX.Archives (2).Eingangsanfragen"`, `"INBOX.Archives (2).Eingangs Ab's"`, `"INBOX.Archives (2).Eingangsrechnungen"`, `"INBOX.Archives (2).Gedruckte Eingangsrechnungen"`, `"INBOX.Archives (2).Werkstoffzeugnisse"`, OUT = `"INBOX.Sent Items"` (1:1 aus `EmailImportService.java:106-117`, hier als eigene Konstanten, da Task 3 die Originale löscht); **`abrufAktiv = true`** (Spec 6.4). Existiert die Adresse schon (`findByAdresseIgnoreCase`), diese Zeile wiederverwenden.
        5. **Dokumente-Postfach** (nur wenn `dokumenteVorhanden`): aus `settings.getDokumentMailKonto()` (host, port, username, password, fromAddress, fromName) + `settings.getDokumentImapZugang()`; `anzeigename = "Postfach für Rechnungen und Mahnungen"`; **`abrufAktiv` = bisheriger Status `MailkontoService.imapAbrufAktiv("DOKUMENTE")`, ohne IMAP-Zugang `false`** (Spec 6.7 korrigiert; IMAP-Zugang wird trotzdem übernommen, damit die Gesendet-Kopie funktioniert — siehe Risiko R2); `ordnerGesendet="Sent"`. Gleiche Adresse wie Altes Postfach → kein neues Postfach, Zweck auf das Alte legen.
        6. **Einkaufs-Postfach** (nur wenn Einkaufszeile mit `aktiv=1` oder nicht-leerer `from_address`): Ciphertexte **unverändert kopieren** (gleiches Format `v1:` und gleicher Schlüssel), `adresse=from_address`, `absenderName=from_name`, `anzeigename="Einkauf"`, Host/Port/User/TLS/`inbox`/`sent` übernehmen, `abrufAktiv = aktiv`.
        7. Zugriff: jede angelegte Zeile bekommt `zugriffNutzerIds` = IDs aller `FrontendUserProfile` (Entscheidung G).
        8. Versandzwecke anlegen/aktualisieren (Entscheidung G), `signaturId = null`.
        9. Bestandsdaten umhängen per `JdbcTemplate` (vorher `postfachRepository.flush()`), nur Zeilen mit `postfach_id IS NULL`:
           - Hat `email` die Spalte `konto_id` (`spalteVorhanden`): `UPDATE email SET postfach_id=? WHERE postfach_id IS NULL AND konto_id='DOKUMENTE'` (Dokumente- sonst Altes Postfach), dasselbe für `'EINKAUF'` (Einkaufs- sonst Altes Postfach), danach Rest (`HAUPT`/`NULL`) → Altes Postfach. Ohne Spalte: alles → Altes Postfach.
           - `email_import_identitaet` genauso.
           - `UPDATE out_of_office_schedule SET postfach_id=? WHERE postfach_id IS NULL` → Altes Postfach.
           - Gibt es kein Altes Postfach (nur Einkauf), die übrigen Zeilen mit `NULL` lassen und WARN loggen (Task 13 würde dann abbrechen — gewollt).
        10. Klartexte leeren: `settings.save("smtp.password", "", …)`, `"imap.password"`, `"smtp.dokumente.password"` (nur wenn übernommen).
        11. Marker setzen, `ABGESCHLOSSEN`.
  - [ ] H2-Einzelplatzbetrieb (Profil `h2`, `ddl-auto=update` löscht keine Spalten): wenn `DatabaseMetaData.getDatabaseProductName()` „H2“ ist und `email.konto_id` bzw. `email_import_identitaet.konto_id` existiert → `ALTER TABLE … ALTER COLUMN konto_id SET NULL` (try/catch, INFO-Log). Sonst schlagen dort nach Task 3 alle Inserts fehl.
  - [ ] Helfer `spalteVorhanden(tabelle, spalte)`/`tabelleVorhanden(tabelle)` über `DatabaseMetaData` (Groß-/Kleinschreibung beider Varianten prüfen), **kein** String-Concat in SQL mit Nutzerdaten.
  - [ ] Logs ohne Adressen und ohne Passwörter; nur Anzahl umgehängter Zeilen.
  - [ ] `PostfachMigrationServiceTest` (H2; Legacy-Spalten per `JdbcTemplate` vorher anlegen: `ALTER TABLE email ADD COLUMN konto_id VARCHAR(16)`, `CREATE TABLE einkauf_mailkonto …`; `SystemSettingsService` als `@MockBean` mit Max-Mustermann-Daten, `MailSecretService` echt mit 32-Byte-Testschlüssel):
        (a) Standardfall → ein Altes Postfach `ALLGEMEIN`, `abrufAktiv`, 5+1 Import-Ordner, Zugriff für Max und Erika, alle drei alten Mails haben `postfach_id`, OOO-Plan hat `postfach_id`, `settings.save("smtp.password","",…)` aufgerufen, Marker gesetzt, Passwort nur verschlüsselt (`startsWith("v1:")`);
        (b) zweiter Aufruf → `BEREITS_ERLEDIGT`, keine zweite Zeile;
        (c) Dokumente aktiv → zweites Postfach, `abrufAktiv` wie vorher (Test mit Abruf an und aus), Mails mit `konto_id='DOKUMENTE'` dort, Zweck `GESCHAEFTSDOKUMENTE` zeigt darauf;
        (d) Einkaufszeile aktiv → Ciphertext identisch kopiert, Zweck `EINKAUF`;
        (e) Schlüssel fehlt → `SCHLUESSEL_FEHLT`, keine Zeile, kein `save`;
        (f) keine Altdaten → `NICHTS_ZU_TUN`, Marker gesetzt;
        (g) Fehler mitten in Schritt 9 (gemocktes `JdbcTemplate` wirft) → Rollback: keine Postfach-Zeile, kein Marker.
  - [ ] `./mvnw -B test -Dtest=PostfachMigrationServiceTest` (+ MySQL-Test falls Docker) grün.

### Task 5 — Sichtbarkeit, Postfach-Verwaltung, Versandzwecke (Backend-API), Abbau der alten Mail-Einstellungen

- **Abhängig von:** Task 1, Task 2 (Verbindungstest nutzt `PostfachService.zugangAus` + `KontoMailTransport.pruefeVerbindung`).
- **Files:**
  - neu: `J/service/postfach/PostfachSichtbarkeitService.java`, `J/service/postfach/PostfachVerwaltungService.java`, `J/service/postfach/VersandzweckVerwaltungService.java`,
    `J/dto/Postfach/PostfachDto.java`, `J/controller/PostfachAdminController.java`, `J/controller/VersandzweckController.java`, `J/controller/PostfachController.java`, `J/exception/PostfachExceptionHandler.java`
  - geändert: `J/controller/SystemSettingsController.java`, `J/service/SystemSettingsService.java`
  - Tests neu: `T/service/postfach/PostfachSichtbarkeitServiceTest.java`, `T/service/postfach/PostfachVerwaltungServiceTest.java`, `T/service/postfach/VersandzweckVerwaltungServiceTest.java`, `T/controller/PostfachAdminControllerTest.java`, `T/controller/PostfachControllerTest.java`;
    angepasst/gelöscht: `T/controller/SystemSettingsControllerDokumentMailTest.java` (löschen), `T/service/SystemSettingsServiceDokumentMailTest.java`, `T/service/SystemSettingsServiceMailFromTest.java` (Save-Fälle löschen, Getter-Fälle behalten)
- **Vorbild:**
  - Aktueller Nutzer/Admin aus DB: `EinkaufBerechtigungService.java:56-78`.
  - Validierung, Versionsprüfung, Passwort nur bei Eingabe, `OptimisticLockingFailureException` → 409: `MailkontoService.speichern/validiere` Zeile 101-189 (im Einkaufs-Branch; Datei wird in Task 2 gelöscht — vorher lesen bzw. per `git show origin/codex/beschaffung-konzept:…`). E-Mail-Regex dort (Zeile 29) ist ReDoS-sicher, wiederverwenden.
  - Controller + Advice mit `assignableTypes`: `EinkaufMailkontoController` / `J/exception/EinkaufExceptionHandler.java:17-50`.
  - `@WebMvcTest` + `addFilters=false` + `Authentication`-Principal: `T/controller/UnifiedEmailControllerTest.java:62-68`, Principal-Aufbau `FrontendUserPrincipal` wie in `MailkontoPersistenzTest.auth()`.
- **Interfaces:**
  - **Produces:** `PostfachSichtbarkeitService`, `PostfachDto`, REST-Tabelle aus „Gemeinsame Verträge“, außerdem
    ```java
    public class PostfachVerwaltungService {
        public List<PostfachDto.Response> liste(Authentication auth);
        public PostfachDto.Response lesen(Authentication auth, Long id);
        public PostfachDto.Response anlegen(Authentication auth, PostfachDto.Update u);
        public PostfachDto.Response aendern(Authentication auth, Long id, PostfachDto.Update u);
        public void loeschen(Authentication auth, Long id);
        public MailTransportDto.Testverbindung verbindungTesten(Authentication auth, PostfachDto.Verbindungstest t);
        public PostfachDto.Status status(Authentication auth);
    }
    public class VersandzweckVerwaltungService {
        public List<PostfachDto.VersandzweckEintrag> liste(Authentication auth);
        public PostfachDto.VersandzweckEintrag speichern(Authentication auth, Versandzweck zweck, PostfachDto.VersandzweckUpdate u);
    }
    ```
  - **Consumes:** Task 1, Task 2 (`PostfachService.sendefaehig/zugangAus`, `KontoMailTransport.pruefeVerbindung`), `MailSecretService`, `FrontendUserProfileRepository`, `OutOfOfficeScheduleRepository` (nur lesend). Für die Löschprüfung ergänzt dieser Task in `PostfachRepository` (`EmailRepository` gehört Task 3):
    `@Query("SELECT COUNT(e) FROM Email e WHERE e.postfachId = :id") long zaehleEmails(@Param("id") Long id);` und
    `@Query("SELECT COUNT(o) FROM OutOfOfficeSchedule o WHERE o.postfachId = :id") long zaehleAbwesenheiten(@Param("id") Long id);`.
- **Steps:**
  - [ ] `PostfachSichtbarkeitService` laut Vertrag. `sichtbarePostfaecher` = `findSichtbarFuer(nutzerId, PERSOENLICH, ALLGEMEIN)` — **kein** Admin-Bypass (Spec 2). `darfEmailSehen` = Entscheidung E. `standardPostfach`/`antwortPostfach` = Entscheidung D (Sendefähigkeit über `PostfachService.sendefaehig`).
  - [ ] `PostfachVerwaltungService`: alle Methoden beginnen mit `sichtbarkeit.verlangeAdmin(auth)`. Validierung (`IllegalArgumentException` mit Nutzertext): Adresse gültig + ≤ 254; Anzeigename 1–120; `art` Pflicht; `PERSOENLICH` → `besitzerId` Pflicht, Profil existiert, kein anderes persönliches Postfach für ihn („Max Mustermann hat bereits ein persönliches Postfach.“), `zugriffNutzerIds` wird geleert; `ALLGEMEIN` → `besitzerId` muss `null` sein; SMTP-Host/-User Pflicht, Ports 1–65535; `imapEingerichtet=true` → IMAP-Host/-User/-Port Pflicht; `abrufAktiv=true` nur mit IMAP; Ordnernamen ≤ 255, keine Steuerzeichen (Muster `hatSteuerzeichen`); Passwörter optional ≤ 2000 — leer = unverändert; beim ersten Anlegen SMTP-Passwort Pflicht. Neues Passwort und `!secrets.isConfigured()` → `IllegalStateException("Der Schlüssel für geschützte Mailzugänge ist nicht eingerichtet. Bitte den Betreuer bitten, mail.credentials.encryption-key zu setzen.")`. Version ≠ gespeicherte → `IllegalStateException("Das Postfach wurde zwischenzeitlich geändert. Bitte neu laden.")`. `saveAndFlush` + `OptimisticLockingFailureException` → gleiche Meldung. `imapEingerichtet=false` → alle IMAP-Felder und Ciphertext auf `null`.
        `loeschen`: `postfachRepository.zaehleEmails(id) > 0` oder OOO-Pläne vorhanden → 409-Text aus der REST-Tabelle; Zweck-Zeilen zeigen auf das Postfach → `postfachId=null` setzen (Warnung erscheint dann in „Automatische Mails“).
        `verbindungTesten`: `Postfach` aus Formular bauen (nicht speichern), bei `postfachId` fehlende Passwörter aus der gespeicherten Zeile, dann `kontoMailTransport.pruefeVerbindung(postfachService.zugangAus(...))`.
        `status`: `verschluesselungEingerichtet = secrets.isConfigured()`, `sendefaehigesPostfachVorhanden`, `umstellungAbgeschlossen = "1".equals(settings.get(PostfachMigrationsMarker.SCHLUESSEL, ""))`, `warnungen` = alle Zwecke ohne gültiges Postfach.
        `Response`: `besitzerName` aus `FrontendUserProfile.getDisplayName()`, `zwecke` aus `versandzweckRepository.findByPostfachId`.
  - [ ] `VersandzweckVerwaltungService`: `liste` liefert immer alle drei `Versandzweck`-Werte (fehlende Zeile = leer), `gueltig` = Postfach vorhanden und sendefähig, `warnung` = Nutzertext aus „Gemeinsame Verträge“ sonst `null`; `speichern` prüft Postfach (existiert, sendefähig) und Signatur (existiert), `postfachId=null` erlaubt (bewusst ausschalten, z. B. Einkauf) und loggt `log.warn`.
  - [ ] Controller dünn, Pfade laut REST-Tabelle, `Authentication` als Parameter. `PostfachController.meine(auth, antwortAufEmailId)`: `postfaecher` = sichtbare **und** sendefähige; `standardPostfachId` = bei `antwortAufEmailId` → `antwortPostfach` (Mail über `EmailRepository.findById`, unsichtbar → wie nicht vorhanden), sonst `standardPostfach`. `adressen()` → `postfachRepository.findAlleAdressen()`.
  - [ ] `PostfachExceptionHandler` `@RestControllerAdvice(assignableTypes = {PostfachAdminController.class, VersandzweckController.class, PostfachController.class, OutOfOfficeController.class})`, Abbildung wie `EinkaufExceptionHandler` (400/404/409), `AccessDeniedException` → 403 `{"message":"Dafür fehlt dir die Berechtigung."}`.
  - [ ] `SystemSettingsController`: Endpunkte `GET/PUT /smtp`, `POST /smtp/test`, `GET/PUT /imap`, `POST /imap/test`, `PUT /email-account` (Zeile 54-153), `GET/PUT /mail-from` (177-206), `GET/PUT /dokument-mail`, `POST /dokument-mail/test` (209-310) samt Request-/Response-Records entfernen.
  - [ ] `SystemSettingsService`: `saveMailFromAddress`, `saveMailAbsenderName`, `saveDokumentMailSettings` (beide), `saveSmtpSettings`, `saveImapSettings`, `saveEmailAccount`, `testSmtp`, `testImap` löschen (vorher `git grep` auf weitere Aufrufer — Task 8 entfernt die in `EmailController`/`AutoMahn…` nicht, die rufen nur Getter); in `getAllSettings` (ab Zeile 455) alle `smtp.*`, `imap.*`, `mail.from-address`, `smtp.dokumente.*`, `mail.dokumente.*` entfernen. Getter bleiben, mit `@Deprecated(forRemoval = true)` + Javadoc „nur PostfachMigrationService, entfällt mit Task 13“. `isInitialConfigurationRequired()` (Zeile 407-409): `!isSmtpConfigured()` → „kein sendefähiges Postfach vorhanden“: `PostfachRepository` injizieren und `postfachRepository.findAll().stream().anyMatch(...)` mit denselben Kriterien wie `PostfachService.sendefaehig` (bewusst **nicht** `PostfachService` injizieren — vermeidet einen Zyklus über `SentMailArchiver` → `SystemSettingsService`). Wirkt auf `AuthController` Zeile 38 und 82 (Ersteinrichtungs-Assistent).
  - [ ] Tests: `PostfachSichtbarkeitServiceTest` — Max sieht sein persönliches + `info@` (mit Zugriff), nicht Erikas persönliches; Admin Erika sieht Max' persönliches **nicht**; Filter `mein`/`alle`/`<fremde id>` → leeres Set; `darfEmailSehen` true für fremdes Postfach mit Projekt-Zuordnung, false ohne; `standardPostfach` ohne persönliches → kleinste sichtbare sendefähige ID. `PostfachVerwaltungServiceTest` — Nicht-Admin → `AccessDeniedException`; zweites persönliches → Fehler; Passwort leer lässt Ciphertext; Response enthält kein Passwort-Feld (Reflection über Record-Komponenten); Schlüssel fehlt → 409-Text; Versionskonflikt; Löschen mit Mails → Konflikt. `PostfachAdminControllerTest` inkl. Sicherheits-Checkliste (`TESTING_SECURITY.md`): Adresse `'; DROP TABLE x; --` und `<script>alert(1)</script>` → 400; IDs `0`, `-1`, `Long.MAX_VALUE` → 404; Anzeigename > 10.000 Zeichen → 400. `PostfachControllerTest`: `meine` ohne persönliches, mit `antwortAufEmailId` eines sichtbaren und eines fremden Postfachs.
  - [ ] `./mvnw -B test -Dtest='Postfach*Test,Versandzweck*Test,SystemSettingsService*Test'` grün.

### Task 6 — Abwesenheit pro Postfach (Backend)

- **Abhängig von:** Task 2 (`PostfachMailFabrik`), Task 3 (`IncomingMail.postfachId`), Task 5 (`PostfachSichtbarkeitService`, `PostfachExceptionHandler`).
- **Files:**
  - geändert: `J/service/OutOfOfficeResponder.java`, `J/controller/OutOfOfficeController.java`, `J/repository/OutOfOfficeScheduleRepository.java`
  - neu: `J/service/postfach/AbwesenheitService.java`, `J/dto/Postfach/AbwesenheitDto.java`
  - gelöscht: `J/service/mail/SmtpHtmlMailSender.java`, `J/service/mail/HtmlMailSender.java`, `src/main/java/org/example/email/ImapAppendService.java` (einziger Nutzer ist der Responder; vorher `git grep`)
  - Tests: `T/service/OutOfOfficeResponderTest.java`, neu `T/service/postfach/AbwesenheitServiceTest.java`, neu `T/controller/OutOfOfficeControllerTest.java`; gelöscht `T/service/mail/SmtpHtmlMailSenderTest.java`; angepasst `T/config/LocalTestIsolationTest.java` (Verweise auf `SmtpHtmlMailSender`/`ImapAppendService` entfernen), Kommentar in `J/service/DokumentFreigabeService.java:214` aktualisieren.
- **Vorbild:** heutiger Ablauf `OutOfOfficeResponder.handleIncomingEmail/sendReply` Zeile 78-147 (Loop-Schutz, Dedup über `OooReplyLogRepository` bleibt unverändert).
- **Interfaces:**
  - **Produces:**
    ```java
    public record AbwesenheitDto(Long id, Long postfachId, String postfachAdresse, String postfachAnzeigename,
            boolean postfachGemeinsam, String title, LocalDate startAt, LocalDate endAt, String subjectTemplate,
            String bodyTemplate, SignaturRef signature, boolean active) {
        public record SignaturRef(Long id, String name) {}
    }
    // OutOfOfficeController (Pfad bleibt /api/email/outofoffice):
    GET    ""        → List<AbwesenheitDto>   (eigenes persönliches Postfach; Admin zusätzlich alle gemeinsamen)
    GET    "/active" → AbwesenheitDto | 204   (eigenes persönliches Postfach)
    POST   ""        → AbwesenheitDto          (SaveOooRequest + Feld Long postfachId; fehlt es → persönliches Postfach)
    DELETE "/{id}"   → 204 / 404
    GET    "/postfaecher" → List<PostfachDto.MeinPostfach>  (Postfächer, für die der Nutzer Abwesenheiten pflegen darf)
    ```
    `OutOfOfficeScheduleRepository`: `findFirstByPostfachIdAndActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqualOrderByStartAtDesc(Long, LocalDate, LocalDate)` (mit `@EntityGraph(attributePaths="signature")`), `List<OutOfOfficeSchedule> findByPostfachIdInOrderByStartAtDesc(Collection<Long>)`.
  - **Consumes:** Task 2, 3, 5.
- **Steps:**
  - [ ] Responder: Abhängigkeiten `HtmlMailSender`, `SystemSettingsService`, `ImapAppendService` (Zeile 45-47) ersetzen durch `PostfachService`, `PostfachMailFabrik`, `PostfachRepository`. `incoming.postfachId() == null` → return. Eigener-Absender-Schutz (Zeile 96-99) → `postfachRepository.existsByAdresseIgnoreCase(sender)` (verhindert Ping-Pong zwischen zwei eigenen Postfächern mit Abwesenheit). Plan-Suche mit neuer Methode für genau dieses Postfach. `sendReply`: `zugang = postfachService.zugang(postfachId)`; `!zugang.aktiv()` → WARN, return; `postfachMailFabrik.emailService(zugang).sendEmailAndReturnMessageIdWithInline(sender, null, zugang.adresse(), subject, htmlBody, inlineImages, null, null)` — die Gesendet-Kopie macht der Handler der Fabrik, `imapAppendService.appendToSent` entfällt. `recordReply` unverändert (Plan gehört jetzt zu genau einem Postfach → Dedup automatisch pro Postfach).
  - [ ] `AbwesenheitService` mit Rechten: Postfach `PERSOENLICH` → nur Besitzer; `ALLGEMEIN` → nur Admin (`sichtbarkeit.istAdmin`). Speichern mit bestehender `id`: Plan muss zu einem erlaubten Postfach gehören **und** Ziel-Postfach erlaubt sein. Fremd/unbekannt → `NoSuchElementException` (→ 404). Kein Admin-Zugriff auf fremde persönliche Pläne. Validierung wie Frontend heute: Titel ≤ 200 Pflicht, `startDate ≤ endDate`, Betreff ≤ 300.
  - [ ] Controller: `Authentication` durchreichen, nur DTOs zurückgeben (heute Entities, Zeile 23-58).
  - [ ] Tests: Responder — antwortet nur für Plan des Eingangs-Postfachs (Plan für `info@`, Mail an Max → keine Antwort); sendet über `emailService` der Fabrik mit `info@muster-handwerk.de` als Absender; Absender ist eigene Postfach-Adresse → keine Antwort; zweite Mail desselben Absenders → keine zweite Antwort; Postfach nicht sendefähig → kein Versand. `AbwesenheitServiceTest` — Max speichert für sein Postfach ok, für `info@` → 404/Access; Admin Erika speichert für `info@` ok, für Max' persönliches → 404; Liste von Max enthält keine gemeinsamen Pläne, Liste von Admin enthält sie. Controller-Test mit Sicherheits-Checkliste (Titel mit Script-Tag wird gespeichert, aber nicht ausgeführt — nur Rückgabe prüfen; überlange Texte → 400).
  - [ ] `./mvnw -B test -Dtest=OutOfOfficeResponderTest,AbwesenheitServiceTest,OutOfOfficeControllerTest,LocalTestIsolationTest` grün.

### Task 7 — E-Mail-Center-Backend: Postfach-Filter, 404 für fremde Mails, Senden/Antworten mit `postfachId`, Dedup in fachlichen Ansichten

- **Abhängig von:** Task 2, Task 3, Task 5.
- **Files:**
  - geändert: `J/controller/UnifiedEmailController.java`, `J/controller/FolderStatsDto.java` (nur falls Felder fehlen), `J/dto/Email/UnifiedEmailDto.java`, `J/dto/ProjektEmail/ProjektEmailDto.java`,
    `J/controller/NotificationController.java`, `J/dto/Email/EmailDraftDto.java`, `J/service/EmailDraftService.java`, `J/controller/EmailDraftController.java` (nur falls Mapping dort),
    `J/service/ProjektManagementService.java`, `J/service/KundenDetailService.java`, `J/service/LieferantenDetailService.java`
  - neu: `J/service/postfach/EmailDeduplizierung.java`
  - Tests: `T/controller/UnifiedEmailControllerTest.java`, `T/controller/EmailDraftControllerTest.java`, neu `T/service/postfach/EmailDeduplizierungTest.java`, neu `T/controller/UnifiedEmailControllerPostfachTest.java`
- **Vorbild:** bestehende In-Memory-Filterung der Ordner (`getInboxEmails` Zeile 651-667 — Liste laden, filtern, `skip/limit`), `getStats` Zeile 1159-1215.
- **Interfaces:**
  - **Produces:** Parameter `postfach` + DTO-Felder laut „E-Mail-Center-Parameter“;
    ```java
    public final class EmailDeduplizierung {
        /** Behält die Reihenfolge; bei gleicher messageId gewinnt die Kopie aus einem bevorzugten Postfach, sonst die erste. Mails ohne messageId bleiben. */
        public static List<Email> proMessageId(List<Email> emails, Set<Long> bevorzugtePostfachIds);
    }
    ```
    `EmailDraftDto` + Feld `Long postfachId` (am Ende der Record-Komponenten), `fromAddress` bleibt (wird nicht mehr ausgewertet).
    `/api/emails/from-addresses` (Zeile 128-132) **entfällt** (Ersatz: `/api/postfaecher/meine` und `/api/postfaecher/adressen` aus Task 5).
  - **Consumes:** `PostfachSichtbarkeitService` (Task 5), `PostfachService.zugang/verlangeZugangFuerZweck` + `PostfachMailFabrik` (Task 2), `Email.postfachId` (Task 1/3).
- **Steps:**
  - [ ] Konstruktor: `EmailAbsenderService`, `FrontendUserProfileService` (nur für `resolveSenderAddress` genutzt — prüfen) und `SystemSettingsService` (nur noch für Dokumentkonto, Zeile 1577-1583) entfernen; `PostfachSichtbarkeitService`, `PostfachService`, `PostfachMailFabrik`, `PostfachRepository` ergänzen. `resolveSenderAddress` (Zeile 2140 ff.) löschen.
  - [ ] Ordner-Endpunkte (Zeilen 570-780: `/unassigned`, `/inquiries`, `/new/*`, `/search`, `/inbox`, `/projects`, `/offers`, `/suppliers`, `/tax-advisors`, `/sent`, `/trash`, `/spam`, `/newsletter`, `/starred`): Parameter `@RequestParam(value = "postfach", required = false) String postfach, Authentication auth`; `Set<Long> ids = sichtbarkeit.gefiltertePostfachIds(auth, postfach)`; Filter `e -> e.getPostfachId() != null && ids.contains(e.getPostfachId())` **vor** `skip/limit`. Einheitlich über eine private Hilfsmethode `Stream<Email> nurPostfaecher(List<Email>, Set<Long>)`.
  - [ ] `/stats` (1159): alle `count…Unread()`-Aufrufe durch Zählen auf den bereits geladenen, postfach-gefilterten Listen ersetzen (gleiche Fachlogik wie heute, nur gefiltert). `/mark-all-read` (1034) nur Mails der gefilterten Postfächer.
  - [ ] Detail-/Aktions-Endpunkte: private `Optional<Email> ladeSichtbar(Long id, Authentication auth)` = `findById` + `sichtbarkeit.darfEmailSehen`; sonst `ResponseEntity.notFound()`. Anwenden auf: Anhang-Download (134), `block-sender`/`mark-spam`/`mark-not-spam`/`mark-not-newsletter`/`confirm-newsletter` (268-434), `/{id}` (782), `/{emailId}/thread` (799, fokussierte Mail prüfen), `mark-viewed` (837), `assign/*` + `unassign` (855-913), `DELETE` (915, 931), `bulk/move-to-folder` (983, unsichtbare IDs still überspringen), `mark-read` (1065), `toggle-star` (1082), `download-all` (1098), `possible-assignments` (1379).
  - [ ] Fachliche Ansichten `/projekt/{id}`, `/anfrage/{id}`, `/lieferant/{id}` (510-565): **kein** Postfach-Filter, aber `EmailDeduplizierung.proMessageId(emails, sichtbarkeit.sichtbarePostfachIds(auth))` vor `limit`.
  - [ ] `toListDto` (1929) / `toDto` (2000): `postfachId`, `postfachAdresse`, `postfachName` (= `anzeigename`) über `postfachRepository.findById` (der Persistenzkontext der `@Transactional(readOnly)`-Methode hält jedes Postfach nach dem ersten Laden — höchstens so viele Abfragen wie Postfächer).
  - [ ] `/send` (1419 ff.): `ProjektEmailDto.postfachId` (neu). Geschäftsdokument (Zeile 1574-1576 bestimmt `istGeschaeftsdokument`) → `postfachService.verlangeZugangFuerZweck(GESCHAEFTSDOKUMENTE)`; `IllegalStateException` → `409` mit `{"message": <Text>}`. Sonst: `postfachId` gesetzt → `sichtbarkeit.darfNutzen` sonst `404`; nicht gesetzt → `sichtbarkeit.standardPostfach(auth)` sonst `400 {"message":"Bitte ein Absender-Postfach auswählen."}`. Dann `zugang = postfachService.zugang(id)`, `emailService = postfachMailFabrik.emailService(zugang)`, `sender = zugang.adresse()`. Den Block Zeile 1577-1621 (Konto-Wahl, Absender-Liste, Anzeigename aus `EmailAbsenderService`) ersetzen. `email.setPostfachId(zugang.postfachId())` bei der gespeicherten Mail (1636).
  - [ ] `/{emailId}/reply` (1745 ff.): Eltern-Mail über `ladeSichtbar`; Postfach: `dto.postfachId` (mit `darfNutzen`) sonst `sichtbarkeit.antwortPostfach(auth, parent)` sonst 400. Block Zeile 1758-1766 und 1810-1825 ersetzen; `email.setPostfachId(...)` (1840).
  - [ ] `NotificationController` (Zeile 130-172): `Authentication` in die Summary-Methode, alle E-Mail-Listen mit `sichtbarePostfachIds` filtern (Ungelesen-Zähler und Glocke).
  - [ ] Entwürfe: `EmailDraftService` Zeile 88 ff. `draft.setPostfachId(dto.postfachId())`, Mapping zurück ins DTO; Länge/Prüfung: `postfachId` muss sichtbar sein, sonst `null` speichern (kein Fehler beim Zwischenspeichern).
  - [ ] Dedup auch in `ProjektManagementService.mappeMitKilogramm` (Zeile 1523), `KundenDetailService` (Zeile 51-52), `LieferantenDetailService.loadEmailsEntities` (Zeile 159) — dort ohne Nutzerkontext: `EmailDeduplizierung.proMessageId(liste, Set.of())`.
  - [ ] Tests: `EmailDeduplizierungTest` (100 % Abdeckung, Util-Regel): leere Liste, `null`-messageId bleibt, Duplikat mit bevorzugtem zweiten Eintrag, Reihenfolge stabil. `UnifiedEmailControllerPostfachTest` (`@WebMvcTest`, `addFilters=false`, Principal Max Mustermann): Posteingang `postfach=alle` zeigt Max' und `info@`-Mails, nicht Erikas; `postfach=mein` nur Max; `postfach=<Erikas id>` → leere Liste; `GET /{id}` einer Erika-Mail → 404, mit Projekt-Zuordnung → 200; `/stats` zählt nur sichtbare; `/projekt/{id}` liefert eine CC-Doppelmail nur einmal; `/send` ohne `postfachId` nutzt Standard-Postfach, mit fremder `postfachId` → 404, Geschäftsdokument ohne Zweck → 409 mit Text, mit Zweck → Absender = Zweck-Adresse; `/reply` auf `info@`-Mail mit Zugriff → Absender `info@`, ohne Zugriff aber mit Projekt-Zuordnung → persönliches Postfach. Bestehenden `UnifiedEmailControllerTest` anpassen (`@MockBean EmailAbsenderService`/`FrontendUserProfileService` raus, neue MockBeans rein, Sender-Erwartungen auf Postfach-Adresse). Sicherheits-Checkliste: `postfach='; DROP TABLE x; --` → leere Liste (kein 500), `postfach=-1`/`Long.MAX_VALUE` → leere Liste.
  - [ ] `./mvnw -B test -Dtest='UnifiedEmailController*Test,EmailDeduplizierungTest,EmailDraftControllerTest'` grün.

### Task 8 — Automatische und weitere Versandstellen auf Postfach/Versandzweck, `EmailAbsender` entfernen

- **Abhängig von:** Task 2, Task 3 (neue Signaturen `EmailOutboundPersistenceService`), Task 5 (`PostfachSichtbarkeitService`). Läuft parallel zu Task 7 (disjunkte Dateien) — **muss im selben Abschnitt wie Task 7 integriert werden** (Task 8 löscht `EmailAbsenderService`, Task 7 entfernt dessen Nutzung in `UnifiedEmailController`).
- **Files:**
  - geändert: `J/controller/EmailController.java`, `J/controller/LieferantenController.java`, `J/dto/Projekt/ProjektEmailDto.java`,
    `J/service/AnfrageBestaetigungVersandService.java`, `J/service/AutoMahnVersandService.java`, `J/service/AutoAuftragsbestaetigungVersandService.java`,
    `J/service/ProjektEmailArchivService.java`, `J/service/EmailSignatureService.java`,
    `J/domain/FrontendUserProfile.java`, `J/service/FrontendUserProfileService.java`, `J/controller/FrontendUserController.java`, `J/controller/FirmaController.java`
  - gelöscht: `J/domain/EmailAbsender.java`, `J/repository/EmailAbsenderRepository.java`, `J/service/EmailAbsenderService.java`, `J/dto/EmailAbsenderDto.java`, `T/service/EmailAbsenderServiceTest.java`
  - Tests: `T/controller/EmailControllerTest.java`, `T/controller/LieferantenControllerTest.java`, `T/service/AnfrageBestaetigungVersandServiceTest.java`, `T/service/AutoMahnVersandServiceTest.java`, Test zu `AutoAuftragsbestaetigungVersandService` (per `git grep` finden), `T/controller/FirmaControllerSecurityTest.java`, `T/controller/FirmaControllerLogoTest.java`, `T/service/FrontendUserProfileServiceRegisterTest.java`
- **Vorbild:** heutiger Dokumentkonto-Versand `AutoMahnVersandService.sendeEmail` Zeile 452-465 (gleiche Struktur, nur Zugang aus Versandzweck).
- **Interfaces:**
  - **Produces:**
    ```java
    // EmailSignatureService
    public String appendSignaturFuerZweck(String htmlBody, Optional<EmailSignature> zweckSignatur); // leer → appendSystemSignatureIfConfigured
    // ProjektEmailArchivService
    public … archiviereVersandteEmail(…, Long postfachId)   // bestehende Signatur um letzten Parameter erweitern, setzt email.setPostfachId
    // AnfrageBestaetigungVersandService (Testnaht, package-private)
    EmailService baueEmailService(PostfachZugang zugang);
    ```
    `GET /api/email/dokument-absender` (EmailController Zeile 100-106) bleibt mit gleicher Antwortform `{aktiv, address}`, gespeist aus `postfachService.zugangFuerZweck(GESCHAEFTSDOKUMENTE)`.
    `GET /api/email/from-addresses` (Zeile 78-82) und `GET/POST/PUT/DELETE /api/firma/email-absender` (FirmaController Zeile 174-205) **entfallen**.
  - **Consumes:** Task 2, Task 3, Task 5.
- **Steps:**
  - [ ] `EmailController` `/send` (354 ff.) und `/send/anfrage` (474 ff.): Block `getDokumentMailKonto()`/`nutztDokumentMailKonto()` (370-381, 488-499) ersetzen durch `zugang = postfachService.verlangeZugangFuerZweck(GESCHAEFTSDOKUMENTE)`, `postfachMailFabrik.emailService(zugang)`, Absender `zugang.adresse()`; `IllegalStateException` → `409` mit Nutzertext (Rückgabetyp `ResponseEntity<?>` bzw. `ResponseStatusException(CONFLICT, text)`). `email.setPostfachId(...)` an den `new Email()`-Stellen (418, 548). `EmailAbsenderService` aus dem Konstruktor (66).
  - [ ] `LieferantenController.sendEmail` (Zeile ~300-340): `Authentication auth` ergänzen, Postfach wie in Task 7 `/send` (ohne Geschäftsdokument-Zweig): `dto.getPostfachId()` (neues Feld in `dto/Projekt/ProjektEmailDto`) mit `darfNutzen`, sonst `standardPostfach`; Versand über Fabrik; `emailContext.setFromAddress(zugang.adresse())`, `setPostfachId`.
  - [ ] `AnfrageBestaetigungVersandService` (86-104, 155-230): `isSmtpConfigured()`-Prüfung ersetzen durch `postfachService.zugangFuerZweck(WEBSITE_ANFRAGE)`; leer → `log.warn("Anfrage-Bestaetigung uebersprungen: kein Postfach für Website-Anfragen eingestellt")`, `return false`. Absender `zugang.adresse()`, Signatur `emailSignatureService.appendSignaturFuerZweck(html, postfachService.signaturFuerZweck(WEBSITE_ANFRAGE))`, Persistenz `outboundPersistenceService.existsByMessageId(zugang.postfachId(), messageId)` / `speichereOutEmail(email, zugang.postfachId())`. `baueEmailService(PostfachZugang)` → `postfachMailFabrik.emailService(zugang)`.
  - [ ] `AutoMahnVersandService.sendeEmail` (452-465) + `ermittleAbsenderAdresse` (1020-1023) und `AutoAuftragsbestaetigungVersandService` (145-165): Zugang aus `verlangeZugangFuerZweck(GESCHAEFTSDOKUMENTE)`, Signatur via `appendSignaturFuerZweck` (an den Stellen, die heute `appendSystemSignatureIfConfigured` rufen), `projektEmailArchivService.archiviereVersandteEmail(…, zugang.postfachId())` (Aufrufe 409 bzw. 239). Fehlender Zweck → Exception läuft in die bestehende Fehlerbehandlung des Laufs (Mahnung/AB gilt als nicht versendet) + `log.warn` — **kein** Rückfall.
  - [ ] `EmailSignatureService.appendSignaturFuerZweck` laut Vertrag (nutzt `ensureSignaturePresentOnce(html, sig, null)`, Zeile 234).
  - [ ] `EmailAbsender` entfernen: `FrontendUserProfile` Feld/Getter/Setter (Zeile 78-80, 185-190); `FrontendUserProfileService` (31, Parameter `emailAbsenderId` in der Methode ab Zeile 95, 105-108, 149); `FrontendUserController` (59, 103); `FirmaController` (33, 174-205). Tabelle/Spalte bleiben bis Task 13 (`ddl-auto=validate` stört sich nicht an Zusatzspalten).
  - [ ] Nach dem Umbau: `git grep -n "getDokumentMailKonto\|nutztDokumentMailKonto\|getStandardMailKonto\|getMailFromAddress\|getSmtpHost\|fuerDokumentKonto\|archiviereKopie(" src/main/java` darf nur noch `SystemSettingsService`, `SentMailArchiver` (deprecated) und `PostfachMigrationService` zeigen.
  - [ ] Tests: `AnfrageBestaetigungVersandServiceTest` — Zweck leer → kein Versand, `false`, WARN; Zweck `info@muster-handwerk.de` → Absender `info@`, Mail mit `postfachId` gespeichert; Zweck-Signatur gesetzt → deren HTML statt System-Signatur. `AutoMahnVersandServiceTest` — Absender = Zweck-Adresse, ohne Zweck → Lauf meldet Fehlschlag. `EmailControllerTest` — `/send` ohne Zweck → 409; `/dokument-absender` liefert Zweck-Adresse; `/from-addresses` → 404. `LieferantenControllerTest` — Absender aus Standard-Postfach. Firma/FrontendUser-Tests ohne Absender-Felder.
  - [ ] `./mvnw -B test -Dtest='EmailControllerTest,LieferantenControllerTest,AnfrageBestaetigungVersandServiceTest,AutoMahnVersandServiceTest,AutoAuftragsbestaetigung*Test,FirmaController*Test,FrontendUserProfileService*Test,EmailSignatureService*Test'` grün, danach `./mvnw -B -q compile` zusammen mit Task 7 (Integration).

Pfadpräfix Frontend: `F = react-pc-frontend/src`, `E = react-pc-frontend/e2e`.
Alle Frontend-Tasks: vorher `FRONTEND_UI.md` lesen **und** Skill `handwerkerprogramm-design`
aufrufen (Farben rose/slate, Wording, Einstellungs-Kits). Kein Build-Commit (Task 12).

### Task 9 — Frontend Admin: „E-Mail-Postfächer“ und „Automatische Mails“

- **Abhängig von:** Task 5 (API). Parallel zu Task 6/7/8 möglich (gestubbte API im Test).
- **Files:**
  - neu: `F/components/settings/postfachApi.ts` (Typen + Fetch-Helfer, **ohne JSX**), `F/components/settings/sections/PostfaecherSection.tsx`, `F/components/settings/sections/PostfachFormular.tsx`, `F/components/settings/sections/AutomatischeMailsSection.tsx`,
    Tests `F/components/settings/sections/PostfaecherSection.test.tsx`, `F/components/settings/sections/AutomatischeMailsSection.test.tsx`, `E/einstellungen-postfaecher.spec.ts`
  - geändert: `F/components/settings/SystemSetupConfigurator.tsx`, `F/components/settings/SystemSetupConfigurator.test.tsx`, `F/pages/FirstLoginSetupPage.tsx`
  - gelöscht: `F/components/settings/sections/EmailSettingsSection.tsx`
- **Vorbild:**
  - Reiter-Aufbau und Häkchen-Status: `SystemSetupConfigurator.tsx:22-35` (`TABS`), `75-102` (`holeSetupStatus`), `193` (Tab-Inhalt).
  - Formularkarten, Passwortfeld mit „gesetzt“-Anzeige, Testergebnis: `EmailSettingsSection.tsx` (Karten „Ihr Postfach“ Zeile 440, „Postfach für Rechnungen und Mahnungen“ Zeile 560, Test-Handler 287-312/342-372) + Bausteine aus `settingsUi.tsx`.
  - Fehlertexte: `parseErrorMessage` (`settingsApi.ts:20`), Toast-Pflicht (`FRONTEND_UI.md` Abschnitt „Toast-Pflicht bei Fehlern“).
  - E2E mit gestubbter API: `E/kasse-einstellungen.spec.ts`, Helfer `E/hilfen/api.ts`.
- **Interfaces:**
  - **Produces:** TS-Typen spiegeln `PostfachDto` exakt (Namen der Record-Komponenten), z. B.
    ```ts
    export type PostfachArt = 'PERSOENLICH' | 'ALLGEMEIN';
    export type Verschluesselung = 'TLS' | 'STARTTLS';
    export type Versandzweck = 'WEBSITE_ANFRAGE' | 'GESCHAEFTSDOKUMENTE' | 'EINKAUF';
    export interface PostfachUpdate { version: number | null; adresse: string; anzeigename: string; absenderName: string | null; art: PostfachArt; besitzerId: number | null; zugriffNutzerIds: number[]; smtpHost: string; smtpPort: number; smtpUsername: string; smtpTls: Verschluesselung; smtpPassword: string | null; imapEingerichtet: boolean; imapHost: string | null; imapPort: number | null; imapUsername: string | null; imapTls: Verschluesselung | null; imapPassword: string | null; ordnerPosteingang: string; ordnerGesendet: string; importOrdner: { ordnerName: string; richtung: 'IN' | 'OUT' }[]; standardSignaturId: number | null; abrufAktiv: boolean; }
    export const HETZNER_VORLAGE = { smtpHost: 'mail.your-server.de', smtpPort: 465, smtpTls: 'TLS', imapHost: 'mail.your-server.de', imapPort: 993, imapTls: 'TLS' } as const;
    export function ladePostfaecher(): Promise<PostfachResponse[]>; export function speicherePostfach(id: number | null, u: PostfachUpdate): Promise<PostfachResponse>;
    export function loeschePostfach(id: number): Promise<void>; export function testeVerbindung(postfachId: number | null, formular: PostfachUpdate): Promise<Testverbindung>;
    export function ladeStatus(): Promise<PostfachStatus>; export function ladeVersandzwecke(): Promise<VersandzweckEintrag[]>; export function speichereVersandzweck(z: Versandzweck, postfachId: number | null, signaturId: number | null): Promise<VersandzweckEintrag>;
    ```
  - **Consumes:** REST aus Task 5; Nutzerliste `GET /api/frontend-users` (Admin, liefert `id`, `displayName`); Signaturen `GET /api/email/signatures`.
- **Steps:**
  - [ ] `postfachApi.ts` wie oben; alle Fehler mit `parseErrorMessage` in `Error` umwandeln.
  - [ ] `PostfaecherSection`: Kopf mit Status aus `ladeStatus()`: fehlt der Schlüssel → rote Hinweiskarte „Zugangsdaten können nicht sicher gespeichert werden. Bitte den Betreuer bitten, den Schlüssel einzurichten.“, **Speichern-Knöpfe deaktiviert**; Umstellung nicht abgeschlossen → gelber Hinweis. Liste (Tabelle/Karten): Anzeigename, Adresse, Art-Etikett („Persönlich: Max Mustermann“ / „Gemeinsam“), Abruf-Status („Abruf aktiv – zuletzt 14:32“ / „Abruf aus“ / „Nur Senden“), letzter Fehler in Klartext (Code → Text: `AUTHENTIFIZIERUNG_FEHLGESCHLAGEN` = „Anmeldung abgelehnt – Passwort prüfen“, `IMAP_VERBINDUNG` = „Server nicht erreichbar“, `ZUGANG_UNLESBAR` = „Gespeichertes Passwort nicht lesbar“, sonst „Unbekannter Fehler“), Zwecke als kleine Etiketten. Knöpfe „Neues Postfach“, je Zeile „Bearbeiten“, „Löschen“ (eigener Bestätigungsdialog, 409-Text als Toast).
  - [ ] `PostfachFormular` (Seitenleiste/Dialog): Abschnitte „Adresse“ (Adresse, Name in der Liste, Absendername für Kunden), „Wer darf es nutzen?“ (Art als Segment-Schalter „Persönlich“/„Gemeinsam“; Persönlich → Person wählen mit `select-custom`; Gemeinsam → Häkchenliste aller Nutzer), „Postausgang (SMTP)“, „Posteingang (IMAP)“ mit Schalter „Mails aus diesem Postfach abholen“ (aus = alle IMAP-Felder ausgeblendet und `imapEingerichtet=false`), „Ordner“ (Posteingang, Gesendet, weitere Ordner mit Richtung „Eingang“/„Ausgang“ hinzufügen/entfernen), „Standard-Signatur“ (`select-custom`), Schalter „Abruf aktiv“. Knopf **„Hetzner-Vorlage“** füllt nur Host/Port/TLS aus `HETZNER_VORLAGE` (Benutzer-/Passwortfelder unberührt). Knopf **„Verbindung testen“** ruft `testeVerbindung` mit den aktuellen Formularwerten, Ergebnis per `TestResultBanner` („Senden: ok · Abholen: ok“ bzw. Fehlertext). Passwortfelder leer mit Hinweis „gespeichert – leer lassen, um es zu behalten“, wenn `…PasswortGesetzt`. Validierung vor dem Speichern im Client (Pflichtfelder, Ports 1–65535 als Ziffern-String, Adressformat) mit eigener Meldung.
  - [ ] `AutomatischeMailsSection`: drei Karten (Bezeichnung aus API), je Postfach-Auswahl (nur sendefähige Postfächer aus `ladePostfaecher()`; Option „Keins – nicht versenden“) und Signatur-Auswahl (Option „System-Signatur“ = `null`); `warnung` als deutlich sichtbare Warnzeile (rose/amber laut Design-Skill).
  - [ ] `SystemSetupConfigurator.tsx`: `TabId` um `'automatische-mails'` erweitern; Reiter `email` heißt „E-Mail-Postfächer“ und zeigt `PostfaecherSection`, neuer Reiter „Automatische Mails“ (Icon `Send` aus lucide) zeigt `AutomatischeMailsSection`; `holeSetupStatus` nutzt `ladeStatus()` → `status.email = sendefaehigesPostfachVorhanden`, `status['automatische-mails'] = warnungen.length === 0`. Import von `EmailSettingsSection` entfernen, Datei löschen.
  - [ ] `FirstLoginSetupPage.tsx` Zeile 29-37: statt `/api/settings/smtp` `ladeStatus()` → `smtpReady = sendefaehigesPostfachVorhanden`.
  - [ ] Tests (Vitest, `fetch` gemockt, Dummy-Daten): Hetzner-Vorlage setzt genau Host/Port/TLS; Speichern schickt `smtpPassword: null`, wenn Feld leer; Antwort ohne Passwort wird angezeigt als „gespeichert“; Schlüssel fehlt → Speichern deaktiviert + Hinweis; Art „Persönlich“ blendet Häkchenliste aus; Verbindungstest zeigt Ergebnis; Automatische Mails zeigt Warnung für Zweck ohne Postfach und speichert Auswahl. `SystemSetupConfigurator.test.tsx` an neue Reiter anpassen.
  - [ ] E2E `einstellungen-postfaecher.spec.ts` (Stubs für alle Endpunkte oben, Nutzer Max/Erika): Postfach anlegen mit Hetzner-Vorlage → Liste zeigt es; Automatische Mails: Warnung sichtbar, nach Auswahl verschwunden.
  - [ ] `npx vitest run src/components/settings`, `npm run lint`, `npm run build` (Ausgabe verwerfen), `E2E_PORT=<port> npx playwright test e2e/einstellungen-postfaecher.spec.ts` grün.

### Task 10 — Frontend E-Mail-Center: Filterleiste, Postfach-Etikett, Von-Auswahl mit Signaturwechsel

- **Abhängig von:** Task 5 (`/api/postfaecher/*`), Task 7 (Parameter/DTO-Felder), Task 8 (`/api/email/dokument-absender` aus Zweck, Wegfall `/api/email/from-addresses`).
- **Files:**
  - neu: `F/features/email/postfaecher.ts` (Typen + `useMeinePostfaecher(antwortAufEmailId?: number)`), `F/features/email/PostfachFilterLeiste.tsx`, `F/features/email/PostfachEtikett.tsx`, Tests `F/features/email/PostfachFilterLeiste.test.tsx`, `E/email-center-postfaecher.spec.ts`
  - geändert: `F/pages/EmailCenter.tsx`, `F/features/email/emailCenterModel.ts`, `F/features/email/EmailDetailHeader.tsx`, `F/components/EmailComposeForm.tsx`, `F/features/email/emailDraftPersistence.ts`, `F/features/email/useEmailDraft.ts`, `F/pages/BestellungEditor.tsx`,
    Tests `F/pages/EmailCenter.test.tsx`, `F/components/EmailComposeForm.test.tsx`, E2E-Stubs in `E/email-center-compact.spec.ts`, `E/email-center-context.spec.ts`, `E/email-center-layout.spec.ts`, `E/email-draft-attachments.spec.ts`, `E/email-thread-cleanup.spec.ts` (Route `from-addresses` → `postfaecher/meine` + `postfaecher/adressen`)
- **Vorbild:** Ordnerleiste/Knöpfe `EmailFolderSidebar.tsx:60-95`; Absender-Auswahl heute `EmailComposeForm.tsx:557-600` und `1133-1156`; Signatur-Ersetzung im Editor `EmailComposeForm.tsx:799-866` (sucht `.email-signature`); `wrapSignatureHtml` Zeile 155-162.
- **Interfaces:**
  - **Produces:**
    ```ts
    export type PostfachFilter = 'alle' | 'mein' | number;
    export interface MeinPostfach { id: number; adresse: string; anzeigename: string; art: 'PERSOENLICH' | 'ALLGEMEIN'; standardSignaturId: number | null; }
    export function useMeinePostfaecher(antwortAufEmailId?: number): { postfaecher: MeinPostfach[]; standardPostfachId: number | null; laden: boolean };
    // emailCenterModel.ts
    export function ordnerEndpunkt(folder: FolderType, filter: PostfachFilter, offset: number, limit: number): string;
    // EmailItem + postfachId?: number; postfachAdresse?: string; postfachName?: string;
    ```
    `EmailComposeForm` sendet `postfachId` statt `sender`; Entwurf speichert `postfachId`.
  - **Consumes:** Task 5/7/8.
- **Steps:**
  - [ ] `ordnerEndpunkt` in `emailCenterModel.ts` bauen und die drei gleichen `endpointMap`-Blöcke in `EmailCenter.tsx` (Zeile 499-513, 575-589, 684-698) darauf umstellen; `&postfach=` anhängen. Cache-Schlüssel `folderCacheRef` = `${activeFolder}|${filter}`. `loadStats` (Zeile 633) und Suche (1308, 1336) ebenfalls mit `postfach=`.
  - [ ] Filter-Zustand in der URL (`?postfach=`), Standard `alle`. `PostfachFilterLeiste` über der Liste (bei der Suchzeile, ~Zeile 1987): Segment-/Chip-Leiste „Alle Postfächer“, „Mein Postfach“ (nur wenn persönliches existiert), je gemeinsames Postfach ein Eintrag mit Anzeigename (Tooltip = Adresse). Nur ein Postfach sichtbar → Leiste ausblenden.
  - [ ] `PostfachEtikett` in der Listenzeile (`filteredEmails.map`, ~Zeile 2150) und im `EmailDetailHeader`: kleines slate-Etikett mit Anzeigename; ausblenden, wenn Filter bereits auf genau ein Postfach steht.
  - [ ] Eigene Adressen (Zeile 134-150) aus `GET /api/postfaecher/adressen` statt `/api/emails/from-addresses`.
  - [ ] `EmailComposeForm`: `fromAddresses/fromAddress` (239-240) → `postfaecher/postfachId`; Laden per `useMeinePostfaecher(replyEmailId)` — Vorbelegung = `standardPostfachId` (Server wählt bei Antworten das Ursprungs-Postfach, falls erlaubt). Auswahl mit `select-custom`, Beschriftung „Von“, Option-Text `Anzeigename <adresse>`. Beim Wechsel: hat das Postfach `standardSignaturId`, `GET /api/email/signatures/{id}` → `wrapSignatureHtml(html)` und vorhandenes `.email-signature`-Element im Editor ersetzen (sonst einfügen wie beim Laden); ohne Signatur → bisherige Nutzer-Standard-Signatur (`loadSignature`, Zeile 532). Payload (919-933): `postfachId` statt `sender`. Geschäftsdokument-Anzeige (`/api/email/dokument-absender`, 587-600, 1133-1150) bleibt, Text: „Rechnungen und Mahnungen gehen immer über das Postfach aus „Automatische Mails“.“ Kein Postfach verfügbar → Senden-Knopf deaktiviert + Hinweis „Dir ist noch kein Postfach zugeordnet. Bitte an den Admin wenden.“
  - [ ] Entwürfe: Inhalt (451-480) + `emailDraftPersistence.ts`/`useEmailDraft.ts` um `postfachId` erweitern; beim Wiederherstellen `postfachId` übernehmen, wenn noch in der Liste.
  - [ ] `BestellungEditor.tsx` (217-218, 286-300, 391, 597-600): Absender-Auswahl auf `useMeinePostfaecher()` umstellen, `postfachId` mitsenden.
  - [ ] Tests: `PostfachFilterLeiste.test.tsx` (Einträge je nach Postfächern, Auswahl ruft Callback, Ausblenden bei einem Postfach); `EmailCenter.test.tsx` (Endpunkt enthält `postfach=mein` nach Klick; Etikett sichtbar); `EmailComposeForm.test.tsx` (Vorbelegung `standardPostfachId`; Wechsel ersetzt Signatur-Block; Payload enthält `postfachId`, kein `sender`).
  - [ ] E2E `email-center-postfaecher.spec.ts`: Filter „info@“ zeigt nur deren Mails, Etikett in der Liste, neue Mail → Von „Max Mustermann <max.mustermann@…>“, Wechsel auf „info@“ tauscht Signatur. Bestehende E-Mail-Center-Specs auf neue Stubs umstellen und selbst fahren.
  - [ ] `npx vitest run src/features/email src/pages/EmailCenter.test.tsx src/components/EmailComposeForm.test.tsx`, `npm run lint`, `npm run build` (verwerfen), die sechs E-Mail-Specs grün.

### Task 11 — Frontend: „Meine Abwesenheit“ pro Postfach, Absender-Reste in Benutzer/Firma entfernen

- **Abhängig von:** Task 6 (Abwesenheits-API), Task 8 (Wegfall `/api/firma/email-absender`, `emailAbsender` am Profil).
- **Files:** `F/components/EmailSettings.tsx`, `F/pages/BenutzerEditor.tsx`, `F/pages/FirmaEditor.tsx`, Tests neu `F/components/EmailSettings.test.tsx` (falls nicht vorhanden), `E/abwesenheit-postfach.spec.ts`
- **Vorbild:** bestehender Abwesenheitsbereich `EmailSettings.tsx:132-135` (State), `165-190` (Laden/Mapping), `314-352` (Speichern), `819 ff.` (Oberfläche).
- **Interfaces:**
  - **Consumes:** `GET/POST/DELETE /api/email/outofoffice`, `GET /api/email/outofoffice/postfaecher` (Task 6), Antwortform `AbwesenheitDto`.
- **Steps:**
  - [ ] `EmailSettings.tsx`: Abschnitt „Abwesenheitsnotizen“ heißt „Meine Abwesenheit“. `OutOfOfficeBackend` (Zeile 65-74) um `postfachId`, `postfachAdresse`, `postfachAnzeigename`, `postfachGemeinsam` erweitern, `OutOfOfficeEntry` um `postfachId`. Postfach-Auswahl im Bearbeiten-Formular aus `/api/email/outofoffice/postfaecher` (nur sichtbar, wenn mehr als ein Eintrag — also für Admins mit gemeinsamen Postfächern); Liste gruppiert „Mein Postfach“ / „Gemeinsame Postfächer“ mit Postfach-Etikett. Speichern-Payload (322-333) um `postfachId`. Kein persönliches Postfach und nicht Admin → leerer Zustand „Dir ist noch kein persönliches Postfach zugeordnet – Abwesenheit ist erst danach möglich.“ Fehler (404/403) als Toast.
  - [ ] Hinweiszeile für Admins unter dem gemeinsamen Postfach: „Tipp: Auf dem alten Postfach kannst du hier einen Hinweis auf die neue Adresse schalten.“ (Spec Abschnitt 6, Nutzungshinweis).
  - [ ] `BenutzerEditor.tsx`: Feld „E-Mail-Absender“ entfernen (30, 156, 169, 211, 229, 255, 485-490) inkl. Fetch `/api/firma/email-absender`; an seiner Stelle ein Info-Text „Das persönliche Postfach wird unter Einstellungen → E-Mail-Postfächer zugeordnet.“ mit Link `/einstellungen#email`.
  - [ ] `FirmaEditor.tsx`: Bereich „E-Mail-Absender“ vollständig entfernen (Interface 142, State 218-221, Laden 268-280, Speichern 509-552, Löschen 553-560, Reiter/Knopf 641, Modal ab 1409).
  - [ ] Tests: Vitest für `EmailSettings` (Liste mit zwei Postfächern gruppiert; Speichern schickt `postfachId`; leerer Zustand ohne Postfach). E2E `abwesenheit-postfach.spec.ts` mit Stubs.
  - [ ] `npx vitest run src/components/EmailSettings.test.tsx`, `npm run lint`, `npm run build` (verwerfen), eigene Spec grün.

### Task 12 — Integration: Frontend-Build einchecken, Gesamtprüfung, Deploy-Checkliste

- **Abhängig von:** Tasks 1–11 (alle integriert auf dem Feature-Branch).
- **Files:** `src/main/resources/static/**` (Build-Ausgabe PC-Frontend), PR-Beschreibung.
- **Steps:**
  - [ ] `git grep -n "kontoId\|KontoZugang\|MailkontoService\|EmailAbsender\|from-addresses\|settings/smtp\|settings/mail-from\|dokument-mail\|SmtpHtmlMailSender\|ImapAppendService" -- src react-pc-frontend/src react-pc-frontend/e2e` → nur noch erlaubte Treffer (Sachkonto-`kontoId` in Beleg/Kasse, `einkauf_versandauftrag.kontoId`, `SystemSettingsService`-Getter, Migrator). Treffer außerhalb davon an den verantwortlichen Task zurückgeben.
  - [ ] `cd react-pc-frontend && npm ci` (falls nötig) `&& npm run build`; Exit-Code per `$?` prüfen (zsh: `${PIPESTATUS}` gibt nichts). `git add -A src/main/resources/static` (alte Hash-Assets bleiben liegen, `index.html`/`sw.js` neu). Mobile-Frontend ist nicht betroffen — nicht bauen.
  - [ ] Backend lokal mit MySQL-Kopie **ohne** Produktivdaten starten: Migrationslog `[PostfachMigration] … ABGESCHLOSSEN`, `SELECT COUNT(*) FROM email WHERE postfach_id IS NULL` = 0.
  - [ ] Abschnitt „Deploy-Hinweise“ (unten) wörtlich in die PR-Beschreibung übernehmen.
  - [ ] `./graphify update .` einmal am Ende.

### Task 13 — Release 2 (eigener PR): Altlasten entfernen — erst nach geprüfter Produktivmigration

- **Abhängig von:** Release 1 ist produktiv, Migrator lief erfolgreich (Deploy-Hinweis 6 erfüllt). Branch `feature/postfaecher-aufraeumen` von `main` **nach** Merge von Release 1.
- **Files:**
  - neu: `R/db/migration/V{M}__postfach_altlasten_entfernen.sql`, `T/db/PostfachAltlastenMigrationMysqlTest.java`
  - geändert: `J/domain/Email.java`, `J/domain/einkauf/EmailImportIdentitaet.java`, `J/domain/OutOfOfficeSchedule.java` (`@Column(nullable = false)` für `postfachId`), `J/service/SystemSettingsService.java` (deprecated SMTP/IMAP/Dokument-Getter + `@Value`-Defaults Zeile 47-62 entfernen), `J/service/mail/SentMailArchiver.java` (`archiviereKopie(MimeMessage)`, `fuerDokumentKonto()`, private `archiviereKopie(…, ImapZugang, …)` entfernen), `R/application-local.properties.example` + `src/test/resources/application.properties` (Einträge `smtp.*`/`imap.*` entfernen)
  - gelöscht: `J/service/postfach/PostfachMigrationService.java`, `J/config/PostfachMigrationRunner.java` und ihre Tests
- **Steps:**
  - [ ] SQL, Reihenfolge verbindlich:
        1. **Wächter** zuerst: `SET @offen = (SELECT COUNT(*) FROM email WHERE postfach_id IS NULL) + (SELECT COUNT(*) FROM email_import_identitaet WHERE postfach_id IS NULL) + (SELECT COUNT(*) FROM out_of_office_schedule WHERE postfach_id IS NULL) + (SELECT IF(COUNT(*) = 1, 0, 1) FROM system_setting WHERE setting_key = 'postfach.migration.version' AND setting_value = '1');`
           `SET @sql = IF(@offen = 0, 'SELECT 1', 'SELECT postfach_umstellung_nicht_abgeschlossen FROM DUAL');` `PREPARE/EXECUTE` — die unbekannte Spalte lässt Flyway mit sprechendem Namen abbrechen, **bevor** etwas gelöscht wird.
        2. `postfach_id` in `email`, `email_import_identitaet`, `out_of_office_schedule` auf `NOT NULL`.
        3. Index `idx_email_konto_message_id` und Spalte `email.konto_id` löschen; `uk_email_import_identitaet_uid` und `email_import_identitaet.konto_id` löschen (jeweils `information_schema`-geschützt).
        4. `DROP TABLE IF EXISTS einkauf_mailkonto`.
        5. `frontend_user_profile`: FK `fk_frontend_user_email_absender` und Spalte `email_absender_id` löschen, dann `DROP TABLE IF EXISTS email_absender`.
        6. `DELETE FROM system_setting WHERE setting_key IN ('smtp.host','smtp.port','smtp.username','smtp.password','imap.host','imap.port','imap.username','imap.password','mail.from-address','mail.absender-name','smtp.dokumente.aktiv','smtp.dokumente.host','smtp.dokumente.port','smtp.dokumente.username','smtp.dokumente.password','mail.dokumente.from-address','mail.dokumente.absender-name','imap.dokumente.host','imap.dokumente.port')`.
        `einkauf_versandauftrag.konto_id` bleibt (Entscheidung I).
  - [ ] MySQL-Test: Wächter bricht bei einer Mail ohne `postfach_id` ab und lässt `einkauf_mailkonto` stehen; mit vollständigen Daten läuft das Skript zweimal durch.
  - [ ] `git grep` wie in Task 12 darf keine Getter-/Migrator-Treffer mehr liefern. `./mvnw -B test` (Reviewer) grün.

---

## Abhängigkeiten (Übersicht)

| Task | braucht | parallel möglich zu |
| --- | --- | --- |
| 1 Schema/Entities | Einkauf-Merge | — |
| 2 Postfach-Zugang/Transport | 1 | 4 |
| 3 Import | 1, 2 (**gleiches Paket wie 2**) | 4 |
| 4 Migrator | 1 | 2+3, 5 |
| 5 Sichtbarkeit/Verwaltung/Zwecke-API | 1, 2 | 3, 4 |
| 6 Abwesenheit Backend | 2, 3, 5 | 7, 8, 9 |
| 7 E-Mail-Center-Backend | 2, 3, 5 | 6, 8, 9 |
| 8 Versandstellen + `EmailAbsender` raus | 2, 3, 5 | 6, 7, 9 (Integration **zusammen mit 7**) |
| 9 Frontend Admin | 5 | 6, 7, 8, 10, 11 |
| 10 Frontend E-Mail-Center | 5, 7, 8 | 9, 11 |
| 11 Frontend Abwesenheit/Benutzer/Firma | 6, 8 | 9, 10 |
| 12 Integration/Build | 1–11 | — |
| 13 Aufräumen (Release 2) | Release 1 produktiv migriert | — |

## Kopplungs-Hinweise für den Abschnitts-Schnitt

- **Task 2 + 3 sind ein Coding-Paket.** Task 2 löscht `MailkontoService` und ändert
  den Typ von `KontoMailTransport`/`SentMailArchiver`; `EmailImportService` kompiliert erst
  nach Task 3 wieder. Getrennte Agenten würden sich blockieren.
- **Task 3 ↔ Task 8:** Task 3 entfernt die HAUPT-Überladungen in
  `EmailOutboundPersistenceService`; deren einziger fremder Aufrufer ist
  `AnfrageBestaetigungVersandService` (Task 8). Task 3 biegt die zwei Aufrufe nur
  mechanisch um (steht im Task), Task 8 ersetzt sie fachlich. Kein Datei-Konflikt,
  solange Task 8 **nach** Task 3 startet.
- **Task 7 ↔ Task 8:** gleiche Welle, gemeinsam integrieren. Task 8 löscht
  `EmailAbsenderService`, Task 7 entfernt die Nutzung in `UnifiedEmailController`
  und dessen Test. Einzeln bauen beide; erst die Integration beider ist grün.
- **Task 3 ↔ Task 6:** `OutOfOfficeResponder.java` wird in Task 3 **nur** am Record
  `IncomingMail` geändert, Task 6 besitzt die Datei danach. Sequenziell, kein Konflikt.
- **Task 5 erstellt `PostfachExceptionHandler` bereits mit `OutOfOfficeController.class`**
  in `assignableTypes` — Task 6 fasst die Datei nicht an.
- **`PostfachRepository`:** Task 1 liefert alle Abfragen; Task 5 ergänzt nur
  `zaehleEmails`/`zaehleAbwesenheiten`. Andere Tasks ändern die Datei nicht.
- **Frontend-Build:** ausschließlich Task 12.
- Ein Designreview sinnvoll nach Task 9 (Einstellungen) und nach Task 10+11 (E-Mail-Center + Abwesenheit) — oder gemeinsam, wenn 9–11 im selben Abschnitt landen.

## Dateiübersicht (Schreibrechte, für die Disjunktheitsprüfung)

| Datei | Task |
| --- | --- |
| `V{N}__postfach_grundlagen.sql`, `domain/postfach/*`, `PostfachVersandzweckRepository` | 1 |
| `PostfachRepository` | 1 (+5: `zaehleEmails`, `zaehleAbwesenheiten`) |
| `domain/Email.java` | 1 (Feld), 3 (Konto raus), 13 |
| `domain/einkauf/EmailImportIdentitaet.java` | 1, 3, 13 |
| `domain/OutOfOfficeSchedule.java`, `domain/EmailDraft.java` | 1 (13: nur OOO) |
| `service/mail/{PostfachZugang,PostfachKennung,PostfachService,PostfachMailFabrik}.java`, `KontoMailTransport`, `org/example/email/EmailService`, `EinkaufOutboxService`, `EinkaufVersandWorker`, `EinkaufExceptionHandler`, `MailkontoDto`, Löschungen Mailkonto | 2 |
| `SentMailArchiver` | 2 (13) |
| `EmailImportService`, `EmailRepository`, `EmailImportIdentitaetRepository`, `BounceErkennungService`, `EmailOutboundPersistenceService`, `EmailThreadBackfillRunner` | 3 |
| `OutOfOfficeResponder` | 3 (Record), 6 |
| `service/postfach/PostfachMigrationService`, `config/PostfachMigrationRunner` | 4 (13 löscht) |
| `service/postfach/{PostfachSichtbarkeitService,PostfachVerwaltungService,VersandzweckVerwaltungService}`, `dto/Postfach/PostfachDto`, `controller/{PostfachAdminController,VersandzweckController,PostfachController}`, `exception/PostfachExceptionHandler`, `SystemSettingsController` | 5 |
| `SystemSettingsService` | 5, 13 |
| `OutOfOfficeController`, `OutOfOfficeScheduleRepository`, `service/postfach/AbwesenheitService`, `dto/Postfach/AbwesenheitDto`, Löschungen `SmtpHtmlMailSender`/`HtmlMailSender`/`ImapAppendService`, `DokumentFreigabeService` (Kommentar) | 6 |
| `UnifiedEmailController`, `UnifiedEmailDto`, `dto/ProjektEmail/ProjektEmailDto`, `NotificationController`, `EmailDraftDto`, `EmailDraftService`, `EmailDraftController`, `service/postfach/EmailDeduplizierung`, `ProjektManagementService`, `KundenDetailService`, `LieferantenDetailService` | 7 |
| `EmailController`, `LieferantenController`, `dto/Projekt/ProjektEmailDto`, `AnfrageBestaetigungVersandService`, `AutoMahnVersandService`, `AutoAuftragsbestaetigungVersandService`, `ProjektEmailArchivService`, `EmailSignatureService`, `FrontendUserProfile`, `FrontendUserProfileService`, `FrontendUserController`, `FirmaController`, Löschungen `EmailAbsender*` | 8 |
| `components/settings/**`, `pages/FirstLoginSetupPage.tsx`, `e2e/einstellungen-postfaecher.spec.ts` | 9 |
| `pages/EmailCenter.tsx`, `features/email/**`, `components/EmailComposeForm.tsx`, `pages/BestellungEditor.tsx`, `e2e/email-center-*.spec.ts`, `e2e/email-draft-attachments.spec.ts`, `e2e/email-thread-cleanup.spec.ts`, `e2e/email-center-postfaecher.spec.ts` | 10 |
| `components/EmailSettings.tsx`, `pages/BenutzerEditor.tsx`, `pages/FirmaEditor.tsx`, `e2e/abwesenheit-postfach.spec.ts` | 11 |
| `src/main/resources/static/**` | 12 |

## Deploy-Hinweise (Release 1)

1. **Vorher** `mail.credentials.encryption-key` in `application-local.properties` setzen
   (genau 16, 24 oder 32 Zeichen, `MailSecretService.isConfigured`). Den Schlüssel
   **sicher außerhalb des Servers aufbewahren** — geht er verloren, sind alle
   gespeicherten Postfach-Passwörter unlesbar. Fehlt er beim Start, führt der Migrator
   nichts aus (`SCHLUESSEL_FEHLT`), und bis zum Neustart mit Schlüssel wird **keine
   Mail abgeholt oder verschickt**.
2. Datenbank-Backup direkt vor dem Deploy.
3. `app.startup-maintenance.enabled` muss `true` sein (Standard), sonst läuft der Migrator nicht.
4. Nach dem Start im Log: `[PostfachMigration] … ABGESCHLOSSEN`. In **Einstellungen →
   E-Mail-Postfächer**: „Altes Postfach (t-online)“, Art „Gemeinsam“, **Abruf aktiv**,
   „zuletzt abgeholt“ springt innerhalb von ~1 Minute. Der t-online-Abruf **bleibt aktiv**
   (Adresse steht noch auf Flyern); der Admin schaltet ihn später selbst ab.
5. **Einstellungen → Automatische Mails:** alle drei Zwecke prüfen. Ohne Einkaufskonto
   bleibt `EINKAUF` bewusst leer (Warnung sichtbar).
6. Prüfabfragen (Voraussetzung für Release 2):
   `SELECT COUNT(*) FROM email WHERE postfach_id IS NULL;`,
   `SELECT COUNT(*) FROM email_import_identitaet WHERE postfach_id IS NULL;`,
   `SELECT COUNT(*) FROM out_of_office_schedule WHERE postfach_id IS NULL;` → alle `0`;
   `SELECT setting_value FROM system_setting WHERE setting_key='postfach.migration.version';` → `1`.
7. Danach `smtp.password`/`imap.password` (und ggf. `smtp.*`/`imap.*`) aus
   `application-local.properties` entfernen — die Datei ist sonst weiter eine
   Klartext-Quelle (Property-Fallback in `SystemSettingsService`, Zeile 47-62).
   Die Klartexte in `system_setting` hat der Migrator bereits geleert.
8. Hetzner-Umstieg danach im Admin-UI: Postfächer bei Hetzner anlegen (extern),
   im ERP je Postfach „Hetzner-Vorlage“ → Zugangsdaten → „Verbindung testen“ →
   Zugriffe → Speichern; dann in „Automatische Mails“ `WEBSITE_ANFRAGE` auf `info@…`,
   `GESCHAEFTSDOKUMENTE` auf das Rechnungs-Postfach, `EINKAUF` auf `einkauf@…` legen.
   SPF/DKIM der eigenen Domain müssen bei Hetzner eingerichtet sein, sonst landen
   Rechnungen im Spam.
9. Release 2 (Task 13) erst deployen, wenn Punkt 6 erfüllt ist; sonst bricht Flyway
   dort absichtlich mit `postfach_umstellung_nicht_abgeschlossen` ab.

## Risiken und offene Rückfragen an den Nutzer

- **R1 Voraussetzung:** Einkaufs-Branch noch nicht auf `main` — Start blockiert.
- **R2 Dokumente-Postfach Abruf (entschieden):** Der Migrator übernimmt den bisherigen
  Abrufstatus des Dokumente-Kontos, damit Antworten an die Rechnungsadresse nach dem Deploy
  weiter im ERP erscheinen (Spec 6.7 entsprechend korrigiert, 23.09.2026).
- **R3 Zugriff nach Migration:** Entscheidung G gibt allen Nutzern Zugriff auf alle
  migrierten Postfächer (heutiges Verhalten). Wer das einschränken will, tut es danach im Admin-UI.
- **R4 Rechnungsversand im E-Mail-Center:** Mails mit Geschäftsdokument gehen immer über
  den Zweck `GESCHAEFTSDOKUMENTE` (wie heute bei aktivem Dokumente-Konto) — die
  Von-Auswahl ist dort gesperrt.
- **R5 H2-Einzelplatzversion:** Hibernate `update` entfernt die alte Pflichtspalte
  `email.konto_id` nicht; der Migrator lockert sie (Task 4). Ohne diesen Schritt
  schlagen dort alle neuen Mails fehl.
- **R6 Testcontainers:** Migrationstests (Task 1, 4, 13) brauchen Docker.

# ⏸ HIER GEHT ES WEITER

Stand 23.09.2026: Pipeline `loese-problem` nach Schritt 3 (Grobplan) auf Wunsch des Nutzers angehalten.
Spec, Issue #168 und dieser Plan liegen auf `main`.

Nächste Schritte beim Wiederaufnehmen:
1. Voraussetzung prüfen: `codex/beschaffung-konzept` ist auf `main` gemergt. Sonst nicht starten.
2. Zeilenangaben in diesem Plan beziehen sich auf `origin/codex/beschaffung-konzept@486ee800`. Nach dem Merge kurz gegenprüfen.
3. Migrationsnummern `V{N}` auf die nächste freie Nummer nach dem Merge setzen.
4. Weiter mit Schritt 4 (`loese-problem-parallelplan`): Feature-Branch `feature/postfaecher-pro-nutzer`, Kontext-Log, Baseline, Worktrees.
   Hinweise zur Kopplung stehen im Plan: Tasks 2+3 in ein Paket, Tasks 7+8 im selben Abschnitt integrieren. Task 13 ist ein eigener PR (Release 2).
Es existieren noch keine Feature-Worktrees oder -Branches für die Umsetzung.
