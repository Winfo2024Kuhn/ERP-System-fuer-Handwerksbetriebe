package org.example.kalkulationsprogramm.controller;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.example.kalkulationsprogramm.service.KiHilfeService;
import org.example.kalkulationsprogramm.service.KiHilfeService.ChatMessage;
import org.example.kalkulationsprogramm.service.KiHilfeService.ChatResult;
import org.example.kalkulationsprogramm.service.KiHilfeService.PageContext;
import org.example.kalkulationsprogramm.service.KiHilfeService.SourceLink;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Chat der KI-Hilfe. Die Antwort kommt als Server-Sent Events: erst die Schritte, die die KI
 * gerade geht ({@code schritt}), dann genau ein {@code antwort} oder {@code fehler}. So sieht der
 * Nutzer live, wo die KI nachschaut, statt bis zu einer Minute auf einen Ladekreis zu starren.
 * <p>
 * Jede Frage laeuft in einem eigenen virtuellen Thread. Wie viele gleichzeitig laufen, ist
 * begrenzt ({@value #MAX_GLEICHZEITIG}) - jede kostet Gemini-Aufrufe. Schliesst der Nutzer den
 * Chat oder dauert es zu lange, wird der Thread unterbrochen; laufende Aufrufe brechen dann ab.
 */
@Slf4j
@RestController
@RequestMapping("/api/ki-hilfe")
@RequiredArgsConstructor
public class KiHilfeController {

    /** Notbremse fuer den Stream; der Dienst selbst hoert nach 150 s auf (KiHilfeService.gesamtZeitlimit). */
    static final long ANTWORT_ZEITLIMIT_MS = 180_000;
    /** So viele Fragen duerfen im ganzen Betrieb gleichzeitig laufen. */
    static final int MAX_GLEICHZEITIG = 8;
    static final int MAX_NACHRICHTEN = 50;
    static final int MAX_ZEICHEN = 5000;
    static final int MAX_ROUTE = 200;
    static final int MAX_SEITENTITEL = 200;
    static final int MAX_FEHLERMELDUNGEN = 2_000;
    static final int MAX_SICHTBARER_INHALT = 10_000;

    static final String FEHLER_ALLGEMEIN = "KI-Hilfe ist gerade nicht verfügbar. Bitte versuche es später erneut.";
    static final String FEHLER_ZU_LANGE = "Die KI hat zu lange gebraucht. Bitte stelle die Frage noch einmal, gern etwas genauer.";
    static final String FEHLER_AUSGELASTET = "Die KI-Hilfe ist gerade ausgelastet. Bitte versuche es gleich noch einmal.";

    private final KiHilfeService kiHilfeService;
    private final Semaphore plaetze = new Semaphore(MAX_GLEICHZEITIG);

    public record ChatRequest(List<MessageDto> messages, PageContextDto context) {}
    public record MessageDto(String role, String text) {}
    public record PageContextDto(String route, String pageTitle, String visibleContent, String errorMessages, Double latitude, Double longitude) {}

    /** Ereignis {@code schritt}: was die KI gerade tut, als reiner Text. */
    record Schritt(String text) {}
    /** Ereignis {@code antwort}: die fertige Antwort. */
    record Antwort(String reply, List<SourceLink> sources, List<String> schritte) {}
    /** Ereignis {@code fehler}: Meldung fuer den Nutzer. */
    record Fehler(String error) {}

    /** Der Browser hat die Verbindung beendet (Chat geschlossen) - es lohnt kein weiterer Schritt. */
    private static final class VerbindungBeendet extends RuntimeException {
        VerbindungBeendet(Throwable ursache) {
            super(ursache);
        }
    }

    @PostMapping("/chat")
    public SseEmitter chat(@RequestBody ChatRequest request, HttpServletResponse response) {
        List<ChatMessage> messages = nachrichtenPruefen(request);
        PageContext pageContext = seitenkontext(request.context());
        // Nur Laengen: Fragen und Bildschirminhalte koennen Namen von Kunden oder Mitarbeitern enthalten.
        log.info("KI-Hilfe Anfrage: {} Nachrichten, Route {}, Kontext {} Zeichen", messages.size(),
                pageContext != null ? pageContext.route() : "(keine)",
                pageContext != null && pageContext.visibleContent() != null ? pageContext.visibleContent().length() : 0);

        SseEmitter emitter = new SseEmitter(ANTWORT_ZEITLIMIT_MS);
        Thread arbeiter = Thread.ofVirtual().name("ki-hilfe").unstarted(() -> {
            try {
                beantworten(emitter, messages, pageContext);
            } finally {
                plaetze.release();
            }
        });
        // Notbremse: Der Dienst meldet sich nach seiner Gesamtfrist selbst (FEHLER_ZU_LANGE). Laeuft
        // trotzdem das Stream-Limit ab, ist der Stream schon zu (Spring 6.1) - nur noch stoppen.
        emitter.onTimeout(() -> {
            log.warn("KI-Hilfe: Zeitlimit von {} s erreicht", ANTWORT_ZEITLIMIT_MS / 1000);
            arbeiter.interrupt();
        });
        // Browser weg (Verbindungsfehler): laufende Gemini-Aufrufe abbrechen, statt weiter zu bezahlen
        emitter.onError(fehler -> arbeiter.interrupt());
        // Proxys (nginx) sollen die Schritte sofort durchreichen, nicht sammeln
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        // Platz erst direkt vor dem Start nehmen: Nur der Arbeiter gibt ihn wieder frei
        if (!plaetze.tryAcquire()) {
            log.warn("KI-Hilfe: {} Fragen laufen schon gleichzeitig - neue abgelehnt", MAX_GLEICHZEITIG);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, FEHLER_AUSGELASTET);
        }
        try {
            arbeiter.start();
        } catch (RuntimeException | Error e) {
            plaetze.release();
            throw e;
        }
        return emitter;
    }

    private void beantworten(SseEmitter emitter, List<ChatMessage> messages, PageContext pageContext) {
        try {
            ChatResult result = kiHilfeService.chat(messages, pageContext,
                    schritt -> senden(emitter, "schritt", new Schritt(schritt)));
            log.info("KI-Hilfe Antwort: {} Zeichen, {} Schritte", result.reply().length(), result.schritte().size());
            senden(emitter, "antwort", new Antwort(result.reply(), result.sources(), result.schritte()));
            emitter.complete();
        } catch (VerbindungBeendet e) {
            log.info("KI-Hilfe: Browser hat die Verbindung beendet");
        } catch (KiHilfeService.ZeitlimitErreicht e) {
            log.warn("KI-Hilfe: Gesamtfrist erreicht");
            fehlerMelden(emitter, FEHLER_ZU_LANGE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fehlerMelden(emitter, FEHLER_ALLGEMEIN);
        } catch (Exception e) {
            // Nur die Fehlerart: Parse-Fehler zitieren die Antwort.
            log.error("KI-Hilfe Fehler: {}", e.getClass().getSimpleName());
            fehlerMelden(emitter, FEHLER_ALLGEMEIN);
        }
    }

    private static void senden(SseEmitter emitter, String ereignis, Object daten) {
        try {
            emitter.send(SseEmitter.event().name(ereignis).data(daten, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            throw new VerbindungBeendet(e);
        }
    }

    private static void fehlerMelden(SseEmitter emitter, String meldung) {
        try {
            senden(emitter, "fehler", new Fehler(meldung));
            emitter.complete();
        } catch (VerbindungBeendet e) {
            log.info("KI-Hilfe: Fehler nicht mehr zustellbar, Verbindung ist schon beendet");
        }
    }

    /** Prueft die Nachrichten vor dem Start; Fehler kommen als 400 mit verstaendlicher Meldung. */
    private static List<ChatMessage> nachrichtenPruefen(ChatRequest request) {
        if (request == null || request.messages() == null || request.messages().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keine Nachricht angegeben");
        }
        if (request.messages().size() > MAX_NACHRICHTEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zu viele Nachrichten");
        }
        for (MessageDto msg : request.messages()) {
            if (msg == null || msg.text() == null || msg.text().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Leere Nachricht");
            }
            if (msg.text().length() > MAX_ZEICHEN) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Nachricht zu lang (max. " + MAX_ZEICHEN + " Zeichen)");
            }
            if (!"user".equals(msg.role()) && !"assistant".equals(msg.role())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültige Rolle");
            }
        }
        return request.messages().stream()
                .map(m -> new ChatMessage(m.role(), m.text()))
                .toList();
    }

    /**
     * Seitenkontext aus dem Browser. Die Route muss wie eine ERP-Seite aussehen (sie landet im Log);
     * die Texte werden gekuerzt - sie gehen in jede Runde an Gemini.
     */
    private static PageContext seitenkontext(PageContextDto dto) {
        if (dto == null) return null;
        String route = dto.route();
        if (route != null && !route.isEmpty()
                && (route.length() > MAX_ROUTE || route.charAt(0) != '/' || route.chars().anyMatch(Character::isISOControl))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültige Seite");
        }
        return new PageContext(route, kuerzen(dto.pageTitle(), MAX_SEITENTITEL),
                kuerzen(dto.visibleContent(), MAX_SICHTBARER_INHALT), kuerzen(dto.errorMessages(), MAX_FEHLERMELDUNGEN),
                dto.latitude(), dto.longitude());
    }

    private static String kuerzen(String text, int max) {
        if (text == null || text.length() <= max) return text;
        // Kein halbes Emoji (Surrogatpaar) am Ende
        return text.substring(0, Character.isHighSurrogate(text.charAt(max - 1)) ? max - 1 : max);
    }
}
