package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Anrede;
import org.example.kalkulationsprogramm.domain.SteuerberaterAnsprechpartner;
import org.example.kalkulationsprogramm.domain.SteuerberaterKontakt;
import org.example.kalkulationsprogramm.dto.SteuerberaterAnsprechpartnerDto;
import org.example.kalkulationsprogramm.dto.SteuerberaterKontaktDto;
import org.example.kalkulationsprogramm.repository.SteuerberaterKontaktRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        mergeAnsprechpartner(sk, dto.getAnsprechpartnerListe());

        sk = repository.save(sk);
        return toDto(sk);
    }

    /**
     * Merge der Ansprechpartner-Liste vom DTO in die persistente Entity-Liste.
     * In-Place-Update statt Liste neu setzen, damit orphanRemoval und Cascade
     * sauber funktionieren.
     *
     * <p>Wenn die eingehende Liste mehrere oder keinen "Lohn-Ansprechpartner"
     * markiert, wird genau einer erzwungen (erster mit Flag, sonst der erste
     * Eintrag insgesamt) – damit das Stunden-Modal später deterministisch
     * einen Default-Empfänger findet.
     */
    private void mergeAnsprechpartner(SteuerberaterKontakt sk, List<SteuerberaterAnsprechpartnerDto> incoming) {
        List<SteuerberaterAnsprechpartner> bestehend = sk.getAnsprechpartnerListe();
        if (incoming == null || incoming.isEmpty()) {
            bestehend.clear();
            return;
        }

        Map<Long, SteuerberaterAnsprechpartner> byId = new HashMap<>();
        for (SteuerberaterAnsprechpartner ap : bestehend) {
            if (ap.getId() != null) byId.put(ap.getId(), ap);
        }

        // Index des ersten markierten Lohn-Ansprechpartners; -1 wenn keiner markiert.
        int lohnIndex = -1;
        for (int i = 0; i < incoming.size(); i++) {
            if (Boolean.TRUE.equals(incoming.get(i).getIstLohnAnsprechpartner())) {
                lohnIndex = i;
                break;
            }
        }
        // Fallback: keiner markiert → ersten als Default setzen.
        if (lohnIndex == -1) {
            lohnIndex = 0;
        }

        List<SteuerberaterAnsprechpartner> nachher = new java.util.ArrayList<>();
        for (int i = 0; i < incoming.size(); i++) {
            SteuerberaterAnsprechpartnerDto in = incoming.get(i);
            SteuerberaterAnsprechpartner ap = (in.getId() != null) ? byId.get(in.getId()) : null;
            if (ap == null) {
                ap = new SteuerberaterAnsprechpartner();
                ap.setSteuerberater(sk);
            }
            ap.setAnrede(Anrede.fromString(in.getAnrede()));
            ap.setVorname(in.getVorname());
            ap.setNachname(in.getNachname());
            ap.setEmail(in.getEmail());
            ap.setTelefon(in.getTelefon());
            ap.setIstLohnAnsprechpartner(i == lohnIndex);
            ap.setNotizen(in.getNotizen());
            nachher.add(ap);
        }

        bestehend.clear();
        bestehend.addAll(nachher);
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
        dto.setAnsprechpartnerListe(sk.getAnsprechpartnerListe().stream()
                .map(this::toAnsprechpartnerDto)
                .collect(Collectors.toList()));
        return dto;
    }

    private SteuerberaterAnsprechpartnerDto toAnsprechpartnerDto(SteuerberaterAnsprechpartner ap) {
        SteuerberaterAnsprechpartnerDto dto = new SteuerberaterAnsprechpartnerDto();
        dto.setId(ap.getId());
        dto.setAnrede(ap.getAnrede() != null ? ap.getAnrede().name() : null);
        dto.setVorname(ap.getVorname());
        dto.setNachname(ap.getNachname());
        dto.setEmail(ap.getEmail());
        dto.setTelefon(ap.getTelefon());
        dto.setIstLohnAnsprechpartner(ap.getIstLohnAnsprechpartner());
        dto.setNotizen(ap.getNotizen());
        return dto;
    }
}
