package org.example.kalkulationsprogramm.service.ids;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.ids.IdsProtokoll;
import org.example.kalkulationsprogramm.domain.ids.IdsVorgang;
import org.example.kalkulationsprogramm.domain.ids.IdsVorgangStatus;
import org.example.kalkulationsprogramm.domain.ids.IdsWarenkorbPosition;
import org.example.kalkulationsprogramm.repository.IdsVorgangRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ein Shop-Besuch von Anfang bis Ende, unabhängig davon, wer den Warenkorb später nutzt.
 *
 * <ol>
 *   <li>{@link #starte}: legt einen {@link IdsVorgang} an und liefert das Anmeldeformular für den Shop.
 *       Die Rücksprung-Adresse trägt einen zufälligen Schlüssel (256 Bit); in der Datenbank steht nur sein Hash.</li>
 *   <li>{@link #nimmRueckgabeAn}: Der Shop schickt den Warenkorb über den Browser an die Rücksprung-Adresse.
 *       Dabei kommt keine Anmeldung mit (Cookie mit SameSite=Lax), deshalb ist der Schlüssel der einzige Ausweis:
 *       nur einmal einlösbar und {@value #GUELTIG_MINUTEN} Minuten gültig.</li>
 *   <li>{@link #lade}: Der Benutzer, der den Shop geöffnet hat, holt den Warenkorb ab.</li>
 * </ol>
 */
@Service
public class IdsShopBesuchService {

    static final long GUELTIG_MINUTEN = 60;
    /** Abgeschlossene Shop-Besuche werden nach dieser Zeit beim nächsten Start weggeräumt. */
    static final Duration AUFBEWAHRUNG = Duration.ofDays(30);
    static final String RUECKSPRUNG_PFAD = "/api/ids/rueckgabe/";
    private static final Pattern SCHLUESSEL_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final IdsVorgangRepository vorgangRepository;
    private final IdsEinstellungenService einstellungen;
    private final Map<IdsProtokoll, IdsProtokollAdapter> adapter = new EnumMap<>(IdsProtokoll.class);
    private final Clock clock;
    private final SecureRandom zufall = new SecureRandom();

    @Autowired
    public IdsShopBesuchService(IdsVorgangRepository vorgangRepository, IdsEinstellungenService einstellungen,
            List<IdsProtokollAdapter> adapterListe) {
        this(vorgangRepository, einstellungen, adapterListe, Clock.systemDefaultZone());
    }

    IdsShopBesuchService(IdsVorgangRepository vorgangRepository, IdsEinstellungenService einstellungen,
            List<IdsProtokollAdapter> adapterListe, Clock clock) {
        this.vorgangRepository = vorgangRepository;
        this.einstellungen = einstellungen;
        this.clock = clock;
        adapterListe.forEach(a -> adapter.put(a.protokoll(), a));
    }

    /** Ergebnis des Starts: Vorgang und das Formular, das der Browser an den Shop schickt. */
    public record Gestartet(Long vorgangId, ShopFormular formular) {
    }

    /** Was der Rücksprung bewirkt hat; die Oberfläche erklärt jedes Ergebnis in eigenen Worten. */
    public enum Ergebnis { OK, ABGELAUFEN, UNGUELTIG, LEER, FEHLER }

    public record Rueckgabe(Long vorgangId, Ergebnis ergebnis) {
    }

    /**
     * @param ruecksprungBasis Adresse dieses Programms, wie der Browser sie benutzt (z. B. {@code https://erp.firma.ts.net})
     */
    @Transactional
    public Gestartet starte(Long lieferantId, Long frontendUserId, String ruecksprungBasis) {
        if (frontendUserId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Den Lieferanten-Shop können nur angemeldete Benutzer am PC öffnen.");
        }
        IdsEinstellungenService.NutzbarerZugang zugang = einstellungen.nutzbarerZugang(lieferantId);
        IdsProtokollAdapter protokoll = adapterFuer(zugang.protokoll());

        LocalDateTime jetzt = LocalDateTime.now(clock);
        vorgangRepository.loeschePositionenAelterAls(jetzt.minus(AUFBEWAHRUNG));
        vorgangRepository.loescheAelterAls(jetzt.minus(AUFBEWAHRUNG));

        String schluessel = neuerSchluessel();
        IdsVorgang vorgang = new IdsVorgang();
        vorgang.setLieferant(zugang.lieferant());
        vorgang.setFrontendUserId(frontendUserId);
        vorgang.setProtokoll(zugang.protokoll());
        vorgang.setSchluesselHash(hash(schluessel));
        vorgang.setStatus(IdsVorgangStatus.WARTET);
        vorgang.setErstelltAm(jetzt);
        vorgang.setGueltigBis(jetzt.plusMinutes(GUELTIG_MINUTEN));
        vorgang = vorgangRepository.save(vorgang);

        String ruecksprung = ruecksprungBasis + RUECKSPRUNG_PFAD + schluessel;
        return new Gestartet(vorgang.getId(), protokoll.anmeldeFormular(zugang.daten(), ruecksprung));
    }

    /** Nimmt den Warenkorb an, den der Shop über den Browser zurückgeschickt hat. Wirft nie – jedes Ende hat ein Ergebnis. */
    @Transactional
    public Rueckgabe nimmRueckgabeAn(String schluessel, IdsRueckgabe rueckgabe) {
        if (schluessel == null || !SCHLUESSEL_FORMAT.matcher(schluessel).matches()) {
            return new Rueckgabe(null, Ergebnis.UNGUELTIG);
        }
        IdsVorgang vorgang = vorgangRepository.findBySchluesselHashGesperrt(hash(schluessel)).orElse(null);
        if (vorgang == null || vorgang.getStatus() != IdsVorgangStatus.WARTET) {
            // Unbekannt oder schon eingelöst: ein zweiter Rücksprung ändert nichts.
            return new Rueckgabe(null, Ergebnis.UNGUELTIG);
        }
        LocalDateTime jetzt = LocalDateTime.now(clock);
        if (jetzt.isAfter(vorgang.getGueltigBis())) {
            vorgang.setStatus(IdsVorgangStatus.ABGELAUFEN);
            return new Rueckgabe(vorgang.getId(), Ergebnis.ABGELAUFEN);
        }
        if (rueckgabe == null || rueckgabe.istLeer()) {
            vorgang.setStatus(IdsVorgangStatus.FEHLER);
            vorgang.setFehlertext("Der Shop hat einen leeren Warenkorb geschickt.");
            return new Rueckgabe(vorgang.getId(), Ergebnis.LEER);
        }
        vorgang.setRohdaten(rueckgabe.alsText());
        IdsWarenkorb warenkorb;
        try {
            warenkorb = adapterFuer(vorgang.getProtokoll()).leseWarenkorb(rueckgabe);
        } catch (IdsWarenkorbUngueltig e) {
            vorgang.setStatus(IdsVorgangStatus.FEHLER);
            vorgang.setFehlertext(e.getMessage());
            return new Rueckgabe(vorgang.getId(), Ergebnis.FEHLER);
        }
        int index = 0;
        for (IdsWarenkorb.Zeile zeile : warenkorb.zeilen()) {
            IdsWarenkorbPosition p = new IdsWarenkorbPosition();
            p.setPositionIndex(index++);
            p.setArtikelnummer(zeile.artikelnummer());
            p.setBezeichnung(zeile.bezeichnung());
            p.setLangtext(zeile.langtext());
            p.setMenge(zeile.menge());
            p.setEinheit(zeile.einheit());
            p.setEinkaufspreis(zeile.einkaufspreis());
            p.setHinweis(zeile.hinweis());
            p.setEan(zeile.ean());
            vorgang.addPosition(p);
        }
        vorgang.setMitBestellung(warenkorb.mitBestellung());
        vorgang.setStatus(IdsVorgangStatus.EMPFANGEN);
        vorgang.setEmpfangenAm(jetzt);
        return new Rueckgabe(vorgang.getId(), Ergebnis.OK);
    }

    /**
     * Shop-Besuch für den Benutzer, der ihn gestartet hat, samt Lieferant und Warenkorb. Ein abgelaufener,
     * noch wartender Besuch wird dabei als abgelaufen markiert. Wer mit dem Ergebnis weiterarbeitet, ruft
     * das in seiner eigenen Transaktion auf (ohne Open-in-View ist die Entity danach sonst abgehängt).
     *
     * @throws ResponseStatusException 404, wenn unbekannt oder von jemand anderem
     */
    @Transactional
    public IdsVorgang lade(Long vorgangId, Long frontendUserId) {
        IdsVorgang vorgang = vorgangRepository.findMitWarenkorb(vorgangId)
                .filter(v -> frontendUserId != null && frontendUserId.equals(v.getFrontendUserId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dieser Shop-Besuch wurde nicht gefunden."));
        if (vorgang.getStatus() == IdsVorgangStatus.WARTET && LocalDateTime.now(clock).isAfter(vorgang.getGueltigBis())) {
            vorgang.setStatus(IdsVorgangStatus.ABGELAUFEN);
        }
        return vorgang;
    }

    private IdsProtokollAdapter adapterFuer(IdsProtokoll protokoll) {
        IdsProtokollAdapter a = adapter.get(protokoll);
        if (a == null) {
            throw new IllegalStateException("Kein Adapter für das Shop-Protokoll " + protokoll);
        }
        return a;
    }

    private String neuerSchluessel() {
        byte[] bytes = new byte[32];
        zufall.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String schluessel) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(schluessel.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 fehlt in dieser Java-Laufzeit.", e);
        }
    }
}
