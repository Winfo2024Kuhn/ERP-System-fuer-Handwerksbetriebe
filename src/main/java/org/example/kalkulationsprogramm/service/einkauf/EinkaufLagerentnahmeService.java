package org.example.kalkulationsprogramm.service.einkauf;

import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBedarf;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufLagerentnahme;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufMengenbuchung;
import org.example.kalkulationsprogramm.domain.einkauf.Einheit;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufLagerentnahmeDto.BewertungDto;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufLagerentnahmeDto.BewertungRequest;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufLagerentnahmeDto.EntnahmeDto;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufLagerentnahmeDto.EntnahmeRequest;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Herkunft;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.EinkaufBedarfRepository;
import org.example.kalkulationsprogramm.repository.EinkaufLagerentnahmeRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufMengenService.Mengenaktion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class EinkaufLagerentnahmeService {
    private static final BigDecimal MAX_DECIMAL = new BigDecimal("9999999999999.999999");
    private final EinkaufBedarfRepository bedarfRepository;
    private final EinkaufLagerentnahmeRepository entnahmeRepository;
    private final FrontendUserProfileRepository profileRepository;
    private final EinkaufMengenService mengenService;

    public EinkaufLagerentnahmeService(EinkaufBedarfRepository bedarfRepository,
            EinkaufLagerentnahmeRepository entnahmeRepository, FrontendUserProfileRepository profileRepository,
            EinkaufMengenService mengenService) {
        this.bedarfRepository = bedarfRepository;
        this.entnahmeRepository = entnahmeRepository;
        this.profileRepository = profileRepository;
        this.mengenService = mengenService;
    }

    @Transactional
    public EntnahmeDto bestaetigen(EntnahmeRequest request, Long akteurId) {
        validiere(request, akteurId);
        Herkunft anteil = request.anteil();
        EinkaufBedarf bedarf = bedarfRepository.findByIdForUpdate(anteil.bedarfId())
                .orElseThrow(() -> new NotFoundException("Der Einkaufsbedarf wurde nicht gefunden."));
        String payloadHash = hash(request, akteurId);
        EinkaufLagerentnahme vorhanden = entnahmeRepository.findByIdempotenzKey(request.idempotenzKey()).orElse(null);
        if (vorhanden != null) {
            if (!payloadHash.equals(vorhanden.getPayloadHash())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Der Idempotenzschlüssel wurde bereits für eine andere Entnahme verwendet.");
            }
            return toDto(vorhanden);
        }
        if (bedarf.getVersion() == null || bedarf.getVersion() != anteil.version()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Der Einkaufsbedarf wurde zwischenzeitlich geändert.");
        }
        Einheit einheit = bedarf.getPosition() == null || bedarf.getPosition().basis() == null
                ? null : bedarf.getPosition().basis().einheit();
        if (bedarf.getProjektId() == null || einheit == null || bedarf.isNachpflegeErforderlich()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Dieser Lagerbedarf muss zuerst vollständig nachgepflegt werden.");
        }
        FrontendUserProfile profil = ladeAkteur(akteurId);
        Long mitarbeiterId = profil.getMitarbeiter().getId();
        Instant entnommenAm = request.entnommenAm();
        EinkaufLagerentnahme entnahme = new EinkaufLagerentnahme(bedarf.getProjektId(), bedarf.getId(),
                anteil.version(), anteil.menge().setScale(6), einheit, request.preisJeEinheit(),
                request.preisQuelle(), bedarf.ungedeckt().subtract(anteil.menge()).max(BigDecimal.ZERO).setScale(6),
                entnommenAm, request.idempotenzKey(), payloadHash, akteurId, mitarbeiterId);
        // The need lock is taken before the append-only ledger row. A failed stock booking rolls back both writes.
        EinkaufLagerentnahme gespeichert = entnahmeRepository.saveAndFlush(entnahme);
        mengenService.buche(List.of(anteil), Mengenaktion.LAGER_ENTNEHMEN,
                "LAGERENTNAHME:" + request.idempotenzKey(), request.idempotenzKey(), akteurId);
        return toDto(gespeichert);
    }

    @Transactional
    public EntnahmeDto bewertungErgaenzen(Long id, BewertungRequest request, Long akteurId) {
        if (id == null || id <= 0) throw new IllegalArgumentException("Die Lagerentnahme-ID ist ungültig.");
        if (request == null || !istGueltigeZahl(request.preisJeEinheit())
                || request.preisQuelle() == null || request.preisQuelle().isBlank()
                || request.preisQuelle().length() > 255) {
            throw new IllegalArgumentException("Bitte geben Sie einen gültigen Preis und seine Quelle an.");
        }
        FrontendUserProfile profil = ladeAkteur(akteurId);
        EinkaufLagerentnahme entnahme = entnahmeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Die Lagerentnahme wurde nicht gefunden."));
        entnahme.fuegeBewertungHinzu(request.preisJeEinheit().setScale(6), request.preisQuelle().trim(),
                Instant.now(), akteurId, profil.getMitarbeiter().getId());
        return toDto(entnahmeRepository.saveAndFlush(entnahme));
    }

    @Transactional(readOnly = true)
    public Page<EntnahmeDto> suche(Long projektId, Pageable pageable) {
        if (projektId == null || projektId <= 0) throw new IllegalArgumentException("Die Projekt-ID ist ungültig.");
        if (pageable == null) throw new IllegalArgumentException("Die Seiteneinstellungen fehlen.");
        return entnahmeRepository.sucheImProjekt(projektId, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public BigDecimal bewerteteEntnahmen(Long projektId) {
        if (projektId == null || projektId <= 0) throw new IllegalArgumentException("Die Projekt-ID ist ungültig.");
        return entnahmeRepository.sumBewerteteEntnahmen(projektId);
    }

    @Transactional(readOnly = true)
    public boolean bewertungOffen(Long projektId) {
        if (projektId == null || projektId <= 0) throw new IllegalArgumentException("Die Projekt-ID ist ungültig.");
        return entnahmeRepository.existsUnbewerteteByProjektId(projektId);
    }

    private FrontendUserProfile ladeAkteur(Long akteurId) {
        if (akteurId == null || akteurId <= 0) throw new IllegalArgumentException("Der Benutzer ist ungültig.");
        FrontendUserProfile profil = profileRepository.findById(akteurId)
                .filter(FrontendUserProfile::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Das Benutzerprofil ist nicht mehr aktiv."));
        if (profil.getMitarbeiter() == null || profil.getMitarbeiter().getId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Deinem Benutzerprofil ist kein Mitarbeiter zugeordnet.");
        }
        return profil;
    }

    private static void validiere(EntnahmeRequest request, Long akteurId) {
        if (request == null || request.anteil() == null || request.anteil().bedarfId() == null
                || request.anteil().bedarfId() <= 0 || request.anteil().version() < 0
                || !istGueltigeZahl(request.anteil().menge()) || request.anteil().menge().signum() <= 0
                || request.entnommenAm() == null || request.idempotenzKey() == null
                || akteurId == null || akteurId <= 0) {
            throw new IllegalArgumentException("Bitte geben Sie Bedarf, positive Menge, Zeitpunkt und Idempotenzschlüssel an.");
        }
        boolean preisAngegeben = request.preisJeEinheit() != null;
        if ((preisAngegeben && (!istGueltigeZahl(request.preisJeEinheit())
                || request.preisQuelle() == null || request.preisQuelle().isBlank()
                || request.preisQuelle().length() > 255))
                || (!preisAngegeben && request.preisQuelle() != null && !request.preisQuelle().isBlank())) {
            throw new IllegalArgumentException("Preis und Preisquelle müssen gemeinsam angegeben werden.");
        }
    }

    private static boolean istGueltigeZahl(BigDecimal zahl) {
        if (zahl == null || zahl.signum() < 0 || zahl.compareTo(MAX_DECIMAL) > 0) return false;
        try { zahl.setScale(6, RoundingMode.UNNECESSARY); return true; }
        catch (ArithmeticException ignored) { return false; }
    }

    private static String hash(EntnahmeRequest request, Long akteurId) {
        String payload = request.anteil().bedarfId() + "|" + request.anteil().version() + "|"
                + request.anteil().menge().stripTrailingZeros().toPlainString() + "|"
                + request.preisJeEinheit() + "|" + request.preisQuelle() + "|" + request.entnommenAm()
                + "|" + akteurId;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 fehlt.", exception);
        }
    }

    private EntnahmeDto toDto(EinkaufLagerentnahme entnahme) {
        List<BewertungDto> bewertungen = entnahme.getBewertungen().stream()
                .map(b -> new BewertungDto(b.getPreisJeEinheit(), b.getPreisQuelle(), b.getBewertetAm(),
                        b.getAkteurId(), b.getMitarbeiterId()))
                .toList();
        return new EntnahmeDto(entnahme.getId(), entnahme.getProjektId(), entnahme.getBedarfId(),
                entnahme.getBedarfVersion(), entnahme.getMenge(), entnahme.getEinheit(), entnahme.getPreisJeEinheit(),
                entnahme.getPreisQuelle(), entnahme.getBewerteterBetrag(), entnahme.isBewertungOffen(),
                entnahme.getOffenerBedarf(), entnahme.getEntnommenAm(), entnahme.getAkteurId(),
                entnahme.getMitarbeiterId(), bewertungen);
    }
}
