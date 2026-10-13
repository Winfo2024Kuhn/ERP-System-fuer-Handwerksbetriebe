# Handy-App Zeiterfassung (react-zeiterfassung)

Die Web-App für die Mitarbeiter auf dem Handy: stempeln auf Projekt, Kategorie und Arbeitsgang, Tagesbuchungen, Salden, Urlaub und Abwesenheiten, Kalender, Notizen und Fotos zu Projekten und Anfragen, Lieferscheine und Reklamationen bei Lieferanten, Belegscanner und MwSt-Rechner. Im Betrieb liefert Spring Boot sie unter `/zeiterfassung` aus. Angemeldet wird per QR-Code aus der Mitarbeiterverwaltung der Desktop-Oberfläche.

Technik: React 19, TypeScript, Vite 7, Tailwind CSS 4 (`@tailwindcss/vite`), React Router 7 (`basename="/zeiterfassung"`), `vite-plugin-pwa` mit eigenem Service Worker (`src/sw.ts`), IndexedDB (`idb`) für Buchungen ohne Netz und Web-Push. Voraussetzung: Node.js 20.19 oder neuer (Vite 7), die CI nutzt Node 24.

## Befehle

| Befehl | Zweck |
| --- | --- |
| `npm ci` | Abhängigkeiten installieren |
| `npm run dev` | Dev-Server mit Hot Reload, per HTTPS (selbst signiertes Zertifikat) und im lokalen Netz erreichbar |
| `npm run build` | Typprüfung (`tsc -b`) und Produktions-Build nach `../src/main/resources/static/zeiterfassung/` |
| `npm run lint` | ESLint |
| `npm test` | Unit-Tests (Vitest, Testing Library, jsdom) |
| `npm run test:coverage` | Unit-Tests mit Abdeckungsbericht |
| `npm run test:e2e` | End-to-End-Tests (Playwright) |

## Backend beim Entwickeln

Der Dev-Server leitet `/api` an das Backend weiter. Das Ziel steht in `vite.config.ts` unter `server.proxy` (derzeit `http://localhost:8082`). Das Backend selbst startet lokal mit `./mvnw spring-boot:run` auf Port 8080, siehe [README im Hauptordner](../README.md).

HTTPS braucht es, weil Kamera, Mikrofon, Service Worker (Offline-Modus) und Push-Nachrichten im Browser nur in einer sicheren Umgebung laufen. Ein echtes Handy erreicht den Dev-Server über die Netzwerkadresse des Rechners.

## Build-Ausgabe

`npm run build` leert `src/main/resources/static/zeiterfassung/` im Hauptordner und baut neu. Diese Dateien sind eingecheckt: Nach Änderungen bauen und den Build mitcommitten. Das Docker-Image baut beide Oberflächen beim Bauen selbst neu.

Die Ecken-Erkennung im Dokumentenscanner nutzt `scanic` mit einem kleinen KI-Modell aus `scanic-ml` (Modell rund 1,9 MB, Laufzeit rund 1,5 MB). Beides liefert die App selbst aus, nicht über ein CDN, und es kommt bewusst mit in den Offline-Speicher, damit der Scanner auch bei schlechtem Netz sofort bereit ist. Die Fotos verlassen das Handy dafür nicht. Die Logik steckt in `src/services/` (`DocumentEdgeDetector`, `kantenEinrasten`, `scanFilter`, `scanSeite`), die Oberfläche in `src/components/scanner/`.

## Aufbau

| Ordner | Inhalt |
| --- | --- |
| `src/pages/` | Seiten, die Routen stehen in `src/App.tsx` |
| `src/components/` | Bausteine, z. B. Kamera, Scanner, Spracheingabe, Sync-Anzeige; Grundbausteine in `src/components/ui/` |
| `src/services/` | Offline-Speicher und Abgleich (`OfflineService`), Benachrichtigungen, Kamera, Spracheingabe, Ecken-Erkennung |
| `src/auth/` | Übertragung des Mitarbeiter-Tokens an die API (`mobileTransport.ts`) |
| `src/sw.ts` | Service Worker (Offline, Push) |
| `e2e/` | Playwright-Tests |

Die Sicherheitsregeln für den Mitarbeiter-Token stehen in [`docs/MOBILE_TOKEN_SECURITY.md`](../docs/MOBILE_TOKEN_SECURITY.md), die Abläufe in [`docs/ZEITERFASSUNG_WORKFLOWS.md`](../docs/ZEITERFASSUNG_WORKFLOWS.md). Texte in der App sind deutsch und in einfacher Handwerker-Sprache.

## End-to-End-Tests

`npm run test:e2e` startet den Vite-Dev-Server per HTTPS auf `127.0.0.1` (Port `E2E_PORT`, Standard 5175) und testet im Handy-Format (iPhone 15, Chromium).
