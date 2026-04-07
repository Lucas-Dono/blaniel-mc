package com.blaniel.minecraft.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import com.blaniel.minecraft.integration.BlanielChatIntegration;

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

        // Abrir screen de chat personalizado
        client.setScreen(new BlanielChatScreen(
            message -> sendMessageToBackend(client, message)
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
