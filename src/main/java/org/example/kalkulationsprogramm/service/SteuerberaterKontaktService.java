package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.SteuerberaterKontakt;
import org.example.kalkulationsprogramm.dto.SteuerberaterKontaktDto;
import org.example.kalkulationsprogramm.repository.SteuerberaterKontaktRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SteuerberaterKontaktService {

    private final SteuerberaterKontaktRepository repository;

    @Transactional(readOnly = true)
    public List<SteuerberaterKontaktDto> findAll() {
        return repository.findByAktivTrue()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SteuerberaterKontaktDto findById(Long id) {
        return repository.findById(id)
                .map(this::toDto)
                .orElse(null);
    }

    @Transactional
    public SteuerberaterKontaktDto speichern(SteuerberaterKontaktDto dto) {
        SteuerberaterKontakt sk;
        if (dto.getId() != null) {
            sk = repository.findById(dto.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Steuerberater nicht gefunden: " + dto.getId()));
        } else {
            sk = new SteuerberaterKontakt();
        }

        sk.setName(dto.getName());
        sk.setEmail(dto.getEmail());
        sk.setTelefon(dto.getTelefon());
        sk.setAnsprechpartner(dto.getAnsprechpartner());
        sk.setAutoProcessEmails(dto.getAutoProcessEmails() != null ? dto.getAutoProcessEmails() : true);
        sk.setAktiv(dto.getAktiv() != null ? dto.getAktiv() : true);
        sk.setNotizen(dto.getNotizen());
        sk.setGueltigAb(dto.getGueltigAb());
        sk.setGueltigBis(dto.getGueltigBis());
        if (dto.getWeitereEmails() != null) {
            sk.setWeitereEmails(new java.util.HashSet<>(dto.getWeitereEmails()));
        } else {
            sk.getWeitereEmails().clear();
        }

        // Validate Validation
        validateOverlap(sk);

        sk = repository.save(sk);
        return toDto(sk);
    }

    private void validateOverlap(SteuerberaterKontakt current) {
        if (current.getGueltigAb() == null) return; // Should likely be required, but optional for legacy
        
        List<SteuerberaterKontakt> all = repository.findByAktivTrue();
        
        for (SteuerberaterKontakt other : all) {
            if (other.getId().equals(current.getId())) continue;
            // Skip if other has no dates (legacy assumed open?) or handled otherwise
            if (other.getGueltigAb() == null) continue; 
            
            // Check overlap
            // Overlap if (StartA <= EndB) and (EndA >= StartB)
            // Use Max date if null (open end)
            java.time.LocalDate startA = current.getGueltigAb();
            java.time.LocalDate endA = current.getGueltigBis() != null ? current.getGueltigBis() : java.time.LocalDate.MAX;
            
            java.time.LocalDate startB = other.getGueltigAb();
            java.time.LocalDate endB = other.getGueltigBis() != null ? other.getGueltigBis() : java.time.LocalDate.MAX;

            if (!startA.isAfter(endB) && !endA.isBefore(startB)) {
                throw new IllegalArgumentException("Zeitraum überschneidet sich mit Steuerberater: " + other.getName());
            }
        }
    }

    @Transactional
    public void loeschen(Long id) {
        repository.findById(id).ifPresent(sk -> {
            sk.setAktiv(false);
            repository.save(sk);
        });
    }

    /**
     * Prüft ob eine E-Mail-Adresse zu einem Steuerberater gehört (Haupt-EMail oder weitere).
     */
    @Transactional(readOnly = true)
    public boolean istSteuerberaterEmail(String email) {
        if (repository.existsByEmailIgnoreCaseAndAktivTrue(email)) return true;
        
        // Check manually in all active (since ElementCollection query might be complex to add to repo right now)
        // Optimization: Create a repo method later properly
        return repository.findByAktivTrue().stream()
                .anyMatch(sk -> sk.getWeitereEmails() != null && sk.getWeitereEmails().stream().anyMatch(e -> e.equalsIgnoreCase(email)));
    }

    /**
     * Findet den Steuerberater anhand der E-Mail-Adresse.
     */
    @Transactional(readOnly = true)
    public SteuerberaterKontaktDto findByEmail(String email) {
        // Try Primary
        if (repository.existsByEmailIgnoreCaseAndAktivTrue(email)) {
            return repository.findByEmailIgnoreCase(email)
                    .map(this::toDto)
                    .orElse(null);
        }
        
        // Try Secondary
        return repository.findByAktivTrue().stream()
                .filter(sk -> sk.getWeitereEmails() != null && sk.getWeitereEmails().stream().anyMatch(e -> e.equalsIgnoreCase(email)))
                .findFirst()
                .map(this::toDto)
                .orElse(null);
    }

    private SteuerberaterKontaktDto toDto(SteuerberaterKontakt sk) {
        SteuerberaterKontaktDto dto = new SteuerberaterKontaktDto();
        dto.setId(sk.getId());
        dto.setName(sk.getName());
        dto.setEmail(sk.getEmail());
        dto.setTelefon(sk.getTelefon());
        dto.setAnsprechpartner(sk.getAnsprechpartner());
        dto.setAutoProcessEmails(sk.getAutoProcessEmails());
        dto.setAktiv(sk.getAktiv());
        dto.setNotizen(sk.getNotizen());
        dto.setGueltigAb(sk.getGueltigAb());
        dto.setGueltigBis(sk.getGueltigBis());
        dto.setWeitereEmails(new java.util.ArrayList<>(sk.getWeitereEmails()));
        return dto;
    }
}
