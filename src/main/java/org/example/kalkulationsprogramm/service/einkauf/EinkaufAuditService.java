package org.example.kalkulationsprogramm.service.einkauf;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufAudit;
import org.example.kalkulationsprogramm.repository.EinkaufAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EinkaufAuditService {
    private final EinkaufAuditRepository auditRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void protokolliere(String vorgangTyp, Long vorgangId, String aktion, Long akteurId,
            JsonNode vorher, JsonNode nachher, String grund) {
        auditRepository.save(new EinkaufAudit(vorgangTyp, vorgangId, aktion, akteurId, vorher, nachher, grund));
    }
}
