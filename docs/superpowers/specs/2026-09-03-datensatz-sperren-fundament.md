# Datensatz-Sperren & Inaktivitäts-Freigabe — Teilprojekt 1: Fundament

> **Herkunft:** Die Original-Spec aus der Cloud-Sitzung ging verloren (Container weg,
> `docs/superpowers/` ist gitignored). Dieser Text ist der Inhalt von GitHub-Issue #82,
> das aus genau dieser Spec erzeugt wurde. Zurückgeholt am 04.09.2026.
> Issue: https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/82

## Ausgangsproblem

Auf einem 14"-MacBook schlieÃŸt der X-Button im Dokument-Editor den Browser-Tab nicht zuverlÃ¤ssig (`window.close()` wird vom Browser blockiert). Der Nutzer geht davon aus, dass das Dokument danach nicht mehr gesperrt ist â€” ist es aber nicht, weil die Freigabe der Sperre bisher ausschlieÃŸlich am SchlieÃŸen der React-Komponente hÃ¤ngt. Bleibt die Komponente offen (weil der Tab nicht schlieÃŸt), bleibt auch die Sperre aktiv.

ZusÃ¤tzlich fehlt bisher ein InaktivitÃ¤ts-Timer: Wer ein Dokument offen lÃ¤sst, ohne zu arbeiten, blockiert Kollegen unnÃ¶tig lange.

Das bestehende Sperr-System ist auÃŸerdem nur fÃ¼r zwei Dokumenttypen gebaut (Rechnungsausgang/-eingang) und lÃ¤sst sich nicht ohne Backend-Umbau auf andere DatensÃ¤tze (Projekte, Anfragen, Kunden, â€¦) Ã¼bertragen. Und es gibt aktuell kein zweites Sicherheitsnetz auf Datenbankebene: Wenn zwei Personen â€” oder die Mobile-App, die nie eine Sperr-OberflÃ¤che bekommen wird â€” gleichzeitig denselben Datensatz speichern, gewinnt kommentarlos, wer zuletzt speichert.

## Ziel

Ein wiederverwendbares Sperr-Fundament, das:

- die Ursache des X-Button-Problems behebt: Freigabe der Sperre komplett vom SchlieÃŸen des Browser-Tabs entkoppeln,
- einen InaktivitÃ¤ts-Timer einfÃ¼hrt,
- das bisher auf zwei Dokumenttypen beschrÃ¤nkte Sperr-System so verallgemeinert, dass jeder kÃ¼nftige Datensatz-Typ (Projekte, Anfragen, Kunden, â€¦) ohne Backend-Umbau sperrbar wird â€” nur ein neuer Eintrag in einer AufzÃ¤hlung,
- mit einer Versionsnummer je Datensatz (optimistisches Sperren) ein zweites Sicherheitsnetz fÃ¼r alle Speicherwege einzieht â€” auch fÃ¼r die Mobile-App `react-zeiterfassung`, die nie eine Sperr-OberflÃ¤che bekommen wird,
- fÃ¼nf wiederverwendbare Bausteine fÃ¼rs Frontend liefert, mit denen sich ein einzelner Editor kÃ¼nftig mit zwei Hooks und zwei Komponenten ausstatten lÃ¤sst.

Adressiert: alle Nutzer der PC-OberflÃ¤che, die DatensÃ¤tze bearbeiten â€” vor allem Innendienst-Mitarbeiter, die denselben Datensatz potenziell gleichzeitig mit Kollegen Ã¶ffnen.

Dieses Teilprojekt liefert nur das Fundament. Die zwei bestehenden Sperr-Verbraucher (Dokument-Editor, Lieferant-Dokument-Modal) werden darauf umgestellt und dienen als Nachweis, dass es funktioniert. Die Ã¼brigen rund 22 noch ungeschÃ¼tzten Editoren werden hier **nicht** umgebaut.

## Nicht-Ziele

- Das Ausrollen der Sperr-OberflÃ¤che auf die restlichen ~22 Editoren (Projekt, Anfrage, Kunde, â€¦) â€” eigenes Teilprojekt mit eigener Spec.
- Der Layout-/Breiten-Umbau bei kleinen Bildschirmen in Projekt-/Anfrage-Editor â€” unabhÃ¤ngiges Teilprojekt.
- Ein Rollen-/Berechtigungssystem ("wer darf was") â€” existiert im Frontend nicht und wird hier nicht eingefÃ¼hrt.
- Versionsnummer auf Kind-DatensÃ¤tzen (Positionen, BlÃ¶cke) â€” die Versionsnummer des Ã¼bergeordneten Datensatzes reicht.
- Versionsnummer auf allen 149 Datenbank-Klassen â€” nur auf den tatsÃ¤chlich von Editoren geschriebenen Haupt-DatensÃ¤tzen (~14 StÃ¼ck).
- Eine Ãœbergangs-Route fÃ¼r die alte Sperr-Schnittstelle â€” es gibt keine externen Nutzer davon, das Frontend wird komplett umgestellt.
- Eine rÃ¼ckwÃ¤rtskompatible DatenÃ¼bernahme der alten Sperr-Tabelle â€” Sperren sind ohnehin nur kurzlebig, die Tabelle wird neu angelegt statt umbenannt.

## Wichtigste Architektur-Entscheidungen

**Sperr-Verallgemeinerung mit AufzÃ¤hlung `SperrbarerTyp`**
Die bisherige lose Liste erlaubter Dokumenttypen wird durch eine feste AufzÃ¤hlung `SperrbarerTyp` ersetzt. Ein neuer sperrbarer Editor-Typ kostet danach nur noch einen neuen Eintrag in dieser AufzÃ¤hlung â€” kein Backend-Umbau mehr. Das ist der Hebel, der das spÃ¤tere Ausrollen auf die Ã¼brigen Editoren parallelisierbar macht. Neue, allgemeine Schnittstellen-Route: `/api/datensatz-locks/{typ}/{id}`.

**Versionsnummer-Netz (`@Version`, optimistisches Sperren)**
Rund 14 Haupt-Datensatz-Klassen (Projekt, Anfrage, Kunde, Lieferant, Artikel, Mitarbeiter, Bestellung, u.a.) bekommen eine Versionsnummer-Spalte. Beim Speichern wird die beim Laden gelesene Version mitgeschickt. Weicht sie ab, hat inzwischen jemand anderes gespeichert â€” die Anfrage wird mit Fehlercode 409 abgelehnt, und die OberflÃ¤che zeigt in Handwerker-Sprache: "Jemand anders hat diesen Datensatz gerade gespeichert. Deine Ã„nderungen wurden nicht Ã¼bernommen â€” bitte neu laden." Das ist der einzige Schutz fÃ¼r die Mobile-App `react-zeiterfassung`, die dieselben Daten schreibt, aber nie eine Sperr-OberflÃ¤che bekommt.

**InaktivitÃ¤ts-Timer: 5 Minuten mit 60-Sekunden-Vorwarnung**
Nach 5 Minuten ohne jede AktivitÃ¤t im Tab (Mausbewegung, Tastatur, Scrollen, Klick, Tab-Wechsel) wird automatisch gespeichert und die Sperre freigegeben. 60 Sekunden vor Ablauf erscheint ein Countdown-Hinweis ("Wird in 60 Sekunden freigegeben â€” bewege die Maus, um weiterzuarbeiten"), den jede Aktion sofort abbricht. Der Timer wird auf hÃ¶chstens einmal pro Sekunde gedrosselt, damit nicht jede Mausbewegung eine Neuberechnung der OberflÃ¤che auslÃ¶st.

**Neuer X-Button-Ablauf im Dokument-Editor**
Bisher hÃ¤ngt die Freigabe der Sperre ausschlieÃŸlich am SchlieÃŸen der Komponente â€” schlieÃŸt der Tab nicht (wie im beschriebenen MacBook-Fall), bleibt die Sperre dauerhaft aktiv. Neue, feste Reihenfolge beim Klick auf den X-Button: (1) bestehende Warnung bei ungespeicherten Ã„nderungen, (2) speichern, (3) Sperre aktiv freigeben, (4) Tab schlieÃŸen versuchen, (5) existiert der Tab nach ~150ms noch: Hinweisseite "Dokument gespeichert und freigegeben â€” du kannst diesen Tab jetzt schlieÃŸen". Die bisherige harte Blockierung beim Ã–ffnen eines bereits gesperrten Dokuments wird durch einen Nur-Lesen-Modus mit ruhigem Hinweis ersetzt ("Anna Musterfrau bearbeitet gerade â€” du siehst den aktuellen Stand").

## Betroffene Bereiche

**Backend** (`src/main/java/org/example/kalkulationsprogramm/`): neue Datenbank-Migrationen fÃ¼r die neue Sperr-Tabelle und die Versionsnummer-Spalten; Umbenennung/Verallgemeinerung der bestehenden Sperr-Klassen (Service, Controller, Domain, DTO, Repository); neue AufzÃ¤hlung `SperrbarerTyp`; neuer Fehler-Handler fÃ¼r Versionskonflikte (HTTP 409).

**Frontend PC** (`react-pc-frontend/src/`): fÃ¼nf neue Bausteine â€” zwei Hooks (InaktivitÃ¤ts-Erkennung, verallgemeinerte Sperr-Logik) und Komponenten fÃ¼r Hinweiszeile bei fremder Bearbeitung, Bearbeiten-Leiste mit Countdown, und Konfliktmeldung bei 409. Umbau der zwei bestehenden Sperr-Verbraucher (Dokument-Editor-Seite, Dokument-Editor-Komponente, Lieferant-Dokument-Modal) auf das neue Fundament. Entfernen eines doppelten, bisher nie gestoppten zweiten Sperr-Pings in der Dokument-Editor-Komponente.

**Frontend Mobile** (`react-zeiterfassung/`): keine Ã„nderungen â€” wird ausschlieÃŸlich indirekt Ã¼ber die Versionsnummer abgesichert.

**Tests**: neue Tests fÃ¼r beide Hooks, Erweiterung des bestehenden Sperr-Service-Tests um die neuen Typen, je ein Integrationstest fÃ¼r den 409-Konfliktfall, Anpassung eines bestehenden Tests an den entfernten doppelten Sperr-Ping. Testdaten ausschlieÃŸlich mit Dummy-Namen (DSGVO).

## VollstÃ¤ndige Spec

Die vollstÃ¤ndige Spec liegt lokal unter `docs/superpowers/specs/2026-09-03-datensatz-sperren-fundament.md`. Dieser Pfad ist gitignored und daher nicht Ã¼ber das Repository einsehbar â€” der Inhalt ist oben sinngemÃ¤ÃŸ zusammengefasst.
