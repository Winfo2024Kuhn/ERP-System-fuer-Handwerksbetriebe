package org.example.kalkulationsprogramm.service;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;

import java.security.*;
import java.util.Base64;

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

        String publicKey = Base64.getUrlEncoder().withoutPadding().encodeToString(
                keyPair.getPublic().getEncoded());
        String privateKey = Base64.getUrlEncoder().withoutPadding().encodeToString(
                keyPair.getPrivate().getEncoded());

        System.out.println("=== VAPID Key Pair Generated ===");
        System.out.println();
        System.out.println("Public Key (for frontend & environment variable):");
        System.out.println(publicKey);
        System.out.println();
        System.out.println("Private Key (for environment variable ONLY - NEVER commit!):");
        System.out.println(privateKey);
        System.out.println();
        System.out.println("Set as environment variables:");
        System.out.println("  Windows (PowerShell):");
        System.out.println("    $env:PUSH_VAPID_PUBLIC_KEY=\"" + publicKey + "\"");
        System.out.println("    $env:PUSH_VAPID_PRIVATE_KEY=\"" + privateKey + "\"");
        System.out.println();
        System.out.println("  Linux/macOS:");
        System.out.println("    export PUSH_VAPID_PUBLIC_KEY=\"" + publicKey + "\"");
        System.out.println("    export PUSH_VAPID_PRIVATE_KEY=\"" + privateKey + "\"");
    }
}
