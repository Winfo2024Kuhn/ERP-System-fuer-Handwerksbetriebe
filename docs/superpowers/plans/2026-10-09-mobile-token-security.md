# Mobile Token-Sicherheit Implementation Plan

**Goal:** Mobile API-Zugriffe authentifizieren, mobile Rechte begrenzen und progressive Token-Wartezeiten erzwingen.
**Spec:** ../specs/2026-10-09-mobile-token-security-design.md
**Architecture:** Bestehende Spring-Filterkette mit explizitem Mobile-Principal und Routenpolitik; begrenzter Rate-Limiter und vertrauenswürdige Proxy-Auflösung; zentraler gleichursprünglicher PWA-Transport und Countdown.
**Tech Stack:** Spring Security 6, Java 23, React, Vitest, Playwright.

## Aufgaben

- [x] Regressionstest mit realer SecurityConfig: GET /api/zeiterfassung/projekte ohne Anmeldung muss 401 statt 200 liefern. Mit gültigem Mobile-Token erlauben, mit deaktiviertem Token verweigern. Verwaltungsaktionen müssen mit Mobile-Token 403 liefern.
- [x] TokenAttemptLimiter mit injizierbarer Uhr: drei Fehlversuche -> Retry-After 2, weiterer Fehler nach Frist -> 4; frühe und parallele Versuche dürfen nicht zusätzlich prüfen; Obergrenze, Verfall, Speicherschranke und erfolgreicher Fremdtoken ohne Reset testen.
- [x] MobileTokenAuthenticationFilter / MobileApiPolicy / MobilePrincipal in config implementieren und SecurityConfig integrieren. Explizite Tokens, sichere lesende Cookie-Kompatibilität, kein Speichern des Mobile-Principals als Desktop-Session.
- [x] ClientIpResolver aus vertrauenswürdigen Proxys konfigurieren; ZeiterfassungSecurityFilter verwendet dieselbe Prüfung. Gefälschte CF-/Forwarded-Header dürfen Sperren und Netzprüfung nicht umgehen.
- [x] Mitarbeiteridentität in Urlaubsantrag-Zugriffen serverseitig binden; fremde IDs und Profilheader ablehnen. Mobile Methoden-/Routenliste anhand bestehender PWA-Aufrufe prüfen.
- [x] PWA-Transport und Countdown zuerst mit Vitest/Playwright testen, dann implementieren. Keine neuen Designkomponenten; handwerkerprogramm-design befolgen.
- [ ] Backend-Tests, beide Frontend-Tests/Lint/Build/E2E, unabhängiger Review gemäß review-and-ship; Dokumentation und Graphify aktualisieren. Keine Änderungen fremder Sessions übernehmen.

## Verifikation am 09.10.2026

- Backend: 4.250 Tests, keine Fehler, 17 bereits projektseitig übersprungene Tests; Paket erfolgreich erstellt.
- Desktop: 2.067 Unit-Tests und 840 E2E-Tests; Lint und Build erfolgreich.
- Mobile: 376 Unit-Tests und 39 E2E-Tests; Lint und Build erfolgreich.
- Graphify im isolierten Worktree aktualisiert; generierte Graph-Dateien werden gemäß Wrapper-Hinweis nicht committed.
- Vollständiger unabhängiger Claude-Review nach Korrekturen: grün. Schlussprüfung des anschließenden AUTH_VALIDATED-Nachtrags durch Claude am Wochenlimit gescheitert; Ersatzreview noch freizugeben.
