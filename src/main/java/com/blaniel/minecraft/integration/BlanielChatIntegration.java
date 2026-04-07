package com.blaniel.minecraft.integration;

import com.google.gson.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import com.blaniel.minecraft.entity.BlanielVillagerEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Ejemplo de integración con el sistema de chat grupal de Blaniel
 *
 * Este archivo muestra cómo enviar mensajes desde Minecraft al backend
 * y procesar las respuestas de los agentes IA.
 *
 * Uso:
 * 1. El usuario configura su API key en blaniel-mc.properties
 * 2. Cuando envía un mensaje, se detectan agentes cercanos
 * 3. Se envía el mensaje al backend con contexto espacial
 * 4. Las respuestas se muestran como chat bubbles en las entidades
 */
public class BlanielChatIntegration {

    // Cliente HTTP con configuración optimizada para localhost
    // Usamos HTTP/1.1 para compatibilidad con servidores de desarrollo (localhost sin TLS)
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private static final Gson GSON = new Gson();

    /**
     * Obtiene la URL de la API desde la configuración
     */
    private static String getApiUrl() {
        return com.blaniel.minecraft.BlanielMod.CONFIG.getApiUrl() + "/api/v1/minecraft/message";
    }

    /**
     * Envía un mensaje de chat al backend y procesa las respuestas
     *
     * @param player El jugador que envía el mensaje
     * @param message El contenido del mensaje
     * @param jwtToken JWT token del usuario (desde login)
     */
    public static CompletableFuture<Void> sendChatMessage(
            PlayerEntity player,
            String message,
            String jwtToken
    ) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 1. Preparar datos del jugador
                JsonObject playerData = buildPlayerData(player);

                // 2. Detectar agentes cercanos (radio de 16 bloques)
                JsonArray nearbyAgents = findNearbyAgents(player, 16.0);

                if (nearbyAgents.size() == 0) {
                    player.sendMessage(
                        Text.literal("§cNo hay agentes IA cercanos para responder"),
                        false
                    );
                    return;
                }

                // 3. Construir request body
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("content", message);
                requestBody.add("player", playerData);
                requestBody.add("nearbyAgents", nearbyAgents);

                // 4. Enviar request HTTP
                String apiUrl = getApiUrl();
                System.out.println("[Blaniel Chat] Enviando mensaje a: " + apiUrl);
                System.out.println("[Blaniel Chat] Request body: " + requestBody.toString());

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", "BlanielMinecraft/0.1.0")
                    .header("Authorization", "Bearer " + jwtToken)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .timeout(Duration.ofSeconds(30))
                    .build();

                HttpResponse<String> response = HTTP_CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
                );

                System.out.println("[Blaniel Chat] Response status: " + response.statusCode());
                System.out.println("[Blaniel Chat] Response body: " + response.body());

                // 5. Procesar respuesta
                if (response.statusCode() == 200) {
                    processAgentResponses(player, response.body());
                } else {
                    handleError(player, response.statusCode(), response.body());
                }

            } catch (Exception e) {
                player.sendMessage(
                    Text.literal("§cError al comunicarse con el servidor: " + e.getMessage()),
                    false
                );
                e.printStackTrace();
            }
        });
    }

    /**
     * Construye datos del jugador en formato JSON
     */
    private static JsonObject buildPlayerData(PlayerEntity player) {
        JsonObject playerObj = new JsonObject();
        playerObj.addProperty("uuid", player.getUuidAsString());
        playerObj.addProperty("name", player.getName().getString());

        JsonObject position = new JsonObject();
        position.addProperty("x", player.getX());
        position.addProperty("y", player.getY());
        position.addProperty("z", player.getZ());
        position.addProperty("yaw", player.getYaw());
        position.addProperty("pitch", player.getPitch());
        position.addProperty("dimensionId", player.getWorld().getRegistryKey().getValue().toString());

        playerObj.add("position", position);

        return playerObj;
    }

    /**
     * Encuentra agentes BlanielVillager cercanos al jugador
     */
    private static JsonArray findNearbyAgents(PlayerEntity player, double radius) {
        JsonArray agents = new JsonArray();

        // Crear bounding box alrededor del jugador
        Box searchBox = new Box(
            player.getX() - radius,
            player.getY() - radius,
            player.getZ() - radius,
            player.getX() + radius,
            player.getY() + radius,
            player.getZ() + radius
        );

        // Buscar entidades BlanielVillager en el área
        List<BlanielVillagerEntity> nearbyVillagers = player.getWorld()
            .getEntitiesByClass(
                BlanielVillagerEntity.class,
                searchBox,
                entity -> entity.isAlive() && entity.distanceTo(player) <= radius
            );

        // Convertir a JSON
        for (BlanielVillagerEntity villager : nearbyVillagers) {
            JsonObject agentObj = new JsonObject();
            agentObj.addProperty("agentId", villager.getBlanielAgentId());
            agentObj.addProperty("entityId", villager.getId());
            agentObj.addProperty("name", villager.getName().getString());

            JsonObject position = new JsonObject();
            position.addProperty("x", villager.getX());
            position.addProperty("y", villager.getY());
            position.addProperty("z", villager.getZ());
            agentObj.add("position", position);

            agentObj.addProperty("isActive", true);

            agents.add(agentObj);
        }

        return agents;
    }

    /**
     * Procesa las respuestas de los agentes IA
     */
    private static void processAgentResponses(PlayerEntity player, String responseBody) {
        try {
            JsonObject result = JsonParser.parseString(responseBody).getAsJsonObject();

            // Mostrar contexto de proximidad (debug)
            JsonObject proximityContext = result.getAsJsonObject("proximityContext");
            boolean isGroupConversation = proximityContext.get("isGroupConversation").getAsBoolean();

            player.sendMessage(
                Text.literal(
                    isGroupConversation
                        ? "§7[Conversación Grupal]"
                        : "§7[Conversación Individual]"
                ),
                true // Actionbar
            );

            // Procesar respuestas de cada agente
            JsonArray agentResponses = result.getAsJsonArray("agentResponses");

            for (JsonElement elem : agentResponses) {
                JsonObject agentResp = elem.getAsJsonObject();

                String agentId = agentResp.get("agentId").getAsString();

                // Encontrar la entidad correspondiente
                BlanielVillagerEntity entity = findEntityByAgentId(player, agentId);

                String agentName = agentResp.get("agentName").getAsString();
                String content = agentResp.get("content").getAsString();

                if (entity != null) {
                    // Verificar si tiene estructura de partes (respuesta avanzada)
                    if (agentResp.has("parts")) {
                        // Procesar respuesta estructurada con comandos
                        CommandHandler.processStructuredResponse(entity, agentResp);
                    } else {
                        // Fallback: respuesta simple
                        String animationHint = agentResp.has("animationHint")
                            ? agentResp.get("animationHint").getAsString()
                            : "talking";

                        // Mostrar chat bubble sobre la entidad
                        entity.displayChatBubble(content);
                        entity.playAnimation(animationHint);
                    }
                }

                // SIEMPRE mostrar en el chat del jugador (consola con T)
                player.sendMessage(
                    Text.literal("§b" + agentName + ": §f" + content),
                    false
                );
            }

            // Mostrar metadata (debug)
            JsonObject metadata = result.getAsJsonObject("metadata");
            int responseTime = metadata.get("responseTime").getAsInt();
            int agentsResponded = metadata.get("agentsResponded").getAsInt();

            player.sendMessage(
                Text.literal(
                    String.format(
                        "§7[%d agente(s) respondieron en %dms]",
                        agentsResponded,
                        responseTime
                    )
                ),
                true // Actionbar
            );

        } catch (Exception e) {
            player.sendMessage(
                Text.literal("§cError al procesar respuestas: " + e.getMessage()),
                false
            );
            e.printStackTrace();
        }
    }

    /**
     * Encuentra una entidad BlanielVillager por su agentId
     */
    private static BlanielVillagerEntity findEntityByAgentId(PlayerEntity player, String agentId) {
        List<BlanielVillagerEntity> entities = player.getWorld()
            .getEntitiesByClass(
                BlanielVillagerEntity.class,
                new Box(
                    player.getX() - 32,
                    player.getY() - 32,
                    player.getZ() - 32,
                    player.getX() + 32,
                    player.getY() + 32,
                    player.getZ() + 32
                ),
                entity -> agentId.equals(entity.getBlanielAgentId())
            );

        return entities.isEmpty() ? null : entities.get(0);
    }

    /**
     * Maneja errores de la API
     */
    private static void handleError(PlayerEntity player, int statusCode, String body) {
        try {
            JsonObject error = JsonParser.parseString(body).getAsJsonObject();
            String errorMessage = error.has("error")
                ? error.get("error").getAsString()
                : "Error desconocido";
            String errorCode = error.has("code")
                ? error.get("code").getAsString()
                : "UNKNOWN";

            String displayMessage = switch (statusCode) {
                case 401 -> "§cError de autenticación. Verifica tu API key en blaniel-mc.properties";
                case 429 -> "§cLímite de tasa excedido. Espera un momento antes de enviar otro mensaje.";
                case 404 -> "§cNo se encontraron agentes. ¿Los has creado en blaniel.com?";
                default -> String.format("§cError %d: %s (Código: %s)", statusCode, errorMessage, errorCode);
            };

            player.sendMessage(Text.literal(displayMessage), false);

        } catch (Exception e) {
            player.sendMessage(
                Text.literal(String.format("§cError HTTP %d: %s", statusCode, body)),
                false
            );
        }
    }

    /**
     * Obtiene el JWT token desde la configuración (guardado después del login)
     */
    public static String getJwtToken() {
        return com.blaniel.minecraft.BlanielMod.CONFIG.getJwtToken();
    }
}
