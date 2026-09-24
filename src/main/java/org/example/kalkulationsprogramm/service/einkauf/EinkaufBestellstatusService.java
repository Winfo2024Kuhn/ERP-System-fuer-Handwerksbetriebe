package org.example.kalkulationsprogramm.service.einkauf;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.einkauf.BestellBestaetigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBestellstatusDto.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBestellungDto.Herkunft;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufLieferungDto.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.VersandDto;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EinkaufBestellstatusService {
    private final EinkaufBestellungRepository orders;
    private final BestellungRevisionRepository revisions;
    private final EinkaufLieferungRepository deliveries;
    private final EinkaufMengenService amounts;
    private final EinkaufVersandauftragRepository dispatches;
    private final EntityManager em;
    private final ObjectMapper json;

    public List<Mengenstand> mengen(Long id) {
        pruefeBestellung(id);
        return amounts.standFuerVorgang("BESTELLUNG:" + id).entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).map(e -> {
                    var s = e.getValue();
                    return new Mengenstand(e.getKey(), s.reserviert(), s.bestellt(), s.geliefert(), s.storniert(), s.offen());
                }).toList();
    }

    public List<Lieferung> lieferungen(Long id) {
        pruefeBestellung(id);
        // Beide Sammlungen gebündelt laden; kein Query je Lieferung/Position/Charge.
        var rows = deliveries.leseMitPositionen(id);
        Map<Long, List<Charge>> charges = new HashMap<>();
        em.createQuery("select c.position.id, c.id, c.kennung, c.schmelznummer from EinkaufCharge c "
                        + "where c.position.lieferung.bestellung.id = :id order by c.id", Object[].class)
                .setParameter("id", id).getResultList().forEach(c -> charges.computeIfAbsent((Long)c[0], key -> new ArrayList<>())
                        .add(new Charge((Long)c[1], (String)c[2], (String)c[3])));
        return rows.stream().map(d -> new Lieferung(d.getId(), id, d.getRevisionId(), d.getLieferscheinId(), d.getEingang(),
                d.getPositionen().stream().sorted(Comparator.comparing(p -> p.getId())).map(p -> new Lieferposition(
                        p.getId(), p.getBestellPosition().getId(), p.getMenge(), p.getCharge(), p.getSchmelznummer(),
                        p.getProjektAnteile().stream().map(h -> json.convertValue(h, Herkunft.class)).toList(),
                        charges.getOrDefault(p.getId(), List.of()))).toList())).toList();
    }

    public List<BestaetigungDto> bestaetigungen(Long id) {
        pruefeBestellung(id);
        return em.createQuery("select b from BestellBestaetigung b where b.bestellung.id = :id order by b.datum, b.id", BestellBestaetigung.class)
                .setParameter("id", id).getResultList().stream().map(b -> new BestaetigungDto(b.getId(), b.getDokumentId(),
                        b.getDatum(), b.getLiefertermin(), b.isAbweichung(), b.getPositionen().stream()
                        .map(p -> json.convertValue(p, BestaetigtePosition.class)).toList())).toList();
    }

    public List<VersandDto> versandstatus(Long id, Long revisionId) {
        pruefeBestellung(id);
        if (revisionId == null || revisionId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Bestellfassung ist ungültig.");
        var revision = revisions.findById(revisionId).orElseThrow(() -> new NotFoundException("Bestellfassung nicht gefunden."));
        if (!Objects.equals(id, revision.getBestellung().getId())) throw new NotFoundException("Bestellfassung gehört nicht zu dieser Bestellung.");
        return dispatches.leseStatus("BESTELLUNG", id, revisionId).stream().map(a -> new VersandDto(a.getId(), a.getVersion(),
                a.getTyp(), a.getVorgangId(), a.getRevisionId(), a.getStatus().name(), a.getFehlerCode(),
                a.getErstelltAm(), a.getAngenommenAm(), a.getArchiviertAm() != null, a.getMessageId())).toList();
    }

    private void pruefeBestellung(Long id) {
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Bestellung ist ungültig.");
        if (!orders.existsById(id)) throw new NotFoundException("Bestellung nicht gefunden.");
    }
}
