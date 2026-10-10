package org.example.kalkulationsprogramm.config;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Kein Desktop-Benutzer: Rechte werden ausschließlich durch MobileApiPolicy vergeben. */
public record MobilePrincipal(Long mitarbeiterId) {
    public static MobilePrincipal current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof MobilePrincipal mobile ? mobile : null;
    }
    public static Long ownMitarbeiterId(Long requested) {
        MobilePrincipal mobile = current();
        if (mobile == null) return requested;
        if (requested != null && !mobile.mitarbeiterId.equals(requested)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Zugriff verweigert.");
        }
        return mobile.mitarbeiterId;
    }
}
