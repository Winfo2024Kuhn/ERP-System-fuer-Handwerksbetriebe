# Zeiterfassung Workflows - Online & Offline

> **Zuletzt aktualisiert:** 12.10.2026 (Stand Oktober 2026)  
> **Anwendung:** react-zeiterfassung (Mobile App / PWA)  
> **Backend:** `ZeiterfassungApiController` + `ZeiterfassungApiService` (`/api/zeiterfassung/*`)

Diese Dokumentation beschreibt alle Workflows der Zeiterfassung-App für die Fehleranalyse.

---

## Übersicht der Aktionen

| Aktion | Knopf in der App | Seite / Funktion | Online-API | Offline-Handling |
|--------|------------------|------------------|------------|------------------|
| **Neue Buchung starten** | „Zeit erfassen“ → „Zeiterfassung starten“ | `ZeiterfassungPage` → `handleStartTracking` | `POST /api/zeiterfassung/start` | `addPendingEntryWithOperationId('start', ...)` |
| **Buchung stoppen** | „Gehen (Feierabend)“ | `DashboardPage` → `handleStopSession` | `POST /api/zeiterfassung/stop` | `addPendingEntryWithOperationId('stop', ...)` |
| **Pause starten** | „Pause“ | `DashboardPage` → `handlePause` | `POST /api/zeiterfassung/pause` | `addPendingEntryWithOperationId('pause', ...)` |
| **Pause beenden** | „Arbeit fortsetzen“ | `DashboardPage` → `handleResumeFromPause` | Stop, dann neue Buchung | Stop (pending), dann neue Buchung |
| **Tätigkeit wechseln** | „Tätigkeit“ | `DashboardPage` → `handleSwitchArbeitsgang` | Stop + Start | Stop + Start (pending) |
| **Kategorie wechseln** | „Kategorie“ | `DashboardPage` → `handleSwitchKategorie` | Stop + Start | Stop + Start (pending) |
| **Auftrag wechseln** | „Auftrag“ → „Auftrag wechseln“ | `DashboardPage` → `ZeiterfassungPage?switching=true` | Stop, dann Start (erst beim Bestätigen) | Stop (pending), dann Start |

Alle Schreib-Aufrufe schicken zusätzlich `originalZeit` (Zeitpunkt der Aktion am Handy) und `idempotencyKey` (UUID der Aktion) mit – siehe `buildBookingRequestPayload()` in `src/services/OfflineService.ts`.

---

## 0. Voraussetzungen: Anmeldung und eingerichtete Arbeitszeit

### Anmeldung per Token
- Anmeldung über QR-Code bzw. Link mit `?token=…` (`App.tsx` → `validateAndStoreToken`), geprüft über `GET /api/mitarbeiter/by-token/{token}`.
- Token und Mitarbeiter liegen in `localStorage` (`zeiterfassung_token`, `zeiterfassung_mitarbeiter`) und zusätzlich als Cookies `ze_token` / `ze_mitarbeiter` (Fallback, weil iOS-PWAs den `localStorage` leeren können).
- `src/auth/mobileTransport.ts` (`createMobileFetch`, in `main.tsx` global eingehängt) setzt bei jedem Aufruf auf `/api/…` den Header `X-Auth-Token`. Im Backend prüft `MobileTokenAuthenticationFilter`, dass Header-, Pfad-, Query- und Body-Token (`token` im JSON) übereinstimmen; erlaubte Routen stehen abschließend in `MobileApiPolicy`. Zu viele Fehlversuche → `429` mit `Retry-After`.
- Beim App-Start prüft `syncData()`, ob der Mitarbeiter noch aktiv ist. `401/403/404` oder `aktiv=false` → automatische Abmeldung; `429` → angemeldet bleiben und später erneut abgleichen.

### Arbeitszeit eingerichtet?
- Dashboard und `ZeiterfassungPage` laden `GET /api/zeiterfassung/buchungszeitfenster/{token}`. Antwort: `fuehrtZeitkonto`, `eingerichtet`, `istGeschaeftsfuehrer`, `kontenGefuehrt`, `hinweis`, `buchungStartZeit`, `buchungEndeZeit`.
- Buchen geht nur, wenn `fuehrtZeitkonto = true` **und** (`eingerichtet = true` oder Geschäftsführer) – siehe `hatEingerichtetesZeitkonto()` in `src/types/zeitkonto.ts`.
- Sonst zeigt das Dashboard „Zeiterfassung derzeit nicht verfügbar“, die Buchungsseite „Keine neue Zeitbuchung möglich“ (mit Knopf „Erneut prüfen“). Die Knöpfe Pause/Tätigkeit/Auftrag/Kategorie und die Kacheln „Abwesenheit beantragen“, „Saldenauswertung“, „Heute gearbeitet“ werden ausgeblendet.
- Das Backend prüft dasselbe bei jedem Start und jeder Pause (`pruefeNeuenStart`): Zeiterfassung ausgeschaltet oder keine gültige Zeitkonto-Version für heute bzw. den Buchungstag → `409 CONFLICT`. Geschäftsführer brauchen keine Zeitkonto-Version.

---

## 1. Neue Buchung starten

**Datei:** `src/pages/ZeiterfassungPage.tsx` – Funktion `handleStartTracking`

### Flow
```
„Zeit erfassen“ → Projekt wählen → Kategorie wählen (oder „Ohne Kategorie fortfahren →“) → Tätigkeit wählen → „Zeiterfassung starten“
```
- Projektliste: `OfflineService.getProjekte()` (`/api/zeiterfassung/projekte?limit=100`, gecacht), abgeschlossene Projekte werden ausgefiltert. Suche über Name, Projektnummer und Kunde.
- Kategorien: `OfflineService.getKategorien(projektId)` (`/api/zeiterfassung/kategorien/{projektId}`).
- Tätigkeiten: `OfflineService.getArbeitsgaenge(token)` (`/api/zeiterfassung/arbeitsgaenge/{token}`, persönliche Liste).

### Online-Modus
```typescript
POST /api/zeiterfassung/start   // Timeout 5 s
Body: {
    token,
    projektId,
    arbeitsgangId,
    produktkategorieId,  // null bei "Ohne Kategorie"
    originalZeit,        // ISO-Zeitpunkt des Tippens
    idempotencyKey       // UUID (createOperationId)
}
```

- **Erfolg:** Session in `localStorage` (`zeiterfassung_active_session`) speichern → Dashboard (`/`)
- **Fehler (Server antwortet mit Fehlerstatus):** Toast mit Server-Fehlertext, **NICHT** offline queuen, auf der Seite bleiben (z. B. „Es läuft bereits eine Buchung. Bitte erst stoppen.“)

### Offline-Modus
Nur wenn `navigator.onLine === false` **oder** der Request per Timeout abgebrochen wurde (`AbortError`):
```typescript
OfflineService.addPendingEntryWithOperationId('start', {
    token,
    projektId,
    arbeitsgangId,
    produktkategorieId
}, startTime, startOperationId)
```
→ Lokale Session mit `id: 'offline-' + Date.now()` erstellen → Dashboard

### Backend (`ZeiterfassungApiService.startZeiterfassung`)
- Idempotenz: gleicher `idempotencyKey` → vorhandene Buchung wird zurückgegeben, keine zweite angelegt.
- Pessimistische Sperre auf den Mitarbeiter (`findByLoginTokenAndAktivTrueForUpdate`), danach Prüfung „läuft schon eine Buchung?“.
- Startzeit = `originalZeit` (falls mitgeschickt), sonst jetzt.
- Stundensatz: `ArbeitsgangStundensatz` des aktuellen Jahres, sonst der neueste vorhandene; ohne Stundensatz → Fehler.
- Audit-Eintrag (`ErfassungsQuelle.MOBILE_APP`) und Invalidierung des MonatsSaldo-Caches.

### ⚠️ Potenzielle Probleme
1. Offline-Session hat keine echte ID – wird beim nächsten Abgleich mit `/aktiv` überschrieben.
2. Ein Netzwerkfehler, der **kein** Timeout ist, während `navigator.onLine` noch `true` meldet (z. B. Server nicht erreichbar), wird nicht gequeued, sondern nur als Toast „Fehler beim Starten der Buchung“ gemeldet.
3. Die Online-Session speichert `kundenName`/`auftragsnummer` nicht lokal; sie erscheinen erst nach dem nächsten Laden von `/aktiv`.

---

## 2. Buchung stoppen (Feierabend)

**Datei:** `src/pages/DashboardPage.tsx` – Funktion `handleStopSession` (Knopf „Gehen (Feierabend)“)

### Online-Modus
```typescript
POST /api/zeiterfassung/stop   // Timeout 3 s
Body: { token, originalZeit, idempotencyKey }
```

### Offline-Modus (Netzfehler, Timeout **oder** Fehlerstatus vom Server)
```typescript
// Dauer der beendeten Arbeit mitspeichern (nur Arbeit, nicht Pause/Abwesenheit)
const stopDuration = isWorkSession && elapsedMinutes > 0 ? elapsedMinutes : undefined
OfflineService.addPendingEntryWithOperationId('stop', { token }, stopTime, stopOperationId, stopDuration)
```
Die App zeigt dann den Hinweis „Feierabend um HH:MM gespeichert, aber noch kein Empfang. Er wird automatisch nachgetragen, sobald du wieder online bist.“

**Danach (immer):** `zeiterfassung_stopped_at` setzen (Stop-Cooldown, siehe Abschnitt 8), `zeiterfassung_active_session` löschen, „Heute gearbeitet“ neu laden.

### Backend (`stopZeiterfassung`)
- Idempotenz über `stopIdempotencyKey`.
- Liegt die Endezeit nicht nach der Startzeit (alter Offline-Stop trifft auf neuere Buchung), wird sie auf Start + 1 Minute korrigiert.
- Keine offene Buchung, aber die Buchung wurde am selben Tag vom Auto-Stop geschlossen (`automatischBeendet = true`)? Dann wird der verspätete Stop **nachgetragen** – nur wenn die echte Endezeit vor der geschätzten liegt (Buchung wird nur verkürzt, nie verlängert). Status in der Antwort: `nachgetragen`.
- Sonst Fehler „Keine aktive Buchung gefunden“ (`400`).

### ⚠️ Potenzielle Probleme
1. Stop ohne aktive Session auf dem Server → `400` beim Sync → Eintrag landet in der Reparatur-Liste (Abschnitt 7).

---

## 3. Pause starten

**Datei:** `src/pages/DashboardPage.tsx` – Funktion `handlePause` (Knopf „Pause“, nur bei Arbeitsbuchungen sichtbar)

### Online-Modus
```typescript
POST /api/zeiterfassung/pause   // Timeout 3 s
Body: { token, originalZeit, idempotencyKey }
```

- **Erfolg:** Server beendet **alle** offenen Buchungen des Mitarbeiters und legt eine `PAUSE`-Buchung ohne Projekt an. Die App zeigt „☕ Pause“.

### Offline-Modus (Netzfehler, Timeout **oder** Fehlerstatus vom Server)
```typescript
OfflineService.addPendingEntryWithOperationId('pause', { token }, pauseTime, pauseOperationId, workDurationMinutes)
```
→ Optimistische Pause-Session; zusätzlich wird `zeiterfassung_start_synced_at` gesetzt, damit die lokale Pause 15 s lang nicht durch die Server-Antwort von `/aktiv` überschrieben wird.

### Doppeltes Tippen
`beginAction()` / `actionLockRef` sperrt Pause, Stop und „Arbeit fortsetzen“, solange eine Aktion läuft. Die Knöpfe sind währenddessen deaktiviert.

---

## 3a. Pause beenden („Arbeit fortsetzen“)

**Datei:** `src/pages/DashboardPage.tsx` – Funktion `handleResumeFromPause`

1. `POST /api/zeiterfassung/stop` beendet die Pause-Buchung (bei Fehler oder offline → `stop` in die Warteschlange).
2. `zeiterfassung_stopped_at` setzen, lokale Session löschen.
3. Weiter zu `/zeiterfassung` – der Mitarbeiter wählt Projekt, Kategorie und Tätigkeit neu.

---

## 4. Tätigkeit wechseln (Quick Switch)

**Datei:** `src/pages/DashboardPage.tsx` – Funktion `handleSwitchArbeitsgang` (Knopf „Tätigkeit“ → Auswahlfenster „Tätigkeit wählen“)

### Online-Modus
1. `POST /api/zeiterfassung/stop` (alte Buchung)
2. `POST /api/zeiterfassung/start` (neue Buchung mit neuer Tätigkeit)

```typescript
// Start-Request:
{
    token,
    projektId: activeSession.projektId,
    arbeitsgangId: newArbeitsgang.id,
    produktkategorieId: activeSession.produktkategorieId ?? null  // Kategorie bleibt!
}
```
Schlägt der Start nach erfolgreichem Stop fehl, versucht die App, die alte Buchung neu zu starten, und zeigt die alte Session weiter an.

### Offline-Modus (Netzfehler/Timeout)
```typescript
// Stop nur, wenn der Online-Stop nicht schon geklappt hat (Race-Condition-Fix)
if (!stopRes || !stopRes.ok) {
    OfflineService.addPendingEntryWithOperationId('stop', { token }, stopOperationTime, stopOperationId, stopDuration)
}
OfflineService.addPendingEntryWithOperationId('start', {
    token,
    projektId: activeSession.projektId,
    arbeitsgangId: newArbeitsgang.id,
    produktkategorieId: activeSession.produktkategorieId ?? null
}, startOperationTime, startOperationId)
```

### ⚠️ Potenzielle Probleme
1. Im Auswahlfenster gibt es unter „Aktuelles Projekt“ den Knopf „Ändern“. Die dort gewählte Projekt-ID (`selectedProjektId`) wird beim Start aber **nicht** verwendet – die neue Buchung läuft immer auf `activeSession.projektId`. Für einen echten Projektwechsel „Auftrag“ verwenden.

---

## 5. Kategorie wechseln (Quick Switch)

**Datei:** `src/pages/DashboardPage.tsx` – Funktion `handleSwitchKategorie` (Knopf „Kategorie“ → Auswahlfenster „Kategorie wählen“ mit den Kategorien des aktuellen Projekts; ohne Projekt öffnet sich nichts)

### Online-Modus
1. `POST /api/zeiterfassung/stop`
2. `POST /api/zeiterfassung/start` (gleiche Tätigkeit, neue Kategorie)

```typescript
{
    token,
    projektId: activeSession.projektId,        // ← Projekt bleibt gleich!
    arbeitsgangId: activeSession.arbeitsgangId, // ← Tätigkeit bleibt gleich!
    produktkategorieId: newKategorie.id        // ← Nur Kategorie ändert sich
}
```
Schlägt der Start fehl, versucht die App (wie beim Tätigkeitswechsel) die alte Buchung wiederherzustellen.

### Offline-Modus
Gleich wie beim Tätigkeitswechsel: `stop` (nur wenn der Online-Stop nicht geklappt hat) + `start` in die Warteschlange, optimistische Session mit `id: 'offline-…'`.

---

## 6. Auftrag wechseln

**Datei:** `src/pages/DashboardPage.tsx` – Funktion `handleSwitchProjekt`, danach `src/pages/ZeiterfassungPage.tsx` (`?switching=true`)

### Flow
1. Knopf „Auftrag“ → Navigation zu `/zeiterfassung?switching=true`. Die laufende Buchung wird **noch nicht** gestoppt (Hinweis: „Laufende Buchung bleibt aktiv bis zur neuen Auswahl. Bei Abbruch läuft sie weiter.“).
2. User wählt **alles** neu: Projekt, Kategorie, Tätigkeit.
3. Knopf „Auftrag wechseln“: erst jetzt wird die alte Buchung gestoppt, dann die neue gestartet.

### Online/Offline-Modus
```typescript
POST /api/zeiterfassung/stop   // Timeout 3 s
// Bei Fehler/Timeout:
OfflineService.addPendingEntryWithOperationId('stop', { token }, stopTime, stopOperationId, stopDuration)
// danach Start wie in Abschnitt 1
```
Klappt der neue Start online, ist bewiesen, dass der Server keine aktive Buchung mehr hat – verwaiste Offline-Stops werden mit `OfflineService.removePendingEntriesByType('stop')` entfernt, damit sie später nicht die **neue** Buchung beenden.

---

## 7. Sync-Prozess

**Datei:** `src/services/OfflineService.ts` – Funktionen `syncAll`, `syncPending`, `_syncPendingInternal`

### Auslöser (`App.tsx`)
- App-Start / Anmeldung (`syncData` bzw. `validateAndStoreToken`)
- Browser-Ereignis `online`
- App kommt aus dem Hintergrund (`visibilitychange`) und es gibt wartende Einträge
- Heartbeat alle 30 s, wenn wartende Einträge existieren
- Manuell: Sync-Anzeige im Kopf (`NetworkStatusBadge`, bei „Sync-Fehler“ antippbar) bzw. Knopf „Jetzt synchronisieren“ auf der Buchungsseite

### Ablauf
1. Sync-Sperre (`acquireSyncLock`) – es läuft nie mehr als ein Sync gleichzeitig.
2. Erreichbarkeit prüfen: `HEAD /api/zeiterfassung/kategorien` mit 2 s Timeout (`navigator.onLine` allein ist unzuverlässig).
3. Hängengebliebene `inflight`-Einträge wieder auf `pending` setzen.
4. Alle Einträge aus dem IndexedDB-Store `pending` holen und **nach `originalTime` sortieren** (chronologisch!).
5. Für jeden Eintrag:
   - Status `inflight`, `attemptCount + 1`
   - `POST /api/zeiterfassung/{type}` mit `{ ...data, originalZeit, idempotencyKey: entry.id }`
   - Erfolg → aus `pending` löschen
   - 4xx → **in die Reparatur-Liste verschieben** (Store `failed`, mit `httpStatus`, `serverError`, `failedAt`) und aus `pending` löschen
   - 5xx → in `pending` behalten (`lastError`) für den nächsten Versuch
   - Netzwerkfehler → Sync abbrechen, alle restlichen Einträge bleiben für später
6. Danach (nur bei `syncAll`) Stammdaten-Cache auffrischen: Projekte, Kategorien (auch je Projekt), Tätigkeiten (global und persönlich), Kunden, Lieferanten; Zeitpunkt `last_sync` speichern.

### Reparatur-Liste
- Banner auf dem Dashboard: „1 Buchung konnte nicht gespeichert werden“ bzw. „N Buchungen konnten nicht gespeichert werden“ → „Tippe hier, um sie zu prüfen“.
- Fenster „Reparatur“ (`src/components/FailedEntriesModal.tsx`): Der Mitarbeiter soll den Chef ansprechen, damit die Buchung am PC nachgetragen wird. „Verwerfen“ entfernt den Eintrag – dann zählt seine Zeit auch nicht mehr in „Heute gearbeitet“.
- Grundsatz: Der Sync fasst die lokale Session (`zeiterfassung_active_session`) **nie** an – nur Aktionen des Mitarbeiters dürfen sie löschen.

### ⚠️ Potenzielle Probleme
1. **Reihenfolge kritisch!** Stop vor Start muss sein – deshalb Sortierung nach `originalTime`.
2. **Abgelehnter Stop:** Wird ein Stop abgelehnt (Reparatur-Liste), läuft der nachfolgende Start trotzdem; er kann dann mit „Es läuft bereits eine Buchung“ ebenfalls in der Reparatur-Liste landen.
3. **Keine Transaktions-Logik** – ein Stop kann erfolgreich sein, aber der folgende Start scheitern.

---

## 8. Session laden

**Datei:** `src/pages/DashboardPage.tsx` – Funktion `loadActiveSession`

### Ablauf
1. Session sofort aus `localStorage` anzeigen.
2. **Stop-Cooldown:** Wurde in den letzten 15 s gestoppt (`zeiterfassung_stopped_at`), wird die Server-Session ignoriert. Ausnahme: Die lokale Session wurde *nach* dem Stop gestartet (neue Buchung) – dann bleibt sie.
3. **Wenn `pending` Einträge existieren:** lokale Session behalten, KEIN Abgleich mit dem Server.
4. **Start-Cooldown:** Wurde in den letzten 15 s ein Start synchronisiert (`zeiterfassung_start_synced_at`), lokale Session behalten.
5. Sonst: `GET /api/zeiterfassung/aktiv/{token}` (Timeout 2 s).
   - Server hat eine aktive Buchung → `localStorage` und Anzeige übernehmen (inkl. `produktkategorieId/Name`, `kundenName`, `auftragsnummer`, `typ`).
   - Server hat keine → lokale Session löschen, **außer** sie ist jünger als 15 s (Frisch-Guard gegen verzögerte Server-Antworten).
   - Server nicht erreichbar → lokale Session behalten.

### ⚠️ Potenzielle Probleme
1. Sync-Zustand kann veraltet sein – die lokale Session weicht vom Server ab, solange Einträge warten.

---

## 9. „Heute gearbeitet“

**Datei:** `src/pages/DashboardPage.tsx` – Funktion `loadHeuteGearbeitet`

```
Heute gearbeitet = Server-Minuten (abgeschlossene Buchungen, GET /api/zeiterfassung/heute/{token}, gecacht)
                 + ungesyncte Stop/Pause-Minuten (pending + Reparatur-Liste, nur heutiger Tag in Geräte-Zeitzone)
                 + laufende Arbeits-Session (live)
```
- Der Server zählt die laufende Buchung **nicht** mit, sondern liefert `aktiveBuchungStartZeit` – so gibt es keine Doppelzählung.
- Pausen und Abwesenheiten zählen nicht.
- Antippen der Kachel öffnet „Tagesbuchungen“ (`/tagesbuchungen`), die Server-Buchungen und noch wartende Offline-Buchungen zusammen zeigt.

---

## 10. Automatisches Beenden (Server)

**Datei:** `src/main/java/.../service/ZeitbuchungAutoStopService.java` (läuft alle 5 Minuten)

- Buchungszeitfenster der am Buchungstag gültigen Zeitkonto-Version überschritten (`buchungEndeZeit`) → Buchung endet zu dieser Uhrzeit.
- Ohne Zeitfenster: Buchung läuft über Mitternacht → Ende 23:59 des Starttags.
- Solche Buchungen bekommen `automatischBeendet = true` (Audit-Quelle `SYSTEM`), erscheinen in der Benachrichtigungs-Glocke am PC als Prüffall und dürfen durch einen verspäteten Feierabend-Stop vom Handy noch verkürzt werden (Abschnitt 2).

---

## Weitere Zeiterfassungs-Seiten der App

| Kachel / Knopf | Route | Inhalt |
|----------------|-------|--------|
| „Abwesenheit beantragen“ | `/urlaub` (auch `/abwesenheit`, `/zeitausgleich` → `/urlaub?typ=ZEITAUSGLEICH`) | Antrag für Urlaub, Krankheit, Fortbildung oder Zeitausgleich (`POST /api/urlaub/antraege`); Zeitausgleich ist für Geschäftsführer nicht wählbar. Bemerkung kann per Mikrofon diktiert werden (Spracheingabe). |
| – (keine Kachel, nur direkt aufrufbar) | `/abwesenheiten` | „Meine Abwesenheiten“ mit Status Offen / Genehmigt / Abgelehnt / Storniert |
| „Saldenauswertung“ | `/salden` | Urlaubstage, Monatssaldo, Gesamtsaldo, Feiertage (`GET /api/zeiterfassung/saldo/{token}`) |
| „Heute gearbeitet“ | `/tagesbuchungen` | Buchungen eines Tages (online + offline) |
| Zahnrad oben rechts | `/einstellungen` | Erlaubnisse für Benachrichtigungen, Mikrofon (Diktieren) und Kamera |

Auf dem Dashboard erscheinen außerdem nur lesend:
- **Langzeitkrankmeldung** (`GET /api/zeiterfassung/langzeitkrankmeldung/{token}`): Karte „{Phase} — seit TT.MM.JJJJ“ bzw. bei Wiedereingliederung „… — heute X Stunden geplant“. Angelegt und geändert wird sie nur am PC (Zeiterfassung → Lange Krankheit).
- **Urlaubsverfall** (`GET /api/zeiterfassung/urlaubsverfall/{token}`): Warnung „Urlaubsverfall beachten!“, wenn Resturlaub aus dem Vorjahr am 01.02. verfällt.

---

## Datenfluss-Diagramm

```
┌──────────────────┐      ┌─────────────────┐      ┌──────────────────┐
│ ZeiterfassungPage│ ────▶│   DashboardPage │ ────▶│  OfflineService  │
│                  │      │                 │      │                  │
│ - Projekt        │      │ - Stop          │      │ - IndexedDB      │
│ - Kategorie      │      │ - Pause         │      │   zeiterfassung-db│
│ - Tätigkeit      │      │ - Fortsetzen    │      │ - pending        │
│ - Start          │      │ - Tätigkeit ▵   │      │ - failed (Repar.)│
│ - Auftrag wechs. │      │ - Kategorie ▵   │      │ - master_data    │
└──────────────────┘      │ - Auftrag ▵     │      │ - Sync           │
                          └─────────────────┘      └──────────────────┘
                                  │
                                  ▼
                          ┌──────────────────────────┐
                          │ localStorage             │
                          │                          │
                          │ - zeiterfassung_token    │
                          │ - zeiterfassung_mitarbeiter│
                          │ - …_active_session       │
                          │ - …_stopped_at           │
                          │ - …_start_synced_at      │
                          └──────────────────────────┘
```

---

## Changelog

| Datum | Änderung |
|-------|----------|
| 15.01.2026 | Fix Backend: `produktkategorieId` + `produktkategorieName` fehlen in `/api/zeiterfassung/aktiv/{token}` Response. Hinzugefügt in `ZeiterfassungApiService.getAktiveBuchung()`. |
| 15.01.2026 | Fix Frontend: `produktkategorieId` bei Tätigkeitswechsel - `??` statt `\|\|` für undefined-Handling. |
| 12.10.2026 | Doku an den Code angeglichen: `originalZeit` + `idempotencyKey`, Reparatur-Liste statt stillem Löschen bei 4xx, Erreichbarkeitsprüfung, Stop-/Start-Cooldown und Frisch-Guard, „Arbeit fortsetzen“, Auftragswechsel erst beim Bestätigen, Zeitkonto-Prüfung, `X-Auth-Token`, Auto-Stop mit Nachtrag, weitere Seiten. |
