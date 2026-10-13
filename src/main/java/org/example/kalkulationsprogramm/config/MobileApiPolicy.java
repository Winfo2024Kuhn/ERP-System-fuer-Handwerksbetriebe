package org.example.kalkulationsprogramm.config;

import java.util.List;
import java.util.regex.Pattern;

/** Abschließende Methoden-/Routenliste. Neue Verwaltungsaktionen sind niemals automatisch mobil erlaubt. */
public final class MobileApiPolicy {
    private record Route(String method, Pattern path) {}
    private static Route route(String method, String path) { return new Route(method, Pattern.compile(path)); }
    private static final String ID = "[0-9]+";
    private static final List<Route> ROUTES = List.of(
        route("GET", "/api/mitarbeiter/by-token/[^/]+"),
        route("GET", "/api/zeiterfassung/(?:projekte|kategorien|lieferanten|feiertage)"),
        route("GET", "/api/zeiterfassung/(?:projekte|kategorien)/" + ID),
        route("GET", "/api/zeiterfassung/projekte/" + ID + "/bilder"),
        route("GET", "/api/zeiterfassung/(?:arbeitsgaenge|aktiv|heute|saldo|buchungen|urlaubsverfall|buchungszeitfenster|langzeitkrankmeldung)/[^/]+"),
        route("POST", "/api/zeiterfassung/(?:start|stop|pause)"),
        route("GET", "/api/urlaub/(?:antraege|resturlaub|typen)"),
        route("POST", "/api/urlaub/antraege"),
        // Eigenen offenen Antrag zurückziehen; die Fachregel (nur eigener, nur offen) steht im Controller/Service.
        route("PUT", "/api/urlaub/antraege/" + ID + "/storno"),
        route("GET", "/api/urlaub/antraege/neue-entscheidungen"),
        route("PUT", "/api/urlaub/antraege/" + ID + "/entscheidung-gesehen"),
        route("GET", "/api/kalender/mobile(?:/tag)?"),
        route("GET", "/api/abwesenheit/team"),
        route("GET", "/api/push/vapid-key"),
        route("POST", "/api/push/subscribe"),
        route("GET", "/api/(?:kunden|arbeitsgaenge)"),
        route("GET", "/api/anfragen(?:/" + ID + ")?"),
        route("GET", "/api/lieferanten/" + ID),
        route("GET", "/api/lieferanten/bilder/file/[^/]+(?:/vorschau)?"),
        route("GET", "/api/lieferanten/" + ID + "/dokumente(?:/" + ID + "/download)?"),
        route("POST", "/api/lieferanten/" + ID + "/dokumente/(?:analyze|import)"),
        route("GET", "/api/(?:projekte|anfragen)/" + ID + "/(?:dokumente|notizen)"),
        route("POST", "/api/(?:projekte|anfragen)/" + ID + "/(?:dokumente|notizen)"),
        route("PATCH", "/api/(?:projekte|anfragen)/" + ID + "/notizen/" + ID),
        route("DELETE", "/api/(?:projekte|anfragen)/" + ID + "/notizen/" + ID + "(?:/bilder/" + ID + ")?"),
        route("POST", "/api/(?:projekte|anfragen)/" + ID + "/notizen/" + ID + "/bilder"),
        route("GET", "/api/reklamationen/(?:lieferant/)?" + ID),
        route("POST", "/api/reklamationen/lieferant/" + ID),
        route("POST", "/api/reklamationen/" + ID + "/bilder"),
        route("GET", "/api/zeitverwaltung/feiertage/zwischen"),
        route("GET", "/api/buchhaltung/mobile/(?:me/permissions|belege(?:/" + ID + ")?)"),
        route("POST", "/api/buchhaltung/mobile/(?:belege|mwst-rechner)"),
        route("PUT", "/api/buchhaltung/mobile/belege/" + ID + "/positionen"),
        route("POST", "/api/spracheingabe/transkribieren"),
        route("GET", "/api/spracheingabe/verfuegbar"),
        route("GET", "/api/(?:images|dokumente)/[^/]+(?:/(?:thumbnail|anzeige))?")
    );
    private MobileApiPolicy() {}
    public static boolean allows(String method, String path) {
        String effectiveMethod = "HEAD".equals(method) ? "GET" : method;
        return ROUTES.stream().anyMatch(r -> r.method.equals(effectiveMethod) && r.path.matcher(path).matches());
    }

    /** "METHODE:muster" je Route, für den Abgleich mit deployment/mobile-public/mobile-routes.conf. */
    static List<String> routenFuerGateway() {
        return ROUTES.stream().map(r -> r.method + ":" + r.path.pattern()).toList();
    }
}
