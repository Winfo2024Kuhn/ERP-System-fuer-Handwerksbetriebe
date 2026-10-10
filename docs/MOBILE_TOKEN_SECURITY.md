# Mobile Token-Sicherheit und Domain-Betrieb

Diese Anleitung beschreibt Token- und Objektberechtigungen der mobilen Zeiterfassung. Das [Docker-Paket für den öffentlichen Zugang](../deployment/mobile-public/README.md) stellt einen eingeschränkten HTTPS-Eingang mit Upload- und Anfragelimits bereit: zunächst mit temporärer Testadresse, später unter einer eigenen Domain. Die konkrete Windows-Installation und Domain müssen vor dem Einsatz eingerichtet und von außen geprüft werden. Das gesamte Desktop-ERP wird dadurch nicht öffentlich freigegeben.

## Anmeldung und Rechte

Die mobile Oberfläche unter `/zeiterfassung` bleibt ohne Anmeldung ladbar. Die API-Bereiche der mobilen Spring-Security-Filterkette verlangen einen Token eines aktiven Mitarbeiters oder eine bestehende Desktop-Anmeldung.

- Die PWA sendet ihren gespeicherten Token im Header `X-Auth-Token` an API-Aufrufe derselben Herkunft. Der Anmeldeaufruf verwendet weiterhin den Token im Pfad. Unterstützte bisherige Pfad- und `token`-Parameter bleiben verwendbar; widersprüchliche Token-Angaben werden zurückgewiesen.
- Ein gültiger mobiler Token erlaubt nur die in `MobileApiPolicy` ausdrücklich aufgeführten Kombinationen aus HTTP-Methode und Route. `HEAD` wird dabei wie `GET` geprüft. Eine erlaubte Route gewährt keine pauschalen Verwaltungsrechte auf alle Pfade darunter.
- Die Mitarbeiteridentität wird aus dem geprüften Token abgeleitet. Abweichende Mitarbeiterangaben in den geprüften Headern, Parametern und JSON-Feldern sowie ein `X-User-Profile-Id`-Header werden abgewiesen.
- Das Cookie `ze_token` kann ausschließlich lesende `GET`-/`HEAD`-Aufrufe authentifizieren, etwa Bilder und Vorschauen. Für mobile Schreibzugriffe ist ein expliziter Token nötig. Geprüfte explizite Token-Aufrufe sind von CSRF ausgenommen; Desktop-Sitzungen behalten ihren CSRF-Schutz.
- Ein zusätzlich explizit mitgesendeter mobiler Token wird auch bei vorhandener Desktop-Anmeldung auf mobile Rechte begrenzt. Ohne expliziten mobilen Token bleibt die Desktop-Sitzung maßgeblich.

**Objektberechtigungen werden zusätzlich zur Routenliste geprüft:**

- Projekt-/Anfrage-Dateien sind mobil ausschließlich als zugeordnete `BILDER` freigegeben; Geschäftsdokumente bleiben ausgeschlossen. Keine Originalnamen- oder Speicher-Fallbacks für mobile Aufrufe.
- Notizbilder benötigen eine mobil sichtbare Notiz; private Notizen bleiben ihrem Ersteller vorbehalten. Notizänderungen prüfen zusätzlich den Ersteller und den zugehörigen Projekt-/Anfragepfad.
- Lieferantendownloads prüfen die vorhandenen Abteilungsrechte für den konkreten Dokumenttyp, Import/Analyse die Scanrechte. Lieferantenbilder sind mobil nur im zugehörigen Reklamationskontext zugänglich.
- Uploads aus der App: Projekt-/Anfrage- und Reklamationsbilder nur als JPEG, PNG, GIF, WebP oder HEIC/HEIF, Lieferschein-Scans zusätzlich als PDF. Die Dateiendung der Reklamationsbilder kommt aus dem geprüften Typ.
- Auslieferung (auch am Desktop): Nur Fotos und PDFs werden im Browser angezeigt. HTML, SVG, XML und JavaScript kommen als Download mit neutralem Typ und `nosniff` – eine hochgeladene Datei kann so kein Skript im Ursprung des ERP ausführen.
- Lieferanten-Details liefert der Mobile-Token nur als Stammdaten (Name), ohne Preise, E-Mails und Notizen.
- Die mobile Belegerfassung erlaubt Lesen und Positionsänderungen nur an eigenen Uploads, zusätzlich zu den vorhandenen BELEG-Rechten. Nicht zugeordnete oder fremde Belege werden abgewiesen. Freigegebene Lieferantendokumente unterliegen weiterhin ihren gesonderten Abteilungsrechten.

Diese Regeln bilden die vorhandene mobile Fachberechtigung ab: Projekt-/Anfrage-Bilder sind teamweit vorgesehen; eine bisher nicht vorhandene Mitarbeiter-Projekt-Zuordnung wird nicht erfunden. Neue Routen benötigen weiterhin passende Objektprüfungen.

Nach dem Update die PWA neu laden, damit der neue Token-Transport und Service Worker aktiv sind. Der Worker entfernt die bisherigen pauschalen API-/Bilder-Caches und lädt geschützte API-Antworten nur über das Netz. Private Bilder und Dokumente benötigen damit eine Verbindung zur aktuellen Berechtigungsprüfung. Die explizite Offline-Zeiterfassung bleibt erhalten; schon heruntergeladene, exportierte oder anderweitig kopierte Daten können nicht rückwirkend entzogen werden. Bestehende QR-Codes und Tokens werden durch dieses Update nicht ersetzt. Es führt weder ein neues Ablaufdatum noch eine einmalige QR-Aktivierung ein. Ein entwendeter gültiger Token bleibt ein Zugangsmittel, solange er nicht ersetzt oder der Mitarbeiter deaktiviert wird. Browser und Offline-Abläufe verwenden weiterhin den vorhandenen Token; ein geänderter oder deaktivierter Token kann später keine ausstehenden Buchungen mehr authentifizieren.

## Fehlversuche und Wartezeiten

`TokenAttemptLimiter` zählt ungültige Token-Versuche gemeinsam **pro ermittelter Client-IP**, unabhängig vom verwendeten Token. Ein fehlender Token führt zu `401`, ohne diesen Zähler zu erhöhen. Ein vorhandener, aber ungültiger, widersprüchlicher oder nicht mehr aktiver Token zählt als Fehlversuch.

| Geprüfter Fehlversuch derselben IP | Antwort | Wartezeit |
| --- | --- | --- |
| 1 und 2 | `401 Unauthorized` | Keine |
| 3 | `429 Too Many Requests` | 2 Sekunden |
| 4 bis 10 | `429 Too Many Requests` | 4, 8, 16, 32, 64, 128, 256 Sekunden |
| Ab 11 | `429 Too Many Requests` | Höchstens 300 Sekunden |

Während einer Sperre antwortet der Server sofort mit `429`, `Retry-After` in Sekunden und `retryAfterSeconds` im JSON. Er wartet nicht mit einem schlafenden Server-Thread. Weitere Aufrufe während dieser Zeit prüfen den Token nicht erneut und verlängern die Sperre nicht. Auch ein gültiger Token kann deshalb vorübergehend `429` erhalten. Die Anmeldemaske zeigt die verbleibende Wartezeit an; maßgeblich bleibt der Server.

Hintergrundabfragen des Service Workers beachten ebenfalls `Retry-After`. Nach `401` oder `403` verwenden sie den abgelehnten Token nicht automatisch weiter; dieser Zustand übersteht Worker-Neustarts. Eine erfolgreiche erneute QR-/manuelle Anmeldung mit demselben Token hebt diese Hintergrund-Ablehnung auf, etwa nach Reaktivierung eines Mitarbeiters. Eine noch laufende Wartezeit bleibt bestehen. Das betrifft die Kalenderabfragen für Erinnerungen; die Anzeige empfangener Web-Push-Nachrichten ist davon unabhängig.

Ein gültiger Token setzt den Fehlversuchszähler nicht zurück. Nach 30 Minuten ohne weiteren tatsächlich geprüften Fehlversuch wird der Zähler zurückgesetzt. Parallele Versuche derselben IP werden nacheinander geprüft, damit sie eine Sperre nicht gemeinsam umgehen.

Der Zustand liegt nur im Speicher des jeweiligen Serverprozesses und ist auf **10.000 Einträge** begrenzt. Einträge entstehen nur durch Fehlversuche – erfolgreiche Anmeldungen belegen keinen Platz. IPv6-Adressen zählen je /64-Netz. Bei voller Kapazität werden zuerst seit mindestens 30 Minuten unbenutzte, dann nicht gesperrte Einträge bereinigt. Bleibt die Tabelle voll, erhalten neue IPs `429` mit 300 Sekunden; laufende Sperren werden nie verdrängt.

Angemeldete Büro-Nutzer (Desktop-Sitzung) zählen nicht als Fehlversuch, wenn sie einen Token im Pfad abfragen, der nicht (mehr) gilt. Sonst könnte das Büro die Wartezeit für das ganze Firmennetz auslösen. Der Zeiterfassungs-Kalender im Büro fragt den Saldo inzwischen ohnehin über die Mitarbeiter-ID ab (`/api/zeitverwaltung/mitarbeiter/{id}/saldo`); Anmelde-Codes anderer Mitarbeiter sehen nur Admins. Erhält die App beim Start `429`, bleibt sie angemeldet, behält offene Buchungen und gleicht nach Ablauf der Wartezeit erneut ab. Ein Neustart verliert den Zustand, mehrere Instanzen teilen ihn nicht. Das Docker-Gateway begrenzt zusätzlich die Anfragerate vor dem Backend, persistiert den exponentiellen Fehlerzähler aber ebenfalls nicht. Für mehrere Instanzen, verteilte Angriffe und Limits über Neustarts hinweg ist ein zusätzlicher gemeinsamer oder vorgeschalteter Limiter nötig. Dies ist kein allgemeines Mengenlimit für erfolgreiche API-Aufrufe.

Mehrere Geräte hinter demselben Firmenrouter oder Mobilfunk-NAT können dieselbe IP und somit dieselbe Wartezeit teilen. Wird hinter einem Proxy keine echte Client-IP ermittelt, können alle Nutzer als eine einzige Proxy-IP erscheinen.

## Vertrauenswürdige Proxys konfigurieren

Proxys auf demselben Rechner (`127.0.0.0/8`, `::1`) gelten immer als freigegeben. Dazu gehören `tailscale serve`, Tailscale Funnel und cloudflared: Tailscale setzt `X-Forwarded-For` selbst auf den echten Absender; bei cloudflared hängt ihn die Cloudflare-Edge an. Ein lokaler Proxy muss `X-Forwarded-For` deshalb selbst setzen oder korrekt ergänzen. Meldet er den Absender zusätzlich in `X-Real-IP`, `CF-Connecting-IP` oder `True-Client-IP`, muss dieser zur Kette passen; sonst gilt die Anfrage als extern, und die Code-Sperre zählt den direkten Partner. Reine TCP-Weiterleitungen auf `localhost` (`tailscale funnel --tcp`, `netsh interface portproxy`, `ssh -R`) liefern gar keinen Absender – damit gälte jeder Internet-Zugriff als lokal. Sie dürfen nicht verwendet werden. Für alle anderen Verbindungspartner verwendet `ClientIpResolver` ohne Konfiguration ausschließlich die direkte Verbindungsadresse (`getRemoteAddr()`); vom Client gesetzte Weiterleitungs-Header ändern diese Adresse nicht.

Ein Proxy auf einem **anderen** Rechner wird über die Property `zeiterfassung.security.trusted-proxies` freigegeben, beispielsweise in `application-local.properties` bei aktivem Profil `local`. Sie enthält eine kommaseparierte Liste tatsächlicher Proxy-IP-Adressen oder CIDRs. Sie gilt für die Token-Sperre und die zusätzliche Netzgrenze in `ZeiterfassungSecurityFilter`. Beispiel mit einer zu ersetzenden Adresse:

```properties
zeiterfassung.security.trusted-proxies=192.0.2.10/32
```

Verwende möglichst einzelne IPv4-Adressen mit `/32` beziehungsweise IPv6-Adressen mit `/128`. Ganze private Netze oder `0.0.0.0/0` und `::/0` sind keine geeignete Freigabe: Dann könnten andere Absender eine Client-IP vortäuschen.

Nur wenn der direkte Verbindungspartner vertrauenswürdig ist, wird `X-Forwarded-For` ausgewertet, über alle Kopfzeilen hinweg. Die Kette wird von rechts nach links gelesen, solange der jeweils aktuelle Hop vertrauenswürdig ist; die erste nicht vertrauenswürdige Adresse ist die Client-IP. **`CF-Connecting-IP`, `X-Real-IP` und `True-Client-IP` werden nicht als Absender übernommen**, sondern nur gegen die Kette abgeglichen; ein `Forwarded`-Header macht die Anfrage zu extern. Leere Glieder in der Kette (`10.0.0.1,,`) machen sie ungültig. Fail-closed gilt: Trägt eine Anfrage einen Weiterleitungs-Header (`X-Forwarded-For`, `Forwarded`, `CF-Connecting-IP`, `X-Real-IP`, `True-Client-IP`), ohne dass sich aus einer freigegebenen `X-Forwarded-For`-Kette ein Absender ergibt, behandelt `ZeiterfassungSecurityFilter` sie als extern – etwa bei einem nicht freigegebenen Proxy, einer unbrauchbaren Kette, einem `Forwarded`-Header, abweichenden Einzeladressen oder einem Tunnel, der nur `CF-Connecting-IP` setzt. Anfragen mit dem von Tailscale gesetzten `Tailscale-Funnel-Request` gelten immer als extern. Ein solcher Header kann Rechte nur einschränken. Die Netzgrenze läuft vor Spring Security, damit sie auch die Anmeldung (`POST /api/auth/login`) erfasst.

Tailscale-Adressen (`100.64.0.0/10` und IPv6 `fd7a:115c:a1e0::/48` innerhalb von `fc00::/7`) zählen wie das Firmen-LAN als lokal. Über `tailscale serve` erreichen Büro-PCs, Handys mit Tailscale-App und ein per Tailscale angebundener Website-Server (Anfrage-Funnel, Angebots-Freigabe unter `/api/internal/**`) das ERP deshalb wie bisher.

**Von außen gilt überall dieselbe Regel wie am Gateway:** Erreichbar sind nur `GET`/`HEAD` auf `/zeiterfassung/**` und die Aufrufe aus `MobileApiPolicy`; alles andere erhält `403`, mehrdeutige Pfade (`..`, `;`, `%2e`, `%2f`, `%5c`, `%00`) `400`, ein Aufruf von `/` führt zu `/zeiterfassung/`. Desktop-Sitzungen zählen von außen nicht, es gilt nur der Mitarbeiter-Code. Anfragen von außen dürfen höchstens 25 MiB groß sein (`413`) und müssen ihre Länge angeben (`411` bei `Transfer-Encoding` ohne `Content-Length`), weil der Server Multipart-Daten sonst schon vor der Code-Prüfung annimmt. Das gilt unabhängig davon, ob die Anfrage direkt, über Tailscale Funnel, einen Tunnel oder das Docker-Gateway kommt.

Der öffentlich erreichbare Proxy muss vom Client angelieferte Weiterleitungs-Header überschreiben und aus der tatsächlichen Verbindung eine geprüfte Kette erstellen. Bei mehreren Proxys muss jede weitere Station ausschließlich eine bereits geprüfte Kette übernehmen und korrekt ergänzen. Die Anwendung darf über Firewall und Netzwerkbindung nur von den vorgesehenen Proxys erreichbar sein; Port 8080 darf nicht zusätzlich öffentlich offenstehen. Auch Container-Portfreigaben gehören zu dieser Prüfung. Vorgelagerte Container-/Framework-Filter dürfen `getRemoteAddr()` nicht unkontrolliert anhand fremder Header umschreiben und dadurch die Vertrauensprüfung umgehen.

## Vor einer öffentlichen Domain ohne VPN

HTTPS über einen Reverse Proxy oder Tunnel ist Teil des späteren Betriebs. Eine Konfiguration, die lediglich die gesamte Domain auf Port 8080 weiterleitet, reicht für die Freigabe nicht aus.

1. **Ersteinrichtung abschließen:** Admin-Zugang im geschützten Netz einrichten und kontrollieren, dass die offene Erstregistrierung beendet ist. Für den Desktop gelten Benutzername/Passwort, Sitzung und CSRF; HTTP Basic ist deaktiviert.
2. **HTTPS und Backend-Zugang einrichten:** Zertifikate und deren Erneuerung konfigurieren, HTTP auf HTTPS umleiten und direkte öffentliche Backend-Verbindungen sperren. Den gesamten Weg vom Browser bis zum Backend entsprechend dem tatsächlichen Netzwerk absichern.
3. **Öffentliche Routen festlegen:** Nur die benötigten mobilen Seiten, statischen Ressourcen und API-Methoden am Proxy freigeben; Desktop-Verwaltung und interne APIs benötigen eigene, ausdrücklich geprüfte Zugangsregeln. Alle öffentlich erreichbaren sensiblen Endpunkte benötigen passende Authentifizierung und Berechtigungen.
4. **Routenliste prüfen:** `MobileApiPolicy` ist die einzige Liste dessen, was von außen erreichbar ist. Netzgrenze, Token-Prüfung und Gateway (`mobile-routes.conf`, erzeugt mit `sync_routes.py`) richten sich danach. Neue mobile Funktionen müssen dort eingetragen werden, sonst sind sie von außen gesperrt. Die Property `zeiterfassung.security.enabled=false` deaktiviert nur diese Netzgrenze, nicht die Spring-Security-Anmeldung; sie ist keine Lösung für ungeprüfte öffentliche Freigaben.
5. **Fachliche Freigaben kontrollieren:** Die implementierten Objektprüfungen mit mehreren Testmitarbeitern über den tatsächlichen öffentlichen Eingang prüfen. Vorhandene Abteilungsrechte, BILDER-Gruppen und mobile Notizfreigaben bestimmen, welche Inhalte öffentlich angemeldete Mitarbeiter sehen dürfen.
6. **Proxy und Limits prüfen:** Vertrauenswürdige Proxys eng konfigurieren, gefälschte `X-Forwarded-For`-Header testen und beobachten, ob unterschiedliche externe Geräte korrekt unterschieden werden. Bei mehreren Instanzen einen gemeinsamen oder vorgeschalteten Limiter einrichten.
7. **Uploads und Betrieb absichern:** Netzgrenze und mitgeliefertes Gateway begrenzen öffentliche Upload-Anfragen auf 25 MiB. Mit den benötigten Bild-/Belegformaten testen. Das bestehende globale Multipart-Limit von 15 GB gilt damit nur im lokalen Netz; der 1-MB-JSON-Deckel im Token-Filter begrenzt Multipart-Dateien nicht. Updates, Backups und Wiederherstellung vorsehen. Tokens aus Proxy-/Anwendungslogs, Fehlerberichten und Analysewerkzeugen fernhalten beziehungsweise redigieren; insbesondere bisherige Token-Pfade und Query-Parameter können sonst in Zugriffslogs landen. Gültige Tokens nicht in öffentliche Tickets oder Screenshots übernehmen.

## Funktionsprüfung und Fehlersuche

Die Freigabeprüfung erfolgt in einer Testumgebung mit Dummy-Mitarbeitern über denselben Proxy-Weg wie später im Betrieb:

- Ohne Anmeldung dürfen geschützte mobile API-Aufrufe keine Daten liefern; die mobile Startseite muss dennoch laden.
- Ein gültiger aktiver Token erreicht die vorgesehenen mobilen Funktionen. Ein deaktivierter oder ersetzter Token wird abgewiesen. Fremde Identitätsangaben und nicht erlaubte Methoden dürfen keine Änderungen auslösen.
- Drei ungültige Token-Versuche von derselben IP lösen 2 Sekunden Wartezeit aus; der nächste nach Ablauf geprüfte Fehlversuch löst 4 Sekunden aus. Eine andere Token-Zeichenfolge oder ein gefälschter Weiterleitungs-Header darf die IP-Sperre nicht aufheben.
- Ein Cookie allein darf keine mobile Schreibaktion autorisieren. Desktop-Schreibaktionen müssen weiterhin ihren CSRF-Token benötigen.
- QR-Anmeldung, Wiederaufnahme nach Browser-Neustart, Bilder/Vorschauen und Synchronisierung vorhandener Offline-Buchungen mit gültigem Token prüfen.

`401` bedeutet fehlende oder ungültige Anmeldung. `429` verlangt Warten gemäß `Retry-After`; bei vielen betroffenen Nutzern zusätzlich Proxy-IP-Ermittlung und Speicherkapazität prüfen. `403` kann eine nicht erlaubte Methode, fremde Identitätsangaben, CSRF oder die Netzgrenze (Aufruf von außen außerhalb der mobilen Routen) bedeuten. Die Sperre durch Server-Neustarts zu löschen ist keine dauerhafte Behebung.

## Quellen im Repository

- [Entwurf und vereinbarter Umfang](superpowers/specs/2026-10-09-mobile-token-security-design.md)
- [Token-Prüfung](../src/main/java/org/example/kalkulationsprogramm/config/MobileTokenAuthenticationFilter.java)
- [Erlaubte mobile Methoden und Routen](../src/main/java/org/example/kalkulationsprogramm/config/MobileApiPolicy.java)
- [Wartezeiten und Speicherkapazität](../src/main/java/org/example/kalkulationsprogramm/config/TokenAttemptLimiter.java)
- [Client-IP-Ermittlung](../src/main/java/org/example/kalkulationsprogramm/config/ClientIpResolver.java)
- [Spring-Security-Filterketten](../src/main/java/org/example/kalkulationsprogramm/config/SecurityConfig.java) und [zusätzliche Netzgrenze](../src/main/java/org/example/kalkulationsprogramm/config/ZeiterfassungSecurityFilter.java)

## Öffentlicher Gateway-Vertrag

Das Gateway setzt `X-ERP-Public-Mobile: 1` selbst und entfernt Desktop-Sitzungscookies sowie `Authorization`. Die Anwendung beschränkt markierte Anfragen vor allen Security-Ketten auf mobile Routen und übernimmt auch bei vorhandenem Desktop-Kontext keine Desktop-Rechte. Der Marker kann Berechtigungen nur einschränken. Der Response-Header gleichen Namens bestätigt diese zusätzliche Schicht für den Startskript-Preflight. Der private Backend-Port darf nicht am Gateway vorbei öffentlich erreichbar sein.

Mobile API-Antworten erhalten eine Sandbox-Content-Security-Policy, damit hochgeladene aktive Inhalte keine Skripte im Ursprung der PWA ausführen können. Bei Dateirechten wird vor dem Vorschaubildcache geprüft; mobile Vorschauen verwenden eigene Speicherschlüssel und `no-store`.
