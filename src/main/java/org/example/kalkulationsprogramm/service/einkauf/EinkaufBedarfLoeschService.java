package org.example.kalkulationsprogramm.service.einkauf;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufAnlageVersion;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBedarf;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.AnfrageRevisionRepository;
import org.example.kalkulationsprogramm.repository.BestellungRevisionRepository;
import org.example.kalkulationsprogramm.repository.EinkaufAnlageVersionRepository;
import org.example.kalkulationsprogramm.repository.EinkaufBedarfRepository;
import org.example.kalkulationsprogramm.repository.EinkaufLagerentnahmeRepository;
import org.example.kalkulationsprogramm.repository.EinkaufMengenbuchungRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Löscht einen Einkaufsbedarf, solange er noch nicht weiterverarbeitet ist. Alles, was schon angefragt, bestellt,
 * reserviert, aus dem Lager entnommen oder als vorhanden eingetragen wurde, bleibt als Beleg erhalten (GoBD) –
 * dann antwortet der Dienst mit 409 und nennt den konkreten Grund.
 */
@Service
public class EinkaufBedarfLoeschService {
    private final EinkaufBedarfRepository bedarfe;
    private final AnfrageRevisionRepository anfragen;
    private final BestellungRevisionRepository bestellungen;
    private final EinkaufMengenbuchungRepository mengenbuchungen;
    private final EinkaufLagerentnahmeRepository lagerentnahmen;
    private final EinkaufAnlageVersionRepository anlagen;
    private final HiCadImportService hicadImporte;
    private final EinkaufAuditService audit;
    private final ObjectMapper json;

    public EinkaufBedarfLoeschService(EinkaufBedarfRepository bedarfe, AnfrageRevisionRepository anfragen,
            BestellungRevisionRepository bestellungen, EinkaufMengenbuchungRepository mengenbuchungen,
            EinkaufLagerentnahmeRepository lagerentnahmen, EinkaufAnlageVersionRepository anlagen,
            HiCadImportService hicadImporte, EinkaufAuditService audit, ObjectMapper json) {
        this.bedarfe = bedarfe;
        this.anfragen = anfragen;
        this.bestellungen = bestellungen;
        this.mengenbuchungen = mengenbuchungen;
        this.lagerentnahmen = lagerentnahmen;
        this.anlagen = anlagen;
        this.hicadImporte = hicadImporte;
        this.audit = audit;
        this.json = json;
    }

    @Transactional
    public void loeschen(Long id, long version, Long akteurId) {
        if (akteurId == null || akteurId <= 0) throw new IllegalArgumentException("Der handelnde Benutzer fehlt.");
        if (id == null || id <= 0 || version < 0) throw new IllegalArgumentException("Der Bedarf oder die Versionsangabe ist ungültig.");
        EinkaufBedarf bedarf = bedarfe.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Der Einkaufsbedarf wurde nicht gefunden."));
        if (bedarf.getVersion() == null || bedarf.getVersion() != version) {
            throw conflict("Der Einkaufsbedarf wurde zwischenzeitlich geändert. Bitte neu laden.");
        }
        pruefeNichtWeiterverarbeitet(bedarf);
        List<EinkaufAnlageVersion> anlageVersionen = anlagen.findByBedarfIdOrderByIdAsc(id);
        if (anlageVersionen.stream().anyMatch(EinkaufAnlageVersion::isVersendet)) {
            throw conflict("Eine Zeichnung dieses Bedarfs wurde bereits an einen Lieferanten verschickt. Der Bedarf kann nicht gelöscht werden.");
        }

        List<HiCadImportService.ImportFreigabe> hicadFreigaben =
                hicadImporte.gibUebernahmeFrei(bedarf.getProjektId(), id, bedarf.getPosition());
        Map<String, Object> vorher = protokollStand(bedarf, anlageVersionen, hicadFreigaben);
        anlagen.deleteAll(anlageVersionen);
        bedarfe.delete(bedarf);
        bedarfe.flush();
        audit.protokolliere("BEDARF", id, "BEDARF_GELOESCHT", akteurId, json.valueToTree(vorher), null,
                "Noch nicht weiterverarbeiteten Bedarf gelöscht");
    }

    private void pruefeNichtWeiterverarbeitet(EinkaufBedarf bedarf) {
        Long id = bedarf.getId();
        if (bedarf.getArtikelInProjektId() != null) {
            throw conflict("Dieser Bedarf stammt aus dem Material des Projekts. Bitte die Materialposition im Projekt entfernen.");
        }
        List<String> bestellNummern = bestellungen.bestellNummernMitBedarf(id);
        if (!bestellNummern.isEmpty()) {
            throw conflict("Der Bedarf ist bereits in " + aufzaehlung(bestellNummern.size() == 1 ? "Bestellung" : "Bestellungen", bestellNummern)
                    + " enthalten und kann nicht gelöscht werden.");
        }
        List<String> anfrageNummern = anfragen.anfrageNummernMitBedarf(id);
        if (!anfrageNummern.isEmpty()) {
            throw conflict("Der Bedarf ist bereits in " + aufzaehlung(anfrageNummern.size() == 1 ? "Preisanfrage" : "Preisanfragen", anfrageNummern)
                    + " enthalten und kann nicht gelöscht werden.");
        }
        if (lagerentnahmen.existsByBedarfId(id)) {
            throw conflict("Für diesen Bedarf wurde bereits Material aus dem Lager entnommen. Er kann nicht gelöscht werden.");
        }
        if (bedarf.getReserviert().signum() > 0) {
            throw conflict("Für diesen Bedarf ist bereits Menge reserviert. Bitte zuerst die Reservierung aufheben.");
        }
        if (bedarf.getBestellt().signum() > 0 || bedarf.getGeliefert().signum() > 0 || bedarf.getStorniert().signum() > 0) {
            throw conflict("Dieser Bedarf wurde bereits bestellt oder geliefert und kann nicht gelöscht werden.");
        }
        if (bedarf.getLagergedeckt().signum() > 0) {
            throw conflict("Für diesen Bedarf ist schon eine vorhandene Menge eingetragen. Bitte „Vorhanden“ zuerst auf 0 setzen.");
        }
        if (mengenbuchungen.existsByBedarf_Id(id)) {
            throw conflict("Für diesen Bedarf wurden schon Mengen gebucht (z. B. eine wieder aufgehobene Reservierung). Er bleibt deshalb als Nachweis erhalten.");
        }
    }

    private Map<String, Object> protokollStand(EinkaufBedarf bedarf, List<EinkaufAnlageVersion> anlageVersionen,
            List<HiCadImportService.ImportFreigabe> hicadFreigaben) {
        Map<String, Object> stand = new LinkedHashMap<>();
        stand.put("id", bedarf.getId());
        stand.put("version", bedarf.getVersion());
        stand.put("projektId", bedarf.getProjektId());
        stand.put("bezeichnung", bedarf.getBezeichnung());
        stand.put("interneKennung", bedarf.getInterneKennung());
        stand.put("bedarfMenge", bedarf.getBedarfMenge());
        stand.put("position", bedarf.getPosition());
        stand.put("liefergruppe", bedarf.getLiefergruppe());
        stand.put("anlagen", anlageVersionen.stream().map(anlage -> {
            Map<String, Object> eintrag = new LinkedHashMap<>();
            eintrag.put("id", anlage.getId());
            eintrag.put("revision", anlage.getRevision());
            eintrag.put("dateiId", anlage.getDatei() == null ? null : anlage.getDatei().getId());
            eintrag.put("freigegeben", anlage.isFreigegeben());
            return eintrag;
        }).toList());
        stand.put("hicadFreigaben", hicadFreigaben);
        return stand;
    }

    private static String aufzaehlung(String art, List<String> nummern) {
        List<String> erste = nummern.stream().limit(3).toList();
        String text = art + " " + String.join(", ", erste);
        return nummern.size() > erste.size() ? text + " und " + (nummern.size() - erste.size()) + " weiteren" : text;
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
