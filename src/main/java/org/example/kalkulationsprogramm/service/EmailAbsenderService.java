package org.example.kalkulationsprogramm.service;

import java.util.ArrayList;
import java.util.List;

import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lesezugriff auf die Absender-Adressen der Postfächer für Stellen, die nur eine
 * Adressliste brauchen (z. B. Bestellung per Mail). Anlegen, Ändern und Löschen
 * laufen über den {@link PostfachService}.
 */
@Service
public class EmailAbsenderService {

    private final EmailAbsenderRepository repository;
    private final FrontendUserProfileRepository frontendUserProfileRepository;

    @Autowired
    public EmailAbsenderService(EmailAbsenderRepository repository,
            FrontendUserProfileRepository frontendUserProfileRepository) {
        this.repository = repository;
        this.frontendUserProfileRepository = frontendUserProfileRepository;
    }

    public EmailAbsenderService(EmailAbsenderRepository repository) {
        this(repository, null);
    }

    @Transactional(readOnly = true)
    public List<String> findActiveEmailAddresses() {
        return repository.findByAktivTrueOrderBySortierungAscIdAsc().stream()
                .map(EmailAbsender::getEmailAdresse)
                .filter(s -> s != null && !s.isBlank())
                .toList();
    }

    /**
     * Liefert die konfigurierten aktiven Absender-Adressen.
     * Wird {@code frontendUserId} uebergeben, wird die dem Profil zugewiesene
     * Absender-Adresse an die erste Position der Liste gesetzt.
     */
    @Transactional(readOnly = true)
    public List<String> getPrioritizedFromAddresses(Long frontendUserId) {
        List<String> aktive = new ArrayList<>(findActiveEmailAddresses());
        if (frontendUserId != null && frontendUserProfileRepository != null) {
            String userAdresse = frontendUserProfileRepository.findById(frontendUserId)
                    .map(FrontendUserProfile::getEmailAbsender)
                    .map(EmailAbsender::getEmailAdresse)
                    .filter(s -> s != null && !s.isBlank())
                    .orElse(null);
            if (userAdresse != null) {
                aktive.removeIf(a -> a.equalsIgnoreCase(userAdresse));
                aktive.add(0, userAdresse);
            }
        }
        return aktive;
    }
}
