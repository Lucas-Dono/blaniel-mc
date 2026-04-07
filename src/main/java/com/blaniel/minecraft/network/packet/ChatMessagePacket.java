package com.blaniel.minecraft.network.packet;

import com.blaniel.minecraft.BlanielMod;
import com.blaniel.minecraft.entity.BlanielVillagerEntity;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Packet para enviar mensajes de chat a un aldeano
 */
public class ChatMessagePacket {

    /**
     * Crear packet para enviar al servidor
     */
    public static PacketByteBuf create(int villagerEntityId, String message) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(villagerEntityId);
        buf.writeString(message);
        return buf;
    }

    /**
     * Manejar packet en el servidor
     */
    public static void handle(MinecraftServer server, ServerPlayerEntity player,
                             int villagerEntityId, String message) {
        try {
            // Buscar la entidad del aldeano
            Entity entity = player.getWorld().getEntityById(villagerEntityId);

            if (!(entity instanceof BlanielVillagerEntity)) {
                player.sendMessage(
                    Text.literal("§c[Blaniel] §fAldeano no encontrado"),
                    false
                );
                return;
            }

            BlanielVillagerEntity villager = (BlanielVillagerEntity) entity;

            if (villager.getBlanielAgentId().isEmpty()) {
                player.sendMessage(
                    Text.literal("§c[Blaniel] §fEl aldeano no tiene agente asignado"),
                    false
                );
                return;
            }

            // Mostrar mensaje del jugador
            player.sendMessage(Text.literal("§eTú§f: " + message), false);

            // Enviar mensaje al agente
            villager.sendMessageToAgent(message, player);

            BlanielMod.LOGGER.info("Player {} sent message to agent {}: {}",
                player.getName().getString(), villager.getBlanielAgentName(), message);

        } catch (Exception e) {
            player.sendMessage(
                Text.literal("§c[Blaniel] §fError al enviar mensaje: " + e.getMessage()),
                false
            );
            BlanielMod.LOGGER.error("Error sending chat message", e);
        }
    }
}
