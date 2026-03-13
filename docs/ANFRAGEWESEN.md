# Anfrageswesen – Anfrageserstellung, Kundenmanagement, Konversionsrate

## Übersicht

Dieses Dokument beschreibt das Anfrageswesen im Kalkulationsprogramm – von der Erstellung eines Anfrages über die Verwaltung von Notizen und Bildern bis zur Konversion zum Projekt.

---

## 1. Anfrage-Entity

### 1.1 Felder

| Feld | Typ | Beschreibung |
|---|---|---|
| `id` | `Long` | Primärschlüssel |
| `bauvorhaben` | `String` | Name des Bauvorhabens |
| `betrag` | `BigDecimal` | Anfragesbetrag |
| `anlegedatum` | `LocalDate` | Erstellungsdatum (Auto-Setzung bei Persistierung) |
| `emailVersandDatum` | `LocalDate` | Datum des E-Mail-Versands |
| `bildUrl` | `String` | Hero-Image URL für UI-Karten |
| `projektStrasse` | `String` | Straße des Bauvorhabens |
| `projektPlz` | `String` | PLZ des Bauvorhabens |
| `projektOrt` | `String` | Ort des Bauvorhabens |
| `kurzbeschreibung` | `String(1000)` | Kurzbeschreibung des Anfrages |
| `abgeschlossen` | `boolean` | Abschluss-Flag (Default: `false`) |
| `createdAt` | `LocalDateTime` | System-Zeitstempel |
| `kunde` | `Kunde` (FK) | Zugehöriger Kunde |
| `projekt` | `Projekt` (FK) | Verknüpftes Projekt (nach Konversion) |
| `kundenEmails` | `ElementCollection` | Persistente E-Mail-Liste pro Anfrage |
| `dokumente` | `List<AnfrageDokument>` | Angehängte Dokumente |
| `notizen` | `List<AnfrageNotiz>` | Notizen zum Anfrage |

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

### 2.2 E-Mail-Zusammenführung

Bei der DTO-Rückgabe werden die E-Mails des Anfrages mit den E-Mails des Kunden zusammengeführt (dedupliziert):

```
Anfrage-Emails: [info@firma.de, rechnung@firma.de]
Kunde-Emails:   [info@firma.de, kontakt@firma.de]
                          ↓
Zusammengeführt: [info@firma.de, rechnung@firma.de, kontakt@firma.de]
```

---

## 3. Notizen und Bilder

### 3.1 AnfrageNotiz

| Feld | Typ | Beschreibung |
|---|---|---|
| `id` | `Long` | Primärschlüssel |
| `anfrage` | `Anfrage` (FK) | Zugehöriges Anfrage |
| `mitarbeiter` | `Mitarbeiter` (FK) | Ersteller der Notiz |
| `notiz` | `String(4000)` | Notiz-Text |
| `mobileSichtbar` | `boolean` | Auf Mobile-App sichtbar (Default: `true`) |
| `nurFuerErsteller` | `boolean` | Nur für Ersteller sichtbar (Default: `false`) |
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

### 3.3 Berechtigungslogik

| Kontext | Bearbeiten | Löschen | Sichtbarkeit |
|---|---|---|---|
| **PC-Frontend** | Alle Notizen | Alle Notizen | Alle (außer `nurFuerErsteller` anderer User) |
| **Mobile-App** | Nur eigene Notizen | Nur eigene Notizen | Alle (außer `nurFuerErsteller` anderer User) |

```
hasEditPermission(notiz, requester, isMobile):
    WENN requester == null → false
    WENN nicht mobile → true (PC-User dürfen alles)
    SONST → nur wenn notiz.mitarbeiter == requester
```

### 3.4 Privacy-Filter

Notizen mit `nurFuerErsteller = true` sind **nur für den Ersteller sichtbar**. Alle anderen Notizen sind für alle Mitarbeiter einsehbar.

---

## 4. Abschluss-Flag

### 4.1 Verwendung

Das `abgeschlossen`-Flag zeigt an, ob ein Anfrage abgeschlossen (bearbeitet/gewonnen) ist:

| Wert | Bedeutung |
|---|---|
| `false` (Default) | Anfrage ist noch offen/aktiv |
| `true` | Anfrage ist abgeschlossen |

### 4.2 Setzung

- Bei **Erstellung**: Optional via DTO (`dto.getAbgeschlossen()`)
- Bei **Aktualisierung**: Optional via DTO
- Typischerweise auf `true` gesetzt, wenn das Anfrage in ein Projekt überführt wird

---

## 5. E-Mail-Backfill (`EmailAddressChangedEvent`)

### 5.1 Zweck

Wenn einem Anfrage eine neue E-Mail-Adresse hinzugefügt wird, sollen **historische E-Mails** mit dieser Adresse nachträglich dem Anfrage zugeordnet werden.

### 5.2 Event-Typen

| Factory-Methode | Auslöser |
|---|---|
| `forNewEntity(ANFRAGE, id, emails)` | Neues Anfrage erstellt |
| `forAddressChange(ANFRAGE, id, newEmails, allEmails)` | E-Mail-Adressen geändert |

### 5.3 Event-Felder

| Feld | Typ | Beschreibung |
|---|---|---|
| `entityType` | `EntityType` | KUNDE, LIEFERANT, ANFRAGE oder PROJEKT |
| `entityId` | `Long` | ID der betroffenen Entity |
| `newAddresses` | `List<String>` | Neu hinzugefügte Adressen |
| `allAddresses` | `List<String>` | Alle aktuellen Adressen |
| `newEntity` | `boolean` | Neue Entity erstellt? |

### 5.4 Ablauf

```
Anfrage erstellt mit E-Mail "info@firma.de"
    │
    └── EmailAddressChangedEvent publiziert
            │
            └── EmailAutoAssignmentService
                    │
                    └── Suche nach historischen E-Mails
                        mit "info@firma.de"
                        → Zuordnung zum Anfrage
```

---

## 6. Konversion zum Projekt

### 6.1 Ablauf

```
Anfrage
    │
    └── [Konversion]
            │
            ├── Neues Projekt erstellt
            │
            ├── Anfrage.projekt = neues Projekt
            │
            ├── Alle Dokumente des Anfrages → Projekt zugeordnet
            │   (anfrage-Referenz entfernt)
            │
            └── Projektkategorien aus Dokumenten-Positionen abgeleitet
```

### 6.2 Dokumenten-Migration (`migrateFromAnfrageToProjekt()`)

1. Alle `AusgangsGeschaeftsDokument`-Einträge des Anfrages werden dem neuen Projekt zugeordnet
2. Die Anfrage-Referenz wird entfernt (`anfrage = null`)
3. Projektkategorien werden aus den Positionen der Dokumente automatisch erstellt

### 6.3 Dokumentkette nach Konversion

```
Anfrage (ANFRAGE)
    └── Auftragsbestätigung (AUFTRAGSBESTAETIGUNG)
        └── Abschlagsrechnung(en)
            └── Schlussrechnung
```

---

## 7. API-Endpoints

### 7.1 Anfrage-CRUD

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/api/anfragen` | Neues Anfrage erstellen (mit optionalem Bild) |
| `GET` | `/api/anfragen` | Anfragen suchen/filtern |
| `GET` | `/api/anfragen/{id}` | Einzelnes Anfrage abrufen |
| `PUT` | `/api/anfragen/{id}` | Anfrage aktualisieren |
| `DELETE` | `/api/anfragen/{id}` | Anfrage löschen |
| `PATCH` | `/api/anfragen/{id}/kurzbeschreibung` | Kurzbeschreibung aktualisieren |

### 7.2 Dokumente

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/api/anfragen/{id}/dokumente` | Dokument anhängen |
| `GET` | `/api/anfragen/{id}/dokumente` | Dokumente auflisten |
| `DELETE` | `/api/anfragen/{id}/dokumente/{docId}` | Dokument entfernen |
| `POST` | `/api/anfragen/{id}/zugferd` | ZUGFeRD-Dokument generieren |

### 7.3 Notizen

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/api/anfragen/{id}/notizen` | Notizen auflisten (mit Privacy-Filter) |
| `POST` | `/api/anfragen/{id}/notizen` | Neue Notiz erstellen |
| `PATCH` | `/api/anfragen/{id}/notizen/{notizId}` | Notiz bearbeiten (Berechtigungsprüfung) |
| `DELETE` | `/api/anfragen/{id}/notizen/{notizId}` | Notiz löschen |

### 7.4 Notiz-Bilder

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/api/anfragen/{id}/notizen/{notizId}/bilder` | Bild hochladen |
| `DELETE` | `/api/anfragen/{id}/notizen/{notizId}/bilder/{bildId}` | Bild löschen |

### 7.5 E-Mails

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/api/anfragen/{id}/emails` | E-Mail zum Anfrage hinzufügen |

### 7.6 Suchoptionen

Die Suche unterstützt:
- **Freitextsuche**: Über Bauvorhaben, Kundenname oder E-Mail
- **Strukturierte Suche**: Jahr, Kundenname, Projekt, Anfragesnummer
- **Filter**: `nurOhneProjekt` (nur Anfragen ohne verknüpftes Projekt)

---

## 8. Zusammenfassung

| Aspekt | Umsetzung | Entity/Service |
|---|---|---|
| Anfrage-CRUD | Vollständiger Lebenszyklus mit Bild-Support | `Anfrage`, `AnfrageService` |
| Kunden-E-Mails | ElementCollection, deduplizierte Zusammenführung | `anfrage_kunden_emails` |
| Notizen & Bilder | 1:N Notizen mit 1:N Bildern, Privacy-Logik | `AnfrageNotiz`, `AnfrageNotizBild` |
| Abschluss | `abgeschlossen`-Flag für Status-Tracking | `Anfrage` |
| E-Mail-Backfill | Event-basierte nachträgliche Zuordnung | `EmailAddressChangedEvent` |
| Projekt-Konversion | Dokumente + Kategorien migrieren | `AnfrageService`, `AusgangsGeschaeftsDokumentService` |
