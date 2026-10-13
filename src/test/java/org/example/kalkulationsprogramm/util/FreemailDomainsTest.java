package org.example.kalkulationsprogramm.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class FreemailDomainsTest {

    private static final Path FRONTEND_LISTE = Path.of("react-pc-frontend/src/lib/freemailDomains.ts");

    @Test
    @DisplayName("istFreemailDomain erkennt die gaengigen Anbieter, egal wie geschrieben")
    void erkenntAnbieter() {
        assertThat(FreemailDomains.istFreemailDomain("t-online.de")).isTrue();
        assertThat(FreemailDomains.istFreemailDomain(" GMX.DE ")).isTrue();
        assertThat(FreemailDomains.istFreemailDomain("gmail.com")).isTrue();
        assertThat(FreemailDomains.istFreemailDomain("musterstahl.example")).isFalse();
        assertThat(FreemailDomains.istFreemailDomain(null)).isFalse();
    }

    @Test
    @DisplayName("istFreemailAdresse prueft die Domain hinter dem @")
    void erkenntAdressen() {
        assertThat(FreemailDomains.istFreemailAdresse("max.mustermann@gmail.com")).isTrue();
        assertThat(FreemailDomains.istFreemailAdresse("Max.Mustermann@GMX.de")).isTrue();
        assertThat(FreemailDomains.istFreemailAdresse("info@musterstahl.example")).isFalse();
        assertThat(FreemailDomains.istFreemailAdresse("keine-adresse")).isFalse();
        assertThat(FreemailDomains.istFreemailAdresse(null)).isFalse();
    }

    @Test
    @DisplayName("Frontend-Liste stimmt mit der Backend-Liste ueberein")
    void frontendListeIstGleich() throws IOException {
        String quelle = Files.readString(FRONTEND_LISTE, StandardCharsets.UTF_8);
        int start = quelle.indexOf("new Set([");
        int ende = quelle.indexOf("])", start);
        assertThat(start).as("Set-Literal in %s", FRONTEND_LISTE).isPositive();

        Set<String> frontend = new TreeSet<>();
        Matcher m = Pattern.compile("'([^']++)'").matcher(quelle.substring(start, ende));
        while (m.find()) {
            frontend.add(m.group(1));
        }

        assertThat(frontend)
                .as("react-pc-frontend/src/lib/freemailDomains.ts muss dieselben Domains führen wie FreemailDomains.java")
                .isEqualTo(new TreeSet<>(FreemailDomains.DOMAINS));
    }
}
