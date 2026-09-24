package org.example.kalkulationsprogramm.service.einkauf;

import java.util.Set;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.EinkaufVersandAngenommen;
import org.example.kalkulationsprogramm.repository.EinkaufMailantwortVorschauRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EinkaufMailantwortVersandListener implements EinkaufAnnahmeereignisConsumer {
    private final EinkaufMailantwortVorschauRepository vorschauen;

    public EinkaufMailantwortVersandListener(EinkaufMailantwortVorschauRepository vorschauen) {
        this.vorschauen = vorschauen;
    }

    @Override
    public Set<String> unterstuetzteVorgangstypen() { return Set.of("ANTWORT"); }

    @Override
    @Transactional
    public void verarbeite(EinkaufVersandAngenommen event) {
        if (event == null || !"ANTWORT".equals(event.typ()) || event.versandId() == null
                || event.vorgangId() == null || event.zeit() == null)
            throw new IllegalArgumentException("Das angenommene Antwortereignis ist ungültig.");
        var vorschau = vorschauen.findByVersandIdAndEmailId(event.versandId(), event.vorgangId())
                .orElseThrow(() -> new IllegalStateException("Die angenommene Antwortvorschau wurde nicht gefunden."));
        if (!"EINKAUF".equals(vorschau.getKontoId())
                || !"ANTWORT".equals(event.typ())
                || !java.util.Objects.equals(vorschau.getRevisionId(), event.revisionId())
                || !java.util.Objects.equals(vorschau.getBeteiligungId(), event.beteiligungId()))
            throw new IllegalStateException("Der angenommene Versand passt nicht zur Einkaufsantwort.");
        vorschau.markiereAngenommen(event.zeit());
        vorschauen.save(vorschau);
    }
}
