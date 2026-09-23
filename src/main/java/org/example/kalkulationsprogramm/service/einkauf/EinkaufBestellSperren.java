package org.example.kalkulationsprogramm.service.einkauf;

import java.math.BigDecimal;
import java.util.*;
import org.example.kalkulationsprogramm.domain.einkauf.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Herkunft;
import org.example.kalkulationsprogramm.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Shared lock ordering and quantity projections for all order mutations. */
final class EinkaufBestellSperren {
    private EinkaufBestellSperren() {}
    static EinkaufBestellung sperre(Long id, Collection<Long> extra, EinkaufBestellungRepository orders,
            BestellungRevisionRepository revisions, EinkaufBedarfRepository needs) {
        var ids = new TreeSet<Long>(extra);
        revisions.findByBestellung_IdOrderByNummerAsc(id).forEach(r -> r.getPositionen().forEach(p ->
                p.getHerkuenfte().forEach(h -> ids.add(h.getBedarfId()))));
        if (!ids.isEmpty()) needs.findeAlleFuerUpdate(List.copyOf(ids));
        var order = orders.findeFuerUpdate(id).orElseThrow(() -> new NoSuchElementException("Bestellung nicht gefunden."));
        var currentIds = new TreeSet<Long>();
        revisions.findByBestellung_IdOrderByNummerAsc(id).forEach(r -> r.getPositionen().forEach(p ->
                p.getHerkuenfte().forEach(h -> currentIds.add(h.getBedarfId()))));
        if (!ids.containsAll(currentIds)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Die Bestellfassung wurde gleichzeitig geändert. Bitte erneut versuchen.");
        return order;
    }
    static List<Herkunft> aktuelleAnteile(Map<Long, BigDecimal> amounts, EinkaufBedarfRepository needs) {
        if (amounts.isEmpty()) return List.of();
        var locked = needs.findeAlleFuerUpdate(amounts.keySet().stream().sorted().toList());
        return locked.stream().map(n -> new Herkunft(n.getId(), n.getVersion(), amounts.get(n.getId()))).toList();
    }
    static Map<Long, BigDecimal> mengen(BestellungRevision revision) {
        Map<Long,BigDecimal> result = new LinkedHashMap<>();
        revision.getPositionen().forEach(p -> p.getHerkuenfte().forEach(h -> result.merge(h.getBedarfId(), h.getMenge(), BigDecimal::add)));
        return result;
    }
    static void aktualisiereStatus(EinkaufBestellung order, Map<Long,EinkaufMengenService.Vorgangsmenge> balances) {
        BigDecimal ordered = balances.values().stream().map(EinkaufMengenService.Vorgangsmenge::bestellt).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal delivered = balances.values().stream().map(EinkaufMengenService.Vorgangsmenge::geliefert).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean pending = order.getRevisionen().stream().max(Comparator.comparingInt(BestellungRevision::getNummer))
                .filter(r -> !r.istAngenommen() && !r.istVerworfen()).isPresent();
        if (pending) {
            order.setStatus(delivered.signum()>0 ? BestellungStatus.TEILGELIEFERT : BestellungStatus.BESTELLT);
            return;
        }
        order.setStatus(ordered.signum()==0 ? BestellungStatus.STORNIERT : delivered.compareTo(ordered)>=0 ? BestellungStatus.GELIEFERT
                : delivered.signum()>0 ? BestellungStatus.TEILGELIEFERT : BestellungStatus.BESTELLT);
    }
}
