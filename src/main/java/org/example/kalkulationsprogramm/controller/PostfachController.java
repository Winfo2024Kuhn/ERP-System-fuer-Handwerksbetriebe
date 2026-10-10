package org.example.kalkulationsprogramm.controller;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.example.kalkulationsprogramm.dto.Postfach.PostfachDto;
import org.example.kalkulationsprogramm.dto.Postfach.PostfachSichtbarkeitRequest;
import org.example.kalkulationsprogramm.dto.Postfach.PostfachSpeichernRequest;
import org.example.kalkulationsprogramm.dto.Postfach.PostfachTestErgebnis;
import org.example.kalkulationsprogramm.dto.Postfach.PostfachTestRequest;
import org.example.kalkulationsprogramm.service.PostfachService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Einstellungen → E-Mail → Postfächer. Nur für Admins (SecurityConfig).
 */
@RestController
@RequestMapping("/api/postfaecher")
@RequiredArgsConstructor
public class PostfachController {

    private final PostfachService postfachService;

    @GetMapping
    public List<PostfachDto> alle() {
        return postfachService.alle();
    }

    @PostMapping
    public ResponseEntity<PostfachDto> anlegen(@RequestBody PostfachSpeichernRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postfachService.anlegen(request));
    }

    @PutMapping("/{id}")
    public PostfachDto aendern(@PathVariable Long id, @RequestBody PostfachSpeichernRequest request) {
        return postfachService.aendern(id, request);
    }

    /** „Wer darf es sehen?“ – gepflegt unter Einstellungen → Berechtigungen. */
    @PutMapping("/{id}/sichtbarkeit")
    public PostfachDto sichtbarkeitAendern(@PathVariable Long id, @RequestBody PostfachSichtbarkeitRequest request) {
        return postfachService.sichtbarkeitAendern(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> loeschen(@PathVariable Long id) {
        postfachService.loeschen(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    public PostfachTestErgebnis teste(@RequestBody PostfachTestRequest request) {
        return postfachService.teste(request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> ungueltig(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> nichtGefunden(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
    }
}
