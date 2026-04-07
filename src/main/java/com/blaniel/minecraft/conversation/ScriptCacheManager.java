package com.blaniel.minecraft.conversation;

import com.blaniel.minecraft.conversation.models.ConversationScript;
import com.blaniel.minecraft.conversation.models.ScriptMetadata;
import com.blaniel.minecraft.network.BlanielAPIClient;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor de caché de scripts conversacionales con versionado
 *
 * Almacena scripts en disco y memoria para acceso rápido.
 * Verifica versiones contra el servidor para detectar actualizaciones.
 */
public class ScriptCacheManager {

	private static final Gson GSON = new Gson();
	private static final File CACHE_DIR = new File("blaniel_cache/scripts");
	private static final Map<String, ConversationScript> memoryCache = new ConcurrentHashMap<>();

	static {
		// Crear directorio de caché si no existe
		if (!CACHE_DIR.exists()) {
			CACHE_DIR.mkdirs();
			System.out.println("[Script Cache] Created cache directory: " + CACHE_DIR.getAbsolutePath());
		}
	}

	/**
	 * Guardar script en caché (disco + memoria)
	 */
	public static void cacheScript(String groupHash, ConversationScript script) {
		if (script == null || groupHash == null) {
			return;
		}

		// Guardar en memoria
		memoryCache.put(groupHash, script);

		// Guardar en disco
		File cacheFile = new File(CACHE_DIR, groupHash + ".json");
		try (FileWriter writer = new FileWriter(cacheFile)) {
			GSON.toJson(script, writer);
			System.out.println("[Script Cache] Cached: " + groupHash +
				" (v" + script.getVersion() + ") - " + script.getTopic());
		} catch (Exception e) {
			System.err.println("[Script Cache] Error saving to disk: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Cargar script de caché
	 */
	public static ConversationScript getCachedScript(String groupHash) {
		if (groupHash == null) {
			return null;
		}

		// Verificar memoria primero (más rápido)
		if (memoryCache.containsKey(groupHash)) {
			return memoryCache.get(groupHash);
		}

		// Cargar de disco
		File cacheFile = new File(CACHE_DIR, groupHash + ".json");
		if (!cacheFile.exists()) {
			return null;
		}

		try (FileReader reader = new FileReader(cacheFile)) {
			ConversationScript script = GSON.fromJson(reader, ConversationScript.class);

			// Cachear en memoria para próxima vez
			memoryCache.put(groupHash, script);

			System.out.println("[Script Cache] Loaded from disk: " + groupHash +
				" (v" + script.getVersion() + ")");
			return script;
		} catch (Exception e) {
			System.err.println("[Script Cache] Error loading from disk: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Verificar si necesita actualización
	 *
	 * @param apiClient Cliente API para consultar servidor
	 * @param groupHash Hash del grupo
	 * @return CompletableFuture que resuelve a true si necesita actualización
	 */
	public static CompletableFuture<Boolean> needsUpdate(BlanielAPIClient apiClient, String groupHash) {
		ConversationScript cached = getCachedScript(groupHash);

		if (cached == null) {
			// No existe en caché, necesita descarga
			return CompletableFuture.completedFuture(true);
		}

		// Consultar metadata del servidor
		return apiClient.getScriptMetadata(groupHash)
			.thenApply(serverMetadata -> {
				if (serverMetadata == null) {
					// No hay script en servidor, usar caché
					return false;
				}

				// Comparar versiones
				boolean needsUpdate = serverMetadata.getVersion() > cached.getVersion();

				if (needsUpdate) {
					System.out.println("[Script Cache] Update available for " + groupHash +
						": v" + cached.getVersion() + " -> v" + serverMetadata.getVersion());
				}

				return needsUpdate;
			})
			.exceptionally(e -> {
				System.err.println("[Script Cache] Error checking update: " + e.getMessage());
				// En caso de error, usar caché si existe
				return false;
			});
	}

	/**
	 * Verificar todas las actualizaciones al iniciar el juego
	 *
	 * @param apiClient Cliente API para consultar servidor
	 * @return CompletableFuture que completa cuando termina la verificación
	 */
	public static CompletableFuture<Void> checkAllUpdates(BlanielAPIClient apiClient) {
		System.out.println("[Script Cache] Checking script updates...");

		// Cargar todos los scripts del disco a memoria
		File[] cacheFiles = CACHE_DIR.listFiles((dir, name) -> name.endsWith(".json"));

		if (cacheFiles == null || cacheFiles.length == 0) {
			System.out.println("[Script Cache] No cached scripts found");
			return CompletableFuture.completedFuture(null);
		}

		System.out.println("[Script Cache] Found " + cacheFiles.length + " cached scripts");

		// Verificar cada script en paralelo
		CompletableFuture<?>[] updateTasks = new CompletableFuture[cacheFiles.length];

		for (int i = 0; i < cacheFiles.length; i++) {
			File file = cacheFiles[i];
			String groupHash = file.getName().replace(".json", "");

			// Verificar y actualizar si es necesario
			updateTasks[i] = needsUpdate(apiClient, groupHash)
				.thenCompose(needsUpdate -> {
					if (!needsUpdate) {
						return CompletableFuture.completedFuture(null);
					}

					// Descargar versión actualizada
					ConversationScript cached = getCachedScript(groupHash);
					if (cached == null) {
						return CompletableFuture.completedFuture(null);
					}

					// Reusar participantes del script cacheado
					return apiClient.getConversationScript(
						null, // No necesitamos agentIds, el servidor usa groupHash
						cached.getLocation(),
						cached.getContextHint(),
						groupHash,
						true // forceNew = true para obtener versión actualizada
					).thenAccept(updated -> {
						if (updated != null) {
							cacheScript(groupHash, updated);
							System.out.println("[Script Cache] Updated: " + groupHash +
								" (v" + updated.getVersion() + ")");
						}
					});
				})
				.exceptionally(e -> {
					System.err.println("[Script Cache] Error updating " + groupHash + ": " + e.getMessage());
					return null;
				});
		}

		// Esperar a que todas las verificaciones completen
		return CompletableFuture.allOf(updateTasks)
			.thenRun(() -> {
				System.out.println("[Script Cache] Update check completed");
			});
	}

	/**
	 * Limpiar caché de memoria (útil para testing o recarga)
	 */
	public static void clearMemoryCache() {
		memoryCache.clear();
		System.out.println("[Script Cache] Memory cache cleared");
	}

	/**
	 * Obtener estadísticas de caché
	 */
	public static String getStats() {
		File[] cacheFiles = CACHE_DIR.listFiles((dir, name) -> name.endsWith(".json"));
		int diskCount = (cacheFiles != null) ? cacheFiles.length : 0;

		return String.format("Scripts in memory: %d, on disk: %d", memoryCache.size(), diskCount);
	}
}
