package org.example.kalkulationsprogramm.service.dokumentstand;

import java.time.format.DateTimeFormatter;

import org.example.kalkulationsprogramm.domain.AusgangsDokumentStand;
import org.example.kalkulationsprogramm.domain.AusgangsDokumentStandArt;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.SperrbarerTyp;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentResponseDto;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentUpdateDto;
import org.example.kalkulationsprogramm.repository.AusgangsDokumentStandRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentAuditService;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService;
import org.example.kalkulationsprogramm.service.DatensatzLockService;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Holt einen älteren Stand zurück: Sein Inhalt wird wieder der aktuelle, bearbeitbare Inhalt.
 *
 * <ol>
 *   <li>Prüfen: Stand gehört zum Dokument (404), keine andere Person hält die Bearbeiten-Sperre
 *       (409), Dokument noch änderbar (400), Stand nicht schon der aktuelle Inhalt (400).</li>
 *   <li>Nicht exportierte Änderungen als Stand {@link AusgangsDokumentStandArt#VOR_WIEDERHERSTELLEN}
 *       sichern – so geht nichts verloren.</li>
 *   <li>Inhalt übernehmen (Betreff, Positionen, HTML, Netto, MwSt, Zahlungsziel, Rechnungsadresse;
 *       das Datum bleibt) über {@link AusgangsGeschaeftsDokumentService#aktualisieren}, damit
 *       Brutto, Projekt-/Anfragepreis und Produktkategorien wie beim Speichern nachgezogen werden.</li>
 *   <li>Wiederherstellungs-Zähler des Dokuments +1: Ein Editor mit älterem Zähler kann den
 *       zurückgeholten Inhalt danach nicht mehr überschreiben ({@link WiederherstellungsZaehlerPruefung}).</li>
 *   <li>Vormerken, dass Stand X zurückgeholt wurde (erscheint am nächsten Stand), und den
 *       GoBD-Audit schreiben.</li>
 * </ol>
 * Alles in einer Transaktion: Scheitert ein Schritt, bleibt das Dokument unverändert.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DokumentStandWiederherstellenService {

    static final String GESPERRT = "Dieses Dokument ist gebucht, angenommen oder storniert und lässt sich nicht "
            + "mehr ändern. Ältere Stände können Sie nur ansehen.";
    static final String SCHON_AKTUELL = "Dieser Stand ist bereits der aktuelle Inhalt.";
    static final String FREMDE_SPERRE = "Dieses Dokument wird gerade von einer anderen Person bearbeitet. "
            + "Bitte warten, bis sie es schließt.";
    static final String NICHT_UEBERNOMMEN = "Dieser Stand ließ sich nicht übernehmen. Bitte das Dokument neu "
            + "laden und es noch einmal versuchen.";
    private static final DateTimeFormatter ZEIT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final AusgangsDokumentStandRepository standRepository;
    private final AusgangsGeschaeftsDokumentRepository dokumentRepository;
    private final FrontendUserProfileRepository benutzerRepository;
    private final DokumentStandService standService;
    private final AusgangsGeschaeftsDokumentService dokumentService;
    private final AusgangsGeschaeftsDokumentAuditService auditService;
    private final DatensatzLockService lockService;

    @Transactional
    public AusgangsGeschaeftsDokumentResponseDto wiederherstellen(Long dokumentId, Long standId, Long benutzerId,
            String ip) {
        DokumentStandAbfrageService.pruefeId(dokumentId);
        DokumentStandAbfrageService.pruefeId(standId);
        AusgangsGeschaeftsDokument dokument = dokumentRepository.findById(dokumentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, DokumentStandAbfrageService.STAND_FEHLT));
        AusgangsDokumentStand stand = standRepository.findByIdAndDokumentId(standId, dokumentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, DokumentStandAbfrageService.STAND_FEHLT));
        if (lockService.istVonAnderemGesperrt(SperrbarerTyp.AUSGANG, dokumentId, benutzerId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, FREMDE_SPERRE);
        }
        if (!dokument.istBearbeitbar()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, GESPERRT);
        }
        if (DokumentStandInhalt.von(dokument).fingerabdruck().equals(stand.getInhaltFingerabdruck())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, SCHON_AKTUELL);
        }

        standService.festhalten(dokument, AusgangsDokumentStandArt.VOR_WIEDERHERSTELLEN, benutzerId, null);
        uebernimmLeereFelder(dokument, stand);
        dokument.setWiederherstellungsZaehler(dokument.getWiederherstellungsZaehler() + 1);
        AusgangsGeschaeftsDokument gespeichert = uebernimm(dokumentId, stand);
        standService.merkeWiederherstellung(dokumentId, benutzerId, stand.getNummer());

        FrontendUserProfile bearbeiter = benutzerId == null ? null : benutzerRepository.findById(benutzerId).orElse(null);
        auditService.protokolliereAenderung(gespeichert, bearbeiter, "Stand " + stand.getNummer() + " vom "
                + stand.getFestgehaltenAm().format(ZEIT) + " wiederhergestellt", ip);
        return dokumentService.findById(dokumentId);
    }

    /** Fachliche Meldungen ({@link ResponseStatusException}, Versionskonflikt) bleiben, alles andere wird 400. */
    private AusgangsGeschaeftsDokument uebernimm(Long dokumentId, AusgangsDokumentStand stand) {
        try {
            return dokumentService.aktualisieren(dokumentId, alsAenderung(stand));
        } catch (ResponseStatusException | ObjectOptimisticLockingFailureException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Stand {} von Dokument {} ließ sich nicht übernehmen: {}", stand.getNummer(), dokumentId,
                    e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, NICHT_UEBERNOMMEN, e);
        }
    }

    /**
     * {@code aktualisieren} übergeht leere Felder; was im Stand leer ist, muss auch im Dokument
     * wieder leer werden (z. B. kein Zahlungsziel). Das Dokument ist hier dieselbe verwaltete
     * Instanz, die {@code aktualisieren} gleich lädt.
     */
    private static void uebernimmLeereFelder(AusgangsGeschaeftsDokument dokument, AusgangsDokumentStand stand) {
        dokument.setBetreff(stand.getBetreff());
        dokument.setBetragNetto(stand.getBetragNetto());
        dokument.setMwstSatz(stand.getMwstSatz());
        dokument.setZahlungszielTage(stand.getZahlungszielTage());
        dokument.setHtmlInhalt(stand.getHtmlInhalt());
        dokument.setPositionenJson(stand.getPositionenJson());
        dokument.setRechnungsadresseOverride(stand.getRechnungsadresseOverride());
    }

    private static AusgangsGeschaeftsDokumentUpdateDto alsAenderung(AusgangsDokumentStand stand) {
        AusgangsGeschaeftsDokumentUpdateDto dto = new AusgangsGeschaeftsDokumentUpdateDto();
        dto.setBetreff(stand.getBetreff());
        dto.setBetragNetto(stand.getBetragNetto());
        dto.setMwstSatz(stand.getMwstSatz());
        dto.setZahlungszielTage(stand.getZahlungszielTage());
        dto.setHtmlInhalt(stand.getHtmlInhalt());
        dto.setPositionenJson(stand.getPositionenJson());
        dto.setRechnungsadresseOverride(stand.getRechnungsadresseOverride());
        return dto;
    }
}
