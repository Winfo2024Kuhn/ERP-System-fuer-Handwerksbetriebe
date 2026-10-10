# Öffentlicher Zugang zur mobilen Zeiterfassung

Der Benutzer hat am 09.10.2026 ausdrücklich die noch fehlenden Objektberechtigungen, HTTPS und Proxy-Konfiguration beauftragt. Domain und Zielinstallation stehen noch nicht fest; voraussichtlich Windows mit Docker. Deshalb entsteht ein überprüfbares Docker-Paket mit temporärem HTTPS-Testzugang und späterem Betrieb unter eigener Domain. Eine unbekannte Installation wird nicht automatisch umgestellt.

## Zugriffsregeln

Vorhandene fachliche Rechte werden weitergeführt, kein neues Rollenmodell:
- Mobile Projekt-/Anfrage-Dateien: ausschließlich gespeicherte BILDER mit passendem Elternobjekt; keine Geschäftsdokumente und keine Originalnamen-/Speicher-Fallbacks.
- Notizen und deren Bilder: mobileSichtbar; private Notizen nur für ihren Ersteller. Änderungen nur durch den bisherigen zulässigen Ersteller und über den passenden Elternpfad.
- Lieferantendokumente: vorhandene Abteilungsrechte für den konkreten Dokumenttyp auch beim direkten Download. Lieferantenbilder mobil nur als zugehörige Reklamationsbilder.
- Belege: bestehendes BELEG-Recht und uploadedBy gleich angemeldetem Mitarbeiter. Firmenweiter Desktop-Zugriff bleibt bestehen.
- Mobile Dateiantworten sind nicht dauerhaft cachebar. Der Service Worker lädt geschützte APIs ausschließlich über das Netz und entfernt die bisherigen pauschalen API-/Bilder-Caches. Die explizite Offline-Zeiterfassung bleibt bestehen; bereits heruntergeladene oder kopierte Daten können nicht rückwirkend gelöscht werden.

## Öffentlicher Eingang

Ein isolierter Nginx-Gateway erlaubt nur MobileApiPolicy-konforme Methoden und Pfade sowie mobile statische Ressourcen. Er setzt X-ERP-Public-Mobile: 1 selbst, entfernt Desktop-Zugangsdaten und übernimmt nur das mobile Cookie. Die Anwendung wendet bei diesem Marker ebenfalls eine begrenzte Routenliste an und übernimmt keine Desktop-Sitzungsrechte. Der Marker kann Rechte ausschließlich verringern.

Gateway und ERP verbinden sich über ein eigenes privates Docker-Netz. Das ERP vertraut nur der festen Gateway-Adresse für die Client-IP. Desktop-Port ist standardmäßig an localhost gebunden, optional ausdrücklich an eine private/Tailscale-Adresse; Datenbank-/Qdrant-Ports bleiben lokal. Fremde Forwarded-Header werden verworfen. Uploadgröße, Verbindungszeiten und Anfrageraten werden vor dem ERP begrenzt; keine URL-/Token-Zugriffslogs.

Testweg: cloudflared Quick Tunnel mit zufälliger temporärer HTTPS-Adresse. Dauerhafter Weg: feste Domain mit Caddy und automatischen Zertifikaten. Vercel ist für die bestehende PWA nicht erforderlich und würde weiterhin einen sicheren Weg zum privaten Windows-ERP brauchen.

## Umsetzung und Prüfung

1. Objektberechtigungen an Datei-/Beleg-/Listen-/Notiz- und Lieferantenschnittstellen mit positiven und negativen Tests durchsetzen.
2. Öffentlichen Anwendungseingang gegen Desktop-Session-Vererbung und fachfremde Routen testen.
3. Service-Worker-Caches und Entzug von Zugriffsrechten prüfen.
4. Gateway-Konfiguration in Docker tatsächlich gegen einen lokalen Test-Upstream prüfen (Methoden, Pfade, Header, Cookies, Limits). Keine echte öffentliche ERP-Instanz ohne bekannte Zielkonfiguration starten.
5. Alle erforderlichen Backend-/Frontend-/Browser-Checks, Build, unabhängigen Review und Graphify durchführen. Öffentliches Deployment und tatsächlich geprüfte lokale Vorbereitung im Abschluss ausdrücklich unterscheiden.
