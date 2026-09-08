package org.example.kalkulationsprogramm.controller;

import java.time.LocalDate;
import java.util.Map;

import org.example.kalkulationsprogramm.service.LangzeitkrankmeldungService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * REST-API fuer die Handy-App (PWA) rund um laufende Langzeitkrankmeldungen -
 * bewusst rein lesend und ein einziger Endpunkt.
 *
 * <p><b>Langzeitkrankmeldungen werden ausschliesslich am PC gepflegt</b>
 * (Vorgabe des Projektinhabers vom 08.09.2026). Ein Mitarbeiter kann in der
 * Handy-App weder eine Meldung anlegen noch eine Phase aendern - deshalb gibt
 * es hier absichtlich **kein** POST/PUT/PATCH/DELETE, auch nicht "fuer
 * spaeter schon vorbereitet". Die App zeigt der laufenden Phase nur an, damit
 * der Mitarbeiter weiss, wogegen er stempelt, wenn sein Tagessoll reduziert
 * ist (z. B. 4 Stunden waehrend der Wiedereingliederung).
 *
 * <p><b>Keine SecurityConfig-Aenderung noetig:</b> Der Pfad liegt unter
 * {@code /api/zeiterfassung/**} und ist damit bereits Teil der
 * Mobile-Whitelist ({@code ZEITERFASSUNG_PATHS} in
 * {@code config/SecurityConfig.java}). Diesen Endpunkt spaeter unter eine
 * eigene, engere Whitelist zu ziehen wuerde ihn aus Versehen hinter den
 * Login sperren - bitte nicht "reparieren".
 *
 * <p>Der Endpunkt ist unauthentifiziert erreichbar (Token statt Login) und
 * damit die exponierteste Stelle des Features. Delegiert komplett an
 * {@link LangzeitkrankmeldungService#getMobileStand}, das bei unbekanntem
 * Token oder fehlender laufender Phase bewusst ein leeres Objekt liefert -
 * keine Information darueber, ob das Token existiert, kein Name, keine
 * Notiz (DSGVO).
 */
@RestController
@RequestMapping("/api/zeiterfassung/langzeitkrankmeldung")
@RequiredArgsConstructor
public class LangzeitkrankmeldungMobileController {

    private final LangzeitkrankmeldungService service;

    /**
     * Liefert den aktuellen Stand der laufenden Langzeitkrankmeldung fuer den
     * Mitarbeiter hinter dem Token, oder ein leeres Objekt, wenn es nichts zu
     * zeigen gibt (unbekanntes Token, keine laufende Phase am heutigen Tag).
     *
     * <p>Response bei laufender Phase:
     * {@code { "phase": "WIEDEREINGLIEDERUNG", "phaseLabel": "Wiedereingliederung",
     * "heuteGeplanteStunden": 4.00, "seit": "2026-03-01", "bisDatum": "2026-04-30" }}
     */
    @GetMapping("/{token}")
    public ResponseEntity<Map<String, Object>> getMobileStand(@PathVariable String token) {
        return ResponseEntity.ok(service.getMobileStand(token, LocalDate.now()));
    }
}
