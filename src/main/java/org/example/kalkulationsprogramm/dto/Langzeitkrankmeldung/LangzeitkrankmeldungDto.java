package org.example.kalkulationsprogramm.dto.Langzeitkrankmeldung;

import lombok.Data;
import org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungPhaseTyp;
import org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Ansicht einer {@link org.example.kalkulationsprogramm.domain.Langzeitkrankmeldung}
 * fuer die Desktop-Oberflaeche. {@code stufenplanTage} wird nur auf der
 * Detailseite befuellt (teure Berechnung ueber den ganzen
 * Wiedereingliederungs-Zeitraum), {@code phasen} immer.
 */
@Data
public class LangzeitkrankmeldungDto {
    private Long id;
    private Long mitarbeiterId;
    private String mitarbeiterName;
    private LocalDate beginn;
    private LocalDate ende;
    private LangzeitkrankmeldungStatus status;
    private String statusLabel;
    private LocalDate lohnfortzahlungBis;
    private String notiz;
    private Long version;
    private LangzeitkrankmeldungPhaseTyp aktuellePhaseTyp;
    private String aktuellePhaseLabel;
    /** Negativ = die Lohnfortzahlung ist bereits ueberschritten. */
    private Integer restTageLohnfortzahlung;
    /** Nur bei laufender Wiedereingliederung befuellt, sonst {@code null}. */
    private BigDecimal heuteGeplanteStunden;
    private LocalDate geplanteRueckkehr;
    private List<LangzeitkrankmeldungPhaseDto> phasen;
    private List<StufenplanTagDto> stufenplanTage;
}
