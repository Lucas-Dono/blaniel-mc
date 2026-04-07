package com.blaniel.minecraft.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

import com.blaniel.minecraft.integration.BlanielChatIntegration;
import com.blaniel.minecraft.entity.BlanielVillagerEntity;

import java.util.List;

/**
 * Manejador de chat avanzado para Blaniel
 *
 * Proporciona funcionalidad de chat para comunicarse con los agentes de Blaniel.
 */
public class BlanielChatHandler {

    /**
     * Inicializa el handler de chat
     */
    public static void initialize() {
        // Inicialización básica si es necesaria en el futuro
    }

    /**
     * Encuentra el agente más cercano al jugador (dentro de 16 bloques)
     */
    private static BlanielVillagerEntity findNearestAgent(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return null;
        }

        double radius = 16.0;
        Box searchBox = new Box(
            client.player.getX() - radius,
            client.player.getY() - radius,
            client.player.getZ() - radius,
            client.player.getX() + radius,
            client.player.getY() + radius,
            client.player.getZ() + radius
        );

        List<BlanielVillagerEntity> nearbyAgents = client.world.getEntitiesByClass(
            BlanielVillagerEntity.class,
            searchBox,
            entity -> entity.isAlive() && entity.distanceTo(client.player) <= radius
        );

        if (nearbyAgents.isEmpty()) {
            return null;
        }

        // Retornar el más cercano
        return nearbyAgents.stream()
            .min((a, b) -> Double.compare(
                a.distanceTo(client.player),
                b.distanceTo(client.player)
            ))
            .orElse(null);
    }

    /**
     * Abre el chat de Blaniel (llamado desde KeyInputHandler)
     */
    public static void openChat(MinecraftClient client) {
        if (client == null || client.player == null) {
            return;
        }

        // Verificar que el usuario esté logueado
        if (!com.blaniel.minecraft.BlanielMod.CONFIG.isLoggedIn()) {
            client.player.sendMessage(
                Text.literal("§c[Blaniel] §fDebes iniciar sesión primero"),
                false
            );
            // Abrir pantalla de login
            client.setScreen(new com.blaniel.minecraft.screen.LoginScreen(null));
            return;
        }

        // Detectar agente más cercano
        BlanielVillagerEntity nearestAgent = findNearestAgent(client);
        Integer agentEntityId = null;

        if (nearestAgent != null) {
            agentEntityId = nearestAgent.getId();

            // Enviar packet al servidor para iniciar modo conversación
            var buf = PacketByteBufs.create();
            buf.writeInt(agentEntityId);
            ClientPlayNetworking.send(
                com.blaniel.minecraft.network.NetworkHandler.CONVERSATION_START_PACKET,
                buf
            );

            com.blaniel.minecraft.BlanielMod.LOGGER.info(
                "Modo conversación iniciado con {} (ID: {})",
                nearestAgent.getBlanielAgentName(),
                agentEntityId
            );
        } else {
            client.player.sendMessage(
                Text.literal("§e[Blaniel] §fNo hay agentes cercanos"),
                true // Actionbar
            );
        }

        // Abrir screen de chat personalizado (pasando el ID del agente)
        client.setScreen(new BlanielChatScreen(
            message -> sendMessageToBackend(client, message),
            agentEntityId
        ));
    }

    /**
     * Envía mensaje al backend
     */
    private static void sendMessageToBackend(MinecraftClient client, String message) {
        if (client.player == null) return;

        // Validar que el mensaje no esté vacío
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        // Obtener JWT token
        String jwtToken = com.blaniel.minecraft.BlanielMod.CONFIG.getJwtToken();
        if (jwtToken == null) {
            client.player.sendMessage(
                Text.literal("§c[Blaniel] §fNo estás logueado. Reinicia el juego."),
                false
            );
            return;
        }

        // Mostrar feedback inmediato
        client.player.sendMessage(
            Text.literal("§7Enviando mensaje..."),
            true // Actionbar
        );

        // Enviar mensaje de forma asíncrona
        BlanielChatIntegration.sendChatMessage(client.player, message, jwtToken)
            .exceptionally(ex -> {
                client.execute(() -> {
                    client.player.sendMessage(
                        Text.literal("§c[Blaniel] §fError: " + ex.getMessage()),
                        false
                    );
                });
                return null;
            });
    }
}
