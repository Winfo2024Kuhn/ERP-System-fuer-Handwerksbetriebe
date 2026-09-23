package org.example.kalkulationsprogramm.controller;

import java.util.List;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAnalyseDto.FeldVorschlag;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAnalyseDto.EmpfehlungDto;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAnalyseDto.JobDto;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAnalyseDto.Uebernahme;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAngebotDto.VersionDto;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufAngebotsAnalyseService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/einkauf/analysen")
public class EinkaufAnalyseController {
    private final EinkaufAngebotsAnalyseService analyse;
    private final EinkaufBerechtigungService rechte;
    public EinkaufAnalyseController(EinkaufAngebotsAnalyseService analyse, EinkaufBerechtigungService rechte) {
        this.analyse = analyse; this.rechte = rechte;
    }
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobDto starten(@RequestParam Long emailId, @RequestParam Long angebotId, Authentication authentication) {
        return analyse.starten(emailId, angebotId, rechte.verlange(authentication, EinkaufBerechtigung.BEARBEITEN));
    }
    @GetMapping("/{jobId}")
    public JobDto job(@PathVariable Long jobId, Authentication authentication) {
        rechte.verlange(authentication, EinkaufBerechtigung.LESEN);
        return analyse.job(jobId);
    }
    @GetMapping("/{jobId}/vorschlaege")
    public List<FeldVorschlag> vorschlaege(@PathVariable Long jobId, Authentication authentication) {
        rechte.verlange(authentication, EinkaufBerechtigung.LESEN);
        return analyse.vorschlaege(jobId);
    }
    @GetMapping("/{jobId}/empfehlung")
    public EmpfehlungDto empfehlung(@PathVariable Long jobId, Authentication authentication) {
        rechte.verlange(authentication, EinkaufBerechtigung.LESEN);
        return analyse.empfehlung(jobId);
    }
    @PostMapping("/{jobId}/uebernehmen")
    public VersionDto uebernehmen(@PathVariable Long jobId, @RequestBody Uebernahme request, Authentication authentication) {
        return analyse.uebernehmen(jobId, request, rechte.verlange(authentication, EinkaufBerechtigung.BEARBEITEN));
    }
}
