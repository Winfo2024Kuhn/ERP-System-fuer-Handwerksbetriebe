package org.example.kalkulationsprogramm.service;

import java.util.Optional;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.config.MobilePrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Ermittelt den Mitarbeiter hinter dem angemeldeten PC-Benutzer.
 *
 * <p>GoBD-Protokolle der Zeiterfassung (Zeitbuchungen, Zeitkonto-Korrekturen) brauchen
 * einen Mitarbeiter als Bearbeiter. Der kommt immer aus der Anmeldung, nie aus dem
 * Request – sonst könnte jeder einen fremden Namen ins Protokoll schreiben. Ist dem
 * Benutzer kein Mitarbeiter zugeordnet, wird die Aktion abgelehnt statt jemand
 * anderem zugeschrieben.</p>
 */
@Service
@RequiredArgsConstructor
public class AngemeldeterMitarbeiterService {

    public static final String NICHT_ANGEMELDET = "Bitte melden Sie sich an.";
    public static final String KEIN_MITARBEITER = "Ihr Benutzer ist keinem Mitarbeiter zugeordnet. "
            + "Bitte einen Admin bitten, das oben rechts unter „Benutzer verwalten“ nachzuholen – "
            + "sonst kann das nicht mit Ihrem Namen gespeichert werden.";

    private final FrontendUserProfileRepository profilRepository;
    private final MitarbeiterRepository mitarbeiterRepository;

    /**
     * Bearbeiter für das Protokoll: der dem angemeldeten Benutzer zugeordnete Mitarbeiter.
     *
     * @throws ResponseStatusException 401 ohne Anmeldung (oder wenn das Profil inzwischen
     *                                 gelöscht ist), 403 ohne zugeordneten Mitarbeiter
     */
    @Transactional(readOnly = true)
    public Mitarbeiter bearbeiter(Authentication authentication) {
        FrontendUserProfile profil = profil(authentication).orElse(null);
        if (profil == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, NICHT_ANGEMELDET);
        }
        if (profil.getMitarbeiter() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, KEIN_MITARBEITER);
        }
        return profil.getMitarbeiter();
    }

    /**
     * Wer gerade handelt – für Hochlader und Notiz-Verfasser, wo eine fehlende Zuordnung
     * nichts blockieren soll: am PC der dem Benutzer zugeordnete Mitarbeiter, in der
     * Handy-App der Mitarbeiter zum Login-Token. Nie ein Wert aus dem Request.
     *
     * @return leer ohne Anmeldung oder ohne zugeordneten Mitarbeiter
     */
    @Transactional(readOnly = true)
    public Optional<Mitarbeiter> handelnderMitarbeiter(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof MobilePrincipal mobil) {
            return mitarbeiterRepository.findById(mobil.mitarbeiterId());
        }
        return profil(authentication).map(FrontendUserProfile::getMitarbeiter);
    }

    private Optional<FrontendUserProfile> profil(Authentication authentication) {
        Long profilId = FrontendUserPrincipal.idAus(authentication);
        return profilId == null ? Optional.empty() : profilRepository.findById(profilId);
    }
}
