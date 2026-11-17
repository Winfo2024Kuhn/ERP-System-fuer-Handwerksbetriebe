package org.example.kalkulationsprogramm.service.dokumentstand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.AusgangsDokumentStand;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.dto.AusgangsDokumentStand.StandDetailDto;
import org.example.kalkulationsprogramm.dto.AusgangsDokumentStand.StandSeiteDto;
import org.example.kalkulationsprogramm.repository.AusgangsDokumentStandRepository;
import org.example.kalkulationsprogramm.repository.AusgangsDokumentStandZeile;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Liest den Verlauf (Stände) eines Ausgangsdokuments: eine Seite der Liste, einen Stand mit
 * Inhalt und das PDF eines Exports.
 *
 * <p>Die Liste lädt die großen Textspalten nicht ({@link AusgangsDokumentStandZeile}); die
 * Benutzernamen einer Seite holt der {@link DokumentStandEintragMapper} in einer Abfrage. „Aktuell“ vergleicht den Fingerabdruck des Stands
 * mit dem des Dokuments; die offenen Änderungen werden live gegen den neuesten Stand gerechnet.</p>
 */
@Service
@RequiredArgsConstructor
public class DokumentStandAbfrageService {

    static final int MAX_GROESSE = 50;
    static final String STAND_FEHLT = "Diesen Stand gibt es nicht.";

    private final AusgangsDokumentStandRepository standRepository;
    private final AusgangsGeschaeftsDokumentRepository dokumentRepository;
    private final DokumentStandEintragMapper mapper;

    /** Eine Seite der Stände, neueste zuerst; {@code size} wird auf 1..50, {@code page} auf ≥ 0 begrenzt. */
    @Transactional(readOnly = true)
    public StandSeiteDto seite(Long dokumentId, int page, int size) {
        AusgangsGeschaeftsDokument dokument = ladeDokument(dokumentId);
        int groesse = Math.min(Math.max(size, 1), MAX_GROESSE);
        int seite = Math.max(page, 0);
        Page<AusgangsDokumentStandZeile> zeilen = standRepository.findeSeite(dokumentId, PageRequest.of(seite, groesse));
        String aktuellerAbdruck = DokumentStandInhalt.von(dokument).fingerabdruck();
        return new StandSeiteDto(mapper.eintraege(zeilen, aktuellerAbdruck), zeilen.getTotalElements(), seite,
                groesse, zeilen.hasNext(), offeneAenderungen(dokument, aktuellerAbdruck), dokument.istBearbeitbar());
    }

    /** Ein Stand mit vollständigem Inhalt; 404, wenn er nicht zu diesem Dokument gehört. */
    @Transactional(readOnly = true)
    public StandDetailDto detail(Long dokumentId, Long standId) {
        AusgangsGeschaeftsDokument dokument = ladeDokument(dokumentId);
        AusgangsDokumentStand s = ladeStand(dokumentId, standId);
        boolean aktuell = DokumentStandInhalt.von(dokument).fingerabdruck().equals(s.getInhaltFingerabdruck());
        return StandDetailDto.aus(mapper.eintrag(s, aktuell), s.getBetreff(), s.getDatum(), s.getMwstSatz(),
                s.getZahlungszielTage(), s.getRechnungsadresseOverride(), s.getPositionenJson(), s.getHtmlInhalt(),
                dokument.istBearbeitbar());
    }

    /** Das beim Export dieses Stands gespeicherte PDF; 404, wenn es nicht (mehr) da ist. */
    @Transactional(readOnly = true)
    public byte[] pdf(Long dokumentId, Long standId) {
        ladeDokument(dokumentId);
        AusgangsDokumentStand stand = ladeStand(dokumentId, standId);
        Path datei = mapper.pdfAblage().finde(stand.getPdfDateiname())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Das PDF dieses Stands ist nicht aufbewahrt."));
        try {
            return Files.readAllBytes(datei);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Das PDF dieses Stands ist nicht lesbar.", e);
        }
    }

    private List<String> offeneAenderungen(AusgangsGeschaeftsDokument dokument, String aktuellerAbdruck) {
        Optional<AusgangsDokumentStand> neuester = standRepository.findFirstByDokumentIdOrderByNummerDesc(dokument.getId());
        if (neuester.isEmpty()) {
            return null;
        }
        if (aktuellerAbdruck.equals(neuester.get().getInhaltFingerabdruck())) {
            return List.of();
        }
        return DokumentStandVergleich.aenderungen(DokumentStandInhalt.von(neuester.get()), DokumentStandInhalt.von(dokument));
    }

    private AusgangsGeschaeftsDokument ladeDokument(Long dokumentId) {
        pruefeId(dokumentId);
        return dokumentRepository.findById(dokumentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dieses Dokument gibt es nicht."));
    }

    private AusgangsDokumentStand ladeStand(Long dokumentId, Long standId) {
        pruefeId(standId);
        return standRepository.findByIdAndDokumentId(standId, dokumentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, STAND_FEHLT));
    }

    /** IDs ≤ 0 sind nie gültig: 400 statt einer Datenbankabfrage. */
    static void pruefeId(Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültige Nummer.");
        }
    }
}
