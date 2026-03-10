# Zeiterfassung Workflows - Online & Offline

> **Zuletzt aktualisiert:** 15.01.2026  
> **Anwendung:** react-zeiterfassung (Mobile App)

Diese Dokumentation beschreibt alle Workflows der Zeiterfassung-App für die Fehleranalyse.

---

## Übersicht der Aktionen

| Aktion | Seite | Online-API | Offline-Handling |
|--------|-------|------------|------------------|
| **Neue Buchung starten** | `ZeiterfassungPage` | `POST /api/zeiterfassung/start` | `addPendingEntry('start', ...)` |
| **Buchung stoppen** | `DashboardPage` | `POST /api/zeiterfassung/stop` | `addPendingEntry('stop', ...)` |
| **Pause starten** | `DashboardPage` | `POST /api/zeiterfassung/pause` | `addPendingEntry('pause', ...)` |
| **Tätigkeit wechseln** | `DashboardPage` | Stop + Start | Stop + Start (pending) |
| **Kategorie wechseln** | `DashboardPage` | Stop + Start | Stop + Start (pending) |
| **Auftrag wechseln** | `DashboardPage` → `ZeiterfassungPage` | Stop, dann neue Buchung | Stop (pending), dann neue Buchung |

---

## 1. Neue Buchung starten

**Datei:** `src/pages/ZeiterfassungPage.tsx`

### Flow
```
Projekt wählen → Kategorie wählen (optional) → Tätigkeit wählen → Start
```

### Online-Modus
```typescript
POST /api/zeiterfassung/start
Body: {
    token,
    projektId,
    arbeitsgangId,
    produktkategorieId  // kann null sein wenn "Ohne Kategorie"
}
```

- **Erfolg:** Session in `localStorage` speichern → Dashboard
- **Fehler (4xx):** Alert anzeigen, NICHT offline queuen

### Offline-Modus
```typescript
OfflineService.addPendingEntry('start', {
    token,
    projektId,
    arbeitsgangId,
    produktkategorieId
})
```
→ Fake-Session mit `id: 'offline-' + Date.now()` erstellen → Dashboard

### ⚠️ Potenzielle Probleme
1. `produktkategorieId` kann `undefined` oder `null` sein - beides sollte als "keine Kategorie" behandelt werden
2. Offline-Session hat keine echte ID - wird bei Sync überschrieben

---

## 2. Buchung stoppen (Feierabend)

**Datei:** `src/pages/DashboardPage.tsx` - Funktion `handleStopSession`

### Online-Modus
```typescript
POST /api/zeiterfassung/stop
Body: { token }
```

- **Erfolg:** `clearOfflineHeuteMinuten()` aufrufen (Server hat echte Daten)

### Offline-Modus
```typescript
OfflineService.addPendingEntry('stop', { token })

// Minuten für Offline-Tracking speichern (nur bei Arbeit, nicht Pause)
if (isWorkSession && elapsedMinutes > 0) {
    OfflineService.addOfflineWorkedMinutes(elapsedMinutes)
}
```

**Danach:** `localStorage.removeItem('zeiterfassung_active_session')`

### ⚠️ Potenzielle Probleme
1. Stop ohne aktive Session auf Server → 4xx Fehler beim Sync

---

## 3. Pause starten

**Datei:** `src/pages/DashboardPage.tsx` - Funktion `handlePause`

### Online-Modus
```typescript
POST /api/zeiterfassung/pause
Body: { token }
```

- **Erfolg:** Server stoppt aktuelle Arbeit und startet Pause-Buchung

### Offline-Modus
```typescript
OfflineService.addPendingEntry('pause', { token })
```
→ Optimistische UI-Update mit Pause-Session

### ⚠️ Potenzielle Probleme
1. Was passiert offline wenn User zweimal Pause drückt?

---

## 4. Tätigkeit wechseln (Quick Switch)

**Datei:** `src/pages/DashboardPage.tsx` - Funktion `handleSwitchArbeitsgang`

### Online-Modus
1. `POST /api/zeiterfassung/stop` (alte Buchung)
2. `POST /api/zeiterfassung/start` (neue Buchung mit neuer Tätigkeit)

```typescript
// Start-Request:
{
    token,
    projektId: selectedProjektId || activeSession.projektId,
    arbeitsgangId: newArbeitsgang.id,
    produktkategorieId: activeSession.produktkategorieId ?? null  // kategorie bleibt!
}
```

### Offline-Modus
```typescript
OfflineService.addPendingEntry('stop', { token })
OfflineService.addPendingEntry('start', {
    token,
    projektId,
    arbeitsgangId,
    produktkategorieId: activeSession.produktkategorieId ?? null
})
```

### ✅ Behobene Probleme (15.01.2026)
1. ~~`produktkategorieId` war `undefined`~~ → Behoben durch `??` Operator

---

## 5. Kategorie wechseln (Quick Switch)

**Datei:** `src/pages/DashboardPage.tsx` - Funktion `handleSwitchKategorie`

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

### Offline-Modus
Gleich wie Online, aber mit `addPendingEntry`

### ⚠️ Potenzielle Probleme
1. Keine Fehlerbehandlung wenn Start fehlschlägt

---

## 6. Auftrag wechseln

**Datei:** `src/pages/DashboardPage.tsx` - Funktion `handleSwitchProjekt`

### Flow
1. Aktuelle Buchung stoppen
2. Zu `ZeiterfassungPage` navigieren
3. User wählt **alles** neu: Projekt, Kategorie, Tätigkeit

### Online/Offline-Modus
```typescript
POST /api/zeiterfassung/stop
// Bei Fehler:
OfflineService.addPendingEntry('stop', { token })
```

Danach: `navigate('/zeiterfassung')` → Neue Buchung komplett neu

---

## 7. Sync-Prozess

**Datei:** `src/services/OfflineService.ts` - Funktion `syncPending`

### Ablauf
1. Prüfe `navigator.onLine` - wenn offline, überspringe
2. Hole alle `pending` Einträge aus IndexedDB
3. **Sortiere nach Timestamp** (chronologisch!)
4. Für jeden Eintrag:
   - `POST /api/zeiterfassung/{type}`
   - Erfolg → Lösche aus DB
   - 4xx Fehler → **Lösche aus DB** (wird nie funktionieren)
   - 5xx Fehler → Behalte für Retry

### ⚠️ Potenzielle Probleme
1. **Reihenfolge kritisch!** Stop vor Start muss sein
2. **4xx wird verworfen** - Aber was wenn Stop ungültig ist? Start wird auch verworfen?
3. **Keine Transaktions-Logik** - Ein Stop kann erfolgreich sein, aber folgender Start scheitert

---

## 8. Session laden

**Datei:** `src/pages/DashboardPage.tsx` - Funktion `loadActiveSession`

### Ablauf
1. Prüfe auf `pending` Einträge in IndexedDB
2. **Wenn pending Einträge existieren**: Lade Session aus `localStorage` (KEIN Server-Sync!)
3. Sonst: Lade aktive Session vom Server

### ⚠️ Potenzielle Probleme
1. Sync-Zustand kann veraltet sein - Lokale Session weicht von Server ab
2. `produktkategorieId/Name` werden vom Server geladen - aber nur wenn online synced

---

## Datenfluss-Diagramm

```
┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
│ ZeiterfassungPage│ ────▶│   DashboardPage │ ────▶│  OfflineService │
│                 │      │                 │      │                 │
│ - Projekt       │      │ - Stop          │      │ - IndexedDB     │
│ - Kategorie     │      │ - Pause         │      │ - Pending Queue │
│ - Tätigkeit     │      │ - Tätigkeit ▵   │      │ - Cache         │
│ - Start         │      │ - Kategorie ▵   │      │ - Sync          │
└─────────────────┘      │ - Auftrag ▵     │      └─────────────────┘
                         └─────────────────┘
                                 │
                                 ▼
                         ┌─────────────────┐
                         │ localStorage    │
                         │                 │
                         │ - active_session│
                         │ - token         │
                         └─────────────────┘
```

---

## Changelog

| Datum | Änderung |
|-------|----------|
| 15.01.2026 | Fix Backend: `produktkategorieId` + `produktkategorieName` fehlen in `/api/zeiterfassung/aktiv/{token}` Response. Hinzugefügt in `ZeiterfassungApiService.getAktiveBuchung()`. |
| 15.01.2026 | Fix Frontend: `produktkategorieId` bei Tätigkeitswechsel - `??` statt `\|\|` für undefined-Handling. |
