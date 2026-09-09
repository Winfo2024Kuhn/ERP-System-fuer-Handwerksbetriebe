package org.example.kalkulationsprogramm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.ZeitkontenmodellDto;
import org.example.kalkulationsprogramm.service.ZeitkontenmodellService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/zeitverwaltung/zeitkontenmodelle")
@RequiredArgsConstructor
public class ZeitkontenmodellController {
    private final ZeitkontenmodellService service;

    @GetMapping
    public List<ZeitkontenmodellDto> alle() { return service.alle(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ZeitkontenmodellDto erstellen(@Valid @RequestBody ZeitkontenmodellDto.Create request) {
        return service.erstellen(request);
    }

    @PutMapping("/{id}")
    public ZeitkontenmodellDto aktualisieren(@PathVariable Long id,
            @Valid @RequestBody ZeitkontenmodellDto.Update request) {
        return service.aktualisieren(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void loeschen(@PathVariable Long id, @RequestParam Long expectedVersion) {
        service.loeschen(id, expectedVersion);
    }
}
