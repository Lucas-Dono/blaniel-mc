package com.blaniel.minecraft.server;

import com.blaniel.minecraft.config.BlanielConfig;
import com.blaniel.minecraft.conversation.ConversationScriptPlayer;
import com.blaniel.minecraft.conversation.ScriptCacheManager;
import com.blaniel.minecraft.conversation.SocialGroupManager;
import com.blaniel.minecraft.network.BlanielAPIClient;
import com.blaniel.minecraft.update.ModUpdateChecker;
import com.blaniel.minecraft.update.ModUpdateInfo;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

/**
 * Manejador de eventos del ciclo de vida del servidor
 *
 * Se encarga de inicializar y limpiar recursos al iniciar y detener el servidor.
 */
public class ServerLifecycleHandler {

	private static BlanielAPIClient apiClient = null;

	/**
	 * Registrar handlers de lifecycle
	 */
	public static void register() {
		// Al iniciar el servidor
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			onServerStarting(server);
		});

		// Al detener el servidor
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			onServerStopping(server);
		});
	}

	/**
	 * Ejecutar al iniciar el servidor
	 */
	private static void onServerStarting(MinecraftServer server) {
		System.out.println("[Blaniel] Server starting - Initializing conversation system...");

		// Inicializar API client si el usuario está logueado
		BlanielConfig config = com.blaniel.minecraft.BlanielMod.CONFIG;

		if (config.isLoggedIn()) {
			String token = config.getJwtToken();
			String apiUrl = config.getApiUrl();

			if (token != null && !token.isEmpty()) {
				apiClient = new BlanielAPIClient(apiUrl, token);
				SocialGroupManager.setAPIClient(apiClient);

				System.out.println("[Blaniel] API client configured for user: " +
					config.getUserData().email);

				// Verificar actualizaciones de scripts en segundo plano
				checkScriptUpdates();

				// Verificar actualizaciones del mod
				checkModUpdates(apiUrl, server);
			} else {
				System.err.println("[Blaniel] User logged in but no JWT token found");
			}
		} else {
			System.out.println("[Blaniel] User not logged in - Conversation scripts disabled");
		}
	}

	/**
	 * Verificar actualizaciones de scripts en segundo plano
	 */
	private static void checkScriptUpdates() {
		if (apiClient == null) {
			return;
		}

		System.out.println("[Blaniel] Checking script updates in background...");

		ScriptCacheManager.checkAllUpdates(apiClient)
			.thenRun(() -> {
				System.out.println("[Blaniel] Script update check completed");
				System.out.println("[Blaniel] Cache stats: " +
					ScriptCacheManager.getStats());
			})
			.exceptionally(e -> {
				System.err.println("[Blaniel] Error checking script updates: " + e.getMessage());
				e.printStackTrace();
				return null;
			});
	}

	/**
	 * Verificar actualizaciones del mod
	 */
	private static void checkModUpdates(String apiUrl, MinecraftServer server) {
		System.out.println("[Blaniel] Checking mod updates in background...");

		ModUpdateChecker.checkForUpdates(apiUrl)
			.thenAccept(updateInfo -> {
				if (updateInfo == null) {
					System.out.println("[Blaniel] Unable to check for mod updates");
					return;
				}

				if (!updateInfo.hasUpdate()) {
					System.out.println("[Blaniel] Mod is up to date");
					return;
				}

				// Hay actualización disponible
				System.out.println("[Blaniel] ========================================");
				System.out.println("[Blaniel] 🚀 NEW MOD UPDATE AVAILABLE!");
				System.out.println("[Blaniel] Current: v" + ModUpdateChecker.getCurrentVersion());
				System.out.println("[Blaniel] Latest:  v" + updateInfo.getLatestVersion());
				System.out.println("[Blaniel] Size:    " + updateInfo.getFormattedFileSize());

				if (updateInfo.isRequired()) {
					System.out.println("[Blaniel] ⚠️  REQUIRED UPDATE!");
				}

				System.out.println("[Blaniel] ========================================");

				// Mostrar pantalla de actualización en el cliente
				// Nota: Esto se mostrará cuando el jugador entre al mundo
				showUpdateScreenOnClient(updateInfo);
			})
			.exceptionally(e -> {
				System.err.println("[Blaniel] Error checking mod updates: " + e.getMessage());
				return null;
			});
	}

	/**
	 * Mostrar pantalla de actualización en el cliente
	 *
	 * Como estamos en el servidor, necesitamos esperar a que el jugador
	 * entre al mundo para mostrar la pantalla
	 */
	private static void showUpdateScreenOnClient(ModUpdateInfo updateInfo) {
		// Guardar info de actualización para mostrarla cuando el jugador entre
		com.blaniel.minecraft.client.BlanielModClient.setUpdateInfo(updateInfo);
	}

	/**
	 * Ejecutar al detener el servidor
	 */
	private static void onServerStopping(MinecraftServer server) {
		System.out.println("[Blaniel] Server stopping - Cleaning up...");

		// Detener todas las conversaciones
		SocialGroupManager.stopAll();

		// Shutdown del scheduler de conversation players
		ConversationScriptPlayer.shutdown();

		System.out.println("[Blaniel] Cleanup completed");
	}
}
