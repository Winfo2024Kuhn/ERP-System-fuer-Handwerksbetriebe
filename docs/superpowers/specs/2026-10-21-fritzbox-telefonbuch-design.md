# FRITZ!Box-Telefonbuch „ERP-Kontakte“

Stand: 21.10.2026

## Ziel

Ruft ein Kunde, Lieferant, Mitarbeiter oder Steuerberater an, zeigen die Telefone und die FRITZ!App Fon seinen Namen. Dafür überträgt das ERP seine Kontakte in ein eigenes Telefonbuch der FRITZ!Box und hält es aktuell.

## Umfang

- **Richtung nur ERP → FRITZ!Box.** Das ERP legt das Telefonbuch „ERP-Kontakte“ an und verwaltet nur dieses. Andere Telefonbücher werden nie gelesen, geändert oder gelöscht.
- **Inhalt** (ein Eintrag je Kontakt, alle Nummern, ohne Doppelte; Kontakte ohne gültige Nummer fehlen):
  - Kunden, ohne gesperrte (Art. 18 DSGVO) und ohne anonymisierte (`V432`). Anonymisierte fliegen beim nächsten Abgleich raus.
  - Lieferanten mit `istAktiv = true` oder ohne Angabe (ältere Datensätze; neue legt das ERP immer mit `true` an).
  - Mitarbeiter mit `aktiv = true` und `art = MENSCH` (System-Benutzer nicht). `telefon` = Handy, `festnetz` = privat.
  - Steuerberater mit `aktiv = true`, deren `gueltigBis` nicht vorbei ist; jeder Ansprechpartner mit eigener Nummer als eigener Eintrag.
  - Gemerkte Zusatznummern (`kontakt_rufnummer`) beim jeweiligen Kunden, Lieferanten oder Steuerberater.
- **Name:** „Name (Art)“, z. B. „Muster Bau GmbH (Kunde)“, „Max Mustermann (Mitarbeiter)“, „Erika Musterfrau (Steuerberater Kanzlei Beispiel)“. Höchstens 64 Zeichen. Zu lang: Erst wird der Name mit „…“ gekürzt, die Art bleibt sichtbar. Steuerzeichen werden entfernt, Leerraum zusammengefasst.
- **Nummern:** wie die Box sie wählt und Anrufer meldet: inländisch mit 0 („0123456789“), Ausland mit + („+431234567“), ohne Vorwahl nur die Ziffern. Normalisierung über `RufnummerNormalisierer`. Erlaubt sind 3 bis 32 Zeichen, höchstens 9 Nummern je Eintrag.
- **Nummerntyp:** `work` (geschäftlich), `mobile` (Handy, auch erkannt in Festnetz-Feldern über 015x/016x/017x), `home` (privat: Kunden mit Anrede Herr/Frau/Familie, Festnetz der Mitarbeiter). Die erste Nummer ist die Hauptnummer (`prio="1"`).

## Ablauf

### Wann abgeglichen wird

| Auslöser | Art | Umsetzung |
| --- | --- | --- |
| Änderung an Kunde, Lieferant, Mitarbeiter, Steuerberater, Ansprechpartner oder Zusatznummer | Teilabgleich ca. 10 s nach dem Commit, entprellt | `TelefonbuchAenderungsBeobachter`: Hibernate-Post-Commit-Listener (`POST_COMMIT_INSERT/UPDATE/DELETE`), nur bei Feldern, die im Telefonbuch landen; nie bei Rollback. `TelefonbuchAbgleichPlaner` plant einen Lauf; weitere Meldungen bis dahin hängen sich an. |
| Start des Servers | Vollabgleich (bzw. offene Löschung) 45 s nach `ApplicationReadyEvent` | im Hintergrund, der Start wartet nicht |
| Nachts 03:40 Uhr | Vollabgleich (bzw. offene Löschung) | `@Scheduled`, Cron über `telefon.telefonbuch.vollabgleich.cron` änderbar |
| Schalter eingeschaltet | Vollabgleich sofort im Hintergrund | `TelefonbuchAbgleichPlaner.sofort()` |
| Schalter ausgeschaltet | Telefonbuch „ERP-Kontakte“ sofort im Hintergrund aus der Box löschen | siehe „Ausschalten“ |
| Knopf „Jetzt abgleichen“ bzw. „Löschen jetzt nachholen“ | Vollabgleich bzw. Löschung sofort im Hintergrund | Endpunkt antwortet 202, das Frontend fragt den Status alle 2 s ab |

Es wird nur übertragen, wenn der Schalter an, die Telefon-Anbindung aktiv und ein Zugang eingerichtet ist. Alle Läufe laufen auf einem eigenen Thread (`telefonbuch-`, ein `ThreadPoolTaskScheduler` mit einem Thread), nicht auf den gemeinsamen Scheduler-Threads und nie im HTTP-Thread. Läufe laufen nie parallel (`ReentrantLock` in `TelefonbuchAbgleichService`). Trifft ein geplanter Lauf auf einen laufenden, wird er danach nachgeholt. Massenänderungen per JPQL/SQL sieht Hibernate nicht; die fängt der Vollabgleich (Start, Nacht).

### Ausschalten (DSGVO)

Was im ERP gelöscht, gesperrt oder anonymisiert wird, darf nicht in der Box liegen bleiben. Deshalb löscht Ausschalten das Telefonbuch:

1. Frontend fragt über den Bestätigungsdialog nach („Das Telefonbuch „ERP-Kontakte“ wird dabei aus der FRITZ!Box gelöscht.“).
2. Der Schalter ist sofort aus, dazu wird der Merker `telefon.telefonbuch.loeschung-offen` gesetzt.
3. Der nächste Lauf löscht per TR-064 `DeletePhonebook` (`NewPhonebookID`, `NewPhonebookExtraID`) – nur die gemerkte ID, wenn sie am Namen „ERP-Kontakte“ bestätigt ist, sonst das Telefonbuch mit genau diesem Namen. Unmittelbar vor dem Löschen wird der Name noch einmal geprüft; das Haupttelefonbuch (ID 0) wird nie gelöscht. Danach Zuordnungstabelle leeren, gemerkte ID vergessen, Merker löschen.
4. Ist die Box nicht erreichbar, bleibt der Schalter aus und der Merker stehen. Der Status zeigt „noch nicht gelöscht“; nachgeholt wird beim Start, nachts, bei der nächsten Änderung und per Knopf „Löschen jetzt nachholen“. Dafür reicht ein eingerichteter Zugang, die Telefon-Anbindung muss nicht aktiv sein.
5. Wiedereinschalten verwirft eine offene Löschung; der Vollabgleich legt alles neu an.

### Ein Lauf (`TelefonbuchAbgleichService`)

1. Soll-Liste aus der Datenbank berechnen (`TelefonbuchSollListe`).
2. Telefonbuch bereitstellen: zuerst unter der gemerkten ID, aber nur, wenn es dort wirklich „ERP-Kontakte“ heißt. Sonst über den Namen suchen, sonst anlegen. Ist es neu oder ein anderes, ist die Zuordnungstabelle ungültig: leeren und alles neu senden.
3. Vollabgleich: Ist-Stand der Box lesen (`GetPhonebook` → URL → Export). Kennt die Tabelle Einträge, die in der Box fehlen, werden sie neu angelegt. Einträge im ERP-Telefonbuch, die nicht vom ERP stammen, werden gelöscht.
4. Je Soll-Eintrag: neu → anlegen; anderer Inhalts-Hash → mit seiner Kennung überschreiben; gleicher Hash → nichts.
5. Einträge in der Tabelle ohne Soll-Eintrag → in der Box löschen.
6. Stand merken: Zeitpunkt, Anzahl, Fehler.

### TR-064 (`FritzBoxTelefonbuch`, Dienst `X_AVM-DE_OnTel:1`, `/upnp/control/x_contact`)

| Aktion | Argumente → Antwort |
| --- | --- |
| `GetPhonebookList` | → `NewPhonebookList` („0,1,2“) |
| `GetPhonebook` | `NewPhonebookID` → `NewPhonebookName`, `NewPhonebookExtraID`, `NewPhonebookURL` |
| `AddPhonebook` | `NewPhonebookExtraID` (leer), `NewPhonebookName` |
| `SetPhonebookEntryUID` | `NewPhonebookID`, `NewPhonebookEntryData` → `NewPhonebookEntryUniqueID` |
| `DeletePhonebookEntryUID` | `NewPhonebookID`, `NewPhonebookEntryUniqueID` |

Eintrags-XML: `<?xml version="1.0" encoding="utf-8"?><contact><category>0</category><person><realName>…</realName></person><telephony nid="n"><number type="work" prio="1" id="0">…</number>…</telephony><uniqueid>…</uniqueid></contact>` (`uniqueid` nur beim Ändern).

**Escaping:** `TelefonbuchXml` escaped jeden Wert genau einmal für das Eintrags-XML. `Tr064Client` escaped das ganze XML als SOAP-Argument ein zweites Mal; die Box packt die SOAP-Ebene aus und liest dann das Eintrags-XML. Steuerzeichen werden vorher entfernt. Antworten und Export laufen nur über `SicheresXml` (kein DOCTYPE, keine externen Entities). Telefonbuch- und Eintrags-IDs müssen aus Ziffern bestehen, sonst wird nichts gesendet.

Die Schnittstelle `TelefonbuchAnlage` ist getrennt von `TelefonAnlage`. Der Rest des ERP kennt keine FRITZ!Box-Einzelheiten.

## Datenmodell

`V435__telefonbuch_zuordnung.sql`:

| Spalte | Bedeutung |
| --- | --- |
| `quelle_typ` | `KUNDE`, `LIEFERANT`, `MITARBEITER`, `STEUERBERATER`, `STEUERBERATER_ANSPRECHPARTNER` (VARCHAR, ohne CHECK) |
| `quelle_id` | ID des Kontakts. Bewusst ohne Fremdschlüssel: gelöschte Kontakte bleiben, bis ihr Eintrag in der Box gelöscht ist. |
| `eintrag_id` | `uniqueid` des Eintrags in der Box |
| `inhalt_hash` | SHA-256 über Name und Nummern (mit Versionskennung „v1“) |
| `gesendet_am` | letzte Übertragung |

Eindeutig: (`quelle_typ`, `quelle_id`).

In `system_setting` (keine Geheimnisse): `telefon.telefonbuch.aktiv`, `.id`, `.letzter-abgleich`, `.anzahl`, `.letzter-fehler`, `.loeschung-offen`.

## Oberfläche

System-Einstellungen › Telefon, Karte „FRITZ!Box-Telefonbuch“ (nur Admins, `/api/telefon/einstellungen/telefonbuch/**`):

- Schalter „Kontakte ins FRITZ!Box-Telefonbuch übertragen“ (Standard aus, wird sofort gespeichert). Ausschalten nach Rückfrage: nichts mehr übertragen, Telefonbuch aus der Box löschen.
- Hinweise: In der FRITZ!App Fon und an den Telefonen „ERP-Kontakte“ auswählen. Die Nummern der Mitarbeiter sind dort für alle sichtbar.
- Stand: zuletzt abgeglichen, Anzahl Einträge, letzter Fehler in Klartext, Hinweis bei offener Löschung.
- Knopf „Jetzt abgleichen“ (bei offener Löschung „Löschen jetzt nachholen“). Der Lauf startet im Hintergrund (HTTP 202). Solange der Status `laeuftGerade` meldet, fragt die Karte alle 2 s nach (Abbruch beim Verlassen der Seite) und zeigt danach `letztesErgebnis` (neu, geändert, gelöscht, abgelehnt) als Banner, Fehler zusätzlich als Toast. Gesperrt mit Begründung, solange Schalter oder Telefon-Anbindung aus sind.
- Info-Knopf: was im Hintergrund passiert und was es bewirkt, auch: Von Hand angelegte Einträge in „ERP-Kontakte“ werden beim nächsten Abgleich gelöscht – eigene Kontakte bitte in ein anderes Telefonbuch.

Status-Endpunkt (`GET …/telefonbuch`): `aktiv`, `telefonAnbindungAktiv`, `telefonbuchName`, `zuletztAbgeglichen`, `anzahlEintraege`, `letzterFehler`, `loeschungOffen`, `laeuftGerade` (angefordert oder Sperre belegt), `letztesErgebnis` (im Speicher, seit dem Serverstart).

## Fehlerfälle

| Fall | Verhalten |
| --- | --- |
| Box nicht erreichbar, Passwort falsch | Lauf bricht ab, Grund als „letzter Fehler“. Speichern im ERP und Serverstart bleiben ungestört (alle Fehler im Beobachter und Planer abgefangen). |
| Box beim Ausschalten nicht erreichbar | Schalter bleibt aus, „Löschung offen“ im Status, wird nachgeholt (Start, Nacht, Knopf) |
| Unerwarteter Fehler (z. B. Datenbank) | allgemeine Meldung als „letzter Fehler“, im Log nur der Klassenname |
| Benutzer ohne Recht (UPnP 606/607, HTTP 403) | „Der FRITZ!Box-Benutzer des ERP darf das Telefonbuch nicht ändern. Bitte … das Recht „Sprachnachrichten, Faxnachrichten, FRITZ!App Fon und Anrufliste“ geben.“ (laut AVM „TR-064 First Steps“ brauchen alle Telefonbuch-Aktionen Recht P) |
| Box verlangt zweiten Faktor (866–868) | eigene Meldung mit Hinweis auf die Einstellung in der Box |
| Box lehnt einzelne Einträge ab | Rest wird übertragen, „N Einträge hat die FRITZ!Box abgelehnt.“. Nimmt sie gar nichts an, Abbruch nach 5 Ablehnungen. |
| Eintrag in der Box von Hand gelöscht | Ändern meldet 713/714 → wird neu angelegt; Vollabgleich legt Fehlende ohnehin neu an. |
| Telefonbuch gelöscht, Box zurückgesetzt oder getauscht | über den Namen gesucht bzw. neu angelegt, alles neu gesendet |
| Abgleich läuft schon | „Der Abgleich läuft gerade schon …“; geplante Läufe werden nachgeholt |

DSGVO: In den Logs stehen nur Zähler und Fehlergründe, keine Namen und keine Nummern.

## Offene Punkte

- **Nicht gegen eine echte FRITZ!Box getestet.** Geprüft gegen eine simulierte Box (`FritzBoxTelefonbuchIntegrationTest`). Vor der Freigabe an der Büro-Box prüfen: Eintrags-XML wird angenommen (mit `<?xml …?>`-Kopf, ohne `<services/>`/`<setup/>`), Anzeige der Namen bei Anrufen, Fehlercode beim Ändern eines fehlenden Eintrags.
- **Größenbegrenzung des Telefonbuchs:** AVM nennt keine feste Grenze in der TR-064-Doku. Bei sehr vielen Kunden (mehrere tausend) kann die Box ablehnen oder langsam werden. Dann ggf. nur Kunden mit Aufträgen der letzten Jahre übertragen.
- **Länge von Namen und Nummern:** 64 bzw. 32 Zeichen sind vorsichtig gewählt, nicht von AVM bestätigt.
- **`letztesErgebnis`** liegt nur im Speicher; nach einem Neustart zeigt der Status nur Zeitpunkt, Anzahl und letzten Fehler.
- **Von Hand geänderte Einträge** (Name oder Nummer in der Box bearbeitet) erkennt der Vollabgleich nicht. Er prüft nur, ob der Eintrag noch da ist. Sie werden erst bei der nächsten Änderung im ERP überschrieben.
- **Dauer des ersten Abgleichs:** je Eintrag ein SOAP-Aufruf (mit Digest-Anmeldung zwei HTTP-Anfragen). Bei 1.000 Kontakten etwa 1–2 Minuten. Läuft im Hintergrund; die Karte zeigt so lange „Wird abgeglichen …“.
- **Zweiter Faktor:** Ob FRITZ!OS für Telefonbuch-Änderungen per TR-064 eine Bestätigung verlangt und wie der Menüpunkt genau heißt, ist offen.
