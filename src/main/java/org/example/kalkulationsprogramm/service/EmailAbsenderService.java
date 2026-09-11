package org.example.kalkulationsprogramm.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.dto.EmailAbsenderDto;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verwaltet die konfigurierbaren Absender-E-Mail-Adressen, die im FirmaEditor
 * gepflegt und einzelnen FrontendUserProfile-Eintraegen zugewiesen werden.
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
    public List<EmailAbsenderDto> findAll() {
        return repository.findAllByOrderBySortierungAscIdAsc().stream()
                .map(EmailAbsenderDto::fromEntity)
                .toList();
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

    @Transactional(readOnly = true)
    public Optional<EmailAbsender> findFirstActive() {
        return repository.findFirstByAktivTrueOrderBySortierungAscIdAsc();
    }

    /**
     * Anzeigename, der zu einer Absender-Adresse gepflegt ist — also der Name,
     * der beim Empfänger statt der nackten Adresse im Posteingang steht.
     *
     * <p>Der Vergleich läuft ohne Rücksicht auf Groß- und Kleinschreibung:
     * E-Mail-Adressen werden im Alltag mal so, mal so getippt, und ein
     * Anzeigename darf daran nicht scheitern.</p>
     *
     * @return leerer {@code Optional}, wenn die Adresse unbekannt ist oder kein
     *         Anzeigename hinterlegt wurde
     */
    @Transactional(readOnly = true)
    public Optional<String> findAnzeigenameFuerAdresse(String emailAdresse) {
        if (emailAdresse == null || emailAdresse.isBlank()) {
            return Optional.empty();
        }
        String gesucht = emailAdresse.trim();
        return repository.findAllByOrderBySortierungAscIdAsc().stream()
                .filter(a -> a.getEmailAdresse() != null && a.getEmailAdresse().equalsIgnoreCase(gesucht))
                .map(EmailAbsender::getAnzeigename)
                .filter(name -> name != null && !name.isBlank())
                .findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<EmailAbsender> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findById(id);
    }

    @Transactional
    public EmailAbsenderDto save(EmailAbsenderDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Daten fehlen.");
        }
        String adresse = dto.getEmailAdresse() == null ? null : dto.getEmailAdresse().trim();
        if (adresse == null || adresse.isEmpty()) {
            throw new IllegalArgumentException("E-Mail-Adresse darf nicht leer sein.");
        }
        if (!adresse.matches("^[^@\\s]+@[^@\\s.]+(?:\\.[^@\\s.]+)+$")) {
            throw new IllegalArgumentException("Ungueltige E-Mail-Adresse: " + adresse);
        }

        EmailAbsender entity;
        if (dto.getId() != null) {
            entity = repository.findById(dto.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Absender nicht gefunden: " + dto.getId()));
        } else {
            entity = new EmailAbsender();
        }

        // Eindeutigkeit der Adresse pruefen (case-insensitive), aber den eigenen
        // Eintrag nicht gegen sich selbst kollidieren lassen.
        repository.findByEmailAdresseIgnoreCase(adresse).ifPresent(existing -> {
            if (entity.getId() == null || !existing.getId().equals(entity.getId())) {
                throw new IllegalArgumentException("Diese E-Mail-Adresse ist bereits angelegt.");
            }
        });

        entity.setEmailAdresse(adresse);
        entity.setAnzeigename(dto.getAnzeigename() != null ? dto.getAnzeigename().trim() : null);
        entity.setAktiv(dto.isAktiv());
        entity.setSortierung(dto.getSortierung());

        return EmailAbsenderDto.fromEntity(repository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        if (id == null) {
            return;
        }
        repository.deleteById(id);
    }
}
