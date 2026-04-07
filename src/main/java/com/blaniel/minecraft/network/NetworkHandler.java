package com.blaniel.minecraft.network;

import com.blaniel.minecraft.BlanielMod;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;

/**
 * Manejador de networking para comunicación cliente-servidor
 */
public class NetworkHandler {

    // Identificadores de packets
    public static final Identifier SPAWN_AGENT_PACKET = new Identifier(BlanielMod.MOD_ID, "spawn_agent");
    public static final Identifier CHAT_MESSAGE_PACKET = new Identifier(BlanielMod.MOD_ID, "chat_message");
    public static final Identifier OPEN_CHAT_PACKET = new Identifier(BlanielMod.MOD_ID, "open_chat");
    public static final Identifier CONVERSATION_START_PACKET = new Identifier(BlanielMod.MOD_ID, "conversation_start");
    public static final Identifier CONVERSATION_END_PACKET = new Identifier(BlanielMod.MOD_ID, "conversation_end");

    /**
     * Registrar receivers del lado del servidor
     */
    public static void registerServerReceivers() {
        // Registrar handler para spawn de agentes
        ServerPlayNetworking.registerGlobalReceiver(SPAWN_AGENT_PACKET,
            (server, player, handler, buf, responseSender) -> {
                // Leer datos del packet
                String agentId = buf.readString();
                String agentName = buf.readString();

                BlanielMod.LOGGER.info("SPAWN_AGENT_PACKET recibido en servidor: agentId={}, agentName={}, player={}",
                    agentId, agentName, player.getName().getString());

                // Ejecutar en thread principal del servidor
                server.execute(() -> {
                    BlanielMod.LOGGER.info("Ejecutando SpawnAgentPacket.handle() en thread principal del servidor");
                    // Importar la lógica de spawn aquí
                    com.blaniel.minecraft.network.packet.SpawnAgentPacket.handle(
                        server, player, agentId, agentName
                    );
                });
            }
        );

        // Registrar handler para mensajes de chat
        ServerPlayNetworking.registerGlobalReceiver(CHAT_MESSAGE_PACKET,
            (server, player, handler, buf, responseSender) -> {
                // Leer datos del packet
                int villagerEntityId = buf.readInt();
                String message = buf.readString();

                // Ejecutar en thread principal del servidor
                server.execute(() -> {
                    com.blaniel.minecraft.network.packet.ChatMessagePacket.handle(
                        server, player, villagerEntityId, message
                    );
                });
            }
        );

        // Registrar handler para inicio de conversación
        ServerPlayNetworking.registerGlobalReceiver(CONVERSATION_START_PACKET,
            (server, player, handler, buf, responseSender) -> {
                // Leer ID de la entidad
                int entityId = buf.readInt();

                // Ejecutar en thread principal del servidor
                server.execute(() -> {
                    var entity = player.getWorld().getEntityById(entityId);
                    if (entity instanceof com.blaniel.minecraft.entity.BlanielVillagerEntity) {
                        com.blaniel.minecraft.entity.BlanielVillagerEntity villager =
                            (com.blaniel.minecraft.entity.BlanielVillagerEntity) entity;
                        villager.enterConversationMode(player);
                        BlanielMod.LOGGER.info("Conversación iniciada con entidad {} por jugador {}",
                            entityId, player.getName().getString());
                    }
                });
            }
        );

        // Registrar handler para fin de conversación
        ServerPlayNetworking.registerGlobalReceiver(CONVERSATION_END_PACKET,
            (server, player, handler, buf, responseSender) -> {
                // Leer ID de la entidad
                int entityId = buf.readInt();

                // Ejecutar en thread principal del servidor
                server.execute(() -> {
                    var entity = player.getWorld().getEntityById(entityId);
                    if (entity instanceof com.blaniel.minecraft.entity.BlanielVillagerEntity) {
                        com.blaniel.minecraft.entity.BlanielVillagerEntity villager =
                            (com.blaniel.minecraft.entity.BlanielVillagerEntity) entity;
                        villager.exitConversationMode();
                        BlanielMod.LOGGER.info("Conversación terminada con entidad {} por jugador {}",
                            entityId, player.getName().getString());
                    }
                });
            }
        );

        BlanielMod.LOGGER.info("Network handlers registrados");
    }
}
