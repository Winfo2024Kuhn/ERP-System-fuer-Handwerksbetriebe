package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.AbteilungDokumentBerechtigung;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.dto.AbteilungBerechtigungDto;
import org.example.kalkulationsprogramm.repository.AbteilungDokumentBerechtigungRepository;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/abteilungen")
@RequiredArgsConstructor
public class AbteilungBerechtigungController {

    private final AbteilungRepository abteilungRepository;
    private final AbteilungDokumentBerechtigungRepository berechtigungRepository;

    /**
     * Listet alle Abteilungen mit ihren Dokument-Berechtigungen.
     */
    @GetMapping("/berechtigungen")
    public ResponseEntity<List<AbteilungBerechtigungDto.Response>> getAllBerechtigungen() {
        List<Abteilung> abteilungen = abteilungRepository.findAll();
        List<AbteilungBerechtigungDto.Response> result = new ArrayList<>();

        for (Abteilung abt : abteilungen) {
            List<AbteilungDokumentBerechtigung> berechtigungen = berechtigungRepository.findByAbteilungId(abt.getId());
            Map<LieferantDokumentTyp, AbteilungDokumentBerechtigung> map = berechtigungen.stream()
                .filter(b -> b.getDokumentTyp() != null)
                .collect(Collectors.toMap(
                    AbteilungDokumentBerechtigung::getDokumentTyp, 
                    Function.identity(), 
                (oldVal, newVal) -> oldVal
                ));

            List<AbteilungBerechtigungDto.TypBerechtigung> typBerechtigungen = new ArrayList<>();
            for (LieferantDokumentTyp typ : LieferantDokumentTyp.values()) {
                AbteilungDokumentBerechtigung b = map.get(typ);
                typBerechtigungen.add(AbteilungBerechtigungDto.TypBerechtigung.builder()
                    .typ(typ)
                    .darfSehen(b != null && Boolean.TRUE.equals(b.getDarfSehen()))
                    .darfScannen(b != null && Boolean.TRUE.equals(b.getDarfScannen()))
                    .build());
            }

            result.add(AbteilungBerechtigungDto.Response.builder()
                .abteilungId(abt.getId())
                .abteilungName(abt.getName())
                .berechtigungen(typBerechtigungen)
                .darfRechnungenGenehmigen(Boolean.TRUE.equals(abt.getDarfRechnungenGenehmigen()))
                .darfRechnungenSehen(Boolean.TRUE.equals(abt.getDarfRechnungenSehen()))
                .build());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Gibt die Berechtigungen einer einzelnen Abteilung zurück.
     */
    @GetMapping("/{id}/berechtigungen")
    public ResponseEntity<AbteilungBerechtigungDto.Response> getBerechtigungen(@PathVariable Long id) {
        Abteilung abteilung = abteilungRepository.findById(id).orElse(null);
        if (abteilung == null) {
            return ResponseEntity.notFound().build();
        }

        List<AbteilungDokumentBerechtigung> berechtigungen = berechtigungRepository.findByAbteilungId(id);
        Map<LieferantDokumentTyp, AbteilungDokumentBerechtigung> map = berechtigungen.stream()
            .filter(b -> b.getDokumentTyp() != null)
            .collect(Collectors.toMap(
                AbteilungDokumentBerechtigung::getDokumentTyp, 
                Function.identity(), 
                (oldVal, newVal) -> oldVal
            ));

        List<AbteilungBerechtigungDto.TypBerechtigung> typBerechtigungen = new ArrayList<>();
        for (LieferantDokumentTyp typ : LieferantDokumentTyp.values()) {
            AbteilungDokumentBerechtigung b = map.get(typ);
            typBerechtigungen.add(AbteilungBerechtigungDto.TypBerechtigung.builder()
                .typ(typ)
                .darfSehen(b != null && Boolean.TRUE.equals(b.getDarfSehen()))
                .darfScannen(b != null && Boolean.TRUE.equals(b.getDarfScannen()))
                .build());
        }

        return ResponseEntity.ok(AbteilungBerechtigungDto.Response.builder()
            .abteilungId(abteilung.getId())
            .abteilungName(abteilung.getName())
            .berechtigungen(typBerechtigungen)
            .darfRechnungenGenehmigen(Boolean.TRUE.equals(abteilung.getDarfRechnungenGenehmigen()))
            .darfRechnungenSehen(Boolean.TRUE.equals(abteilung.getDarfRechnungenSehen()))
            .build());
    }

    /**
     * Aktualisiert die Berechtigungen einer Abteilung.
     */
    @PutMapping("/{id}/berechtigungen")
    @Transactional
    public ResponseEntity<AbteilungBerechtigungDto.Response> updateBerechtigungen(
            @PathVariable Long id,
            @RequestBody AbteilungBerechtigungDto.UpdateRequest request) {

        Abteilung abteilung = abteilungRepository.findById(id).orElse(null);
        if (abteilung == null) {
            return ResponseEntity.notFound().build();
        }

        // Bestehende Berechtigungen laden und Duplikate bereinigen
        List<AbteilungDokumentBerechtigung> existing = berechtigungRepository.findByAbteilungId(id);
        
        // Gruppieren nach Typ um Duplikate zu finden (z.B. RECHNUNG vs EINGANGSRECHNUNG -> beides RECHNUNG)
        Map<LieferantDokumentTyp, List<AbteilungDokumentBerechtigung>> grouped = existing.stream()
            .filter(b -> b.getDokumentTyp() != null)
            .collect(Collectors.groupingBy(AbteilungDokumentBerechtigung::getDokumentTyp));

        java.util.Map<LieferantDokumentTyp, AbteilungDokumentBerechtigung> map = new java.util.HashMap<>();
        List<AbteilungDokumentBerechtigung> toDelete = new ArrayList<>();

        grouped.forEach((typ, list) -> {
            // Den ersten Eintrag behalten wir, den Rest löschen wir
            map.put(typ, list.getFirst());
            if (list.size() > 1) {
                for (int i = 1; i < list.size(); i++) {
                    toDelete.add(list.get(i));
                }
            }
        });

        // Duplikate löschen falls vorhanden
        if (!toDelete.isEmpty()) {
            berechtigungRepository.deleteAll(toDelete);
            berechtigungRepository.flush();
        }

        // Offene-Posten-Flags aktualisieren
        if (request.getDarfRechnungenGenehmigen() != null) {
            abteilung.setDarfRechnungenGenehmigen(request.getDarfRechnungenGenehmigen());
        }
        if (request.getDarfRechnungenSehen() != null) {
            abteilung.setDarfRechnungenSehen(request.getDarfRechnungenSehen());
        }
        abteilungRepository.save(abteilung);

        // Berechtigungen aktualisieren oder erstellen
        for (AbteilungBerechtigungDto.TypBerechtigung tb : request.getBerechtigungen()) {
            AbteilungDokumentBerechtigung b = map.get(tb.getTyp());
            if (b == null) {
                b = new AbteilungDokumentBerechtigung();
                b.setAbteilung(abteilung);
                b.setDokumentTyp(tb.getTyp());
            }
            b.setDarfSehen(Boolean.TRUE.equals(tb.getDarfSehen()));
            b.setDarfScannen(Boolean.TRUE.equals(tb.getDarfScannen()));
            berechtigungRepository.save(b);
        }

        return getBerechtigungen(id);
    }

    /**
     * Gibt alle verfügbaren Dokumenttypen zurück.
     */
    @GetMapping("/dokumenttypen")
    public ResponseEntity<List<String>> getDokumentTypen() {
        return ResponseEntity.ok(Arrays.stream(LieferantDokumentTyp.values())
            .map(Enum::name)
            .collect(Collectors.toList()));
    }
}
