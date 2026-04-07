package com.blaniel.minecraft.skin;

import com.blaniel.minecraft.BlanielMod;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor de skins personalizadas de Blaniel
 *
 * Funcionalidades:
 * - Descarga skins desde API de Blaniel
 * - Caché local en disco (.minecraft/blaniel-skins/)
 * - Aplicación de skins a GameProfile
 * - Registro de texturas en TextureManager
 */
public class BlanielSkinManager {

	// Cliente HTTP compartido
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
		.version(HttpClient.Version.HTTP_1_1)
		.connectTimeout(Duration.ofSeconds(10))
		.build();

	// Directorio de caché de skins
	private static final Path SKIN_CACHE_DIR = FabricLoader.getInstance()
		.getGameDir()
		.resolve("blaniel-skins");

	// Caché en memoria de GameProfiles ya procesados
	private static final Map<String, GameProfile> PROFILE_CACHE = new ConcurrentHashMap<>();

	// Caché de texturas registradas
	private static final Map<String, Identifier> TEXTURE_CACHE = new ConcurrentHashMap<>();

	/**
	 * Inicializar manager (crear directorios)
	 */
	public static void initialize() {
		try {
			Files.createDirectories(SKIN_CACHE_DIR);
			BlanielMod.LOGGER.info("Blaniel Skin Manager inicializado: {}", SKIN_CACHE_DIR);
		} catch (IOException e) {
			BlanielMod.LOGGER.error("Error creando directorio de caché de skins", e);
		}
	}

	/**
	 * Obtener skin de agente (async)
	 *
	 * Flujo:
	 * 1. Verificar caché en memoria
	 * 2. Verificar caché en disco
	 * 3. Descargar desde API
	 * 4. Cachear en disco
	 * 5. Aplicar al GameProfile
	 *
	 * @param agentId ID del agente
	 * @param agentName Nombre del agente (para GameProfile)
	 * @param apiUrl URL base de la API
	 * @param jwtToken Token de autenticación
	 * @return CompletableFuture con GameProfile que tiene la skin aplicada
	 */
	public static CompletableFuture<GameProfile> loadSkin(
		String agentId,
		String agentName,
		String apiUrl,
		String jwtToken
	) {
		// 1. Verificar caché en memoria
		if (PROFILE_CACHE.containsKey(agentId)) {
			BlanielMod.LOGGER.debug("Skin de {} encontrada en caché RAM", agentId);
			return CompletableFuture.completedFuture(PROFILE_CACHE.get(agentId));
		}

		// Path del archivo en caché
		Path cachedSkinPath = SKIN_CACHE_DIR.resolve(agentId + ".png");

		// 2. Verificar caché en disco
		if (Files.exists(cachedSkinPath)) {
			BlanielMod.LOGGER.debug("Skin de {} encontrada en caché disco", agentId);
			return loadFromCache(agentId, agentName, cachedSkinPath);
		}

		// 3. Descargar desde API
		BlanielMod.LOGGER.info("Descargando skin para agente: {}", agentId);
		String skinUrl = apiUrl + "/api/v1/minecraft/agents/" + agentId + "/skin";

		return downloadSkin(skinUrl, jwtToken)
			.thenCompose(skinData -> {
				if (skinData == null) {
					BlanielMod.LOGGER.warn("No se pudo descargar skin para {}, usando default", agentId);
					return CompletableFuture.completedFuture(createDefaultProfile(agentId, agentName));
				}

				// 4. Guardar en caché
				try {
					Files.write(cachedSkinPath, skinData);
					BlanielMod.LOGGER.debug("Skin de {} guardada en caché", agentId);
				} catch (IOException e) {
					BlanielMod.LOGGER.warn("Error guardando skin en caché: {}", e.getMessage());
				}

				// 5. Aplicar skin
				return applySkin(agentId, agentName, skinData);
			});
	}

	/**
	 * Descargar skin desde API
	 */
	private static CompletableFuture<byte[]> downloadSkin(String skinUrl, String jwtToken) {
		try {
			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(skinUrl))
				.header("Authorization", "Bearer " + jwtToken)
				.header("Accept", "image/png")
				.header("User-Agent", "BlanielMinecraft/0.1.0")
				.GET()
				.timeout(Duration.ofSeconds(15))
				.build();

			return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
				.thenApply(response -> {
					if (response.statusCode() == 200) {
						BlanielMod.LOGGER.info("Skin descargada exitosamente ({} bytes)", response.body().length);
						return response.body();
					} else {
						BlanielMod.LOGGER.warn("HTTP {}: No se pudo descargar skin", response.statusCode());
						return null;
					}
				})
				.exceptionally(e -> {
					BlanielMod.LOGGER.error("Error descargando skin: {}", e.getMessage());
					return null;
				});

		} catch (Exception e) {
			BlanielMod.LOGGER.error("Error creando request de skin", e);
			return CompletableFuture.completedFuture(null);
		}
	}

	/**
	 * Cargar skin desde caché en disco
	 */
	private static CompletableFuture<GameProfile> loadFromCache(
		String agentId,
		String agentName,
		Path cachedSkinPath
	) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				byte[] skinData = Files.readAllBytes(cachedSkinPath);
				return applySkinSync(agentId, agentName, skinData);
			} catch (IOException e) {
				BlanielMod.LOGGER.error("Error leyendo skin desde caché: {}", e.getMessage());
				return createDefaultProfile(agentId, agentName);
			}
		});
	}

	/**
	 * Aplicar skin a GameProfile (async)
	 */
	private static CompletableFuture<GameProfile> applySkin(
		String agentId,
		String agentName,
		byte[] skinData
	) {
		return CompletableFuture.supplyAsync(() -> applySkinSync(agentId, agentName, skinData));
	}

	/**
	 * Aplicar skin a GameProfile (sync)
	 */
	private static GameProfile applySkinSync(
		String agentId,
		String agentName,
		byte[] skinData
	) {
		try {
			// Crear GameProfile con UUID único basado en agentId
			UUID uuid = UUID.nameUUIDFromBytes(("Blaniel:" + agentId).getBytes());
			GameProfile profile = new GameProfile(uuid, agentName);

			// Registrar textura en TextureManager (solo en cliente)
			if (MinecraftClient.getInstance() != null) {
				// Registrar textura y esperar a que termine
				try {
					Identifier skinIdentifier = registerTextureSync(agentId, skinData);

					// Convertir skin a formato base64 para GameProfile
					String base64Skin = Base64.getEncoder().encodeToString(skinData);

					// Crear property de textura
					// Formato esperado por Minecraft: { "textures": { "SKIN": { "url": "..." } } }
					String textureJson = String.format(
						"{\"timestamp\":%d,\"profileId\":\"%s\",\"profileName\":\"%s\",\"textures\":{\"SKIN\":{\"url\":\"blaniel:%s\"}}}",
						System.currentTimeMillis(),
						uuid.toString(),
						agentName,
						skinIdentifier.getPath()
					);

					String encodedTextures = Base64.getEncoder().encodeToString(textureJson.getBytes());
					profile.getProperties().put("textures", new Property("textures", encodedTextures));

					BlanielMod.LOGGER.info("Skin aplicada a GameProfile: {} ({})", agentName, agentId);
				} catch (Exception e) {
					BlanielMod.LOGGER.error("Error registrando textura para {}: {}", agentName, e.getMessage());
				}
			}

			// Guardar en caché RAM
			PROFILE_CACHE.put(agentId, profile);

			return profile;

		} catch (Exception e) {
			BlanielMod.LOGGER.error("Error aplicando skin a GameProfile", e);
			return createDefaultProfile(agentId, agentName);
		}
	}

	/**
	 * Registrar textura en TextureManager de Minecraft (sincrónico)
	 *
	 * Este método espera a que la textura esté completamente registrada
	 * antes de retornar el Identifier, eliminando la race condition.
	 */
	private static Identifier registerTextureSync(String agentId, byte[] skinData) throws Exception {
		// Verificar caché de texturas
		if (TEXTURE_CACHE.containsKey(agentId)) {
			BlanielMod.LOGGER.debug("Textura de {} encontrada en caché", agentId);
			return TEXTURE_CACHE.get(agentId);
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			throw new IllegalStateException("MinecraftClient es null, no se puede registrar textura");
		}

		// Identifier único para esta skin
		Identifier skinIdentifier = new Identifier("blaniel", "skins/agent_" + agentId);

		// Convertir byte[] a NativeImage
		NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(skinData));

		// Usar CompletableFuture para esperar el registro en el thread principal
		CompletableFuture<Void> registrationFuture = new CompletableFuture<>();

		// Registrar textura en TextureManager (debe ejecutarse en thread principal)
		client.execute(() -> {
			try {
				client.getTextureManager().registerTexture(
					skinIdentifier,
					new NativeImageBackedTexture(nativeImage)
				);
				BlanielMod.LOGGER.info("Textura registrada exitosamente: {}", skinIdentifier);
				registrationFuture.complete(null);
			} catch (Exception e) {
				BlanielMod.LOGGER.error("Error en thread principal al registrar textura", e);
				registrationFuture.completeExceptionally(e);
			}
		});

		// Esperar a que el registro termine (timeout de 5 segundos)
		try {
			registrationFuture.get(5, java.util.concurrent.TimeUnit.SECONDS);
		} catch (java.util.concurrent.TimeoutException e) {
			throw new Exception("Timeout esperando registro de textura", e);
		} catch (java.util.concurrent.ExecutionException e) {
			throw new Exception("Error durante registro de textura", e.getCause());
		}

		// Guardar en caché solo después de que el registro esté completo
		TEXTURE_CACHE.put(agentId, skinIdentifier);
		BlanielMod.LOGGER.debug("Textura {} agregada al caché", agentId);

		return skinIdentifier;
	}

	/**
	 * Crear GameProfile con skin por defecto (Steve)
	 */
	private static GameProfile createDefaultProfile(String agentId, String agentName) {
		UUID uuid = UUID.nameUUIDFromBytes(("Blaniel:" + agentId).getBytes());
		GameProfile profile = new GameProfile(uuid, agentName);

		BlanielMod.LOGGER.debug("Usando GameProfile por defecto para {}", agentName);

		// Guardar en caché para evitar recreaciones
		PROFILE_CACHE.put(agentId, profile);

		return profile;
	}

	/**
	 * Limpiar caché (útil para recargar skins actualizadas)
	 */
	public static void clearCache(String agentId) {
		PROFILE_CACHE.remove(agentId);
		TEXTURE_CACHE.remove(agentId);

		Path cachedSkinPath = SKIN_CACHE_DIR.resolve(agentId + ".png");
		try {
			Files.deleteIfExists(cachedSkinPath);
			BlanielMod.LOGGER.info("Caché de skin eliminado para: {}", agentId);
		} catch (IOException e) {
			BlanielMod.LOGGER.warn("Error eliminando caché de skin: {}", e.getMessage());
		}
	}

	/**
	 * Limpiar todo el caché
	 */
	public static void clearAllCache() {
		PROFILE_CACHE.clear();
		TEXTURE_CACHE.clear();

		try {
			Files.walk(SKIN_CACHE_DIR)
				.filter(Files::isRegularFile)
				.forEach(path -> {
					try {
						Files.delete(path);
					} catch (IOException e) {
						BlanielMod.LOGGER.warn("Error eliminando {}: {}", path, e.getMessage());
					}
				});
			BlanielMod.LOGGER.info("Todo el caché de skins eliminado");
		} catch (IOException e) {
			BlanielMod.LOGGER.error("Error limpiando caché de skins", e);
		}
	}

	/**
	 * Obtener Identifier de textura para un agente (si está cacheado)
	 *
	 * Retorna null si la textura no está cacheada aún (sin registrar).
	 * El renderer debe manejar el null y usar su fallback.
	 */
	public static Identifier getTextureIdentifier(String agentId) {
		return TEXTURE_CACHE.get(agentId);  // Retorna null si no está en caché
	}

	/**
	 * Verificar si una textura está cargada y lista para usar
	 */
	public static boolean isTextureLoaded(String agentId) {
		return TEXTURE_CACHE.containsKey(agentId);
	}
}
