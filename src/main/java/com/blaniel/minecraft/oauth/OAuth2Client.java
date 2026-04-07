package com.blaniel.minecraft.oauth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Cliente OAuth2 para Google Sign-In en Minecraft
 *
 * Implementa Authorization Code Flow con PKCE (RFC 7636)
 *
 * Flujo:
 * 1. Generar code_verifier y code_challenge (PKCE)
 * 2. Abrir browser con authorization URL
 * 3. Iniciar servidor HTTP local en 127.0.0.1:8888
 * 4. Usuario autoriza en browser
 * 5. Capturar authorization code del callback
 * 6. Intercambiar code por tokens (access_token, id_token)
 * 7. Enviar id_token a backend de Blaniel
 * 8. Recibir JWT de Blaniel
 */
public class OAuth2Client {

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    // Google OAuth2 endpoints
    private static final String AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";

    // OAuth2 configuration
    private final String clientId;
    private final String redirectUri;
    private final String blanielApiUrl;

    // PKCE values (generated per request)
    private String codeVerifier;
    private String codeChallenge;

    // CSRF protection
    private String state;

    public OAuth2Client(String clientId, String redirectUri, String blanielApiUrl) {
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.blanielApiUrl = blanielApiUrl;
    }

    /**
     * Iniciar flujo de autorización completo
     *
     * @return CompletableFuture con LoginResponse de Blaniel
     */
    public CompletableFuture<LoginResponse> authorize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Generar PKCE codes
                PKCE pkce = new PKCE();
                codeVerifier = pkce.getCodeVerifier();
                codeChallenge = pkce.getCodeChallenge();

                // 2. Generar state para CSRF protection
                state = PKCE.generateRandomString(32);

                // 3. Construir authorization URL
                String authUrl = buildAuthorizationUrl();

                System.out.println("[OAuth2] Authorization URL: " + authUrl);

                // 4. Abrir browser
                BrowserLauncher.openURL(authUrl);

                // 5. Iniciar servidor HTTP local y esperar callback
                LocalCallbackServer callbackServer = new LocalCallbackServer(8888, "/callback");
                callbackServer.start();

                System.out.println("[OAuth2] Esperando autorización del usuario...");

                // Bloquear hasta recibir callback (con timeout de 5 minutos)
                Map<String, String> callbackParams = callbackServer.waitForCallback(5 * 60 * 1000);

                // 6. Validar state parameter (CSRF)
                String receivedState = callbackParams.get("state");
                if (!state.equals(receivedState)) {
                    throw new RuntimeException("Estado CSRF inválido");
                }

                // 7. Verificar si hubo error
                String error = callbackParams.get("error");
                if (error != null) {
                    throw new RuntimeException("Error de autorización: " + error);
                }

                // 8. Obtener authorization code
                String authorizationCode = callbackParams.get("code");
                if (authorizationCode == null || authorizationCode.isEmpty()) {
                    throw new RuntimeException("No se recibió código de autorización");
                }

                System.out.println("[OAuth2] Código de autorización recibido");

                // 9. Intercambiar code por tokens
                TokenResponse tokens = exchangeCodeForTokens(authorizationCode);

                System.out.println("[OAuth2] Tokens recibidos de Google");

                // 10. Enviar id_token a backend de Blaniel
                LoginResponse blanielResponse = loginToBlaniel(tokens.id_token);

                // 11. Guardar refresh_token de Google en la respuesta
                blanielResponse.googleRefreshToken = tokens.refresh_token;

                System.out.println("[OAuth2] Login exitoso en Blaniel");

                return blanielResponse;

            } catch (Exception e) {
                System.err.println("[OAuth2] Error en flujo de autorización: " + e.getMessage());
                e.printStackTrace();

                LoginResponse errorResponse = new LoginResponse();
                errorResponse.success = false;
                errorResponse.error = e.getMessage();
                return errorResponse;
            }
        });
    }

    /**
     * Construir URL de autorización con todos los parámetros
     */
    private String buildAuthorizationUrl() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("client_id", clientId);
        params.put("redirect_uri", redirectUri);
        params.put("response_type", "code");
        params.put("scope", "openid email profile");
        params.put("code_challenge", codeChallenge);
        params.put("code_challenge_method", "S256");
        params.put("state", state);
        params.put("access_type", "offline"); // Para obtener refresh_token
        params.put("prompt", "select_account"); // Permitir cambiar de cuenta

        StringBuilder url = new StringBuilder(AUTHORIZATION_ENDPOINT);
        url.append("?");

        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                url.append("&");
            }
            url.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            url.append("=");
            url.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            first = false;
        }

        return url.toString();
    }

    /**
     * Intercambiar authorization code por tokens
     */
    private TokenResponse exchangeCodeForTokens(String authorizationCode) throws Exception {
        // Construir request body (application/x-www-form-urlencoded)
        Map<String, String> params = new HashMap<>();
        params.put("code", authorizationCode);
        params.put("client_id", clientId);
        params.put("redirect_uri", redirectUri);
        params.put("grant_type", "authorization_code");
        params.put("code_verifier", codeVerifier); // PKCE

        String body = buildFormUrlEncoded(params);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(TOKEN_ENDPOINT))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Error al intercambiar código: HTTP " + response.statusCode() + " - " + response.body());
        }

        return GSON.fromJson(response.body(), TokenResponse.class);
    }

    /**
     * Login a backend de Blaniel con id_token
     */
    private LoginResponse loginToBlaniel(String idToken) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("id_token", idToken);

        String url = blanielApiUrl + "/api/auth/minecraft-oauth-login";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            // Parsear error del backend
            JsonObject errorJson = GSON.fromJson(response.body(), JsonObject.class);
            String errorMessage = errorJson.has("error") ? errorJson.get("error").getAsString() : "Error desconocido";

            LoginResponse errorResponse = new LoginResponse();
            errorResponse.success = false;
            errorResponse.error = errorMessage;
            return errorResponse;
        }

        LoginResponse loginResponse = GSON.fromJson(response.body(), LoginResponse.class);
        loginResponse.success = true;
        return loginResponse;
    }

    /**
     * Helper: Construir body application/x-www-form-urlencoded
     */
    private String buildFormUrlEncoded(Map<String, String> params) throws Exception {
        StringBuilder body = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                body.append("&");
            }
            body.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            body.append("=");
            body.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            first = false;
        }
        return body.toString();
    }

    /**
     * Refrescar tokens usando refresh_token (método estático)
     *
     * Este método se llama automáticamente al iniciar el juego si hay un refresh_token guardado,
     * evitando que el usuario tenga que hacer el flujo OAuth completo cada vez.
     *
     * @param clientId Google OAuth Client ID
     * @param refreshToken Refresh token de Google (guardado en config)
     * @param blanielApiUrl URL del backend de Blaniel
     * @return CompletableFuture con LoginResponse actualizado
     */
    public static CompletableFuture<LoginResponse> refreshTokens(
        String clientId,
        String refreshToken,
        String blanielApiUrl
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("[OAuth2 Refresh] Refrescando tokens con refresh_token...");

                // 1. Intercambiar refresh_token por nuevos tokens
                Map<String, String> params = new HashMap<>();
                params.put("client_id", clientId);
                params.put("refresh_token", refreshToken);
                params.put("grant_type", "refresh_token");

                StringBuilder bodyBuilder = new StringBuilder();
                boolean first = true;
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    if (!first) {
                        bodyBuilder.append("&");
                    }
                    bodyBuilder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                    bodyBuilder.append("=");
                    bodyBuilder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
                    first = false;
                }

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TOKEN_ENDPOINT))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyBuilder.toString()))
                    .timeout(Duration.ofSeconds(30))
                    .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new RuntimeException("Error al refrescar tokens: HTTP " + response.statusCode() + " - " + response.body());
                }

                TokenResponse tokens = GSON.fromJson(response.body(), TokenResponse.class);

                System.out.println("[OAuth2 Refresh] Nuevos tokens recibidos de Google");

                // 2. Enviar nuevo id_token a backend de Blaniel
                JsonObject body = new JsonObject();
                body.addProperty("id_token", tokens.id_token);

                String url = blanielApiUrl + "/api/auth/minecraft-oauth-login";

                HttpRequest blanielRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .timeout(Duration.ofSeconds(30))
                    .build();

                HttpResponse<String> blanielResponse = HTTP_CLIENT.send(blanielRequest, HttpResponse.BodyHandlers.ofString());

                if (blanielResponse.statusCode() != 200) {
                    JsonObject errorJson = GSON.fromJson(blanielResponse.body(), JsonObject.class);
                    String errorMessage = errorJson.has("error") ? errorJson.get("error").getAsString() : "Error desconocido";

                    LoginResponse errorResponse = new LoginResponse();
                    errorResponse.success = false;
                    errorResponse.error = errorMessage;
                    return errorResponse;
                }

                LoginResponse loginResponse = GSON.fromJson(blanielResponse.body(), LoginResponse.class);
                loginResponse.success = true;

                // 3. Importante: mantener el mismo refresh_token (o usar el nuevo si Google envió uno)
                loginResponse.googleRefreshToken = tokens.refresh_token != null ? tokens.refresh_token : refreshToken;

                System.out.println("[OAuth2 Refresh] Login exitoso en Blaniel (sesión renovada)");

                return loginResponse;

            } catch (Exception e) {
                System.err.println("[OAuth2 Refresh] Error al refrescar tokens: " + e.getMessage());
                e.printStackTrace();

                LoginResponse errorResponse = new LoginResponse();
                errorResponse.success = false;
                errorResponse.error = e.getMessage();
                return errorResponse;
            }
        });
    }

    // ===== Data Classes =====

    public static class TokenResponse {
        public String access_token;
        public String id_token;
        public String refresh_token;
        public int expires_in;
        public String token_type;
        public String scope;
    }

    public static class LoginResponse {
        public boolean success;
        public String error;
        public String token;
        public int expiresIn;
        public String googleRefreshToken; // Refresh token de Google para renovar sesión
        public UserDataResponse user;
        public AgentData[] agents;
        public String message;
    }

    public static class UserDataResponse {
        public String id;
        public String email;
        public String name;
        public String image;
        public String plan;
    }

    public static class AgentData {
        public String id;
        public String name;
        public String gender;
        public Integer age;
    }
}
