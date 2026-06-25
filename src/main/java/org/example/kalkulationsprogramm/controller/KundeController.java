package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.KundeNotiz;
import org.example.kalkulationsprogramm.dto.Kunde.KundeCreateRequestDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeDetailDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeNotizDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeDuplikatResponseDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeListItemDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeSearchResponseDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeUpdateRequestDto;
import org.example.kalkulationsprogramm.domain.Anrede;
import org.example.kalkulationsprogramm.event.EmailAddressChangedEvent;
import org.example.kalkulationsprogramm.mapper.KundeMapper;
import org.example.kalkulationsprogramm.repository.KundeNotizRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.service.KundeDuplikatService;
import org.example.kalkulationsprogramm.service.KundenDetailService;
import org.example.kalkulationsprogramm.service.KundennummerService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/kunden")
@RequiredArgsConstructor
public class KundeController {
    private final KundeRepository kundeRepository;
    private final KundeMapper kundeMapper;
    private final KundenDetailService kundenDetailService;
    private final ApplicationEventPublisher eventPublisher;
    private final KundennummerService kundennummerService;
    private final KundeDuplikatService kundeDuplikatService;
    private final KundeNotizRepository kundeNotizRepository;

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
                        cb.like(cb.lower(root.get("ansprechspartner")), term),
                        cb.like(cb.lower(cb.coalesce(root.get("telefon"), "")), term),
                        cb.like(cb.lower(cb.coalesce(root.get("mobiltelefon"), "")), term)
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

    /**
     * Live-Check beim Anlegen: liefert mögliche Duplikate (E-Mail, Telefon, Name+PLZ, …).
     * Wird vom Frontend debounced bei Feld-Eingabe aufgerufen, blockt nicht.
     */
    @GetMapping("/duplikat-check")
    public KundeDuplikatResponseDto duplikatCheck(@RequestParam(required = false) String email,
                                                  @RequestParam(required = false) String telefon,
                                                  @RequestParam(required = false) String mobiltelefon,
                                                  @RequestParam(required = false) String name,
                                                  @RequestParam(required = false) String plz,
                                                  @RequestParam(required = false) String strasse) {
        return kundeDuplikatService.findeDuplikate(email, telefon, mobiltelefon, name, plz, strasse);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public KundeListItemDto createKunde(@Valid @RequestBody KundeCreateRequestDto request,
                                        @RequestHeader(value = "X-Duplikat-Bestaetigt", required = false) String duplikatBestaetigt) {
        // Duplikat-Check: nur wenn der User noch nicht ausdrücklich bestätigt hat
        if (!"true".equalsIgnoreCase(duplikatBestaetigt)) {
            String erstEmail = (request.getKundenEmails() != null && !request.getKundenEmails().isEmpty())
                    ? request.getKundenEmails().get(0) : null;
            KundeDuplikatResponseDto duplikate = kundeDuplikatService.findeDuplikate(
                    erstEmail,
                    request.getTelefon(),
                    request.getMobiltelefon(),
                    request.getName(),
                    request.getPlz(),
                    request.getStrasse());
            if (duplikate.getDuplikate() != null && !duplikate.getDuplikate().isEmpty()) {
                throw new KundeDuplikatException(duplikate);
            }
        }

        if (!StringUtils.hasText(request.getKundennummer())) {
            request.setKundennummer(kundennummerService.reserviereNaechsteKundennummer());
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

    /**
     * Übersetzt einen Duplikat-Verdacht in HTTP 409 mit Treffer-Liste im Body.
     * Frontend kann anhand des Body den Bestätigungs-Modal anzeigen und ggf.
     * das POST mit Header {@code X-Duplikat-Bestaetigt: true} wiederholen.
     */
    @ExceptionHandler(KundeDuplikatException.class)
    public ResponseEntity<KundeDuplikatResponseDto> handleDuplikat(KundeDuplikatException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getAntwort());
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
        return kundennummerService.generiereNaechsteKundennummer();
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

    // ==================== NOTIZEN ENDPOINTS ====================

    @GetMapping("/{id}/notizen")
    public ResponseEntity<List<KundeNotizDto>> listNotizen(
            @PathVariable Long id,
            @RequestParam(value = "q", required = false) String query) {
        if (!kundeRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        List<KundeNotiz> notizen;
        if (StringUtils.hasText(query)) {
            notizen = kundeNotizRepository.findByKundeIdAndTextContainingIgnoreCaseOrderByErstelltAmDesc(id, query);
        } else {
            notizen = kundeNotizRepository.findByKundeIdOrderByErstelltAmDesc(id);
        }
        List<KundeNotizDto> dtos = notizen.stream().map(this::toNotizDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/{id}/notizen")
    @Transactional
    public ResponseEntity<KundeNotizDto> createNotiz(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Kunde kunde = kundeRepository.findById(id).orElse(null);
        if (kunde == null) {
            return ResponseEntity.notFound().build();
        }
        String text = body.get("text");
        if (!StringUtils.hasText(text)) {
            return ResponseEntity.badRequest().build();
        }
        KundeNotiz notiz = new KundeNotiz();
        notiz.setKunde(kunde);
        notiz.setText(text.trim());
        notiz = kundeNotizRepository.save(notiz);
        return ResponseEntity.status(HttpStatus.CREATED).body(toNotizDto(notiz));
    }

    @DeleteMapping("/{id}/notizen/{notizId}")
    @Transactional
    public ResponseEntity<Void> deleteNotiz(
            @PathVariable Long id,
            @PathVariable Long notizId) {
        if (!kundeRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        KundeNotiz notiz = kundeNotizRepository.findById(notizId).orElse(null);
        if (notiz == null || !notiz.getKunde().getId().equals(id)) {
            return ResponseEntity.notFound().build();
        }
        kundeNotizRepository.delete(notiz);
        return ResponseEntity.noContent().build();
    }

    private KundeNotizDto toNotizDto(KundeNotiz notiz) {
        KundeNotizDto dto = new KundeNotizDto();
        dto.setId(notiz.getId());
        dto.setText(notiz.getText());
        dto.setErstelltAm(notiz.getErstelltAm());
        return dto;
    }
}
