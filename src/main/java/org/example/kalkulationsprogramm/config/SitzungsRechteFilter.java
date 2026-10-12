package org.example.kalkulationsprogramm.config;

import java.io.IOException;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

/**
 * Hält die Rechte einer laufenden PC-Sitzung auf dem Stand der Datenbank.
 *
 * <p>Spring Security legt die Rollen bei der Anmeldung in der Sitzung ab. Bekam ein Kollege
 * danach Admin-Rechte, zeigte ihm die Oberfläche (über {@code /api/auth/me}, frisch aus der
 * Datenbank) zwar die Admin-Menüs, der Server verweigerte aber Einstellungen, Benutzer und
 * Postfächer (Issue #244). Umgekehrt behielt ein Admin, dem das Recht entzogen wurde, es bis
 * zur Abmeldung.
 *
 * <p>Der Filter gleicht deshalb vor der Rechteprüfung jede Anfrage ab: geänderte Rollen
 * übernimmt er in die Sitzung, ein gelöschter oder deaktivierter Benutzer wird abgemeldet.
 * Handy-Anmeldungen per Token ({@link MobilePrincipal}) lässt er unberührt.
 *
 * <p>Die Sitzungs-ID bleibt bei einer Rechteänderung bewusst gleich: Die Oberfläche schickt
 * viele Anfragen parallel, eine neue ID würde die noch laufenden mit 401 abbrechen. Gegen
 * Session-Fixation schützt die neue ID bei der Anmeldung selbst.
 */
@Slf4j
public class SitzungsRechteFilter extends OncePerRequestFilter {

    private final FrontendUserDetailsService benutzer;
    private final SecurityContextHolderStrategy kontexte = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository sitzungen = new DelegatingSecurityContextRepository(
            new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());

    public SitzungsRechteFilter(FrontendUserDetailsService benutzer) {
        this.benutzer = benutzer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication anmeldung = kontexte.getContext().getAuthentication();
        FrontendUserPrincipal angemeldet = FrontendUserPrincipal.aus(anmeldung);
        if (angemeldet != null) {
            Optional<FrontendUserPrincipal> aktuell = benutzer.aktuellerStand(angemeldet);
            if (aktuell.isEmpty()) {
                log.info("[Sitzung] Benutzer {} ist deaktiviert oder gelöscht – abgemeldet", angemeldet.getId());
                abmelden(request);
            } else if (aktuell.get() != angemeldet) {
                log.info("[Sitzung] Rechte von Benutzer {} geändert: {} -> {}", angemeldet.getId(),
                        angemeldet.getRoles(), aktuell.get().getRoles());
                uebernehmen(aktuell.get(), anmeldung, request, response);
            }
        }
        chain.doFilter(request, response);
    }

    private void abmelden(HttpServletRequest request) {
        kontexte.clearContext();
        HttpSession sitzung = request.getSession(false);
        if (sitzung != null) {
            sitzung.invalidate();
        }
    }

    private void uebernehmen(FrontendUserPrincipal aktuell, Authentication bisher,
                             HttpServletRequest request, HttpServletResponse response) {
        UsernamePasswordAuthenticationToken neu = UsernamePasswordAuthenticationToken.authenticated(
                aktuell, null, aktuell.getAuthorities());
        neu.setDetails(bisher.getDetails());
        SecurityContext kontext = kontexte.createEmptyContext();
        kontext.setAuthentication(neu);
        kontexte.setContext(kontext);
        sitzungen.saveContext(kontext, request, response);
    }
}
