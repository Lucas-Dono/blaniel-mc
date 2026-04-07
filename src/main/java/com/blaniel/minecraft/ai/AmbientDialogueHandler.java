package com.blaniel.minecraft.ai;

import com.blaniel.minecraft.BlanielMod;
import com.blaniel.minecraft.entity.BlanielVillagerEntity;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ambient Dialogue Handler
 *
 * Activates dialogues between NPCs when a player approaches a group
 */
public class AmbientDialogueHandler {

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    // Cooldown to prevent dialogue spam
    private static final Map<UUID, Long> groupDialogueCooldown = new ConcurrentHashMap<>();
    private static final long DIALOGUE_COOLDOWN_MS = 30000; // 30 seconds

    /**
     * Trigger ambient dialogue for a group
     *
     * @param groupId ID of the group
     * @param members Members of the group
     * @param player Player who triggered the dialogue
     */
    public static void triggerAmbientDialogue(
        UUID groupId,
        Set<Integer> memberIds,
        PlayerEntity player,
        net.minecraft.server.world.ServerWorld world
    ) {
        // Check cooldown
        Long lastDialogue = groupDialogueCooldown.get(groupId);
        if (lastDialogue != null && System.currentTimeMillis() - lastDialogue < DIALOGUE_COOLDOWN_MS) {
            return; // Cooldown active
        }

        // Get group entities
        List<BlanielVillagerEntity> members = new ArrayList<>();
        for (Integer memberId : memberIds) {
            var entity = world.getEntityById(memberId);
            if (entity instanceof BlanielVillagerEntity) {
                members.add((BlanielVillagerEntity) entity);
            }
        }

        if (members.size() < 2) {
            return; // Not enough NPCs
        }

        // Check if there is important history (simplified: if any NPC has lastInteractedPlayerId)
        boolean hasImportantHistory = members.stream()
            .anyMatch(npc -> npc.getLastInteractedPlayerId() != null);

        BlanielMod.LOGGER.info("Triggering ambient dialogue for group {} ({} NPCs, history: {})",
            groupId, members.size(), hasImportantHistory);

        // Request dialogue from backend (asynchronous)
        requestDialogueFromBackend(groupId, members, player, hasImportantHistory);

        // Update cooldown
        groupDialogueCooldown.put(groupId, System.currentTimeMillis());
    }

    /**
     * Request dialogue from backend
     */
    private static void requestDialogueFromBackend(
        UUID groupId,
        List<BlanielVillagerEntity> members,
        PlayerEntity player,
        boolean hasImportantHistory
    ) {
        try {
            // Build request body
            JsonObject body = new JsonObject();

            JsonArray agentIds = new JsonArray();
            for (BlanielVillagerEntity npc : members) {
                agentIds.add(npc.getBlanielAgentId());
            }
            body.add("agentIds", agentIds);

            body.addProperty("location", player.getWorld().getRegistryKey().getValue().toString());
            body.addProperty("hasImportantHistory", hasImportantHistory);

            String apiUrl = BlanielMod.CONFIG.getApiUrl() + "/api/v1/minecraft/ambient-dialogue";
            String jwtToken = BlanielMod.CONFIG.getJwtToken();

            BlanielMod.LOGGER.info("[Ambient Dialogue] Requesting dialogue from: {}", apiUrl);

            // Build request
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + jwtToken)
                .header("User-Agent", "BlanielMinecraft/0.1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .timeout(Duration.ofSeconds(30))
                .build();

            // Execute request (async)
            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        processDialogueResponse(response.body(), members, player);
                    } else {
                        BlanielMod.LOGGER.error("[Ambient Dialogue] Error: HTTP {}", response.statusCode());
                    }
                })
                .exceptionally(ex -> {
                    BlanielMod.LOGGER.error("[Ambient Dialogue] Connection error: {}", ex.getMessage());
                    return null;
                });

        } catch (Exception e) {
            BlanielMod.LOGGER.error("[Ambient Dialogue] Error requesting dialogue: {}", e.getMessage());
        }
    }

    /**
     * Process response with dialogues
     */
    private static void processDialogueResponse(
        String responseBody,
        List<BlanielVillagerEntity> members,
        PlayerEntity player
    ) {
        try {
            JsonObject response = GSON.fromJson(responseBody, JsonObject.class);
            JsonArray dialogues = response.getAsJsonArray("dialogues");

            if (dialogues == null || dialogues.size() == 0) {
                BlanielMod.LOGGER.warn("[Ambient Dialogue] No dialogues in response");
                return;
            }

            // Play dialogues with delay between them
            for (int i = 0; i < dialogues.size(); i++) {
                JsonObject dialogue = dialogues.get(i).getAsJsonObject();
                String agentId = dialogue.get("agentId").getAsString();
                String agentName = dialogue.get("agentName").getAsString();
                String message = dialogue.get("message").getAsString();

                // Find the corresponding entity
                BlanielVillagerEntity speaker = members.stream()
                    .filter(npc -> npc.getBlanielAgentId().equals(agentId))
                    .findFirst()
                    .orElse(null);

                if (speaker != null) {
                    // Progressive delay (0s, 3s, 6s, ...)
                    int delay = i * 60; // 3 seconds in ticks (20 ticks/s * 3)

                    // Schedule dialogue display
                    scheduleDialogueDisplay(speaker, message, delay, player);
                }
            }

            BlanielMod.LOGGER.info("[Ambient Dialogue] {} dialogues scheduled", dialogues.size());

        } catch (Exception e) {
            BlanielMod.LOGGER.error("[Ambient Dialogue] Error processing response: {}", e.getMessage());
        }
    }

    /**
     * Schedule dialogue display with delay
     */
    private static void scheduleDialogueDisplay(
        BlanielVillagerEntity speaker,
        String message,
        int delayTicks,
        PlayerEntity player
    ) {
        // Use the server scheduler
        speaker.getWorld().getServer().execute(() -> {
            try {
                Thread.sleep(delayTicks * 50); // Convert ticks to ms

                // Display chat bubble above the NPC
                speaker.displayChatBubble(message);

                // Send to player's chat
                if (player instanceof ServerPlayerEntity) {
                    player.sendMessage(
                        Text.literal("§7[" + speaker.getBlanielAgentName() + "]: §f" + message),
                        false
                    );
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
