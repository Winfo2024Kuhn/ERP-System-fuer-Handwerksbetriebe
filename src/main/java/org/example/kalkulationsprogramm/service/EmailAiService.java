package org.example.kalkulationsprogramm.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.dto.Email.EmailSchreibstil;
import org.example.kalkulationsprogramm.service.emailki.EmailKiNachbereitung;
import org.example.kalkulationsprogramm.service.emailki.EmailKiPrompt;
import org.example.kalkulationsprogramm.service.emailki.EmailKiVorbereitung;
import org.example.kalkulationsprogramm.service.emailki.VorbereiteterEntwurf;
import org.example.kalkulationsprogramm.service.ki.KiAnfrage;
import org.example.kalkulationsprogramm.service.ki.KiDienst;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiTeil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;

/**
 * Formuliert den eigenen Text einer E-Mail per KI neu („KI-Optimierung“ im E-Mail-Formular).
 *
 * <p>Ablauf: {@link EmailKiVorbereitung} ersetzt Tabellen und Bilder durch Platzhalter und
 * bereinigt das HTML; {@link EmailKiPrompt} baut die Prompts (mit den Schreibregeln nach
 * humanizer-de); {@link EmailKiNachbereitung} prüft und bereinigt die Antwort, ersetzt
 * Gedankenstriche und setzt Tabellen und Bilder unverändert wieder ein. Mail-Inhalte werden
 * nie geloggt, nur die Fehlerart.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAiService {

    /** Längster Text für die KI (bereinigtes HTML ohne Tabellen und Bilder, die nur als Platzhalter zählen). */
    public static final int MAX_TEXT_ZEICHEN = 50_000;

    /** Harte Obergrenze für das rohe HTML samt Tabellen und Bildern. */
    public static final int MAX_HTML_ZEICHEN = 500_000;

    private static final Duration ZEITLIMIT = Duration.ofSeconds(90);

    private final ObjectMapper objectMapper;
    private final KiDienst kiDienst;
    private final EmailKiVorbereitung vorbereitung;

    @Value("${ai.email.temperature:0.2}")
    private double temperature;

    @Value("${ai.email.enabled:true}")
    private boolean enabled;

    /**
     * Überarbeitet den eigenen Text einer E-Mail im gewählten Stil.
     *
     * @param entwurf      eigener Text als HTML (oder Klartext), ohne Signatur und Zitat
     * @param replyContext zitierte Vorgänger-Mail oder Projekt-Hinweis als Klartext; bei
     *                     {@link EmailSchreibstil#VERLAUF} Pflicht
     * @param stil         {@code null} = {@link EmailSchreibstil#RECHTSCHREIBUNG}
     * @return bereinigtes HTML; leer, wenn nichts zu formulieren war oder die KI nichts lieferte
     * @throws ResponseStatusException 400 bei zu langem Text ({@link #MAX_TEXT_ZEICHEN} für die KI,
     *                                 {@link #MAX_HTML_ZEICHEN} roh) oder {@code VERLAUF} ohne Verlauf
     * @throws IOException             wenn die KI nicht oder leer antwortet
     */
    public String beautify(String entwurf, String replyContext, EmailSchreibstil stil) throws IOException {
        EmailSchreibstil gewaehlt = stil == null ? EmailSchreibstil.RECHTSCHREIBUNG : stil;
        if (entwurf != null && entwurf.length() > MAX_HTML_ZEICHEN) {
            throw zuLang();
        }
        String kontext = EmailKiPrompt.kuerzeKontext(replyContext == null ? "" : normalisiereZeilen(replyContext).trim());
        if (gewaehlt == EmailSchreibstil.VERLAUF && kontext.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Kein Mail-Verlauf vorhanden. Bitte einen anderen Stil wählen.");
        }
        if (entwurf == null || entwurf.isBlank()) {
            return "";
        }

        VorbereiteterEntwurf vorbereitet = vorbereitung.vorbereiten(normalisiereZeilen(entwurf).trim());
        if (vorbereitet.htmlFuerKi().length() > MAX_TEXT_ZEICHEN) {
            throw zuLang();
        }
        if (!vorbereitet.hatText()) {
            return vorbereitet.festeElemente().isEmpty() ? "" : EmailKiNachbereitung.ohneKi(vorbereitet);
        }
        if (!enabled) {
            return EmailKiNachbereitung.ohneKi(vorbereitet);
        }

        try {
            String antwort = rufKi(EmailKiPrompt.systemPrompt(gewaehlt),
                    EmailKiPrompt.userPrompt(vorbereitet.htmlFuerKi(), kontext, gewaehlt));
            if (antwort == null || antwort.isBlank()) {
                throw new IOException("Keine Antwort von KI erhalten");
            }
            return EmailKiNachbereitung.fertigstellen(emailAus(antwort), vorbereitet);
        } catch (Exception e) {
            // Nur die Fehlerart: Parse-Fehler zitieren die Antwort (E-Mail-Inhalte).
            log.error("Fehler beim KI-Aufruf: {}", e.getClass().getSimpleName());
            throw new IOException("KI-Verarbeitung fehlgeschlagen", e);
        }
    }

    private static ResponseStatusException zuLang() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Der Text ist zu lang für die KI-Formulierung. Bitte kürzen.");
    }

    /** Liest {@code { "email": "…" }} (oder {@code text}); ist die Antwort kein JSON, gilt sie selbst als Text. */
    private String emailAus(String antwort) {
        try {
            JsonNode inhalt = objectMapper.readTree(antwort);
            if (inhalt.has("email")) {
                return inhalt.get("email").asString("");
            }
            if (inhalt.has("text")) {
                return inhalt.get("text").asString("");
            }
            return antwort;
        } catch (RuntimeException e) {
            log.warn("Konnte KI-Antwort nicht als JSON lesen, verwende Roh-Antwort: {}", e.getClass().getSimpleName());
            return antwort;
        }
    }

    /** Schickt die Anfrage an den KI-Anbieter, der für {@link KiFunktion#EMAIL_TEXT} eingestellt ist. */
    private String rufKi(String systemPrompt, String userMessage) {
        KiAnfrage anfrage = KiAnfrage.mitAnweisung(systemPrompt)
                .nutzer(KiTeil.text(userMessage))
                .temperatur(temperature)
                .json()
                .zeitlimit(ZEITLIMIT)
                .bauen();
        return kiDienst.sende(KiFunktion.EMAIL_TEXT, anfrage).text();
    }

    private static String normalisiereZeilen(String value) {
        return value.replace("\r\n", "\n").replace("\r", "\n");
    }
}
