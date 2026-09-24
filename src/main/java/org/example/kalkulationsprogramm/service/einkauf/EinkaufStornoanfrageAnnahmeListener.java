package org.example.kalkulationsprogramm.service.einkauf;

import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufVersandauftrag;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.EinkaufVersandAngenommen;
import org.example.kalkulationsprogramm.repository.EinkaufVersandauftragRepository;
import org.springframework.stereotype.Component;

/** Annahme einer Bitte um Storno ist nur ein Kommunikationsnachweis, keine Stornobestätigung. */
@Component
@RequiredArgsConstructor
public class EinkaufStornoanfrageAnnahmeListener implements EinkaufAnnahmeereignisConsumer {
    private final EinkaufVersandauftragRepository auftraege;
    @Override public Set<String> unterstuetzteVorgangstypen() { return Set.of("STORNO_ANFRAGE"); }
    @Override public void verarbeite(EinkaufVersandAngenommen event) {
        if (event == null || !"STORNO_ANFRAGE".equals(event.typ())) throw new IllegalArgumentException("Ungültiges Stornoanfrage-Ereignis.");
        var auftrag = auftraege.findById(event.versandId()).orElseThrow();
        if (!"STORNO_ANFRAGE".equals(auftrag.getTyp()) || !Objects.equals(auftrag.getVorgangId(), event.vorgangId())
                || !Objects.equals(auftrag.getRevisionId(), event.revisionId()) || auftrag.getStatus() != EinkaufVersandauftrag.Status.ANGENOMMEN)
            throw new IllegalStateException("Der Versandnachweis gehört nicht zu dieser Stornoanfrage.");
        // Outbox und dauerhaftes Annahmeereignis sind der Verlauf; keine Mengenbuchung.
    }
}
