package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.service.WerkstoffService;
import org.example.kalkulationsprogramm.service.WerkstoffService.Eingabe;
import org.example.kalkulationsprogramm.service.WerkstoffService.Zeile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Werkstoffe pflegen (Stammdaten). Die Prüfungen und verständlichen Meldungen stehen im {@link WerkstoffService}. */
@RestController
@RequestMapping("/api/werkstoffe")
@RequiredArgsConstructor
public class WerkstoffController {

    private final WerkstoffService service;

    @GetMapping
    public List<Zeile> alle() {
        return service.alle();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Zeile anlegen(@RequestBody Eingabe eingabe) {
        return service.anlegen(eingabe);
    }

    @PutMapping("/{id}")
    public Zeile aendern(@PathVariable Long id, @RequestBody Eingabe eingabe) {
        return service.aendern(id, eingabe);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void loeschen(@PathVariable Long id) {
        service.loeschen(id);
    }
}
