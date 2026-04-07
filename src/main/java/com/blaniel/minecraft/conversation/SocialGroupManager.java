package com.blaniel.minecraft.conversation;

import com.blaniel.minecraft.conversation.models.ConversationScript;
import com.blaniel.minecraft.entity.BlanielVillagerEntity;
import com.blaniel.minecraft.network.BlanielAPIClient;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Gestor de grupos sociales y conversaciones
 *
 * Detecta grupos de NPCs cercanos entre sí, inicia conversaciones automáticamente
 * y gestiona el ciclo de vida de los ConversationScriptPlayers.
 */
public class SocialGroupManager {

	private static final Map<String, ConversationScriptPlayer> activePlayers = new ConcurrentHashMap<>();
	private static BlanielAPIClient apiClient = null;

	// Radio de detección de grupos sociales (bloques)
	private static final double GROUP_DETECTION_RADIUS = 10.0;

	// Máximo de NPCs por grupo
	private static final int MAX_GROUP_SIZE = 4;

	/**
	 * Configurar cliente API (debe llamarse al iniciar)
	 */
	public static void setAPIClient(BlanielAPIClient client) {
		apiClient = client;
	}

	/**
	 * Iniciar conversación para un grupo
	 */
	public static void startConversation(
		String groupHash,
		List<String> agentIds,
		String location,
		String contextHint
	) {
		if (apiClient == null) {
			System.err.println("[Social Groups] API client not configured");
			return;
		}

		// Verificar si ya hay player activo
		if (activePlayers.containsKey(groupHash)) {
			return; // Ya está en progreso
		}

		System.out.println("[Social Groups] Starting conversation for: " + groupHash);

		// Intentar cargar de caché primero
		ConversationScript cached = ScriptCacheManager.getCachedScript(groupHash);

		if (cached != null) {
			// Verificar si necesita actualización (async)
			ScriptCacheManager.needsUpdate(apiClient, groupHash)
				.thenAccept(needsUpdate -> {
					if (needsUpdate) {
						// Descargar nueva versión
						downloadAndStartConversation(groupHash, agentIds, location, contextHint);
					} else {
						// Usar caché
						startPlayer(groupHash, cached);
					}
				});
		} else {
			// No está en caché, descargar
			downloadAndStartConversation(groupHash, agentIds, location, contextHint);
		}
	}

	/**
	 * Descargar script y empezar conversación
	 */
	private static void downloadAndStartConversation(
		String groupHash,
		List<String> agentIds,
		String location,
		String contextHint
	) {
		apiClient.getConversationScript(agentIds, location, contextHint, groupHash, false)
			.thenAccept(script -> {
				if (script == null) {
					System.err.println("[Social Groups] Failed to get script for: " + groupHash);
					return;
				}

				// Cachear el script
				ScriptCacheManager.cacheScript(groupHash, script);

				// Iniciar player
				startPlayer(groupHash, script);
			})
			.exceptionally(e -> {
				System.err.println("[Social Groups] Error getting script: " + e.getMessage());
				return null;
			});
	}

	/**
	 * Iniciar player con el script
	 */
	private static void startPlayer(String groupHash, ConversationScript script) {
		ConversationScriptPlayer player = new ConversationScriptPlayer(groupHash, script);
		activePlayers.put(groupHash, player);
		player.start();

		System.out.println("[Social Groups] Conversation started: " + groupHash +
			" - " + script.getTopic() + " (v" + script.getVersion() + ")");
	}

	/**
	 * Detener conversación de un grupo
	 */
	public static void stopConversation(String groupHash) {
		ConversationScriptPlayer player = activePlayers.remove(groupHash);
		if (player != null) {
			player.stop();
			System.out.println("[Social Groups] Conversation stopped: " + groupHash);
		}
	}

	/**
	 * Verificar proximidad de jugador a grupos
	 * Llamar cada segundo (20 ticks) desde un tick handler
	 */
	public static void checkPlayerProximity(ServerPlayer player) {
		if (apiClient == null) {
			return;
		}

		ServerLevel level = player.serverLevel();

		// Buscar todos los BlanielVillagers cerca del jugador
		BlockPos playerPos = player.blockPosition();
		AABB searchArea = new AABB(playerPos).inflate(GROUP_DETECTION_RADIUS * 2);

		List<BlanielVillagerEntity> nearbyVillagers = level.getEntitiesOfClass(
			BlanielVillagerEntity.class,
			searchArea,
			villager -> villager.distanceTo(player) <= GROUP_DETECTION_RADIUS * 2
		);

		if (nearbyVillagers.isEmpty()) {
			return;
		}

		// Detectar grupos sociales (NPCs cerca entre sí)
		List<SocialGroup> groups = detectSocialGroups(nearbyVillagers);

		// Iniciar conversaciones para grupos sin conversación activa
		for (SocialGroup group : groups) {
			String groupHash = group.getHash();

			if (!activePlayers.containsKey(groupHash)) {
				startConversation(
					groupHash,
					group.getAgentIds(),
					level.dimension().location().toString(),
					"cerca de " + playerPos.toShortString()
				);
			}
		}
	}

	/**
	 * Detectar grupos sociales (NPCs cerca entre sí)
	 */
	private static List<SocialGroup> detectSocialGroups(List<BlanielVillagerEntity> villagers) {
		List<SocialGroup> groups = new ArrayList<>();
		Set<BlanielVillagerEntity> processed = new HashSet<>();

		for (BlanielVillagerEntity villager : villagers) {
			if (processed.contains(villager)) {
				continue;
			}

			// Encontrar todos los NPCs cerca de este
			List<BlanielVillagerEntity> groupMembers = new ArrayList<>();
			groupMembers.add(villager);
			processed.add(villager);

			for (BlanielVillagerEntity other : villagers) {
				if (processed.contains(other)) {
					continue;
				}

				if (villager.distanceTo(other) <= GROUP_DETECTION_RADIUS) {
					groupMembers.add(other);
					processed.add(other);

					if (groupMembers.size() >= MAX_GROUP_SIZE) {
						break;
					}
				}
			}

			// Solo crear grupo si hay al menos 2 NPCs
			if (groupMembers.size() >= 2) {
				groups.add(new SocialGroup(groupMembers));
			}
		}

		return groups;
	}

	/**
	 * Obtener cantidad de conversaciones activas
	 */
	public static int getActiveConversationCount() {
		return activePlayers.size();
	}

	/**
	 * Detener todas las conversaciones (al cerrar el juego)
	 */
	public static void stopAll() {
		for (String groupHash : new ArrayList<>(activePlayers.keySet())) {
			stopConversation(groupHash);
		}
		System.out.println("[Social Groups] All conversations stopped");
	}

	/**
	 * Clase auxiliar para representar un grupo social
	 */
	private static class SocialGroup {
		private final List<BlanielVillagerEntity> members;

		public SocialGroup(List<BlanielVillagerEntity> members) {
			this.members = members;
		}

		public String getHash() {
			// Crear hash único ordenado por agentId
			List<String> ids = members.stream()
				.map(BlanielVillagerEntity::getBlanielAgentId)
				.filter(Objects::nonNull)
				.sorted()
				.collect(Collectors.toList());

			return String.join("_", ids);
		}

		public List<String> getAgentIds() {
			return members.stream()
				.map(BlanielVillagerEntity::getBlanielAgentId)
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
		}
	}
}
