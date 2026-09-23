package org.example.kalkulationsprogramm.dto.Einkauf;

import java.time.LocalDate;
import java.util.List;

public final class EinkaufFaelligkeitDto {
    private EinkaufFaelligkeitDto() {}
    public record Faelligkeit(String typ,Long vorgangId,String nummer,Long beteiligungId,LocalDate frist,Long zustaendigId,String hinweis) {}
    public record NachfrageRequest(String typ,Long vorgangId,Long beteiligungId) {}
    public record NachfrageEntwurf(String typ,Long vorgangId,Long beteiligungId,Long vorlageId,long vorlageVersion,
            String empfaenger,String subject,String htmlBody,List<String> fehlendeNachweise) {
        public NachfrageEntwurf { fehlendeNachweise=fehlendeNachweise==null?List.of():List.copyOf(fehlendeNachweise); }
    }
}
