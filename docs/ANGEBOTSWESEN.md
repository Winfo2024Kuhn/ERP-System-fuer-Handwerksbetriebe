# Angebotswesen – Angebotserstellung, Kundenmanagement, Konversionsrate

## Übersicht

Dieses Dokument beschreibt das Angebotswesen im Kalkulationsprogramm – von der Angebotserstellung über die Kundenkommunikation und Notizenverwaltung bis zur Konversion zum Projekt.

---

## 1. Angebot-Entity

### 1.1 Felder

| Feld | Typ | Beschreibung |
|---|---|---|
| `id` | `Long` | Primärschlüssel |
| `bauvorhaben` | `String` | Bezeichnung des Bauvorhabens |
| `betrag` | `BigDecimal` | Angebotsbetrag |
| `anlegedatum` | `LocalDate` | Datum der Angebotserstellung |
| `emailVersandDatum` | `LocalDate` | Datum des E-Mail-Versands |
| `bildUrl` | `String` | Hero-Image URL für UI-Karten |
| `projektStrasse` | `String` | Straße des Projektstandorts |
| `projektPlz` | `String` | PLZ des Projektstandorts |
| `projektOrt` | `String` | Ort des Projektstandorts |
| `kurzbeschreibung` | `String` | Kurzbeschreibung des Angebots |
| `kunde` | `Kunde` (FK) | Zugehöriger Kunde |
| `projekt` | `Projekt` (FK) | Zugehöriges Projekt (nach Konversion) |
| `abgeschlossen` | `Boolean` | `true` wenn Angebot abgeschlossen |
| `createdAt` | `LocalDateTime` | Erstellungszeitpunkt |

### 1.2 Beziehungen

| Beziehung | Typ | Beschreibung |
|---|---|---|
| `dokumente` | 1:N `AngebotDokument` | Zugehörige Angebotsdokumente |
| `kundenEmails` | `ElementCollection` | Persistente E-Mail-Liste pro Angebot |
| `notizen` | 1:N `AngebotNotiz` | Notizen zum Angebot |

---

## 2. Kunden-E-Mails

### 2.1 ElementCollection

Jedes Angebot verfügt über eine **persistente E-Mail-Liste** (`kundenEmails`), gespeichert als `ElementCollection`. Diese E-Mails werden für die Kommunikation mit dem Kunden verwendet.

### 2.2 E-Mail-Backfill (`EmailAddressChangedEvent`)

Wenn sich die E-Mail-Adresse eines Kunden ändert, wird ein `EmailAddressChangedEvent` ausgelöst. Dadurch werden bestehende Angebote nachträglich mit der neuen E-Mail-Adresse verknüpft.

---

## 3. Notizen & Bilder

### 3.1 Entity `AngebotNotiz`

| Feld | Typ | Beschreibung |
|---|---|---|
| `id` | `Long` | Primärschlüssel |
| `angebot` | `Angebot` (FK) | Zugehöriges Angebot |
| `mitarbeiter` | `Mitarbeiter` (FK) | Ersteller der Notiz |
| `notiz` | `String` (max. 4000) | Notiztext |
| `mobileSichtbar` | `Boolean` | Sichtbar auf der mobilen App |
| `nurFuerErsteller` | `Boolean` | Nur für den Ersteller sichtbar |
| `erstelltAm` | `LocalDateTime` | Erstellungszeitpunkt |
| `bilder` | 1:N `AngebotNotizBild` | Angehängte Bilder |

### 3.2 Entity `AngebotNotizBild`

| Feld | Typ | Beschreibung |
|---|---|---|
| `id` | `Long` | Primärschlüssel |
| `notiz` | `AngebotNotiz` (FK) | Zugehörige Notiz |
| `gespeicherterDateiname` | `String` | UUID-basierter Dateiname |
| `originalDateiname` | `String` | Originaler Dateiname |
| `dateityp` | `String` | MIME-Typ des Bildes |
| `erstelltAm` | `LocalDateTime` | Erstellungszeitpunkt |

### 3.3 Bild-Upload

Bilder werden im Angebots-Upload-Verzeichnis gespeichert (`file.offer-upload-dir`). Der Dateiname wird durch eine UUID ersetzt, um Kollisionen zu vermeiden.

---

## 4. Abschluss-Flag

### 4.1 Zweck

Das `abgeschlossen`-Flag markiert ein Angebot als abgeschlossen (angenommen oder abgelehnt). Abgeschlossene Angebote können in der UI gefiltert werden.

### 4.2 Status-Übergänge

```
OFFEN (abgeschlossen = false)
   │
   ├─[Kunde nimmt an]──► ABGESCHLOSSEN + Projekt erstellen
   │
   └─[Kunde lehnt ab]──► ABGESCHLOSSEN (ohne Projekt)
```

---

## 5. Konversion zum Projekt

### 5.1 Ablauf

```
Angebot (Bauvorhaben, Betrag, Kundendaten)
    │
    ▼
[Projekt erstellen / migrieren]
    │
    ├── Alle Dokumente des Angebots → Projekt zuordnen
    ├── Angebot-Referenz entfernen (angebot = null auf Dokumenten)
    ├── Projektkategorien aus Positionen ableiten
    │
    ▼
Projekt (mit übernommenen Dokumenten)
    │
    ▼
Auftragsbestätigung erstellen
    │
    ▼
Rechnungen erstellen (Abschlags- / Schlussrechnung)
```

### 5.2 Migration (`migrateFromAngebotToProjekt()`)

Bei der Migration werden:
1. Alle `AusgangsGeschaeftsDokumente` des Angebots dem neuen Projekt zugeordnet
2. Die `angebot`-Referenz auf den Dokumenten entfernt
3. Projektkategorien automatisch aus den Dokumenten-Positionen abgeleitet

### 5.3 Dokumentkette nach Konversion

```
[Angebot-Phase]                    [Projekt-Phase]
Angebot                            Auftragsbestätigung (AB)
  └── Angebotsdokumente              ├── Abschlagsrechnung 1
                                     ├── Abschlagsrechnung 2
                                     └── Schlussrechnung
```

---

## 6. Projekt-Vorlage

### 6.1 Endpoint

```
GET /api/angebote/{id}/projekt-vorlage
```

Gibt eine Vorlage für die Projekt-Erstellung basierend auf den Angebotsdaten zurück. Enthält:
- Bauvorhaben als Projektname
- Kundenreferenz
- Projektadresse (Straße, PLZ, Ort)
- Angebotsbetrag als Planwert

---

## 7. E-Mail-Integration

### 7.1 E-Mail-Versand

Angebote können per E-Mail an den Kunden versendet werden:

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/api/angebote/{angebotId}/emails` | E-Mail mit Angebot versenden |

### 7.2 E-Mail-Dokumente

Dem Angebot zugeordnete E-Mails können abgerufen werden:

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/api/angebote/{angebotID}/email-dokumente` | E-Mail-Dokumente des Angebots |

---

## 8. API-Referenz

### 8.1 Basis-URL

```
/api/angebote
```

### 8.2 Angebotsverwaltung

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Alle Angebote auflisten |
| `GET` | `/{id}` | Einzelnes Angebot abrufen |
| `GET` | `/jahre` | Verfügbare Jahre (für Filter) |
| `POST` | `/` | Neues Angebot erstellen |

### 8.3 Dokumente

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/{angebotID}/dokumente` | Dokumente eines Angebots |
| `POST` | `/{angebotID}/dokumente` | Dokument zum Angebot hochladen |

### 8.4 Notizen

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/{angebotId}/notizen` | Notizen eines Angebots |
| `POST` | `/{angebotId}/notizen` | Neue Notiz erstellen |
| `POST` | `/{angebotId}/notizen/{notizId}/bilder` | Bild zu einer Notiz hochladen |

### 8.5 ZUGFeRD

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/zugferd/extract` | ZUGFeRD-Daten aus PDF extrahieren |
| `POST` | `/zugferd/extract-ai` | KI-basierte Extraktion |
| `POST` | `/{angebotID}/zugferd` | ZUGFeRD-PDF erstellen |

### 8.6 Projekt-Konversion

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/{id}/projekt-vorlage` | Projekt-Vorlage aus Angebot generieren |

### 8.7 Request-DTO (`AngebotErstellenDto`)

| Feld | Beschreibung |
|---|---|
| `bauvorhaben` | Bezeichnung des Bauvorhabens |
| `kundenEmails` | Liste der Kunden-E-Mail-Adressen |
| `projektStrasse` | Straße des Projektstandorts |
| `projektPlz` | PLZ |
| `projektOrt` | Ort |
| `kurzbeschreibung` | Kurzbeschreibung |
| `abgeschlossen` | Abschluss-Status |

---

## 9. Entitäten-Übersicht

| Entity | Zweck |
|---|---|
| `Angebot` | Angebots-Entity mit Kundendaten und Projektadresse |
| `AngebotNotiz` | Notizen zum Angebot mit Sichtbarkeitssteuerung |
| `AngebotNotizBild` | Bilder zu Angebotsnotizen |
| `AngebotDokument` | Dokumentcontainer für Angebote |
| `AngebotGeschaeftsdokument` | Geschäftsmetadaten für Angebotsdokumente |

| Service | Verantwortung |
|---|---|
| `AngebotService` | Angebotserstellung, Dokumenten-Upload, E-Mail-Handling |
| `AusgangsGeschaeftsDokumentService` | Dokumentenkonversion und -verwaltung |
| `AngebotMapper` | DTO ↔ Entity Mapping |
