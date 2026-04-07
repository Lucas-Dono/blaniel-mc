package com.blaniel.minecraft.client.network;

import com.blaniel.minecraft.BlanielMod;
import com.blaniel.minecraft.network.NetworkHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Manejador de networking del lado del cliente
 */
public class ClientNetworkHandler {

	/**
	 * Registrar receivers del lado del cliente
	 */
	public static void register() {
		// Registrar handler para abrir ventana de chat
		ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.OPEN_CHAT_PACKET,
			(client, handler, buf, responseSender) -> {
				// Leer datos del packet
				int entityId = buf.readInt();
				String agentId = buf.readString();
				String agentName = buf.readString();

				// Ejecutar en thread principal del cliente
				client.execute(() -> {
					BlanielMod.LOGGER.info("Recibido OPEN_CHAT_PACKET: entityId={}, agentId={}, agentName={}",
						entityId, agentId, agentName);

					// TODO: Abrir GUI de chat cuando esté implementada
					// com.blaniel.minecraft.client.gui.AgentChatScreen.open(entityId, agentId, agentName);
				});
			}
		);

		BlanielMod.LOGGER.info("Client network handlers registrados");
	}
}
