package org.example.kalkulationsprogramm.controller;

import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.dto.Ids.IdsArtikelstammAnfrageDto;
import org.example.kalkulationsprogramm.dto.Ids.IdsArtikelstammErgebnisDto;
import org.example.kalkulationsprogramm.dto.Ids.IdsVerfuegbarDto;
import org.example.kalkulationsprogramm.dto.Ids.IdsVorgangDto;
import org.example.kalkulationsprogramm.dto.Ids.IdsVorgangGestartetDto;
import org.example.kalkulationsprogramm.dto.Ids.IdsVorgangStartDto;
import org.example.kalkulationsprogramm.service.ids.IdsArtikelstammAbgleich;
import org.example.kalkulationsprogramm.service.ids.IdsEinstellungenService;
import org.example.kalkulationsprogramm.service.ids.IdsShopBesuchService;
import org.example.kalkulationsprogramm.service.ids.ShopFormular;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Lieferanten-Shop im Dokumenteditor (und später anderswo): Shop öffnen, Warenkorb abholen,
 * Artikelstamm abgleichen. Jeder angemeldete PC-Benutzer; ein Warenkorb gehört dem, der den Shop geöffnet hat.
 */
@RestController
@RequestMapping("/api/ids")
@RequiredArgsConstructor
public class IdsShopController {

    /** Nur Schema, Host und Port – so schickt der Browser seinen {@code Origin}. */
    private static final Pattern ORIGIN = Pattern.compile("https?://[A-Za-z0-9.-]{1,253}(:\\d{1,5})?");

    private final IdsEinstellungenService einstellungen;
    private final IdsShopBesuchService shopBesuch;
    private final IdsArtikelstammAbgleich abgleich;

    @GetMapping("/verfuegbar")
    public IdsVerfuegbarDto verfuegbar() {
        return einstellungen.verfuegbar();
    }

    /**
     * Startet einen Shop-Besuch. Die Antwort enthält das Shop-Passwort im Formular (der Browser muss es an
     * den Shop schicken) und wird deshalb nirgends zwischengespeichert.
     */
    @PostMapping("/vorgaenge")
    public ResponseEntity<IdsVorgangGestartetDto> starte(@Valid @RequestBody IdsVorgangStartDto dto,
            @RequestHeader(name = "Origin", required = false) String origin, Authentication authentication) {
        IdsShopBesuchService.Gestartet gestartet = shopBesuch.starte(dto.lieferantId(),
                FrontendUserPrincipal.idAus(authentication), ruecksprungBasis(origin));
        ShopFormular f = gestartet.formular();
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(new IdsVorgangGestartetDto(gestartet.vorgangId(),
                        new IdsVorgangGestartetDto.Formular(f.action(), f.enctype(), f.felder())));
    }

    @GetMapping("/vorgaenge/{vorgangId}")
    public IdsVorgangDto lade(@PathVariable Long vorgangId, Authentication authentication) {
        return abgleich.ansicht(vorgangId, FrontendUserPrincipal.idAus(authentication));
    }

    @PostMapping("/vorgaenge/{vorgangId}/artikelstamm")
    public IdsArtikelstammErgebnisDto artikelstamm(@PathVariable Long vorgangId,
            @RequestBody IdsArtikelstammAnfrageDto anfrage, Authentication authentication) {
        return abgleich.uebernimm(vorgangId, FrontendUserPrincipal.idAus(authentication), anfrage);
    }

    /**
     * Adresse, unter der der Browser dieses Programm erreicht – dorthin schickt der Shop den Warenkorb zurück.
     * Der {@code Origin} des Aufrufs ist genau diese Adresse (auch hinter Tailscale oder im Entwicklungs-Proxy);
     * ohne ihn die Adresse der Anfrage.
     */
    static String ruecksprungBasis(String origin) {
        if (origin != null && ORIGIN.matcher(origin).matches()) {
            return origin;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
    }
}
