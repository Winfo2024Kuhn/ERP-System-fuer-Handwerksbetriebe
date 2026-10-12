# Anfragewesen – Anfrageerstellung, Kundenmanagement, Konversionsrate

## Übersicht

Dieses Dokument beschreibt das Anfragewesen im Kalkulationsprogramm – von der Erstellung einer Anfrage über Tagebuch-Einträge, Bilder und Geschäftsdokumente bis zur Übernahme in ein Projekt.

Eine **Anfrage** ist der Vorgang, bevor ein Auftrag feststeht: Ein Kunde meldet sich, Sie besichtigen, schreiben ein Angebot. Wird daraus ein Auftrag, entsteht ein **Projekt** – die Anfrage geht dabei vollständig im Projekt auf und wird anschließend gelöscht (siehe Abschnitt 6).

> **Hinweis zur Begriffswelt:** Früher hieß dieser Vorgang im Code „Angebot“. Heute ist die *Anfrage* der Vorgang und das *Angebot* ein Geschäftsdokument innerhalb der Anfrage bzw. des Projekts (siehe `ANGEBOTSWESEN.md`).

---

## 0. Bedienung im Programm

**Menü:** Projektmanagement → Aufträge → **Anfragen** (Seite „ANFRAGENÜBERSICHT“).

| Was Sie tun möchten | So geht's |
|---|---|
| Neue Anfrage anlegen | Knopf **„Neue Anfrage“** → Kunde auswählen (oder neu anlegen) → Bauvorhaben, Adresse, E-Mail-Adressen usw. eintragen → speichern. Ohne gewählten Kunden lässt sich nicht speichern. |
| Anfragen finden | Filter oben: **Freitext** (Bauvorhaben, Kunde …), **Jahr**, **Angebots-Status** (Alle / Angebot angenommen / Wartet auf Kunden / Link abgelaufen), **Status** (Nur offene / Nur beendete / Offene und beendete), **Herkunft** (Alle / Über die Webseite / Selbst angelegt), **Sortierung** (Neueste zuerst / Älteste zuerst). Gefiltert wird sofort, „Filter zurücksetzen“ stellt alles zurück. Standard: nur offene Anfragen, neueste zuerst. |
| Anfrage als erledigt markieren | Häkchen **„Beendet“** auf der Anfrage-Karte („Als beendet markieren“). |
| Anfrage ändern | Anfrage öffnen → **„Bearbeiten“**. |
| Angebot schreiben | Anfrage öffnen → Reiter **„Geschäftsdokumente“** → **„Dokument erstellen“** → „Angebot“. Der Dokument-Editor öffnet sich in einem neuen Tab. |
| Notiz / Fotos festhalten | Reiter **„Tagebuch“** → **„Neuer Eintrag“**. |
| Dateien ablegen | Reiter **„Dateien“**. |
| Aus der Anfrage ein Projekt machen | Siehe Abschnitt 6 (Projekt anlegen → „Von Anfrage übernehmen“, im Projekt „mit Anfrage zusammenführen“ oder automatisch bei digitaler Angebotsannahme). |

**Reiter einer geöffneten Anfrage:** E-Mails · Geschäftsdokumente · Dateien · Beschreibung · Tagebuch.
Im Kopf stehen Bauvorhaben, Anfragenummer, Kunde, Adresse, Anlagedatum, Brutto- und Nettobetrag sowie die Knöpfe **„Bearbeiten“** und **„Löschen“**.

**Status in der Liste:** „Offen“ oder „Beendet“. Bei älteren Datensätzen mit verknüpftem Projekt erscheint „Projekt erstellt“.

**Anfragenummer:** Eine Anfrage hat keinen eigenen Nummernkreis. Als Anfragenummer wird die Dokumentnummer des Angebots dieser Anfrage angezeigt (z. B. `AG-2026/10/00012`). Solange es kein Angebot gibt, bleibt die Nummer leer.

---

## 1. Anfrage-Entity

### 1.1 Felder

| Feld | Typ | Beschreibung |
|---|---|---|
| `id` | `Long` | Primärschlüssel |
| `version` | `Long` | Versionsnummer (optimistisches Sperren): Speichern zwei Personen gleichzeitig, wird der zweite Speicherversuch mit HTTP 409 abgelehnt |
| `bauvorhaben` | `String` | Name des Bauvorhabens |
| `betrag` | `BigDecimal` | Bruttobetrag der Anfrage. Wird automatisch aus den Geschäftsdokumenten übernommen (Auftragsbestätigung bzw. noch nicht bestätigte Angebote + Nachtragsangebote), sobald diese einen Preis liefern; ohne Dokumente bleibt ein von Hand erfasster Betrag stehen |
| `anlegedatum` | `LocalDate` | Anlagedatum (Auto-Setzung bei Persistierung, falls leer) |
| `emailVersandDatum` | `LocalDate` | Datum, an dem zur Anfrage eine E-Mail versendet wurde |
| `bildUrl` | `String` | Profilbild für die Anfrage-Kachel |
| `projektStrasse` | `String` | Straße des Bauvorhabens |
| `projektPlz` | `String` | PLZ des Bauvorhabens |
| `projektOrt` | `String` | Ort des Bauvorhabens |
| `kurzbeschreibung` | `String(1000)` | Kurzbeschreibung (Reiter „Beschreibung“) |
| `abgeschlossen` | `boolean` | „Beendet“-Haken (Default: `false`) |
| `createdAt` | `LocalDateTime` | System-Zeitstempel, Grundlage der Sortierung |
| `kunde` | `Kunde` (FK) | Zugehöriger Kunde (technisch optional, in der Oberfläche Pflicht) |
| `projekt` | `Projekt` (FK) | Nur Altbestand – der aktuelle Code setzt dieses Feld nicht mehr, weil Anfragen beim Übergang ins Projekt gelöscht werden |
| `kundenEmails` | `ElementCollection` | Persistente E-Mail-Liste pro Anfrage |
| `dokumente` | `List<AnfrageDokument>` | Angehängte Dateien (Reiter „Dateien“), eingeteilt in Dokumentgruppen |
| `notizen` | `List<AnfrageNotiz>` | Tagebuch-Einträge |

Die Geschäftsdokumente der Anfrage (Angebot, ggf. Auftragsbestätigung) liegen **nicht** in `dokumente`, sondern als `AusgangsGeschaeftsDokument` mit Verweis `anfrage_id` (siehe `RECHNUNGSWESEN.md`).

### 1.2 Auto-Setzung

Bei `@PrePersist`:
- `createdAt` wird auf `LocalDateTime.now()` gesetzt (falls null)
- `anlegedatum` wird auf `LocalDate.now()` gesetzt (falls null)

---

## 2. Kunden-E-Mails

### 2.1 ElementCollection

Kunden-E-Mails werden als `@ElementCollection` mit Eager-Loading gespeichert:

```
Tabelle: anfrage_kunden_emails
├── anfrage_id (FK)
└── email (String)
```

Beim Anlegen ohne eigene E-Mail-Liste werden die E-Mail-Adressen des gewählten Kunden übernommen. Im Formular heißt das Feld „E-Mail-Adressen“ (z. B. für einen zusätzlichen Statiker).

### 2.2 E-Mail-Zusammenführung

Bei der Rückgabe an die Oberfläche werden die E-Mails des Kunden und die E-Mails der Anfrage zusammengeführt (dedupliziert, Kunden-Adressen zuerst). Das geschieht nur, wenn der Anfrage ein Kunde zugeordnet ist.

```
Kunde-Emails:   [info@firma.de, kontakt@firma.de]
Anfrage-Emails: [info@firma.de, rechnung@firma.de]
                          ↓
Zusammengeführt: [info@firma.de, kontakt@firma.de, rechnung@firma.de]
```

---

## 3. Notizen und Bilder (Reiter „Tagebuch“)

### 3.1 AnfrageNotiz

| Feld | Typ | Beschreibung |
|---|---|---|
| `id` | `Long` | Primärschlüssel |
| `anfrage` | `Anfrage` (FK) | Zugehörige Anfrage |
| `mitarbeiter` | `Mitarbeiter` (FK) | Ersteller der Notiz |
| `notiz` | `String(4000)` | Notiz-Text |
| `mobileSichtbar` | `boolean` | „In mobiler App anzeigen“ (Default: `true`) |
| `nurFuerErsteller` | `boolean` | „Nur für mich sichtbar“ (Default: `false`) |
| `erstelltAm` | `LocalDateTime` | Erstellungszeitpunkt |
| `bilder` | `List<AnfrageNotizBild>` | Angehängte Bilder |

### 3.2 AnfrageNotizBild

| Feld | Typ | Beschreibung |
|---|---|---|
| `id` | `Long` | Primärschlüssel |
| `notiz` | `AnfrageNotiz` (FK) | Zugehörige Notiz |
| `gespeicherterDateiname` | `String` | UUID-basierter Dateiname |
| `originalDateiname` | `String` | Ursprünglicher Dateiname |
| `dateityp` | `String` | MIME-Typ |
| `erstelltAm` | `LocalDateTime` | Upload-Zeitpunkt |

Die Bilder werden über `/api/images/{dateiname}` ausgeliefert, Vorschaubilder über `/api/dokumente/{dateiname}/thumbnail`.

### 3.3 Berechtigungslogik

| Kontext | Bearbeiten | Löschen | Sichtbarkeit |
|---|---|---|---|
| **PC-Frontend** | Alle Notizen | Alle Notizen | Alle (außer `nurFuerErsteller` anderer Mitarbeiter) |
| **Mobile-App** | Nur eigene Notizen | Nur eigene Notizen | Alle (außer `nurFuerErsteller` anderer Mitarbeiter) |

```
hasEditPermission(notiz, requester, isMobile):
    WENN Mobile-Zugriff und Notiz für diesen Mitarbeiter nicht lesbar → false
    WENN requester == null → false
    WENN nicht mobile → true (PC-User dürfen alles)
    SONST → nur wenn notiz.mitarbeiter == requester
```

### 3.4 Privacy-Filter

Notizen mit `nurFuerErsteller = true` sind **nur für den Ersteller sichtbar**. Alle anderen Notizen sind für alle Mitarbeiter einsehbar.

---

## 4. Abschluss-Flag („Beendet“)

### 4.1 Verwendung

Das `abgeschlossen`-Flag zeigt an, ob eine Anfrage erledigt ist – z. B. weil der Kunde abgesagt hat. In der Oberfläche heißt es **„Beendet“**.

| Wert | Bedeutung |
|---|---|
| `false` (Default) | Anfrage ist noch offen |
| `true` | Anfrage ist beendet |

### 4.2 Setzung

- Häkchen „Beendet“ auf der Anfrage-Karte in der Übersicht
- Bei **Erstellung** und **Aktualisierung** optional über das DTO (`abgeschlossen`)

Wird eine Anfrage in ein Projekt übernommen, wird sie **nicht** auf „Beendet“ gesetzt, sondern gelöscht (siehe Abschnitt 6). Die Übersicht zeigt standardmäßig nur offene Anfragen; die mobile App zeigt ebenfalls nur offene Anfragen.

---

## 5. E-Mail-Backfill (`EmailAddressChangedEvent`)

### 5.1 Zweck

Wenn eine Anfrage mit E-Mail-Adressen angelegt oder ihre Adressliste gespeichert wird, sollen **bereits vorhandene, noch nicht zugeordnete E-Mails** mit diesen Adressen nachträglich der Anfrage zugeordnet werden.

### 5.2 Event-Typen

| Factory-Methode | Auslöser |
|---|---|
| `forNewEntity(ANFRAGE, id, emails)` | Neue Anfrage mit E-Mail-Adressen erstellt |
| `forAddressChange(ANFRAGE, id, newEmails, allEmails)` | Anfrage mit E-Mail-Adressen aktualisiert |

### 5.3 Event-Felder

| Feld | Typ | Beschreibung |
|---|---|---|
| `entityType` | `EntityType` | KUNDE, LIEFERANT, ANFRAGE, PROJEKT oder ANGEBOT (ANGEBOT wird vom Listener ignoriert) |
| `entityId` | `Long` | ID der betroffenen Entity |
| `newAddresses` | `List<String>` | Neu hinzugefügte Adressen |
| `allAddresses` | `List<String>` | Alle aktuellen Adressen |
| `newEntity` | `boolean` | Neue Entity erstellt? |

### 5.4 Ablauf

```
Anfrage gespeichert mit E-Mail "info@firma.de"
    │
    └── EmailAddressChangedEvent publiziert
            │
            └── EmailBackfillEventListener (asynchron, nach Commit)
                    │
                    └── Suche nach noch nicht zugeordneten E-Mails
                        mit "info@firma.de"
                        → Zuordnung zur Anfrage
```

---

## 6. Übernahme in ein Projekt

### 6.1 Drei Wege

| Weg | Wo in der Oberfläche | Was passiert |
|---|---|---|
| **Projekt aus Anfrage anlegen** | Projekte → „Neues Projekt“ → Reiter **„Von Anfrage übernehmen“** → Anfrage wählen → „Projekt anlegen“ | Formular wird mit Kunde, Bauvorhaben, Adresse und E-Mails der Anfrage vorbefüllt; Produktkategorien werden aus Auftragsbestätigung bzw. Angebot vorgeschlagen |
| **Anfrage mit bestehendem Projekt zusammenführen** | Projekt öffnen → **„mit Anfrage zusammenführen“** | Nur möglich, wenn das Projekt noch keine eigenen Ausgangsgeschäftsdokumente hat, Projekt und Anfrage denselben Kunden haben und die Anfrage noch keinem Projekt zugeordnet ist |
| **Automatisch bei digitaler Angebotsannahme** | – (läuft im Hintergrund) | Nimmt der Kunde ein Angebot einer Anfrage digital an, legt das System selbst ein Projekt an (Auftragsnummer automatisch, Projektart „Pauschalpreis“, Kategorien aus dem Angebot) und erzeugt die Auftragsbestätigung (siehe `ANGEBOTSWESEN.md`) |

### 6.2 Was übernommen wird

```
Anfrage
    │
    └── [Übernahme]
            │
            ├── Dateien der Anfrage → ins Projekt verschoben
            ├── E-Mail-Verlauf → dem Projekt zugeordnet
            ├── Tagebuch-Notizen (inkl. Bilder) → als Projekt-Notizen kopiert
            ├── Kalendereinträge → ins Projekt übernommen
            ├── Geschäftsdokumente (Angebot, AB …) → migrateFromAnfrageToProjekt()
            ├── Projektbild → von der Anfrage, falls das Projekt noch keins hat
            │   (nur bei Projektanlage, nicht beim Zusammenführen)
            │
            └── Anfrage wird gelöscht
```

Beim Zusammenführen werden zusätzlich die Kontakt-E-Mails und die Kurzbeschreibung der Anfrage in das Projekt übernommen.

### 6.3 Dokumenten-Migration (`migrateFromAnfrageToProjekt()`)

1. Alle `AusgangsGeschaeftsDokument`-Einträge der Anfrage werden dem Projekt zugeordnet
2. Die Anfrage-Referenz wird entfernt (`anfrage = null`), damit die Anfrage gelöscht werden kann
3. Projektkategorien werden aus den Positionen der Dokumente automatisch abgeleitet (Auftragsbestätigung hat Vorrang vor dem Angebot)

### 6.4 Dokumentkette nach Übernahme

```
Angebot (AG-…)
    └── Auftragsbestätigung (AB-…)
        ├── Abschlagsrechnung(en) (AR-…)
        └── Schlussrechnung (SR-…)
```

Eine Auftragsbestätigung zu einem Angebot entsteht erst im Projekt oder automatisch bei digitaler Annahme. In der Anfrage selbst gibt es keine Rechnungen.

---

## 7. Anfrage löschen

Der Knopf **„Löschen“** im Kopf der Anfrage löscht nur, solange noch nichts Geschäftsrelevantes an der Anfrage hängt. Sonst antwortet der Server mit HTTP 409 und einem Hinweis:

| Grund (`LoeschGrund`) | Hinweis |
|---|---|
| `IN_PROJEKT_UMGEWANDELT` | Anfrage ist bereits in ein Projekt umgewandelt |
| `EMAIL_VERSENDET` | Es wurde bereits eine E-Mail zu dieser Anfrage versendet |
| `EMAIL_VORHANDEN` | An der Anfrage hängen bereits E-Mails |
| `GESCHAEFTSDOKUMENT_VORHANDEN` | An der Anfrage hängen Angebote/Auftragsbestätigungen bzw. ausgehende Geschäftsdokumente |
| `DATEI_VORHANDEN` | An der Anfrage hängen Dateien – bitte zuerst entfernen |
| `BENUTZER_NOTIZ_VORHANDEN` | Es wurden bereits Tagebuch-Notizen von Mitarbeitern erfasst |
| `NICHT_GEFUNDEN` | Anfrage existiert nicht (mehr) → HTTP 404 |

Notizen und Bilder, die automatisch von einer Webseiten-Anfrage stammen, blockieren das Löschen nicht. Mit `cascadeKunde=true` wird zusätzlich der Kunde gelöscht, wenn er danach weder Anfragen noch Projekte hat und an keinen anderen Daten hängt.

---

## 8. Anfragen von der Webseite

Über das Anfrage-Formular der eigenen Webseite kommen Anfragen automatisch ins Programm (Einrichtung: `ANFRAGE_FUNNEL_SETUP.md`).

- Die Webseite schickt die Anfrage Server-zu-Server an `POST /api/internal/anfrage` (geschützt über einen gemeinsamen Schlüssel bzw. Cloudflare Access).
- Das Programm legt einen Kunden an (oder findet einen passenden Bestandskunden) und erstellt die Anfrage.
- Text und Fotos des Kunden landen als Tagebuch-Eintrag des System-Mitarbeiters „Webseite“.
- Optional: automatische Bestätigungs-E-Mail an den Kunden und eine KI-Prüfung auf offensichtliche Spaß-/Spam-Anfragen.
- In der Übersicht lassen sich diese Anfragen über den Filter **Herkunft → „Über die Webseite“** finden.

---

## 9. Konversionsrate

Unter Finanzen & Controlling → Auswertung → **Erfolgsanalyse** zeigt die Kachel **„Konversionsrate“**, wie viele Anfragen eines Jahres zu Projekten wurden:

```
Konversionsrate = Projekte des Jahres / (Projekte des Jahres + noch vorhandene Anfragen des Jahres) × 100
```

Gezählt wird nach Anlegedatum. Weil übernommene Anfragen gelöscht werden, steckt jede gewonnene Anfrage im Projektzähler.

---

## 10. API-Endpoints

### 10.1 Anfrage-CRUD

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/api/anfragen` | Neue Anfrage erstellen (JSON oder Multipart mit optionalem Bild `imageFile`) |
| `GET` | `/api/anfragen` | Anfragen suchen/filtern (flache Liste) |
| `GET` | `/api/anfragen?page=…&size=…` | Seitenweise Liste für die Anfragenübersicht (Filter `freigabe`, `status`, `herkunft`, `sortierung`) |
| `GET` | `/api/anfragen/jahre` | Verfügbare Jahre für den Filter |
| `GET` | `/api/anfragen/{id}` | Einzelne Anfrage abrufen |
| `PUT` | `/api/anfragen/{id}` | Anfrage aktualisieren (JSON oder Multipart mit Bild) |
| `DELETE` | `/api/anfragen/{id}` | Anfrage löschen (mit Prüfung, optional `cascadeKunde=true`) |
| `PATCH` | `/api/anfragen/{id}/kurzbeschreibung` | Kurzbeschreibung aktualisieren (Text) |
| `GET` | `/api/anfragen/{id}/projekt-vorlage` | Vorlage für die Projektanlage aus den Anfragedaten |
| `GET` | `/api/anfragen/{id}/produktkategorien-vorschlag` | Produktkategorien aus AB bzw. Angebot als Vorschlag |
| `GET` | `/api/anfragen/freigabe-status?ids=…` | Jüngste digitale Freigabe je Anfrage (für die Status-Plaketten) |

### 10.2 Dateien

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/api/anfragen/{id}/dokumente` | Datei(en) anhängen (optional `gruppe`) |
| `GET` | `/api/anfragen/{id}/dokumente` | Dateien auflisten (optional `gruppe`) |
| `GET` | `/api/anfragen/{id}/email-dokumente` | Versandfähige Dokumente für den E-Mail-Versand |
| `DELETE` | `/api/anfragen/{id}/dokumente/{docId}` | Datei entfernen |

### 10.3 Notizen

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/api/anfragen/{id}/notizen` | Notizen auflisten (mit Privacy-Filter) |
| `POST` | `/api/anfragen/{id}/notizen` | Neue Notiz erstellen |
| `PATCH` | `/api/anfragen/{id}/notizen/{notizId}` | Notiz bearbeiten (Berechtigungsprüfung) |
| `DELETE` | `/api/anfragen/{id}/notizen/{notizId}` | Notiz löschen (inkl. Bilder) |

### 10.4 Notiz-Bilder

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/api/anfragen/{id}/notizen/{notizId}/bilder` | Bild hochladen |
| `DELETE` | `/api/anfragen/{id}/notizen/{notizId}/bilder/{bildId}` | Bild löschen |

### 10.5 E-Mails

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/api/anfragen/{id}/emails` | Eine E-Mail-Adresse zur Anfrage hinzufügen |

### 10.6 ZUGFeRD (Altfunktionen)

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/api/anfragen/zugferd/extract` | ZUGFeRD-Daten aus einem hochgeladenen PDF lesen |
| `POST` | `/api/anfragen/zugferd/extract-ai` | Daten per KI aus einem PDF lesen (Standard-Dokumenttyp „Angebot“) |
| `POST` | `/api/anfragen/{id}/zugferd` | ZUGFeRD-PDF aus hochgeladenem PDF + Daten erzeugen und an der Anfrage ablegen |

Diese Endpunkte werden von der PC-Oberfläche derzeit nicht aufgerufen; Angebote entstehen im Dokument-Editor.

### 10.7 Webseiten-Anfragen

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/api/internal/anfrage` | Eingang vom Webseiten-Formular (Multipart: Teil `anfrage` als JSON, Teil `bilder` optional; oder reines JSON) |

### 10.8 Suchoptionen

Die Suche unterstützt:
- **Freitextsuche** (`q`): Über Bauvorhaben, Kundenname oder E-Mail
- **Strukturierte Suche**: Jahr, Kundenname (`kundenname` oder `kunde`), Bauvorhaben, Anfragenummer (`anfragesnummer`)
- **Filter**: `nurOhneProjekt` (nur Anfragen ohne verknüpftes Projekt)
- **Nur Seitenliste**: `freigabe` (all/accepted/pending/expired), `status` (offen/beendet/alle), `herkunft` (webseite/manuell/alle), `sortierung` (neu/alt); Seitengröße Standard 12, höchstens 100

---

## 11. Zusammenfassung

| Aspekt | Umsetzung | Entity/Service |
|---|---|---|
| Anfrage-CRUD | Vollständiger Lebenszyklus mit Bild-Support, Versionsnummer gegen gleichzeitiges Speichern | `Anfrage`, `AnfrageService` |
| Kunden-E-Mails | ElementCollection, deduplizierte Zusammenführung | `anfrage_kunden_emails` |
| Tagebuch | 1:N Notizen mit 1:N Bildern, Privacy-Logik | `AnfrageNotiz`, `AnfrageNotizBild` |
| Beendet | `abgeschlossen`-Flag für erledigte Anfragen | `Anfrage` |
| E-Mail-Backfill | Event-basierte nachträgliche Zuordnung | `EmailAddressChangedEvent`, `EmailBackfillEventListener` |
| Übernahme ins Projekt | Dateien, E-Mails, Notizen, Termine und Geschäftsdokumente wandern ins Projekt, Anfrage wird gelöscht | `ProjektManagementService`, `AusgangsGeschaeftsDokumentService` |
| Löschen | Nur ohne geschäftsrelevante Anhänge | `AnfrageService.loescheMitPruefung` |
| Webseiten-Anfragen | Automatische Anlage aus dem Webseiten-Formular | `AnfrageFunnelService` |
