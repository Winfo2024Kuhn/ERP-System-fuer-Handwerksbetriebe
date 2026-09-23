package org.example.kalkulationsprogramm.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufZeugnisDto.*;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufZeugnisService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/einkauf")
public class EinkaufZeugnisController {
    private final EinkaufZeugnisService service;
    private final EinkaufBerechtigungService rechte;
    public EinkaufZeugnisController(EinkaufZeugnisService service,EinkaufBerechtigungService rechte){this.service=service;this.rechte=rechte;}

    @GetMapping("/zeugnisse")
    public List<ErwartungDto> liste(@RequestParam Long bestellungId,Authentication auth){rechte.verlange(auth,EinkaufBerechtigung.LESEN);return service.liste(bestellungId);}
    @PostMapping("/zeugnisse/revision/{revisionId}/erwarten")
    public void erwarte(@PathVariable Long revisionId,@RequestParam LocalDate frist,Authentication auth){rechte.verlange(auth,EinkaufBerechtigung.BEARBEITEN);service.erwarte(revisionId,frist);}
    @PostMapping("/zeugnisse/{id}/zuordnen")
    public ZuordnungDto zuordnen(@PathVariable Long id,@RequestBody Zuordnung request,Authentication auth){
        if(request.dokumentId()!=null&&!Objects.equals(id,request.dokumentId()))throw new IllegalArgumentException("Dokument-ID in Pfad und Inhalt unterscheiden sich.");
        Zuordnung withId=new Zuordnung(id,request.erwartungIds(),request.lieferPositionIds(),request.chargeIds(),request.schmelznummer());
        return service.zuordnen(withId,rechte.verlange(auth,EinkaufBerechtigung.BEARBEITEN));
    }
    @PostMapping("/zeugnisse/{id}/eingang/{dokumentId}")
    public ErwartungDto eingang(@PathVariable Long id,@PathVariable Long dokumentId,Authentication auth){
        rechte.verlange(auth,EinkaufBerechtigung.BEARBEITEN);return service.eingang(id,dokumentId);
    }
    @PostMapping("/zeugnisse/{id}/pruefen")
    public PruefungDto pruefen(@PathVariable Long id,@RequestBody Pruefung request,Authentication auth){
        return service.pruefen(id,request,rechte.verlange(auth,EinkaufBerechtigung.ZEUGNIS_PRUEFEN));
    }
    @GetMapping("/anforderungsvorlagen")
    public List<DokumentSoll> vorschlagen(@RequestParam(required=false) Long artikelId,@RequestParam(required=false) Long projektId,Authentication auth){
        rechte.verlange(auth,EinkaufBerechtigung.LESEN);return service.vorschlagen(artikelId,projektId);
    }
    @PostMapping("/anforderungsvorlagen")
    public VorlageDto vorlage(@RequestBody Vorlage request,Authentication auth){
        return service.vorlageAnlegen(request,rechte.verlange(auth,EinkaufBerechtigung.ZEUGNIS_PRUEFEN));
    }
}
