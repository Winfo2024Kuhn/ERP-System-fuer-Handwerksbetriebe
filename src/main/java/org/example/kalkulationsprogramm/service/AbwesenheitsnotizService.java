package org.example.kalkulationsprogramm.service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.EmailSignature;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.OutOfOfficeSchedule;
import org.example.kalkulationsprogramm.dto.Abwesenheitsnotiz.AbwesenheitsPostfachDto;
import org.example.kalkulationsprogramm.dto.Abwesenheitsnotiz.AbwesenheitsnotizDto;
import org.example.kalkulationsprogramm.dto.Abwesenheitsnotiz.AbwesenheitsnotizSpeichernRequest;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.OutOfOfficeScheduleRepository;
import org.example.kalkulationsprogramm.util.EmailHtmlSanitizer;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * Abwesenheitsnotizen pro Postfach (E-Mail-Einstellungen → Abwesenheitsnotizen).
 *
 * <p>Wer welche Postfächer wählen darf:</p>
 * <ul>
 *   <li>Admins: jedes eingeschaltete Postfach.</li>
 *   <li>Alle anderen: das eigene Postfach und allgemeine Postfächer (keinem Benutzer als
 *       eigenes zugeordnet, z. B. info@), die sie im E-Mail-Center sehen.</li>
 *   <li>Nie: auslaufende Postfächer und das reine Rechnungs-Postfach (außer es ist zugleich
 *       das Hauptpostfach) – darüber wird nicht geantwortet.</li>
 * </ul>
 * <p>Eine Notiz sieht und bearbeitet nur, wer alle ihre Postfächer wählen darf. So bleibt die
 * Notiz eines Kollegen für dessen persönliches Postfach für andere unsichtbar. Der Benutzer
 * kommt ausschließlich aus der Anmeldung.</p>
 */
@Service
@RequiredArgsConstructor
public class AbwesenheitsnotizService {

    static final int MAX_TITEL = 200;
    static final int MAX_BETREFF = 300;
    static final int MAX_NACHRICHT = 100_000;
    static final String STANDARD_BETREFF = "Automatische Antwort: {{subject}}";
    private static final Pattern HTML_TAG = Pattern.compile("<\\s*+[a-zA-Z][^<>]*+>");

    private final OutOfOfficeScheduleRepository notizRepository;
    private final EmailAbsenderRepository postfachRepository;
    private final EmailSignaturVerwaltungService signaturVerwaltung;
    private final FrontendUserProfileRepository benutzerRepository;
    private final FrontendUserProfileService benutzerService;
    private final PostfachSichtbarkeitService sichtbarkeitService;

    /** Postfächer, für die der Benutzer Notizen einstellen darf – das eigene zuerst. */
    @Transactional(readOnly = true)
    public List<AbwesenheitsPostfachDto> waehlbarePostfaecher(Authentication authentication) {
        Long eigenesId = eigenesPostfachId(authentication);
        return waehlbar(authentication, eigenesId).stream()
                .map(p -> AbwesenheitsPostfachDto.von(p, p.getId().equals(eigenesId)))
                .sorted(Comparator.comparing(AbwesenheitsPostfachDto::eigenes).reversed())
                .toList();
    }

    /** Notizen, die der Benutzer bearbeiten darf – die neueste zuerst. */
    @Transactional(readOnly = true)
    public List<AbwesenheitsnotizDto> liste(Authentication authentication) {
        boolean admin = PostfachSichtbarkeitService.istAdmin(authentication);
        Set<Long> erlaubt = admin ? Set.of() : waehlbareIds(authentication);
        return notizRepository.findAllByOrderByStartAtDescIdDesc().stream()
                .filter(n -> admin || darfBearbeiten(n, erlaubt))
                .map(AbwesenheitsnotizDto::von)
                .toList();
    }

    /**
     * Legt eine Notiz an oder ändert sie.
     *
     * @throws IllegalArgumentException Angaben unvollständig oder ungültig (400)
     * @throws AccessDeniedException    Postfach nicht erlaubt oder fremde Notiz (403)
     * @throws NoSuchElementException   Notiz gibt es nicht (mehr) (404)
     */
    @Transactional
    public AbwesenheitsnotizDto speichern(AbwesenheitsnotizSpeichernRequest request, Authentication authentication) {
        if (request == null) {
            throw new IllegalArgumentException("Bitte die Abwesenheit ausfüllen.");
        }
        pruefeAngaben(request);
        boolean admin = PostfachSichtbarkeitService.istAdmin(authentication);
        Set<Long> erlaubt = admin ? Set.of() : waehlbareIds(authentication);

        OutOfOfficeSchedule notiz;
        if (request.id() == null) {
            notiz = new OutOfOfficeSchedule();
        } else {
            notiz = notizRepository.findById(request.id()).orElseThrow(AbwesenheitsnotizService::nichtGefunden);
            if (!admin && !darfBearbeiten(notiz, erlaubt)) {
                // Wie „gibt es nicht“: verrät nicht, dass ein Kollege eine Notiz hat.
                throw nichtGefunden();
            }
        }

        Set<EmailAbsender> postfaecher = gewaehltePostfaecher(request.postfachIds(), admin, erlaubt);
        notiz.setTitle(request.title().trim());
        notiz.setStartAt(request.startDate());
        notiz.setEndAt(request.endDate());
        notiz.setActive(Boolean.TRUE.equals(request.active()));
        notiz.setSubjectTemplate(StringUtils.hasText(request.subject()) ? request.subject().trim() : STANDARD_BETREFF);
        notiz.setBodyTemplate(bereinigt(request.message()));
        notiz.setSignature(signatur(request.signatureId(), notiz.getSignature(), authentication));
        notiz.getPostfaecher().clear();
        notiz.getPostfaecher().addAll(postfaecher);
        return AbwesenheitsnotizDto.von(notizRepository.save(notiz));
    }

    /**
     * Löscht eine Notiz.
     *
     * @throws NoSuchElementException Notiz gibt es nicht oder der Benutzer darf sie nicht sehen (404)
     */
    @Transactional
    public void loeschen(Long id, Authentication authentication) {
        OutOfOfficeSchedule notiz = notizRepository.findById(id).orElseThrow(AbwesenheitsnotizService::nichtGefunden);
        if (!PostfachSichtbarkeitService.istAdmin(authentication)
                && !darfBearbeiten(notiz, waehlbareIds(authentication))) {
            throw nichtGefunden();
        }
        notizRepository.delete(notiz);
    }

    // ==================== Regeln ====================

    /** Darf über dieses Postfach überhaupt automatisch geantwortet werden? */
    static boolean antwortetAutomatisch(EmailAbsender postfach) {
        return postfach.isAktiv()
                && !postfach.isLaeuftAus()
                && PostfachVersandService.istFreiWaehlbar(postfach);
    }

    /** Alle Postfächer der Notiz wählbar? Notizen ohne Postfach (Altbestand) bleiben Admins vorbehalten. */
    private static boolean darfBearbeiten(OutOfOfficeSchedule notiz, Set<Long> erlaubt) {
        return !notiz.getPostfaecher().isEmpty()
                && notiz.getPostfaecher().stream().map(EmailAbsender::getId).allMatch(erlaubt::contains);
    }

    private List<EmailAbsender> waehlbar(Authentication authentication, Long eigenesId) {
        List<EmailAbsender> kandidaten = postfachRepository.findByAktivTrueOrderBySortierungAscIdAsc().stream()
                .filter(AbwesenheitsnotizService::antwortetAutomatisch)
                .toList();
        if (PostfachSichtbarkeitService.istAdmin(authentication)) {
            return kandidaten;
        }
        PostfachSichtbarkeit sichtbarkeit = sichtbarkeitService.fuer(authentication);
        Set<Long> mitBesitzer = Set.copyOf(benutzerRepository.findEmailAbsenderIdsMitBesitzer());
        return kandidaten.stream()
                .filter(p -> p.getId().equals(eigenesId)
                        || (!mitBesitzer.contains(p.getId()) && sichtbarkeit.siehtPostfach(p)))
                .toList();
    }

    private Set<Long> waehlbareIds(Authentication authentication) {
        return waehlbar(authentication, eigenesPostfachId(authentication)).stream()
                .map(EmailAbsender::getId)
                .collect(Collectors.toSet());
    }

    private Long eigenesPostfachId(Authentication authentication) {
        return angemeldeterBenutzer(authentication)
                .map(FrontendUserProfile::getEmailAbsender)
                .map(EmailAbsender::getId)
                .orElse(null);
    }

    private Optional<FrontendUserProfile> angemeldeterBenutzer(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !StringUtils.hasText(authentication.getName())) {
            return Optional.empty();
        }
        return benutzerService.findByUsername(authentication.getName());
    }

    private Set<EmailAbsender> gewaehltePostfaecher(List<Long> ids, boolean admin, Set<Long> erlaubt) {
        Set<Long> gewaehlt = ids == null ? Set.of()
                : ids.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (gewaehlt.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens ein Postfach auswählen.");
        }
        Set<EmailAbsender> postfaecher = new LinkedHashSet<>();
        for (Long id : gewaehlt) {
            EmailAbsender postfach = postfachRepository.findById(id)
                    .filter(AbwesenheitsnotizService::antwortetAutomatisch)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Ein gewähltes Postfach gibt es nicht (mehr) oder es ist ausgeschaltet. Bitte neu auswählen."));
            if (!admin && !erlaubt.contains(postfach.getId())) {
                throw new AccessDeniedException(
                        "Abwesenheitsnotizen gehen nur für Ihr eigenes Postfach und allgemeine Postfächer.");
            }
            postfaecher.add(postfach);
        }
        return postfaecher;
    }

    /**
     * HTML-Nachricht wie beim Versand bereinigen (Skripte, Event-Handler raus), damit nichts
     * Gefährliches gespeichert und später in den Einstellungen angezeigt wird. Reiner Text bleibt.
     */
    /**
     * Enthält der Text ein HTML-Tag? Linear: {@code [^<>]} stoppt am nächsten {@code <}, die
     * Quantoren sind possessiv – kein Backtracking auch bei 100.000 Zeichen voller {@code <}.
     */
    static boolean siehtNachHtmlAus(String text) {
        return text != null && HTML_TAG.matcher(text).find();
    }

    private static String bereinigt(String nachricht) {
        if (!siehtNachHtmlAus(nachricht)) {
            return nachricht;
        }
        return EmailHtmlSanitizer.sanitizeDetailHtml(nachricht);
    }

    /**
     * Gewählte Signatur. Erlaubt sind die eigenen (Admins: alle) und die, die schon an der Notiz
     * hängt – sonst könnte niemand außer dem Admin eine Notiz eines allgemeinen Postfachs ändern.
     * Fremde Signaturen gelten als „gibt es nicht“.
     */
    private EmailSignature signatur(Long signaturId, EmailSignature bisher, Authentication authentication) {
        if (signaturId == null) {
            return null;
        }
        if (bisher != null && signaturId.equals(bisher.getId())) {
            return bisher;
        }
        return signaturVerwaltung.sichtbare(signaturId, signaturVerwaltung.benutzer(authentication))
                .orElseThrow(() -> new IllegalArgumentException("Die gewählte Signatur gibt es nicht mehr."));
    }

    private static void pruefeAngaben(AbwesenheitsnotizSpeichernRequest request) {
        if (!StringUtils.hasText(request.title())) {
            throw new IllegalArgumentException("Bitte einen Titel eingeben, z. B. „Urlaub“.");
        }
        if (request.title().trim().length() > MAX_TITEL) {
            throw new IllegalArgumentException("Der Titel ist zu lang (höchstens " + MAX_TITEL + " Zeichen).");
        }
        if (request.startDate() == null || request.endDate() == null) {
            throw new IllegalArgumentException("Bitte Beginn und Ende der Abwesenheit angeben.");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("Das Ende liegt vor dem Beginn. Bitte die Daten prüfen.");
        }
        if (request.subject() != null && request.subject().trim().length() > MAX_BETREFF) {
            throw new IllegalArgumentException("Der Betreff ist zu lang (höchstens " + MAX_BETREFF + " Zeichen).");
        }
        if (request.message() != null && request.message().length() > MAX_NACHRICHT) {
            throw new IllegalArgumentException("Die Nachricht ist zu lang. Bitte kürzer fassen.");
        }
    }

    private static NoSuchElementException nichtGefunden() {
        return new NoSuchElementException("Diese Abwesenheitsnotiz gibt es nicht mehr.");
    }
}
