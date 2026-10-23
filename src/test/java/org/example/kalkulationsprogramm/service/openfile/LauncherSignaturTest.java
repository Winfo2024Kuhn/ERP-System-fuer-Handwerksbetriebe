package org.example.kalkulationsprogramm.service.openfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LauncherSignaturTest {

    private static PublicKey ausPem(String pem) throws Exception {
        String b64 = pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(b64)));
    }

    private static boolean pruefe(PublicKey key, String text, String signaturB64) throws Exception {
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initVerify(key);
        s.update(text.getBytes(StandardCharsets.UTF_8));
        return s.verify(Base64.getDecoder().decode(signaturB64));
    }

    @Test
    void erzeugtSchluesselEinmalUndUnterschriftIstPruefbar() throws Exception {
        SystemSettingsService settings = mock(SystemSettingsService.class);
        LauncherSignatur signatur = new LauncherSignatur(settings);

        String unterschrift = signatur.unterschreibe("version\n{}\nskript äöü");
        PublicKey key = ausPem(signatur.oeffentlicherSchluesselPem());

        assertThat(pruefe(key, "version\n{}\nskript äöü", unterschrift)).isTrue();
        assertThat(pruefe(key, "version\n{}\nskript boese", unterschrift)).isFalse();
        verify(settings).save(eq(LauncherSignatur.SCHLUESSEL_SETTING), anyString(), anyString());
        // Zweiter Aufruf nutzt den gemerkten Schlüssel
        signatur.unterschreibe("noch einmal");
        verify(settings).get(LauncherSignatur.SCHLUESSEL_SETTING, null);
    }

    @Test
    void laedtGespeichertenSchluesselStattNeuZuErzeugen() throws Exception {
        SystemSettingsService ersteInstallation = mock(SystemSettingsService.class);
        LauncherSignatur erste = new LauncherSignatur(ersteInstallation);
        String pem = erste.oeffentlicherSchluesselPem();
        ArgumentCaptor<String> gespeichert = ArgumentCaptor.forClass(String.class);
        verify(ersteInstallation).save(eq(LauncherSignatur.SCHLUESSEL_SETTING), gespeichert.capture(), anyString());

        SystemSettingsService nachNeustart = mock(SystemSettingsService.class);
        when(nachNeustart.get(LauncherSignatur.SCHLUESSEL_SETTING, null)).thenReturn(gespeichert.getValue());
        LauncherSignatur zweite = new LauncherSignatur(nachNeustart);

        assertThat(zweite.oeffentlicherSchluesselPem()).isEqualTo(pem);
        assertThat(pruefe(ausPem(pem), "Max Mustermann", zweite.unterschreibe("Max Mustermann"))).isTrue();
        verify(nachNeustart, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void xmlSchluesselPasstZumPem() throws Exception {
        LauncherSignatur signatur = new LauncherSignatur(mock(SystemSettingsService.class));
        RSAPublicKey key = (RSAPublicKey) ausPem(signatur.oeffentlicherSchluesselPem());

        Matcher m = Pattern.compile("<RSAKeyValue><Modulus>([^<]+)</Modulus><Exponent>([^<]+)</Exponent></RSAKeyValue>")
                .matcher(signatur.oeffentlicherSchluesselXml());
        assertThat(m.matches()).isTrue();
        byte[] modulus = Base64.getDecoder().decode(m.group(1));
        assertThat(modulus[0]).isNotZero(); // .NET erwartet kein Vorzeichen-Byte
        assertThat(modulus).hasSize(384); // 3072 Bit
        assertThat(new BigInteger(1, modulus)).isEqualTo(key.getModulus());
        assertThat(new BigInteger(1, Base64.getDecoder().decode(m.group(2)))).isEqualTo(key.getPublicExponent());
    }

    @Test
    void istEingerichtetErzeugtKeinenSchluessel() {
        SystemSettingsService settings = mock(SystemSettingsService.class);
        LauncherSignatur signatur = new LauncherSignatur(settings);

        assertThat(signatur.istEingerichtet()).isFalse();
        verify(settings, never()).save(anyString(), anyString(), anyString());

        when(settings.get(LauncherSignatur.SCHLUESSEL_SETTING, null)).thenReturn("  ");
        assertThat(signatur.istEingerichtet()).isFalse();
        when(settings.get(LauncherSignatur.SCHLUESSEL_SETTING, null)).thenReturn("gespeichert");
        assertThat(signatur.istEingerichtet()).isTrue();

        LauncherSignatur benutzt = new LauncherSignatur(mock(SystemSettingsService.class));
        benutzt.unterschreibe("x");
        assertThat(benutzt.istEingerichtet()).isTrue();
    }

    @Test
    void kaputterGespeicherterSchluesselWirdGemeldet() {
        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.get(LauncherSignatur.SCHLUESSEL_SETTING, null)).thenReturn("kein-schluessel!");
        LauncherSignatur signatur = new LauncherSignatur(settings);

        assertThatThrownBy(() -> signatur.unterschreibe("x")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ohneVorzeichenEntferntNurFuehrendeNull() {
        assertThat(LauncherSignatur.ohneVorzeichen(new BigInteger("128"))).containsExactly((byte) 0x80);
        assertThat(LauncherSignatur.ohneVorzeichen(new BigInteger("65537"))).containsExactly(1, 0, 1);
        assertThat(LauncherSignatur.ohneVorzeichen(BigInteger.ZERO)).containsExactly(0);
    }
}
