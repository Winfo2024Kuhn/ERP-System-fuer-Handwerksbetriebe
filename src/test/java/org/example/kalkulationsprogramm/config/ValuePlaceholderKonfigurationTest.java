package org.example.kalkulationsprogramm.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

/**
 * Regressionstest für Issue #68: Die Release-.exe (h2-Profil) crashte beim
 * Start mit "Could not resolve placeholder 'smtp.host'", weil der
 * LieferantenController {@code @Value("${smtp.host}")} ohne Default nutzte
 * und smtp.* nur in der gitignorten application-local.properties definiert
 * war. Beim Endnutzer (ohne local-Profil) konnte Spring den Platzhalter
 * nicht auflösen und beendete den Boot.
 *
 * Dieser Test stellt sicher, dass jeder @Value-Platzhalter OHNE
 * Default-Wert in der Basis-application.properties definiert ist (die in
 * jedem Profil geladen wird) oder eine JVM-System-Property ist
 * (z.B. user.dir, user.home).
 */
class ValuePlaceholderKonfigurationTest {

    /** Findet ${...}-Vorkommen; Gruppe 1 ist der Inhalt zwischen den Klammern. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]*)\\}");

    @Test
    void allePlaceholderOhneDefaultSindInBasisPropertiesDefiniert() throws Exception {
        Properties basis = ladeBasisProperties();
        List<String> verstoesse = new ArrayList<>();

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));

        for (BeanDefinition bd : scanner.findCandidateComponents("org.example")) {
            Class<?> clazz;
            try {
                clazz = Class.forName(bd.getBeanClassName());
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                continue;
            }
            for (Field field : clazz.getDeclaredFields()) {
                pruefeAnnotation(field.getAnnotation(Value.class),
                        clazz.getSimpleName() + "." + field.getName(), basis, verstoesse);
            }
            for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                for (Parameter parameter : constructor.getParameters()) {
                    pruefeAnnotation(parameter.getAnnotation(Value.class),
                            clazz.getSimpleName() + "(" + parameter.getName() + ")", basis, verstoesse);
                }
            }
        }

        assertTrue(verstoesse.isEmpty(),
                "Diese @Value-Platzhalter haben keinen Default und sind nicht in der Basis-"
                        + "application.properties definiert – sie crashen den Boot der Release-.exe "
                        + "(vgl. Issue #68). Entweder Default angeben (${key:default}) oder den Wert "
                        + "über SystemSettingsService beziehen:\n"
                        + String.join("\n", verstoesse));
    }

    private void pruefeAnnotation(Value annotation, String ort, Properties basis, List<String> verstoesse) {
        if (annotation == null) {
            return;
        }
        Matcher matcher = PLACEHOLDER.matcher(annotation.value());
        while (matcher.find()) {
            String inhalt = matcher.group(1);
            if (inhalt.contains(":")) {
                continue; // hat einen Default -> Boot kann nicht daran scheitern
            }
            if (basis.containsKey(inhalt) || System.getProperties().containsKey(inhalt)) {
                continue;
            }
            verstoesse.add("  - " + ort + " -> ${" + inhalt + "}");
        }
    }

    /**
     * Lädt bewusst src/main/resources/application.properties direkt vom
     * Dateisystem: Im Test-Classpath würde src/test/resources/application.properties
     * gewinnen, die (anders als das Release) smtp.* definiert.
     */
    private Properties ladeBasisProperties() throws Exception {
        Properties properties = new Properties();
        Path pfad = Path.of("src", "main", "resources", "application.properties");
        try (InputStream in = Files.newInputStream(pfad)) {
            properties.load(in);
        }
        return properties;
    }
}
