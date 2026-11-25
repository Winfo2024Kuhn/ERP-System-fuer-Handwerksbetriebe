package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantAnsprechpartnerKurzDto;
import org.example.kalkulationsprogramm.dto.Lieferant.ZeiterfassungLieferantDto;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.LieferantenSuche;
import org.example.kalkulationsprogramm.service.lieferantansprechpartner.LieferantAnsprechpartnerUebersicht;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lieferantenliste der Handy-App ({@code GET /api/zeiterfassung/lieferanten}): aktive Lieferanten
 * bzw. Suchtreffer mit Adresse, Kontaktdaten und Ansprechpartnern. Höchstens {@value #MAX} je Abruf,
 * in der Datenbank begrenzt und nach Name sortiert; E-Mails und Ansprechpartner kommen für alle
 * gelieferten Lieferanten gesammelt (je eine Abfrage).
 */
@Service
@RequiredArgsConstructor
public class ZeiterfassungLieferantenService {

    static final int STANDARD = 15;
    static final int MAX = 50;

    private final LieferantenRepository lieferantenRepository;
    private final LieferantAnsprechpartnerUebersicht ansprechpartnerUebersicht;

    /**
     * @param limit  gewünschte Anzahl; ohne Angabe (oder ≤ 0) {@value #STANDARD}, höchstens {@value #MAX}
     * @param search Suche in Name, zweitem Namen, E-Mail-Adressen und Ansprechpartnern
     *               (auch inaktive Lieferanten); ohne Angabe die aktiven
     */
    @Transactional(readOnly = true)
    public List<ZeiterfassungLieferantDto> getLieferanten(Integer limit, String search) {
        int anzahl = limit == null || limit <= 0 ? STANDARD : Math.min(limit, MAX);
        List<Lieferanten> lieferanten = lieferantenRepository.findAll(LieferantenSuche.handySuche(search),
                PageRequest.of(0, anzahl, LieferantenSuche.SORTIERUNG)).getContent();
        if (lieferanten.isEmpty()) {
            return List.of();
        }
        List<Long> ids = lieferanten.stream().map(Lieferanten::getId).toList();
        Map<Long, List<String>> emails = emails(ids);
        Map<Long, List<LieferantAnsprechpartnerKurzDto>> ansprechpartner = ansprechpartnerUebersicht.fuerHandy(ids);
        return lieferanten.stream()
                .map(l -> new ZeiterfassungLieferantDto(l.getId(), l.getLieferantenname(), l.getAliasName(),
                        l.getStrasse(), l.getPlz(), l.getOrt(), l.getTelefon(), l.getMobiltelefon(),
                        emails.getOrDefault(l.getId(), List.of()), l.getLieferantenTyp(), l.getEigeneKundennummer(),
                        ansprechpartner.getOrDefault(l.getId(), List.of())))
                .toList();
    }

    private Map<Long, List<String>> emails(List<Long> ids) {
        Map<Long, List<String>> emails = new HashMap<>();
        for (Object[] z : lieferantenRepository.findeEmailsFuerLieferanten(ids)) {
            emails.computeIfAbsent((Long) z[0], k -> new ArrayList<>()).add((String) z[1]);
        }
        return emails;
    }
}
