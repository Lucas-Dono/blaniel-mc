package com.blaniel.minecraft.update;

import com.blaniel.minecraft.BlanielMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Verificador de actualizaciones del mod
 *
 * Consulta el servidor para verificar si hay nuevas versiones disponibles
 */
public class ModUpdateChecker {

	private static final String CURRENT_VERSION = "0.1.0";
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.build();

	private static final Gson GSON = new GsonBuilder().create();

	private static ModUpdateInfo cachedUpdateInfo = null;
	private static long lastCheckTime = 0;
	private static final long CHECK_COOLDOWN = 3600000; // 1 hora en milisegundos

	/**
	 * Obtener la versión actual del mod
	 */
	public static String getCurrentVersion() {
		return CURRENT_VERSION;
	}

	/**
	 * Verificar actualizaciones disponibles
	 *
	 * @param apiUrl URL base de la API
	 * @return CompletableFuture con información de actualización, o null si no hay
	 */
	public static CompletableFuture<ModUpdateInfo> checkForUpdates(String apiUrl) {
		// Verificar cooldown para no spammear el servidor
		long now = System.currentTimeMillis();
		if (cachedUpdateInfo != null && (now - lastCheckTime) < CHECK_COOLDOWN) {
			System.out.println("[Blaniel Update] Using cached update info (cooldown active)");
			return CompletableFuture.completedFuture(cachedUpdateInfo);
		}

		String url = apiUrl + "/api/v1/minecraft/mod/version?currentVersion=" + CURRENT_VERSION;

		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.GET()
			.timeout(Duration.ofSeconds(10))
			.header("User-Agent", "BlanielMC/" + CURRENT_VERSION)
			.build();

		System.out.println("[Blaniel Update] Checking for updates at: " + url);

		return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
			.thenApply(response -> {
				if (response.statusCode() != 200) {
					System.err.println("[Blaniel Update] Failed to check updates: HTTP " + response.statusCode());
					return null;
				}

				try {
					ModUpdateInfo updateInfo = GSON.fromJson(response.body(), ModUpdateInfo.class);

					// Cachear resultado
					cachedUpdateInfo = updateInfo;
					lastCheckTime = System.currentTimeMillis();

					if (updateInfo.hasUpdate()) {
						System.out.println("[Blaniel Update] Update available: " +
							CURRENT_VERSION + " -> " + updateInfo.getLatestVersion());
						System.out.println("[Blaniel Update] Download size: " +
							updateInfo.getFormattedFileSize());

						if (updateInfo.isRequired()) {
							System.out.println("[Blaniel Update] ⚠️ This is a REQUIRED update!");
						}
					} else {
						System.out.println("[Blaniel Update] Mod is up to date (v" + CURRENT_VERSION + ")");
					}

					return updateInfo;
				} catch (Exception e) {
					System.err.println("[Blaniel Update] Error parsing update info: " + e.getMessage());
					e.printStackTrace();
					return null;
				}
			})
			.exceptionally(ex -> {
				System.err.println("[Blaniel Update] Error checking for updates: " + ex.getMessage());
				return null;
			});
	}

	/**
	 * Verificar si hay actualización en caché (sin hacer request)
	 */
	public static ModUpdateInfo getCachedUpdateInfo() {
		return cachedUpdateInfo;
	}

	/**
	 * Limpiar caché (forzar nuevo chequeo en próxima llamada)
	 */
	public static void clearCache() {
		cachedUpdateInfo = null;
		lastCheckTime = 0;
	}

	/**
	 * Verificar si la actualización es obligatoria
	 */
	public static boolean isUpdateRequired() {
		return cachedUpdateInfo != null && cachedUpdateInfo.isRequired();
	}
}
