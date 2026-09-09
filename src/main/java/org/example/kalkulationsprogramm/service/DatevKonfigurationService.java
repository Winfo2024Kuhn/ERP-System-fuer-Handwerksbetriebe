package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.DatevDto.*;
import org.example.kalkulationsprogramm.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
@Transactional(readOnly = true)
public class DatevKonfigurationService {
 public static final Set<String> KATEGORIEN = Set.of("ARBEIT", "FEIERTAG", "URLAUB", "KRANKHEIT",
     "FORTBILDUNG", "ZEITAUSGLEICH", "KRANKENGELD", "WIEDEREINGLIEDERUNG");
 private static final ObjectMapper JSON = new ObjectMapper();
 private final DatevKonfigurationRepository configs;
 private final DatevPersonalnummerRepository nummern;
 private final MonatsabschlussBerechtigungService rechte;

 public Konfiguration laden(Authentication auth) {
  rechte.verlangeAkteur(auth);
  var entity = configs.findById(1L).orElseThrow(() -> new IllegalStateException("DATEV-Konfiguration fehlt; Migration prüfen"));
  var personal = nummern.findAll().stream().sorted(Comparator.comparing(DatevPersonalnummer::getMitarbeiterId))
      .map(n -> new Personalnummer(n.getMitarbeiterId(), n.getPersonalnummer())).toList();
  return dto(entity, personal);
 }

 @Transactional
 public Konfiguration speichern(Konfiguration input, Authentication auth) {
  rechte.verlangeAkteur(auth);
  var clean = validieren(input);
  var entity = configs.sperren().orElseThrow(() -> new IllegalStateException("DATEV-Konfiguration fehlt; Migration prüfen"));
  if (!Objects.equals(entity.getVersion(), clean.version()))
   throw new ResponseStatusException(HttpStatus.CONFLICT, "Die DATEV-Einstellungen wurden geändert. Bitte neu laden.");
  Set<Long> ids = new HashSet<>();
  clean.personalnummern().forEach(n -> ids.add(n.mitarbeiterId()));
  if (!ids.isEmpty() && !new HashSet<>(nummern.findMenschenIds(ids)).equals(ids))
   throw bad("Eine Mitarbeiterzuordnung ist ungültig. Bitte die Mitarbeiterliste neu laden.");
  entity.setZiel(clean.ziel()); entity.setBeraterNr(clean.beraterNr()); entity.setMandantenNr(clean.mandantenNr());
  try { entity.setZuordnungenJson(JSON.writeValueAsString(clean.zuordnungen())); }
  catch (JsonProcessingException ex) { throw new IllegalStateException("DATEV-Zuordnungen konnten nicht gespeichert werden", ex); }
  entity.setAenderungszaehler(entity.getAenderungszaehler() + 1);
  // Delete first, so exchanging two existing numbers cannot transiently violate the unique constraint.
  nummern.deleteAllInBatch();
  var rows = clean.personalnummern().stream().filter(n -> !n.personalnummer().isEmpty()).map(n -> {
   var row = new DatevPersonalnummer(); row.setMitarbeiterId(n.mitarbeiterId());
   row.setPersonalnummer(n.personalnummer()); row.setNormalisiert(normalisieren(n.personalnummer())); return row;
  }).toList();
  nummern.saveAll(rows);
  configs.saveAndFlush(entity);
  log.info("DATEV-Konfiguration gespeichert: id=1 version={}", entity.getVersion());
  return dto(entity, clean.personalnummern().stream().filter(n -> !n.personalnummer().isEmpty()).toList());
 }

 private Konfiguration dto(DatevKonfiguration entity, List<Personalnummer> personal) {
  try { return new Konfiguration(entity.getVersion(), entity.getZiel(), entity.getBeraterNr(), entity.getMandantenNr(),
      JSON.readValue(entity.getZuordnungenJson(), new TypeReference<List<Zuordnung>>() {}), personal); }
  catch (JsonProcessingException ex) { throw new IllegalStateException("Gespeicherte DATEV-Zuordnungen sind ungültig", ex); }
 }
 private Konfiguration validieren(Konfiguration input) {
  if (input == null || input.version() == null || input.version() < 0) throw bad("Die Konfigurationsversion fehlt oder ist ungültig.");
  if (!"LODAS".equals(input.ziel())) throw bad("Als DATEV-Ziel wird nur LODAS unterstützt.");
  // DATEV LODAS Schnittstellenhandbuch, 94. Auflage Juni 2026, Fach 2 Seite 2-6:
  // BeraterNr 4–7 Stellen, MandantenNr 1–5 Stellen.
  String berater = nummer(input.beraterNr(), 4, 7, "Beraternummer");
  String mandant = nummer(input.mandantenNr(), 1, 5, "Mandantennummer");
  if (input.zuordnungen() == null || input.zuordnungen().size() > KATEGORIEN.size()) throw bad("Die Lohnartenzuordnungen sind ungültig.");
  if (input.personalnummern() == null || input.personalnummern().size() > 10000) throw bad("Höchstens 10000 Personalnummern sind erlaubt.");
  Set<String> kategorien = new HashSet<>(); List<Zuordnung> mapping = new ArrayList<>();
  for (var z : input.zuordnungen()) {
   if (z == null || z.kategorie() == null || !KATEGORIEN.contains(z.kategorie()) || !kategorien.add(z.kategorie())) throw bad("Eine Lohnkategorie ist unbekannt oder doppelt.");
   String lohnart = nummer(z.lohnart(), 1, 4, "Lohnart");
   if (z.ausgeschlossen() && !lohnart.isEmpty()) throw bad("Eine ausgeschlossene Kategorie darf keine Lohnart haben.");
   mapping.add(new Zuordnung(z.kategorie(), z.ausgeschlossen(), lohnart));
  }
  Set<Long> ids = new HashSet<>(); Set<String> vergeben = new HashSet<>(); List<Personalnummer> personal = new ArrayList<>();
  for (var n : input.personalnummern()) {
   if (n == null || n.mitarbeiterId() == null || n.mitarbeiterId() <= 0 || !ids.add(n.mitarbeiterId())) throw bad("Eine Mitarbeiter-ID ist ungültig oder doppelt.");
   String value = nummer(n.personalnummer(), 1, 5, "Personalnummer");
   if (!value.isEmpty() && !vergeben.add(normalisieren(value))) throw bad("Eine Personalnummer ist mehrfach vergeben (auch mit führenden Nullen).");
   personal.add(new Personalnummer(n.mitarbeiterId(), value));
  }
  return new Konfiguration(input.version(), "LODAS", berater, mandant, List.copyOf(mapping), List.copyOf(personal));
 }
 private static String nummer(String raw, int min, int max, String name) {
  if (raw == null || raw.isEmpty()) return "";
  if (!raw.matches("[0-9]{" + min + "," + max + "}") || Integer.parseInt(raw) == 0)
   throw bad(name + " muss aus " + min + " bis " + max + " Ziffern bestehen und größer als 0 sein.");
  return raw;
 }
 private static String normalisieren(String value) { return Integer.toString(Integer.parseInt(value)); }
 private static ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, text); }
}
