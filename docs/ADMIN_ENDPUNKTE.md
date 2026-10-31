# Admin- und Wartungs-Endpunkte ohne Knopf im Frontend

Diese Endpunkte gibt es nur im Backend. Es gibt im PC-Frontend und in der
Zeiterfassungs-App keinen Knopf dafür, man ruft sie direkt im Browser auf.
Stand: 12.10.2026, abgeglichen mit Controllern und `SecurityConfig`.
Alle übrigen Endpunkte stehen in der [API-Referenz](API_REFERENZ.md).

## So geht's

1. Im Chrome normal im ERP anmelden (als Admin, wo „nur Admin“ steht).
2. **GET-Endpunkte:** Adresse in die Adresszeile kopieren und Enter drücken.
   `localhost:8080` durch die eigene Serveradresse ersetzen.
3. **POST-/DELETE-Endpunkte:** Diese gehen nicht über die Adresszeile. Im
   ERP-Tab mit `F12` (Mac: `Cmd`+`Alt`+`J`) die Konsole öffnen, die Zeile
   einfügen und Enter drücken. Die Zeile schickt den CSRF-Schutz-Token
   (`XSRF-TOKEN`-Cookie) automatisch mit. Chrome fragt beim ersten Einfügen
   eventuell nach `allow pasting`. Das einmal eintippen, dann geht's.

Platzhalter in `{...}` (z. B. `{lieferantId}`) durch die echte ID ersetzen.

> **Vorsicht:** Fast alle POST-Aufrufe schreiben über den ganzen Bestand
> oder kosten KI-Aufrufe. Vorher ein Backup ziehen und möglichst
> außerhalb der Arbeitszeit starten.

---

## 1. Nur lesen (GET, Adresszeile)

| Was | Adresse | Wer |
| --- | --- | --- |
| Status Microsoft/Amazon/Apple-Rechnungsabruf | `http://localhost:8080/api/admin/vendor-invoices/status` | nur Admin |
| Doppelte Lieferanten-Dokumente anzeigen (zeigt, was die Bereinigung löschen würde) | `http://localhost:8080/api/lieferant-dokumente/duplicates` | nur Admin |
| Stand des Spam-Lernmodells | `http://localhost:8080/api/emails/spam-model/stats` | angemeldet |
| Ist die KI-Mailzuordnung (Gemini) an? | `http://localhost:8080/api/email-ki/status` | angemeldet |
| Kassenbuch-Protokoll auf Veränderungen prüfen | `http://localhost:8080/api/buchhaltung/kassenbuch/pruefung` | Abteilungsrecht „Belege sehen“ |
| GoBD-Verfahrensdokumentation herunterladen (.txt) | `http://localhost:8080/api/buchhaltung/kassenbuch/verfahrensdokumentation` | Abteilungsrecht „Belege sehen“ |

---

## 2. Wartungsläufe unter `/api/admin` (nur Admin, Konsole)

### Projekte: fehlende Auftragspreise nachtragen
Trägt bei Projekten ohne Auftragspreis den Preis aus Angebot/AB/Nachtrag
(sonst Summe der Rechnungen) nach. Projekte mit Preis bleiben unverändert.
```js
await fetch('/api/admin/projekte/preise-nachtragen',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```

### Rechnungen mit Rabatt: Beträge neu rechnen
Rechnet Netto/Brutto bei Bestandsdokumenten mit Rabatt aus den Positionen
neu, auch bei gebuchten Rechnungen (landet im Audit-Trail). Wiederholbar.
**Nur nach Feierabend:** Blockiert während des Laufs Buchen, Versenden,
Stornieren und die Online-Annahme durch Kunden.
```js
await fetch('/api/admin/projekte/rabatt-betraege-korrigieren',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```

### Werkstoffzeugnisse von der KI nachlesen lassen
Liest alle Werkstoffzeugnisse ohne Nummer oder ohne Positionen per KI und
hängt sie in ihre Belegkette. Kostet KI-Aufrufe, antwortet erst am Ende.
Läuft schon einer, kommt `409`.
```js
await fetch('/api/admin/lieferant-dokumente/werkstoffzeugnisse/nachlesen',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```

### Händler-Rechnungen abrufen (Microsoft/Amazon)
Alle zusammen:
```js
await fetch('/api/admin/vendor-invoices/fetch',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```
Nur Microsoft:
```js
await fetch('/api/admin/vendor-invoices/fetch/microsoft',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```
Nur Amazon:
```js
await fetch('/api/admin/vendor-invoices/fetch/amazon',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```

---

## 3. E-Mails

### Verläufe bereinigen (nur Admin)
Löst falsche Verknüpfungen, die nur über den Betreff entstanden sind, und
verknüpft neu. **Standard ist ein Probelauf**, der nichts speichert:
```js
await fetch('/api/emails/admin/rebuild-threads',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```
Wirklich speichern:
```js
await fetch('/api/emails/admin/rebuild-threads?probelauf=false',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```

### Anhang-Dateinamen reparieren (nur Admin)
Dekodiert Dateinamen, die noch als `=?iso-8859-1?Q?...?=` gespeichert sind.
Wiederholbar.
```js
await fetch('/api/emails/admin/backfill-attachment-filenames',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```

### Lieferanten-Dokumente: XML durch PDF ersetzen (nur Admin)
Dokumente, die wegen eines alten Fehlers die XML statt der PDF anzeigen,
bekommen die PDF aus derselben Mail. Wiederholbar.
```js
await fetch('/api/emails/admin/backfill-xml-to-pdf',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```

### Antworten mit ihrer Ursprungsmail verknüpfen (nur Admin)
Verknüpft bestehende Mails nachträglich mit der Mail, auf die sie antworten,
und übernimmt deren Zuordnung (Projekt/Anfrage/Lieferant).
```js
await fetch('/api/emails/backfill-parents',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```

### Postfach sofort abrufen (nur Admin)
Startet den Mail-Import jetzt statt beim nächsten Zeitplan.
```js
await fetch('/api/emails/import',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.text())
```

### Nicht zugeordnete Mails neu zuordnen (nur Admin)
Prüft Spam/Newsletter neu und versucht alle Posteingangs-Mails ohne
Zuordnung automatisch zuzuordnen.
```js
await fetch('/api/emails/scan-assignments',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```

### Spam-Prüfung für noch nicht geprüfte Mails (nur Admin)
```js
await fetch('/api/emails/scan-spam',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```

### Anfrage-Erkennung für noch nicht geprüfte Mails (nur Admin)
```js
await fetch('/api/emails/scan-inquiries',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```

### Steuerberater-Mails (Lohnabrechnungen, BWA) nachträglich verarbeiten (nur Admin)
```js
await fetch('/api/emails/scan-steuerberater',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```

### Eine Mail per KI einordnen (angemeldet)
Geht nur für Mails, die man selbst sehen darf, sonst kommt `404`.
Nur ansehen, was die KI vorschlägt:
```js
await fetch('/api/email-ki/classify/{emailId}',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```
Einordnen und direkt zuordnen (ab 60 % Sicherheit):
```js
await fetch('/api/email-ki/classify-and-assign/{emailId}',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```

> `POST /api/emails/spam-model/bootstrap` (Spam-Modell aus CSV anlernen, nur Admin)
> braucht einen Datei-Upload und passt deshalb nicht in eine Konsolenzeile.

---

## 4. Lieferanten-Dokumente

### Doppelte Dokumente löschen (nur Admin, endgültig)
Behält pro Dokumentnummer und Lieferant das älteste, löscht den Rest.
**Vorher mit der GET-Adresse aus Abschnitt 1 prüfen, was weg kommt.**
```js
await fetch('/api/lieferant-dokumente/duplicates',{method:'DELETE',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```

### Belegketten ergänzen (nur Admin)
Ergänzt fehlende Verknüpfungen zwischen den Dokumenten eines Lieferanten.
Vorhandene Verknüpfungen bleiben.

Alle Lieferanten:
```js
await fetch('/api/lieferant-dokumente/relink-all',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```
Ein Lieferant:
```js
await fetch('/api/lieferant-dokumente/lieferant/{lieferantId}/relink',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```

### Dokumente neu von der KI lesen lassen
Alle Dokumente eines Lieferanten (nur Admin, überschreibt die gelesenen
Daten, kostet KI-Aufrufe):
```js
await fetch('/api/lieferant-dokumente/lieferant/{lieferantId}/reanalyze',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```
Ein einzelnes Dokument (angemeldet; nur Dokumenttypen, die die eigene Abteilung
sehen darf, sonst `404`):
```js
await fetch('/api/lieferant-dokumente/{dokumentId}/reanalyze',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```

### Mail-Anhänge zu Lieferanten-Dokumenten machen (nur Admin)
Legt aus PDF-/XML-Anhängen von Lieferanten-Mails Dokumente an und lässt sie
von der KI lesen.

Alle Lieferanten-Mails:
```js
await fetch('/api/lieferant-dokumente/process-assigned-emails',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```
Alle Mails eines Lieferanten:
```js
await fetch('/api/lieferant-dokumente/lieferant/{lieferantId}/process-emails',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```
Eine Mail:
```js
await fetch('/api/lieferant-dokumente/process-email/{emailId}',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```

### Alle Anhänge eines Lieferanten neu verarbeiten (nur Admin)
Setzt die „schon verarbeitet“-Markierung der PDF-Anhänge zurück. Anhänge, zu denen
es schon ein Dokument gibt, werden nur wieder damit verknüpft – es entsteht kein
zweites Dokument und kein KI-Aufruf. Nur Anhänge ohne Dokument werden neu gelesen
(kostet KI-Aufrufe). Bestehende Dokumente neu lesen lassen: siehe „reanalyze“ oben.
```js
await fetch('/api/admin/lieferanten/{lieferantId}/reprocess-attachments',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```

### Doppelte Lieferanten-Dokumente (läuft automatisch, kein Endpoint)
Beim Start löscht `LieferantDokumentDuplikatBackfillRunner` Dokumente, die beim selben
Lieferanten dieselbe gespeicherte Datei zeigen oder deren Mail-Anhang Byte für Byte
gleich ist (z. B. die Widerrufsbelehrung in jeder Mail); je Gruppe bleibt eins.
Neue Mails schicken einen schon bekannten Anhang gar nicht erst an die KI.
Gruppen, in denen mehrere Exemplare von Hand gepflegt sind (Projekt-Zuordnung, Beleg,
Reklamation, bezahlt, freigegeben, Lagerbestellung), bleiben stehen und erscheinen im
Log unter `[Duplikate] Gruppe … übersprungen` – dann bei jedem Start erneut, bis sie von
Hand bereinigt sind. Erledigt ist der Lauf, wenn in den System-Einstellungen
`lieferant.duplikate.version` = `2` steht.

---

## 5. Sonstiges

### Mahnlauf sofort starten (nur Admin)
Verschickt **echte** Zahlungserinnerungen/Mahnungen an Kunden, wie der
tägliche Lauf. Läuft schon einer, kommt `409`.
```js
await fetch('/api/mahnwesen/lauf',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```

### Zeitkonten-Zwischenspeicher neu füllen (nur Admin)
Rechnet die Monatssalden aller aktiven Mitarbeiter neu in den Cache.
```js
await fetch('/api/zeitverwaltung/saldo-cache/warmup',{method:'POST',headers:{'X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}}).then(r=>r.json())
```

---

## Wer darf was? (So ist es abgesichert)

- **„nur Admin“** prüft die `SecurityConfig` über Pfad-Regeln mit
  `hasRole("ADMIN")`. `@PreAuthorize` gibt es im Projekt nicht. Von den
  Endpunkten oben betrifft das:
  - alles unter `/api/admin/**` (Projekte, Werkstoffzeugnisse,
    Händler-Rechnungen, `reprocess-attachments`)
  - `POST /api/emails/admin/**` (Verläufe bereinigen, Dateinamen, XML durch PDF)
  - `GET /api/lieferant-dokumente/duplicates` und jedes
    `DELETE /api/lieferant-dokumente/**`
  - `POST /api/lieferant-dokumente/relink-all`,
    `POST /api/lieferant-dokumente/lieferant/{lieferantId}/relink` und
    `POST /api/lieferant-dokumente/lieferant/{lieferantId}/reanalyze`
  - `POST /api/mahnwesen/lauf`
  - die Massenläufe (`SecurityConfig.ADMIN_MASSENLAEUFE`, jede Methode):
    Postfach-Abruf `POST /api/emails/import`, `scan-*`, `backfill-parents`,
    das Anlernen des Spam-Modells (`bootstrap`), die `process-*`-Läufe der
    Lieferanten-Dokumente und `POST /api/zeitverwaltung/saldo-cache/warmup`
- **„angemeldet“** heißt: jede angemeldete Person darf es, eine Rolle wird
  nicht geprüft. Das gilt für den Stand des Spam-Modells (`stats`), die
  KI-Einordnung und das Neu-Lesen eines einzelnen Dokuments. Eingeschränkt
  wird nur, *was* man sieht:
  bei der KI-Einordnung die Mails aus freigegebenen Postfächern, beim
  einzelnen Dokument die Dokumenttypen der eigenen Abteilung.
- **Kassenbuch-Prüfung und Verfahrensdokumentation** brauchen das
  Abteilungsrecht „Belege sehen“, sonst kommt `403`.
- Mit dem Handy-Token der Zeiterfassungs-App geht keiner dieser Aufrufe;
  sie laufen nur mit einer PC-Anmeldung.

---

## Antworten verstehen

| Code | Bedeutung |
| --- | --- |
| `200` | Hat geklappt, Ergebnis steht in der Antwort |
| `400` | Aufruf passt nicht, z. B. fehlende oder falsche Angaben |
| `401` | Nicht angemeldet: erst im ERP einloggen |
| `403` | Angemeldet, aber kein Admin bzw. Recht fehlt, oder CSRF-Token fehlt (Seite neu laden und nochmal) |
| `404` | Gibt es nicht, oder man darf es nicht sehen (Mail, Dokument, Lieferant) |
| `409` | Derselbe Lauf läuft gerade schon |
