package org.example.kalkulationsprogramm.service.telefon.fritzbox;

import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException;
import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException.Grund;
import org.example.kalkulationsprogramm.service.telefon.TelefonZugang;
import org.example.kalkulationsprogramm.service.telefon.telefonbuch.TelefonbuchAnlage;
import org.example.kalkulationsprogramm.service.telefon.telefonbuch.TelefonbuchEintrag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.example.kalkulationsprogramm.service.telefon.fritzbox.FritzBoxTelefonAnlage.ONTEL;
import static org.example.kalkulationsprogramm.service.telefon.fritzbox.FritzBoxTelefonAnlage.ONTEL_URL;

/**
 * FRITZ!Box-Umsetzung der {@link TelefonbuchAnlage} über TR-064 (Dienst X_AVM-DE_OnTel).
 * Angefasst wird nur das Telefonbuch mit der übergebenen ID; welches das ist,
 * prüft {@link #stelleTelefonbuchBereit} jedes Mal am Namen – so schreibt das ERP
 * auch nach einem Wechsel der Box oder einem Zurücksetzen nie in ein fremdes Telefonbuch.
 */
@Component
public class FritzBoxTelefonbuch implements TelefonbuchAnlage {

    /** UPnP-Fehler 713 (Index ungültig) / 714 (Eintrag gibt es nicht): der Eintrag ist schon weg. */
    private static final int INDEX_UNGUELTIG = 713;
    private static final int EINTRAG_FEHLT = 714;
    /** 866–868: Die Box verlangt eine Bestätigung mit zweitem Faktor. */
    private static final int ZWEITER_FAKTOR_VON = 866;
    private static final int ZWEITER_FAKTOR_BIS = 868;
    /** Das Haupttelefonbuch (ID 0) wird nie gelöscht. */
    private static final String HAUPTTELEFONBUCH = "0";
    /** Mehr Telefonbücher hat keine FRITZ!Box – schützt vor einer unsinnigen Antwort. */
    private static final int MAX_TELEFONBUECHER = 50;

    private final Tr064Client client;

    @Autowired
    public FritzBoxTelefonbuch() {
        this(new Tr064Client());
    }

    FritzBoxTelefonbuch(Tr064Client client) {
        this.client = client;
    }

    @Override
    public Telefonbuch stelleTelefonbuchBereit(TelefonZugang zugang, String name, String bekannteId) {
        return mitRechtepruefung(() -> {
            List<String> ids = telefonbuchIds(zugang);
            if (bekannteId != null && ids.contains(bekannteId) && name.equals(nameVon(zugang, bekannteId))) {
                return new Telefonbuch(bekannteId, false);
            }
            Optional<String> gefunden = sucheNachName(zugang, ids, name);
            if (gefunden.isPresent()) {
                return new Telefonbuch(gefunden.get(), true);
            }
            Map<String, String> argumente = new LinkedHashMap<>();
            argumente.put("NewPhonebookExtraID", "");
            argumente.put("NewPhonebookName", name);
            client.aktion(zugang, ONTEL_URL, ONTEL, "AddPhonebook", argumente);
            String neu = sucheNachName(zugang, telefonbuchIds(zugang), name)
                    .orElseThrow(() -> new TelefonAnlageException(Grund.UNERWARTETE_ANTWORT,
                            "AddPhonebook: neues Telefonbuch danach nicht gefunden", null));
            return new Telefonbuch(neu, true);
        });
    }

    @Override
    public Set<String> ladeEintragsIds(TelefonZugang zugang, String telefonbuchId) {
        return mitRechtepruefung(() -> {
            Map<String, String> antwort = client.aktion(zugang, ONTEL_URL, ONTEL, "GetPhonebook",
                    Map.of("NewPhonebookID", pruefeTelefonbuchId(telefonbuchId)));
            String url = antwort.get("NewPhonebookURL");
            if (url == null || url.isBlank()) {
                throw new TelefonAnlageException(Grund.UNERWARTETE_ANTWORT, "GetPhonebook: keine Download-Adresse", null);
            }
            byte[] export = client.laden(zugang, url);
            try {
                return TelefonbuchXml.eintragsIds(export);
            } catch (TelefonAnlageException e) {
                throw new TelefonAnlageException(Grund.UNERWARTETE_ANTWORT, "Telefonbuch-Export nicht lesbar", e);
            }
        });
    }

    @Override
    public String speichereEintrag(TelefonZugang zugang, String telefonbuchId, TelefonbuchEintrag eintrag,
                                   String eintragsId) {
        return mitRechtepruefung(() -> {
            try {
                return setze(zugang, telefonbuchId, eintrag, eintragsId);
            } catch (SoapFehler e) {
                // Eintrag wurde in der Box von Hand gelöscht → neu anlegen statt aufzugeben
                if (eintragsId != null && (e.code() == INDEX_UNGUELTIG || e.code() == EINTRAG_FEHLT)) {
                    return setze(zugang, telefonbuchId, eintrag, null);
                }
                throw e;
            }
        });
    }

    @Override
    public void loescheEintrag(TelefonZugang zugang, String telefonbuchId, String eintragsId) {
        mitRechtepruefung(() -> {
            Map<String, String> argumente = new LinkedHashMap<>();
            argumente.put("NewPhonebookID", pruefeTelefonbuchId(telefonbuchId));
            argumente.put("NewPhonebookEntryUniqueID", TelefonbuchXml.pruefeId(eintragsId));
            try {
                client.aktion(zugang, ONTEL_URL, ONTEL, "DeletePhonebookEntryUID", argumente);
            } catch (SoapFehler e) {
                if (e.code() != INDEX_UNGUELTIG && e.code() != EINTRAG_FEHLT) {
                    throw e;
                }
            }
            return null;
        });
    }

    @Override
    public boolean loescheTelefonbuch(TelefonZugang zugang, String name, String bekannteId) {
        return mitRechtepruefung(() -> {
            List<String> ids = telefonbuchIds(zugang);
            Optional<String> ziel = bekannteId != null && ids.contains(bekannteId) && name.equals(nameVon(zugang, bekannteId))
                    ? Optional.of(bekannteId) : sucheNachName(zugang, ids, name);
            if (ziel.isEmpty() || HAUPTTELEFONBUCH.equals(ziel.get())) {
                return false;
            }
            Map<String, String> buch = client.aktion(zugang, ONTEL_URL, ONTEL, "GetPhonebook", Map.of("NewPhonebookID", ziel.get()));
            // Unmittelbar vor dem Löschen noch einmal den Namen prüfen – nie ein fremdes Telefonbuch löschen.
            if (!name.equals(buch.getOrDefault("NewPhonebookName", "").trim())) {
                return false;
            }
            Map<String, String> argumente = new LinkedHashMap<>();
            argumente.put("NewPhonebookID", ziel.get());
            argumente.put("NewPhonebookExtraID", buch.getOrDefault("NewPhonebookExtraID", ""));
            try {
                client.aktion(zugang, ONTEL_URL, ONTEL, "DeletePhonebook", argumente);
            } catch (SoapFehler e) {
                if (e.code() != INDEX_UNGUELTIG && e.code() != EINTRAG_FEHLT) {
                    throw e;
                }
                return false;
            }
            return true;
        });
    }

    private String setze(TelefonZugang zugang, String telefonbuchId, TelefonbuchEintrag eintrag, String eintragsId) {
        Map<String, String> argumente = new LinkedHashMap<>();
        argumente.put("NewPhonebookID", pruefeTelefonbuchId(telefonbuchId));
        argumente.put("NewPhonebookEntryData", TelefonbuchXml.eintrag(eintrag, eintragsId));
        Map<String, String> antwort = client.aktion(zugang, ONTEL_URL, ONTEL, "SetPhonebookEntryUID", argumente);
        try {
            return TelefonbuchXml.pruefeId(antwort.get("NewPhonebookEntryUniqueID"));
        } catch (TelefonAnlageException e) {
            throw new TelefonAnlageException(Grund.UNERWARTETE_ANTWORT,
                    "SetPhonebookEntryUID: keine gültige Eintrags-Kennung zurück", e);
        }
    }

    /** "0,1,2" → [0, 1, 2]; nur Ziffern-IDs. */
    private List<String> telefonbuchIds(TelefonZugang zugang) {
        Map<String, String> antwort = client.aktion(zugang, ONTEL_URL, ONTEL, "GetPhonebookList", Map.of());
        String liste = antwort.getOrDefault("NewPhonebookList", "");
        List<String> ids = new ArrayList<>();
        for (String teil : liste.split(",", -1)) {
            String id = teil.trim();
            if (TelefonbuchXml.istGueltigeId(id) && !ids.contains(id) && ids.size() < MAX_TELEFONBUECHER) {
                ids.add(id);
            }
        }
        return ids;
    }

    private Optional<String> sucheNachName(TelefonZugang zugang, List<String> ids, String name) {
        for (String id : ids) {
            if (name.equals(nameVon(zugang, id))) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }

    private String nameVon(TelefonZugang zugang, String id) {
        Map<String, String> antwort = client.aktion(zugang, ONTEL_URL, ONTEL, "GetPhonebook", Map.of("NewPhonebookID", id));
        String name = antwort.get("NewPhonebookName");
        return name == null ? "" : name.trim();
    }

    private static String pruefeTelefonbuchId(String id) {
        return TelefonbuchXml.pruefeId(id);
    }

    /**
     * Fehlende Rechte und verlangte Zusatz-Bestätigung bekommen eine Meldung, die
     * sagt, was in der FRITZ!Box einzustellen ist.
     */
    private static <T> T mitRechtepruefung(Supplier<T> aufruf) {
        try {
            return aufruf.get();
        } catch (SoapFehler e) {
            if (e.code() >= ZWEITER_FAKTOR_VON && e.code() <= ZWEITER_FAKTOR_BIS) {
                throw new TelefonAnlageException(Grund.TELEFONBUCH_BESTAETIGUNG, e.getDetail(), e);
            }
            throw e;
        } catch (TelefonAnlageException e) {
            if (e.getGrund() == Grund.KEINE_RECHTE) {
                throw new TelefonAnlageException(Grund.TELEFONBUCH_KEINE_RECHTE, e.getDetail(), e);
            }
            throw e;
        }
    }
}
