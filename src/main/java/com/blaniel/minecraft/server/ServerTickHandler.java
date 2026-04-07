package com.blaniel.minecraft.server;

import com.blaniel.minecraft.ai.AmbientDialogueHandler;
import com.blaniel.minecraft.ai.SocialGroupingSystem;
import com.blaniel.minecraft.conversation.SocialGroupManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Set;
import java.util.UUID;

/**
 * Manejador de eventos de tick del servidor
 *
 * Ejecuta lógica periódica como evaluación de grupos sociales y diálogos ambientales
 */
public class ServerTickHandler {

    private static int tickCounter = 0;
    private static final int GROUP_EVALUATION_INTERVAL = 100; // 5 segundos (20 ticks/s * 5)
    private static final int DIALOGUE_CHECK_INTERVAL = 40; // 2 segundos (20 ticks/s * 2)

    /**
     * Registrar handlers de tick
     */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;

            // Evaluar formación de grupos cada 5 segundos
            if (tickCounter >= GROUP_EVALUATION_INTERVAL) {
                tickCounter = 0;

                // Evaluar en cada mundo (overworld, nether, end)
                for (ServerWorld world : server.getWorlds()) {
                    SocialGroupingSystem.getInstance().evaluateGroupFormation(world);
                }
            }

            // Verificar si jugadores están cerca de grupos (cada 2 segundos)
            if (tickCounter % DIALOGUE_CHECK_INTERVAL == 0) {
                for (ServerWorld world : server.getWorlds()) {
                    checkPlayersNearGroups(world);
                }
            }
        });
    }

    /**
     * Verificar si hay jugadores cerca de grupos para activar diálogos
     */
    private static void checkPlayersNearGroups(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            // Sistema de conversation scripts (guiones completos)
            SocialGroupManager.checkPlayerProximity(player);

            // Sistema legacy de diálogos ambientales (frases sueltas)
            UUID nearbyGroupId = SocialGroupingSystem.getInstance().findNearbyGroup(player);

            if (nearbyGroupId != null) {
                Set<Integer> members = SocialGroupingSystem.getInstance().getGroupMembers(nearbyGroupId);

                if (members != null && members.size() >= 2) {
                    // Activar diálogo ambiental
                    AmbientDialogueHandler.triggerAmbientDialogue(
                        nearbyGroupId,
                        members,
                        player,
                        world
                    );
                }
            }
        }
    }
}
