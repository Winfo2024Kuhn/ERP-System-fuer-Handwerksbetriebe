package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/utils")
public class UtilsController {

    @GetMapping("/verrechnungseinheiten")
    public ResponseEntity<Verrechnungseinheit[]> getVerrechnungseinheiten() {
        return ResponseEntity.ok(Verrechnungseinheit.values());
    }
}
