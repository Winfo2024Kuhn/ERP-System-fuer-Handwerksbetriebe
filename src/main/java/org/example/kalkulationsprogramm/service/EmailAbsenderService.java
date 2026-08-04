package org.example.kalkulationsprogramm.service;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.dto.EmailAbsenderDto;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Verwaltet die konfigurierbaren Absender-E-Mail-Adressen, die im FirmaEditor
 * gepflegt und einzelnen FrontendUserProfile-Eintraegen zugewiesen werden.
 */
@Service
@RequiredArgsConstructor
public class EmailAbsenderService {

    private final EmailAbsenderRepository repository;

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
