package org.example.kalkulationsprogramm.service.einkauf;
import java.util.Set;import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.EinkaufVersandAngenommen;import org.springframework.context.annotation.Lazy;import org.springframework.stereotype.Component;import org.springframework.transaction.annotation.Transactional;
@Component public class EinkaufBestellVersandListener implements EinkaufAnnahmeereignisConsumer {
 private final EinkaufBestellfreigabeService service;public EinkaufBestellVersandListener(@Lazy EinkaufBestellfreigabeService service){this.service=service;}
 @Override public Set<String> unterstuetzteVorgangstypen(){return Set.of("BESTELLUNG");}
 @Override @Transactional public void verarbeite(EinkaufVersandAngenommen event){service.versandAngenommen(event);}
}
