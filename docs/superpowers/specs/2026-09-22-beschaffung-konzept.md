# Beschaffung für Handwerksbetriebe – Konzept und fachliche Spec

Stand: 23.09.2026. Status: **Konzept zur Abstimmung, keine Implementierungsfreigabe**.

## 1. Ziel und bestätigte Entscheidungen

Der Einkauf bildet einen nachvollziehbaren Ablauf ab: **Bedarf → Lieferanten anfragen → Angebote vergleichen → Bestellung → Lieferung und Unterlagen**. Ein Handwerker erkennt jederzeit, was noch fehlt, wer geantwortet hat und welche Entscheidung als Nächstes ansteht.

Vom Nutzer vorgegeben:

- Den alten Branch `feature/en1090-echeck` nur als Referenz nutzen. Kein Merge; geeignete Ideen schrittweise auf dem aktuellen Programm aufbauen.
- Bedarf in Anfragen an mehrere Lieferanten übernehmen; Antworten automatisch zuordnen; KI soll beim Angebotsvergleich unterstützen.
- Ein zusätzliches Postfach `einkauf@bauschlosserei-kuhn.de` über Einstellungen anbinden, einschließlich Passwort, Versand und Empfang.
- Standardtexte im vorhandenen E-Mail-Textvorlageneditor pflegen. Nummern müssen in Betreff und Nachricht verfügbar sein.
- **Getrennte Anfragenummer und anschließende Bestellnummer** – ausdrücklich am 22.09.2026 bestätigt.
- Im Einkauf durchgängig die eigene interne Artikelnummer verwenden. Derselbe Artikel kann bei mehreren Lieferanten angefragt werden.
- Für Stahlbau geeignete Positionen einschließlich Profilen und mit anfragbaren Werkstoffzeugnissen.
- Jetzt Konzept und Spec mit Zuordnung zu bestehenden beziehungsweise ergänzenden Issues erstellen.

Weitere Regeln in diesem Dokument sind konkrete Konzeptvorschläge zur Abstimmung, keine Behauptung über bereits fertige Funktionen.

Die vom Nutzer nachgereichte externe Einschätzung wurde gegen den Quellcode geprüft. Daraus ergänzte Funktionen sind ebenfalls Konzeptvorschläge. Insbesondere Direktbestellung, HiCAD-Import und einfache Lagerentnahme sind empfohlene Erweiterungen, noch keine separat bestätigten Produktentscheidungen.

## 2. Befund im aktuellen Programm und im Referenzbranch

Untersuchte lokale Stände:

- `main`: `28d778d44ebbfac86cc5953094e6be0bad8f973a`
- `feature/en1090-echeck`: `255cd2862fdf01a3588d4afa6652f3abdd5431e0`
- GitHub-Issues und zugehörige Kommentare am 22.09.2026 live gelesen.

| Bereich | Befund | Konsequenz |
| --- | --- | --- |
| Navigation auf main | Einkauf mit Bestellungen und Bedarf vorhanden | Vorhandenen Bereich erweitern |
| Bedarf auf main | `BestellungService` arbeitet mit `ArtikelInProjekt` und einem Bestellt-Flag | Mengen- und Vorgangsmodell ergänzen; Flag allein reicht für Teilmengen nicht |
| Artikel auf main | Eigene `Artikel.artikelnummer`, Werkstoff-/Profilmerkmale und Lieferantenpreise vorhanden | Artikelstamm wiederverwenden |
| Einkaufsmapping auf main | Offene Positionen verwenden noch `getExterneArtikelnummer()` | Interne Nummer ausdrücklich in DTOs, Ansicht, E-Mail und PDF übernehmen |
| Mailvorlagen auf main | Zentraler Editor und `EmailTextTemplateService` rendern Betreff und HTML mit Platzhaltern | Einkauf als eigene Vorlagengruppe ergänzen |
| Mailkonten auf main | Hauptkonto und zusätzliches Dokumentkonto; Dokumentkonto vorwiegend für Versand und Gesendet-Ablage | Einkauf benötigt zusätzlich eigenen Eingangsabruf und Kontobezug jeder Nachricht |
| Alter Branch | Preisanfragen, Lieferantenbeteiligungen mit Token, Antwortzuordnung, Vergleichsmatrix, KI-Preisextraktion, Bestellköpfe | Fachliche Ideen gezielt übernehmen |
| Grenzen des alten Ansatzes | Externe Artikelnummern in Anfragepositionen; fest codierte Anfragetexte; Zusatzkosten teils nur geloggt; einzelner Antwortverweis; Bestellstatus teilweise direkt bei Vergabe gesetzt | Diese Stellen auf dem aktuellen Stand neu entwerfen |

Quellengrundlage auf main: `react-pc-frontend/src/pages/BestellungEditor.tsx`, `react-pc-frontend/src/components/layout/RibbonNav.tsx`, `react-pc-frontend/src/pages/EmailTextvorlagenEditor.tsx`, `react-pc-frontend/src/components/settings/sections/EmailSettingsSection.tsx`, `src/main/java/org/example/kalkulationsprogramm/domain/Artikel.java` sowie die Services `BestellungService`, `SystemSettingsService`, `EmailTextTemplateService` und `EmailTextTemplateKategorien`.

Quellengrundlage im alten Branch: `docs/EINKAUF_WORKFLOWS.md`, `PreisanfrageService`, `PreisanfrageZuordnungService`, `PreisanfrageAngebotsExtraktionService`, `BestellauftragService`, `ZeugnisService` und die zugehörigen Domainklassen. Die alte Dokumentation weicht teilweise vom späteren Branchcode ab; Code und Issue-Kommentare wurden deshalb zusätzlich geprüft.

Die verlinkte Live-Seite `/email-textvorlagen` war über das Web-Werkzeug nicht erreichbar. Die Bewertung des Editors beruht auf dem lokalen Quellcode, nicht auf einer Sichtprüfung des laufenden Systems.

## 3. Vorgehensvarianten

| Variante | Vorteil | Nachteil |
| --- | --- | --- |
| Nur Anfragefunktion an bestehendes Bestellt-Flag anhängen | Kleiner erster Eingriff | Keine belastbaren Teilmengen, Bestellhistorie und Versandzustände |
| **Schrittweiser Einkaufsablauf auf gemeinsamem Mengen-, Mail- und Dokumentmodell** | Je Stufe nutzbar; aktuelle Bausteine bleiben Grundlage | Benötigt zunächst klar getrennte Vorgänge |
| Umfangreiches Einkaufs-, Lager- und EN-1090-Modul auf einmal | Viele Funktionen gleichzeitig | Hoher Umfang; verzögert den gewünschten Alltagsnutzen |

Empfohlen wird die zweite Variante. Ein vollständiger einfacher Ablauf entsteht zuerst, anschließend kommen KI, feinere Vergabe und Wareneingang hinzu.

## 4. Oberfläche und täglicher Ablauf

### 4.1 Einkauf als zusammenhängender Arbeitsbereich

Drei Hauptseiten: **Bedarf**, **Anfragen**, **Bestellungen**. Später folgt **Lieferungen & Zeugnisse**. Das bestehende E-Mail-Center bleibt der gemeinsame Posteingang und erhält Filter nach Postfach sowie Links zum Einkaufsvorgang.

Eine Anfrage erhält eine Detailseite mit den Bereichen Positionen, Lieferanten & Antworten, Angebotsvergleich und Verlauf. Der Kopf zeigt Anfragenummer, Frist, Projektbezug und beispielsweise „2 von 3 Lieferanten haben geantwortet“. Automatische Eingangsbestätigungen zählen nicht als verwertbares Angebot.

Die Bestelldetailseite zeigt Bestellnummer, zugehörige Anfrage (falls vorhanden), Lieferant, Positionen, Versand, zugesagten Termin und verknüpfte Belege. Informationen wie „Zeugnis fehlt“ bleiben auch dann sichtbar, wenn bereits eine Rechnung eingegangen ist.

Vorhandene Layouts, Tabellen, Dialoge, Artikel-/Lieferantenauswahl und E-Mail-Bausteine wiederverwenden. Rose/Slate, verständliche Beschriftungen, eigene Auswahl- und Datumsfelder sowie deutsche Zahlenformate bleiben verbindlich.

### 4.2 Bedarf sammeln

Bedarf entsteht aus vorhandenen Projektpositionen, manueller Erfassung oder später aus einer HiCAD-Sägeliste. Katalogmaterial referenziert einen internen Artikel; fehlt dieser, führt der Ablauf über eine kurze Artikelanlage mit eigener Nummer. Zusätzlich gibt es die Positionsart **Teil nach Zeichnung** für Brenn-, Laser-, Kantteile und Sonderbleche ohne wiederverwendbaren Katalogartikel. Sie besitzt eine eindeutige interne, projektbezogene Positionskennung, Zeichnungsnummer und Revision. Beide Positionsarten behalten dieselbe interne Referenz über alle Lieferanten; externe Lieferantennummern ersetzen sie niemals. Historische Positionen ohne eindeutige interne Referenz werden sichtbar zur Nachpflege angeboten, nicht still einem beliebigen Artikel zugeordnet.

Je Zeile: interne Artikelnummer beziehungsweise Zeichnungsteilkennung, Bezeichnung, Material/Abmessung, Projekt oder Lagerzweck, benötigte Menge, Beschaffungseinheit, benötigter Termin und Zeugnisanforderung. Profilpositionen ergänzen Stückzahl, Einzellänge, Gesamtlänge, Gewichtsbasis und Zuschnitt. Ein Profil ist beispielsweise „4 Stück à 6.000 mm“, nicht lediglich „24 m“.

Bearbeitungen und Oberfläche gehören ebenfalls zur verbindlichen Positionsbeschreibung: beispielsweise sägen, bohren, strahlen, grundieren oder verzinken. Die genaue Ausführung wird beschrieben und gegebenenfalls durch Zeichnung oder Spezifikation ergänzt. Eine abweichende Oberfläche ist eine Angebotsabweichung, kein automatisch gleichwertiger Ersatz.

Zeichnungen und weitere technische Anlagen werden positionsbezogen und versioniert verwaltet (PDF, DXF, STEP). Die Versandvorschau zeigt die freizugebenden Dateien; alle angefragten Lieferanten erhalten dieselbe freigegebene Fassung. Sichere Dateinamen, Typ-/Größenprüfung, Zugriffsrechte und zulässige Gesamtmailgröße sind Teil der Umsetzung. Keine CAD-Vorschau oder automatische Geometrieauswertung im ersten Ausbau erforderlich. Übersteigt ein Paket die Versandgrenze, muss eine explizite Alternative gewählt werden; Anhänge dürfen nicht still fehlen.

Die Auswahl ist lieferantenneutral. „Angebote einholen“ übernimmt die ausgewählte offene Beschaffungsmenge in eine Anfrage. Lieferanten werden anschließend ausgewählt. Bündeln darf nur Positionen mit identischem Artikel beziehungsweise identischer Zeichnungsteilkennung und Zeichnungsrevision, technischen Anforderungen, Zuschnitt und Zeugnisbedarf sowie kompatiblem Lieferort und Bedarfstermin zusammenfassen; Herkunftspositionen und Projektmengen bleiben erhalten. Unterschiedliche Baustellen oder unvereinbare Termine bilden getrennte Liefergruppen mit eigenen Transportkosten und Terminzusagen.

Bedarf, Lagerdeckung, angefragte Menge, für eine Bestellung reservierte Menge, bestellte Menge, gelieferte Menge und stornierte Menge sind unterschiedliche Größen. **Eine Anfrage deckt noch keinen Bedarf.** Mehrere Lieferanten für dieselbe Anfrage vervielfachen die Bedarfsmenge nicht. Mengen und Zuordnungen werden im Backend gespeichert, nicht nur im `localStorage`.

### 4.2a Weitere Eingänge und Abzweigungen

- **Direkt bestellen:** Bedarf auswählen, einen Lieferanten bestimmen und dessen letzten bestätigten Preis mit Quelle und Datum vorschlagen. Historische Preise sind keine aktuelle Lieferzusage. Vor Versand Menge, Preisgeltung, Konditionen und Empfänger bewusst bestätigen; bei fehlendem Preis zunächst anfragen. Dieser Weg erzeugt direkt einen Bestellentwurf mit B-Nummer, ohne künstliche PA-Nummer, und nutzt denselben Freigabe-/Versandprozess wie eine Bestellung aus einem Angebot.
- **Aus Lager nehmen:** Eine einfache Material-/Entnahmeliste lässt sich drucken und in der Werkstatt abhaken. Erst die bestätigte tatsächlich entnommene Teilmenge deckt den Bedarf; ein Ausdruck allein bewirkt nichts. Entnahme erfasst Projekt, Menge, Zeitpunkt, Mitarbeiter und nachvollziehbare Bewertung. Ein bestätigter vorhandener Durchschnitts- oder Einkaufspreis kann als Vorschlag dienen, fehlende Preise bleiben prüfpflichtig. Keine Einführung einer vollständigen Lagerwirtschaft in dieser Stufe. `ArtikelInProjekt.ausLager` ist auf main vorhanden, reicht allein aber nicht für Teilmengen.
- **HiCAD-Sägeliste importieren:** Excel einlesen → Vorschau mit Stückzahl, Länge, Werkstoff, interner Zuordnung und vorhandenen Anschnittangaben → unklare Zuordnungen korrigieren → ausgewählte Zeilen einmalig als Bedarf übernehmen. Dateifassung und Ursprungszeile bleiben nachvollziehbar; wiederholter Import warnt vor Duplikaten und erzeugt keine doppelte Menge ohne ausdrückliche Entscheidung. Eingebettete Schnittbilder vor Übernahme prüfen. Ein Profil-Matching darf ähnliche Güten nicht still zusammenlegen. Empfohlen als Stufe 1b nach dem manuellen Bedarf; keine automatische Zuschnittoptimierung übernehmen.

### 4.2b Lieferantenkontakte

Pro Lieferant separate Standardempfänger für Anfragen und Bestellungen sowie optionale Ansprechpartner mit Name und Anrede pflegen. Eine bekannte Rechnungsabsenderadresse ist nicht automatisch die richtige Einkaufsadresse. Auf main existieren `kundenEmails`, das freie Textfeld `vertreter` und `eigeneKundennummer`, aber kein entsprechender strukturierter Kontakt für diesen Ablauf.

Vor Versand Empfänger explizit anzeigen und bei Bedarf ändern. Die ausgewählte Adresse und Kontaktfassung am Vorgang speichern, sodass eine spätere Stammänderung alte Nachrichten nicht umdeutet. Ohne persönlichen Ansprechpartner wird eine neutrale Anrede verwendet; keine Personennamen aus dem Vertreter-Freitext erraten.

### 4.3 Anfrage erstellen und versenden

1. Positionen und angefragte Teilmengen prüfen.
2. Einen oder mehrere Lieferanten und deren Ansprechpartner auswählen.
3. Antwortfrist, gewünschten Liefertermin, Lieferort und Zeugnisanforderungen festlegen.
4. Vorlage, Betreff, Nachricht und Anfrage-PDF als Vorschau anzeigen.
5. „Anfragen versenden“ bestätigen.

Das System vergibt beispielsweise `PA-2026-00042`. Jeder Lieferant bekommt eine eigene Nachricht mit derselben fachlichen Positionsfassung und einem eigenen Zuordnungscode. Empfänger, Preise und Antworten anderer Lieferanten werden nicht offengelegt.

Bei Versandfehlern pro Lieferant Erfolg und Fehler getrennt zeigen. Erneutes Senden betrifft nur den gewählten fehlgeschlagenen Versand. Die Nummernvergabe muss auch bei parallelen Nutzern eindeutig sein; ein ungeschütztes „höchste Nummer + 1“ genügt nicht.

Den vorhandenen `DokumentnummerCounter` als Ausgangspunkt für gemeinsame Nummernvergabe nutzen, mit getrennten Nummernkreisen für PA und B und der gewünschten Jahresperiode. **Der aktuelle Mechanismus ist nicht bereits nachweislich nebenläufigkeitssicher:** `FormularTemplateService.generateDokumentnummer()` liest und erhöht einen monatlichen Zähler; Entity und Repository zeigen hier weder Versionsfeld noch explizite Sperre. Die Erweiterung benötigt eine atomare Vergabe oder Datenbanksperre einschließlich des erstmaligen Anlegens eines Zählers, Eindeutigkeitsregeln und Parallelitätstests. Bestehende Verkaufsnummernkreise und ihre Ausgabe dürfen sich dadurch nicht ändern.

Versendete Anfragen bleiben als Revision erhalten. Änderungen an Menge, Werkstoff, Zuschnitt oder Zeugnisanforderung erzeugen eine neue Revision mit erneutem gezieltem Versand. Alte Angebote bleiben ihrer ursprünglichen Revision zugeordnet und gelten nicht automatisch für die neue Fassung.

## 5. Einkaufs-Postfach und Vorlagen

### 5.1 Einstellungen

Unter „Einstellungen → E-Mail“ eine Karte **Einkaufs-Postfach**:

- Aktivierung, E-Mail-Adresse, Anzeigename und Passwort ändern.
- SMTP- und IMAP-Server, Port und Verschlüsselung; getrennte Zugangsdaten bei Bedarf.
- Posteingangs- und Gesendet-Ordner, Verbindung prüfen, explizite Testmail senden.
- Letzter erfolgreicher Abruf und verständlicher Fehlerzustand.

`einkauf@bauschlosserei-kuhn.de` ist ein neues Postfach unter der vorhandenen Domain. Es ist keine zusätzliche Domain erforderlich. Das Konto muss beim Mailanbieter bereitgestellt sein.

Versand, manuelle Antworten aus einem Einkaufsvorgang und Ablage im Gesendet-Ordner verwenden dieses Konto. Eingehende Nachrichten werden mit der Konto-ID importiert. Bei aktiviertem, aber fehlerhaftem Einkaufskonto kein stiller Wechsel zum Rechnungs- oder Hauptkonto.

Technisch gemeinsame Kontokonfiguration und wiederverwendbare Einstellungsfelder statt einer weiteren Kopie des gesamten Einstellungsformulars. Kennwörter werden nur schreibend angenommen, nie im Klartext zurückgegeben oder geloggt; serverseitig geschützt gespeichert, Schlüsselmaterial außerhalb versionierter Dateien. Kontoänderungen sind administrativen Berechtigungen vorbehalten.

### 5.2 Bestehenden Textvorlageneditor erweitern

Neue Gruppe **Einkauf** mit Vorlagen für Lieferantenanfrage, Bestellung, Nachfrage zum Angebot und fehlendes Werkstoffzeugnis. Ein bearbeitbarer Standard je Typ ist enthalten; weitere Varianten können gewählt werden. Eine Vorlage wird vor dem Versand mit echten Vorgangsdaten angezeigt. Änderungen an Vorlagen verändern keine bereits versendeten Nachrichten.

| Platzhalter | Verwendung |
| --- | --- |
| `{{ANFRAGENUMMER}}` | Einkaufsspezifischer Kontext; auch in einer daraus erzeugten Bestellung verfügbar |
| `{{BESTELLNUMMER}}` | Ab Anlage eines Bestellentwurfs; keine erfundene Bestellnummer in einer Anfrage |
| `{{LIEFERANTENNAME}}`, `{{ANSPRECHPARTNER}}`, `{{ANREDE}}` | Empfängerbezogene Angaben |
| `{{ANTWORTFRIST}}`, `{{LIEFERTERMIN}}`, `{{LIEFERADRESSE}}` | Termin- und Lieferinformationen |
| `{{PROJEKTNUMMER}}`, `{{BAUVORHABEN}}` | Bei projektbezogenem Vorgang |
| `{{RUECKMELDECODE}}` | Eindeutige Anfrage-Lieferanten-Zuordnung |
| `{{EIGENE_KUNDENNUMMER_BEIM_LIEFERANTEN}}` | Unsere beim Lieferanten geführte Nummer aus `eigeneKundennummer`; ausdrücklich verschieden von der Kundenstammnummer |
| `{{LIEFERANTEN_ANGEBOTSNUMMER}}` | Angebotsnummer des Lieferanten aus der ausgewählten Angebotsversion, etwa „gemäß Ihrem Angebot Nr. …“ |

Skalare Platzhalter funktionieren in Betreff und Text. Strukturierte Positions- und Zeugnislisten werden als geeigneter Inhaltsbaustein und im PDF eingefügt, nicht als HTML im Betreff. Fehlende Pflichtwerte, unbekannte Platzhalter und für die Vorlagenart unzulässige Platzhalter verhindern den Versand mit konkretem Hinweis. Sammelanfragen ohne einzelnen Projektbezug verwenden eine passende allgemeine Vorlage.

Der bereits vorhandene Platzhalter `ANFRAGENUMMER` wird je Vorlagenart mit dem richtigen Kontext befüllt; eine Kundenanfragenummer darf niemals versehentlich in der Einkaufsanfrage stehen.

`KUNDENNUMMER` behält seine Bedeutung im Verkauf und wird nicht als Einkaufskürzel umgedeutet. Für Direktbestellungen ohne vorgelagerte Anfrage oder Lieferantenangebot steht eine passende Bestellvorlage ohne diese beiden Pflichtreferenzen zur Verfügung. Das Fehlen einer fachlich optionalen Referenz wird nicht durch eine erfundene Nummer kaschiert.

Beispiel Anfragebetreff: `Preisanfrage {{ANFRAGENUMMER}} – Lieferung bis {{LIEFERTERMIN}}`.

Beispiel Nachricht: „Guten Tag, bitte bieten Sie uns die beigefügten Positionen bis zum {{ANTWORTFRIST}} an. Bitte nennen Sie Liefertermin, Fracht-, Zuschnitt- und Zeugniskosten getrennt. Die je Position aufgeführten Werkstoffzeugnisse bitte mit anbieten. Antworten Sie gerne direkt auf diese E-Mail.“

Beispiel Bestellbetreff: `Bestellung {{BESTELLNUMMER}} zur Anfrage {{ANFRAGENUMMER}}`.

Der Zuordnungscode wird als geschützter Systemzusatz in Betreff beziehungsweise Referenzzeile aufgenommen, auch wenn die Vorlage den Platzhalter nicht enthält. Er bleibt in der Vorschau sichtbar.

### 5.3 Antworten zuverlässig zuordnen

Zuordnung zuerst über gespeicherte Message-ID, `In-Reply-To` und `References`, dann über den lieferantenspezifischen Zuordnungscode. Anfragenummer plus bekannter Lieferantenkontakt dient nur bei eindeutiger Übereinstimmung als weiterer Weg. Widersprüchliche Hinweise, neue Absender und mehrdeutige Treffer landen unter „Zuordnung prüfen“.

Ein vollständiger Verlauf speichert beliebig viele Nachrichten je Anfrage und Lieferant: Rückfrage, Angebot, Nachverhandlung, neue Angebotsfassung. Eine weitere Antwort überschreibt nicht die vorherige. Nach Bestellanlage erhält die Bestellung einen eigenen verknüpften Verlauf; unklare Antworten auf ältere Anfragemails werden nicht ungeprüft zur Auftragsbestätigung umgedeutet.

Eine spezielle Reply-To-Adresse pro Lieferant ist optional und setzt passende Alias-/Catch-all-Unterstützung beim Anbieter voraus. Für den ersten Ausbau genügt das feste Einkaufspostfach mit Threadbezug und Code.

Mailimport und Dateiverarbeitung sind wiederholbar ohne doppelte Vorgänge; Importidentitäten berücksichtigen Konto, Ordner und IMAP-Kennung. Automatische Antworten und Unzustellbarkeitsmeldungen erhalten eigene Zustände. Threadzuordnung ist kein Nachweis für einen richtigen Preis oder einen authentischen Absender.

Steht unsere Nummer nur in einem PDF-Anhang oder schreibt ein unbekannter Innendienstkontakt, darf die Dokumentauswertung einen **Zuordnungsvorschlag mit Fundstelle** liefern. Der Nutzer prüft Anfrage, Lieferant und Absender, bevor daraus eine bestätigte Zuordnung entsteht. Das Auslesen einer Nummer aus einem Anhang genügt nicht für automatische Bestellung, Preisübernahme oder Materialfreigabe.

## 6. Angebotsvergleich und KI

### 6.1 Vergleich auf gleicher Grundlage

Zeilen enthalten unsere Positionen mit internen Artikelnummern beziehungsweise Zeichnungsteilkennungen, Spalten die angefragten Lieferanten. Pro Angebot werden Originalbezeichnung/-nummer des Lieferanten zusätzlich gespeichert, aber nicht zum führenden Artikelschlüssel.

Verglichen werden Nettopreise auf derselben Mengenbasis, gesamte angefragte Menge, Liefertermin, Angebotsgültigkeit, Mindestmengen, Verpackungseinheiten, technische Abweichungen sowie die zugesagten Zeugnisse. Fracht, Zuschnitt, Verpackung, Mindermengenzuschläge und Zeugnisse gehen ausdrücklich in den Gesamtpreis ein. Skonto erscheint mit seinen Bedingungen separat und wird nicht als sicherer Nachlass eingerechnet.

Stahlangebote können Grundpreis sowie Legierungs-, Schrott-, Energie-, Mengen- oder Gütezuschläge ausweisen. Diese werden als getrennte Preisbestandteile mit Berechnungsbasis (je Stück, kg, 100 kg, Tonne, prozentual oder pauschal) geführt. „Im Endpreis enthalten“ und „zusätzlich“ müssen unterscheidbar sein, damit Zuschläge nicht doppelt addiert werden. Bearbeitung und Oberflächenbehandlung erhalten eigene Kostenbestandteile. Bei variablen Zuschlägen ohne feststehende Bezugsgröße bleibt der Gesamtpreis entsprechend unvollständig.

Die Bindefrist wird sichtbar hervorgehoben, beispielsweise „Angebot läuft morgen ab“. Bei Bestellfreigabe wird die Gültigkeit erneut geprüft. Abgelaufene Tagespreise brauchen eine neue Bestätigung des Lieferanten; ein Klick auf einen alten Preis erneuert keine Bindefrist.

Stück, Meter, Kilogramm, Tonne und Preise pro 100 Einheiten werden nur mit nachvollziehbarer Umrechnung verglichen. Gewicht und Umrechnungsfaktor müssen belegt oder gepflegt sein. Fehlende Angaben sind „offen“, niemals automatisch null Euro. Im ersten Ausbau EUR; andere Währungen bleiben ohne freigegebene Umrechnungsbasis außerhalb der automatischen Rangfolge.

| Fiktives Beispiel | Lieferant A | Lieferant B | Lieferant C |
| --- | ---: | ---: | ---: |
| Material netto | 1.000 € | 970 € | 920 € |
| Fracht | 40 € | 110 € | offen |
| Zeugnis | 20 € | enthalten | nicht bestätigt |
| Vergleichbarer Gesamtpreis | **1.060 €** | 1.080 € | **nicht vollständig** |

Das Ergebnis lautet hier „A ist unter den vollständigen Angeboten 20 € günstiger als B“. C darf wegen fehlender Angaben nicht als günstigster vollständiger Anbieter erscheinen. Technisch ungeeignete oder abgelaufene Angebote werden erkennbar aus der regulären Empfehlung ausgeschlossen.

### 6.2 Rolle der KI

Die KI liest E-Mail-Text und Angebotsanhänge, schlägt Positionszuordnungen vor und extrahiert Preise, Kosten, Lieferzeit, Gültigkeit und Zeugniszusagen. Jede Aussage verweist auf die Originalmail beziehungsweise PDF-Seite und zeigt unklare Felder. Verarbeitung startet nach jedem geeigneten Angebot; sie wartet nicht auf den letzten Lieferanten.

Der Mitarbeiter prüft und korrigiert die Werte. Ein bestätigter Datenstand wird nicht durch einen erneuten KI-Lauf überschrieben. Überarbeitete Lieferantenangebote bilden eigene Versionen. Manuelle Erfassung funktioniert auch ohne KI.

Berechnung und Rangfolge beruhen auf validierten strukturierten Daten. Die KI formuliert eine verständliche Begründung, beispielsweise „A ist insgesamt am günstigsten; B liefert drei Tage früher“. Technische Ersatzartikel oder andere Werkstoffgüten benötigen ausdrückliche Bestätigung. Die KI bestellt nichts selbstständig und ändert keine Artikelstammdaten oder allgemeinen Lieferantenpreise ungeprüft.

E-Mail-Inhalte und Anhänge sind fremde Daten, keine Handlungsanweisungen an den KI-Prozess. Dokumenttypen, Größe, sichere Dateiverarbeitung und HTML-Ausgabe folgen den vorhandenen Sicherheitsbausteinen.

### 6.3 Bestätigte Preise, Planung und tatsächliche Kosten

Nach Prüfung eines Angebots kann der Nutzer über „Als Lieferantenpreis übernehmen“ einen neuen Stand der bestehenden Preishistorie anlegen. Artikelidentität, Lieferant, normalisierte Einheit, Preisbasis, Angebotsdatum, Gültigkeit, Staffel/Mindestmenge und Belegbezug bleiben nachvollziehbar. `PreisQuelle.ANGEBOT_EMAIL` existiert bereits und wird wiederverwendet. Projekt- oder mengenabhängige Sonderpreise werden nicht als uneingeschränkt gültiger Standardpreis übernommen. Fracht, einmalige Bearbeitung und enthaltene Zuschläge dürfen beim Übernehmen und späteren Rechnen nicht doppelt auftauchen.

Angebots-/Bestellpreise dienen der **geplanten** Einkaufssumme. Tatsächliche Einkaufskosten entstehen im bestehenden Rechnungspfad nach Prüfung und Projektzuordnung; bestätigte Lagerentnahmen folgen der bestehenden separaten Lagerbewertung. Kein pauschales Zurückschreiben jedes gewählten Angebots in `ArtikelInProjekt.preisProStueck`: Dieses Feld hat auf main abhängig vom Kontext Einzelpreis- oder Summenbedeutung, und bestellte Ware wird später über Lieferantenrechnungen berücksichtigt. Eine solche Übernahme könnte die Nachkalkulation verfälschen oder doppelt belasten. Ein gewichteter Durchschnittspreis ist eine spätere, eigenständig zu definierende Bewertungsregel und kein Ersatz für aktuelle Angebotskosten.

## 7. Vergabe, Bestellung und Mengensicherheit

Zuerst vollständige Vergabe eines ausgewählten Positionspakets an einen Lieferanten. Die spätere Aufteilung von Positionen oder Teilmengen auf mehrere Lieferanten wird im Datenmodell vorbereitet, aber als eigener Ausbau umgesetzt. Eine Aufteilung muss zusätzliche Fracht, Mindestmengen und weggefallene Paketnachlässe neu berücksichtigen.

Bei einer Bestellung aus einem Angebot erzeugt „Als Bestellung vorbereiten“ einen Bestellentwurf, beispielsweise `B-2026-00118`, verknüpft mit `PA-2026-00042` und der gewählten Angebotsversion. Je Lieferant und unabhängigem Versandvorgang entsteht ein eigener Bestellkopf. Projektanteile bleiben positionsbezogen erhalten.

Vor dem verbindlichen Versand prüft ein berechtigter Mitarbeiter Empfänger, Lieferadresse, Mengen, Preise, Bedingungen, Termin und Zeugnisse. Angebotsauswahl allein bedeutet weder Versand noch Lieferantenzusage. Ein PDF-Download setzt ebenfalls keinen Versandstatus.

Bestellung und Versand haben getrennte Zustände:

- Fachlich: Entwurf → verbindlich bestellt → teilweise geliefert → geliefert; Storno separat nachvollziehbar.
- Versand: vorbereitet → läuft → vom Mailserver angenommen / fehlgeschlagen / Versand unklar.
- Lieferant: Bestätigung ausstehend / bestätigt / Abweichung zu prüfen.

Mit der menschlichen Versandfreigabe wird die konkrete Bestellfassung unveränderlich eingefroren und ein Versandauftrag angelegt; ihre Menge bleibt zunächst reserviert. Erst die eindeutig protokollierte SMTP-Annahme überführt diese Reservierung atomar in „bestellt“. Das bezeichnet den internen Status der ausgelösten Bestellung, keine bestätigte Annahme durch den Lieferanten. Bei nachgewiesenem Versand außerhalb des Systems kann ein berechtigter Nutzer denselben Übergang mit Datum und Beleg dokumentieren.

SMTP-Annahme ist kein Zustellnachweis. Bei einem Timeout nach möglicher Annahme wird nicht automatisch erneut bestellt: „Versand unklar“ hält die Mengenreservierung, bis der Sachverhalt geprüft ist. Fehlgeschlagene Ablage im Gesendet-Ordner führt ebenfalls nicht zum erneuten Versand. Eine nachträgliche Unzustellbarkeit erzeugt einen Klärfall, gibt die bestellte Menge aber nicht automatisch frei. Ein nachweislich fehlgeschlagener Versand kann gezielt erneut versucht werden; inhaltliche Korrekturen erhalten eine neue Fassung mit erneuter Freigabe.

Reservierung und Bestellanlage erfolgen transaktional mit Versionsprüfung. Lagerentnahmen, Reservierungen und Bestellungen greifen auf dieselbe transaktional geschützte offene Bedarfsmenge zu. Bereits reservierte oder bestellte Mengen dürfen erst nach ausdrücklicher Umplanung beziehungsweise bestätigtem Storno aus Lager gedeckt werden. Zwei Nutzer dürfen dieselbe offene Menge nicht doppelt vergeben oder gleichzeitig aus Lager decken. Entwürfe reservieren Mengen sichtbar; Verwerfen gibt sie frei. Abgesendete Stornoanfragen geben Mengen erst nach bestätigtem Storno beziehungsweise bewusst dokumentierter Klärung frei.

Versendete Bestellpositionen speichern interne Nummer, Beschreibung, technische Anforderungen, Mengen, Einheiten, Preise, Bedingungen und Zeugnisbedarf als feste Fassung. Spätere Stammänderungen verändern sie nicht. Änderungen am Auftrag erfolgen als nachvollziehbare Änderungsversion mit erneutem Versand.

## 8. Werkstoffzeugnisse und spätere Lieferung

Je Materialposition kann ein gefordertes Dokument beziehungsweise eine Zeugnisart festgelegt werden, beispielsweise ein Abnahmeprüfzeugnis 3.1. Vorgaben aus Artikel und Projekt werden vorgeschlagen und können durch berechtigte Personen fachlich überprüft werden. Die Anforderung erscheint in Anfrage, Angebotserfassung, Vergleich und Bestellung.

Der Auswahlkatalog sieht die Zeugnisarten 2.1, 2.2, 3.1 und 3.2 vor sowie separat geführte Anforderungen an Leistungserklärung und CE-Nachweise, etwa bei passenden Verbindungsmitteln. Mehrere Dokumentanforderungen pro Position sind möglich. Diese Optionen ersetzen einander nicht automatisch; aus der bloßen Auswahl wird keine allgemeine normative Pflicht abgeleitet.

Lieferantenantworten unterscheiden „enthalten“, „mit Aufpreis“, „nicht lieferbar“ und „noch offen“. Unterschiedliche Zeugnisanforderungen dürfen beim Bündeln von Bedarf nicht verloren gehen.

Dokumentverlauf: **angefordert → erwartet → eingegangen → zugeordnet → geprüft**, mit zusätzlichem Zustand „Klärung nötig“. Ein bloßer PDF-Eingang bedeutet noch keine erfolgreiche Prüfung. Nach Bestellversand startet die Erwartung mit dokumentierter Frist; fehlende Zeugnisse können mit einer eigenen Vorlage angefordert werden.

Zeugnisse können vor oder nach der physischen Lieferung eintreffen. Deshalb Liefermenge, Dokumentvollständigkeit und Materialfreigabe getrennt führen. Für den späteren Wareneingang werden Bestellung/Position, Teillieferung, Charge beziehungsweise Schmelznummer, Zeugnis und Projektbezug miteinander verknüpft. Eine Position kann mehrere Chargen und Zeugnisse benötigen; ein Zeugnis kann mehrere passende Lieferpositionen belegen. Dateiduplikate vermeiden, die Beziehungen dennoch vollständig erhalten.

Die alten Issues enthalten widersprüchliche pauschale EXC-/Werkstoffregeln. Diese Spec übernimmt daraus **keine ungeprüfte Normautomatik**. Anforderungen werden zentral, versioniert und mit fachlich bestätigter Grundlage geführt. Unbekannte Klassifikationen bedeuten „Prüfung erforderlich“, nicht automatisch „keine Anforderungen“. Der Einkauf funktioniert bereits mit explizit bestätigten positionsbezogenen Anforderungen; automatische normative Freigaben gehören zum separat zu konkretisierenden Umfang von #28/#51.

## 9. Fachliche Bausteine und Integration

| Baustein | Verantwortung |
| --- | --- |
| Bedarfsposition und Mengenbezüge | Bedarf, Projekt/Lagerzweck, Deckung und Reservierung |
| Einkaufsanfrage mit Revisionen | Nummer, technische Positionsfassung, Fristen |
| Anfrage-Lieferant | Empfänger, Code, Versand, Gesprächsverlauf |
| Lieferantenangebot mit Versionen | Originaldokument, angebotene Positionen, Kosten und Bedingungen |
| Vergabe | Ausgewählte Angebotsversion, Mengen und dokumentierte Entscheidung |
| Bestellung mit Positionen | Eigene Nummer, feste Versandfassung, Lieferant und Herkunft |
| Mailkonto und Versandauftrag | Kontozuordnung, Zugang, Import, wiederholbarer Versandprozess |
| Zeugnisanforderung und Dokumentzuordnung | Soll-Dokumente und Verknüpfungen zu Lieferung/Charge |

Die technische Ausarbeitung soll bestehende Services erweitern beziehungsweise nach diesen Verantwortlichkeiten schneiden. Gemeinsame Artikelauflösung, Mengen-/Preisumrechnung, Vorlagenauflösung, Mailversand und PDF-Positionsdarstellung werden wiederverwendet. Neue Einkaufsanfragen bleiben klar von Kundenanfragen getrennt.

Eingangsrechnungen, Auftragsbestätigungen und Lieferscheine docken an bestehende Dokumentenketten an. Eine Auftragsbestätigung mit abweichender Menge oder Preis erzeugt einen Prüfhinweis statt eine stille Änderung der Bestellung. Rechnungsimport und Zuordnung dürfen Kosten nicht doppelt in die Projektkalkulation buchen.

### 9.1 Rechnungsabgleich und Reklamationen

Beim Rechnungsabgleich je Position und Kostenbestandteil die bestellte, bestätigte, gelieferte und bereits abgerechnete Menge gegenüberstellen. Teilrechnungen werden gegen ihren belegten Anteil verglichen, nicht gegen den Gesamtbetrag der Bestellung. Mengen, Einheiten, vereinbarte Zuschläge und Fracht normalisieren; unbekannte Verknüpfungen zur Prüfung vorlegen. Hinweise wie „Rechnung 80 € über dem vereinbarten Betrag“ müssen ihre Grundlage zeigen. Gutschriften, Storno und Nachberechnungen bleiben als zusammengehörige Belege berücksichtigt.

Abweichungen nicht automatisch akzeptieren oder bestehende Bestellpreise überschreiben. Die bestehende `LieferantReklamation` mit Lieferant- und Lieferscheinbezug um verknüpfte Bestellung/Position und bei Bedarf Rechnung ergänzen; keinen zweiten Reklamationsablauf daneben aufbauen. Kostenübernahme bleibt im zentralen bestehenden Buchungs-/Zuordnungspfad.

### 9.2 Fällige Rückmeldungen und Lieferungen

Im Einkauf eine Arbeitsliste „Das ist fällig“: Antwortfrist abgelaufen, Auftragsbestätigung bis zur hinterlegten Frist ausstehend, bestätigter Liefertermin überschritten und fälliges Zeugnis fehlt. Sollfristen und Zuständigkeit sind am Vorgang sichtbar; terminlose Fälle heißen „Termin klären“. Erledigte, abgesagte oder stornierte Vorgänge entfallen aus der offenen Liste. Ein Klick bereitet eine passende Vorlagenmail vor; der Nutzer prüft und versendet sie. Keine automatischen Nachfassmails im ersten Ausbau.

Neue Migrationen auf Basis des dann aktuellen main anlegen. Keine alten Flyway-Dateien oder veralteten Versionsnummern aus Issues übernehmen. Bestehende Belegketten erhalten ihre Bedeutung; historische Vorgänge ohne nachweisbare Bestellung werden nicht nachträglich als versendet ausgegeben.

## 10. Bestehende Issues und notwendige Präzisierungen

Alle nachfolgend genannten Issues waren beim direkten Abruf am 22.09.2026 **offen**. Branchfortschritt bedeutet nicht, dass die Funktion auf main vorhanden ist.

| Issue | Verwendung im neuen Konzept | Präzisierung |
| --- | --- | --- |
| [#54 Bedarf → Preisanfrage](https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/54) | Kern des Übergangs aus der Bedarfsliste | Kommentar bestätigt: klassischer Anfrageweg weiterhin offen; IDS-Fortschritt ersetzt ihn nicht. Backend-Persistenz und interne Artikelnummer ergänzen |
| [#52 Schnittbildauswahl](https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/52) | Gemeinsame Positionsbearbeitung | Maßgeblich ist der spätere Kommentar: **ein Schnittbild plus Winkel links/rechts**, nicht zwei Bilder. Historische Anfragefassungen dürfen nicht nachträglich überschrieben werden |
| [#53 Anfrage-/Bestell-PDF](https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/53) | Verständliche Profil-/Zuschnittdarstellung | An das korrigierte Modell aus #52 anpassen; interne Nummer, Frist und Zeugnisanforderung ergänzen |
| [#44 KI-Zeugnisextraktion](https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/44) | Späterer Dokumenteingang | Quellenbezug, Prüfung und Zuordnung zu Teillieferung/Charge ergänzen; nicht mit KI-Angebotsauslesen verwechseln |
| [#28 Zeugnis-Check](https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/28) | Spätere fachliche Prüfung | Pauschale Materialregeln vor Umsetzung fachlich verifizieren; Einkauf braucht schon vorher explizite Zeugnisanforderungen |
| [#51 Zentrale EN-1090-Anforderungen](https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/51) | Gemeinsame Quelle für bestätigte Projektanforderungen | Widerspruch zu älteren EXC-adaptiven Kommentaren in #29/#40 auflösen; unbekannte Klassen nicht automatisch freigeben |
| [#29 Wareneingang](https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/29) | Späterer Ausbau Lieferung/Freigabe | Kommentare berücksichtigen: physische Annahme und spätere Zeugnisprüfung getrennt. Alte Abhängigkeits- und Migrationsangaben nicht kopieren |
| [#40 Mobiler Wareneingang](https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/40) | Spätere mobile Bedienung | Bestehenden dreistufigen Ansatz auf die neuen Bestellungen aufsetzen |

Besonders relevante Kommentare:

- [#52: Reduktion auf ein Schnittbild](https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/52#issuecomment-4296593989)
- [#54: Preisanfrage bleibt unabhängig vom IDS-Fortschritt offen](https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/54#issuecomment-4404703763)
- [#29: Zeugnisse treffen zeitversetzt ein](https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/29#issuecomment-4290541131)

Gesucht wurde repositoryweit ohne Zustandsbeschränkung nach Einkauf, Beschaffung, Preisanfrage, Bestellnummer, Postfach, Zeugnis, E-Mail sowie passenden Titeln für Bestellung und Angebotsvergleich. Für die folgenden zusammenhängenden Ergänzungen fand sich dabei kein passendes bestehendes Gesamt-Issue:

1. Einkaufs-Postfach mit echtem SMTP/IMAP-Betrieb und Kontozuordnung.
2. Einkaufsvorlagen und typspezifische Platzhalter im zentralen Editor.
3. Versionierte Mehrlieferantenanfragen, vollständiger Mailverlauf und robuste Antwortzuordnung auf main.
4. Manueller und KI-unterstützter Angebotsvergleich einschließlich Gesamtkosten.
5. Verknüpfte, nummerierte Bestellungen mit Mengenschutz und belastbaren Versandzuständen.

Diese Lücken sind im neu angelegten [Konzept-Issue #167: Beschaffung – Einkaufs-Postfach, Lieferantenanfragen, Angebotsvergleich und nummerierte Bestellungen](https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/167) zusammengefasst und mit den vorhandenen Issues verknüpft. Nach dem externen Review wurde auch nach HiCAD, Ansprechpartner und Rechnungsabgleich gesucht; dabei ergaben sich keine passenden bestehenden Issues. Die ergänzten Anforderungen aus diesem Review werden ebenfalls in #167 gesammelt; die spätere Umsetzung kann daraus kleinere Tickets ableiten. Vorhandene Fach-Issues werden nicht dupliziert oder allein wegen des Referenzbranchcodes geschlossen. Das Issue enthält eine eigenständig lesbare Zusammenfassung; diese ausführliche Spec wird auf dem Branch `codex/beschaffung-konzept` versioniert.

## 11. Vorgeschlagene Ausbaustufen

| Stufe | Nutzbarer Abschluss | Grundlage |
| --- | --- | --- |
| 1 – Bedarf und Anfrage vorbereiten | Interne Artikel-/Zeichnungsteilreferenz, technische Anlagen, persistente Mengen, einfache Lagerentnahme, Lieferantenkontakte und Anfrageentwurf; anschließend HiCAD-Import als Stufe 1b | #54; korrigierter Umfang #52; Ergänzungen #167 |
| 2 – Anfragen senden und Antworten sammeln | Einkaufspostfach, Standardtexte, PDF, Einzelversand und vollständiger zugeordneter Verlauf | Neues Ergänzungspaket; #53 |
| 3 – Vergleichen und bestellen | Manuelle Angebote, Gesamtkosten einschließlich Zuschlägen, bestätigte Preishistorie, Gesamtvergabe oder Direktbestellung mit eigener Nummer; fällige Rückmeldungen sichtbar | Neues Ergänzungspaket |
| 4 – KI und feinere Vergabe | Quellenbelegte Extraktion, begründete Empfehlung, später positions-/mengenweise Aufteilung | Neues Ergänzungspaket |
| 5 – Lieferung und Zeugnisse | Teillieferungen, Rechnungsabgleich/Reklamation, Zeugnisnachforderung/-prüfung, Chargenbezug und anschließend mobile Annahme | #44, #28, #51, #29, #40; Ergänzungen #167 |

Nummern, Revisionen, Einheiten, Mengenschutz und Herkunftsbezüge werden von Beginn an berücksichtigt. Die spätere Oberfläche für Teilvergabe wird dadurch möglich, ohne schon in Stufe 1 einen komplexen Optimierer bauen zu müssen.

Nicht Bestandteil des ersten Ausbaus: IDS/Punchout, automatische Lieferantenportale, eigenständige KI-Bestellungen, vollständige Lagerwirtschaft, Zuschnittoptimierung, mobile Einkaufsverwaltung oder eine pauschale EN-1090-Zertifizierungslogik.

## 12. Abnahmefälle für die spätere Umsetzung

1. Derselbe interne Artikel wird mit identischer Nummer bei drei Lieferanten angefragt; deren externe Nummern überschreiben den Stamm nicht.
2. Aus einem Bedarf von zehn Stück werden vier angefragt. Es bleiben zehn ungedeckte Stück, bis eine tatsächliche Lagerdeckung oder verbindliche Bestellung vorliegt; die angefragten vier sind separat erkennbar.
3. Drei Lieferantenantworten auf diese vier Stück ergeben keine zwölf bestellten Stück. Parallele Vergaben können die offene Menge nicht überschreiten.
4. Änderungen an einer versendeten Anfrage erzeugen eine neue Revision. Ein Angebot zur alten Menge wird als solche erkannt.
5. Die Vorschau und tatsächliche Mail verwenden dieselbe Vorlagenfassung und korrekte Nummern; ungeeignete Platzhalter blockieren den Versand.
6. Einkaufsmails gehen über das Einkaufskonto, Antworten werden dort importiert; Haupt- und Dokumentkonto funktionieren unverändert weiter.
7. Antworten mit verändertem Betreff, mehrere Rückfragen und neue Angebotsversionen bleiben im Verlauf. Uneindeutige Fälle können manuell zugeordnet werden.
8. Automatische Eingangsbestätigung, Absage, Angebot und Unzustellbarkeit sind unterscheidbar. Doppelte Importe erzeugen keine doppelten Angebote.
9. Der Vergleich berücksichtigt Fracht, Zuschnitt und Zeugnisse. Fehlende Preise sind nicht null, falsche Einheiten nicht direkt vergleichbar.
10. KI-Felder haben Quellen; Korrekturen bleiben erhalten. Bei KI-Ausfall kann der Vorgang manuell abgeschlossen werden.
11. „Angebot wählen“, PDF-Download und fehlgeschlagener Versand markieren keine Bestellung als erfolgreich versendet. Unklarer Versand führt nicht zu blindem Wiederholen.
12. Eine ausgewählte Angebotsversion samt technischen Bedingungen bleibt in der Bestellung nachvollziehbar. Eine spätere Stammänderung verändert den versendeten Beleg nicht.
13. Ein später eingehendes Zeugnis kann einer Charge/Teillieferung zugeordnet werden. Vollständiger Wareneingang ersetzt keine ausstehende Zeugnisprüfung.
14. Rollen für Einstellungen, Anfrageversand, Bestellfreigabe und Zeugnisprüfung werden serverseitig durchgesetzt; fremde Vorgangs-IDs umgehen sie nicht.
15. Direktbestellung erzeugt ohne Anfrage eine B-Nummer; Vorlage und Prüfungen funktionieren ohne erfundene PA- oder Angebotsnummer.
16. Ein Zeichnungsteil bleibt mit eigener interner Positionskennung und identischer freigegebener Zeichnungsrevision über alle Lieferanten vergleichbar. Fehlende oder zu große Anhänge werden vor Versand angezeigt.
17. Anfrageempfänger und unsere Kundennummer beim Lieferanten werden von Rechnungsabsendern beziehungsweise Verkaufs-Kundennummern unterschieden. Fehlender Ansprechpartner führt zu neutraler Anrede.
18. HiCAD-Vorschau übernimmt nur bestätigte Zeilen und Teilmengen. Erneutes Einlesen erzeugt keine unbemerkten Doppelbedarfe. Gedruckte Lagerlisten decken Bedarf erst nach bestätigter Entnahme. Gleichzeitige Lagerentnahme und Bestellreservierung überschreiten zusammen nicht die offene Menge; bereits disponierte Mengen benötigen eine ausdrückliche Umplanung.
19. Zuschläge werden genau einmal gerechnet; abgelaufene Angebote erfordern erneute Preisbestätigung. Bestätigte Preisübernahme erhält Quelle und Gültigkeit und bucht keine tatsächlichen Projektkosten.
20. Teilrechnungen, Gutschriften und Reklamationen werden den richtigen Bestellanteilen zugeordnet; der Vergleich markiert belegte Abweichungen ohne Doppelbuchung. Fällige Vorgänge bereiten Nachfragen vor, versenden sie aber nicht ungefragt.

Bei Implementierung: Backend- und Frontend-Tests sowie vollständige Playwright-Abläufe für die neuen Workflows ergänzen, mit Dummy-Daten. Vor Commit/Push alle vorgeschriebenen Tests, Lint, betroffene Builds und Designprüfung grün; `/review-and-ship` und Graphify-Synchronisierung nach Projektvorgabe. Diese Konzeptaufgabe verändert keine ausführbare Anwendung und behauptet keine Testfreigabe für die spätere Umsetzung.

## 13. Review und Abstimmungsstand

Die erste unabhängige Konzeptprüfung identifizierte fünf Punkte zur Konkretisierung: getrennte Mengen/Zustände, Revisionen mit menschlicher Bestellfreigabe, vollständige Vergleichsbedingungen, unklare SMTP-Ergebnisse und begrenztes Vertrauen in eingehende Angebote. Diese Punkte sind in den Abschnitten 4 bis 8 und den Abnahmefällen aufgenommen.

Die Folgeprüfung ergänzte kompatible Lieferorte/Termine beim Bündeln und den genauen Übergang von Reservierung zu Bestellung. Der damalige Stand wurde unabhängig **grün für das Konzept** bewertet. Danach wurde das vom Nutzer übergebene externe Review eingearbeitet und erneut unabhängig geprüft. Die abschließende Prüfung bewertete auch diesen erweiterten Stand grün für die Spezifikationsveröffentlichung, nachdem Zeichnungsteilkennungen, optionale Anfragereferenzen und der gemeinsame Mengenschutz für Lagerentnahme und Bestellung präzisiert wurden. Es handelt sich weiterhin um ein Konzept, nicht um eine Implementierungs-, Code- oder Testfreigabe.

Beim externen Review wurden die Punkte zu Lieferantenkontakten, Direktbestellungen, Zeichnungsteilen, Preisrückfluss, Nummernzähler, Platzhaltern, Stahlzuschlägen, Bearbeitung/Oberfläche, Dokumentarten, HiCAD, Lagerentnahme, Fälligkeiten, Rechnungsabgleich und PDF-Zuordnung aufgenommen. Zwei Aussagen wurden anhand des Codes korrigiert: Der bestehende Zähler ist noch nicht ausreichend gegen parallele Vergabe abgesichert; Angebotspreise sind keine tatsächlich angefallenen Projektkosten. Mobile Bedarfsmeldung bleibt eine spätere, hier nicht eingeplante Erweiterung.

Die Nummerntrennung ist bereits bestätigt. Die Ausbaustufen, erste Gesamtvergabe mit späterer Teilvergabe und konkrete Bediengestaltung sind Vorschläge dieses Dokuments. Der nächste fachliche Schritt ist die gemeinsame Durchsicht des Konzepts; es wird noch keine Implementierung gestartet.
