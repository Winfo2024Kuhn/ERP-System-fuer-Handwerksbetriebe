package org.example.kalkulationsprogramm.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.ServletWebRequest;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Generischer SPA-Reload-Handler.
 *
 * Spring Boot ruft diesen Controller bei jedem Fehler (insbesondere 404) auf.
 * Wir leiten 404er auf HTML-Navigationsanfragen (z.B. F5 auf
 * /dokumentuebersicht) auf /index.html weiter, damit React Router das Routing
 * uebernehmen kann. API-, Asset- und Zeiterfassungs-Pfade behalten ihr
 * normales Fehlerverhalten.
 *
 * Vorteil gegenueber einer manuellen Routen-Liste: neue React-Routen
 * funktionieren sofort beim Reload, ohne Backend-Aenderung.
 */
@Controller
@RequestMapping("/error")
public class SpaErrorController implements ErrorController {

    private final ErrorAttributes errorAttributes;

    public SpaErrorController(ErrorAttributes errorAttributes) {
        this.errorAttributes = errorAttributes;
    }

    @RequestMapping(produces = MediaType.TEXT_HTML_VALUE)
    public Object handleHtml(HttpServletRequest request) {
        int status = resolveStatus(request);
        String uri = resolveOriginalUri(request);

        if (status == HttpStatus.NOT_FOUND.value() && isSpaNavigationCandidate(uri)) {
            return "forward:/index.html";
        }
        return ResponseEntity.status(HttpStatus.valueOf(status))
                .contentType(MediaType.TEXT_HTML)
                .body(buildMinimalHtml(status, uri));
    }

    @RequestMapping
    public ResponseEntity<Map<String, Object>> handleJson(HttpServletRequest request) {
        int status = resolveStatus(request);
        Map<String, Object> body = new HashMap<>(errorAttributes.getErrorAttributes(
                new ServletWebRequest(request),
                ErrorAttributeOptions.defaults()));
        return ResponseEntity.status(HttpStatus.valueOf(status)).body(body);
    }

    private int resolveStatus(HttpServletRequest request) {
        Object statusObj = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (statusObj == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR.value();
        }
        try {
            return Integer.parseInt(statusObj.toString());
        } catch (NumberFormatException e) {
            return HttpStatus.INTERNAL_SERVER_ERROR.value();
        }
    }

    private String resolveOriginalUri(HttpServletRequest request) {
        Object uriObj = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        return uriObj != null ? uriObj.toString() : "";
    }

    /**
     * Pfade, die NICHT auf das SPA umgeleitet werden duerfen, weil sie eine
     * eigene Behandlung brauchen oder sonst Endlosschleifen entstehen koennten.
     */
    private boolean isSpaNavigationCandidate(String uri) {
        if (uri == null || uri.isEmpty()) {
            return false;
        }
        if (uri.startsWith("/api/") || uri.equals("/api")) {
            return false;
        }
        if (uri.startsWith("/zeiterfassung/") || uri.equals("/zeiterfassung")) {
            return false;
        }
        if (uri.startsWith("/actuator/") || uri.equals("/actuator")) {
            return false;
        }
        if (uri.startsWith("/error")) {
            return false;
        }
        // Pfade mit Datei-Endung (.js, .css, .png, .map ...) NICHT umleiten
        int lastSlash = uri.lastIndexOf('/');
        String last = lastSlash >= 0 ? uri.substring(lastSlash + 1) : uri;
        return !last.contains(".");
    }

    private String buildMinimalHtml(int status, String uri) {
        String safePath = uri == null ? "" : uri.replace("<", "&lt;").replace(">", "&gt;");
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>Fehler "
                + status + "</title></head><body style=\"font-family:sans-serif;padding:2rem\">"
                + "<h1>Fehler " + status + "</h1>"
                + "<p>Pfad: " + safePath + "</p>"
                + "</body></html>";
    }
}
