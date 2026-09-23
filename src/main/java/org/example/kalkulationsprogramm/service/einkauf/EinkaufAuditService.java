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
    public boolean istWiederholung(String typ, Long id, String aktion, Long actor, JsonNode payload) {
        String key = payload.path("idempotenzKey").asText();
        if (key.isBlank()) return false;
        for (var event : auditRepository.findByVorgangTypAndVorgangIdAndAktion(typ, id, aktion)) {
            var prior = event.getNachherSnapshot();
            if (prior != null && key.equals(prior.path("idempotenzKey").asText())) {
                if (!java.util.Objects.equals(actor, event.getAkteurId()) || !inhaltGleich(payload, prior))
                    throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                            "Der Idempotenzschlüssel gehört zu einem anderen Beleg oder Inhalt.");
                return true;
            }
        }
        return false;
    }

    static boolean inhaltGleich(JsonNode left, JsonNode right) {
        if (left.isNumber() && right.isNumber()) return left.decimalValue().compareTo(right.decimalValue()) == 0;
        if (left.isObject() && right.isObject()) {
            if (left.size() != right.size()) return false;
            var fields = left.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (!right.has(entry.getKey()) || !inhaltGleich(entry.getValue(), right.get(entry.getKey()))) return false;
            }
            return true;
        }
        if (left.isArray() && right.isArray()) {
            if (left.size() != right.size()) return false;
            for (int i = 0; i < left.size(); i++) if (!inhaltGleich(left.get(i), right.get(i))) return false;
            return true;
        }
        return left.equals(right);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void protokolliere(String vorgangTyp, Long vorgangId, String aktion, Long akteurId,
            JsonNode vorher, JsonNode nachher, String grund) {
        auditRepository.save(new EinkaufAudit(vorgangTyp, vorgangId, aktion, akteurId, vorher, nachher, grund));
    }
}
