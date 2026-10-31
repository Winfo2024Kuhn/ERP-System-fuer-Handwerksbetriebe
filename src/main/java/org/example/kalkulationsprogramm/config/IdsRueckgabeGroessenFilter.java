package org.example.kalkulationsprogramm.config;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Begrenzt die Größe eines Shop-Rücksprungs, bevor Spring den Formular-Body liest. Der Rücksprung ist
 * ohne Anmeldung erreichbar; ohne diese Grenze gälte die allgemeine Upload-Grenze von 15 GB.
 * Browser schicken Formulare immer mit {@code Content-Length}; ohne Längenangabe wird abgelehnt.
 */
public class IdsRueckgabeGroessenFilter extends OncePerRequestFilter {

    /**
     * Ein Warenkorb mit 500 Artikeln samt Langtexten bleibt deutlich darunter. Bewusst unter Tomcats
     * Grenze für url-kodierte Formulare (2 MB), die einen größeren Body still verwerfen würde.
     */
    static final long MAX_BYTES = 1024L * 1024;

    /** Nur der POST des Shops trägt einen Body; alles andere lehnt die Rechteprüfung ab. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long laenge = request.getContentLengthLong();
        if (laenge < 0) {
            response.sendError(HttpServletResponse.SC_LENGTH_REQUIRED);
            return;
        }
        if (laenge > MAX_BYTES) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        chain.doFilter(request, response);
    }
}
