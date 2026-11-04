package org.example.kalkulationsprogramm.service.mail.suche;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailOrdner;
import org.example.kalkulationsprogramm.dto.Email.EmailSuchErgebnis;
import org.example.kalkulationsprogramm.dto.Email.UnifiedEmailDto;
import org.example.kalkulationsprogramm.repository.EmailSuchAbfrage;
import org.example.kalkulationsprogramm.repository.EmailSucheRepository;
import org.example.kalkulationsprogramm.service.EmailGelesenService;
import org.example.kalkulationsprogramm.service.GelesenStand;
import org.example.kalkulationsprogramm.service.PostfachSichtbarkeit;
import org.example.kalkulationsprogramm.service.PostfachSichtbarkeitService;
import org.example.kalkulationsprogramm.service.mail.liste.EmailListenDtoMapper;
import org.example.kalkulationsprogramm.service.mail.liste.SteuerberaterOrdner;
import org.example.kalkulationsprogramm.util.LikeMuster;
import org.example.kalkulationsprogramm.util.suche.EmailSuchNormalisierung;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * E-Mail-Suche über alle Ordner oder in einem Ordner ({@code GET /api/emails/suche}).
 *
 * <p>Jedes Wort der Eingabe muss irgendwo im Suchtext der Mail vorkommen (Absender, Empfänger, CC,
 * Betreff, Text samt Verlauf, HTML-Text, Anhang-Dateinamen – siehe {@link EmailSuchtext}).
 * Groß-/Kleinschreibung und Umlaut-Schreibweisen sind egal ({@link EmailSuchNormalisierung}).
 * Ordner, Sichtbarkeit der Postfächer und Seiten wirken in der Datenbank-Abfrage; geladen werden
 * nur die Mails der Seite.</p>
 */
@Service
@RequiredArgsConstructor
public class EmailSucheService {

    static final int MIN_ZEICHEN = 2;
    static final int MAX_ZEICHEN = 200;
    static final int MAX_SEITENGROESSE = 50;

    private final EmailSucheRepository sucheRepository;
    private final PostfachSichtbarkeitService postfachSichtbarkeitService;
    private final EmailGelesenService emailGelesenService;
    private final SteuerberaterOrdner steuerberaterOrdner;
    private final EmailListenDtoMapper mapper;

    /**
     * Eine Trefferseite.
     *
     * @param q      Suchbegriff, getrimmt 2–200 Zeichen, höchstens 10 Wörter zählen
     * @param ordner Ordner-Schlüssel ({@link EmailOrdner#schluessel()}), leer = alle
     * @throws ResponseStatusException 400 bei zu kurzem/langem Begriff oder unbekanntem Ordner
     */
    @Transactional(readOnly = true)
    public EmailSuchErgebnis suche(String q, String ordner, int page, int size, Authentication authentication) {
        String begriff = gepruefterBegriff(q);
        EmailOrdner gewaehlt = gepruefterOrdner(ordner);
        int seite = Math.max(page, 0);
        int groesse = Math.min(Math.max(size, 1), MAX_SEITENGROESSE);
        List<String> woerter = EmailSuchNormalisierung.suchwoerter(begriff).stream()
                .map(EmailSucheService::muster)
                .toList();
        long offset = (long) seite * groesse;
        if (woerter.isEmpty() || offset > Integer.MAX_VALUE - groesse) {
            return new EmailSuchErgebnis(List.of(), false, seite, groesse);
        }

        List<Long> ids = sucheRepository.sucheIds(new EmailSuchAbfrage(woerter, gewaehlt,
                sichtbarePostfaecher(authentication), steuerberaterDomains(gewaehlt), (int) offset, groesse + 1));
        boolean hatMehr = ids.size() > groesse;
        List<Long> seitenIds = hatMehr ? ids.subList(0, groesse) : ids;
        return new EmailSuchErgebnis(dtos(seitenIds, emailGelesenService.fuer(authentication)), hatMehr, seite,
                groesse);
    }

    private static String gepruefterBegriff(String q) {
        String begriff = q == null ? "" : q.strip();
        if (begriff.length() < MIN_ZEICHEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte mindestens 2 Zeichen eingeben.");
        }
        if (begriff.length() > MAX_ZEICHEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Der Suchbegriff ist zu lang (höchstens 200 Zeichen).");
        }
        return begriff;
    }

    private static EmailOrdner gepruefterOrdner(String ordner) {
        if (ordner == null || ordner.isBlank()) {
            return EmailOrdner.ALLE;
        }
        return EmailOrdner.vonSchluessel(ordner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unbekannter Ordner."));
    }

    private static String muster(String wort) {
        return "%" + LikeMuster.escape(wort) + "%";
    }

    /** {@code null} = keine Einschränkung (Admin). */
    private Set<Long> sichtbarePostfaecher(Authentication authentication) {
        PostfachSichtbarkeit sicht = postfachSichtbarkeitService.fuer(authentication);
        return sicht == null || sicht.alles() ? null : sicht.sichtbarePostfachIds();
    }

    private Set<String> steuerberaterDomains(EmailOrdner ordner) {
        return ordner == EmailOrdner.TAX_ADVISORS ? steuerberaterOrdner.domains() : Set.of();
    }

    /** Mails der Seite in einer Abfrage laden, in Trefferreihenfolge abbilden. */
    private List<UnifiedEmailDto> dtos(List<Long> ids, GelesenStand gelesen) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Email> geladen = sucheRepository.findSeite(ids).stream()
                .collect(Collectors.toMap(Email::getId, Function.identity(), (a, b) -> a));
        return ids.stream()
                .map(geladen::get)
                .filter(Objects::nonNull)
                .map(email -> mapper.listenDto(email, gelesen))
                .toList();
    }
}
