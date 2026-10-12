# Angebotswesen – Angebotserstellung, Kundenmanagement, Konversionsrate

## Übersicht

Dieses Dokument beschreibt das Angebotswesen im Kalkulationsprogramm – vom Schreiben eines Angebots im Dokument-Editor über den Versand und die digitale Annahme durch den Kunden bis zur Auftragsbestätigung und zum Projekt.

> **Wichtig – Begriffswechsel:** Früher gab es im Code eine eigene Entity `Angebot` mit Notizen, Bildern und E-Mail-Liste. Diese heißt heute **`Anfrage`** und ist in `ANFRAGEWESEN.md` beschrieben. Ein **Angebot** ist heute ein **Geschäftsdokument** (`AusgangsGeschaeftsDokument` mit Typ `ANGEBOT` oder `NACHTRAGSANGEBOT`), das an einer Anfrage oder an einem Projekt hängt. Die alten Endpunkte unter `/api/angebote` gibt es nicht mehr. Kundendaten und E-Mail-Adressen hängen an der Anfrage bzw. am Projekt; die Konversionsrate (Anfragen → Projekte) steht in `ANFRAGEWESEN.md`, Abschnitt 9.

---

## 1. Was ist ein Angebot im System?

### 1.1 Dokumenttypen

| Typ | In der Oberfläche | Nummer (Beispiel) | Wo anlegbar |
|---|---|---|---|
| `ANGEBOT` | Angebot | `AG-2026/10/00012` | Anfrage oder Projekt – als erstes Dokument (Basisdokument) |
| `NACHTRAGSANGEBOT` | Nachtragsangebot | `NA-2026/10/00031` | Nur im Projekt, und nur wenn dort schon ein Angebot existiert |

Beide verhalten sich gleich: eigener Vorgang mit eigener Auftragsbestätigung und eigenen Rechnungen, digitale Annahme möglich. Das Nachtragsangebot ist für zusätzliche Leistungen gedacht, die nach dem ursprünglichen Angebot dazukommen.

Die Nummer wird beim ersten Speichern vergeben (Format `{Kürzel}-JJJJ/MM/NNNNN`, ein gemeinsamer Monatszähler für alle Dokumenttypen, siehe `RECHNUNGSWESEN.md`).

### 1.2 Regeln für Basisdokumente

- Pro Anfrage bzw. Projekt gibt es **höchstens ein Angebot** – es ist das erste Dokument des Vorgangs (Basisdokument).
- Jedes **weitere** Basisdokument muss ein **Nachtragsangebot** sein (nur im Projekt).
- Im Projekt ist „Angebot“ im Dialog „Dokument erstellen“ dann ausgegraut („Es existiert bereits ein Basisdokument – weitere nur als Nachtragsangebot“). In der Anfrage ist der Knopf „Dokument erstellen“ ganz gesperrt, sobald es ein Basisdokument gibt.
- Ein Nachtragsangebot ohne vorhandenes Angebot lehnt der Server ab („Ein Nachtragsangebot benötigt ein bestehendes Angebot als Basisdokument.“).

### 1.3 Wichtige Felder (Auszug aus `AusgangsGeschaeftsDokument`)

| Feld | Bedeutung für Angebote |
|---|---|
| `dokumentNummer` | z. B. `AG-2026/10/00012` – erscheint in der Anfrage auch als „Anfragenummer“ |
| `betreff` | Betreff/Titel, beim Anlegen aus der Anfrage meist das Bauvorhaben |
| `positionenJson` | Leistungen, Texte, Bauabschnitte, Wahlpositionen, Rabatt |
| `betragNetto` / `betragBrutto` / `mwstSatz` | Beträge, MwSt-Standard 19 % |
| `anfrage` / `projekt` / `kunde` | Zuordnung |
| `versandDatum` | Gesetzt beim endgültigen E-Mail-Versand |
| `digitalAngenommen` | `true` nach digitaler Annahme → Angebot ist **verbindlich und gesperrt** |
| `gebucht` | Bei Angeboten immer `false` – Angebote werden nie gebucht |

---

## 2. Angebot erstellen und bearbeiten

### 2.1 So legen Sie ein Angebot an

1. **Anfrage** öffnen (Projektmanagement → Aufträge → Anfragen) oder **Projekt** öffnen.
2. Reiter **„Geschäftsdokumente“** → Knopf **„Dokument erstellen“**.
3. **„Angebot“** wählen (im Projekt bei Bedarf **„Nachtragsangebot“**).
4. Der **Dokument-Editor** öffnet sich in einem neuen Tab.

### 2.2 Werkzeuge im Dokument-Editor

| Knopf | Wofür |
|---|---|
| **Textbaustein** | Vorgefertigte Texte einfügen (Vorlagen unter Vorlagen & Stammdaten → Textvorlagen) |
| **Leistung** | Leistung aus dem Leistungskatalog einfügen |
| **Stundensätze** | Arbeitszeit mit hinterlegtem Stundensatz einfügen |
| **Material** | Artikel aus dem Artikelstamm als Position einfügen |
| **Bauabschnitt** | Überschrift für einen Abschnitt (fasst Positionen zusammen, mit Zwischensumme) |
| **Trennlinie** | Horizontale Linie |
| **Rabatt** | Rabatt auf einzelne Positionen oder Pauschalrabatt auf das ganze Dokument |
| **GAEB** | Leistungsverzeichnis im GAEB-Format importieren (`.xml`, `.x83`, `.gaeb`) |
| **Speichern** | Zwischenspeichern |
| Pfeile ↶ ↷ | Rückgängig / Wiederholen – bis zu 20 Schritte (Strg+Z / Strg+Y) |

Standard-Vor- und Nachtexte für den Dokumenttyp werden automatisch eingesetzt.

### 2.3 Wahlpositionen (optional / alternativ)

Jede Leistung hat einen Knopf **„Kunde wählt“** mit drei Möglichkeiten:

| Auswahl | Bedeutung |
|---|---|
| **Fest beauftragt** | Zählt ganz normal in die Summe |
| **Optional** | Kunde kann sie dazubuchen – zählt **nicht** in die Angebotssumme |
| **Alternativ** | Kunde wählt eine aus mehreren („Alternative Leistungen“) – zählt **nicht** in die Angebotssumme |

Bei der digitalen Annahme wählt der Kunde die gewünschten Optionen; je Alternativgruppe muss er sich für eine Variante entscheiden. In die Auftragsbestätigung kommen nur die tatsächlich beauftragten Positionen.

### 2.4 Bearbeitbarkeit

- Angebote werden **nie gebucht** und bleiben bearbeitbar – auch nach dem Versand (Verhandlungsspielraum).
- **Gesperrt** ist ein Angebot erst, wenn der Kunde es **digital angenommen** hat (Plakette „Verbindlich“). Für Änderungen danach legen Sie im Projekt ein Nachtragsangebot an.
- **Löschen** ist nur möglich, solange das Angebot noch nicht per E-Mail versendet wurde – mit Pflicht-Begründung (siehe `DOKUMENTEN_LIFECYCLE.md`).
- Öffnet ein Kollege dasselbe Dokument, sieht er nur den aktuellen Stand („… bearbeitet das gerade — Sie sehen den aktuellen Stand.“).

---

## 3. Versand an den Kunden

### 3.1 Knöpfe im Dokument-Editor

| Knopf | Was passiert |
|---|---|
| **E-Mail** | Format wählen (**„Standard PDF“** oder **„ZUGFeRD PDF“**) → bei Angeboten zusätzlich die Gültigkeit des Annahme-Links wählen → E-Mail verfassen und senden. Danach wird das Versanddatum gesetzt. |
| **Entwurf** | „Entwurf mit Wasserzeichen senden – Dokument bleibt offen und änderbar“. Das PDF trägt das Wasserzeichen „ENTWURF“, es entsteht **kein** Annahme-Link und kein Versanddatum. |
| **Drucken** | Drucken, wahlweise „Mit Hintergrund drucken“ (Briefkopf und Hintergrundbild). Angebote werden dabei nicht gebucht. |
| **PDF** | PDF herunterladen (Standard oder ZUGFeRD). |

Beim endgültigen Versand, Drucken oder PDF-Export wird das Dokumentdatum auf den heutigen Tag gesetzt.

### 3.2 Gültigkeit des Annahme-Links

Beim E-Mail-Versand eines Angebots oder Nachtragsangebots fragt das Programm: **„Wie lange soll der Annahme-Link gültig sein?“**

- Schnellauswahl: 7, 14, 21 oder 30 Tage, oder ein eigenes Datum
- Vorgabe: **14 Tage**, zulässig 1 bis 365 Tage
- Wird ein Angebot erneut versendet, werden ältere, noch offene Links zurückgezogen („Link zurückgezogen“).
- Nach der Annahme wird kein neuer Annahme-Link mehr erzeugt; eine erneute E-Mail enthält stattdessen den Hinweis, dass das Angebot bereits angenommen wurde.

Voraussetzung für den Link ist eine eingerichtete öffentliche Adresse der Freigabe-Seite (Einstellung `freigabe.public-base-url`, meist die eigene Webseite). Ist sie leer, gehen Angebote **ohne** Annahme-Link hinaus.

Auftragsbestätigungen aus dem Dokument-Editor bekommen keinen Annahme-Link – der Kunde stimmt nur dem Angebot zu.

---

## 4. Digitale Annahme durch den Kunden (Freigabe)

### 4.1 Ablauf aus Sicht des Kunden

1. Kunde öffnet den Link aus der E-Mail und sieht das Angebot mit allen Positionen und dem PDF.
2. Er wählt optionale bzw. alternative Leistungen aus (falls vorhanden).
3. Er gibt Vor- und Nachnamen ein und klickt auf „Freigeben“.
4. Er erhält einen **6-stelligen Bestätigungscode** per E-Mail an die Adresse, an die das Angebot ging, und gibt ihn ein.
5. Das Angebot ist angenommen.

**Schutzregeln für den Code:** 15 Minuten gültig, neuer Code frühestens nach 30 Sekunden, höchstens 5 Codes pro Stunde. Nach insgesamt 15 Fehlversuchen ist die Online-Freigabe gesperrt; das Büro wird per Push benachrichtigt und kann das Angebot neu versenden. Gespeichert wird nur ein HMAC des Codes, nie der Code selbst.

### 4.2 Was danach automatisch passiert

```
Kunde nimmt Angebot (oder Nachtragsangebot) an
    │
    ├── Angebot: digitalAngenommen = true → "Verbindlich", gesperrt
    │   └── Audit-Eintrag DIGITAL_ANGENOMMEN (Hash-Kette, siehe GOBD_COMPLIANCE.md)
    │
    ├── Hängt das Angebot an einer Anfrage?
    │   └── Projekt wird automatisch angelegt, Anfrage geht im Projekt auf
    │       (Auftragsnummer automatisch, Projektart "Pauschalpreis",
    │        Produktkategorien aus dem Angebot)
    │
    ├── Auftragsbestätigung wird automatisch erstellt (Nachfolger des Angebots)
    │   ├── enthält nur fest beauftragte + vom Kunden gewählte Positionen
    │   ├── Standard-Vor-/Nachtexte der AB werden eingesetzt
    │   └── sofort "Verbindlich" (digitalAngenommen = true, gesperrt)
    │
    ├── Auftragsbestätigung geht automatisch als PDF per E-Mail an den Kunden
    │   (sofern eine Empfängeradresse bekannt ist; Layout aus dem Formularwesen,
    │    mit Hinweis "Angebot angenommen" und Annahme-Datum)
    │
    └── Push-Nachricht "… angenommen" an Mitarbeiter, deren Abteilung das erlaubt
```

Existiert zum Angebot bereits eine (nicht stornierte) Auftragsbestätigung, wird keine zweite erzeugt.

### 4.3 Beweissicherung (`DokumentFreigabe`)

Pro versendetem Link wird ein Datensatz `DokumentFreigabe` angelegt (Snapshot-Prinzip – unabhängig davon, ob das Angebot später geändert wird):

| Feld | Inhalt |
|---|---|
| `uuid` | Schlüssel im Link |
| `quellTyp` / `quellDokumentId` | Quelle: `AUSGANGS_DOKUMENT` für Angebote aus dem Dokument-Editor; `ANFRAGE` bzw. `PROJEKT` für hochgeladene Angebots-/AB-Dateien (Altweg) |
| `dokumentNummer`, `dokumentArt`, `dokumentBetrag`, `bauvorhaben`, `kundeName`, `kundeEmail` | Daten zum Versandzeitpunkt |
| `positionenSnapshot`, `basisNetto`, `mwstSatz` | Positionen und Beträge zum Versandzeitpunkt |
| `hashOriginal` | SHA-256-Fingerabdruck der Geschäftsdaten beim Versand |
| `ablaufDatum` | Ende der Gültigkeit |
| `status` | `PENDING` (Wartet auf Kunde), `ACCEPTED` (Angenommen), `EXPIRED` (Abgelaufen), `REVOKED` (Zurückgezogen) |
| `akzeptiertAm`, `akzeptiertIp`, `akzeptiertUserAgent`, `akzeptiertEmail`, `unterzeichnerName` | Wer hat wann angenommen |
| `akzeptierteAlternativen`, `akzeptierterBetrag` | Gewählte Wahlpositionen und verbindlicher Endbetrag |
| `hashAcceptance` | Zweiter Hash über Original-Hash und Annahmedaten |
| `codeBestaetigtAm`, `codeBestaetigtEmail` | Nachweis des bestätigten E-Mail-Codes |

Die Annahme ist idempotent: Ein zweiter Klick ändert nichts mehr am ersten Nachweis.

### 4.4 Anzeige im Programm

| Wo | Plakette |
|---|---|
| Geschäftsdokument (Anfrage/Projekt) | „Angenommen · TT.MM.JJ“, „Wartet auf Kunde“, „Link abgelaufen“, „Link zurückgezogen“ |
| Anfragenübersicht | „Angebot angenommen · TT.MM.JJ“, „Angebot wartet auf Kunde“, „Angebot – Link abgelaufen“ |
| Filter Anfragenübersicht | **Angebots-Status**: Alle / Angebot angenommen / Wartet auf Kunden / Link abgelaufen |

Ein Klick auf „Angenommen“ lädt den Annahme-Nachweis (E-Mail, IP, Zeitpunkt, Hash).

---

## 5. Vom Angebot zum Auftrag

### 5.1 Ablauf

```
Angebot (in Anfrage oder Projekt)
    │
    ├─[digitale Annahme]──► Projekt (falls nötig) + Auftragsbestätigung automatisch
    │
    └─[von Hand]
          │
          ├── Anfrage → Projekt übernehmen (siehe ANFRAGEWESEN.md, Abschnitt 6)
          │
          ▼
       Projekt: Angebot anklicken → "Auftragsbestätigung"
          │
          ▼
       Auftragsbestätigung (AB-…)
          │
          ▼
       Rechnungen: Abschlagsrechnung(en), Teilrechnung(en), Schlussrechnung
```

### 5.2 Regeln

- Eine **Auftragsbestätigung** zu einem Angebot entsteht nur **im Projekt** (Aktionsmenü des Angebots → „Auftragsbestätigung“) oder automatisch bei digitaler Annahme. In einer Anfrage wird sie nicht angeboten.
- Pro Angebot bzw. Nachtragsangebot gibt es **höchstens eine aktive Auftragsbestätigung**. Versuch einer zweiten: „Zu … gibt es bereits eine Auftragsbestätigung. Für zusätzliche Leistungen legen Sie bitte ein Nachtragsangebot an.“
- **Rechnungen** werden am untersten Dokument eines Vorgangs erstellt: Hat das Angebot eine Auftragsbestätigung, wird an der AB abgerechnet („Rechnung bitte an der Auftragsbestätigung anlegen“). Ausnahme: Wurde schon vor der AB am Angebot abgerechnet, geht es dort weiter.
- Beim Übergang Anfrage → Projekt wandern alle Angebote mit (`migrateFromAnfrageToProjekt()`), die Produktkategorien des Projekts werden aus den Positionen abgeleitet.

### 5.3 Preis von Anfrage und Projekt

Der Betrag der Anfrage bzw. der Auftragspreis des Projekts wird automatisch aus den Dokumenten berechnet:

```
Preis = Summe aller Auftragsbestätigungen (brutto)
      + Summe aller Angebote/Nachtragsangebote, die noch keine AB haben (brutto)
```

Stornierte Dokumente zählen nicht. Optionale und alternative Positionen zählen nicht, solange sie nicht beauftragt sind.

---

## 6. Projekt-Vorlage

### 6.1 Endpoint

```
GET /api/anfragen/{id}/projekt-vorlage
```

Gibt eine Vorlage für die Projekt-Erstellung aus den Anfragedaten zurück:
- Bauvorhaben
- Kunde (Name, Kundennummer, Kunden-ID)
- Anfrage-Betrag als Bruttopreis
- Anlagedatum
- E-Mail-Adressen der Anfrage
- Anfrage-ID (`anfrageIds`)

Für den Vorschlag der Produktkategorien gibt es zusätzlich `GET /api/anfragen/{id}/produktkategorien-vorschlag` (Auftragsbestätigung hat Vorrang vor dem Angebot). Diesen Endpunkt nutzt der Dialog „Neues Projekt anlegen“ → „Von Anfrage übernehmen“.

---

## 7. E-Mail-Integration

- Der Versand läuft aus dem Dokument-Editor über das E-Mail-Fenster (Knopf **„E-Mail“**); Empfänger ist vorbelegt mit der ersten E-Mail-Adresse des Kunden bzw. der Anfrage.
- Ist ein eigenes Postfach für Geschäftsdokumente eingerichtet, geht die Mail darüber hinaus.
- Für Angebote fügt das Programm den Annahme-Link als Knopf (mit Klartext-Link als Ausweichlösung) in die E-Mail ein.
- Nach dem Versand ruft der Editor `POST /api/ausgangs-dokumente/{id}/email-versendet` auf – das setzt das Versanddatum (Angebote werden dabei nicht gebucht).

---

## 8. API-Referenz

### 8.1 Angebote als Geschäftsdokumente (`/api/ausgangs-dokumente`)

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/api/ausgangs-dokumente` | Angebot/Nachtragsangebot anlegen (`typ`, `anfrageId` oder `projektId`, …) |
| `PUT` | `/api/ausgangs-dokumente/{id}` | Angebot speichern (nur solange bearbeitbar und mit gehaltener Bearbeitungssperre) |
| `GET` | `/api/ausgangs-dokumente/anfrage/{anfrageId}` | Alle Geschäftsdokumente einer Anfrage |
| `GET` | `/api/ausgangs-dokumente/projekt/{projektId}` | Alle Geschäftsdokumente eines Projekts |
| `POST` | `/api/ausgangs-dokumente/{id}/email-versendet` | Versanddatum setzen |
| `DELETE` | `/api/ausgangs-dokumente/{id}?begruendung=…` | Unversendetes Angebot löschen |

### 8.2 Digitale Freigabe

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/api/ausgangs-dokumente/freigabe-status?ids=…` | Jüngste Freigabe je Dokument (Plaketten) |
| `GET` | `/api/ausgangs-dokumente/{id}/freigabe-audit` | Annahme-Nachweis eines Dokuments |
| `GET` | `/api/anfragen/freigabe-status?ids=…` | Jüngste Freigabe je Anfrage (Anfragenübersicht) |
| `GET` | `/api/internal/freigabe/{uuid}` | Daten für die öffentliche Freigabe-Seite (Server-zu-Server, geschützt) |
| `GET` | `/api/internal/freigabe/{uuid}/pdf` | PDF für die Freigabe-Seite |
| `POST` | `/api/internal/freigabe/{uuid}/code` | Bestätigungscode anfordern |
| `POST` | `/api/internal/freigabe/{uuid}/akzeptieren` | Annahme mit Code |

### 8.3 Projekt-Übernahme

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/api/anfragen/{id}/projekt-vorlage` | Projekt-Vorlage aus der Anfrage |
| `GET` | `/api/anfragen/{id}/produktkategorien-vorschlag` | Kategorien aus AB bzw. Angebot |
| `POST` | `/api/projekte/{projektId}/anfragen/{anfrageId}/zusammenfuehren` | Anfrage in bestehendes Projekt übernehmen |

---

## 9. Entitäten-Übersicht

| Entity | Zweck |
|---|---|
| `AusgangsGeschaeftsDokument` | Das Angebot selbst (Typ `ANGEBOT` / `NACHTRAGSANGEBOT`) |
| `Anfrage` | Vorgang vor dem Auftrag (früher `Angebot`), siehe `ANFRAGEWESEN.md` |
| `DokumentFreigabe` | Digitale Annahme mit Beweissicherung |
| `FreigabeStatus` | Enum: PENDING, ACCEPTED, EXPIRED, REVOKED |
| `FreigabeQuellTyp` | Enum: ANFRAGE, PROJEKT, AUSGANGS_DOKUMENT |
| `AusgangsGeschaeftsDokumentAudit` | Audit-Eintrag `DIGITAL_ANGENOMMEN` in der Hash-Kette |

| Service | Verantwortung |
|---|---|
| `AusgangsGeschaeftsDokumentService` | Anlegen, Speichern, Basisdokument-Regeln, Preisberechnung, Wahlpositionen |
| `DokumentFreigabeService` | Annahme-Links, digitale Annahme, Auto-Projekt, Auto-Auftragsbestätigung |
| `FreigabeCodeService` | 6-stelliger Bestätigungscode per E-Mail |
| `AutoAuftragsbestaetigungVersandService` | Standardtexte der Auto-AB, automatischer Versand nach Annahme |
| `ProjektManagementService` | Projektanlage aus Anfrage, Zusammenführen |
