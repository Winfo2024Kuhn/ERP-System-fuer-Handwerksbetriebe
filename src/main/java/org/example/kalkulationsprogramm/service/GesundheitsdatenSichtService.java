package org.example.kalkulationsprogramm.service;

import java.util.Optional;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.config.MobilePrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Wer darf den Grund einer Abwesenheit sehen, wenn er „Krankheit" lautet?
 *
 * <p>Gesundheitsdaten sind besonders geschützt (DSGVO Art. 9). Am PC sieht den
 * Grund, wer die Zeiterfassung sehen darf ({@link ZeiterfassungSichtbarkeitService}:
 * Admin, Geschäftsführung, Abteilungsrecht „Zeiterfassung im Menü sehen“). In der
 * Handy-App nur die Geschäftsführung. Alle anderen sehen Kollegen nur als
 * „abwesend“; die eigenen Einträge sieht jeder im Klartext (Nutzerentscheidungen
 * vom 13.10.2026, Issue #268).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GesundheitsdatenSichtService {

    private final FrontendUserProfileRepository profileRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final ZeiterfassungSichtbarkeitService zeiterfassungSichtbarkeitService;

    /** Sicht des angemeldeten Benutzers; {@code eigeneMitarbeiterId} ist null, wenn kein Mitarbeiter verknüpft ist. */
    public record Sicht(boolean darfAlleGruendeSehen, Long eigeneMitarbeiterId) {
        public boolean darfGrundSehen(Long mitarbeiterId) {
            return darfAlleGruendeSehen
                    || (eigeneMitarbeiterId != null && eigeneMitarbeiterId.equals(mitarbeiterId));
        }
    }

    public Sicht sichtFuer(Authentication authentication) {
        if (authentication == null) {
            return new Sicht(false, null);
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof MobilePrincipal mobile) {
            boolean gf = mitarbeiterRepository.findById(mobile.mitarbeiterId())
                    .map(GesundheitsdatenSichtService::istGeschaeftsfuehrer)
                    .orElse(false);
            return new Sicht(gf, mobile.mitarbeiterId());
        }
        if (principal instanceof FrontendUserPrincipal frontend) {
            // Am PC sieht Krankheit, wer die Zeiterfassung sehen darf (Admin, Geschäftsführung,
            // Abteilungsrecht „Zeiterfassung im Menü sehen“) – Nutzerentscheidung 13.10.2026.
            boolean darf = zeiterfassungSichtbarkeitService.darfZeiterfassungSehen(authentication);
            if (frontend.hasRole(FrontendUserRole.ADMIN)) {
                return new Sicht(darf, null);
            }
            Optional<Mitarbeiter> eigener = frontend.getId() == null ? Optional.empty()
                    : profileRepository.findById(frontend.getId()).map(p -> p.getMitarbeiter());
            return new Sicht(darf, eigener.map(Mitarbeiter::getId).orElse(null));
        }
        return new Sicht(false, null);
    }

    private static boolean istGeschaeftsfuehrer(Mitarbeiter m) {
        return Boolean.TRUE.equals(m.getIstGeschaeftsfuehrer());
    }
}
