package org.example.kalkulationsprogramm.service.mail.reiter;

import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.EmailBezug;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.dto.Email.EmailReiterSeiteDto;
import org.example.kalkulationsprogramm.repository.EmailReiterAbfrage;
import org.example.kalkulationsprogramm.repository.EmailReiterRepository;
import org.example.kalkulationsprogramm.repository.EmailVerlaufZeile;
import org.example.kalkulationsprogramm.service.LieferantDokumentZugriffService;
import org.example.kalkulationsprogramm.service.PostfachSichtbarkeit;
import org.example.kalkulationsprogramm.service.PostfachSichtbarkeitService;
import org.example.kalkulationsprogramm.util.LikeMuster;
import org.example.kalkulationsprogramm.util.suche.EmailSuchNormalisierung;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * E-Mail-Reiter in Projekt, Anfrage, Kunde und Lieferant ({@code GET /api/emails/reiter/{bezug}/{id}}).
 *
 * <p>Eine Zeile ist ein Verlauf: die ganze Kette über {@code parent_email_id} ab der obersten Mail. Ein
 * Verlauf gehört zum Reiter, wenn mindestens eine seiner Mails zum Bereich gehört (beim Kunden: Mails
 * seiner Projekte und Anfragen). Suche (wie im E-Mail-Center über {@code email.suchtext}) und Richtung
 * wirken auf diese Mails; die Zeile zeigt die neueste passende, die Liste ist danach sortiert – eine
 * neue Antwort auf einen alten Verlauf holt ihn nach oben. Sortieren, Filtern und der Seitenschnitt
 * laufen in der Datenbank ({@link EmailReiterRepository}).</p>
 *
 * <p>Rechte: angemeldet wie alle {@code /api/**}; gezählt und geöffnet werden nur Mails, die der Benutzer
 * lesen darf (wie {@code GET /api/emails/{id}/thread}). Beim Lieferanten zusätzlich wie bei seinen
 * Dokumenten: ohne zuzuordnenden Mitarbeiter 401, Anhänge nur sichtbarer Dokumenttypen zählen.</p>
 */
@Service
@RequiredArgsConstructor
public class EmailReiterService {

    static final int MIN_ZEICHEN = 2;
    static final int MAX_ZEICHEN = 200;
    static final int MAX_SEITENGROESSE = 50;

    private final EmailReiterRepository reiterRepository;
    private final PostfachSichtbarkeitService postfachSichtbarkeitService;
    private final LieferantDokumentZugriffService dokumentZugriffService;
    private final EmailReiterEintraege eintraege;

    /**
     * Eine Seite des Reiters.
     *
     * @param bezug    {@code projekt}, {@code anfrage}, {@code kunde} oder {@code lieferant}
     * @param q        Suchbegriff; getrimmt unter 2 Zeichen = keine Suche, über 200 Zeichen = 400
     * @param richtung {@code IN} oder {@code OUT}; leer = beide
     * @throws ResponseStatusException 400 (Bereich, Suchbegriff, Richtung), 401 (Lieferant ohne
     *                                 zuzuordnenden Benutzer), 404 (Datensatz fehlt)
     */
    @Transactional(readOnly = true)
    public EmailReiterSeiteDto seite(String bezug, Long id, int page, int size, String q, String richtung,
            Authentication authentication) {
        EmailBezug bereich = EmailBezug.vonSchluessel(bezug).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unbekannter Bereich für E-Mails."));
        Set<LieferantDokumentTyp> sichtbareTypen = bereich == EmailBezug.LIEFERANT
                ? dokumentZugriffService.sichtbareTypen(null, authentication).orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bitte erneut anmelden."))
                : null;
        if (id == null || !reiterRepository.bezugVorhanden(bereich, id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, bereich.nichtGefunden());
        }
        List<String> woerter = suchwoerter(q);
        EmailDirection gewaehlt = richtung(richtung);
        int seite = Math.max(page, 0);
        int groesse = Math.min(Math.max(size, 1), MAX_SEITENGROESSE);
        long offset = (long) seite * groesse;
        boolean seiteMoeglich = offset <= Integer.MAX_VALUE - groesse;

        EmailReiterAbfrage abfrage = new EmailReiterAbfrage(bereich, id, woerter, gewaehlt,
                sichtbarePostfaecher(authentication), seiteMoeglich ? (int) offset : 0, groesse);
        long gesamt = reiterRepository.zaehleVerlaeufe(abfrage);
        if (!seiteMoeglich || offset >= gesamt) {
            return new EmailReiterSeiteDto(List.of(), gesamt, seite, groesse);
        }
        List<EmailVerlaufZeile> zeilen = reiterRepository.verlaeufe(abfrage);
        return new EmailReiterSeiteDto(eintraege.baue(zeilen, abfrage, sichtbareTypen), gesamt, seite, groesse);
    }

    /** Wie die E-Mail-Suche: normalisierte Wörter als LIKE-Muster, alle müssen passen. */
    private static List<String> suchwoerter(String q) {
        String begriff = q == null ? "" : q.strip();
        if (begriff.length() > MAX_ZEICHEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Der Suchbegriff ist zu lang (höchstens 200 Zeichen).");
        }
        if (begriff.length() < MIN_ZEICHEN) {
            return List.of();
        }
        return EmailSuchNormalisierung.suchwoerter(begriff).stream()
                .map(wort -> "%" + LikeMuster.escape(wort) + "%")
                .toList();
    }

    private static EmailDirection richtung(String richtung) {
        if (richtung == null || richtung.isBlank()) {
            return null;
        }
        return switch (richtung.strip()) {
            case "IN" -> EmailDirection.IN;
            case "OUT" -> EmailDirection.OUT;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unbekannte Richtung.");
        };
    }

    /** {@code null} = keine Einschränkung (Admin). */
    private Set<Long> sichtbarePostfaecher(Authentication authentication) {
        PostfachSichtbarkeit sicht = postfachSichtbarkeitService.fuer(authentication);
        return sicht == null || sicht.alles() ? null : sicht.sichtbarePostfachIds();
    }
}
