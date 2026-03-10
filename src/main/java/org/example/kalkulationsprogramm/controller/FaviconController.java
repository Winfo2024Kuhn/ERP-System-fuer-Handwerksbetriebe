package org.example.kalkulationsprogramm.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FaviconController {

    // Serve a favicon to avoid 404 noise in the browser console
    @GetMapping("/favicon.ico")
    public ResponseEntity<Void> favicon() {
        return ResponseEntity.status(302)
                .header("Location", "/firmenlogo.png")
                .build();
    }
}

