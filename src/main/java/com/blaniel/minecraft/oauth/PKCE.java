package com.blaniel.minecraft.oauth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Implementación de PKCE (Proof Key for Code Exchange) - RFC 7636
 *
 * PKCE es una extensión de seguridad para OAuth 2.0 que protege contra
 * ataques de interceptación del authorization code.
 *
 * Flujo:
 * 1. Generar code_verifier (string aleatorio de 43-128 caracteres)
 * 2. Generar code_challenge = BASE64URL(SHA256(code_verifier))
 * 3. Enviar code_challenge en authorization request
 * 4. Enviar code_verifier en token request
 * 5. Servidor verifica: SHA256(code_verifier) == code_challenge
 *
 * Referencia: https://datatracker.ietf.org/doc/html/rfc7636
 */
public class PKCE {

    private static final String ALLOWED_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String codeVerifier;
    private final String codeChallenge;

    /**
     * Constructor que genera automáticamente verifier y challenge
     */
    public PKCE() {
        this.codeVerifier = generateCodeVerifier();
        this.codeChallenge = generateCodeChallenge(this.codeVerifier);
    }

    /**
     * Obtener code_verifier (se envía en token request)
     */
    public String getCodeVerifier() {
        return codeVerifier;
    }

    /**
     * Obtener code_challenge (se envía en authorization request)
     */
    public String getCodeChallenge() {
        return codeChallenge;
    }

    /**
     * Generar code_verifier aleatorio (43-128 caracteres)
     *
     * RFC 7636: "high-entropy cryptographic random string using the
     * unreserved characters [A-Z] / [a-z] / [0-9] / "-" / "." / "_" / "~"
     */
    private String generateCodeVerifier() {
        return generateRandomString(128); // Longitud máxima permitida
    }

    /**
     * Generar code_challenge a partir del code_verifier
     *
     * code_challenge = BASE64URL(SHA256(ASCII(code_verifier)))
     */
    private String generateCodeChallenge(String codeVerifier) {
        try {
            // SHA256 hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII));

            // Base64 URL-safe encoding (sin padding)
            return base64UrlEncode(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Generar string aleatorio seguro
     */
    public static String generateRandomString(int length) {
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            result.append(ALLOWED_CHARS.charAt(RANDOM.nextInt(ALLOWED_CHARS.length())));
        }
        return result.toString();
    }

    /**
     * Base64 URL-safe encoding (sin padding)
     */
    private static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
