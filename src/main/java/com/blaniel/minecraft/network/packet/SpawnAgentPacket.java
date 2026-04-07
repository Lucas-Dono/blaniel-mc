package com.blaniel.minecraft.network.packet;

import com.blaniel.minecraft.BlanielMod;
import com.blaniel.minecraft.entity.BlanielVillagerEntity;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Packet para spawnear un agente como aldeano
 */
public class SpawnAgentPacket {

    /**
     * Crear packet para enviar al servidor
     */
    public static PacketByteBuf create(String agentId, String agentName) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(agentId);
        buf.writeString(agentName);
        return buf;
    }

    /**
     * Manejar packet en el servidor
     */
    public static void handle(MinecraftServer server, ServerPlayerEntity player,
                             String agentId, String agentName) {
        try {
            // Crear aldeano en posición del jugador
            BlanielVillagerEntity villager = new BlanielVillagerEntity(
                BlanielMod.BLANIEL_VILLAGER,
                player.getWorld()
            );

            villager.refreshPositionAndAngles(
                player.getX(), player.getY(), player.getZ(),
                player.getYaw(), 0.0f
            );

            villager.setBlanielAgentId(agentId);
            villager.setBlanielAgentName(agentName);

            player.getWorld().spawnEntity(villager);

            player.sendMessage(
                Text.literal("§a[Blaniel] §fAldeano spawneado: " + agentName),
                false
            );

            BlanielMod.LOGGER.info("Spawned agent {} for player {}", agentName, player.getName().getString());

        } catch (Exception e) {
            player.sendMessage(
                Text.literal("§c[Blaniel] §fError al spawnear: " + e.getMessage()),
                false
            );
            BlanielMod.LOGGER.error("Error spawning agent", e);
        }
    }
}
