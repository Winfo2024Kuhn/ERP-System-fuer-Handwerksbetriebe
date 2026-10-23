package org.example.kalkulationsprogramm.service.openfile;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Unterschreibt Updates des OpenFile Launchers mit einem eigenen Schlüssel dieser Installation.
 *
 * <p>Der private Schlüssel entsteht beim ersten Bedarf und liegt in {@code system_setting}
 * (wie die übrigen Zugangsdaten, kommt mit der Datenbank-Sicherung mit). Den öffentlichen Teil
 * bekommt jeder PC mit der Einrichtungs-Datei; der Launcher installiert danach nur Fassungen,
 * deren Unterschrift dazu passt (RSA, SHA-256, PKCS#1 v1.5 – prüfbar mit .NET und openssl).</p>
 */
@Component
@RequiredArgsConstructor
public class LauncherSignatur {

    static final String SCHLUESSEL_SETTING = "openfile-launcher.signatur-schluessel";
    private static final String ALGORITHMUS = "SHA256withRSA";
    private static final int SCHLUESSEL_BITS = 3072;

    private final SystemSettingsService settings;

    private volatile KeyPair schluessel;

    /** Gibt es schon einen Schlüssel? Erzeugt keinen. */
    public boolean istEingerichtet() {
        if (schluessel != null) {
            return true;
        }
        String gespeichert = settings.get(SCHLUESSEL_SETTING, null);
        return gespeichert != null && !gespeichert.isBlank();
    }

    /** Unterschrift über die UTF-8-Bytes des Textes, Base64. */
    public String unterschreibe(String text) {
        try {
            Signature signatur = Signature.getInstance(ALGORITHMUS);
            signatur.initSign(schluessel().getPrivate());
            signatur.update(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signatur.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Launcher-Update konnte nicht unterschrieben werden", e);
        }
    }

    /** Öffentlicher Schlüssel im .NET-Format für {@code RSACryptoServiceProvider.FromXmlString}. */
    public String oeffentlicherSchluesselXml() {
        RSAPublicKey key = (RSAPublicKey) schluessel().getPublic();
        Base64.Encoder b64 = Base64.getEncoder();
        return "<RSAKeyValue><Modulus>" + b64.encodeToString(ohneVorzeichen(key.getModulus()))
                + "</Modulus><Exponent>" + b64.encodeToString(ohneVorzeichen(key.getPublicExponent()))
                + "</Exponent></RSAKeyValue>";
    }

    /** Öffentlicher Schlüssel als PEM (SubjectPublicKeyInfo) für {@code openssl dgst -verify}. */
    public String oeffentlicherSchluesselPem() {
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(schluessel().getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + b64 + "\n-----END PUBLIC KEY-----\n";
    }

    private KeyPair schluessel() {
        KeyPair vorhanden = schluessel;
        if (vorhanden != null) {
            return vorhanden;
        }
        synchronized (this) {
            if (schluessel == null) {
                schluessel = ladeOderErzeuge();
            }
            return schluessel;
        }
    }

    private KeyPair ladeOderErzeuge() {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            String gespeichert = settings.get(SCHLUESSEL_SETTING, null);
            if (gespeichert != null && !gespeichert.isBlank()) {
                PrivateKey privat = factory.generatePrivate(
                        new PKCS8EncodedKeySpec(Base64.getDecoder().decode(gespeichert.strip())));
                RSAPrivateCrtKey crt = (RSAPrivateCrtKey) privat;
                PublicKey oeffentlich = factory.generatePublic(
                        new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
                return new KeyPair(oeffentlich, privat);
            }
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(SCHLUESSEL_BITS);
            KeyPair neu = generator.generateKeyPair();
            settings.save(SCHLUESSEL_SETTING,
                    Base64.getEncoder().encodeToString(neu.getPrivate().getEncoded()),
                    "Schluessel zum Unterschreiben der Updates des OpenFile Launchers (nicht weitergeben)");
            return neu;
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            throw new IllegalStateException("Schluessel fuer Launcher-Updates unbrauchbar", e);
        }
    }

    /** BigInteger ohne führendes Vorzeichen-Byte, wie .NET es erwartet. */
    static byte[] ohneVorzeichen(BigInteger zahl) {
        byte[] bytes = zahl.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            return Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return bytes;
    }
}
