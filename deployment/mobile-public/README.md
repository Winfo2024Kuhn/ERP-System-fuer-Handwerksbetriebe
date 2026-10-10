# Öffentlicher mobiler Zugang unter Windows und Docker

Dieses Paket stellt die vorhandene mobile PWA über HTTPS bereit. Die Desktop-Oberfläche, Desktop-Anmeldung und nicht freigegebene API-Methoden bleiben am öffentlichen Zugang gesperrt. Es setzt die Anwendungsversion mit `X-ERP-Public-Mobile`-Schutz und den zugehörigen Objektberechtigungen voraus. Es wurde mit einem isolierten Test-Backend geprüft; eine Domain, ein Windows-Produktivserver oder ein öffentlicher Tunnel werden durch das Herunterladen dieses Pakets nicht eingerichtet.

Zwei Wege stehen bereit:

- **Test ohne eigene Domain:** Ein Cloudflare Quick Tunnel erzeugt beim Start eine zufällige `trycloudflare.com`-HTTPS-Adresse. Dieser Weg ist für zeitlich begrenzte Tests gedacht.
- **Dauerhafter Betrieb:** Caddy nimmt HTTPS auf einer eigenen festen Domain entgegen und verwaltet die Zertifikate. DNS sowie die Erreichbarkeit von Port 80/443 müssen dafür eingerichtet sein.

Vercel ist für diese bestehende PWA nicht erforderlich. Die Anwendung und ihre Dateien bleiben auf dem vorhandenen ERP-Rechner.

## Aufbau und feste Vertrauensgrenzen

```text
Feste Domain → Caddy (.50.2) → Nginx:8081 ─┐
                                         ├→ Nginx (.51.2) → ERP app:8080 (.51.3)
Testadresse → cloudflared (.50.4) → :8082 ─┘
```

Die Kurzadressen stehen für `172.30.50.x` beziehungsweise `172.30.51.x`.

| Netz / Verbindung | Zweck |
| --- | --- |
| `172.30.50.0/29`, internes Compose-Netz | Nur Caddy, Tunnel und Gateway. Beide Gateway-Ports bleiben unveröffentlicht. |
| `erp-mobile-backend`, `172.30.51.0/29`, intern | Gateway `.2` erreicht ERP `.3` unter dem Alias `app`. |
| Separates Internet-Netz | Nur Caddy für Zertifikate beziehungsweise cloudflared für den Tunnel. |
| ERP-Port 8080 auf dem Windows-Rechner | Standardmäßig nur `127.0.0.1`; optional eine konkrete LAN-/Tailscale-Adresse. |
| Datenbank / Qdrant | Hostports ausschließlich an `127.0.0.1`. |

Der ERP-Server vertraut für `X-Forwarded-For` **ausschließlich `172.30.51.2/32`**. `server.forward-headers-strategy=none` erhält die unveränderte direkte Verbindungsadresse für diese Prüfung. Keine pauschale Freigabe privater Netze hinzufügen. Wenn die beiden Subnetze bereits anderweitig benutzt werden, zuerst einen anderen konsistenten Adressplan festlegen; vorhandene Netze werden nicht automatisch verändert.

Caddy überschreibt fremde Forwarding-Header mit der tatsächlichen Client-Verbindung. Der separate Tunnel-Eingang akzeptiert nur `.50.4` und übernimmt dort das von Cloudflare gesetzte `CF-Connecting-IP`; danach erhält die Anwendung lediglich ein neu gesetztes `X-Forwarded-For`. Das Backend wertet `CF-Connecting-IP` selbst weiterhin nicht aus. Diese Trennung folgt den [Cloudflare-Headerregeln](https://developers.cloudflare.com/fundamentals/reference/http-headers/) und der [Nginx-Vertrauenskonfiguration](https://nginx.org/en/docs/http/ngx_http_realip_module.html).

Nginx überschreibt immer `X-ERP-Public-Mobile: 1`. Es entfernt Desktop-Sitzungscookies, `Authorization` und `X-XSRF-TOKEN`; nur ein gültig aufgebautes `ze_token`-Cookie wird weitergereicht. `Set-Cookie` aus dem Backend wird am öffentlichen Zugang entfernt. Der Anwendungsschutz muss unabhängig davon Desktop-Sitzungen bei diesem Marker ignorieren.

## Voraussetzungen

- Windows mit Docker Desktop und **Linux-Containern**, PowerShell 5.1 oder neuer.
- Docker Compose **ab 2.24.4**. Das Overlay nutzt `!override`, damit alte öffentliche Portbindungen ersetzt werden und nicht zusätzlich erhalten bleiben. [Docker-Referenz](https://docs.docker.com/reference/compose-file/merge/#replace-value)
- Die neue, geprüfte ERP-Version als Docker-Image und ein gesichertes Datenbank-Backup. Die Startskripte bauen die Java-Anwendung nicht automatisch.
- Der bisherige Compose-Projektname und die bisherigen Daten-Volumes bleiben erhalten. Bei einem bestehenden Standardcontainer erkennt das Skript den Projektnamen aus dessen Label. Bei einer neuen Installation muss `-ErpProjectName` ausdrücklich angegeben werden.
- Keine zusätzliche Router-Portweiterleitung auf 8080, 3307, 6333 oder 6334. Windows-Firewall und Docker-Portbindungen gemeinsam prüfen.

## Einmalig vorbereiten

PowerShell im Verzeichnis dieses Pakets öffnen:

```powershell
Copy-Item .env.example .env
notepad .env
```

Eigene Werte einsetzen. Bei einer vorhandenen Datenbank **deren vorhandene Zugangsdaten übernehmen**: Ein geändertes Passwort in `.env` ändert kein Passwort in der Datenbank. Das Skript lehnt leere, kurze und erkennbare Beispielkennwörter ab, ohne ihre Werte auszugeben. `.env` bleibt lokal und darf nicht committed oder in Fehlermeldungen kopiert werden. Bei einer bestehenden Installation dürfen `APP_ADMIN_USER` und `APP_ADMIN_PASS` beide leer bleiben: Diese Variablen ändern vorhandene Benutzer nicht. Bei einer bewusst neuen Installation verlangt das Skript beide Werte. Ein Bootstrap-Admin wird nur angelegt, solange noch kein Login-Benutzer existiert; danach die Ersteinrichtung privat abschließen. Vor dem Öffnen muss der private Bootstrap-Status `hasLoginUsers=true` und `setupRequired=false` bestätigen.

`ERP_PRIVATE_BIND_IP=127.0.0.1` erlaubt Desktop-Zugriff nur auf dem Server selbst. Für andere Bürorechner kann hier bewusst die konkrete LAN- oder Tailscale-IPv4-Adresse des ERP-Rechners stehen; die Firewall darf diesen Port nur für die vorgesehenen privaten Geräte zulassen. Keine öffentliche Adresse und kein `0.0.0.0` verwenden.

Bei manuellen Compose-Befehlen immer dieselben Dateien, denselben Projektnamen und dieselbe `.env` wie beim Startskript verwenden. Das bestehende ERP-Compose ohne Overlay würde wieder seine bisherigen breiten Portbindungen anwenden. Das Skript erstellt bei Bedarf ausschließlich dieses neue isolierte Netz:

```powershell
docker network create --internal --subnet 172.30.51.0/29 erp-mobile-backend
```

Für einen bewusst neu angelegten Compose-Stack muss dessen Anwendungsimage vor dem Start gebaut beziehungsweise durch den bestehenden Update-Prozess bereitgestellt werden. Beispiel aus dem Repository-Stamm; `mein-erp` muss dem tatsächlichen Projektnamen entsprechen:

```powershell
docker compose --env-file deployment/mobile-public/.env --project-name mein-erp -f docker-compose.yml -f deployment/mobile-public/erp.override.yaml build app
```

Der Bau startet keine öffentliche Freigabe. Die bestehende Datenbank nicht durch neue Volumes oder einen anderen Compose-Projektnamen ersetzen.

## Weg 1: Zufällige HTTPS-Adresse für einen Test

```powershell
.\Start-MobilePublic.ps1 -Mode quick -ValidateOnly
.\Start-MobilePublic.ps1 -Mode quick
docker compose --env-file .env -f compose.yaml logs -f tunnel
```

Bei einer neuen Installation ergänzen beide Startbefehle beispielsweise `-ErpProjectName mein-erp`. Das Skript prüft zuerst die Konfiguration, startet ERP und Gateway mit privaten Ports und prüft dann den Public-Mobile-Schutz am Backend. Eine ältere Anwendung, die den Marker nicht einschränkt, verhindert den öffentlichen Start. Das Skript verlangt `404` plus den Antwortheader `X-ERP-Public-Mobile: 1` am gesperrten Bootstrap-Pfad, eine aktive Token-Prüfung und eine privat abgeschlossene Ersteinrichtung. Bei einer neuen Installation die Einrichtung zunächst über den privaten Desktop-Zugang abschließen und den Start danach wiederholen.

Das Tunnelprotokoll zeigt ausschließlich die erzeugte Basisadresse mit `/zeiterfassung/`. Diese Adresse auf dem Handy öffnen und dort den vorhandenen QR-Code scannen beziehungsweise den Mitarbeiter-Token eingeben. Ein alter QR-Link zur privaten Serveradresse wird durch dieses Paket nicht nachträglich umgeschrieben.

Quick Tunnels haben keine Verfügbarkeitsgarantie, höchstens 200 gleichzeitig laufende Anfragen und unterstützen keine Server-Sent Events. Die Adresse ändert sich bei jedem neuen Tunnel. Das sind [Cloudflares dokumentierte Testgrenzen](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/). Für Tests möglichst Dummy-Mitarbeiter verwenden. Bei einem neuen Hostnamen wechseln außerdem Browser-Speicher und Service-Worker-Bereich; ausstehende Offline-Buchungen gehören weiterhin zur bisherigen Adresse und müssen dort vorher synchronisiert werden.

Für diesen Weg werden keine eingehenden Hostports veröffentlicht. Der ausgehende Tunnel muss Cloudflare erreichen können. Die zufällige Adresse ersetzt keine Mitarbeiter-Anmeldung.

## Weg 2: Feste Domain

In `.env` setzen, hier mit einer zu ersetzenden Beispieldomain:

```properties
MOBILE_DOMAIN=zeit.mein-betrieb.de
ZEITERFASSUNG_URL=https://zeit.mein-betrieb.de
```

`ZEITERFASSUNG_URL` wird im Docker-Profil auf `zeiterfassung.base-url` abgebildet. **Nur die HTTPS-Herkunft eintragen, ohne `/zeiterfassung/` und ohne abschließenden Schrägstrich**; `MitarbeiterService` ergänzt beim Erstellen des QR-Codes den Pfad und Token selbst. Für Quick-Tunnel-Tests kann der bisherige Wert erhalten bleiben.

DNS muss auf den erreichbaren Server zeigen; Port 80 und 443 müssen zu Caddy gelangen. Ein veröffentlichter AAAA-Eintrag muss ebenfalls einen funktionierenden Weg haben. Das Paket veröffentlicht standardmäßig IPv4-Hostports. Ein vorgeschalteter CDN-/Proxy-Dienst ist nicht Teil dieser direkten Caddy-Konfiguration; dessen zusätzliche Vertrauensgrenzen müssen gesondert eingerichtet werden.

```powershell
.\Start-MobilePublic.ps1 -Mode domain -ValidateOnly
.\Start-MobilePublic.ps1 -Mode domain
```

Caddy fordert passende Zertifikate an und erneuert sie; die dafür benötigten Daten liegen in den persistenten `caddy_data`-/`caddy_config`-Volumes. Diese Volumes nicht bei Updates löschen. Voraussetzungen und Verhalten beschreibt [Caddys HTTPS-Dokumentation](https://caddyserver.com/docs/automatic-https).

Der Start des Containers bestätigt noch keine von außen funktionierende Domain. Von einem Mobilfunkgerät anschließend `https://DEINE-DOMAIN/zeiterfassung/` öffnen, Zertifikat und QR-Anmeldung prüfen und die untenstehenden Negativprüfungen durchführen.

## Konkrete Schutzregeln

- Die Methoden-/Routenliste in `mobile-routes.conf` wird aus `MobileApiPolicy.java` erzeugt. Nur `GET`/`HEAD` auf `/zeiterfassung` und dessen Ressourcen kommen hinzu; `/` führt zur mobilen Startseite. Desktop-Pfade, Login-Endpunkte und andere Methoden enden am Gateway mit `404`.
- Jeder Request-Body ist auf **25 MiB insgesamt** begrenzt, einschließlich Multipart-Daten. Größere Anfragen erhalten `413`. [Nginx-Bodylimit](https://nginx.org/en/docs/http/ngx_http_core_module.html#client_max_body_size), [Caddy-Bodylimit](https://caddyserver.com/docs/caddyfile/directives/request_body)
- Pro Client-IP: insgesamt 60 Anfragen/s mit zusätzlichem Burst 100; API 10/s mit Burst 30; Token-Anmeldung 1/s mit Burst 5; höchstens 16 gleichzeitig bearbeitete Verbindungen. Überlast erhält `429` und `Retry-After: 1`. Die Anwendung kann längere Token-Wartezeiten zurückgeben. Diese Regeln nutzen [Nginx-Limits](https://nginx.org/en/docs/http/ngx_http_limit_req_module.html); geteilte NAT-Adressen teilen sich die Limits, Neustarts setzen die Proxy-Zähler zurück.
- API-Antworten erhalten `Cache-Control: no-store`, `X-Content-Type-Options: nosniff` und `Content-Security-Policy: sandbox; default-src 'none'`. Die CSP verhindert aktive Inhalte in über API ausgelieferten HTML-/SVG-Dateien, ohne diese Richtlinie auf die PWA-Oberfläche anzuwenden. PDF-Vorschauen müssen mit den tatsächlich eingesetzten Browsern geprüft werden.
- Access- und HTTP-Fehlerlogs der Proxys sind abgeschaltet, weil auch Fehlerlogs Token-Pfade enthalten können. Der Tunnel-Wrapper verwirft alle cloudflared-Ausgaben außer dem bloßen Testhostnamen. Dadurch fehlen ausführliche Laufzeitdiagnosen; Containerstatus, Konfigurationsprüfung und statusbasierte Funktionsprüfungen dienen zur Fehlersuche. Backend-Logs und externe Plattformprotokolle sind davon nicht automatisch bereinigt.

## Prüfen, aktualisieren und stoppen

Entwickler prüfen Routenänderungen mit Python 3:

```powershell
python .\sync_routes.py --check
# Nur bei beabsichtigten API-Aenderungen, danach Diff pruefen:
python .\sync_routes.py
```

Die Tests benötigen Docker und Python 3 und veröffentlichen ausschließlich einen zufälligen lokalen HTTPS-Port; es wird kein öffentlicher Tunnel gestartet. Sie erzeugen einen separaten Dummy-Stack und entfernen anschließend dessen Container, Volumes und Netz. Die festen Paket-Subnetze müssen dafür frei sein:

```powershell
$env:RUN_PROXY_INTEGRATION = '1'
python -m unittest discover -s .\tests
.\tests\test_startup.ps1
```

Die Tests prüfen echten Caddy-/Nginx-Verkehr einschließlich Header-Manipulationen, Sessiontrennung, CSP, Methode/Pfad, Uploadgrenze, Rate Limit und Log-Ausgaben. Die PowerShell-Prüfung verwendet einen gemockten Docker-Befehl; sie ändert keinen laufenden Stack. Die separat geprüften Objektberechtigungen der Java-Anwendung werden durch den Dummy-Backend-Test nicht ersetzt.

Nach der Einrichtung von außen prüfen: Mobile Startseite erreichbar; `/login`, `/api/auth/me` und `DELETE /api/projekte/1` gesperrt; geschützte API ohne Token abgewiesen; fremde Dokument-/Mitarbeiter-IDs auch mit gültigem Token gesperrt; Bilder und PDFs nur im vorgesehenen Umfang verfügbar. Nicht mit echten Tokens in Shell-History oder öffentlichen Testdiensten arbeiten.

Zum Stoppen nur des öffentlichen Zugangs:

```powershell
.\Stop-MobilePublic.ps1
```

ERP und Datenbank bleiben privat weiter in Betrieb. Für Updates dieselben Compose-Dateien und Image-Digests verwenden. Neue Proxy-Versionen ausdrücklich aktualisieren, die Tests wiederholen und die Digests in `compose.yaml` beziehungsweise `tunnel/Dockerfile` gemeinsam prüfen. Getesteter Stand: Caddy 2.11.7, Nginx 1.30.5, cloudflared 2026.10.0; Images sind zusätzlich auf ihren Digest festgelegt.
