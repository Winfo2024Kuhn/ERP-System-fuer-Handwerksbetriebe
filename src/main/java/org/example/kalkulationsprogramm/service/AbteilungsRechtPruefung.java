package org.example.kalkulationsprogramm.service;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.MitarbeiterArt;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Gemeinsame Prüfung für Rechte, die in Einstellungen → Abteilungen & Rechte je Abteilung vergeben werden und die
 * Administratoren immer haben (z. B. „Aufschläge ändern“, „Kostenstellen verwalten“). Alle anderen brauchen ein aktives
 * Profil, das mit einem aktiven Mitarbeiter verknüpft ist, der in mindestens einer Abteilung mit dem Recht ist.
 * {@link #zugehoerigkeit} liefert dieselbe Auskunft als Abteilungs-IDs (z. B. für die Ordnerrechte der Firmendokumente).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AbteilungsRechtPruefung {

    private final FrontendUserProfileRepository profileRepository;

    /**
     * Wer ist angemeldet? {@code admin} = Rolle ADMIN; sonst die Abteilungen des verknüpften aktiven Mitarbeiters
     * (leer ohne Anmeldung, bei inaktivem Profil oder Mitarbeiter und für Nicht-Menschen).
     */
    public record Zugehoerigkeit(boolean admin, Set<Long> abteilungIds) {
    }

    private static final Zugehoerigkeit NIEMAND = new Zugehoerigkeit(false, Set.of());

    public boolean adminOderAbteilungDarf(Authentication authentication, Predicate<Abteilung> recht) {
        Optional<FrontendUserPrincipal> principal = angemeldet(authentication);
        if (principal.isEmpty()) {
            return false;
        }
        if (principal.get().hasRole(FrontendUserRole.ADMIN)) {
            return true;
        }
        return abteilungen(principal.get()).anyMatch(recht);
    }

    /** Admin oder die IDs der Abteilungen des Angemeldeten – für Rechte, die je Abteilung in einer eigenen Tabelle stehen. */
    public Zugehoerigkeit zugehoerigkeit(Authentication authentication) {
        Optional<FrontendUserPrincipal> principal = angemeldet(authentication);
        if (principal.isEmpty()) {
            return NIEMAND;
        }
        if (principal.get().hasRole(FrontendUserRole.ADMIN)) {
            return new Zugehoerigkeit(true, Set.of());
        }
        Set<Long> ids = abteilungen(principal.get()).map(Abteilung::getId).filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        return new Zugehoerigkeit(false, ids);
    }

    private static Optional<FrontendUserPrincipal> angemeldet(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof FrontendUserPrincipal principal) || !principal.isEnabled()) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    /** Abteilungen des aktiven Mitarbeiters hinter einem aktiven Profil, sonst keine. */
    private Stream<Abteilung> abteilungen(FrontendUserPrincipal principal) {
        if (principal.getId() == null) {
            return Stream.empty();
        }
        return profileRepository.findById(principal.getId())
                .filter(AbteilungsRechtPruefung::hatAktivenMitarbeiter)
                .map(p -> p.getMitarbeiter().getAbteilungen().stream())
                .orElseGet(Stream::empty);
    }

    private static boolean hatAktivenMitarbeiter(FrontendUserProfile p) {
        if (!p.isActive() || p.getMitarbeiter() == null) {
            return false;
        }
        var m = p.getMitarbeiter();
        return m.getId() != null && m.getArt() == MitarbeiterArt.MENSCH && Boolean.TRUE.equals(m.getAktiv());
    }
}
