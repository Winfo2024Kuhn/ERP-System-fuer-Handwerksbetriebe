# E-Mail-Einordnung: Spam und Newsletter mit einem Besitzer, Belegen und Lernen aus Urteilen

Stand: 31.10.2026 · Status: freigegeben (Brainstorming), gegengelesen gegen origin/main d801608f,
Umsetzung in vier PRs

## Ziel

Keine echte Mail darf verloren gehen. Seit #375 verschiebt das ERP Spam auch auf dem
IMAP-Server. Ein Irrtum der Erkennung trifft damit Posteingang, Handy und Outlook. Die
Spam- und Newsletter-Erkennung wird deshalb von Grund auf neu geschnitten:

- **Ein Besitzer** für den Einordnungs-Zustand einer Mail statt über 30 Schreibstellen.
- **Belege statt Punkte:** Prüfer stellen fest, eine reine Entscheidungsregel entscheidet.
- **Folgen entkoppelt:** Server-Abgleich, Glocke und Ausblenden lesen den Zustand. Pflichtfolgen
  entstehen in derselben Transaktion.
- **Nichts wird automatisch gelöscht**, auch der Papierkorb nicht.
- **Lernen nur aus Menschen-Urteilen.** Der Lernfilter ist jederzeit aus den Urteilen neu
  berechenbar.
- **Messstand:** Jede Regeländerung wird vor dem PR blind gegen eine Prod-Kopie gemessen.

## Ausgangslage (Code-Stand origin/main d801608f)

Bestandsaufnahme vom 31.10.2026:

- **Schreibstellen:** `isSpam`, `isNewsletter`, `userSpamVerdict` und `spamScore` werden geschrieben in:
  - `UnifiedEmailController`: mark-spam, mark-not-spam, mark-not-newsletter,
    confirm-newsletter, bulk/move-to-folder, DELETE permanent, block-sender und das private
    implizite HAM bei Zuordnung und Antwort.
  - `EmailImportService`: List-Unsubscribe, Lieferanten-Bereinigung.
  - `SpamFilterService.analyzeAndMarkSpam`.
  - `EmailCleanupService`: 85er-Regel vor dem Löschen, implizites HAM nach 2 Monaten.
- **`deletedAt`** schreibt nur der Controller (Löschen, Verschieben, Wiederherstellen).
- **`SpamFilterService`** verrechnet Wortpunkte, Signalpunkte, Auffälligkeiten und Blacklist (100)
  zu einem Regel-Score und mischt ihn mit dem Bayes-Wert (40/60).
- **`SpamBayesService`** zählt Tokens inkrementell (`train`/`untrain`). Rund zwei Drittel seines
  Wissens stammen aus einem englischen SMS-Korpus (10.257 Spam / 22.364 Ham). Aus echten Mails hat
  er nur 7 Spam- und 720 Kein-Spam-Urteile (5 von Hand, 715 still).
- **Löschwege:**
  - `EmailCleanupService` löscht Papierkorb, Spam und Newsletter nach 30 Tagen endgültig, in der
    Datenbank und auf dem Mail-Server.
  - „Endgültig löschen“ und „Absender sperren“ löschen hart auf dem Server, „Absender sperren“
    auch zugeordnete Mails.
  - Der Import verwirft künftige Mails gesperrter Absender.
- **`ServerSpamAbgleichService`** verschiebt `isSpam=true` alle zwei Minuten in den Spam-Ordner des
  Servers. Mails, die nicht mehr spamverdächtig sind und die das ERP verschoben hat, holt er zurück.
- **Widersprüche, unter anderem:**
  - Zugeordnete Mails behalten Spam-Kennzeichen.
  - Die 85er-Regel übergeht „Kein Spam“.
  - Newsletter-Aktionen trainieren und setzen Felder unterschiedlich.
  - Lieferanten werden an drei Stellen erkannt.
  - `userSpamVerdict` ist zugleich Handurteil und Trainingsmarker.
  - Backfill (`EmailBackfillEventListener`), `scan-assignments` und `findTaxAdvisorCandidates`
    ordnen auch Spam zu.
  - Wiederherstellen aus dem Papierkorb zählt als „Kein Spam“.

Blind-Test gegen die Prod-Kopie vom 31.10.2026 (Prototyp-Branch `claude/spam-stufen-und-glocke`):
Der Lernfilter gab 279 zugeordneten, also sicher echten Mails einen Wert von mindestens 0,99. Der
Lernfilter darf deshalb nie allein entscheiden.

## Entscheidungen

| Frage | Entscheidung |
| --- | --- |
| Aufbewahrung | Nichts automatisch löschen. Spam, Newsletter und Papierkorb werden nach 30 Tagen (ab Einordnung bzw. Löschzeitpunkt) nur ausgeblendet. Die Suche findet sie weiter. |
| Mail-Server | Das ERP löscht auf dem Server nie automatisch. Im ERP Gelöschtes bleibt auf dem Server liegen, wo es ist. Eine Verschiebung in den Server-Papierkorb gibt es nicht: Viele Anbieter leeren ihn selbst, und die Mail wäre weg. |
| Lernen | Nur aus Menschen-Urteilen (Hand) und stillen Urteilen (Antwort, Zuordnung von Hand, 2 Monate unauffällig im Posteingang). Nie aus eigenen automatischen Entscheidungen. Der SMS-Korpus entfällt. |
| Newsletter | Gleicher Kern wie Spam (eine Einordnung je Mail). Die Newsletter-Erkennung selbst bleibt fachlich unverändert. Newsletter werden nie auf dem Server verschoben. |
| Schutz | Zugeordnete Mails, bekannte Kunden, Lieferanten und Steuerberater, Absender, denen wir geschrieben haben, und Antworten auf eigene Mails werden nie Spam. |
| Absender sperren | Verschiebt bestehende und künftige Mails des Absenders nach Spam (Hand). Von Hand zugeordnete Mails bleiben, wo sie sind. Es wird nichts gelöscht, auch nicht beim Import. „Kein Spam“ auf eine Mail des Absenders hebt die Sperre auf. |
| Vielleicht Spam | Bleibt im Posteingang mit Hinweis, klingelt in der Glocke, bekommt keine Abwesenheitsnotiz. |
| Gefährlicher Anhang | Ein ausführbarer Anhang zeigt immer mindestens „Vielleicht Spam“, auch bei geschützten Mails. |
| Mehrere Postfächer | Wie seit #375: Eine Spam-Einordnung gilt für die Mail in allen Postfächern, in denen sie liegt. |
| KI | Kommt später als optionaler Prüfer (eigener Spec), nicht Teil dieses Umbaus. |
| Bisheriger Branch | `claude/spam-stufen-und-glocke` wird nicht gemergt, sondern als Steinbruch genutzt. |
| **Offen: „Endgültig löschen“** | Der Knopf im Papierkorb löscht heute im ERP und auf dem Server. Empfehlung: Er entfällt, der Papierkorb ist Endstation. Die Entscheidung des Nutzers steht vor PR 1 aus. Bleibt der Knopf, löscht er weiter von Hand mit Rückfrage auf dem Server, damit der Import die Mail nicht neu holt. |

## Design

### 1. Datenmodell: die Einordnung

Eingebettetes Objekt `@Embeddable Einordnung` an `Email`. Es bekommt neue Spalten, die Migration
erhält die nächste freie Nummer.

| Feld | Typ | Bedeutung |
| --- | --- | --- |
| `kategorie` | Enum `EinordnungKategorie`: POSTEINGANG, NEWSLETTER, SPAM; leer = noch nicht eingeordnet | Ablage der Mail |
| `verdacht` | Enum, leer oder SPAM | „Vielleicht Spam“ bei den Kategorien POSTEINGANG und NEWSLETTER |
| `quelle` | Enum `EinordnungQuelle`: AUTOMATISCH, HAND, SCHUTZ | Wer entschieden hat. Die Automatik überschreibt HAND und SCHUTZ nie. |
| `belege` | Text, Kürzel kommagetrennt | Anzeige „Warum?“ und Messstand |
| `eingeordnetAm` | Zeitstempel | Ändert sich nur bei einem Wechsel der Kategorie. Grundlage für das Ausblenden nach 30 Tagen. |
| `regelVersion` | Zahl | Version der Entscheidungsregel, mit der automatisch entschieden wurde |

- **Ein Besitzer, technisch erzwungen:** `Einordnung` hat keine öffentlichen Setter, die
  Mutatoren sind paketintern im Einordnungs-Paket. Die Spalten `is_spam` und `is_newsletter`
  entfallen samt ihren Settern. `Email.isSpam()` und `isNewsletter()` bleiben als abgeleitete
  Getter, alle Abfragen lesen `einordnung.kategorie`. Damit gibt es nur eine Quelle der Wahrheit,
  und kein JPQL-Update und kein Setter kann sie umgehen.
- **Gelöscht** bleibt ein eigener Zustand neben der Kategorie (`deletedAt`) und gehört ebenfalls
  dem Kern.
- **Migration des Bestands** (idempotent, zweiter Lauf über `DO $$ … IF EXISTS column … $$`):
  - `user_spam_verdict` SPAM → Kategorie SPAM, Quelle HAND.
  - HAM → POSTEINGANG bzw. NEWSLETTER (je nach `is_newsletter`), Quelle HAND.
  - Zugeordnete Mails (Projekt, Anfrage, Lieferant, Steuerberater) → POSTEINGANG, Quelle SCHUTZ.
  - Alle übrigen Mails übernehmen ihre heutige Ablage mit Quelle AUTOMATISCH.
  - `eingeordnet_am = COALESCE(processed_at, sent_at, now())`.
- **`spam_urteil`** entsteht schon in PR 1 und wird aus `user_spam_verdict` befüllt:
  - SPAM und HAM werden zu Quelle HAND.
  - HAM_IMPLICIT wird zu Quelle STILL.
  - Die alte Spalte und der alte Lernfilter bleiben bis PR 4 lesbar und werden erst dort
    entfernt.
- **Entfallen:** `spamScore`, `bayesScore` und das 85er-Kriterium.

### 2. Der eine Besitzer: `EinordnungService`

| Befehl | Wann | Wirkung |
| --- | --- | --- |
| `ordneAutomatischEin(Email)` | Einmal je Import-Pfad, **nach** allen automatischen Zuordnungen (auch für Mails, die der Parent zuordnet, und für Steuerberater-Mails) | Wirkt nur bei leerer Quelle oder Quelle AUTOMATISCH ohne Kategorie. Schlägt die Bewertung fehl, bleibt die Kategorie leer, und ein Nachlauf versucht es erneut. Bereits eingeordnete Mails werden nie neu bewertet (`reprocessSpam` und `scan-spam` entfallen). |
| `entscheideVonHand(Email, Kategorie, Nutzer)` | Alle Knöpfe (Tabelle unten) | Setzt Kategorie, Quelle HAND und gegebenenfalls ein Urteil |
| `schuetze(Email, Schutzgrund)` | Zuordnung von Hand zu Projekt, Anfrage, Lieferant oder Steuerberater, unsere Antwort an den Absender, neuer Kontakt (siehe unten) | POSTEINGANG mit Quelle SCHUTZ, stilles Urteil KEIN_SPAM, wenn es eine Handlung war |
| `loesche(Email)` / `stelleWiederHer(Email)` | Papierkorb | Setzt bzw. leert `deletedAt`. Wiederherstellen erzeugt **kein** Urteil und ändert die Kategorie nicht. |
| `sperreAbsender(Email, Nutzer)` | „Absender sperren“ | Sperre eintragen und alle Mails des Absenders außer SCHUTZ-Mails von Hand auf SPAM (HAND) setzen. Eine Mail wird zum Urteil SPAM. |

**Knöpfe → Kategorie → Urteil**

| Knopf | Neue Kategorie | Urteil in `spam_urteil` |
| --- | --- | --- |
| Ist Spam (einzeln, mehrfach, Verschieben nach Spam) | SPAM | SPAM, HAND |
| Kein Spam (aus Spam oder bei Verdacht) | POSTEINGANG, Verdacht weg, Sperre des Absenders aufgehoben | KEIN_SPAM, HAND |
| Verschieben in den Posteingang aus Spam | wie „Kein Spam“ | KEIN_SPAM, HAND |
| Ist Newsletter / Newsletter bestätigen | NEWSLETTER | keins (Newsletter sind gewollte Werbung und würden den Lernfilter verwässern) |
| Kein Newsletter | POSTEINGANG | keins |
| Wiederherstellen aus dem Papierkorb | unverändert | keins |

**Transaktionen:** Kategorie, Löschzustand, Sperre und Urteil schreibt der Befehl in **derselben
Transaktion**. Das Ereignis `EinordnungGeaendert(emailId, alt, neu, quelle, nutzer)` folgt erst
nach dem Commit und nur für Nachgelagertes ohne Pflicht, zum Beispiel den Anstoß zum Neuaufbau des
Lernfilters. Der Server-Abgleich liest weiter den Zustand per Abfrage und braucht kein Ereignis.

**Neuer Kontakt:** Wird eine Adresse als Kunde, Lieferant oder Steuerberater eingetragen, ruft
der Kern für deren Mails mit Kategorie SPAM und Quelle AUTOMATISCH `schuetze` auf. Diese
Neubewertung geht nur Richtung Posteingang und ist deshalb ungefährlich. So repariert man einen
Fehlalarm am häufigsten.

**Durchsetzung:** Das Modell (§1) macht Schreiben außerhalb des Pakets unmöglich. Ein
Architektur-Test im Stil von `PdfWriterFabrikTest` lässt den Build zusätzlich scheitern, wenn
außerhalb des Kerns `setDeletedAt` aufgerufen wird oder eine Abfrage die Einordnungsspalten
setzt.

### 3. Erkennung: Prüfer und Entscheidungsregel

- **`MailMerkmale`:** unveränderlicher Blick auf die Mail, einmal je Bewertung erstellt. Enthält
  Absender, Anzeigename, Antwortadresse, Domain, Betreff, sichtbaren Text ohne HTML, Anhänge,
  DMARC-Ergebnis, `X-Spam-Flag`, Antwort-Kennung und Empfänger. Fehlende Header (Anzeigename,
  `X-Spam-Flag`) zieht der Import nach.
- **`Kontaktwissen`:** schmale Schnittstelle mit den Fragen: Kunde? Lieferant? Steuerberater?
  Haben wir dieser Adresse geschrieben? Bezieht sich die Mail auf eine eigene Message-ID? Ist der
  Absender gesperrt? Eine einzige Implementierung ersetzt die drei heutigen Lieferanten-Prüfungen.
  „Geschrieben an“ nutzt einen Index bzw. eine Adressliste statt `LIKE` auf `recipient`.
- **Prüfer** (Schnittstelle `EinordnungsPruefer`) liefern `Beleg(art, staerke, kuerzel)`:

| Prüfer | Art | Belege (Stärke) |
| --- | --- | --- |
| Schutz | SCHUTZ | bekannter Kontakt, geschrieben an, Antwort auf eigene Mail |
| Nutzerregel | NUTZER | Absender gesperrt |
| Absender | ABSENDER | Name gefälscht (stark), Antwort umgeleitet (schwach), DMARC-Fehler (schwach), Anbieter-Spam (schwach) |
| Struktur | STRUKTUR | ausführbarer Anhang (stark), Makro-Datei (schwach), nur Bild + Link (schwach), Datum fehlt (schwach), Wegwerf-Adresse (stark) |
| Inhalt | INHALT | Spam-Wörter als ganze Wörter im sichtbaren Text (schwach), Lernfilter (stark ab ≥ 0,95, sonst schwach ab ≥ 0,60) |
| Newsletter | NEWSLETTER | heutige Erkennung (List-Unsubscribe, Absender- und Betreff-Merkmale) |

- **`AbzockeErkennung`** ist ein eigener Prüfer. Er setzt nur `warnhinweis` und ändert die
  Kategorie nie. Er läuft für jede Eingangsmail, auch für zugeordnete und geschützte.
- **`Entscheidungsregel`** (reine Funktion, versioniert, als Fall-Tabelle getestet):
  1. Absender gesperrt → SPAM. Die Sperre ist der jüngste ausdrückliche Wille des Nutzers und
     geht deshalb dem Kontakt-Schutz vor.
  2. Schutz-Beleg → POSTEINGANG. Verdacht SPAM nur bei einem ausführbaren Anhang oder bei
     Kontakt-Schutz mit DMARC-Fehler (gefälschte Lieferanten-Rechnung). Eine geschützte Mail wird
     nie SPAM.
  3. SPAM, wenn alle drei Bedingungen erfüllt sind:
     - Es gibt Belege aus mindestens zwei verschiedenen Arten (ABSENDER, STRUKTUR, INHALT).
     - Mindestens einer davon ist stark.
     - Mindestens einer ist nicht INHALT.
  4. Newsletter-Beleg → NEWSLETTER. Verdacht nur bei einem starken Beleg. Zwei schwache Belege wie
     „nur Bild + Link“ und eine abweichende Antwortadresse sind bei Newslettern normal.
  5. Verdacht SPAM bei einem starken Beleg oder zwei schwachen → POSTEINGANG mit Verdacht.
  6. Sonst POSTEINGANG.
- **Wann der Lernfilter Belege liefert:** erst ab 30 Spam-Urteilen von Hand und 30
  Kein-Spam-Urteilen. Stille Kein-Spam-Urteile fließen höchstens im Zehnfachen der Spam-Urteile ein
  (Zufallsstichprobe mit festem Startwert). Sonst würden Hunderte stille Urteile im Monat den
  Filter nie aktiv werden lassen.

### 4. Folgen

| Folge | Verhalten |
| --- | --- |
| Mail-Server | Der Abgleich (alle 2 Minuten) verschiebt Kategorie SPAM in den Spam-Ordner. Ausnahme ist Quelle SCHUTZ, die doppelt gefiltert wird, auch Steuerberater. Was nicht mehr SPAM ist, holt der Abgleich zurück. Löschen im ERP bewegt nichts auf dem Server. |
| Ausblenden | Spam- und Newsletter-Ordner zeigen Einordnungen der letzten 30 Tage, der Papierkorb Löschungen der letzten 30 Tage. Die Frist steckt in der Abfrage selbst und gilt gleich für Liste, Zähler, Glocke und „alle gelesen“. Indizes auf `(kategorie, eingeordnet_am)` und `deleted_at`. Der nächtliche Löschlauf entfällt. Der Ordnerhinweis erklärt das. Die Suche findet ausgeblendete Mails weiter. |
| Glocke | Spam und Newsletter klingeln nie, „Vielleicht Spam“ klingelt. |
| Abwesenheitsnotiz | Läuft weiter im Import, nach dem Einordnen. Nicht an Spam, Newsletter und „Vielleicht Spam“. |
| Automatische Zuordnung, Backfill, `scan-assignments`, Steuerberater-Kandidaten, Anfrage-Erkennung, KI-Einordnung | Überspringen Kategorie SPAM. Eine automatische Zuordnung würde sonst Anhänge verarbeiten und eine Spam-Mail zur Lieferantenpost machen. |
| Lern-Urteile | Handentscheidungen und stille Urteile schreiben in `spam_urteil` (siehe §2). |
| Oberfläche | Ein gemeinsamer Satz Knöpfe für Spam und Newsletter über `entscheideVonHand`. Hinweiszeile „Vielleicht Spam“ mit Gründen, Warnhinweis für Abzocke. Bestätigung „Absender sperren“ sagt „werden nach Spam verschoben“ statt „werden gelöscht“. |

### 5. Lernen aus Urteilen

- **Tabelle `spam_urteil`:** eine gültige Zeile je Mail mit `urteil` (SPAM/KEIN_SPAM), `quelle`
  (HAND/STILL) und `erstellt_am`. Die Zeile hängt an der Mail und fällt mit ihr weg. Sie ist die
  einzige Wahrheit für den Lernfilter.
- **Der Lernfilter ist abgeleitet:**
  - Zerleger auf `MailMerkmale`, Robinson/Fisher als Rechenkern.
  - Neuaufbau aus allen Urteilen beim Start, nachts und gebündelt nach neuen Urteilen. Der Aufbau
    läuft im Hintergrund, das fertige Modell ist unveränderlich und wird über `AtomicReference`
    getauscht.
  - Solange kein Modell bereit ist, wird ohne Lernfilter entschieden. Das ist die sichere
    Richtung.
  - Kein `train`/`untrain`, keine Zerleger-Versionen.
- **Entfallen:** `spam_token_count`, `spam_model_stats`, `user_spam_verdict` und der CSV-Import
  (`POST /spam-model/bootstrap`).

### 6. Messstand und Tests

- **`EinordnungMessstand`:** ein Test im Repo, der nur mit `-Dmessstand.db.url=…` gegen eine lokale
  Prod-Kopie (`scripts/prodkopie`) läuft.
  - Er liest nur, die Transaktion wird zurückgerollt.
  - Er bewertet jede beurteilte Mail blind. Leave-one-out: Die eigenen Tokens werden von den
    Zählern abgezogen.
  - Er gibt nur Zahlen aus: Urteil × neue Einordnung, Häufigkeit je Beleg, Regel-Version.
- **Qualitätstor:** Jede Änderung an Prüfern oder Entscheidungsregel braucht vor dem PR einen
  Messstand-Lauf mit 0 Fehlalarmen auf Kein-Spam-Urteilen (HAND und STILL) und auf zugeordneten
  Mails. Das Ergebnis steht in der PR-Beschreibung. Festgehalten in `TESTING_SECURITY.md` und im
  Review-Ablauf.
- **Automatische Tests:**
  - Fall-Tabelle der Entscheidungsregel und Tests je Prüfer.
  - Architektur-Test.
  - Migrationstest (Altbestand inkl. HAM_IMPLICIT, zweiter Lauf).
  - MockMvc für alle Einordnungs-Endpunkte inkl. 401/403/CSRF.
  - Vitest für Hinweise und Knöpfe.
  - Playwright für „Vielleicht Spam“, Ausblenden und Glocke.

## Umsetzung

| PR | Inhalt | Ergebnis |
| --- | --- | --- |
| 1. Kern | `Einordnung` (Modell ohne Setter, Migration inkl. `spam_urteil`), `EinordnungService` mit allen Befehlen und Ereignis. Alle Schreiber umgestellt, auch `deletedAt`, Backfill, `scan-assignments` und Import-Reihenfolge. Architektur-Test. Löschlauf, 85er-Regel und harte Löschungen beim Sperren entfallen, der Import verwirft nichts mehr. Die Erkennung läuft über einen Übergangs-Prüfer: `SpamFilterService` wird zu einem reinen `bewerte()`, dessen Ergebnis in Belege übersetzt wird. | Gleiche Erkennung, ein Besitzer, kein Löschen |
| 2. Folgen und Oberfläche | Ausblenden statt Löschen (Abfragen, Zähler, Indizes), Glocke, Abwesenheitsnotiz, Schutz bei neuem Kontakt, gemeinsame Knöpfe für Spam und Newsletter, „Absender sperren“ samt Aufheben, Ordnerhinweise, Info-Buttons. | Verhalten für Nutzer stimmig |
| 3. Prüfer, Entscheidungsregel, Messstand | Prüfer aus dem Steinbruch (`SpamSignale`, `AbsenderFaelschungErkennung`, `AbzockeErkennung`, `MailDomain`, `Textsuche`), `Kontaktwissen`, Entscheidungsregel, Messstand, Hinweiszeile „Vielleicht Spam“. Übergangs-Prüfer und alte Punkte-Logik entfallen. | Neue Erkennung mit Messstand-Nachweis |
| 4. Lernen | Abgeleiteter Lernfilter aus `spam_urteil`. Rückbau von Zählertabellen, `user_spam_verdict` und CSV-Import. | Lernfilter aus echten Urteilen |

Jeder PR startet von `main`, und es läuft immer nur ein PR gleichzeitig in der CI.
`scripts/prodkopie/abschotten.sql` ist nicht betroffen, denn es gibt keine neue Außenverbindung.

## Außerhalb dieses Umbaus

- KI-Prüfer (Jev, Ollama, Gemini/Mistral): eigener Spec, Datenschutz (AV-Vertrag) klären.
- Rückmeldung vom Server: Zieht jemand eine vom ERP verschobene Mail am Handy zurück in den
  Posteingang, erfährt das ERP es heute nicht. Später erkennen und als „Kein Spam“ von Hand werten.
- Optionale Verschiebung von Gelöschtem in einen eigenen Server-Ordner („Gelöscht (ERP)“), je
  Postfach zuschaltbar und nur für Löschungen nach einem Stichtag.
- Weil nichts mehr gelöscht wird, wachsen Datenbank und `uploads/` (Anhänge von Spam) dauerhaft.
  Das ist bewusst so entschieden. Eine Aufräumfunktion von Hand kann später kommen.
- Eine eigene Ansicht „Ältere Spam-Mails“ ist nicht geplant.
