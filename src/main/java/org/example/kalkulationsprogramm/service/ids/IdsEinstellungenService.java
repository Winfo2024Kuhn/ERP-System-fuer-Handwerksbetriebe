package org.example.kalkulationsprogramm.service.ids;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.ids.IdsProtokoll;
import org.example.kalkulationsprogramm.domain.ids.IdsZugang;
import org.example.kalkulationsprogramm.dto.Ids.IdsEinstellungenDto;
import org.example.kalkulationsprogramm.dto.Ids.IdsVerfuegbarDto;
import org.example.kalkulationsprogramm.dto.Ids.IdsZugangDto;
import org.example.kalkulationsprogramm.dto.Ids.IdsZugangSpeichernDto;
import org.example.kalkulationsprogramm.repository.IdsZugangRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.mail.MailSecretService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Einrichtung der Shop-Anbindung: Grundschalter in {@code system_setting} und je Lieferant ein
 * {@link IdsZugang}. Passwörter werden mit {@link MailSecretService} verschlüsselt (derselbe Schlüssel
 * wie bei den E-Mail-Postfächern) und nie zurückgegeben.
 */
@Service
@RequiredArgsConstructor
public class IdsEinstellungenService {

    static final String AKTIV = "ids.aktiv";
    static final int MAX_URL = 500;
    static final int MAX_FELD = 50;
    static final int MAX_PASSWORT = 100;

    private final SystemSettingsService settings;
    private final MailSecretService secrets;
    private final IdsZugangRepository zugangRepository;
    private final LieferantenRepository lieferantenRepository;

    @Transactional(readOnly = true)
    public IdsEinstellungenDto lade() {
        return new IdsEinstellungenDto(istAktiv(), secrets.isConfigured(),
                zugangRepository.findeAlleMitLieferant().stream().map(IdsEinstellungenService::zuDto).toList());
    }

    @Transactional
    public IdsEinstellungenDto setzeAktiv(boolean aktiv) {
        settings.save(AKTIV, Boolean.toString(aktiv), "Shop-Anbindung (IDS) an/aus");
        return lade();
    }

    public boolean istAktiv() {
        return Boolean.parseBoolean(settings.get(AKTIV, "false"));
    }

    @Transactional
    public IdsZugangDto speichereZugang(Long lieferantId, IdsZugangSpeichernDto dto) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte die Angaben zum Shop-Zugang mitschicken.");
        }
        Lieferanten lieferant = lieferantenRepository.findById(lieferantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Der Lieferant wurde nicht gefunden."));
        String shopUrl = leerAlsNull(dto.shopUrl());
        if (shopUrl != null) {
            pruefeShopUrl(shopUrl);
        } else if (dto.aktiv()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Zum Einschalten bitte die Shop-Adresse eintragen, die der Lieferant für IDS mitgeteilt hat.");
        }
        String kundennummer = begrenze(leerAlsNull(dto.kundennummer()), MAX_FELD, "Die Kundennummer");
        String benutzername = begrenze(leerAlsNull(dto.benutzername()), MAX_FELD, "Der Benutzername");
        String passwort = dto.passwort() == null || dto.passwort().isEmpty() ? null
                : begrenze(dto.passwort(), MAX_PASSWORT, "Das Passwort");

        IdsZugang zugang = zugangRepository.findByLieferant_Id(lieferantId).orElseGet(() -> {
            IdsZugang neu = new IdsZugang();
            neu.setLieferant(lieferant);
            return neu;
        });
        if (passwort != null) {
            if (!secrets.isConfigured()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Zum Speichern von Passwörtern fehlt der Verschlüsselungsschlüssel – derselbe wie für die "
                                + "E-Mail-Postfächer (mail.credentials.encryption-key). Bitte zuerst einrichten.");
            }
            zugang.setPasswortVerschluesselt(secrets.encrypt(passwort));
        }
        zugang.setAktiv(dto.aktiv());
        zugang.setProtokoll(dto.protokoll() != null ? dto.protokoll() : IdsProtokoll.IDS_CONNECT_2_5);
        zugang.setShopUrl(shopUrl);
        zugang.setKundennummer(kundennummer);
        zugang.setBenutzername(benutzername);
        zugang.setGeaendertAm(LocalDateTime.now());
        return zuDto(zugangRepository.save(zugang));
    }

    @Transactional
    public void loescheZugang(Long lieferantId) {
        IdsZugang zugang = zugangRepository.findByLieferant_Id(lieferantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Für diesen Lieferanten ist kein Shop-Zugang eingerichtet."));
        zugangRepository.delete(zugang);
    }

    /** Für den Knopf im Dokumenteditor: nur bei eingeschaltetem Grundschalter und mit nutzbaren Zugängen. */
    @Transactional(readOnly = true)
    public IdsVerfuegbarDto verfuegbar() {
        if (!istAktiv()) {
            return new IdsVerfuegbarDto(false, List.of());
        }
        List<IdsVerfuegbarDto.Lieferant> lieferanten = zugangRepository.findeAlleMitLieferant().stream()
                .filter(this::istNutzbar)
                .map(z -> new IdsVerfuegbarDto.Lieferant(z.getLieferant().getId(), z.getLieferant().getLieferantenname(),
                        z.getPasswortVerschluesselt() != null && !z.getPasswortVerschluesselt().isBlank()))
                .toList();
        return new IdsVerfuegbarDto(!lieferanten.isEmpty(), lieferanten);
    }

    /**
     * Entschlüsselte Zugangsdaten für genau einen Shop-Besuch.
     *
     * @throws ResponseStatusException 400 mit verständlicher Meldung, wenn der Zugang nicht nutzbar ist
     */
    @Transactional(readOnly = true)
    public NutzbarerZugang nutzbarerZugang(Long lieferantId) {
        if (!istAktiv()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Die Shop-Anbindung ist ausgeschaltet (Einstellungen → Lieferanten-Shops).");
        }
        IdsZugang zugang = zugangRepository.findByLieferant_Id(lieferantId)
                .filter(IdsZugang::istBereit)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Für diesen Lieferanten ist kein eingeschalteter Shop-Zugang eingerichtet."));
        String passwort = null;
        if (zugang.getPasswortVerschluesselt() != null) {
            try {
                passwort = secrets.decrypt(zugang.getPasswortVerschluesselt());
            } catch (IllegalStateException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Das gespeicherte Shop-Passwort ist nicht lesbar. Bitte in den Einstellungen neu eintragen.");
            }
        }
        return new NutzbarerZugang(zugang.getLieferant(), zugang.getProtokoll(),
                new IdsZugangsdaten(zugang.getShopUrl(), zugang.getKundennummer(), zugang.getBenutzername(), passwort));
    }

    /** Ein einsatzbereiter Zugang samt entschlüsselter Daten. */
    public record NutzbarerZugang(Lieferanten lieferant, IdsProtokoll protokoll, IdsZugangsdaten daten) {
    }

    private boolean istNutzbar(IdsZugang z) {
        return z.istBereit() && (z.getPasswortVerschluesselt() == null || secrets.isConfigured());
    }

    /** Nur {@code https://}; {@code http://} ausschließlich für Tests auf diesem Rechner. */
    static void pruefeShopUrl(String url) {
        if (url.length() > MAX_URL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Shop-Adresse ist zu lang.");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Shop-Adresse ist keine gültige Internetadresse.");
        }
        String schema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Shop-Adresse ist keine gültige Internetadresse.");
        }
        boolean lokal = host.equals("localhost") || host.equals("127.0.0.1");
        if ("https".equals(schema) || ("http".equals(schema) && lokal)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Shop-Adresse muss mit https:// beginnen.");
    }

    static IdsZugangDto zuDto(IdsZugang z) {
        return new IdsZugangDto(z.getLieferant().getId(), z.getLieferant().getLieferantenname(), z.isAktiv(),
                z.getProtokoll(), z.getShopUrl(), z.getKundennummer(), z.getBenutzername(),
                z.getPasswortVerschluesselt() != null && !z.getPasswortVerschluesselt().isBlank(), z.getGeaendertAm());
    }

    private static String leerAlsNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String begrenze(String wert, int max, String feld) {
        if (wert != null && wert.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, feld + " darf höchstens " + max + " Zeichen lang sein.");
        }
        return wert;
    }
}
