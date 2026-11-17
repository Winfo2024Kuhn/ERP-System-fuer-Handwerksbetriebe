package org.example.kalkulationsprogramm.service.dokumentstand;

import org.example.kalkulationsprogramm.dto.AusgangsDokumentStand.StandFesthaltenDto;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.UnexpectedRollbackException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Hält einen Stand ohne PDF fest – für Exportwege, die kein PDF ans Backend schicken (Drucken und
 * PDF-Knopf bei Angebot, Nachtragsangebot, Auftragsbestätigung). Gleiche Regeln wie nach
 * {@code pdf-speichern} ({@link DokumentStandService#festhaltenNachExport}): kein neuer Stand bei
 * gleichem Inhalt, offene Bearbeitung wird übernommen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DokumentStandFesthaltenService {

    private final DokumentStandService standService;
    private final DokumentStandEintragMapper mapper;

    /**
     * Ohne eigene Transaktion: Die Transaktion von {@link DokumentStandService} ist die Grenze, damit
     * eine doppelte Nummer (gleichzeitiger Export trotz Zeilensperre) hier als „kein neuer Stand“
     * ankommt statt als Fehler mit Datenbank-Details.
     */
    public StandFesthaltenDto festhalten(Long dokumentId, Long benutzerId) {
        DokumentStandAbfrageService.pruefeId(dokumentId);
        try {
            return standService.festhaltenNachExport(dokumentId, benutzerId, null)
                    // Ein gerade festgehaltener Stand ist per Definition der aktuelle Inhalt.
                    .map(stand -> new StandFesthaltenDto(true, mapper.eintrag(stand, true)))
                    .orElseGet(() -> new StandFesthaltenDto(false, null));
        } catch (DataIntegrityViolationException | UnexpectedRollbackException e) {
            log.warn("Stand für Dokument {} nicht festgehalten (gleichzeitiger Export)", dokumentId);
            return new StandFesthaltenDto(false, null);
        }
    }
}
