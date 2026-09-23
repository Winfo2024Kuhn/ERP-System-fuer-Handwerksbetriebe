package org.example.kalkulationsprogramm.service.einkauf;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.einkauf.LieferantEinkaufKontakt;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto.Kontakt;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto.KontaktZweck;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto.Snapshot;
import org.example.kalkulationsprogramm.repository.LieferantEinkaufKontaktRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LieferantEinkaufKontaktService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+$");

    private final LieferantEinkaufKontaktRepository repository;
    private final EinkaufAuditService auditService;
    private final ObjectMapper objectMapper;

    public List<Kontakt> liste(Long lieferantId) {
        repository.findLieferantById(lieferantId)
                .orElseThrow(() -> new NoSuchElementException("Lieferant nicht gefunden."));
        return repository.findByLieferantIdOrderById(lieferantId).stream().map(this::toDto).toList();
    }

    @Transactional
    public Kontakt speichern(Long lieferantId, Kontakt request, Long akteurId) {
        Lieferanten lieferant = ladeGesperrtenLieferanten(lieferantId);
        validiere(request);
        LieferantEinkaufKontakt entity;
        Kontakt vorher = null;
        if (request.id() == null) {
            if (request.version() != 0) throw new IllegalArgumentException("Ein neuer Kontakt darf keine Version enthalten.");
            entity = new LieferantEinkaufKontakt();
            entity.setLieferant(lieferant);
        } else {
            entity = repository.findByIdAndLieferantId(request.id(), lieferantId)
                    .orElseThrow(() -> new NoSuchElementException("Einkaufskontakt nicht gefunden."));
            if (entity.getVersion() == null || entity.getVersion() != request.version()) {
                throw new IllegalStateException("Der Kontakt wurde zwischenzeitlich geändert. Bitte neu laden.");
            }
            vorher = toDto(entity);
        }

        entity.setName(trimOrNull(request.name()));
        entity.setAnrede(trimOrNull(request.anrede()));
        entity.setEmail(request.email().trim());
        entity.setAktiv(request.aktiv());
        entity.setStandardAnfrage(request.aktiv() && request.standardAnfrage());
        entity.setStandardBestellung(request.aktiv() && request.standardBestellung());
        if (entity.isStandardAnfrage()) clearOtherStandard(lieferantId, entity, KontaktZweck.ANFRAGE, akteurId);
        if (entity.isStandardBestellung()) clearOtherStandard(lieferantId, entity, KontaktZweck.BESTELLUNG, akteurId);

        entity = repository.saveAndFlush(entity);
        auditService.protokolliere("LIEFERANT_EINKAUF_KONTAKT", entity.getId(),
                vorher == null ? "KONTAKT_ANGELEGT" : "KONTAKT_GEAENDERT", akteurId,
                vorher == null ? null : objectMapper.valueToTree(vorher), objectMapper.valueToTree(toDto(entity)), null);
        return toDto(entity);
    }

    @Transactional
    public void deaktivieren(Long lieferantId, Long kontaktId, long version, Long akteurId) {
        Kontakt aktueller = repository.findByIdAndLieferantId(kontaktId, lieferantId)
                .map(this::toDto).orElseThrow(() -> new NoSuchElementException("Einkaufskontakt nicht gefunden."));
        speichern(lieferantId, new Kontakt(aktueller.id(), version, aktueller.name(), aktueller.anrede(),
                aktueller.email(), false, false, false), akteurId);
    }

    public Snapshot snapshot(Long lieferantId, Long kontaktId, String emailOverride, KontaktZweck zweck) {
        if (zweck == null) throw new IllegalArgumentException("Der Kontaktzweck fehlt.");
        Lieferanten lieferant = repository.findLieferantByIdForUpdate(lieferantId)
                .orElseThrow(() -> new NoSuchElementException("Lieferant nicht gefunden."));
        LieferantEinkaufKontakt kontakt;
        if (kontaktId != null) {
            kontakt = repository.findByIdAndLieferantId(kontaktId, lieferantId)
                    .filter(LieferantEinkaufKontakt::isAktiv)
                    .orElseThrow(() -> new IllegalArgumentException("Der Kontakt gehört nicht zu diesem Lieferanten oder ist deaktiviert."));
            if (kontakt.getLieferant() == null || !lieferantId.equals(kontakt.getLieferant().getId())) {
                throw new IllegalArgumentException("Der Kontakt gehört nicht zu diesem Lieferanten.");
            }
        } else {
            List<LieferantEinkaufKontakt> aktive = repository.findByLieferantIdAndAktivTrueOrderById(lieferantId);
            kontakt = aktive.stream().filter(k -> zweck == KontaktZweck.ANFRAGE ? k.isStandardAnfrage() : k.isStandardBestellung())
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Für diesen Zweck ist kein Standardkontakt festgelegt."));
        }
        String email = emailOverride == null || emailOverride.isBlank() ? kontakt.getEmail() : emailOverride.trim();
        pruefeEmail(email);
        return new Snapshot(lieferant.getId(), kontakt.getId(), lieferant.getLieferantenname(), email,
                kontakt.getName(), kontakt.getAnrede(), lieferant.getEigeneKundennummer());
    }

    private Lieferanten ladeGesperrtenLieferanten(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("Die Lieferanten-ID ist ungültig.");
        return repository.findLieferantByIdForUpdate(id).orElseThrow(() -> new NoSuchElementException("Lieferant nicht gefunden."));
    }

    private void clearOtherStandard(Long lieferantId, LieferantEinkaufKontakt selected, KontaktZweck zweck, Long akteurId) {
        for (LieferantEinkaufKontakt other : repository.findByLieferantIdAndAktivTrueOrderById(lieferantId)) {
            if (!other.getId().equals(selected.getId())) {
                Kontakt vorher = toDto(other);
                if (zweck == KontaktZweck.ANFRAGE) other.setStandardAnfrage(false);
                else other.setStandardBestellung(false);
                other = repository.saveAndFlush(other);
                auditService.protokolliere("LIEFERANT_EINKAUF_KONTAKT", other.getId(), "STANDARDKONTAKT_ENTFERNT",
                        akteurId, objectMapper.valueToTree(vorher), objectMapper.valueToTree(toDto(other)), null);
            }
        }
    }

    private void validiere(Kontakt request) {
        if (request == null) throw new IllegalArgumentException("Der Kontakt fehlt.");
        if (request.name() != null && request.name().length() > 160) throw new IllegalArgumentException("Der Name darf höchstens 160 Zeichen enthalten.");
        if (request.anrede() != null && request.anrede().length() > 40) throw new IllegalArgumentException("Die Anrede darf höchstens 40 Zeichen enthalten.");
        pruefeEmail(request.email());
    }

    private void pruefeEmail(String email) {
        if (email == null || email.length() > 254 || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Bitte eine gültige E-Mail-Adresse ohne zusätzliche Kopfzeilen eingeben.");
        }
    }

    private Kontakt toDto(LieferantEinkaufKontakt k) {
        return new Kontakt(k.getId(), k.getVersion() == null ? 0 : k.getVersion(), k.getName(), k.getAnrede(),
                k.getEmail(), k.isStandardAnfrage(), k.isStandardBestellung(), k.isAktiv());
    }

    private String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
