# Mobile Token-Sicherheit

Freigegebener Auftrag: offene mobile Zugriffe absichern und nach drei falschen Token-Versuchen eine progressive, serverseitige Wartezeit einführen. Vorhandene QR-Codes und Offline-Buchungen bleiben nutzbar. Dieser erste Schritt enthielt noch kein öffentliches Deployment. Der anschließend autorisierte Ausbau ist im [Entwurf für den öffentlichen Mobile-Zugang](2026-10-09-public-mobile-design.md) beschrieben.

## Architektur

Die bestehende mobile Filterkette prüft vor jedem API-Zugriff einen Token eines aktiven Mitarbeiters oder eine bestehende Desktop-Anmeldung. Mobile Rechte werden durch eine explizite Liste aus HTTP-Methode und Route begrenzt. Desktop-Verwaltungsaktionen sind mit mobilen Tokens verboten. Die Mitarbeiteridentität stammt vom geprüften Token; fremde Mitarbeiter-/Profilangaben dürfen keine Rechte vermitteln. Die mobile Oberfläche bleibt ohne Anmeldung ladbar.

Die PWA sendet den gespeicherten Token zentral im X-Auth-Token-Header ausschließlich an gleichursprüngliche API-Aufrufe. Bestehende Token-Parameter bleiben für kompatible Aufrufe erhalten. Das mobile Cookie darf ausschließlich lesende Aufrufe authentifizieren (Bilder/Vorschauen); für mobile Schreibzugriffe ist ein expliziter Token nötig. Desktop-Sitzungen behalten ihren CSRF-Schutz. Geprüfte explizite Token-Anfragen sind zustandslos und werden nicht als Desktop-Anmeldung gespeichert.

## Wartezeiten

Fehlversuche werden pro Client-IP über alle Tokens gemeinsam gezählt. Dritter Fehler: 2 Sekunden, dann 4, 8, 16, 32, 64, 128, 256, maximal 300 Sekunden. Gesperrte Aufrufe erhalten sofort HTTP 429 mit Retry-After; keine blockierenden Sleeps. Parallelversuche sind pro Herkunft serialisiert. Ein gültiger Token setzt den Fehlversuchszähler nicht zurück. Nach 30 Minuten ohne weiteren Fehlversuch verfällt er. Speicher ist begrenzt; bei Überlast wird geschlossen statt aktive Sperren zu verdrängen. Der Speicher gilt für einen Serverprozess; Neustarts und mehrere Instanzen erfordern vorgeschaltete oder gemeinsame Limits.

Weiterleitungs-Header werden nur von ausdrücklich konfigurierten vertrauenswürdigen Proxys übernommen. Standard: direkte Verbindungsadresse. Die Oberfläche zeigt die verbleibende Wartezeit und verhindert vorzeitige Wiederholungen; der Server bleibt maßgeblich.

## Prüfung und Grenzen

Reale Spring-Security-Filterketten prüfen anonyme Zugriffe, gültige/ungültige/deaktivierte Tokens, Desktop-Sitzungen, Methodenbeschränkungen, fremde Mitarbeiter-IDs, CSRF und parallele Fehlversuche. Frontend-Tests prüfen Countdown, erneuten Versuch, keine Token-Weitergabe an externe URLs sowie den vorhandenen QR-/Offline-Ablauf.

Dieses Paket ersetzt keine vollständige Freigabe sämtlicher ERP-Funktionen für Internetbetrieb. Vor öffentlicher Bereitstellung müssen Proxy-Konfiguration, HTTPS, Objektberechtigungen und Betriebsmaßnahmen geprüft werden. Token-Diebstahl und verteilte Angriffe werden durch Wartezeiten allein nicht verhindert. Einmalige QR-Aktivierung und kurzlebige Sitzungen sind ein gesonderter Ausbau; die hier vereinbarte kompatible Absicherung lässt bestehende Tokens gültig.
