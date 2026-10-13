package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.example.kalkulationsprogramm.controller.AbwesenheitController;
import org.example.kalkulationsprogramm.controller.DatevExportController;
import org.example.kalkulationsprogramm.controller.DatevKonfigurationController;
import org.example.kalkulationsprogramm.controller.LangzeitkrankmeldungController;
import org.example.kalkulationsprogramm.controller.MitarbeiterSaldoController;
import org.example.kalkulationsprogramm.controller.MonatsabschlussController;
import org.example.kalkulationsprogramm.controller.MonatsabschlussUebersichtController;
import org.example.kalkulationsprogramm.controller.UrlaubsantragController;
import org.example.kalkulationsprogramm.controller.ZeitkontenmodellController;
import org.example.kalkulationsprogramm.controller.ZeitkontoKorrekturController;
import org.example.kalkulationsprogramm.controller.ZeitverwaltungController;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Wächter gegen Lücken in der Zeiterfassungs-Sperre: Jeder Endpunkt der
 * Zeiterfassungs-Controller muss von {@link ZeiterfassungZugriffInterceptor#GESPERRTE_PFADE}
 * erfasst sein oder ausdrücklich in {@code FREIE_PFADE} stehen. Kommt ein neuer
 * Endpunkt unter einem anderen Pfad dazu, schlägt dieser Test an.
 */
class ZeiterfassungSperreAbdeckungTest {

    private static final List<Class<?>> ZEITERFASSUNGS_CONTROLLER = List.of(
            ZeitverwaltungController.class, MitarbeiterSaldoController.class, MonatsabschlussController.class,
            MonatsabschlussUebersichtController.class, DatevExportController.class, DatevKonfigurationController.class,
            ZeitkontenmodellController.class, ZeitkontoKorrekturController.class, UrlaubsantragController.class,
            AbwesenheitController.class, LangzeitkrankmeldungController.class);

    private final AntPathMatcher matcher = new AntPathMatcher();

    @Test
    void jederZeiterfassungsEndpunktIstGesperrtOderAusdruecklichFrei() {
        List<String> ungeschuetzt = new ArrayList<>();
        for (Class<?> controller : ZEITERFASSUNGS_CONTROLLER) {
            for (String pfad : pfade(controller)) {
                String beispiel = pfad.replaceAll("\\{[^}]+}", "1");
                boolean gesperrt = ZeiterfassungZugriffInterceptor.GESPERRTE_PFADE.stream()
                        .anyMatch(m -> matcher.match(m, beispiel));
                boolean frei = ZeiterfassungZugriffInterceptor.FREIE_PFADE.stream()
                        .anyMatch(m -> matcher.match(m, beispiel));
                if (!gesperrt && !frei) {
                    ungeschuetzt.add(controller.getSimpleName() + " " + pfad);
                }
            }
        }
        assertThat(ungeschuetzt).as("Endpunkte ohne Zeiterfassungs-Sperre").isEmpty();
    }

    @Test
    void wächterFindetUeberhauptEndpunkte() {
        assertThat(pfade(ZeitkontoKorrekturController.class)).isNotEmpty()
                .allMatch(p -> p.startsWith("/api/zeitkonto/korrekturen"));
    }

    private static List<String> pfade(Class<?> controller) {
        RequestMapping klasse = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        String[] basen = klasse == null || pfadeAus(klasse).length == 0 ? new String[] { "" } : pfadeAus(klasse);
        List<String> ergebnis = new ArrayList<>();
        for (Method methode : controller.getDeclaredMethods()) {
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(methode, RequestMapping.class);
            if (mapping == null) continue;
            String[] teile = pfadeAus(mapping).length == 0 ? new String[] { "" } : pfadeAus(mapping);
            for (String basis : basen) {
                for (String teil : teile) {
                    ergebnis.add(basis + teil);
                }
            }
        }
        return ergebnis;
    }

    private static String[] pfadeAus(Annotation mapping) {
        RequestMapping m = (RequestMapping) mapping;
        return m.path().length > 0 ? m.path() : m.value();
    }
}
