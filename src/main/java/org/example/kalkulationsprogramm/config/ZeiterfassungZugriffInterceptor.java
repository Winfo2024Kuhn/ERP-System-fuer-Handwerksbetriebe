package org.example.kalkulationsprogramm.config;

import java.util.List;

import org.example.kalkulationsprogramm.service.ZeiterfassungSichtbarkeitService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sperrt die Zeiterfassungs-Daten am PC für Zugänge ohne das Recht
 * „Zeiterfassung im Menü sehen“ (Nutzerentscheidung 13.10.2026, Issue #268):
 * Zeiten, Abwesenheiten mit Krankheit, Urlaubsanträge, Lange Krankheit und
 * Monatsabschlüsse anderer Mitarbeiter sind Personaldaten.
 *
 * <p>Gilt nur für angemeldete PC-Benutzer. Die Handy-App ({@link MobilePrincipal})
 * bleibt unberührt – dort regelt {@link MobileApiPolicy}, dass jeder nur seine
 * eigenen Daten erreicht. Nicht angemeldete Anfragen behandelt Spring Security.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZeiterfassungZugriffInterceptor implements HandlerInterceptor {

    /** Bereiche, die zur Menü-Gruppe „Zeiterfassung“ gehören. */
    static final List<String> GESPERRTE_PFADE = List.of(
            "/api/zeitverwaltung/**",
            "/api/zeitkonto/**",
            "/api/urlaub/**",
            "/api/abwesenheit/**",
            "/api/langzeitkrankmeldungen/**");

    /**
     * Ausnahmen: die Abfrage, ob man den Bereich sieht; der Feiertags-Lookup;
     * der Team-Kalender im Projektmanagement (zeigt Krankheit ohne Recht bereits
     * nur als „Abwesend“, siehe {@code GesundheitsdatenSichtService}).
     */
    static final List<String> FREIE_PFADE = List.of(
            "/api/zeitverwaltung/sichtbarkeit",
            "/api/zeitverwaltung/feiertage/zwischen",
            "/api/abwesenheit/team");

    static final String MELDUNG = "Die Zeiterfassung ist für deinen Zugang ausgeblendet. Frag im Büro nach, wenn du sie brauchst.";

    /**
     * Über {@link ObjectProvider}, weil {@code @WebMvcTest}-Schnitte den Interceptor
     * (über {@code WebConfig}) laden, den Service aber nicht – dort prüft er nichts.
     * In der laufenden Anwendung ist der Service immer da, die Prüfung greift also immer.
     */
    private final ObjectProvider<ZeiterfassungSichtbarkeitService> sichtbarkeitService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.getPrincipal() instanceof FrontendUserPrincipal)) {
            return true;
        }
        ZeiterfassungSichtbarkeitService service = sichtbarkeitService.getIfAvailable();
        if (service != null && !service.darfZeiterfassungSehen(authentication)) {
            log.info("Zeiterfassung gesperrt: Profil {} auf {} {}", ((FrontendUserPrincipal) authentication.getPrincipal()).getId(),
                    request.getMethod(), request.getRequestURI());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, MELDUNG);
        }
        return true;
    }
}
