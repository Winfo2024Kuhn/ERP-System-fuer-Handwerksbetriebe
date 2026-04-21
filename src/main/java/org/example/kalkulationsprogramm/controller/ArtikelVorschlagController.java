package org.example.kalkulationsprogramm.controller;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelVorschlag;
import org.example.kalkulationsprogramm.domain.ArtikelVorschlagStatus;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.example.kalkulationsprogramm.dto.ArtikelVorschlagDto;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.ArtikelVorschlagRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.LieferantenArtikelPreiseRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.example.kalkulationsprogramm.service.ArtikelPreisHookService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Review-API für "Vorgeschlagene neue Materialien" (ArtikelVorschlag).
 * Der Nutzer kann Vorschläge ansehen, korrigieren, freigeben oder ablehnen.
 * Bei Freigabe wird ein echter Artikel (ArtikelWerkstoffe-Subtyp) inkl.
 * LieferantenArtikelPreise angelegt.
 */
@RestController
@RequestMapping("/api/artikel-vorschlaege")
@RequiredArgsConstructor
public class ArtikelVorschlagController {

    private final ArtikelVorschlagRepository vorschlagRepository;
    private final ArtikelRepository artikelRepository;
    private final KategorieRepository kategorieRepository;
    private final WerkstoffRepository werkstoffRepository;
    private final LieferantenArtikelPreiseRepository preiseRepository;
    private final ArtikelPreisHookService preisHookService;

    @GetMapping
    @Transactional(readOnly = true)
    public List<ArtikelVorschlagDto.Response> liste(
            @RequestParam(value = "status", defaultValue = "PENDING") String statusParam) {
        ArtikelVorschlagStatus status = parseStatus(statusParam);
        return vorschlagRepository.findByStatusOrderByErstelltAmDesc(status).stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/count")
    @Transactional(readOnly = true)
    public ArtikelVorschlagDto.CountResponse pendingCount() {
        return new ArtikelVorschlagDto.CountResponse(
                vorschlagRepository.countByStatus(ArtikelVorschlagStatus.PENDING));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ArtikelVorschlagDto.Response> detail(@PathVariable("id") Long id) {
        return vorschlagRepository.findById(id)
                .map(v -> ResponseEntity.ok(toDto(v)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    @Transactional
    public ResponseEntity<ArtikelVorschlagDto.Response> patch(@PathVariable("id") Long id,
            @RequestBody ArtikelVorschlagDto.UpdateRequest req) {
        Optional<ArtikelVorschlag> opt = vorschlagRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ArtikelVorschlag v = opt.get();
        if (v.getStatus() != ArtikelVorschlagStatus.PENDING) {
            return ResponseEntity.badRequest().build();
        }
        applyUpdate(v, req);
        return ResponseEntity.ok(toDto(vorschlagRepository.save(v)));
    }

    private void applyUpdate(ArtikelVorschlag v, ArtikelVorschlagDto.UpdateRequest req) {
        if (req == null) return;
        if (req.getProduktname() != null) v.setProduktname(req.getProduktname());
        if (req.getProduktlinie() != null) v.setProduktlinie(req.getProduktlinie());
        if (req.getProdukttext() != null) v.setProdukttext(req.getProdukttext());
        if (req.getExterneArtikelnummer() != null) v.setExterneArtikelnummer(req.getExterneArtikelnummer());
        if (req.getKategorieId() != null) {
            kategorieRepository.findById(req.getKategorieId()).ifPresent(v::setVorgeschlageneKategorie);
        }
        if (req.getWerkstoffId() != null) {
            werkstoffRepository.findById(req.getWerkstoffId()).ifPresent(v::setVorgeschlagenerWerkstoff);
        }
        if (req.getMasse() != null) v.setMasse(req.getMasse());
        if (req.getHoehe() != null) v.setHoehe(req.getHoehe());
        if (req.getBreite() != null) v.setBreite(req.getBreite());
        if (req.getEinzelpreis() != null) v.setEinzelpreis(req.getEinzelpreis());
        if (req.getPreiseinheit() != null) v.setPreiseinheit(req.getPreiseinheit());
        v.setBearbeitetAm(LocalDateTime.now());
    }

    @PostMapping("/{id}/approve")
    @Transactional
    public ResponseEntity<ArtikelVorschlagDto.Response> approve(@PathVariable("id") Long id,
            @RequestBody(required = false) ArtikelVorschlagDto.UpdateRequest req) {
        Optional<ArtikelVorschlag> opt = vorschlagRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ArtikelVorschlag v = opt.get();
        if (v.getStatus() != ArtikelVorschlagStatus.PENDING) {
            return ResponseEntity.badRequest().build();
        }
        if (req != null) {
            applyUpdate(v, req);
        }

        // Artikel anlegen — ArtikelWerkstoffe-Subtyp mit Maßen
        ArtikelWerkstoffe artikel = new ArtikelWerkstoffe();
        artikel.setProduktname(v.getProduktname());
        artikel.setProduktlinie(v.getProduktlinie());
        artikel.setProdukttext(v.getProdukttext());
        artikel.setPreiseinheit(v.getPreiseinheit());
        artikel.setKategorie(v.getVorgeschlageneKategorie());
        artikel.setWerkstoff(v.getVorgeschlagenerWerkstoff());
        artikel.setMasse(v.getMasse());
        if (v.getHoehe() != null) artikel.setHoehe(v.getHoehe());
        if (v.getBreite() != null) artikel.setBreite(v.getBreite());

        Artikel gespeichert = artikelRepository.save(artikel);

        // LieferantenArtikelPreise anlegen (Preis + externe Nummer in einem Datensatz)
        if (v.getLieferant() != null
                && (v.getEinzelpreis() != null
                    || (v.getExterneArtikelnummer() != null && !v.getExterneArtikelnummer().isBlank()))) {
            LieferantenArtikelPreise lap = new LieferantenArtikelPreise();
            lap.setArtikel(gespeichert);
            lap.setLieferant(v.getLieferant());
            lap.setExterneArtikelnummer(v.getExterneArtikelnummer());
            lap.setPreis(v.getEinzelpreis());
            lap.setPreisAenderungsdatum(new Date());
            gespeichert.getArtikelpreis().add(lap);
            preiseRepository.save(lap);
            if (v.getEinzelpreis() != null) {
                preisHookService.registriere(gespeichert, v.getLieferant(), v.getEinzelpreis(),
                        gespeichert.getVerrechnungseinheit(),
                        PreisQuelle.VORSCHLAG, v.getExterneArtikelnummer());
            }
        }

        v.setStatus(ArtikelVorschlagStatus.APPROVED);
        v.setBearbeitetAm(LocalDateTime.now());
        v.setTrefferArtikel(gespeichert);
        return ResponseEntity.ok(toDto(vorschlagRepository.save(v)));
    }

    @PostMapping("/{id}/reject")
    @Transactional
    public ResponseEntity<ArtikelVorschlagDto.Response> reject(@PathVariable("id") Long id) {
        return vorschlagRepository.findById(id)
                .map(v -> {
                    if (v.getStatus() != ArtikelVorschlagStatus.PENDING) {
                        return ResponseEntity.badRequest().<ArtikelVorschlagDto.Response>build();
                    }
                    v.setStatus(ArtikelVorschlagStatus.REJECTED);
                    v.setBearbeitetAm(LocalDateTime.now());
                    return ResponseEntity.ok(toDto(vorschlagRepository.save(v)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        if (!vorschlagRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        vorschlagRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private ArtikelVorschlagDto.Response toDto(ArtikelVorschlag v) {
        ArtikelVorschlagDto.Response dto = new ArtikelVorschlagDto.Response();
        dto.setId(v.getId());
        dto.setStatus(v.getStatus() != null ? v.getStatus().name() : null);
        dto.setTyp(v.getTyp() != null ? v.getTyp().name() : null);
        dto.setErstelltAm(v.getErstelltAm());
        dto.setBearbeitetAm(v.getBearbeitetAm());
        if (v.getLieferant() != null) {
            dto.setLieferantId(v.getLieferant().getId());
            dto.setLieferantName(v.getLieferant().getLieferantenname());
        }
        if (v.getQuelleDokument() != null) {
            dto.setQuelleDokumentId(v.getQuelleDokument().getId());
            dto.setQuelleDokumentBezeichnung(v.getQuelleDokument().getOriginalDateiname());
        }
        dto.setExterneArtikelnummer(v.getExterneArtikelnummer());
        dto.setProduktname(v.getProduktname());
        dto.setProduktlinie(v.getProduktlinie());
        dto.setProdukttext(v.getProdukttext());
        if (v.getVorgeschlageneKategorie() != null) {
            Kategorie k = v.getVorgeschlageneKategorie();
            dto.setVorgeschlageneKategorieId(k.getId());
            dto.setVorgeschlageneKategoriePfad(buildPfad(k));
        }
        if (v.getVorgeschlagenerWerkstoff() != null) {
            Werkstoff w = v.getVorgeschlagenerWerkstoff();
            dto.setVorgeschlagenerWerkstoffId(w.getId());
            dto.setVorgeschlagenerWerkstoffName(w.getName());
        }
        dto.setMasse(v.getMasse());
        dto.setHoehe(v.getHoehe());
        dto.setBreite(v.getBreite());
        dto.setEinzelpreis(v.getEinzelpreis());
        dto.setPreiseinheit(v.getPreiseinheit());
        dto.setKiKonfidenz(v.getKiKonfidenz());
        dto.setKiBegruendung(v.getKiBegruendung());
        if (v.getKonfliktArtikel() != null) {
            dto.setKonfliktArtikelId(v.getKonfliktArtikel().getId());
            dto.setKonfliktArtikelName(v.getKonfliktArtikel().getProduktname());
        }
        if (v.getTrefferArtikel() != null) {
            dto.setTrefferArtikelId(v.getTrefferArtikel().getId());
            dto.setTrefferArtikelName(v.getTrefferArtikel().getProduktname());
        }
        return dto;
    }

    private String buildPfad(Kategorie k) {
        StringBuilder sb = new StringBuilder();
        Kategorie cur = k;
        java.util.Deque<String> stack = new java.util.ArrayDeque<>();
        while (cur != null) {
            stack.push(cur.getBeschreibung() != null ? cur.getBeschreibung() : "?");
            cur = cur.getParentKategorie();
        }
        while (!stack.isEmpty()) {
            sb.append(stack.pop());
            if (!stack.isEmpty()) sb.append(" > ");
        }
        return sb.toString();
    }

    private ArtikelVorschlagStatus parseStatus(String raw) {
        try {
            return ArtikelVorschlagStatus.valueOf(raw.toUpperCase());
        } catch (Exception e) {
            return ArtikelVorschlagStatus.PENDING;
        }
    }
}
