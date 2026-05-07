package org.example.kalkulationsprogramm.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.EntityLastAccessed;
import org.example.kalkulationsprogramm.repository.EntityLastAccessedRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Verwaltet pro Frontend-User die "Zuletzt aufgerufen"-Stempel für Entities
 * (z. B. Projekte, Anfragen). Frontend-Listen nutzen diese Stempel, um die
 * zuletzt geöffneten Karten geräteübergreifend nach oben zu sortieren.
 */
@Service
@RequiredArgsConstructor
public class EntityLastAccessedService {

    private final EntityLastAccessedRepository repository;

    @Transactional
    public void track(Long userId, String entityType, Long entityId) {
        if (userId == null || entityType == null || entityId == null) {
            return;
        }
        EntityLastAccessed entry = new EntityLastAccessed(userId, entityType, entityId, LocalDateTime.now());
        repository.save(entry);
    }

    /**
     * Liefert eine geordnete Map (entityId -> Epoch-Millis) sortiert nach
     * Zugriffszeit absteigend, sodass das Frontend ohne weitere Logik
     * "neueste zuerst" anzeigen kann.
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> listForUser(Long userId, String entityType) {
        if (userId == null || entityType == null) {
            return Map.of();
        }
        List<EntityLastAccessed> entries = repository.findAllByUserAndType(userId, entityType);
        Map<Long, Long> result = new LinkedHashMap<>(entries.size());
        for (EntityLastAccessed entry : entries) {
            LocalDateTime ts = entry.getZugegriffenAm();
            if (ts == null) continue;
            long epochMillis = ts.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            result.put(entry.getId().getEntityId(), epochMillis);
        }
        return result;
    }
}
