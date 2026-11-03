package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerAnlegenDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerUmbenennenDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerVerschiebenDto;
import org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentOrdnerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Ordnerbaum der Firmendokumente. Jeder angemeldete PC-Benutzer (Stammdaten, nicht nur Admins); sichtbar und
 * änderbar ist, was die Ordnerrechte erlauben (Prüfung im Dienst).
 */
@RestController
@RequestMapping("/api/firmendokumente/ordner")
@RequiredArgsConstructor
public class FirmendokumentOrdnerController {

    private final FirmendokumentOrdnerService service;

    @GetMapping
    public List<FirmendokumentOrdnerDto> liste(Authentication authentication) {
        return service.liste(authentication);
    }

    @PostMapping
    public ResponseEntity<FirmendokumentOrdnerDto> anlegen(@RequestBody FirmendokumentOrdnerAnlegenDto eingabe,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.anlegen(eingabe, authentication));
    }

    @PutMapping("/{id}")
    public FirmendokumentOrdnerDto umbenennen(@PathVariable Long id,
            @RequestBody FirmendokumentOrdnerUmbenennenDto eingabe, Authentication authentication) {
        return service.umbenennen(id, eingabe.name(), authentication);
    }

    @PutMapping("/{id}/verschieben")
    public FirmendokumentOrdnerDto verschieben(@PathVariable Long id,
            @RequestBody FirmendokumentOrdnerVerschiebenDto eingabe, Authentication authentication) {
        return service.verschieben(id, eingabe.parentId(), authentication);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> loeschen(@PathVariable Long id, Authentication authentication) {
        service.loeschen(id, authentication);
        return ResponseEntity.noContent().build();
    }
}
