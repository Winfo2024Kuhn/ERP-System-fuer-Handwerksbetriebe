# Desktop-Oberfläche (react-pc-frontend)

Die Oberfläche des ERP für den Büro-PC: React 19, TypeScript, Vite 7, Tailwind CSS 3 und React Router 7. Im Betrieb liefert Spring Boot den fertigen Build unter `/` aus, die Handy-App liegt getrennt in [`react-zeiterfassung`](../react-zeiterfassung).

Voraussetzung: Node.js 20.19 oder neuer (Vite 7), die CI nutzt Node 24.

## Befehle

| Befehl | Zweck |
| --- | --- |
| `npm ci` | Abhängigkeiten installieren |
| `npm run dev` | Dev-Server mit Hot Reload |
| `npm run build` | Typprüfung (`tsc -b`) und Produktions-Build nach `../src/main/resources/static/` |
| `npm run lint` | ESLint |
| `npm test` | Unit-Tests (Vitest, Testing Library, jsdom) |
| `npm run test:coverage` | Unit-Tests mit Abdeckungsbericht |
| `npm run test:e2e` | End-to-End-Tests (Playwright) |
| `npm run test:e2e:ui` | Playwright im UI-Modus |
| `npm run typecheck:e2e` | Typprüfung der E2E-Tests |

## Backend beim Entwickeln

Der Dev-Server leitet `/api` an das Backend weiter. Das Ziel steht in `vite.config.ts` unter `server.proxy` (derzeit `https://localhost:8080`). Das Backend selbst startet lokal mit `./mvnw spring-boot:run` auf Port 8080, siehe [README im Hauptordner](../README.md).

## Build-Ausgabe

`npm run build` schreibt nach `src/main/resources/static/` im Hauptordner. Diese Dateien sind eingecheckt: Nach Änderungen an der Oberfläche bauen und den Build mitcommitten, sonst zeigt `./mvnw spring-boot:run` den alten Stand. Der Build leert den Ordner nicht (`emptyOutDir: false`), dort liegt u. a. auch die Handy-App. Das Docker-Image baut beide Oberflächen beim Bauen selbst neu.

## Aufbau

| Ordner | Inhalt |
| --- | --- |
| `src/pages/` | Seiten, die Routen stehen in `src/App.tsx` |
| `src/components/` | Bausteine der Seiten, z. B. `DetailLayout`, `EmailsTab`, `DocumentEditor` |
| `src/components/ui/` | Grundbausteine: `button`, `select-custom`, `datepicker`, `image-viewer`, `dialog`, `confirm-dialog`, `toast` u. a. |
| `src/components/layout/` | Rahmen der Anwendung: `MainLayout`, Menüleiste `RibbonNav`, `PageLayout` |
| `src/features/` | Fachliche Bereiche, z. B. `email`, `monatsabschluss`, `telefon`, `zeitkonto` |
| `src/lib/` | Hilfsfunktionen, u. a. `htmlSanitizer.ts` (Pflicht für HTML aus fremden Quellen) |
| `src/auth/` | Anmeldung, Sitzung und Routen-Schutz |
| `e2e/` | Playwright-Tests, Hilfen in `e2e/hilfen/` |

Regeln für Design und Pflicht-Bausteine: [`.github/DEVELOPMENT.md`](../.github/DEVELOPMENT.md) und [`docs/agent instructions/docs/FRONTEND_UI.md`](../docs/agent%20instructions/docs/FRONTEND_UI.md). Texte in der Oberfläche sind deutsch und in einfacher Handwerker-Sprache.

## End-to-End-Tests

`npm run test:e2e` baut die Oberfläche frisch und testet sie über `vite preview` (Port `E2E_PORT`, Standard 4173). Spring Boot und Datenbank braucht es dafür nicht: Jeder Test stubbt seine `/api`-Aufrufe selbst (`e2e/hilfen/api.ts`) und nutzt nur Dummy-Daten. Jede Spec läuft in drei Fenstergrößen (1440×900, 1536×960, 1920×1080). Mit `E2E_DEVSERVER=1` oder im UI-Modus läuft stattdessen der Dev-Server.
