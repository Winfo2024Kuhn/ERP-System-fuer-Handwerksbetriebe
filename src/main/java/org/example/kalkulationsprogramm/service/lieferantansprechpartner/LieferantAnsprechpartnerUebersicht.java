package org.example.kalkulationsprogramm.service.lieferantansprechpartner;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.LieferantAnsprechpartner;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantAnsprechpartnerKurzDto;
import org.example.kalkulationsprogramm.mapper.LieferantAnsprechpartnerMapper;
import org.example.kalkulationsprogramm.repository.LieferantAnsprechpartnerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Liest Ansprechpartner für Anzeigen außerhalb des Reiters: Zähler, Kurzhinweis in Liste und
 * Anruf-Fenster („Max Mustermann +2“), Kurzliste der Handy-App. Immer gesammelt für alle
 * Lieferanten einer Seite (Blöcke zu {@value #BLOCK} IDs), nie eine Abfrage je Lieferant.
 */
@Service
@RequiredArgsConstructor
public class LieferantAnsprechpartnerUebersicht {

    /** Höchstens so viele IDs je {@code IN}-Liste. */
    static final int BLOCK = 1000;

    private final LieferantAnsprechpartnerRepository repository;

    /** Zähler für den Reiter „Ansprechpartner“. */
    @Transactional(readOnly = true)
    public long anzahl(Long lieferantId) {
        return repository.countByLieferantId(lieferantId);
    }

    /** Hinweis je Lieferant; Lieferanten ohne Ansprechpartner fehlen in der Map. */
    @Transactional(readOnly = true)
    public Map<Long, String> hinweise(Collection<Long> lieferantIds) {
        Map<Long, String> hinweise = new HashMap<>();
        gruppiert(lieferantIds).forEach((id, personen) -> hinweise.put(id, hinweisText(personen)));
        return hinweise;
    }

    /** Hinweis eines Lieferanten (Anruf-Fenster); {@code null}, wenn er keinen Ansprechpartner hat. */
    @Transactional(readOnly = true)
    public String hinweis(Long lieferantId) {
        return lieferantId == null ? null : hinweise(List.of(lieferantId)).get(lieferantId);
    }

    /** Kurzliste je Lieferant für die Handy-App; Lieferanten ohne Ansprechpartner fehlen in der Map. */
    @Transactional(readOnly = true)
    public Map<Long, List<LieferantAnsprechpartnerKurzDto>> fuerHandy(Collection<Long> lieferantIds) {
        Map<Long, List<LieferantAnsprechpartnerKurzDto>> ergebnis = new HashMap<>();
        gruppiert(lieferantIds).forEach((id, personen) ->
                ergebnis.put(id, personen.stream().map(LieferantAnsprechpartnerMapper::toKurzDto).toList()));
        return ergebnis;
    }

    /** Erster Ansprechpartner der sortierten Liste, bei mehreren mit „ +N“; leer → {@code null}. */
    String hinweisText(List<LieferantAnsprechpartner> sortiert) {
        if (sortiert.isEmpty()) {
            return null;
        }
        String erster = LieferantAnsprechpartnerMapper.anzeigename(sortiert.getFirst());
        return sortiert.size() > 1 ? erster + " +" + (sortiert.size() - 1) : erster;
    }

    private Map<Long, List<LieferantAnsprechpartner>> gruppiert(Collection<Long> lieferantIds) {
        Map<Long, List<LieferantAnsprechpartner>> gruppen = new LinkedHashMap<>();
        if (lieferantIds == null || lieferantIds.isEmpty()) {
            return gruppen;
        }
        List<Long> ids = lieferantIds.stream().distinct().toList();
        for (int start = 0; start < ids.size(); start += BLOCK) {
            List<Long> block = ids.subList(start, Math.min(start + BLOCK, ids.size()));
            for (LieferantAnsprechpartner a : repository.findeFuerLieferanten(block)) {
                gruppen.computeIfAbsent(a.getLieferant().getId(), k -> new ArrayList<>()).add(a);
            }
        }
        return gruppen;
    }
}
