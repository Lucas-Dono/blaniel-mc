package com.blaniel.minecraft.oauth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Servidor HTTP local para recibir OAuth2 callback
 *
 * Escucha en http://127.0.0.1:8888/callback y captura
 * el authorization code enviado por Google.
 *
 * Inspirado en: Google OAuth Client Library - LocalServerReceiver
 * https://github.com/googleapis/google-oauth-java-client/blob/main/google-oauth-client-jetty/src/main/java/com/google/api/client/extensions/jetty/auth/oauth2/LocalServerReceiver.java
 */
public class LocalCallbackServer {

    private final int port;
    private final String callbackPath;

    private HttpServer server;
    private Map<String, String> callbackParams;
    private CountDownLatch latch;

    public LocalCallbackServer(int port, String callbackPath) {
        this.port = port;
        this.callbackPath = callbackPath;
    }

    /**
     * Iniciar servidor HTTP
     */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext(callbackPath, new CallbackHandler());
        server.setExecutor(null); // Use default executor
        server.start();

        latch = new CountDownLatch(1);

        System.out.println("[LocalCallbackServer] Servidor iniciado en http://127.0.0.1:" + port + callbackPath);
    }

    /**
     * Esperar callback (bloquea thread hasta recibir callback o timeout)
     *
     * @param timeoutMs Timeout en milisegundos
     * @return Parámetros del callback (code, state, error, etc.)
     */
    public Map<String, String> waitForCallback(long timeoutMs) throws InterruptedException {
        boolean received = latch.await(timeoutMs, TimeUnit.MILLISECONDS);

        // Detener servidor
        server.stop(0);

        if (!received) {
            throw new RuntimeException("Timeout esperando autorización del usuario");
        }

        return callbackParams;
    }

    /**
     * Handler para el callback de OAuth2
     */
    private class CallbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // Parsear query parameters
                URI requestUri = exchange.getRequestURI();
                String query = requestUri.getQuery();
                callbackParams = queryToMap(query);

                // Mostrar página de éxito al usuario
                String response = buildSuccessPage();

                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }

                // Notificar que recibimos el callback
                latch.countDown();

            } catch (Exception e) {
                System.err.println("[CallbackHandler] Error procesando callback: " + e.getMessage());
                e.printStackTrace();

                // Enviar error al browser
                String errorResponse = buildErrorPage(e.getMessage());
                exchange.sendResponseHeaders(500, errorResponse.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorResponse.getBytes(StandardCharsets.UTF_8));
                }
            }
        }

        /**
         * Convertir query string a Map
         * Ejemplo: "code=abc&state=xyz" -> {code: "abc", state: "xyz"}
         */
        private Map<String, String> queryToMap(String query) {
            Map<String, String> result = new HashMap<>();
            if (query == null || query.isEmpty()) {
                return result;
            }

            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    result.put(keyValue[0], keyValue[1]);
                }
            }

            return result;
        }

        /**
         * HTML de éxito mostrado al usuario en el browser
         */
        private String buildSuccessPage() {
            return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "  <meta charset='UTF-8'>\n" +
                "  <title>Login Exitoso - Blaniel</title>\n" +
                "  <style>\n" +
                "    body {\n" +
                "      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;\n" +
                "      display: flex;\n" +
                "      justify-content: center;\n" +
                "      align-items: center;\n" +
                "      height: 100vh;\n" +
                "      margin: 0;\n" +
                "      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
                "    }\n" +
                "    .container {\n" +
                "      background: white;\n" +
                "      padding: 40px;\n" +
                "      border-radius: 12px;\n" +
                "      box-shadow: 0 10px 40px rgba(0,0,0,0.2);\n" +
                "      text-align: center;\n" +
                "      max-width: 400px;\n" +
                "    }\n" +
                "    h1 { color: #667eea; margin-bottom: 10px; }\n" +
                "    p { color: #555; line-height: 1.6; }\n" +
                "    .success { color: #4bb71b; font-size: 60px; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div class='container'>\n" +
                "    <div class='success'>✓</div>\n" +
                "    <h1>¡Login Exitoso!</h1>\n" +
                "    <p>Has iniciado sesión correctamente con Google.</p>\n" +
                "    <p>Ahora puedes cerrar esta ventana y volver a Minecraft.</p>\n" +
                "  </div>\n" +
                "</body>\n" +
                "</html>";
        }

        /**
         * HTML de error mostrado al usuario en el browser
         */
        private String buildErrorPage(String errorMessage) {
            return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "  <meta charset='UTF-8'>\n" +
                "  <title>Error - Blaniel</title>\n" +
                "  <style>\n" +
                "    body {\n" +
                "      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;\n" +
                "      display: flex;\n" +
                "      justify-content: center;\n" +
                "      align-items: center;\n" +
                "      height: 100vh;\n" +
                "      margin: 0;\n" +
                "      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
                "    }\n" +
                "    .container {\n" +
                "      background: white;\n" +
                "      padding: 40px;\n" +
                "      border-radius: 12px;\n" +
                "      box-shadow: 0 10px 40px rgba(0,0,0,0.2);\n" +
                "      text-align: center;\n" +
                "      max-width: 400px;\n" +
                "    }\n" +
                "    h1 { color: #dc3545; margin-bottom: 10px; }\n" +
                "    p { color: #555; line-height: 1.6; }\n" +
                "    .error { color: #dc3545; font-size: 60px; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div class='container'>\n" +
                "    <div class='error'>✗</div>\n" +
                "    <h1>Error de Autenticación</h1>\n" +
                "    <p>" + (errorMessage != null ? errorMessage : "Error desconocido") + "</p>\n" +
                "    <p>Por favor, intenta nuevamente desde Minecraft.</p>\n" +
                "  </div>\n" +
                "</body>\n" +
                "</html>";
        }
    }
}
