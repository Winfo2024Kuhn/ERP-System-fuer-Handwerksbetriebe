package org.example.kalkulationsprogramm.service;

import java.util.List;

import org.example.kalkulationsprogramm.domain.AusgelesenePosition;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.repository.LieferantDokumentPositionRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Übernimmt Positionen aus der schon gespeicherten KI-Antwort in Dokumente, die
 * bisher keine haben – ohne neuen KI-Aufruf.
 *
 * <p>Hintergrund: Der E-Mail-Import hat die von der KI gelesenen Positionen lange
 * nicht gespeichert (nur das Hochladen von Hand tat das). Die Antwort selbst liegt
 * aber in {@code aiRawJson}. Preise werden dabei bewusst nicht übernommen – alte
 * Belege dürfen aktuelle Einkaufspreise nicht überschreiben.
 *
 * <p>Jedes Dokument läuft in einer eigenen Transaktion: ein kaputtes Dokument hält
 * die übrigen nicht auf.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LieferantDokumentPositionNachtrag {

    private final LieferantDokumentPositionRepository positionRepository;
    private final LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    private final LieferantDokumentPositionService positionService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * @return Anzahl der Dokumente, die Positionen bekommen haben
     */
    public int nachtragen() {
        List<Long> ids = positionRepository.findeIdsOhnePositionenMitKiAntwort(
                LieferantDokumentPositionService.typenMitPositionen());
        int ergaenzt = 0;
        for (Long id : ids) {
            try {
                Integer anzahl = transactionTemplate.execute(status -> uebernimm(id));
                if (anzahl != null && anzahl > 0) {
                    ergaenzt++;
                }
            } catch (Exception e) {
                log.warn("[PositionenNachtrag] Dokument {} übersprungen: {}", id, e.getClass().getSimpleName());
            }
        }
        log.info("[PositionenNachtrag] {} von {} Dokumenten haben Positionen bekommen", ergaenzt, ids.size());
        return ergaenzt;
    }

    private int uebernimm(Long id) {
        LieferantGeschaeftsdokument gd = geschaeftsdokumentRepository.findById(id).orElse(null);
        if (gd == null || gd.getAiRawJson() == null || gd.getDokument() == null) {
            return 0;
        }
        // Läuft parallel zum Betrieb: Hat das Dokument inzwischen Positionen (Import,
        // „Positionen nachlesen“), bleiben die – samt einer Aufteilung auf Projekte.
        if (positionRepository.countByGeschaeftsdokumentId(id) > 0) {
            return 0;
        }
        JsonNode antwort;
        try {
            antwort = objectMapper.readTree(gd.getAiRawJson());
        } catch (JacksonException e) {
            // Nie die Antwort selbst loggen: sie enthält die Belegdaten.
            log.debug("[PositionenNachtrag] KI-Antwort von Dokument {} nicht lesbar", id);
            return 0;
        }
        List<AusgelesenePosition> positionen = LieferantDokumentPositionService.ausKiAntwort(antwort);
        if (positionen == null || positionen.isEmpty()) {
            return 0;
        }
        return positionService.ersetzePositionen(gd, gd.getDokument().getTyp(), positionen);
    }
}
