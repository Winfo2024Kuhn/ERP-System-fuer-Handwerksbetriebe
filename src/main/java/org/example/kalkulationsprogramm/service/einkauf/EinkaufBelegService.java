package org.example.kalkulationsprogramm.service.einkauf;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBestellung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBelegDto.*;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EinkaufBelegService {
    private final EinkaufBestellungRepository bestellungen;
    private final LieferantenRepository lieferanten;
    private final LieferantDokumentRepository dokumente;
    private final EinkaufDateiService dateien;
    private final EinkaufAuditService audit;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public List<Beleg> auflisten(Long bestellungId) {
        var bestellung = bestellung(bestellungId);
        return dokumente.leseBestellbelege(bestellung.getLieferantId(), bestellungId).stream()
                .map(d -> new Beleg(d.getId(), d.getTyp(), d.getEffektiverDateiname(), dateien.lieferantenbelegVerfuegbar(d))).toList();
    }

    @Transactional
    public Datei registrieren(Long bestellungId, Long dokumentId, Long akteur) {
        var bestellung = bestellung(bestellungId);
        if (dokumentId == null || dokumentId <= 0) throw new IllegalArgumentException("Der Beleg ist ungültig.");
        var dokument = dokumente.sperreEinkaufsbeleg(dokumentId).orElseThrow(() -> new NotFoundException("Beleg nicht gefunden."));
        pruefeZuordnung(bestellung, dokument);
        var result = datei(dokument);
        audit.protokolliere("BESTELLUNG", bestellungId, "BELEG_REGISTRIERT", akteur, null, json.valueToTree(result), "Lieferantenbeleg ausgewählt");
        return result;
    }

    @Transactional
    public Datei hochladen(Long bestellungId, LieferantDokumentTyp typ, MultipartFile datei, Long akteur) {
        var bestellung = bestellung(bestellungId);
        if (typ == null || typ == LieferantDokumentTyp.BELEG) throw new IllegalArgumentException("Bitte einen passenden Lieferanten-Belegtyp auswählen.");
        var lieferant = lieferanten.findById(bestellung.getLieferantId()).orElseThrow(() -> new NotFoundException("Lieferant nicht gefunden."));
        var dokument = dateien.ladeLieferantenbelegHoch(lieferant, typ, datei);
        // Die fachliche Zuordnung erfolgt erst im Lieferungs-/Zeugnis-/Rechnungsworkflow.
        var result = datei(dokument);
        audit.protokolliere("BESTELLUNG", bestellungId, "BELEG_HOCHGELADEN", akteur, null, json.valueToTree(result), "Lieferantenbeleg hochgeladen");
        return result;
    }

    private Datei datei(LieferantDokument dokument) {
        var datei = dateien.registriereLieferantenbeleg(dokument);
        return new Datei(datei.getId(), dokument.getId(), dokument.getTyp(), dokument.getEffektiverDateiname(), true);
    }
    private EinkaufBestellung bestellung(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("Die Bestellung ist ungültig.");
        return bestellungen.findById(id).orElseThrow(() -> new NotFoundException("Bestellung nicht gefunden."));
    }
    private void pruefeZuordnung(EinkaufBestellung bestellung, LieferantDokument dokument) {
        if (dokument.isAusgeblendet() || !bestellung.getLieferantId().equals(dokument.getLieferant().getId())
                || dokument.getEinkaufBestellungId() != null && !bestellung.getId().equals(dokument.getEinkaufBestellungId()))
            throw new NotFoundException("Der Beleg gehört nicht zu dieser Bestellung oder ihrem Lieferanten.");
    }
}
