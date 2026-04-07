package org.example.kalkulationsprogramm.service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;

/**
 * Utility to generate VAPID key pair for Web Push notifications.
 * Run: java -cp ... org.example.kalkulationsprogramm.service.VapidKeyGenerator
 *
 * Set the output as environment variables:
 *   PUSH_VAPID_PUBLIC_KEY=...
 *   PUSH_VAPID_PRIVATE_KEY=...
 */
public class VapidKeyGenerator {

    public static void main(String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        ECNamedCurveParameterSpec parameterSpec = ECNamedCurveTable.getParameterSpec("prime256v1");
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("ECDSA", "BC");
        keyPairGenerator.initialize(parameterSpec);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // X.509 / PKCS8 encoded (for application-local.properties)
        String publicKey = Base64.getUrlEncoder().withoutPadding().encodeToString(
                keyPair.getPublic().getEncoded());
        String privateKey = Base64.getUrlEncoder().withoutPadding().encodeToString(
                keyPair.getPrivate().getEncoded());

        // Raw uncompressed EC point (for browser debug / verification)
        ECPublicKey ecPub = (ECPublicKey) keyPair.getPublic();
        byte[] x = ecPub.getW().getAffineX().toByteArray();
        byte[] y = ecPub.getW().getAffineY().toByteArray();
        byte[] rawPoint = new byte[65];
        rawPoint[0] = 0x04;
        System.arraycopy(x, Math.max(0, x.length - 32), rawPoint, 1 + Math.max(0, 32 - x.length), Math.min(32, x.length));
        System.arraycopy(y, Math.max(0, y.length - 32), rawPoint, 33 + Math.max(0, 32 - y.length), Math.min(32, y.length));
        String rawPubKey = Base64.getUrlEncoder().withoutPadding().encodeToString(rawPoint);

        System.out.println("=== VAPID Key Pair Generated ===");
        System.out.println();
        System.out.println("Public Key (for application-local.properties):");
        System.out.println(publicKey);
        System.out.println();
        System.out.println("Private Key (for application-local.properties - NEVER commit!):");
        System.out.println(privateKey);
        System.out.println();
        System.out.println("Raw Public Key (65-byte uncompressed EC point, for browser debug):");
        System.out.println(rawPubKey);
        System.out.println();
        System.out.println("Add to application-local.properties:");
        System.out.println("  push.vapid.public-key=" + publicKey);
        System.out.println("  push.vapid.private-key=" + privateKey);
        System.out.println("  push.vapid.subject=mailto:your@email.com");
    }
}
