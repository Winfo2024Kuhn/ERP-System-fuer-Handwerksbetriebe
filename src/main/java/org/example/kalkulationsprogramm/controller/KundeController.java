package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.dto.Kunde.KundeCreateRequestDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeDetailDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeListItemDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeSearchResponseDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeUpdateRequestDto;
import org.example.kalkulationsprogramm.domain.Anrede;
import org.example.kalkulationsprogramm.event.EmailAddressChangedEvent;
import org.example.kalkulationsprogramm.mapper.KundeMapper;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.service.KundenDetailService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.ListJoin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/kunden")
@RequiredArgsConstructor
public class KundeController {
    private final KundeRepository kundeRepository;
    private final KundeMapper kundeMapper;
    private final KundenDetailService kundenDetailService;
    private final ApplicationEventPublisher eventPublisher;

    @GetMapping
    public KundeSearchResponseDto sucheKunden(@RequestParam(value = "q", required = false) String query,
                                              @RequestParam(value = "name", required = false) String name,
                                              @RequestParam(value = "nummer", required = false) String kundennummer,
                                              @RequestParam(value = "ort", required = false) String ort,
                                              @RequestParam(value = "email", required = false) String email,
                                              @RequestParam(value = "typ", required = false) String typ,
                                              @RequestParam(value = "page", defaultValue = "0") int page,
                                              @RequestParam(value = "size", defaultValue = "50") int size) {
        int pageIndex = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), 50);

        Specification<Kunde> specs = Specification.where(null);
        if (StringUtils.hasText(query)) {
            final String likeValue = wrapLike(query);
            specs = specs.and((root, cq, cb) -> {
                String term = likeValue.toLowerCase(Locale.GERMAN);
                return cb.or(
                        cb.like(cb.lower(root.get("name")), term),
                        cb.like(cb.lower(root.get("kundennummer")), term),
                        cb.like(cb.lower(root.get("ort")), term),
                        cb.like(cb.lower(root.get("strasse")), term),
                        cb.like(cb.lower(root.get("ansprechspartner")), term)
                );
            });
        }
        specs = specs.and(buildSpec("name", name));
        specs = specs.and(buildSpec("kundennummer", kundennummer));
        specs = specs.and(buildSpec("ort", ort));
        if (StringUtils.hasText(email)) {
            final String likeValue = wrapLike(email);
            specs = specs.and((root, cq, cb) -> {
                cq.distinct(true);
                ListJoin<Kunde, String> join = root.joinList("kundenEmails", JoinType.LEFT);
                return cb.like(cb.lower(join), likeValue.toLowerCase(Locale.GERMAN));
            });
        }
        if (StringUtils.hasText(typ)) {
            if ("KUNDE".equalsIgnoreCase(typ)) {
                specs = specs.and((root, cq, cb) -> {
                    cq.distinct(true);
                    return cb.isNotEmpty(root.get("projekts"));
                });
            } else if ("ANFRAGER".equalsIgnoreCase(typ)) {
                specs = specs.and((root, cq, cb) -> {
                    cq.distinct(true);
                    return cb.isEmpty(root.get("projekts"));
                });
            }
        }

        Page<Kunde> result = kundeRepository.findAll(
                specs,
                PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Order.asc("name").ignoreCase()))
        );

        KundeSearchResponseDto response = new KundeSearchResponseDto();
        response.setKunden(result.stream().map(kundeMapper::toListItem).toList());
        response.setGesamt(result.getTotalElements());
        response.setSeite(pageIndex);
        response.setSeitenGroesse(pageSize);
        return response;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public KundeListItemDto createKunde(@Valid @RequestBody KundeCreateRequestDto request) {
        if (!StringUtils.hasText(request.getKundennummer())) {
            request.setKundennummer(generateNextKundennummer());
        }

        final String kundennummer = request.getKundennummer().trim();
        if (kundeRepository.findByKundennummerIgnoreCase(kundennummer).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Kundennummer ist bereits vergeben.");
        }

        Kunde kunde = new Kunde();
        applyRequest(kunde, request);

        Kunde saved = kundeRepository.save(kunde);
        
        // Trigger email backfill for new Kunde
        if (saved.getKundenEmails() != null && !saved.getKundenEmails().isEmpty()) {
            eventPublisher.publishEvent(EmailAddressChangedEvent.forNewEntity(
                    EmailAddressChangedEvent.EntityType.KUNDE,
                    saved.getId(),
                    new ArrayList<>(saved.getKundenEmails())));
        }
        
        return kundeMapper.toListItem(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public KundeListItemDto updateKunde(@PathVariable Long id,
                                        @Valid @RequestBody KundeUpdateRequestDto request) {
        Kunde existing = kundeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kunde wurde nicht gefunden."));
        
        // Speichere alte E-Mails für Vergleich
        Set<String> oldEmails = existing.getKundenEmails() != null 
                ? new HashSet<>(existing.getKundenEmails()) 
                : new HashSet<>();
        
        if (StringUtils.hasText(request.getKundennummer())) {
            final String neueNummer = request.getKundennummer().trim();
            kundeRepository.findByKundennummerIgnoreCase(neueNummer)
                    .filter(k -> !k.getId().equals(existing.getId()))
                    .ifPresent(k -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Kundennummer ist bereits vergeben.");
                    });
        }
        
        applyRequest(existing, request);
        Kunde updated = kundeRepository.save(existing);
        
        // Finde neu hinzugefügte E-Mail-Adressen
        List<String> newEmails = new ArrayList<>();
        if (updated.getKundenEmails() != null) {
            for (String email : updated.getKundenEmails()) {
                if (!oldEmails.contains(email)) {
                    newEmails.add(email);
                }
            }
        }
        
        // Trigger email backfill für neue Adressen
        if (!newEmails.isEmpty()) {
            eventPublisher.publishEvent(EmailAddressChangedEvent.forAddressChange(
                    EmailAddressChangedEvent.EntityType.KUNDE,
                    updated.getId(),
                    newEmails,
                    new ArrayList<>(updated.getKundenEmails())));
        }
        
        return kundeMapper.toListItem(updated);
    }

    @GetMapping("/next-kundennummer")
    public java.util.Map<String, String> getNextKundennummer() {
        return java.util.Map.of("kundennummer", generateNextKundennummer());
    }

    @GetMapping("/{id}")
    public KundeDetailDto getKundeDetail(@PathVariable Long id) {
        return kundenDetailService.loadDetails(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kunde wurde nicht gefunden."));
    }

    private Specification<Kunde> buildSpec(String field, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        final String likeValue = wrapLike(value);
        return (root, cq, cb) -> cb.like(cb.lower(root.get(field)), likeValue.toLowerCase(Locale.GERMAN));
    }

    private String wrapLike(String value) {
        return "%" + value.trim() + "%";
    }

    private String generateNextKundennummer() {
        return kundeRepository.findMaxKundennummer()
                .map(max -> {
                    try {
                        long val = Long.parseLong(max);
                        return String.valueOf(val + 1);
                    } catch (NumberFormatException e) {
                        return "1000";
                    }
                })
                .orElse("1000");
    }

    private void applyRequest(Kunde kunde, KundeCreateRequestDto request) {
        if (StringUtils.hasText(request.getKundennummer())) {
            kunde.setKundennummer(request.getKundennummer().trim());
        }
        kunde.setName(request.getName().trim());
        kunde.setAnrede(Anrede.fromString(request.getAnrede()));
        kunde.setAnsprechspartner(trimToNull(request.getAnsprechspartner()));
        kunde.setStrasse(trimToNull(request.getStrasse()));
        kunde.setPlz(trimToNull(request.getPlz()));
        kunde.setOrt(trimToNull(request.getOrt()));
        kunde.setTelefon(trimToNull(request.getTelefon()));
        kunde.setMobiltelefon(trimToNull(request.getMobiltelefon()));
        kunde.setKundenEmails(new ArrayList<>(normalizeEmails(request.getKundenEmails())));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * Fügt eine einzelne E-Mail-Adresse zum Kunden hinzu.
     */
    @PostMapping("/{id}/emails")
    @Transactional
    public org.springframework.http.ResponseEntity<java.util.Map<String, Object>> addEmail(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body) {
        String email = body.get("email");
        if (!StringUtils.hasText(email)) {
            return org.springframework.http.ResponseEntity.badRequest()
                    .body(java.util.Map.of("error", "E-Mail-Adresse fehlt"));
        }
        email = email.trim().toLowerCase(Locale.GERMAN);
        Kunde kunde = kundeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kunde nicht gefunden"));
        if (kunde.getKundenEmails() == null) {
            kunde.setKundenEmails(new ArrayList<>());
        }
        if (kunde.getKundenEmails().contains(email)) {
            return org.springframework.http.ResponseEntity.ok(
                    java.util.Map.of("message", "E-Mail-Adresse bereits vorhanden", "added", false));
        }
        kunde.getKundenEmails().add(email);
        kundeRepository.save(kunde);
        eventPublisher.publishEvent(EmailAddressChangedEvent.forAddressChange(
                EmailAddressChangedEvent.EntityType.KUNDE,
                kunde.getId(),
                List.of(email),
                new ArrayList<>(kunde.getKundenEmails())));
        return org.springframework.http.ResponseEntity.ok(
                java.util.Map.of("message", "E-Mail-Adresse gespeichert", "added", true));
    }

    private Set<String> normalizeEmails(java.util.List<String> emails) {
        if (emails == null) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String email : emails) {
            if (!StringUtils.hasText(email)) {
                continue;
            }
            normalized.add(email.trim().toLowerCase(Locale.GERMAN));
        }
        return normalized;
    }
}
