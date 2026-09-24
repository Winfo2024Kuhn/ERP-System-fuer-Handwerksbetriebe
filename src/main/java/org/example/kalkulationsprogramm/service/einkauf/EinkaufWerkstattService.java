package org.example.kalkulationsprogramm.service.einkauf;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBedarf;
import org.example.kalkulationsprogramm.domain.einkauf.Einheit;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBedarfDto.Werkstattpruefung;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.EinkaufBedarfRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EinkaufWerkstattService {
    private final EinkaufBedarfRepository bedarfe;
    private final EinkaufAuditService audit;
    private final ObjectMapper json;

    public EinkaufWerkstattService(EinkaufBedarfRepository bedarfe, EinkaufAuditService audit, ObjectMapper json) {
        this.bedarfe = bedarfe; this.audit = audit; this.json = json;
    }

    /** Absolute coverage of this need, not a warehouse stock movement. */
    @Transactional
    public List<Long> pruefen(Werkstattpruefung request, Long actor) {
        if (actor == null || actor <= 0 || request == null || request.positionen() == null
                || request.positionen().isEmpty() || request.positionen().size() > 5000)
            throw new IllegalArgumentException("Bitte wählen Sie 1 bis 5000 Bedarfe und einen gültigen Benutzer.");
        for (var item : request.positionen()) {
            if (item == null || item.bedarfId() == null || item.bedarfId() <= 0 || item.version() == null || item.version() < 0
                    || item.vorhanden() == null || item.vorhanden().signum() < 0 || item.vorhanden().scale() > 6
                    || item.vorhanden().compareTo(new BigDecimal("9999999999999.999999")) > 0)
                throw new IllegalArgumentException("Vorhanden muss eine gültige Menge ab 0 mit höchstens 6 Nachkommastellen sein.");
        }
        List<Long> ids = request.positionen().stream().map(p -> p.bedarfId()).distinct().sorted().toList();
        if (ids.size() != request.positionen().size()) throw new IllegalArgumentException("Ein Bedarf darf nur einmal vorkommen.");
        Map<Long, EinkaufBedarf> locked = bedarfe.findeAlleFuerUpdate(ids).stream()
                .collect(Collectors.toMap(EinkaufBedarf::getId, Function.identity()));
        if (locked.size() != ids.size()) throw new NotFoundException("Mindestens ein Bedarf wurde nicht gefunden.");
        // Validate the whole batch before mutating any managed entity.
        for (var item : request.positionen()) {
            var need = locked.get(item.bedarfId());
            if (need.getVersion() == null || !need.getVersion().equals(item.version()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ein Bedarf wurde inzwischen geändert. Bitte neu laden.");
            if (need.getBedarfMenge() == null || need.isNachpflegeErforderlich())
                throw new IllegalArgumentException("Bitte vervollständigen Sie zuerst die Angaben zum Bedarf.");
            if (need.getPosition().basis().einheit() == Einheit.STUECK && item.vorhanden().stripTrailingZeros().scale() > 0)
                throw new IllegalArgumentException("Vorhandene Stück müssen als ganze Zahl angegeben werden.");
            var maximum = need.getBedarfMenge().subtract(need.getBestellt()).subtract(need.getReserviert());
            if (item.vorhanden().compareTo(maximum) > 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Vorhanden überschreitet die noch nicht bestellte oder reservierte Bedarfsmenge.");
        }
        for (var item : request.positionen()) {
            var need = locked.get(item.bedarfId());
            if (need.getLagergedeckt().compareTo(item.vorhanden()) == 0) continue;
            var before = json.valueToTree(Map.of("vorhanden", need.getLagergedeckt(), "version", need.getVersion()));
            need.setLagergedeckt(item.vorhanden());
            audit.protokolliere("BEDARF", need.getId(), "WERKSTATTPRUEFUNG", actor, before,
                    json.valueToTree(Map.of("vorhanden", item.vorhanden())), "Vorhandene Menge für diesen Bedarf geprüft");
        }
        bedarfe.flush();
        return ids;
    }
}
