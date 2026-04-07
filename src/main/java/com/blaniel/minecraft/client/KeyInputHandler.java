package com.blaniel.minecraft.client;

import com.blaniel.minecraft.BlanielMod;
import com.blaniel.minecraft.client.gui.AgentSelectionScreen;
import com.blaniel.minecraft.screen.LoginScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/**
 * Handler de eventos de teclado
 */
public class KeyInputHandler {

	/**
	 * Registrar handlers de teclado
	 */
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// Verificar si se presionó la tecla de abrir UI
			while (BlanielKeyBindings.openUIKey.wasPressed()) {
				handleOpenUI(client);
			}

			// Verificar si se presionó la tecla de abrir chat
			while (BlanielKeyBindings.openChatKey.wasPressed()) {
				handleOpenChat(client);
			}
		});
	}

	/**
	 * Manejar apertura de UI
	 */
	private static void handleOpenUI(MinecraftClient client) {
		if (client.player == null) {
			return;
		}

		BlanielMod.LOGGER.info("Tecla K presionada - abriendo UI de Blaniel");

		// Verificar si el usuario está logueado
		if (BlanielMod.CONFIG.isLoggedIn()) {
			// Usuario logueado: abrir selección de agentes
			BlanielMod.LOGGER.info("Usuario logueado - abriendo AgentSelectionScreen");
			client.setScreen(new AgentSelectionScreen());
		} else {
			// Usuario no logueado: abrir login
			BlanielMod.LOGGER.info("Usuario no logueado - abriendo LoginScreen");
			client.setScreen(new LoginScreen(null));
		}
	}

	/**
	 * Manejar apertura de chat
	 */
	private static void handleOpenChat(MinecraftClient client) {
		if (client.player == null) {
			return;
		}

		BlanielMod.LOGGER.info("Tecla C presionada - abriendo chat de Blaniel");

		// Usar el handler de chat de BlanielChatHandler
		BlanielChatHandler.openChat(client);
	}
}
