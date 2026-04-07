package com.blaniel.minecraft.client;

import com.blaniel.minecraft.BlanielMod;
import com.blaniel.minecraft.client.network.ClientNetworkHandler;
import com.blaniel.minecraft.client.renderer.BlanielVillagerRenderer;
import com.blaniel.minecraft.config.BlanielConfig;
import com.blaniel.minecraft.oauth.OAuth2Client;
import com.blaniel.minecraft.skin.BlanielSkinManager;
import com.blaniel.minecraft.screen.LoginScreen;
import com.blaniel.minecraft.screen.UpdateAvailableScreen;
import com.blaniel.minecraft.update.ModUpdateInfo;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Inicializador del mod en el lado del cliente
 *
 * Registra renderers, GUI handlers, y otras funcionalidades exclusivas del cliente.
 */
public class BlanielModClient implements ClientModInitializer {

	// Flag para evitar múltiples intentos de auto-login
	private static boolean autoLoginAttempted = false;
	private static boolean autoLoginInProgress = false;

	// Información de actualización pendiente
	private static ModUpdateInfo pendingUpdateInfo = null;
	private static boolean updateScreenShown = false;

	@Override
	public void onInitializeClient() {
		BlanielMod.LOGGER.info("Inicializando Blaniel Client");

		// Inicializar skin manager
		BlanielSkinManager.initialize();

		// Inicializar chat handler
		BlanielChatHandler.initialize();
		BlanielMod.LOGGER.info("Chat handler inicializado");

		// Registrar renderer para BlanielVillager
		EntityRendererRegistry.register(BlanielMod.BLANIEL_VILLAGER, BlanielVillagerRenderer::new);

		// Registrar keybindings
		BlanielKeyBindings.register();
		BlanielMod.LOGGER.info("Keybindings registrados");

		// Registrar handlers de input
		KeyInputHandler.register();
		BlanielMod.LOGGER.info("Input handlers registrados");

		// Registrar client-side network handlers
		ClientNetworkHandler.register();

		// Auto-login con refresh token o mostrar login screen
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// Solo ejecutar si el jugador está en un mundo
			if (client.world == null || client.player == null) {
				return;
			}

			// Verificar si hay actualización pendiente para mostrar
			if (pendingUpdateInfo != null && !updateScreenShown) {
				// Solo mostrar si no hay otra pantalla abierta (excepto login)
				if (client.currentScreen == null || client.currentScreen instanceof LoginScreen) {
					updateScreenShown = true;
					client.setScreen(new UpdateAvailableScreen(null, pendingUpdateInfo));

					// Notificar en chat
					if (client.player != null) {
						String prefix = pendingUpdateInfo.isRequired() ? "§c⚠️ [Blaniel]" : "§a🚀 [Blaniel]";
						client.player.sendMessage(
							Text.literal(prefix + " §fNueva actualización disponible: v" +
								pendingUpdateInfo.getLatestVersion()),
							false
						);
					}
				}
				return; // Dar prioridad a la pantalla de actualización
			}

			// Si ya está logueado, no hacer nada
			if (BlanielMod.CONFIG.isLoggedIn()) {
				return;
			}

			// Si ya intentamos auto-login, no volver a intentar
			if (autoLoginAttempted) {
				// Mostrar login screen si no está ya mostrado
				if (client.currentScreen == null && !autoLoginInProgress) {
					BlanielMod.LOGGER.info("Auto-login falló previamente, mostrando LoginScreen");
					client.setScreen(new LoginScreen(null));
					autoLoginAttempted = true; // Reset para no mostrar múltiples veces
				}
				return;
			}

			// Si hay refresh token guardado, intentar renovar sesión automáticamente
			if (BlanielMod.CONFIG.hasRefreshToken()) {
				autoLoginAttempted = true;
				autoLoginInProgress = true;

				BlanielMod.LOGGER.info("Refresh token encontrado, renovando sesión automáticamente...");

				String clientId = BlanielMod.CONFIG.getGoogleClientId();
				String refreshToken = BlanielMod.CONFIG.getGoogleRefreshToken();
				String apiUrl = BlanielMod.CONFIG.getApiUrl();

				// Intentar refrescar tokens
				OAuth2Client.refreshTokens(clientId, refreshToken, apiUrl).thenAccept(response -> {
					client.execute(() -> {
						autoLoginInProgress = false;

						if (response.success && response.token != null) {
							// Renovación exitosa
							BlanielConfig.UserData userData = new BlanielConfig.UserData(
								response.user.id,
								response.user.email,
								response.user.name,
								response.user.plan
							);

							// Actualizar JWT y refresh token
							BlanielMod.CONFIG.loginWithOAuth(
								response.token,
								userData,
								response.googleRefreshToken != null ? response.googleRefreshToken : refreshToken
							);

							BlanielMod.LOGGER.info("Sesión renovada automáticamente para: " + userData.name);

							if (client.player != null) {
								client.player.sendMessage(
									Text.literal("§a[Blaniel] §fSesión renovada automáticamente. ¡Bienvenido de vuelta " + userData.name + "!"),
									false
								);
							}
						} else {
							// Renovación falló, mostrar login screen
							BlanielMod.LOGGER.warn("Error al renovar sesión: " + response.error);
							BlanielMod.LOGGER.info("Mostrando LoginScreen para nueva autenticación");

							if (client.currentScreen == null) {
								client.setScreen(new LoginScreen(null));
							}
						}
					});
				}).exceptionally(ex -> {
					client.execute(() -> {
						autoLoginInProgress = false;
						BlanielMod.LOGGER.error("Error al renovar sesión: " + ex.getMessage());

						if (client.currentScreen == null) {
							client.setScreen(new LoginScreen(null));
						}
					});
					return null;
				});
			} else {
				// No hay refresh token, mostrar login screen
				autoLoginAttempted = true;

				if (client.currentScreen == null) {
					BlanielMod.LOGGER.info("Usuario no logueado y sin refresh token, mostrando LoginScreen");
					client.setScreen(new LoginScreen(null));
				}
			}
		});

		BlanielMod.LOGGER.info("Blaniel Client inicializado exitosamente");
	}

	/**
	 * Configurar información de actualización pendiente
	 *
	 * Este método es llamado desde el servidor cuando se detecta una actualización
	 */
	public static void setUpdateInfo(ModUpdateInfo updateInfo) {
		pendingUpdateInfo = updateInfo;
		updateScreenShown = false;
		BlanielMod.LOGGER.info("Update info set: v" + updateInfo.getLatestVersion() +
			" (required: " + updateInfo.isRequired() + ")");
	}
}
