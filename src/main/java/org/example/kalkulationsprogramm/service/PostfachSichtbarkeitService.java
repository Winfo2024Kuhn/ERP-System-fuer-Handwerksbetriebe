package org.example.kalkulationsprogramm.service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.EmailPostfachZuordnungRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Ermittelt, welche Postfächer und damit welche Mails ein angemeldeter Benutzer im
 * E-Mail-Center sieht.
 *
 * <p>Ein Benutzer (nicht Admin) sieht ein eingeschaltetes Postfach, wenn es</p>
 * <ul>
 *   <li>das Hauptpostfach ist (sieht immer jeder),</li>
 *   <li>das Postfach für Rechnungen und Mahnungen ist (sieht ebenfalls immer jeder),</li>
 *   <li>„für alle sichtbar“ ist,</li>
 *   <li>sein eigenes Postfach ist,</li>
 *   <li>ihm direkt freigegeben ist oder</li>
 *   <li>einer seiner Abteilungen freigegeben ist.</li>
 * </ul>
 * <p>Admins ({@code ROLE_ADMIN}) sehen alles. Der Benutzer kommt ausschließlich aus der
 * Anmeldung – nie aus Angaben des Clients. Ohne Anmeldung gibt es keinen Benutzer: dann
 * zählen nur Hauptpostfach und „für alle sichtbar“.</p>
 */
@Service
@RequiredArgsConstructor
public class PostfachSichtbarkeitService {

    static final String ADMIN_ROLLE = "ROLE_ADMIN";

    /** Platzhalter für leere IN-Listen in JPQL. */
    private static final Long KEINE_ID = -1L;

    private final EmailAbsenderRepository postfachRepository;
    private final EmailPostfachZuordnungRepository zuordnungRepository;
    private final FrontendUserProfileService frontendUserProfileService;
    private final EmailRepository emailRepository;

    /** Sichtbarkeit für den angemeldeten Benutzer. */
    @Transactional(readOnly = true)
    public PostfachSichtbarkeit fuer(Authentication authentication) {
        if (istAdmin(authentication)) {
            return PostfachSichtbarkeit.ALLES;
        }
        return fuerBenutzer(angemeldeterBenutzer(authentication).orElse(null));
    }

    /** Angemeldeter Benutzer per Benutzername aus der Anmeldung; anonym oder unbekannt = keiner. */
    private Optional<FrontendUserProfile> angemeldeterBenutzer(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || authentication.getName() == null || authentication.getName().isBlank()) {
            return Optional.empty();
        }
        return frontendUserProfileService.findByUsername(authentication.getName());
    }

    /**
     * IDs der Mails, die der Benutzer nicht sieht (liegen nur in Postfächern, die er nicht
     * sieht). Für Listen: vor dem Seitenschnitt herausfiltern, damit die Seiten stimmen.
     */
    @Transactional(readOnly = true)
    public Set<Long> verborgeneEmailIds(PostfachSichtbarkeit sichtbarkeit) {
        if (sichtbarkeit == null || sichtbarkeit.alles()) {
            return Set.of();
        }
        Set<Long> sichtbar = sichtbarkeit.sichtbarePostfachIds().isEmpty()
                ? Set.of(KEINE_ID)
                : sichtbarkeit.sichtbarePostfachIds();
        Set<Long> verborgen = zuordnungRepository.findEmailIdsNurInAnderenPostfaechern(sichtbar);
        return verborgen == null ? Set.of() : verborgen;
    }

    /**
     * Welche dieser Mails darf der Benutzer nicht öffnen (Regel wie {@link PostfachSichtbarkeit#darfLesen})?
     * Für Entwürfe, die sich auf eine Mail beziehen (Antwort, Weiterleitung). Nicht mehr vorhandene
     * Mails gelten nicht als gesperrt – sie verraten nichts.
     */
    @Transactional(readOnly = true)
    public Set<Long> nichtLesbareEmailIds(java.util.Collection<Long> emailIds, PostfachSichtbarkeit sichtbarkeit) {
        if (sichtbarkeit == null || sichtbarkeit.alles() || emailIds == null) {
            return Set.of();
        }
        Set<Long> ids = emailIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Set.of();
        }
        return emailRepository.findAllById(ids).stream()
                .filter(email -> !sichtbarkeit.darfLesen(email))
                .map(org.example.kalkulationsprogramm.domain.Email::getId)
                .collect(Collectors.toSet());
    }

    /**
     * Welche dieser Postfächer gibt es, ohne dass der Benutzer sie sieht? Für Entwürfe einer neuen
     * Mail mit gewähltem Absender (z. B. der Chef schreibt aus chef@). Postfächer, die es nicht
     * mehr gibt, zählen nicht – sie verraten nichts.
     */
    @Transactional(readOnly = true)
    public Set<Long> nichtSichtbarePostfachIds(java.util.Collection<Long> postfachIds, PostfachSichtbarkeit sichtbarkeit) {
        if (sichtbarkeit == null || sichtbarkeit.alles() || postfachIds == null) {
            return Set.of();
        }
        Set<Long> ids = postfachIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Set.of();
        }
        return postfachRepository.findAllById(ids).stream()
                .filter(postfach -> !sichtbarkeit.siehtPostfach(postfach))
                .map(EmailAbsender::getId)
                .collect(Collectors.toSet());
    }

    PostfachSichtbarkeit fuerBenutzer(FrontendUserProfile benutzer) {
        Long eigenesPostfachId = null;
        Set<Long> freigegeben = new HashSet<>();
        if (benutzer != null && benutzer.getId() != null) {
            if (benutzer.getEmailAbsender() != null) {
                eigenesPostfachId = benutzer.getEmailAbsender().getId();
            }
            freigegeben.addAll(postfachRepository.findIdsFreigegebenFuerBenutzer(benutzer.getId()));
            Set<Long> abteilungIds = abteilungIdsVon(benutzer);
            if (!abteilungIds.isEmpty()) {
                freigegeben.addAll(postfachRepository.findIdsFreigegebenFuerAbteilungen(abteilungIds));
            }
        }

        Set<Long> sichtbar = new HashSet<>();
        for (EmailAbsender postfach : postfachRepository.findByAktivTrueOrderBySortierungAscIdAsc()) {
            if (postfach.isHauptpostfach()
                    || postfach.isFuerGeschaeftsdokumente()
                    || postfach.isSichtbarFuerAlle()
                    || postfach.getId().equals(eigenesPostfachId)
                    || freigegeben.contains(postfach.getId())) {
                sichtbar.add(postfach.getId());
            }
        }
        return new PostfachSichtbarkeit(false, sichtbar);
    }

    private static Set<Long> abteilungIdsVon(FrontendUserProfile benutzer) {
        if (benutzer.getMitarbeiter() == null || benutzer.getMitarbeiter().getAbteilungen() == null) {
            return Set.of();
        }
        return benutzer.getMitarbeiter().getAbteilungen().stream()
                .map(Abteilung::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    static boolean istAdmin(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || authentication.getAuthorities() == null) {
            return false;
        }
        List<String> rollen = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return rollen.contains(ADMIN_ROLLE);
    }
}
